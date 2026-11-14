package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * TD -> DH 的 LOD 生成器 (S2b: 地形几何 + 水体; 群系/表面方块占位, S4 换真数据).
 *
 * <p>列写入协议: setApiDataPointColumn(x, z, list) 的 x/z 是数据源内局部索引,
 * 列内点自底向上无缝平铺, 全部列写完后 resultConsumer.accept(dataSource) 恰一次
 * (依据: 官方 TestGenericWorldGenerator.generateInternal 字节码).</p>
 */
public class TdDhWorldGenerator implements IDhApiWorldGenerator {

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Generator");

    /** 注册时捕获的世界种子, 用于 TD 管线 seed 一致性防护. */
    private final long expectedSeed;
    /** 本生成器绑定的维度 (wrapper 按维度获取). */
    private final IDhApiLevelWrapper levelWrapper;
    /** 首次 generateLod 落参数日志 (posX/posZ 单位与实际档位的运行时定案证据). */
    private final AtomicBoolean firstCallLogged = new AtomicBoolean(false);
    /** wrapper 缓存 (首次生成时懒取, 取失败则本数据源放弃). */
    private IDhApiBiomeWrapper fillerBiome;
    private IDhApiBlockStateWrapper surfaceBlock;
    private IDhApiBlockStateWrapper waterBlock;
    private IDhApiBlockStateWrapper airBlock;

    public TdDhWorldGenerator(IDhApiLevelWrapper levelWrapper, long worldSeed) {
        this.levelWrapper = levelWrapper;
        this.expectedSeed = worldSeed;
    }

    // ==== 档位与类型声明 ====

    /**
     * 只供 API dl0 (64 块/列, = core dl6), 粗档交 DH 原生 DOWN_SAMPLED.
     * 声明单位与 generateLod 的 detailLevel 参数同语义; default 实现亦返回 BLOCK(0),
     * 若为 core 级单位则 default 会引导 DH 请求负档, 不成立 — 联调首调日志终审.
     */
    @Override
    public byte getSmallestDataDetailLevel() {
        return 0;
    }

    @Override
    public byte getLargestDataDetailLevel() {
        return 0;
    }

    /**
     * 必须 override: default 返回 VANILLA_CHUNKS, DH 会分流到 generateChunks,
     * generateLod 永不被调. 我们是数据源型生成器.
     */
    @Override
    public EDhApiWorldGeneratorReturnType getReturnType() {
        return EDhApiWorldGeneratorReturnType.API_DATA_SOURCES;
    }

    /** 照官方测试生成器姿态跳过 API 自检. */
    @Override
    public boolean runApiValidation() {
        return false;
    }

    // ==== 生成入口 ====

    /**
     * runAsync 壳: 真逻辑在 DH 提供的池线程上跑 (TD 的推理阻塞语义正确落地于此),
     * 官方模板同构. 异常吞在 future 内, 不调 consumer (DH 对未回执数据源按超时处理).
     */
    @Override
    public CompletableFuture<Void> generateLod(int chunkPosMinX, int chunkPosMinZ, int posX, int posZ,
                                               byte detailLevel, IDhApiFullDataSource dataSource,
                                               EDhApiDistantGeneratorMode mode, ExecutorService pool,
                                               Consumer<IDhApiFullDataSource> resultConsumer) {
        return CompletableFuture.runAsync(
                () -> generateInternal(chunkPosMinX, chunkPosMinZ, posX, posZ,
                        detailLevel, dataSource, mode, resultConsumer),
                pool);
    }

    /** 真逻辑 (同步体, 跑在 DH 池线程). */
    private void generateInternal(int chunkPosMinX, int chunkPosMinZ, int posX, int posZ,
                                  byte detailLevel, IDhApiFullDataSource dataSource,
                                  EDhApiDistantGeneratorMode mode,
                                  Consumer<IDhApiFullDataSource> resultConsumer) {
        int cols = dataSource.getWidthInDataColumns();

        // 首调日志: posX/posZ 单位 + 实际请求档位的运行时定案证据
        if (firstCallLogged.compareAndSet(false, true)) {
            LOG.info("首调: chunkMin=({},{}) pos=({},{}) dl={} cols={} mode={} ds={}",
                    chunkPosMinX, chunkPosMinZ, posX, posZ, detailLevel, cols, mode,
                    dataSource.getClass().getSimpleName());
        }

        // 守卫 1: 档位过滤 (声明 [0,0], 非 0 请求属于单位理解错误, 日志留证不硬吞)
        if (detailLevel != 0) {
            LOG.warn("跳过非 dl0 请求 (dl={}), 档位单位理解需修正", detailLevel);
            return;
        }
        // 守卫 2: 模式过滤 (S2b 姿态: 宁可多吃不可漏吃, 写同一数据源幂等)
        if (mode != EDhApiDistantGeneratorMode.SURFACE && mode != EDhApiDistantGeneratorMode.INTERNAL_SERVER) {
            return; // FEATURES (无 feature 数据) / PRE_EXISTING_ONLY (存档已有数据) 静默让行
        }
        // 守卫 3: seed 一致性 (TD 管线换种子窗口期, 静默错数据比缺数据更糟)
        if (LocalTerrainProvider.getSeed() != expectedSeed) {
            LOG.warn("seed 不符 (TD={} 期望={}), 跳过本数据源", LocalTerrainProvider.getSeed(), expectedSeed);
            return;
        }
        // ==== TD 查询 (块坐标, i=Z j=X; withBiomes=false: S2b 占位不需要 climate/biome, 数据量 520MB->150MB) ====
        int[] b = TdLodTranslator.dataSourceBlockBounds(posX, posZ, detailLevel, cols);
        LocalTerrainProvider.RiverTerrainData data;
        try {
            data = LocalTerrainProvider.getRiverTerrainData(b[0], b[1], b[2], b[3], false);
        } catch (Exception e) {
            LOG.warn("TD 查询失败 [i1={},j1={},i2={},j2={}]: {}", b[0], b[1], b[2], b[3], e.toString());
            return;
        }
        if (data == null || data.elevation == null) {
            LOG.warn("TD 返回空数据");
            return;
        }

        // ==== wrapper 懒取 (字符串获取抛 checked IOException, 失败则放弃本数据源) ====
        if (!resolveWrappers()) {
            return;
        }

        // ==== 双循环写列 ====
        // y 段语义按半开区间 [bottom, top) 写 (官方模板 (0,h)+(h,顶) 的解读, 运行时偏移一格属已知待验证项)
        int minY = levelWrapper.getMinHeight();
        int maxY = levelWrapper.getMaxHeight();
        int width = data.width;               // TD 数组宽 = X 向块数
        float[] elev = data.elevation;        // 海拔米 (行主序 idx = z*width + x)
        byte[] waterMask = data.waterMask;    // 1 = 水下
        float[] waterSurf = data.waterSurface; // 水面海拔米 (可能为 null)

        for (int x = 0; x < cols; x++) {
            for (int z = 0; z < cols; z++) {
                int idx = TdLodTranslator.sampleIndex(x, z, width);
                int solidTop = clampY(HeightConverter.convertToMinecraftHeight((short) elev[idx]) + 1, minY, maxY);
                int coverTop = solidTop;
                // 水体列: 水面高于地形才成池 (waterMask 优先, 液面 <= 地形视为无水)
                if (waterMask != null && waterMask[idx] != 0 && waterSurf != null) {
                    int waterTop = clampY(HeightConverter.convertToMinecraftHeight((short) waterSurf[idx]) + 1, minY, maxY);
                    if (waterTop > solidTop) {
                        coverTop = waterTop;
                    }
                }
                List<DhApiTerrainDataPoint> points = new ArrayList<>(3);
                points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, minY, solidTop, surfaceBlock, fillerBiome));
                if (coverTop > solidTop) {
                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, solidTop, coverTop, waterBlock, fillerBiome));
                }
                if (coverTop < maxY) {
                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, coverTop, maxY, airBlock, fillerBiome));
                }
                dataSource.setApiDataPointColumn(x, z, points);
            }
        }
        // 全列完成, 恰一次回执 (官方模板收尾)
        resultConsumer.accept(dataSource);
    }

    // ==== 辅助 ====

    /** y 边界钳制 (TD 极值理论上可达 ~396, 越界列截断到维度高度域). */
    private static int clampY(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    /** 首次生成时取 wrapper 并缓存; 取失败 (无效 ID / IO) 返回 false, 本数据源放弃. */
    private boolean resolveWrappers() {
        if (fillerBiome != null) {
            return true;
        }
        TdConfig cfg = TdConfig.get();
        try {
            fillerBiome = DhApi.Delayed.wrapperFactory.getBiomeWrapper(cfg.fillerBiome, levelWrapper);
            surfaceBlock = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(cfg.surfaceBlock, levelWrapper);
            // API 工厂无水专用方法, 按默认方块状态取 (水的默认 state 即静止水)
            waterBlock = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper("minecraft:water", levelWrapper);
            airBlock = DhApi.Delayed.wrapperFactory.getAirBlockStateWrapper();
            return true;
        } catch (Exception e) {
            LOG.warn("wrapper 获取失败 (配置: surface={} biome={}): {}", cfg.surfaceBlock, cfg.fillerBiome, e.toString());
            return false;
        }
    }

    /** DH 生成任务开始前钩子 (S2b 无需准备, 留空). */
    @Override
    public void preGeneratorTaskStart() {
    }

    /**
     * 生命周期收尾. DH 注册面无反注册 API (IDhApiWorldGeneratorOverrideRegister 仅 register),
     * 世界卸载的清理落在本钩子: 置空 wrapper 引用, 便于 GC 回收维度级缓存.
     */
    @Override
    public void close() {
        fillerBiome = null;
        surfaceBlock = null;
        waterBlock = null;
        airBlock = null;
    }
}
