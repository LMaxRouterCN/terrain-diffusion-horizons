package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.lmaxrouter.terraindiffusionhorizons.TerrainDiffusionHorizons;

/**
 * Distant Horizons 兼容层入口 (S2a 占位).
 *
 * <p>S2b 将在此落地: IDhApiWorldGenerator 实现 (generateLod 走 TD 的
 * LocalTerrainProvider.getRiverTerrainData) + DhApi.worldGenOverrides 注册
 * + WrapperFactory_forge.INSTANCE 数据点包装 + seed 一致性防护 (S1 案卷).</p>
 */
public final class DhCompat {

    private DhCompat() {
    }

    /** 双宿主在场时由主类调用. S2a 只打旗标, 不触碰任何 DH/TD 类. */
    public static void register() {
        TerrainDiffusionHorizons.LOG.info("DH 兼容层挂钩 (S2a 占位, S2b 落地生成器注册)");
    }
}