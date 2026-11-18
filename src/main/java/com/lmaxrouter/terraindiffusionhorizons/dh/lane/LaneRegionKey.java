package com.lmaxrouter.terraindiffusionhorizons.dh.lane;

/**
 * 车道缓存键: 世界种子 + 档位 + 数据源原点 (跨世界/跨档位/跨 region 三重隔离).
 * 包私有: 仅车道包内使用.
 */
record LaneRegionKey(long seed, byte detailLevel, int posX, int posZ) {
}