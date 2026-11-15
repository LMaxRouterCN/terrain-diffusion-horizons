package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.github.xandergos.terraindiffusionmc.biome.TerrainBiomeRegistry;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * TD -> DH 的 LOD 生成器 (案②α: 地形几何 + 水体 + 群系驱动表面方块/色调).
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
    /** 案②α: 冰盖水段替换方块 (冻河/冻洋). */
    private IDhApiBlockStateWrapper iceBlock;
    /** 案②α: 纯几何/解析失败回退样式 (config surfaceBlock + fillerBiome). */
    private ColumnStyle fallbackStyle;
    /** 案②α: 群系 key -> 列表面样式缓存 (DH 池多线程; 并发重复构造等价无害). */
    private final ConcurrentHashMap<String, ColumnStyle> styleCache = new ConcurrentHashMap<>();

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
        // [长期记忆: 085] 只拒 PRE_EXISTING_ONLY (语义即"不生成新的"); FEATURES 必须放行:
        // DH 队列传给 generateLod 的 mode 直传 config chunkGeneratorMode (默认 FEATURES),
        // 合法值无 SURFACE (它只存在于 generatorPlan, 永不到达 API 生成器),
        // 拒 FEATURES = 拒默认配置 = 远距零生成 (2026-11-15 TEST 实证)
        if (mode == EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY) {
            return;
        }
        // 守卫 3: seed 一致性 (TD 管线换种子窗口期, 静默错数据比缺数据更糟)
        if (LocalTerrainProvider.getSeed() != expectedSeed) {
            LOG.warn("seed 不符 (TD={} 期望={}), 跳过本数据源", LocalTerrainProvider.getSeed(), expectedSeed);
            return;
        }
        // ==== TD 查询 (块坐标, i=Z j=X) ====
        // 案②α: withBiomes 门控 (内存 ~520MB vs ~150MB, TEST 实测; config 关掉即退纯几何模式)
        boolean withBiomes = TdConfig.get().useBiomeSurface;
        int[] b = TdLodTranslator.dataSourceBlockBounds(posX, posZ, detailLevel, cols);
        LocalTerrainProvider.RiverTerrainData data;
        try {
            data = LocalTerrainProvider.getRiverTerrainData(b[0], b[1], b[2], b[3], withBiomes);
        } catch (Exception e) {
            LOG.warn("TD 查询失败 [i1={},j1={},i2={},j2={}]: {}", b[0], b[1], b[2], b[3], e.toString());
            return;
        }
        if (data == null || data.elevation == null) {
            LOG.warn("TD 返回空数据");
            return;
        }

        // [契约观测 2026-11-15] TD 数组宽应与 DH 数据源列数一致 (dl0 均 64); 不符仅告警不中断, 留运行时证据
        if (data.width != cols) {
            LOG.warn("TD data.width({}) != DH cols({}), 采样索引可能越界/错位", data.width, cols);
        }

        // ==== wrapper 懒取 (字符串获取抛 checked IOException, 失败则放弃本数据源) ====
        if (!resolveWrappers()) {
            return;
        }

        // ==== 双循环写列 ====
        // y 段按半开区间 [bottom, top) 写.
        // [长期记忆: 092] DH 存储 y 为 12 位无符号: 负 bottom (minY=-64) 回绕成 4032 → bottom>top 几何倒挂 → 冲天石柱 + Negative y size 刷屏 (已修).
        // [长期记忆: 095] DH y 语义 = 距世界底偏移 (相对 y): 4032 修复时顶保持绝对坐标 → 重测整体矮 ~64 (=|minY|), 092 预案命中.
        // 故全部 y 统一转相对: yRel = yAbs - minY; dataBottom = 0 即世界底, maxY 同步转.
        int minY = levelWrapper.getMinHeight();
        int dataBottom = 0; // 相对语义: 0 = 世界底 (主世界即绝对 -64); 地下不可见不写段, 与官方模板同构
        int maxY = levelWrapper.getMaxHeight() - minY; // 维度顶转相对 (主世界 320-(-64) = 384)
        int width = data.width;               // TD 数组宽 = X 向块数
        float[] elev = data.elevation;        // 海拔米 (行主序 idx = z*width + x)
        byte[] waterMask = data.waterMask;    // 1 = 水下
        float[] waterSurf = data.waterSurface; // 水面海拔米 (可能为 null)
        short[] biomeIdx = withBiomes ? data.biomeIndexes : null; // 案②α: 群系索引 (withBiomes=true 时非 null)

        for (int x = 0; x < cols; x++) {
            for (int z = 0; z < cols; z++) {
                int idx = TdLodTranslator.sampleIndex(x, z, width);
                // 案①: convert() = 第一个空气 y (density 函数/mixin/FeaturePlacer 五证合一), topY 同语义直接用, 不再 +1
                int solidTop = clampY(HeightConverter.convertToMinecraftHeight((short) elev[idx]) - minY, dataBottom, maxY); // -minY: 绝对 y 转相对 [长期记忆: 095]
                int coverTop = solidTop;
                boolean waterCol = waterMask != null && waterMask[idx] != 0 && waterSurf != null;
                // 水体列: 水面高于地形才成池 (waterMask 优先, 液面 <= 地形视为无水)
                if (waterCol) {
                    int waterTop = clampY(HeightConverter.convertToMinecraftHeight((short) waterSurf[idx]) - minY, dataBottom, maxY); // 案①同修: 同语义不加 1
                    if (waterTop > solidTop) {
                        coverTop = waterTop;
                    }
                }
                // 案②α: 列表面样式 (群系驱动; 查询未带群系时退 fallback)
                ColumnStyle style = biomeIdx != null ? resolveStyle(biomeIdx[idx]) : fallbackStyle;
                List<DhApiTerrainDataPoint> points = new ArrayList<>(3);
                // [长期记忆: 092] 段底喂 dataBottom (非负): 喂 minY=-64 被 DH 12 位无符号存储回绕成 4032 → 冲天石柱元凶
                // 水列顶部用地表下顶部方块 (vanilla 水下分支同源); 冰盖群系水段顶换冰
                points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, dataBottom, solidTop, waterCol ? style.underwater : style.surface, style.biome));
                if (coverTop > solidTop) {
                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, solidTop, coverTop, style.iceCover ? iceBlock : waterBlock, style.biome));
                }
                if (coverTop < maxY) {
                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, coverTop, maxY, airBlock, style.biome));
                }
                // 4 参显式版: 3 参 default 已弃用, 其字节码转发 4 参时 step 固定 SURFACE (javap 实证, 同值替换行为无损)
                dataSource.setApiDataPointColumn(x, z, EDhApiWorldGenerationStep.SURFACE, points);
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
            iceBlock = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper("minecraft:ice", levelWrapper);
            // 案②α: fallback 样式 = 纯几何模式旧路径 (表面=水下=cfg.surfaceBlock, 无冰盖)
            fallbackStyle = new ColumnStyle(surfaceBlock, surfaceBlock, fillerBiome, false);
            return true;
        } catch (Exception e) {
            LOG.warn("wrapper 获取失败 (配置: surface={} biome={}): {}", cfg.surfaceBlock, cfg.fillerBiome, e.toString());
            return false;
        }
    }

    // ==== 案②α: 群系驱动的列表面样式 ====

    /** 不可变列样式: 表面/水下方块 wrapper + 群系 wrapper + 冰盖标志. */
    private static final class ColumnStyle {
        final IDhApiBlockStateWrapper surface;
        final IDhApiBlockStateWrapper underwater;
        final IDhApiBiomeWrapper biome;
        final boolean iceCover;

        ColumnStyle(IDhApiBlockStateWrapper surface, IDhApiBlockStateWrapper underwater,
                    IDhApiBiomeWrapper biome, boolean iceCover) {
            this.surface = surface;
            this.underwater = underwater;
            this.biome = biome;
            this.iceCover = iceCover;
        }
    }

    /**
     * biomeIndex -> 列表面样式 (带缓存).
     * registry.keyForIndex 对未注册索引无空检查 (NPE 风险已知), 异常/未知 key 全部落 fallback.
     * 群系 wrapper 直接用 TD catalog key (minecraft:xxx), 与 chunk 侧 BiomeSource 同源.
     */
    private ColumnStyle resolveStyle(short biomeIndex) {
        String key;
        try {
            key = TerrainBiomeRegistry.instance().keyForIndex(biomeIndex);
        } catch (Exception e) {
            return fallbackStyle;
        }
        if (key == null) {
            return fallbackStyle;
        }
        ColumnStyle cached = styleCache.get(key);
        if (cached != null) {
            return cached;
        }
        // 构造 (良性竞争: 并发重复构造等价无害, put 覆盖同值)
        TdConfig cfg = TdConfig.get();
        String surfaceId = cfg.surfaceBlockOverrides.getOrDefault(key, TdSurfaceBlockMap.surface(key));
        try {
            ColumnStyle s = new ColumnStyle(
                    DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(surfaceId, levelWrapper),
                    DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(TdSurfaceBlockMap.underwater(key), levelWrapper),
                    DhApi.Delayed.wrapperFactory.getBiomeWrapper(key, levelWrapper),
                    TdSurfaceBlockMap.iceCover(key));
            styleCache.put(key, s);
            return s;
        } catch (Exception e) {
            LOG.warn("群系样式构造失败 ({}): {}, 本数据源退 fallback", key, e.toString());
            return fallbackStyle;
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
        iceBlock = null;
        fallbackStyle = null;
        styleCache.clear();
    }
}
