package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import com.lmaxrouter.terraindiffusionhorizons.TerrainDiffusionHorizons;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.server.level.ServerLevel;

/**
 * Distant Horizons 兼容层 (S2b 真注册).
 *
 * <p>事件链: mod 构造期挂 LevelLoad 事件 -> 每个世界加载时回调 onLevelLoad ->
 * 守卫链 (overworld / TD 世界 / seed 可得) -> DhApi.worldGenOverrides 注册生成器.
 * 世界卸载无反注册 API, 由生成器自身 close() 清引用.</p>
 */
public final class DhCompat {

    private DhCompat() {
    }

    /** 双宿主在场时由主类调用 (S2a 既有入口, 签名不变). */
    public static void register() {
        if (!TdConfig.get().enabled) {
            TerrainDiffusionHorizons.LOG.info("TDH 配置禁用, 不注册 DH 生成器");
            return;
        }
        // 匿名子类只 override onLevelLoad; fireEvent 是 final, removeAfterFiring 用 default (持续监听)
        DhApiEventRegister.on(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
            @Override
            public void onLevelLoad(DhApiEventParam<DhApiLevelLoadEvent.EventParam> param) {
                onLevel(param.value.levelWrapper);
            }
        });
        TerrainDiffusionHorizons.LOG.info("DH 兼容层已挂钩 (LevelLoad 事件监听中)");
    }
    /** LevelLoad 回调: 守卫链 + 注册 (跑在 DH 事件线程, 只做轻量判断与注册). */
    private static void onLevel(IDhApiLevelWrapper levelWrapper) {
        String dim = levelWrapper.getDimensionName();
        // TD 管线只服务主世界 (S2b 硬边界; 多维度支持属 S4 配置化范畴)
        if (!"minecraft:overworld".equals(dim)) {
            return;
        }
        // TD 世界判定: 非 TD 世界让行给 DH 原生生成链
        if (!WorldScaleManager.isTerrainDiffusionWorldActive()) {
            TerrainDiffusionHorizons.LOG.info("[{}] 非 TD 世界, 跳过生成器注册", dim);
            return;
        }
        // seed 捕获: LevelLoad 时 ServerLevel 已就绪 (S1 案卷); 取不得则放弃注册
        Object mcLevel = levelWrapper.getWrappedMcObject();
        if (!(mcLevel instanceof ServerLevel serverLevel)) {
            TerrainDiffusionHorizons.LOG.warn("[{}] 原生 level 不可得 ({}), 跳过注册", dim, mcLevel);
            return;
        }
        long worldSeed = serverLevel.getSeed();
        TdDhWorldGenerator generator = new TdDhWorldGenerator(levelWrapper, worldSeed);
        // DhApiResult 吞返回值打日志, 注册失败可见 (重复注册/无效 level 等场景)
        var result = DhApi.worldGenOverrides.registerWorldGeneratorOverride(levelWrapper, generator);
        TerrainDiffusionHorizons.LOG.info("[{}] TDH 生成器注册完成 (seed={}): {}", dim, worldSeed, result);
    }
}