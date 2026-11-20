package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import com.lmaxrouter.terraindiffusionhorizons.TerrainDiffusionHorizons;
import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneManagers;
import com.lmaxrouter.terraindiffusionhorizons.dh.structure.StructureSetParams;
import com.lmaxrouter.terraindiffusionhorizons.dh.structure.TdStructureAssembleCore;
import com.lmaxrouter.terraindiffusionhorizons.dh.structure.TdStructureContext;
import com.lmaxrouter.terraindiffusionhorizons.dh.structure.TdStructureSpreadCore;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelUnloadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Distant Horizons 兼容层 (S2b 真注册; B2 追加车道装配与参数快照).
 *
 * <p>事件链: mod 构造期挂 LevelLoad/Unload 事件 -> 守卫链 (overworld / TD 世界 /
 * seed 可得) -> 注册生成器 + 车道核心 + 撒点参数快照.
 * 世界卸载无反注册 API, 由生成器自身 close() 清引用; 车道数据面 (缓存/参数表/
 * 刷新钩子 wrapper) 由 Unload 回调收口.</p>
 *
 * <p>B2 追加职责:
 * ①register() 装配车道 (结构撒点核心进 structure 车道, 刷新钩子挂两车道);
 * ②onLevel 装配撒点参数快照 (vanilla structure_set json, ResourceManager 直读)
 *   并绑定刷新钩子的 level wrapper;
 * ③Unload 回调判维度后才清数据面 — nether/end 的 unload 与 overworld 平级触发,
 *   B1 无差别清缓存无害, B2 若无差别清参数表则 overworld 无 LevelLoad 再触发,
 *   数据面永久哑掉 [B1 遗留坑, 本轮修].</p>
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
        // [B1] 车道生命周期 + [B2] 维度收口: 只认 overworld 的卸载 (EventParam.levelWrapper
        // 与 Load 同构, javap 实证); nether/end unload 平级触发, 误清 = overworld 参数表
        // 哑掉且无 LevelLoad 再装配, 必须挡
        DhApiEventRegister.on(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent() {
            @Override
            public void onLevelUnload(DhApiEventParam<DhApiLevelUnloadEvent.EventParam> param) {
                if (!"minecraft:overworld".equals(param.value.levelWrapper.getDimensionName())) {
                    return;
                }
                DhRefreshHook.INSTANCE.unbind();
                TdStructureSpreadCore.INSTANCE.clearParams();
                TdStructureContext.clear();
                LaneManagers.onLevelUnloadAll();
            }
        });
        // [B2] 车道装配: 撒点核心进 structure 车道; 刷新钩子挂两车道
        // (features 车道 L3/L4 再塞核心, 钩子先挂上保持对称)
        LaneManagers.structure().addCore(TdStructureSpreadCore.INSTANCE);
        // [L3] 组装核心进 features 车道 (撒点命中 -> 消元 -> generate -> placeInChunk -> 列折叠)
        LaneManagers.features().addCore(TdStructureAssembleCore.INSTANCE);
        LaneManagers.structure().setRefreshHook(DhRefreshHook.INSTANCE);
        LaneManagers.features().setRefreshHook(DhRefreshHook.INSTANCE);
        TerrainDiffusionHorizons.LOG.info("DH 兼容层已挂钩 (LevelLoad/Unload 事件监听中)");
    }
    /** LevelLoad 回调: 守卫链 + 注册 + 车道数据面装配 (跑在 DH 事件线程). */
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
        // [B2] 刷新钩子绑定: api wrapper 实为 core wrapper (DH 内部同一对象, instanceof 防御后强转)
        if (levelWrapper instanceof ILevelWrapper coreWrapper) {
            DhRefreshHook.INSTANCE.bind(coreWrapper);
        }
        // [B2] 撒点参数装配: vanilla structure_set json -> 不可变快照 (含装配日志)
        // [L3] 同表喂组装上下文 (entries 解析 + generate 十参活对象), 组装车道消费
        Map<String, StructureSetParams> setTable = readStructureSets(serverLevel);
        TdStructureSpreadCore.INSTANCE.updateParams(setTable);
        TdStructureContext.assemble(serverLevel, setTable);
        TdDhWorldGenerator generator = new TdDhWorldGenerator(levelWrapper, worldSeed);
        // DhApiResult 吞返回值打日志, 注册失败可见 (重复注册/无效 level 等场景)
        var result = DhApi.worldGenOverrides.registerWorldGeneratorOverride(levelWrapper, generator);
        TerrainDiffusionHorizons.LOG.info("[{}] TDH 生成器注册完成 (seed={}): {}", dim, worldSeed, result);
    }

    /**
     * 读 vanilla structure_set json -> 撒点参数表 (B2; 参数源定案 [长期记忆: 115]).
     *
     * <p>单人端服务端 ResourceManager 即权威 (fork 无覆盖 + 无 openloader 实证
     * 2026-11-19); 只认 minecraft 命名空间 + overworld 白名单 — 模组 structure_set
     * 属多人 registry 同步悬案 #3, B2 不碰.</p>
     */
    private static Map<String, StructureSetParams> readStructureSets(ServerLevel serverLevel) {
        Map<String, StructureSetParams> table = new LinkedHashMap<>();
        // listResources: 文件夹级枚举 (谓词恒真, 命名空间/文件名在下方手工筛)
        Map<ResourceLocation, Resource> resources = serverLevel.getServer().getResourceManager()
                .listResources("worldgen/structure_set", id -> true);
        for (Map.Entry<ResourceLocation, Resource> e : resources.entrySet()) {
            ResourceLocation loc = e.getKey();
            if (!"minecraft".equals(loc.getNamespace())) {
                continue;
            }
            // path 形如 "worldgen/structure_set/villages.json" -> 集合名 "villages"
            // (与 json 内 exclusion_zone.other_set 的去后缀名一致 = 排除区查表键)
            String file = loc.getPath().substring(loc.getPath().lastIndexOf('/') + 1);
            String name = file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
            if (!StructureSetParams.OVERWORLD_SETS.contains(name)) {
                continue; // 白名单外 (strongholds/异维度集合等)
            }
            StructureSetParams p;
            try (BufferedReader reader = e.getValue().openAsReader()) {
                p = StructureSetParams.parse(reader);
            } catch (Exception ex) {
                TerrainDiffusionHorizons.LOG.warn("structure_set 读取失败, 跳过 {}: {}", name, ex.toString());
                continue;
            }
            if (p == null) {
                TerrainDiffusionHorizons.LOG.warn("structure_set 非 random_spread 或参数坏, 跳过: {}", name);
                continue;
            }
            table.put(name, p);
        }
        TerrainDiffusionHorizons.LOG.info("撒点参数装配: {} 个集合 [{}]", table.size(), String.join(", ", table.keySet()));
        return table;
    }
}