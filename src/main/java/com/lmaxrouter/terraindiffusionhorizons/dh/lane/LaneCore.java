package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

import java.util.List;

/**
 * 车道计算核心 — 纯函数接口 (B1 骨架, B2 载荷化; 设计稿 docs/batch-b-design.md §2).
 *
 * <p>契约: 无状态、无副作用、无锁; 一切随机性由 worldSeed 派生,
 * 相同输入恒返回相同输出. 任意线程可调 (generate 线程同步内联 / 车道线程异步).
 * 抛异常 = 该 region 整体进黑名单, 由 LaneManager 隔离, 不连坐 [长期记忆: 118].</p>
 *
 * <p>坐标语义 [已校准 2026-11-19]: posX/posZ = 数据源原点的 section 网格坐标
 * (首调日志 pos=(13,15) × 64 = chunkMin(52,60) 实证; 数据源块边长 = 2^(detailLevel+6)).
 * B2 首个载荷 (结构撒点) 已按此换算落位.</p>
 *
 * <p>载荷: B2 起 compute 返回 LanePayload (sealed — LaneSegment 方块段 / StructureHit
 * 结构坐标事实), generate 合并点 (B3) 按类型分发.</p>
 */
public interface LaneCore {

    /**
     * @param worldSeed   世界种子 (同源前提)
     * @param detailLevel API 档位 (dl0 = 64 块/列)
     * @param posX        数据源原点 section 网格坐标 X
     * @param posZ        数据源原点 section 网格坐标 Z
     * @return 该区域内的车道载荷 (空列表 = 无内容, 不是错误)
     */
    List<LanePayload> compute(long worldSeed, byte detailLevel, int posX, int posZ) throws Exception;
}