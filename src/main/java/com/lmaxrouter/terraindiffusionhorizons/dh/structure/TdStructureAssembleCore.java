package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneCore;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LanePayload;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneSegment;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.StructureHit;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 结构组装核心 (L3; 设计稿 docs/batch-c-design.md §2.4/§3).
 *
 * <p>数据流: 撒点命中 (B2 SpreadCore, 外扩扫描界) -> 消元循环选 entry ->
 * structure.generate 十参复刻 (谓词 = structure.biomes()::contains, 高度走
 * far-lookup 捷径与 /locate 同链) -> placeInChunk 在 TdSilentLevel 上写真方块
 * (ProtoChunk 内存后端) -> 列折叠成 LaneSegment (region 界 clip).</p>
 *
 * <p><b>随机流 parity</b>: 消元循环与 generate 内部 (GenerationContext 九参便捷
 * 构造自建 random, Structure.java L164/L168 实证) 均为 (seed, chunk) 确定流;
 * placeInChunk 侧每 chunk setDecorationSeed(worldSeed, cx*16, cz*16) +
 * setFeatureSeed(dec, 0, step) — 与 vanilla applyBiomeDecoration 同式
 * [WorldgenRandom L47 实证]. 已知折损: 同 chunk 同 step 双结构 l 序错位
 * (频率极低, 设计稿 §6 登记).</p>
 *
 * <p><b>上下文注入</b>: B1 "核心不碰 registry" 原则在 L3 破戒 — 活对象经
 * TdStructureContext.Snapshot 注入 (装配期解析, 车道线程只读).</p>
 */
public final class TdStructureAssembleCore implements LaneCore {

    public static final TdStructureAssembleCore INSTANCE = new TdStructureAssembleCore();

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Structure");

    private TdStructureAssembleCore() {
    }

    @Override
    public List<LanePayload> compute(long worldSeed, byte detailLevel, int posX, int posZ) {
        TdStructureContext.Snapshot ctx = TdStructureContext.get();
        if (ctx == null) {
            return List.of(); // 未装配 (非 TD 世界 / 装配失败): 静默空载荷
        }
        // region chunk 界 (与 SpreadCore.compute 同式; 数据源边长 2^(dl+2) chunk)
        int shift = detailLevel + 2;
        int rMinX = posX << shift;
        int rMinZ = posZ << shift;
        int rMaxX = rMinX + (1 << shift) - 1;
        int rMaxZ = rMinZ + (1 << shift) - 1;

        // 扫描界 = region 界外扩 padChunks (bbox 跨界的结构也收; 产出只 clip region 内列)
        int pad = TdConfig.get().structurePadChunks;
        List<StructureHit> hits = TdStructureSpreadCore.INSTANCE.computeRange(worldSeed,
                rMinX - pad, rMinZ - pad, rMaxX + pad, rMaxZ + pad);
        if (hits.isEmpty()) {
            return List.of();
        }

        TdSilentLevel level = new TdSilentLevel(ctx);
        List<LanePayload> segments = new ArrayList<>();
        // far-lookup 会话: 高度采样走 coarse 捷径 (ThreadLocal 计数器, 车道线程自洽
        // [LocalTerrainProvider 实证]; 与 /locate 结构搜索同机制)
        LocalTerrainProvider.beginFarLookup();
        try {
            int assembled = 0;
            for (StructureHit hit : hits) {
                List<TdStructureContext.ResolvedEntry> entries = ctx.entriesTable().get(hit.setName());
                if (entries == null) {
                    continue; // 集合全 miss 已在装配期跳过, 双保险
                }
                if (assemble(ctx, entries, worldSeed, hit, level) != null) {
                    assembled++;
                }
            }
            if (assembled > 0) {
                foldColumns(level,
                        rMinX << 4, rMinZ << 4, (rMaxX << 4) + 15, (rMaxZ << 4) + 15, segments);
                LOG.info("[assemble] region dl{} +({},{}) -> {} 命中 / {} 组装 / {} 列段",
                        detailLevel, posX, posZ, hits.size(), assembled, segments.size());
            }
        } finally {
            LocalTerrainProvider.endFarLookup();
        }
        return segments;
    }

    /** 单 hit 组装: 消元循环选 entry -> generate -> placeInChunk. 失败返回 null. */
    private StructureStart assemble(TdStructureContext.Snapshot ctx, List<TdStructureContext.ResolvedEntry> entries,
                                    long seed, StructureHit hit, TdSilentLevel level) {
        // biomeSource: vanilla tryGenerateStructure 用 generator 的字段, 此处同源直取
        BiomeSource biomeSource = ctx.generator().getBiomeSource();
        Structure chosen;
        if (entries.size() == 1) {
            // 单 entry: vanilla 同款无随机直调分支 (createStructures 实证)
            Structure s = entries.get(0).structure();
            chosen = tryGenerate(ctx, biomeSource, s, seed, hit.chunkX(), hit.chunkZ()) != null ? s : null;
        } else {
            chosen = elimination(ctx, biomeSource, entries, seed, hit.chunkX(), hit.chunkZ());
        }
        if (chosen == null) {
            return null;
        }
        StructureStart start = tryGenerate(ctx, biomeSource, chosen, seed, hit.chunkX(), hit.chunkZ());
        if (start == null || !start.isValid()) {
            return null;
        }
        // placeInChunk: bbox 逐 chunk (vanilla applyBiomeDecoration 结构步复刻, 设计稿 §3.3)
        BoundingBox bb = start.getBoundingBox();
        for (int cz = bb.minZ() >> 4; cz <= bb.maxZ() >> 4; cz++) {
            for (int cx = bb.minX() >> 4; cx <= bb.maxX() >> 4; cx++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                // 每 chunk 独立确定流: setDecorationSeed 覆盖初始种子 [WorldgenRandom L47 实证]
                WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L)); // 构造态即弃 (setDecorationSeed 首行 setSeed 全量重置); 0L 与 vanilla makeRandom 同姿态, 确定性无歧义
                long dec = wr.setDecorationSeed(seed, cx << 4, cz << 4);
                wr.setFeatureSeed(dec, 0, chosen.step().ordinal()); // l=0: 同 chunk 同 step 双结构折损已登记
                BoundingBox writable = new BoundingBox(cp.getMinBlockX(), ctx.level().getMinBuildHeight(),
                        cp.getMinBlockZ(), cp.getMaxBlockX(), ctx.level().getMaxBuildHeight(), cp.getMaxBlockZ());
                try {
                    // structureManager: postProcess 侧几不用 (设计稿 §2 定案), null 填坑 crash 驱动
                    start.placeInChunk(level, null, ctx.generator(), wr, writable, cp);
                } catch (Exception e) {
                    // crash 驱动信号: 缺面/哑数据异常打全栈 (TEST 观察期); 半组装 chunk 保留继续折叠
                    LOG.warn("[assemble] placeInChunk 失败 {}@({},{}) chunk({},{})",
                            chosen, hit.chunkX(), hit.chunkZ(), cx, cz, e);
                }
            }
        }
        return start;
    }

    /** 消元循环 (mojmap createStructures 逐指令, 设计稿 §3.1). */
    private Structure elimination(TdStructureContext.Snapshot ctx, BiomeSource biomeSource,
                                  List<TdStructureContext.ResolvedEntry> entries, long seed, int cx, int cz) {
        List<TdStructureContext.ResolvedEntry> list = new ArrayList<>(entries);
        WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
        wr.setLargeFeatureSeed(seed, cx, cz); // 一次播种, 失败重抽同流继续
        int total = 0;
        for (TdStructureContext.ResolvedEntry e : list) {
            total += e.weight();
        }
        while (!list.isEmpty()) {
            int j = wr.nextInt(total); // [0, total)
            int k = 0;
            for (TdStructureContext.ResolvedEntry e : list) {
                j -= e.weight();
                if (j < 0) {
                    break;
                }
                ++k;
            }
            TdStructureContext.ResolvedEntry chosen = list.get(k);
            StructureStart s = tryGenerate(ctx, biomeSource, chosen.structure(), seed, cx, cz);
            if (s != null && s.isValid()) {
                return chosen.structure();
            }
            total -= chosen.weight();
            list.remove(k);
        }
        return null;
    }

    /** tryGenerateStructure 复刻 (定义体全文实证, 设计稿 §3.2); 无效返回 INVALID_START (非 null). */
    private StructureStart tryGenerate(TdStructureContext.Snapshot ctx, BiomeSource biomeSource,
                                       Structure structure, long seed, int cx, int cz) {
        Predicate<Holder<Biome>> pred = structure.biomes()::contains; // BF 滤: TDBS Holder 对 registry 集
        // references=0: 离线新 chunk fetchReferences 恒 0 [C1 §1]; 第十参 = LevelHeightAccessor [L82 实证]
        return structure.generate(ctx.registryAccess(), ctx.generator(), biomeSource, ctx.randomState(),
                ctx.templateManager(), seed, new ChunkPos(cx, cz), 0, ctx.level(), pred);
    }

    /** 列折叠: 每列多段切分 -> LaneSegment 链 (C-light; 半开 [bottom, top+1)), clip region 界. */
    private void foldColumns(TdSilentLevel level, int minBlockX, int minBlockZ,
                             int maxBlockX, int maxBlockZ, List<LanePayload> out) {
        for (ProtoChunk chunk : level.chunks()) {
            ChunkPos p = chunk.getPos();
            LevelChunkSection[] sections = chunk.getSections();
            for (int x = 0; x < 16; x++) {
                int wx = (p.x << 4) + x;
                if (wx < minBlockX || wx > maxBlockX) {
                    continue; // region 界外列: 不产出
                }
                for (int z = 0; z < 16; z++) {
                    int wz = (p.z << 4) + z;
                    if (wz < minBlockZ || wz > maxBlockZ) {
                        continue;
                    }
                    columnTop(level, sections, wx, wz, out);
                }
            }
        }
    }

    /**
     * 单列多段折叠 (C-light): 自顶向下单趟, blockId 变化处切段, 空气/structure_void 闭合段;
     * 段数上限可配 (structureColumnSegments, 默认 6; =1 恰好复现旧单段行为 = 配置级回滚).
     *
     * <p>超限吞底: 名额用尽时当前段吸收后续异块延伸到列底 (块色保持段首块, 高度不浮空);
     * 已被空气闭合后名额已满的后续内容丢弃 (极端深结构兜底, 默认值下罕见).
     * 空 section = 16 格空气, 同样闭合 (塔尖与柱基同 id 不得跨空 section 误并段).</p>
     */
    private void columnTop(TdSilentLevel level, LevelChunkSection[] sections, int wx, int wz, List<LanePayload> out) {
        final int limit = Math.max(1, TdConfig.get().structureColumnSegments); // >=1 防御 (0/负 = 退单段)
        Block runBlock = null; // 当前段块单例 (== 比较; id 仅 emit 时取, 每段一次 getKey)
        int runTop = 0;       // 段顶 y (开段时定格; 半开区间 [runBottom, runTop+1))
        int runBottom = 0;    // 段底 y (同块/吞底向下延伸)
        int segCount = 0;     // 已 emit 段数
        for (int si = sections.length - 1; si >= 0; si--) {
            LevelChunkSection s = sections[si];
            if (s == null || s.hasOnlyAir()) {
                if (runBlock != null) { // 空 section: 闭合段
                    out.add(new LaneSegment(wx, wz, runBottom, runTop + 1,
                            BuiltInRegistries.BLOCK.getKey(runBlock).toString()));
                    segCount++;
                    runBlock = null;
                }
                continue;
            }
            for (int y = 15; y >= 0; y--) {
                BlockState st = s.getBlockState(wx & 15, y, wz & 15);
                Block blk = st.getBlock();
                int yAbs = level.getMinBuildHeight() + (si << 4) + y; // section 底自算 [1.20.1 无 bottomBlockY 字段]
                boolean airLike = st.isAir() || blk == Blocks.STRUCTURE_VOID; // void 按 air 防御过滤 (落盘与否未实证)
                // 闭合: 空气 / 异块且还有切换名额 (闭合后可开新段)
                if (runBlock != null && (airLike || (blk != runBlock && segCount + 1 < limit))) {
                    out.add(new LaneSegment(wx, wz, runBottom, runTop + 1,
                            BuiltInRegistries.BLOCK.getKey(runBlock).toString()));
                    segCount++;
                    runBlock = null;
                }
                if (!airLike) {
                    if (runBlock == null) { // 开新段 (名额已满 = 封顶后内容, 丢弃)
                        if (segCount < limit) {
                            runBlock = blk;
                            runTop = yAbs;
                            runBottom = yAbs;
                        }
                    } else { // 吞: 同块延伸 / 异块但名额用尽 (吞底, 块色保持段首)
                        runBottom = yAbs;
                    }
                }
            }
        }
        if (runBlock != null) { // 列底 flush: 开放段 (吞底态延伸到列底)
            out.add(new LaneSegment(wx, wz, runBottom, runTop + 1,
                    BuiltInRegistries.BLOCK.getKey(runBlock).toString()));
        }
    }
}