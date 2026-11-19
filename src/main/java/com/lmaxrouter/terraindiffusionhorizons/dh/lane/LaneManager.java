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
import java.util.stream.Collectors;

/**
 * 异步车道调度外壳 (B1 骨架 -> B2 激活; 设计稿 docs/batch-b-design.md §2/§4.2).
 *
 * <p>计算与调度解耦: LaneCore 是纯函数核心, 本类只决定"在哪跑" —
 * async=true 走后台线程 (地形 LOD 先发布, 算完写缓存表 + 请求 DH 重拉该 region);
 * async=false 在 generate 调用栈内联 (一次成型, 无等待原语).
 * 同一份核心, 两个调用位置, 开关切换不触碰核心一行.</p>
 *
 * <p>B2 变化:
 * ①载荷泛化: List&lt;LaneSegment&gt; -> List&lt;LanePayload&gt; (sealed 管道,
 *   StructureHit 结构撒点与 LaneSegment 方块段同流);
 * ②异步完成回调: 非空载荷打观察日志 + 调刷新钩子请求 DH 重拉 (DhRefreshHook);
 * ③缓存命中非空打 info — 异步环路判据 (首算完成 -> 重拉 -> 二次 generate 命中缓存),
 *   观察期日志, 验收后可降级 [待验 #6: 已有 LOD 的 section 重拉是否短路];
 * ④unload 空缓存静默 (非 TD 世界/未用车道的世界不刷日志).</p>
 */
public final class LaneManager {

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Lane");

    /** 车道名 (日志/线程名). */
    private final String name;
    /** 总开关谓词 (config: structure.enabled / features.enabled). */
    private final BooleanSupplier enabled;
    /** 异步开关谓词 (config: *.async; false = 同步内联模式). */
    private final BooleanSupplier async;

    /** 计算核心注册表. COW: 写极少读多. */
    private final CopyOnWriteArrayList<LaneCore> cores = new CopyOnWriteArrayList<>();
    /** 车道结果缓存: region 键 -> 载荷列表. 与 generate 的唯一交界面 (合并点只读此表). */
    private final ConcurrentHashMap<LaneRegionKey, List<LanePayload>> cache = new ConcurrentHashMap<>();
    /** in-flight 去重: 同 region 任务不重复提交. */
    private final Set<LaneRegionKey> inFlight = ConcurrentHashMap.newKeySet();
    /** 异常黑名单: 炸过的 region 不再重试, 单点失败不连坐 [长期记忆: 118]. */
    private final Set<LaneRegionKey> blacklist = ConcurrentHashMap.newKeySet();
    /** 懒起线程池 (单线程 daemon). */
    private final AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
    /** 车道完成后的刷新钩子 (B2; null = 无钩子直通, B1 行为). 由 DhCompat 注册期装配. */
    private volatile LaneRefreshHook refreshHook;

    LaneManager(String name, BooleanSupplier enabled, BooleanSupplier async) {
        this.name = name;
        this.enabled = enabled;
        this.async = async;
    }

    /** 注册计算核心 (初始化期调用; 列表非空前本类整体短路). */
    public void addCore(LaneCore core) {
        cores.add(core);
        LOG.info("[{}] 车道核心已注册: {}", name, core.getClass().getSimpleName());
    }

    /** 装配刷新钩子 (DhCompat 注册期; 实现自带全部防御, 此处零 try-catch). */
    public void setRefreshHook(LaneRefreshHook hook) {
        this.refreshHook = hook;
    }

    /**
     * generate 线程入口: 查缓存 / (async) 提交后台 / (sync) 内联计算.
     *
     * @return 命中或同步算得的载荷; null = 本次无数据 (未命中/无载荷)
     */
    public List<LanePayload> pollOrSubmit(long worldSeed, byte detailLevel, int posX, int posZ) {
        // 短路序: 总开关 -> 无载荷 (cores 空 = 车道闲置, 连线程池都不碰)
        if (!enabled.getAsBoolean() || cores.isEmpty()) {
            return null;
        }
        LaneRegionKey key = new LaneRegionKey(worldSeed, detailLevel, posX, posZ);
        List<LanePayload> hit = cache.get(key);
        if (hit != null) {
            // B2 观察日志: 异步环路闭环判据 (重拉后的二次 generate 走到此分支)
            // [待验 #6: 若重拉短路则此行永不出现 — 判据双向有效]
            if (!hit.isEmpty()) {
                LOG.info("[{}] 缓存命中 {}: {} 项", name, key, hit.size());
            }
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
                    List<LanePayload> segs = computeAll(key);
                    cache.put(key, segs);
                    // [B2] 非空载荷: 观察日志 + 请求 DH 重拉该 region (获取链 [长期记忆: 125]);
                    // 空载荷 = 无内容 region, 不打日志不重拉 (重拉也是空, 白耗)
                    if (!segs.isEmpty()) {
                        LOG.info("[{}] region {} 完成: {} 项: {}", name, key, segs.size(), preview(segs));
                        LaneRefreshHook hook = refreshHook; // 快照读 (volatile)
                        if (hook != null) {
                            hook.requestRefresh(key.detailLevel(), key.posX(), key.posZ());
                        }
                    }
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
    private List<LanePayload> computeSync(LaneRegionKey key) {
        try {
            List<LanePayload> segs = computeAll(key);
            cache.put(key, segs);
            return segs;
        } catch (Exception e) {
            blacklist.add(key);
            LOG.warn("[{}] region {} 同步计算失败, 进黑名单: {}", name, key, e.toString());
            return null;
        }
    }

    /** 聚合所有 core 的载荷 (core 间相互独立; 一个炸 = 整 region 事务失败进黑名单). */
    private List<LanePayload> computeAll(LaneRegionKey key) throws Exception {
        List<LanePayload> all = new ArrayList<>();
        for (LaneCore core : cores) {
            all.addAll(core.compute(key.seed(), key.detailLevel(), key.posX(), key.posZ()));
        }
        return all;
    }

    /** 日志预览: 前 4 项 toString + 溢出计数 (StructureHit.toString 已是 /locate 对照格式). */
    private static String preview(List<LanePayload> segs) {
        String head = segs.stream().limit(4).map(Object::toString).collect(Collectors.joining(", "));
        return segs.size() > 4 ? head + " …+" + (segs.size() - 4) : head;
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
     *  池保留 (下次加载复用); inFlight 孤儿任务跑完自然落表, 无害.
     *  空缓存静默 (B2: 非 TD 世界 / 车道未用时不刷日志). */
    public void onLevelUnload() {
        int n = cache.size();
        cache.clear();
        blacklist.clear();
        if (n > 0) {
            LOG.info("[{}] 车道缓存已清 ({} region, level unload)", name, n);
        }
    }
}