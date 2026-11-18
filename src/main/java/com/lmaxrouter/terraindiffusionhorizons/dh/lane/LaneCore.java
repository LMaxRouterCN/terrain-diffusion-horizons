package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

import java.util.List;

/**
 * 车道计算核心 — 纯函数接口 (B1 骨架; 设计稿 docs/batch-b-design.md §2).
 *
 * <p>契约: 无状态、无副作用、无锁; 一切随机性由 worldSeed 派生,
 * 相同输入恒返回相同输出. 任意线程可调 (generate 线程同步内联 / 车道线程异步).
 * 抛异常 = 该 region 整体进黑名单, 由 LaneManager 隔离, 不连坐 [长期记忆: 118].</p>
 *
 * <p>坐标语义 [B2 校准点]: posX/posZ 透传 generateLod 同名参数 (数据源原点块坐标);
 * B2 接入第一个核心 (结构撒点) 时对照首次调用日志复核单位.</p>
 */
public interface LaneCore {

    /**
     * @param worldSeed   世界种子 (同源前提)
     * @param detailLevel LOD 档位 (API 语义, dl0 = 64 块/列)
     * @param posX        数据源原点块坐标 X
     * @param posZ        数据源原点块坐标 Z
     * @return 该区域内的车道段 (空列表 = 无内容, 不是错误)
     */
    List<LaneSegment> compute(long worldSeed, byte detailLevel, int posX, int posZ) throws Exception;
}