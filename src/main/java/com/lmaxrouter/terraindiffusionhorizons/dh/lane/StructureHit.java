package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

/**
 * 结构撒点命中 (B2 载荷; 设计稿 §4.3 TdStructureOracle 的 L2 输出).
 *
 * <p>L2 层只承诺坐标事实: 该 chunk 是该结构集的 potential start chunk,
 * 与 vanilla 同 seed 同判 (数学直调 vanilla 本尊, 见 TdStructureSpreadCore).
 * 不含方块布局 (L3 组装 = C 批) 与视觉呈现 (设计稿 §6 甲/乙, 呈 max 裁决).</p>
 *
 * @param setName 结构集名 (json 文件名, 如 "villages"; 无命名空间后缀)
 * @param chunkX  potential start chunk X
 * @param chunkZ  potential start chunk Z
 */
public record StructureHit(String setName, int chunkX, int chunkZ) implements LanePayload {

    @Override
    public String toString() {
        // /locate 对照格式: locate 返回的是块坐标, 一并打出省一次心算
        return setName + "@chunk(" + chunkX + "," + chunkZ + ")=block(" + chunkX * 16 + "," + chunkZ * 16 + ")";
    }
}