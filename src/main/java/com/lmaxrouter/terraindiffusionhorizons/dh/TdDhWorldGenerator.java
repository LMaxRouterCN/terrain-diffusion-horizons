package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.github.xandergos.terraindiffusionmc.biome.TerrainBiomeRegistry;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import net.minecraft.world.level.biome.Biome;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneManagers;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LanePayload;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneSegment;
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
import java.util.Optional;
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
    /** [C] 河床强度分档方块 (per-column 选择, 不进 styleCache). */
    private IDhApiBlockStateWrapper sandBlock;
    private IDhApiBlockStateWrapper gravelBlock;
    private IDhApiBlockStateWrapper clayBlock;
    /** 案②α: 纯几何/解析失败回退样式 (config surfaceBlock + fillerBiome). */
    private ColumnStyle fallbackStyle;
    /** 案②α: 群系 key -> 列表面样式缓存 (DH 池多线程; 并发重复构造等价无害). */
    private final ConcurrentHashMap<String, ColumnStyle> styleCache = new ConcurrentHashMap<>();
    /** [L3] 结构方块 wrapper 缓存 (Optional 哨兵: CHM 不容 null value, empty = 获取失败不重试). */
    private final ConcurrentHashMap<String, Optional<IDhApiBlockStateWrapper>> structWrapperCache = new ConcurrentHashMap<>();

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
        // ==== [B1] 车道骨架占位: cores 空表全短路, 行为与 A 批完全一致 ====
        // B2 塞入 LaneCore 后激活: async=true 地形 LOD 先行, 车道后台补算 + 刷新;
        // async=false generate 调用栈内联一次成型; 返回段 B3 合并点消费 (设计稿 §2/§3/§5)
        LaneManagers.structure().pollOrSubmit(expectedSeed, detailLevel, posX, posZ); // [L2] 撒点载荷 (坐标事实, 日志/调试通道)
        // [L3] features 车道 = 结构组装载荷 (LaneSegment 列段); 命中/同步算得返回列表, 异步首算 null (地形 LOD 先行, 算完刷新补全)
        List<LanePayload> structPayloads = LaneManagers.features().pollOrSubmit(expectedSeed, detailLevel, posX, posZ);

        // ==== TD 查询 (块坐标, i=Z j=X) ====
        // 案②α: withBiomes 门控 (内存 ~520MB vs ~150MB, TEST 实测; config 关掉即退纯几何模式)
        TdConfig cfg0 = TdConfig.get();
        boolean withBiomes = cfg0.useBiomeSurface;
        // [max 2026-11-18 裁决] L1 树格(含竹层)属地物范畴且半成品跳级, 配置控制断开恒关;
        // 代码保留待地物批参考, 地物批落地后整块退役; vegetationColoring 归还花草上色层 [长期记忆: 126]
        boolean vegEnabled = false;
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
        int seaTop = clampY(HeightConverter.convertToMinecraftHeight((short) 0) - minY, dataBottom, maxY); // [O] 海平面 (convert(0)=63): 海洋列水段顶, 循环外常量
        int width = data.width;               // TD 数组宽 = X 向块数
        float[] elev = data.elevation;        // 海拔米 (行主序 idx = z*width + x)
        byte[] waterMask = data.waterMask;    // 水强度 0-255 (>=8 有效, <8 边缘抗锯齿格 [W])
        float[] waterSurf = data.waterSurface; // 水面海拔米 (可能为 null)
        short[] biomeIdx = withBiomes ? data.biomeIndexes : null; // 案②α: 群系索引 (withBiomes=true 时非 null)

        // ==== [L3] 结构段索引: features 车道载荷 -> 列顶段 (世界块坐标 -> 数据源局部) ====
        // b = [Z起, X起, Z止, X止] (dataSourceBlockBounds 返回序); 同列多段取 topY 最高者 (顶盖语义, 重叠结构罕见);
        // 越界弃 = AssembleCore 已 clip region 界的防御双保险
        LaneSegment[] structSegs = new LaneSegment[cols * cols];
        if (structPayloads != null) {
            for (LanePayload p : structPayloads) {
                if (!(p instanceof LaneSegment seg)) {
                    continue; // 非方块段载荷 (防御: features 车道现仅产 LaneSegment)
                }
                int lx = seg.blockX() - b[1];
                int lz = seg.blockZ() - b[0];
                if (lx < 0 || lx >= cols || lz < 0 || lz >= cols) {
                    continue;
                }
                int li = lx * cols + lz;
                LaneSegment cur = structSegs[li];
                if (cur == null || seg.topY() > cur.topY()) {
                    structSegs[li] = seg;
                }
            }
        }
        for (int x = 0; x < cols; x++) {
            for (int z = 0; z < cols; z++) {
                int idx = TdLodTranslator.sampleIndex(x, z, width);
                // 案①: convert() = 第一个空气 y (density 函数/mixin/FeaturePlacer 五证合一), topY 同语义直接用, 不再 +1
                int solidTop = clampY(HeightConverter.convertToMinecraftHeight((short) elev[idx]) - minY, dataBottom, maxY); // -minY: 绝对 y 转相对 [长期记忆: 095]
                int coverTop = solidTop;
                // [W] waterMask 为 0-255 强度: <8 是边缘抗锯齿格 (TD chunk 侧同样跳过); NaN 液面一并防御
                int strength = waterMask != null ? (waterMask[idx] & 0xFF) : 0;
                boolean fluvialCol = strength >= 8 && waterSurf != null && Float.isFinite(waterSurf[idx]);
                // [O] 海洋 cell 的 elevation 是负米海床深度 (BiomeClassifier.isOcean = elev < 0)
                boolean oceanCol = elev[idx] < 0f;
                boolean waterCol = false;
                // 水体列一 (fluvial 河流): 液面高于地形才成池 (液面 <= 地形视为无水, TD chunk 同语义)
                if (fluvialCol) {
                    int waterTop = clampY(HeightConverter.convertToMinecraftHeight((short) waterSurf[idx]) - minY, dataBottom, maxY); // 案①同修: 同语义不加 1
                    if (waterTop > coverTop) {
                        coverTop = waterTop;
                        waterCol = true;
                    }
                }
                // 水体列二 (ocean 海洋): 水段 [海床, seaTop(=y63)]; TD 侧 applyRiverBiomesFromWindow 对
                // ocean cell 跳过河群系 -> fluvial/ocean 互斥, 此处防御取液面高者
                if (oceanCol && seaTop > coverTop) {
                    coverTop = seaTop;
                    waterCol = true;
                }
                // 案②α: 列表面样式 (群系驱动; 查询未带群系时退 fallback)
                ColumnStyle style = biomeIdx != null ? resolveStyle(biomeIdx[idx]) : fallbackStyle;
                List<DhApiTerrainDataPoint> points = new ArrayList<>(3);
                // [长期记忆: 092] 段底喂 dataBottom (非负): 负值被 DH 12 位无符号存储回绕成 4032 → 冲天石柱元凶 (已修)
                // 水列底段方块: ocean 列 = 群系海床方块, fluvial 列按水强度分档 [C] (经验配比 + 空间散列, 键用世界块坐标)
                IDhApiBlockStateWrapper bedBlock = style.surface;
                if (waterCol) {
                    bedBlock = style.underwater;
                    if (fluvialCol) {
                        int gx = b[1] + x; // b = [i1(Z起), j1(X起), i2, j2] -> gx/gz = 世界块坐标
                        int gz = b[0] + z;
                        int h = (gx * 0x9E3779B9 + gz * 0x85EBCA6B) >>> 24; // 确定性散列取高 8 位 [0,255]
                        bedBlock = strength < 72
                                ? (h < 160 ? sandBlock : gravelBlock) // 弱水: 沙 62.5% / 砾 37.5%
                                : (h < 218 ? gravelBlock : clayBlock); // 强水: 砾 85.2% / 黏土 14.8%
                    }
                }
                // ==== L1 花草色斑 (列级概率近似; 视觉目标 = 方案 b, 发射形态 = 方案 a 帽段) ====
                // 守卫: cfg 开关 + 干列 (水列顶是水/冰盖) + 列顶在地表以上 + 群系有花草样式;
                // 花优先于草, 草池受 grassColoring gate (草色≈草方块色, 留口默认不发, TdFlowerMap 类注 6)
                IDhApiBlockStateWrapper topBlock = null;
                if (cfg0.vegetationColoring && !waterCol && solidTop > dataBottom && style.flower != null) {
                    int gx = b[1] + x; // 世界块坐标 (与 [C] 河床分档散列同源)
                    int gz = b[0] + z;
                    // 列哈希: 结构 (XOR 流) 与尾盐均区别于树格, 避免花斑与树格系统性同位
                    long fh = mix64(expectedSeed ^ gx * 0x9E3779B1L ^ gz * 0x85EBCA77L ^ 0x27D4EB2FL);
                    int roll = (int) Long.remainderUnsigned(fh, 1000);
                    VegPool flowerPool = style.flower.flowers;
                    VegPool grassPool = cfg0.grassColoring ? style.flower.grasses : null;
                    VegPool hitPool = null;
                    if (flowerPool != null && roll < flowerPool.permille) {
                        hitPool = flowerPool; // 命花 (permille = 列顶是花的概率)
                    } else if (grassPool != null && roll >= 1000 - grassPool.permille) {
                        hitPool = grassPool; // 命草 (留口: grassColoring 开启才可达)
                    }
                    if (hitPool != null) {
                        // 二次哈希扫权重前缀和: 池内条目采样 (与 roll 独立)
                        long ph = mix64(fh + 0x9E3779B97F4A7C15L);
                        int r = (int) Long.remainderUnsigned(ph, hitPool.total);
                        for (int wi = 0; wi < hitPool.wrappers.length; wi++) {
                            if (r < hitPool.prefix[wi]) {
                                topBlock = hitPool.wrappers[wi];
                                break;
                            }
                        }
                    }
                }
                // 基础段发射: 原单段 [底, 顶) 恒发; 命中 = 顶上方加 [顶, 顶+1) 花草帽段
                // [bug根因 2026-11-18 TEST 实测症状 + 机制推断(jar 溯源同轮验证)] 旧拆段方案:
                // 花/草 = cross 非 solid, DH 顶面取色吃 topmost 段但列高跳过非 solid 段
                // → 花列整体矮 1 格 ("上色块下沉" = 坑); 坑壁 4 侧 = +4 quad/命中列,
                // cherry 14% 列命中 → quad +56% → FPS 500→~300 实测吻合.
                // 帽段修法: 列高走 bed 段顶 (地形高度不动), 取色走帽段 (花色); 与 vanilla 几何
                // 一致 (花长在草上方, 非替换); 悬崖侧面仍 bed 色 (方案 b 视觉目标保持).
                // maxY 守卫防顶越界 (段 [顶, 顶+1) 需 顶+1 <= maxY).
                points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, dataBottom, solidTop, bedBlock, style.biome));
                if (topBlock != null && solidTop < maxY) {
                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, solidTop, solidTop + 1, topBlock, style.biome));
                    // [bug根因 2026-11-18] 帽段占用 [solidTop, solidTop+1) 但 coverTop 未抬升, 尾段 air 仍按
                    // [coverTop(=solidTop), maxY) 发射 → 与帽段重叠; DH 段堆叠不变量 (top-down: prev.bottom == cur.top)
                    // 在 air/帽边界被切 → "gap between [X] and [X]" 两值相等 = 重叠指纹 → 每命中列炸 → LOD 零输出.
                    // 修: 帽段发射后抬 coverTop, 尾段 air 从帽上起 (solidTop+1<=maxY 由外层 if 守卫保证).
                    coverTop = solidTop + 1;
                }
                // [半成品·停用 2026-11-18 max裁决] L1 植被虚拟树格 (含竹层), 地物范畴半成品:
                // 跳级+形状bug+配置接错 → 配置断开恒关 (vegEnabled=false), 代码保留待地物批参考.
                // 现状清单:
                // ① 发射bug(未修): 块形冠段无离心调制 = 立方体树冠 (实测筷子插豆腐), 见下方冠段BUG标注;
                //    Chebyshev 距离方角 = 另一小病灶 (角落补刀可修, 未修).
                // ② 已实测验证: 悬空冠段+冠下 air 段渲染协议通过 [长期记忆: 115 结案]; tint 链生效.
                // ③ 未验证: 云杉锥形观感 / cherry 垂叶 / pink_petals 机制.
                // ④ 数据侧 (TdVegetationMap) 无罪: 表驱动, 字段全带 json/javap 出处.
                // 下接原设计备忘 (内容继续有效, 半成品现状以上述清单为准):
                // L1 植被虚拟树格 [长期记忆: 123 勘误: dl0 列 = 1x1 块; 旧 density 观感标定表已退役]:
                // 世界按 cell 划格, 每 cell 确定性哈希恰一棵树 (格内抖动 / 池权重 / 干高 =
                // trunkBase+U(0,randA)+U(0,randB) 双独立哈希, vanilla trunk_placer 双 nextInt 同构);
                // 树心列 = 干段(log)+冠段, 冠半径列 = 悬空冠段锚定树心干顶, 冠下缝隙显式 air 段
                // (列内点自底向上无缝平铺协议).
                // [临时] [长期记忆: 106] feature 层近似, L4 地物同源落地后退役.
                // [长期记忆: 115] 悬空冠段渲染行为未实测 (旁证 = vanilla DH 卸载林子的树冠段), TEST 验.
                if (vegEnabled && !waterCol && style.veg != null && style.logs != null) {
                    int gx = b[1] + x; // b = [i1(Z起), j1(X起), i2, j2] -> gx/gz = 世界块坐标
                    int gz = b[0] + z;
                    TdVegetationMap.VegStat veg = style.veg;
                    int cell = veg.cell;
                    int cix = Math.floorDiv(gx, cell);
                    int ciz = Math.floorDiv(gz, cell);
                    // 预判: 本列是否自己格的树心 (树心列不放竹; 与格循环同哈希源)
                    long ph = mix64(expectedSeed + cix * 0x9E3779B1L + ciz * 0x85EBCA77L + 0x2545F491L);
                    int pjx = (int) Long.remainderUnsigned(ph, cell);
                    ph = mix64(ph);
                    int pjz = (int) Long.remainderUnsigned(ph, cell);
                    boolean selfTrunk = cix * cell + pjx == gx && ciz * cell + pjz == gz;
                    int cursor = solidTop; // 已写段顶游标
                    // 竹噪声列层 (bamboo_jungle): 命中概率 = vanilla 噪声 count / 256;
                    // count = ceil((BIOME_INFO_NOISE(x/80, z/80) + 0.3) * 160) [NoiseBasedCountPlacement 同构],
                    // 噪声 = Biome 静态字段 (固定种子 1234, 世界无关) 直引零构造;
                    // 采样取 chunk 中心 (vanilla 装饰协议), 忠实复刻竹丛 chunk 块状密度
                    if (veg.bamboo && !selfTrunk && style.bamboo != null) {
                        int ccx = (gx >> 4) * 16 + 8;
                        int ccz = (gz >> 4) * 16 + 8;
                        double bn = Biome.BIOME_INFO_NOISE.getValue(ccx / 80.0, ccz / 80.0, false); // [deprecation 有意] 上游废弃标记不跟随: 该字段就是 vanilla 竹丛密度的本尊 (固定种子 1234, 世界无关)
                        int bcnt = (int) Math.ceil((bn + 0.3) * 160);
                        if (bcnt > 0) {
                            long bz = mix64(expectedSeed + gx * 0x27D4EB2FL + gz * 0x165667B1L + 0xB5026F5AL);
                            if (Long.remainderUnsigned(bz, 256) < bcnt) {
                                // 竿高 5-16 [BambooFeature javap: 5 + nextInt(12)]
                                int btop = Math.min(solidTop + 5 + (int) Long.remainderUnsigned(bz >>> 32, 12), maxY);
                                if (btop > cursor) {
                                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, cursor, btop, style.bamboo, style.biome));
                                    cursor = btop;
                                }
                            }
                        }
                    }
                    // 邻域 cell 反查 (radiusCells 格, 覆盖充分性由 TdVegetationMap 静态校验)
                    for (int aj = ciz - veg.radiusCells; aj <= ciz + veg.radiusCells; aj++) {
                        for (int ai = cix - veg.radiusCells; ai <= cix + veg.radiusCells; ai++) {
                            // cell 树哈希: 种子 + 格坐标, splitmix64 链 (派生值独立再混合, 同格永不跳变)
                            long hz = mix64(expectedSeed + ai * 0x9E3779B1L + aj * 0x85EBCA77L + 0x2545F491L);
                            int jx = (int) Long.remainderUnsigned(hz, cell); // 格内抖动
                            hz = mix64(hz);
                            int jz = (int) Long.remainderUnsigned(hz, cell);
                            int tx = ai * cell + jx; // 树心世界坐标
                            int tz = aj * cell + jz;
                            int dc = Math.max(Math.abs(gx - tx), Math.abs(gz - tz)); // Chebyshev 距离
                            if (dc > veg.maxCanopyR) continue; // 快速剔除 (池内最大半径)
                            hz = mix64(hz);
                            int sel = (int) Long.remainderUnsigned(hz, 1000); // 树种 (池权重千分比)
                            hz = mix64(hz);
                            int hA = (int) (hz >>> 32); // 干高窗口 A (兼双倍茎判定)
                            int hB = (int) hz; // 干高窗口 B
                            // 池权重前缀和选型 (千分比; wrapper 取失败的型跳过 = 该格无树)
                            TdVegetationMap.TreeType type = null;
                            IDhApiBlockStateWrapper logW = null, leafW = null;
                            int acc = 0;
                            for (int pi = 0; pi < veg.pool.length; pi++) {
                                acc += veg.pool[pi].weight;
                                if (sel < acc) {
                                    type = veg.pool[pi].type;
                                    logW = style.logs[pi];
                                    leafW = style.leaves[pi];
                                    break;
                                }
                            }
                            if (type == null || logW == null || leafW == null) continue;
                            // 干高: vanilla trunk_placer 同构 (双 nextInt 独立分布)
                            int trunkH = type.trunkBase + Math.floorMod(hA, type.randA + 1) + Math.floorMod(hB, type.randB + 1);
                            // 巨型蘑菇双倍茎: 概率命中时茎高翻倍 [javap AbstractHugeMushroomFeature.getTreeHeight]
                            if (type.doubleStemPermille > 0 && Math.floorMod(hA >>> 16, 1000) < type.doubleStemPermille) {
                                trunkH *= 2;
                            }
                            // 树心地面: elevation[树心] (树心可能在界外, clamp 进数组取近似)
                            int ax = Math.min(Math.max(tx - b[1], 0), width - 1);
                            int az = Math.min(Math.max(tz - b[0], 0), width - 1);
                            int treeGround = clampY(HeightConverter.convertToMinecraftHeight((short) elev[TdLodTranslator.sampleIndex(ax, az, width)]) - minY, dataBottom, maxY);
                            int tt = treeGround + trunkH; // 树心干顶 (冠锚点)
                            if (dc == 0) {
                                // 树心列: 干段 [cursor, 冠底) (conical 冠底低于干顶, 干止于冠底;
                                // 邻树高冠已越过本冠底时干段被吞 — 观感无碍, 干本就藏于冠内)
                                int crownBottom = type.conical ? tt - type.canopyH + 1 : tt;
                                int segEnd = Math.min(Math.max(crownBottom, cursor), maxY);
                                if (segEnd > cursor) {
                                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, cursor, segEnd, logW, style.biome));
                                    cursor = segEnd;
                                }
                            }
                            // 冠段: 锥形 (距树心越远越窄底越高) / 块形 (干顶上 canopyH 厚悬空冠)
                            // [BUG·停用未修 2026-11-18] 块形 else 分支 cb/ct 与离心 dc 无关 = 全列同一 y 区间 = 立方体树冠;
                            // 修法 = 离心衰减推广 (dc=0 满厚, 边缘矮半截), 见上方树格块现状清单
                            int cb;
                            int ct;
                            if (type.conical) {
                                cb = tt - type.canopyH + 1 + dc;
                                ct = cb + Math.max(1, type.canopyH - 2 * dc);
                            } else {
                                cb = tt;
                                ct = tt + type.canopyH;
                            }
                            if (ct > maxY) {
                                ct = maxY;
                            }
                            if (ct > cursor && ct > solidTop) {
                                int segB = Math.max(cb, Math.max(cursor, solidTop));
                                if (segB < ct) {
                                    if (segB > cursor) { // 悬空冠下缝隙: 显式 air 段 (无缝平铺协议)
                                        points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, cursor, segB, airBlock, style.biome));
                                    }
                                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, segB, ct, leafW, style.biome));
                                    cursor = ct;
                                }
                            }
                        }
                    }
                    coverTop = cursor; // 结构顶 (尾段空气由下方公共 coverTop < maxY 分支写)
                }
                // 水段仅水列开火: 干列树冠抬高的 coverTop 不得触发水段 (否则树冠上叠同区间水膜);
                // 等效性: 旧代码 coverTop>solidTop 只能由两个水列分支造成 (均置 waterCol), 此门不改原有行为
                if (waterCol && coverTop > solidTop) {
                    points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, solidTop, coverTop, style.iceCover ? iceBlock : waterBlock, style.biome));
                }
                // ==== [L3] 结构段接管列顶 (C 批合并点; 设计稿 batch-c-design §2.6) ====
                // sseg.topY = 顶块上界 (半开) -> 相对列高 sTop = topY - minY; 高于当前覆盖面才发段:
                // 水下结构矮于水面 = 海面语义保持 (monument 视觉顶=海面); 埋地矮于 solidTop = 不可见, 均自然跳过
                LaneSegment sseg = structSegs[x * cols + z];
                if (sseg != null) {
                    int sTop = clampY(sseg.topY() - minY, dataBottom, maxY);
                    if (sTop > coverTop) {
                        IDhApiBlockStateWrapper sw = structWrapper(sseg.blockId());
                        if (sw != null) {
                            points.add(DhApiTerrainDataPoint.create(detailLevel, 0, 15, coverTop, sTop, sw, style.biome));
                            coverTop = sTop; // 尾段 air 自结构顶起 (DH 段堆叠不变量, 帽段事故教训同源)
                        }
                    }
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

    /** [L3] 结构方块 wrapper 懒取 (Optional 缓存: 失败只警一次不重试; 竞态重复构造等价无害). */
    private IDhApiBlockStateWrapper structWrapper(String blockId) {
        return structWrapperCache.computeIfAbsent(blockId, id -> {
            try {
                return Optional.ofNullable(DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(id, levelWrapper));
            } catch (Exception e) {
                LOG.warn("结构方块 wrapper 获取失败: {}: {}", id, e.toString());
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** splitmix64 弱哈希终轮: 树格/竹列确定性随机的充分混合 (无状态, 线程安全). */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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
            // [C] 河床强度分档方块 (与基本件同期懒取, 任一失败整体放弃本数据源)
            sandBlock = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper("minecraft:sand", levelWrapper);
            gravelBlock = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper("minecraft:gravel", levelWrapper);
            clayBlock = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper("minecraft:clay", levelWrapper);
            // 案②α: fallback 样式 = 纯几何模式旧路径 (表面=水下=cfg.surfaceBlock, 无冰盖; 无植被)
            fallbackStyle = new ColumnStyle(surfaceBlock, surfaceBlock, fillerBiome, false, null, null, null, null, null);
            return true;
        } catch (Exception e) {
            LOG.warn("wrapper 获取失败 (配置: surface={} biome={}): {}", cfg.surfaceBlock, cfg.fillerBiome, e.toString());
            return false;
        }
    }

    // ==== 案②α: 群系驱动的列表面样式 ====

    // ==== L1 花草色斑: 池结构与构建 (TdFlowerMap 数据 -> wrapper 化) [长期记忆: 124 数据源] ====

    /** 列顶色斑池: 方块 wrapper + 权重前缀和 (单条目失败 = 不收录; 空池 = null 不建). */
    private static final class VegPool {
        /** 池对齐的方块 wrapper. */
        final IDhApiBlockStateWrapper[] wrappers;
        /** 权重前缀和: prefix[i] = 前 i+1 条目权重累计. */
        final int[] prefix;
        /** 权重总和 (二次哈希取模域). */
        final int total;
        /** 列顶命中概率 (permille, 千分比). */
        final int permille;

        VegPool(IDhApiBlockStateWrapper[] wrappers, int[] prefix, int total, int permille) {
            this.wrappers = wrappers;
            this.prefix = prefix;
            this.total = total;
            this.permille = permille;
        }
    }

    /** 群系花草样式: 花/草两池 (纯几何模式/无花草群系/池全失败 = null). */
    private static final class FlowerStyle {
        /** 花池 (无花群系 = null). */
        final VegPool flowers;
        /** 草池 (留口: 数据照建, 消费 gate 在发射端 cfg.grassColoring). */
        final VegPool grasses;

        FlowerStyle(VegPool flowers, VegPool grasses) {
            this.flowers = flowers;
            this.grasses = grasses;
        }
    }

    /**
     * 构建列顶色斑池: 条目 wrapper 懒取, 单条目失败跳过 (观感少一种, 不连坐整池 — 与树池单型
     * 失败同策略); 无条目/概率 0/全失败 = null. 花池草池同构, 草池消费 gate 在发射端.
     */
    private VegPool buildVegPool(TdFlowerMap.Entry[] entries, int permille) {
        if (entries == null || entries.length == 0 || permille <= 0) {
            return null;
        }
        IDhApiBlockStateWrapper[] wrappers = new IDhApiBlockStateWrapper[entries.length];
        int[] prefix = new int[entries.length];
        int total = 0;
        int cnt = 0;
        for (TdFlowerMap.Entry entry : entries) {
            try {
                wrappers[cnt] = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(entry.state, levelWrapper);
                total += entry.weight;
                prefix[cnt] = total;
                cnt++;
            } catch (Exception we) {
                // 单条目 wrapper 失败 = 跳过该条目 (不连坐)
            }
        }
        if (cnt == 0) {
            return null;
        }
        // 尾部空槽收缩 (全限定免 import)
        return new VegPool(java.util.Arrays.copyOf(wrappers, cnt), java.util.Arrays.copyOf(prefix, cnt), total, permille);
    }
    /** 不可变列样式: 表面/水下方块 wrapper + 群系 wrapper + 冰盖标志 + L1 植被/花草参数. */
    private static final class ColumnStyle {
        final IDhApiBlockStateWrapper surface;
        final IDhApiBlockStateWrapper underwater;
        final IDhApiBiomeWrapper biome;
        final boolean iceCover;
        /** L1 植被统计参数 (无植被群系/纯几何模式 = null). */
        final TdVegetationMap.VegStat veg;
        /** 池对齐的干方块 wrapper (veg = null 时 null; 单型失败 = null, 该型树跳过). */
        final IDhApiBlockStateWrapper[] logs;
        /** 池对齐的冠方块 wrapper (与 logs 同索引). */
        final IDhApiBlockStateWrapper[] leaves;
        /** 竹方块 wrapper (veg.bamboo = false 时 null). */
        final IDhApiBlockStateWrapper bamboo;
        /** L1 花草色斑样式 (无花草群系/纯几何模式 = null; 草池数据照建, 消费 gate 在发射端). */
        final FlowerStyle flower;

        ColumnStyle(IDhApiBlockStateWrapper surface, IDhApiBlockStateWrapper underwater,
                    IDhApiBiomeWrapper biome, boolean iceCover,
                    TdVegetationMap.VegStat veg,
                    IDhApiBlockStateWrapper[] logs, IDhApiBlockStateWrapper[] leaves,
                    IDhApiBlockStateWrapper bamboo,
                    FlowerStyle flower) {
            this.surface = surface;
            this.underwater = underwater;
            this.biome = biome;
            this.iceCover = iceCover;
            this.veg = veg;
            this.logs = logs;
            this.leaves = leaves;
            this.bamboo = bamboo;
            this.flower = flower;
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
            // L1: 植被参数 (独立内层 try — 池 wrapper 失败仅退化该群系植被, 不连坐 style 本体)
            TdVegetationMap.VegStat veg = TdVegetationMap.vegetation(key);
            IDhApiBlockStateWrapper[] logs = null;
            IDhApiBlockStateWrapper[] leaves = null;
            IDhApiBlockStateWrapper bamboo = null;
            if (veg != null) {
                try {
                    logs = new IDhApiBlockStateWrapper[veg.pool.length];
                    leaves = new IDhApiBlockStateWrapper[veg.pool.length];
                    for (int pi = 0; pi < veg.pool.length; pi++) {
                        try {
                            logs[pi] = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(veg.pool[pi].type.log, levelWrapper);
                            leaves[pi] = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(veg.pool[pi].type.leaves, levelWrapper);
                        } catch (Exception ve) {
                            logs[pi] = null; // 单型失败 = 该型树跳过 (观感少一种树, 样式本体不受连坐)
                            leaves[pi] = null;
                        }
                    }
                    if (veg.bamboo) {
                        bamboo = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper("minecraft:bamboo", levelWrapper);
                    }
                } catch (Exception ve) {
                    logs = null; // 整池失败 = 观感无植被
                    leaves = null;
                }
            }
            // L1 花草色斑样式: 花池草池都建 (草池 = 留口数据, 消费 gate 在发射端 cfg.grassColoring);
            // 池失败仅退化该群系花草, 不连坐 style 本体 (与 veg 池同策略)
            FlowerStyle flowerStyle = null;
            TdFlowerMap.FlowerStat fs = TdFlowerMap.stat(key);
            if (fs != null) {
                VegPool flowerPool = buildVegPool(fs.flowers, fs.flowerPermille);
                VegPool grassPool = buildVegPool(fs.grasses, fs.grassPermille);
                if (flowerPool != null || grassPool != null) {
                    flowerStyle = new FlowerStyle(flowerPool, grassPool);
                }
            }
            ColumnStyle s = new ColumnStyle(
                    DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(surfaceId, levelWrapper),
                    DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(TdSurfaceBlockMap.underwater(key), levelWrapper),
                    DhApi.Delayed.wrapperFactory.getBiomeWrapper(key, levelWrapper),
                    TdSurfaceBlockMap.iceCover(key),
                    veg, logs, leaves, bamboo, flowerStyle);
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
        sandBlock = null;
        gravelBlock = null;
        clayBlock = null;
        fallbackStyle = null;
        styleCache.clear();
    }
}
