package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

/**
 * 车道刷新钩子 (B2; 设计稿 §4.4 PassB 的窄化接口).
 *
 * <p>异步车道算完落表后由此触发 DH 重拉该 region (慢车道回写).
 * 实现住 dh 包 (DhRefreshHook, 持 provider 与生命周期), 车道包经此
 * 解耦 — LaneManager 不 import 任何 DH core 类.</p>
 *
 * <p>契约: 实现内部自带全部防御 (provider 缺位静默降级 / 异常自吞),
 * 调用方 (LaneManager) 不再 try-catch — 钩子失败不折损已落表的车道数据
 * (兜底 = 设计稿 §9: 视觉延迟到下次自然拉取, 数据不丢).</p>
 *
 * <p>内部协议接口: 勿在 lane 包外实现 (sealed 不适用 — 函数式单方法
 * 跨包实现是设计本意).</p>
 */
@FunctionalInterface
public interface LaneRefreshHook {

    /**
     * 请求 DH 重拉该 region.
     *
     * @param detailLevel API 档位 (generateLod 原样透传)
     * @param posX        数据源原点 X (section 网格坐标, 同 LaneCore 契约)
     * @param posZ        数据源原点 Z
     */
    void requestRefresh(byte detailLevel, int posX, int posZ);
}