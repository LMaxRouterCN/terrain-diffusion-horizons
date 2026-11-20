package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneCore;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LanePayload;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.StructureHit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结构撒点核心 (B2 载荷; 设计稿 docs/batch-b-design.md §4.3, 种子链实证 [长期记忆: 115]).
 *
 * <p>[L3 扩展 2026-11-21] compute 的界计算抽为 public computeRange (chunk 双闭界):
 * 撒点行为零变; 组装核心 (TdStructureAssembleCore) 以外扩扫描界调用得 hits.</p>
 * <p><b>零复刻原则</b>: 撒点数学直调 vanilla 本尊 (WorldgenRandom / LegacyRandomSource /
 * setLargeFeatureWithSalt / setLargeFeatureSeed / RandomSpreadType.evaluate 全 public),
 * 341873128712 一类混合常量一行不抄 — 本类只写编排层 (格枚举 / 频率分发 / 排除区循环),
 * 全部语义逐指令对照 1.20.1 字节码 (2026-11-19 六轮 javap, recompile jar)。
 * 因此 /locate 对照从"验实现"降为"验参数读取"。</p>
 *
 * <p>坐标换算 [B2 校准点已核]: posX/posZ = 数据源原点 section 网格坐标 (首调日志实证),
 * 块边长 = 2^(detailLevel+6), chunk 边长 = 2^(detailLevel+2)。</p>
 *
 * <p>已知限制 (B2 声明): 排除区的对方集合不在白名单表时放行 (vanilla 实况
 * outposts→villages 双白名单, 真实场景无折损); 排除区递归深度上限 8 (防御性偏离 —
 * vanilla 无环校验依赖 registry codec, 自定义 json 成环会 StackOverflow, 到顶判禁区
 * fail-safe 宁少报不多报, /locate 包含性协议的安全侧)。</p>
 */
public final class TdStructureSpreadCore implements LaneCore {

    public static final TdStructureSpreadCore INSTANCE = new TdStructureSpreadCore();

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Oracle");

    /** 排除区递归深度上限 (防御性偏离, 见类 javadoc). */
    private static final int MAX_EXCLUSION_DEPTH = 8;

    /** 参数表快照 (DhCompat 装配 / 清空; volatile 安全发布, compute 侧只读). */
    private volatile Map<String, StructureSetParams> params = Map.of();

    private TdStructureSpreadCore() {
    }

    /** 装配参数表 (LevelLoad 时 DhCompat 调; 拷贝隔离, 此后与 json 读取再无关系). */
    public void updateParams(Map<String, StructureSetParams> table) {
        this.params = Map.copyOf(table);
    }

    /** 清参数 (LevelUnload 时 DhCompat 调; 空表 = compute 短路返回空, 车道零载荷). */
    public void clearParams() {
        this.params = Map.of();
    }

    @Override
    public List<LanePayload> compute(long worldSeed, byte detailLevel, int posX, int posZ) {
        // region chunk 界 (含端点) — 界计算抽 computeRange 后的纯转发, 撒点行为零变 (B2 冒烟不回归)
        int shift = detailLevel + 2; // 数据源边长 2^(dl+6) 块 = 2^(dl+2) chunk
        int chunkMinX = posX << shift;
        int chunkMinZ = posZ << shift;
        return new ArrayList<>(computeRange(worldSeed, chunkMinX, chunkMinZ,
                chunkMinX + (1 << shift) - 1, chunkMinZ + (1 << shift) - 1));
    }

    /**
     * 界定撒点: chunk 双闭界 [min, max] 内的全部命中 (含端点).
     * L3 组装核心的输入 — 界 = region 界外扩 padChunks 的扫描界 (设计稿 batch-c-design §3.5);
     * 抽取自原 compute, 枚举与判定逻辑零改动.
     */
    public List<StructureHit> computeRange(long worldSeed, int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
        Map<String, StructureSetParams> table = params;
        if (table.isEmpty()) {
            return List.of(); // 未装配 (非 TD 世界 / 装配失败): 静默空载荷, 装配日志在 DhCompat
        }
        List<StructureHit> hits = new ArrayList<>();
        for (Map.Entry<String, StructureSetParams> e : table.entrySet()) {
            StructureSetParams p = e.getValue();
            int sp = p.spacing();
            // 格枚举界: potential = g*sp + off, off ∈ [0, sp) -> 覆盖扫描界的格在
            // [floorDiv(min-(sp-1), sp), floorDiv(max, sp)]; 各放宽 1 格做安全垫 (代价微忽)
            int gMinX = Math.floorDiv(chunkMinX, sp) - 1;
            int gMaxX = Math.floorDiv(chunkMaxX, sp) + 1;
            int gMinZ = Math.floorDiv(chunkMinZ, sp) - 1;
            int gMaxZ = Math.floorDiv(chunkMaxZ, sp) + 1;
            for (int gx = gMinX; gx <= gMaxX; gx++) {
                for (int gz = gMinZ; gz <= gMaxZ; gz++) {
                    ChunkPos pc = potentialChunk(p, worldSeed, gx, gz);
                    if (pc.x < chunkMinX || pc.x > chunkMaxX || pc.z < chunkMinZ || pc.z > chunkMaxZ) {
                        continue; // 该格 potential 落在扫描界外
                    }
                    // potential 命中由构造保证; 完整判定 (频率+排除区) 走同一 helper,
                    // 与排除区递归共用单一代码路径 = 语义与 vanilla 逐点一致
                    if (isStructureChunk(p, worldSeed, pc.x, pc.z, table, MAX_EXCLUSION_DEPTH)) {
                        hits.add(new StructureHit(e.getKey(), pc.x, pc.z));
                    }
                }
            }
        }
        return hits;
    }

    /** vanilla isStructureChunk 完整三段: potential 命中 -> 频率 -> 排除区 [字节码实证]. */
    private boolean isStructureChunk(StructureSetParams p, long seed, int x, int z,
                                     Map<String, StructureSetParams> table, int depth) {
        int gx = Math.floorDiv(x, p.spacing());
        int gz = Math.floorDiv(z, p.spacing());
        ChunkPos pc = potentialChunk(p, seed, gx, gz);
        if (pc.x != x || pc.z != z) {
            return false;
        }
        if (!frequencyPass(p, seed, x, z)) {
            return false;
        }
        return !isExcluded(p, seed, x, z, table, depth);
    }

    /** vanilla getPotentialStructureChunk 逐指令镜像 [字节码实证; 常量零抄写]. */
    private ChunkPos potentialChunk(StructureSetParams p, long seed, int gx, int gz) {
        WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
        wr.setLargeFeatureWithSalt(seed, gx, gz, p.salt());
        int range = p.spacing() - p.separation();
        RandomSpreadType type = p.triangular() ? RandomSpreadType.TRIANGULAR : RandomSpreadType.LINEAR;
        int ox = type.evaluate(wr, range); // X 先 (求值序 = 字节码 istore 9 -> 10)
        int oz = type.evaluate(wr, range); // Z 后 (同一随机流连续消费)
        return new ChunkPos(gx * p.spacing() + ox, gz * p.spacing() + oz);
    }

    /**
     * 四频率法逐指令镜像 [字节码实证]. 两个容易抄错点:
     * ① default 的实参序 setLargeFeatureWithSalt(seed, salt, x, z) — 盐占 gridX 槽
     *    (vanilla 源码即此形状, 非笔误); ② legacy_type_3 的盐是硬编码 10387320
     *    (vanilla 常量, json salt 不参与 — 唯一进代码的 vanilla 数字, 字节码出处)。
     * 未知 method 放行: superset 偏置, /locate 包含性协议的安全侧。
     */
    private boolean frequencyPass(StructureSetParams p, long seed, int x, int z) {
        float freq = p.frequency();
        if (freq >= 1.0f) {
            return true;
        }
        switch (p.frequencyMethod()) {
            case "default" -> {
                WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
                wr.setLargeFeatureWithSalt(seed, p.salt(), x, z);
                return wr.nextFloat() < freq;
            }
            case "legacy_type_1" -> {
                WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
                wr.setSeed((long) ((x >> 4) ^ ((z >> 4) << 4)) ^ seed);
                wr.nextInt(); // 弃一个 [字节码 pop]
                return wr.nextInt((int) (1.0f / freq)) == 0;
            }
            case "legacy_type_2" -> {
                WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
                wr.setLargeFeatureSeed(seed, x, z);
                return wr.nextDouble() < freq;
            }
            case "legacy_type_3" -> {
                WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
                wr.setLargeFeatureWithSalt(seed, x, z, 10387320);
                return wr.nextFloat() < freq;
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * 排除区: 双闭方 [x-c,x+c]×[z-c,z+c] 逐点查对方集合的完整 isStructureChunk
     * (含对方自己的频率与排除区 = 递归) [字节码实证 hasStructureChunkInRange]。
     */
    private boolean isExcluded(StructureSetParams p, long seed, int x, int z,
                               Map<String, StructureSetParams> table, int depth) {
        String other = p.exclusionOtherSet();
        if (other == null) {
            return false;
        }
        StructureSetParams otherP = table.get(other);
        if (otherP == null) {
            return false; // 对方集合不在白名单表 = 放行 [类 javadoc 已知限制]
        }
        if (depth <= 0) {
            LOG.warn("排除区递归过深 (疑似 json 成环), 判禁区跳过该 chunk: {}", other);
            return true; // fail-safe: 宁少报不多报
        }
        int c = p.exclusionChunkCount();
        for (int ex = x - c; ex <= x + c; ex++) {
            for (int ez = z - c; ez <= z + c; ez++) {
                if (isStructureChunk(otherP, seed, ex, ez, table, depth - 1)) {
                    return true;
                }
            }
        }
        return false;
    }
}