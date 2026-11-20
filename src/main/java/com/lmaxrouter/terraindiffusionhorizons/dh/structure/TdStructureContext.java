package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构组装上下文 (L3; 设计稿 docs/batch-c-design.md §2.1).
 *
 * <p>活对象包: structure.generate 十参里无法离线构造的部分 (generator / randomState /
 * templateManager / registryAccess) 在 LevelLoad 时一次性组装, 车道线程只读消费.
 * B1 "核心不碰 MC registry" 原则在 L3 正式破戒 (立项令预告), 破戒形态 = 本类:
 * registry 读取全部发生在装配期 (DhCompat onLevel 回调, LevelLoad 时 ServerLevel
 * 在手 [S1 案卷]), 车道核心经 get() 拿不可变快照 — 车道线程零 registry 调用.</p>
 *
 * <p>生命周期: volatile 单例快照, Unload 清 null (模式同 DhRefreshHook).
 * 装配失败 = active 保持 null, 组装核心静默降级为空载荷.</p>
 */
public final class TdStructureContext {

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Structure");

    /** 已解析 entry: Structure 实例 + 权重 (消元循环输入, 设计稿 §3.1). */
    public record ResolvedEntry(Structure structure, int weight) {
    }

    /**
     * 一次装配的原子快照 (volatile 读一致性: 车道线程要么拿全套要么拿 null).
     * level 即 generate 第十参 LevelHeightAccessor (Structure.java L82 签名实证,
     * ServerLevel 实现 LevelHeightAccessor).
     */
    public record Snapshot(
            ServerLevel level,
            ChunkGenerator generator,
            RandomState randomState,
            StructureTemplateManager templateManager,
            RegistryAccess registryAccess,
            Registry<Biome> biomeRegistry,
            Map<String, List<ResolvedEntry>> entriesTable) {
    }

    private static volatile Snapshot active;

    private TdStructureContext() {
    }

    /**
     * 装配 (DhCompat onLevel 调, DH 事件线程; params = SpreadCore 同源 json 表).
     * entries 名字在此解析成 Structure 实例 (Registry.get(ResourceLocation),
     * miss 即 null 剔除), 全 miss 的集合整集跳过 — datapack 缺件防御, 宁少组不误组.
     */
    public static void assemble(ServerLevel level, Map<String, StructureSetParams> params) {
        try {
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            RandomState randomState = level.getChunkSource().randomState();
            // 命名陷阱: MinecraftServer.getStructureManager() 返回 StructureTemplateManager
            // (mojmap 源 L1668 实证, 非 net.minecraft.world.level.StructureManager)
            StructureTemplateManager templateManager = level.getServer().getStructureManager();
            RegistryAccess registryAccess = level.registryAccess();
            Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
            Map<String, List<ResolvedEntry>> table = new LinkedHashMap<>();
            int resolved = 0;
            for (Map.Entry<String, StructureSetParams> e : params.entrySet()) {
                List<ResolvedEntry> list = new ArrayList<>();
                for (StructureSetParams.StructureEntry ne : e.getValue().entries()) {
                    // Registry.get(ResourceLocation) -> 实例或 null (最古老的读取面, 无签名悬念)
                    Structure s = structureRegistry.get(new ResourceLocation(ne.structure()));
                    if (s != null) {
                        list.add(new ResolvedEntry(s, ne.weight()));
                        resolved++;
                    } else {
                        LOG.warn("结构 entry 解析 miss, 剔除: {} (集合 {})", ne.structure(), e.getKey());
                    }
                }
                if (!list.isEmpty()) {
                    table.put(e.getKey(), list);
                } else {
                    LOG.warn("集合 {} 全部 entry miss, 组装侧跳过", e.getKey());
                }
            }
            active = new Snapshot(level, generator, randomState, templateManager,
                    registryAccess, biomeRegistry, table);
            LOG.info("结构组装上下文已装配: {} 集合 / {} entry", table.size(), resolved);
        } catch (Exception ex) {
            active = null;
            LOG.warn("结构组装上下文装配失败, 组装车道空载荷: {}", ex.toString());
        }
    }

    /** 清 (DhCompat unload 调; 迟到的车道任务 get() 拿 null 自然空载荷). */
    public static void clear() {
        active = null;
    }

    /** null = 未装配 (非 TD 世界 / 装配失败 / 已卸载). */
    public static Snapshot get() {
        return active;
    }
}