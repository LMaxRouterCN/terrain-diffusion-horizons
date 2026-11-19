package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

/**
 * 车道载荷标记接口 (B2; 设计稿 docs/batch-b-design.md §4.2/§4.3).
 *
 * <p>车道输出统一父型: B1 时代载荷只有方块段 (LaneSegment);
 * B2 结构撒点追加坐标事实 (StructureHit). sealed 收口 —
 * 载荷集合 = 编译期封闭, generate 合并点 (B3) 按 instanceof 分发,
 * 未知载荷类型直接编译错, 不留运行时兜底分支.</p>
 *
 * <p>载荷不碰 MC registry / DH wrapper (线程安全 + 与 DH 版本解耦,
 * 与 LaneSegment 同契约); 不可变 record 实现, 任意线程可传.</p>
 */
public sealed interface LanePayload permits LaneSegment, StructureHit {
}