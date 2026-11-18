package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

import com.lmaxrouter.terraindiffusionhorizons.TdConfig;

/**
 * 车道聚合入口 (B1): 两键各一条车道 (structure / features, 立项令钦定).
 *
 * <p>实例常驻 (线程池/核心注册表是跨世界设施), 世界级数据 (缓存) 由
 * LevelUnload 事件清. generator 不持有车道引用, 经此类静态转发 —
 * 生成器随世界生灭, 车道独立存活, 生命周期解耦.</p>
 */
public final class LaneManagers {

    private static final LaneManager STRUCTURE =
            new LaneManager("structure", () -> TdConfig.get().structureEnabled, () -> TdConfig.get().structureAsync);
    private static final LaneManager FEATURES =
            new LaneManager("features", () -> TdConfig.get().featuresEnabled, () -> TdConfig.get().featuresAsync);

    private LaneManagers() {
    }

    /** 结构车道 (B2 载荷: TdStructureOracle 同源撒点). */
    public static LaneManager structure() {
        return STRUCTURE;
    }

    /** 地物车道 (L3/L4 载荷: 结构组装 / 逐株地物). */
    public static LaneManager features() {
        return FEATURES;
    }

    /** 世界卸载: 全车道清数据 (DhCompat 的 LevelUnload 订阅转发到此). */
    public static void onLevelUnloadAll() {
        STRUCTURE.onLevelUnload();
        FEATURES.onLevelUnload();
    }
}