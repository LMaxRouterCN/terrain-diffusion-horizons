package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 群系装饰循环复刻 (L4; 设计稿 docs/batch-l4-design.md §3.2).
 *
 * <p>vanilla ChunkGenerator.applyBiomeDecoration 的 feature 半边逐指令复刻
 * (结构半边 = TdStructureAssembleCore 已有): 每 chunk setDecorationSeed ->
 * 配置步集过滤 -> biome 集三邻域并集 -> 步表全局索引并集 sort ->
 * setFeatureSeed(dec, 全局索引, 步序) -> placeWithBiomeCheck(TdDecorateLevelView).
 * 步表 = Snapshot.featureSteps (装配期 FeatureSorter.buildFeaturesPerStep,
 * 与 vanilla ChunkGenerator 构造 L93 memoize 逐参同源).</p>
 *
 * <p><b>折损 (设计稿 §6 已登记)</b>: 只跑配置步集 (默认 A = vegetal +
 * top_layer); 单 feature 异常 warn 不杀 chunk (vanilla 上抛 CrashReport,
 * LOD 容错语义); biome 集查询走 per-chunk quart 全采 distinct (值等价
 * vanilla 邻域 palette 并集, biome链v3 实证).</p>
 */
public final class TdBiomeDecorator {

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Structure");

    /**
     * 装饰界外扩 (chunk): 溢出护栏 — 3x3 邻域 biome 查询 + feature 越界放置吸收.
     * 几何依据: 最大树冠半径 ~9 格 (mangrove/giant 系) << 32 格 = 2 chunk.
     */
    private static final int DECORATION_PAD_CHUNKS = 2;

    private final TdStructureContext.Snapshot ctx;
    private final TdDecorateLevelView view;
    /** per-chunk biome distinct 集 (quart 全采一次; 3x3 邻域消费, ~10x10 范围复用). */
    private final Map<Long, Set<Holder<Biome>>> chunkBiomeCache = new HashMap<>();
    /** 观察期统计: feature 放置尝试次数. */
    private int featureCalls;
    /** [L4·方案3] feature 异常签名 -> 次数 (签名 = 异常类@步:全局索引; 首条全栈余只计数). */
    private final Map<String, Integer> warnCounts = new HashMap<>();
    /** [L4·方案3] feature 异常总次数 (region 末汇总日志). */
    private int warnTotal;

    private TdBiomeDecorator(TdStructureContext.Snapshot ctx, TdDecorateLevelView view) {
        this.ctx = ctx;
        this.view = view;
    }

    /**
     * region 装饰入口 (AssembleCore 调; 必须在 far-lookup 会话内):
     * 界 = region chunk 界 +- DECORATION_PAD_CHUNKS, View 的 writableBox 同界.
     *
     * @param waterData [L4·排障·方案2] prefetch 的装饰窗 heightmap (含 fluvial 水数组),
     *                  View 三态水背景源; null (关/失败) = View 回退旧两态.
     */
    public static void decorateRegion(TdStructureContext.Snapshot ctx, TdSilentLevel level,
                                      long worldSeed, int rMinCx, int rMinCz, int rMaxCx, int rMaxCz,
                                      HeightmapData waterData) {
        int minCx = rMinCx - DECORATION_PAD_CHUNKS;
        int minCz = rMinCz - DECORATION_PAD_CHUNKS;
        int maxCx = rMaxCx + DECORATION_PAD_CHUNKS;
        int maxCz = rMaxCz + DECORATION_PAD_CHUNKS;
        BoundingBox writable = new BoundingBox(
                minCx << 4, level.getMinBuildHeight(), minCz << 4,
                (maxCx << 4) + 15, level.getMaxBuildHeight(), (maxCz << 4) + 15);
        // 数据窗原点与 writable 同界 (prefetch 同公式), View 查表 dataZ = z - minCz<<4
        TdDecorateLevelView view = new TdDecorateLevelView(level, writable, waterData,
                minCz << 4, minCx << 4);
        TdBiomeDecorator dec = new TdBiomeDecorator(ctx, view);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                dec.decorateChunk(worldSeed, cx, cz);
            }
        }
        if (dec.featureCalls > 0) { // 观察期日志 (TEST 后降 debug)
            LOG.info("[decorate] chunks[({},{})..({},{})] feature 尝试 {} 次, 背景 {}",
                    minCx, minCz, maxCx, maxCz, dec.featureCalls,
                    view.hasWaterBackground() ? "三态(水)" : "两态(回退)");
        }
        // [L4·方案3] warn 去重: 同签名 (异常类@步:全局索引) 首条全栈, 重复只计数, region 末汇总
        if (dec.warnTotal > 0) {
            LOG.warn("[decorate] region[({},{})..({},{})] feature 异常汇总: {} 次 / {} 签名 {}",
                    minCx, minCz, maxCx, maxCz, dec.warnTotal, dec.warnCounts.size(), dec.warnCounts);
        }
    }

    /**
     * [L4·排障·方案1] 装饰窗 heightmap prefetch — 必须在 far-lookup 会话外调用
     * (AssembleCore 于 beginFarLookup 前; 会话内冷 tile 只出近似数据且无水数组).
     * 冷 tile 在此走真推理并驻留 (CACHE/hydrology), 装饰期 mixin 高度查询全走 resident
     * 纯拷贝 — 修方格错树 (近似高度场两值交替跨树位; vanilla 距离消失 = 渲染权顶替
     * 非修复, 冷 tile 不驻留为根因). 返回值兼作方案 2 水背景燃料; null = 调用方回退旧路径.
     */
    public static HeightmapData prefetchDecorationHeightmap(
            int rMinCx, int rMinCz, int rMaxCx, int rMaxCz) {
        if (!TdConfig.get().decorationPrefetch) return null;
        // 装饰窗 (与 decorateRegion 同式, DECORATION_PAD_CHUNKS 单点), 上界排他
        int z0 = (rMinCz - DECORATION_PAD_CHUNKS) << 4;
        int x0 = (rMinCx - DECORATION_PAD_CHUNKS) << 4;
        int z1e = (rMaxCz + DECORATION_PAD_CHUNKS + 1) << 4;
        int x1e = (rMaxCx + DECORATION_PAD_CHUNKS + 1) << 4;
        try {
            // fetchHeightmap(i=Z 低, j=X 低, i=Z 高排他, j=X 高排他) — RiverDecorator 同式
            return LocalTerrainProvider.getInstance().fetchHeightmap(z0, x0, z1e, x1e);
        } catch (Exception e) {
            // LOD 容错: 预取失败不杀车道, 装饰回退旧近似路径
            LOG.warn("[decorate] heightmap prefetch 失败 region[({},{})..({},{})]: {}",
                    rMinCx, rMinCz, rMaxCx, rMaxCz, e.toString());
            return null;
        }
    }

    /** 单 chunk 装饰循环 (vanilla applyBiomeDecoration L278-357 feature 半边逐指令). */
    private void decorateChunk(long worldSeed, int cx, int cz) {
        // L278-279: 播种姿态同构 (构造态即弃, setDecorationSeed 首行 setSeed 全量重置)
        WorldgenRandom wr = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long dec = wr.setDecorationSeed(worldSeed, cx << 4, cz << 4);
        // L280-288: biome 集 = 3x3 邻域并集 (ChunkPos.rangeClosed(chunk, 1) 同构展开)
        Set<Holder<Biome>> biomeSet = new HashSet<>();
        for (int ncx = cx - 1; ncx <= cx + 1; ncx++) {
            for (int ncz = cz - 1; ncz <= cz + 1; ncz++) {
                biomeSet.addAll(chunkBiomeSet(ncx, ncz));
            }
        }
        // L289: possibleBiomes 交集 (datapack 防御, vanilla 原样)
        biomeSet.retainAll(ctx.generator().getBiomeSource().possibleBiomes());
        // L271-272: origin = minSection 原点 (blockpos)
        BlockPos origin = new BlockPos(cx << 4, view.getMinBuildHeight(), cz << 4);
        List<FeatureSorter.StepFeatureData> steps = ctx.featureSteps();
        for (int k : ctx.decorationSteps()) { // 配置步集 (ordinal 升序, EnumSet 迭代序)
            if (k >= steps.size()) {
                continue; // 步表短于步序号 = 该世界无此步 feature (vanilla k < j 守卫同构)
            }
            FeatureSorter.StepFeatureData stepData = steps.get(k);
            // L321-332: biome 集内全部 feature 的步表全局索引并集
            IntSet intset = new IntArraySet();
            for (Holder<Biome> holder : biomeSet) {
                List<HolderSet<PlacedFeature>> l1 = ctx.generator().getBiomeGenerationSettings(holder).features();
                if (k < l1.size()) {
                    for (Holder<PlacedFeature> h : l1.get(k)) {
                        intset.add(stepData.indexMapping().applyAsInt(h.value()));
                    }
                }
            }
            int[] aint = intset.toIntArray();
            Arrays.sort(aint); // 全局索引升序 = 步表序 = 跨 chunk 确定性
            for (int l1i : aint) {
                PlacedFeature pf = stepData.features().get(l1i);
                wr.setFeatureSeed(dec, l1i, k); // L345: (dec, 全局索引, 步序)
                featureCalls++;
                try {
                    pf.placeWithBiomeCheck(view, ctx.generator(), wr, origin);
                } catch (Exception e) {
                    // LOD 容错: 单 feature 异常不杀 chunk (vanilla 上抛)
                    // [L4·方案3] 刷屏去重: 同签名 (异常类@步:全局索引) 首条全栈, 重复只计数;
                    // region 末汇总见 decorateRegion (cocoa 每 chunk 一条全栈的刷屏根因)
                    String sig = e.getClass().getSimpleName() + "@" + k + ":" + l1i;
                    if (warnCounts.merge(sig, 1, Integer::sum) == 1) {
                        LOG.warn("[decorate] feature 异常 chunk({},{}) step {} idx {}: {}",
                                cx, cz, k, l1i, e.toString(), e);
                    }
                    warnTotal++;
                }
            }
        }
    }

    /**
     * per-chunk quart 全采 distinct (4x4x96 = 1536 采一次, 邻域共享).
     * 值等价 vanilla 邻域 palette 并集 (biome链v3: palette = quart TDBS 灌值).
     */
    private Set<Holder<Biome>> chunkBiomeSet(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        Set<Holder<Biome>> cached = chunkBiomeCache.get(key);
        if (cached != null) {
            return cached;
        }
        Set<Holder<Biome>> set = new HashSet<>();
        int qx0 = cx << 2; // chunk -> quart
        int qz0 = cz << 2;
        int qyMin = view.getMinBuildHeight() >> 2;
        int qyMax = view.getMaxBuildHeight() >> 2;
        for (int qy = qyMin; qy < qyMax; qy++) {
            for (int qx = 0; qx < 4; qx++) {
                for (int qz = 0; qz < 4; qz++) {
                    set.add(view.getNoiseBiome(qx0 + qx, qy, qz0 + qz));
                }
            }
        }
        chunkBiomeCache.put(key, set);
        return set;
    }
}