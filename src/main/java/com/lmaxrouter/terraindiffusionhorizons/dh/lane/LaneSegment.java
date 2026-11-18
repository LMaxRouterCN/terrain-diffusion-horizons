package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

/**
 * 车道输出段 (B1 数据载体; 设计稿 §4.2).
 *
 * <p>方块用原版 ID 字符串: 车道核心纯函数不碰 MC registry / DH wrapper
 * (线程安全 + 与 DH 版本解耦); wrapper 转换在 generate 合并点执行 (B3).</p>
 *
 * @param blockX  段锚点世界块坐标 X
 * @param blockZ  段锚点世界块坐标 Z
 * @param bottomY 段底 y (含)
 * @param topY    段顶 y (不含; 与 DH data point 半开区间协议一致)
 * @param blockId 原版方块 ID (形如 "minecraft:sandstone")
 */
public record LaneSegment(int blockX, int blockZ, int bottomY, int topY, String blockId) {
}