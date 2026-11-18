package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 异步车道调度外壳 (B1 骨架; 设计稿 docs/batch-b-design.md §2/§4.2).
 *
 * <p>计算与调度解耦: LaneCore 是纯函数核心, 本类只决定"在哪跑" —
 * async=true 走后台线程 (地形 LOD 先发布, 算完写缓存表等刷新);
 * async=false 在 generate 调用栈内联 (一次成型, 无等待原语).
 * 同一份核心, 两个调用位置, 开关切换不触碰核心一行.</p>
 *
 * <p>B1 状态: cores 空列表 = 全部短路, 线程池永不创建, 行为与无车道时代
 * 完全一致 (零开销占位). B2 塞入 LaneCore 实现后管线激活.</p>
 */
public final class LaneManager {

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Lane");

    /** 车道名 (日志/线程名). */
    private final String name;
    /** 总开关谓词 (config: structure.enabled / features.enabled). */
    private final BooleanSupplier enabled;
    /** 异步开关谓词 (config: *.async; false = 同步内联模式). */
    private final BooleanSupplier async;

    /** 计算核心注册表 (B1 恒空; B2 塞结构撒点核心). COW: 写极少读多. */
    private final CopyOnWriteArrayList<LaneCore> cores = new CopyOnWriteArrayList<>();
    /** 车道结果缓存: region 键 -> 段列表. 与 generate 的唯一交界面 (合并点只读此表). */
    private final ConcurrentHashMap<LaneRegionKey, List<LaneSegment>> cache = new ConcurrentHashMap<>();
    /** in-flight 去重: 同 region 任务不重复提交. */
    private final Set<LaneRegionKey> inFlight = ConcurrentHashMap.newKeySet();
    /** 异常黑名单: 炸过的 region 不再重试, 单点失败不连坐 [长期记忆: 118]. */
    private final Set<LaneRegionKey> blacklist = ConcurrentHashMap.newKeySet();
    /** 懒起线程池 (单线程 daemon; cores 恒空时永不创建 = 零开销). */
    private final AtomicReference<ExecutorService> executorRef = new AtomicReference<>();

    LaneManager(String name, BooleanSupplier enabled, BooleanSupplier async) {
        this.name = name;
        this.enabled = enabled;
        this.async = async;
    }

    /** 注册计算核心 (B2+: 初始化期调用; 列表非空前本类整体短路). */
    public void addCore(LaneCore core) {
        cores.add(core);
        LOG.info("[{}] 车道核心已注册: {}", name, core.getClass().getSimpleName());
    }

    /**
     * generate 线程入口: 查缓存 / (async) 提交后台 / (sync) 内联计算.
     * B1 阶段 cores 空 -> 所有分支短路返回 null, generate 行为零变化.
     *
     * @return 命中或同步算得的段; null = 本次无数据 (未命中/无载荷)
     */
    public List<LaneSegment> pollOrSubmit(long worldSeed, byte detailLevel, int posX, int posZ) {
        // 短路序: 总开关 -> 无载荷 (cores 空 = B1 占位, 连线程池都不碰)
        if (!enabled.getAsBoolean() || cores.isEmpty()) {
            return null;
        }
        LaneRegionKey key = new LaneRegionKey(worldSeed, detailLevel, posX, posZ);
        List<LaneSegment> hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        if (async.getAsBoolean()) {
            submitAsync(key);
            return null; // 异步: 本次先无数据, 地形 LOD 先行发布, 算完刷新补全 (设计稿 §3)
        }
        return computeSync(key); // 同步: generate 调用栈内一次成型 (设计稿 §5)
    }

    /** 后台提交 (原子去重 + 黑名单挡; 车道缺席不阻塞地形). */
    private void submitAsync(LaneRegionKey key) {
        if (blacklist.contains(key)) {
            return;
        }
        if (!inFlight.add(key)) {
            return; // add 返回 false = 已在跑 (原子去重, 免双查竞态)
        }
        try {
            executor().submit(() -> {
                try {
                    List<LaneSegment> segs = computeAll(key);
                    cache.put(key, segs);
                    // [B2] 刷新钩子挂点: 触发 DH 对该 region 重拉 (获取链 [长期记忆: 125])
                } catch (Exception e) {
                    blacklist.add(key); // 异常隔离: 该 region 进黑名单不连坐 [长期记忆: 118]
                    LOG.warn("[{}] region {} 计算失败, 进黑名单: {}", name, key, e.toString());
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (Exception e) {
            inFlight.remove(key); // 提交期异常 (池死等): 复位 inFlight, 数据迟到不损坏
            LOG.warn("[{}] 提交失败: {}", name, e.toString());
        }
    }

    /** 同步内联 (async=false: 无等待原语, generate 返回时数据完整). 结果同样落表 (后续命中免算). */
    private List<LaneSegment> computeSync(LaneRegionKey key) {
        try {
            List<LaneSegment> segs = computeAll(key);
            cache.put(key, segs);
            return segs;
        } catch (Exception e) {
            blacklist.add(key);
            LOG.warn("[{}] region {} 同步计算失败, 进黑名单: {}", name, key, e.toString());
            return null;
        }
    }

    /** 聚合所有 core 的段 (core 间相互独立; 一个炸 = 整 region 事务失败进黑名单). */
    private List<LaneSegment> computeAll(LaneRegionKey key) throws Exception {
        List<LaneSegment> all = new ArrayList<>();
        for (LaneCore core : cores) {
            all.addAll(core.compute(key.seed(), key.detailLevel(), key.posX(), key.posZ()));
        }
        return all;
    }

    /** 懒起单线程 daemon 池 (双检锁: generate 线程并发调 pollOrSubmit 是常态). */
    private ExecutorService executor() {
        ExecutorService ex = executorRef.get();
        if (ex == null) {
            synchronized (this) {
                ex = executorRef.get();
                if (ex == null) {
                    ex = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "TDH-" + name + "-lane");
                        t.setDaemon(true);
                        return t;
                    });
                    executorRef.set(ex);
                    LOG.info("[{}] 车道线程池已创建 (核心数 {})", name, cores.size());
                }
            }
        }
        return ex;
    }

    /** 世界卸载: 清缓存与黑名单 (key 含 seed 跨世界本不撞, 清 = 回收内存);
     *  池保留 (下次加载复用); inFlight 孤儿任务跑完自然落表, 无害. */
    public void onLevelUnload() {
        cache.clear();
        blacklist.clear();
        LOG.info("[{}] 车道缓存已清 (level unload)", name);
    }
}