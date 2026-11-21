package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

/**
 * 车道方块段 (B1 数据载体; B2 起挂 LanePayload; 设计稿 §4.2).
 *
 * <p>方块用原版 ID 字符串: 车道核心纯函数不碰 MC registry / DH wrapper
 * (线程安全 + 与 DH 版本解耦); wrapper 转换在 generate 合并点执行 (B3).</p>
 *
 * @param blockX  段锚点世界块坐标 X
 * @param blockZ  段锚点世界块坐标 Z
 * @param bottomY 段底 y (含)
 * @param topY    段顶 y (不含; 与 DH data point 半开区间协议一致)
 * @param blockId 原版方块 ID (形如 "minecraft:sandstone")
 * @param surfaceHint 列级事实: 该列位于 beard 系结构 (terrain_adaptation = beard_thin/
 *                   beard_box) XZ 投影内 — 原版放置挖空 bbox 上覆 (路顶必然露天),
 *                   TD 高度场不知此挖掘 → 假埋深; 消费端对 hint 列保留 blendDepth
 *                   窗口吸收, none/bury 列埋深即真埋 [长期记忆: 156]
 */
public record LaneSegment(int blockX, int blockZ, int bottomY, int topY, String blockId,
                          boolean surfaceHint) implements LanePayload {
}
