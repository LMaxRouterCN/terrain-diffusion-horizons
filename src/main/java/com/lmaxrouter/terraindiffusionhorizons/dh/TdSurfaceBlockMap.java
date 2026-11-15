package com.lmaxrouter.terraindiffusionhorizons.dh;

import java.util.HashMap;
import java.util.Map;

/**
 * TD 群系 key -> LOD 表面样式表 (案② α: 剪枝规则表).
 *
 * <p>同源依据: TD chunk 的表面方块 = vanilla SurfaceSystem.buildSurface x TD BiomeSource,
 * 而 BiomeSource 读 hydrology tile 的 biomeIndexes — 与本 mod LOD 查询的是同一份缓存数据.
 * 本表是 vanilla 主世界 surface rule 的 "LOD 输入可达分支" 剪枝复刻:
 * 群系主导表面方块 + 水下顶部方块 + 水面结冰标志, 数据 (biomeIndexes) 与规则 (群系->主导方块)
 * 均与 chunk 生成同源, 消灭系统性偏差; 剩余差异只有 64 块降采样的固有模糊.</p>
 *
 * <p>保真边界 (有意取舍, 非 bug): vanilla 规则中的噪声 patch (粗泥/花岗岩碎斑/恶地条纹)
 * 尺度小于 LOD 采样格, 属压缩的正确损失, 不复刻. 用户可用 config 的
 * surfaceBlockOverrides 覆盖任意群系的地表字段.</p>
 */
public final class TdSurfaceBlockMap {

    /** 未知群系 key 的地表 fallback (vanilla 主世界主导表面). */
    public static final String DEFAULT_SURFACE = "minecraft:grass_block";
    /** 未知群系 key 的水下顶部 fallback. */
    public static final String DEFAULT_UNDERWATER = "minecraft:dirt";

    /** 一个群系的 LOD 列表面样式. */
    public static final class SurfaceStyle {
        public final String surface;    // 地表 (干列) 方块
        public final String underwater; // 水列顶部 (河床/海床) 方块
        public final boolean iceCover;  // 水面是否结冰 (冻河/冻洋)

        private SurfaceStyle(String surface, String underwater, boolean iceCover) {
            this.surface = surface;
            this.underwater = underwater;
            this.iceCover = iceCover;
        }
    }

    private static final Map<String, SurfaceStyle> TABLE = new HashMap<>(80);

    private static void def(String key, String surface, String underwater, boolean iceCover) {
        TABLE.put(key, new SurfaceStyle(surface, underwater, iceCover));
    }

    static {
        // ---- 草地系: vanilla 主导 grass_block, 水下 dirt ----
        def("minecraft:plains", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:sunflower_plains", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:forest", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:birch_forest", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:old_growth_birch_forest", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:flower_forest", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:dark_forest", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:taiga", "minecraft:grass_block", "minecraft:dirt", false);
        // podzol 属 feature 层, LOD 固有损失, 表面仍 grass
        def("minecraft:old_growth_pine_taiga", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:old_growth_spruce_taiga", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:cherry_grove", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:pale_garden", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:meadow", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:jungle", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:sparse_jungle", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:bamboo_jungle", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:savanna", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:savanna_plateau", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:windswept_hills", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:windswept_forest", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:windswept_savanna", "minecraft:grass_block", "minecraft:dirt", false);
        def("minecraft:swamp", "minecraft:grass_block", "minecraft:dirt", false);

        // ---- 雪盖系: vanilla 为草+顶雪层, LOD 单方块取 snow_block 保雪感 ----
        def("minecraft:snowy_plains", "minecraft:snow_block", "minecraft:dirt", false);
        def("minecraft:snowy_taiga", "minecraft:snow_block", "minecraft:dirt", false);
        def("minecraft:ice_spikes", "minecraft:snow_block", "minecraft:dirt", false);
        def("minecraft:grove", "minecraft:snow_block", "minecraft:dirt", false);
        def("minecraft:snowy_slopes", "minecraft:snow_block", "minecraft:dirt", false);
        def("minecraft:jagged_peaks", "minecraft:snow_block", "minecraft:dirt", false);
        def("minecraft:snowy_beach", "minecraft:snow_block", "minecraft:sand", false);

        // ---- 冰岩峰: vanilla packed_ice 主导 ----
        def("minecraft:frozen_peaks", "minecraft:packed_ice", "minecraft:dirt", false);

        // ---- 沙漠/沙滩 ----
        def("minecraft:desert", "minecraft:sand", "minecraft:sand", false);
        def("minecraft:beach", "minecraft:sand", "minecraft:sand", false);

        // ---- 恶地: vanilla 红沙/橙陶瓦混合, 主导橙陶瓦 ----
        def("minecraft:badlands", "minecraft:orange_terracotta", "minecraft:orange_terracotta", false);
        def("minecraft:eroded_badlands", "minecraft:orange_terracotta", "minecraft:orange_terracotta", false);
        def("minecraft:wooded_badlands", "minecraft:orange_terracotta", "minecraft:orange_terracotta", false);

        // ---- 特殊 ----
        def("minecraft:mushroom_fields", "minecraft:mycelium", "minecraft:dirt", false);
        def("minecraft:mangrove_swamp", "minecraft:mud", "minecraft:mud", false);
        def("minecraft:windswept_gravelly_hills", "minecraft:gravel", "minecraft:dirt", false);
        def("minecraft:stony_peaks", "minecraft:stone", "minecraft:stone", false);
        def("minecraft:stony_shore", "minecraft:stone", "minecraft:stone", false);

        // ---- 海洋: 暖温海沙, 冷深海沙砾, 冻洋加冰盖 ----
        def("minecraft:ocean", "minecraft:gravel", "minecraft:gravel", false);
        def("minecraft:deep_ocean", "minecraft:gravel", "minecraft:gravel", false);
        def("minecraft:cold_ocean", "minecraft:gravel", "minecraft:gravel", false);
        def("minecraft:deep_cold_ocean", "minecraft:gravel", "minecraft:gravel", false);
        def("minecraft:lukewarm_ocean", "minecraft:sand", "minecraft:sand", false);
        def("minecraft:deep_lukewarm_ocean", "minecraft:sand", "minecraft:sand", false);
        def("minecraft:warm_ocean", "minecraft:sand", "minecraft:sand", false);
        def("minecraft:frozen_ocean", "minecraft:gravel", "minecraft:gravel", true);
        def("minecraft:deep_frozen_ocean", "minecraft:gravel", "minecraft:gravel", true);

        // ---- 河流: vanilla 河床 sand 主导, 冻河冰盖 ----
        def("minecraft:river", "minecraft:sand", "minecraft:sand", false);
        def("minecraft:frozen_river", "minecraft:sand", "minecraft:sand", true);

        // ---- 下界/末地/洞穴/虚空: 主世界 tile 不应出现 (分类器 canGenerateOverworld 过滤), 防御 fallback ----
        def("minecraft:nether_wastes", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:soul_sand_valley", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:crimson_forest", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:warped_forest", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:basalt_deltas", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:the_end", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:end_highlands", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:end_midlands", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:end_barrens", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:small_end_islands", "minecraft:stone", "minecraft:dirt", false);
        // 洞穴群系: tile biomeIndexes 存的是地表分类结果, 深处群系是 chunk 运行时推导; 防御
        def("minecraft:deep_dark", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:dripstone_caves", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:lush_caves", "minecraft:stone", "minecraft:dirt", false);
        def("minecraft:the_void", "minecraft:stone", "minecraft:dirt", false);
    }

    /** 群系地表方块 (未知 key 落 DEFAULT_SURFACE; config 覆盖在生成器侧做). */
    public static String surface(String biomeKey) {
        SurfaceStyle s = TABLE.get(biomeKey);
        return s != null ? s.surface : DEFAULT_SURFACE;
    }

    /** 群系水下顶部方块 (未知 key 落 DEFAULT_UNDERWATER). */
    public static String underwater(String biomeKey) {
        SurfaceStyle s = TABLE.get(biomeKey);
        return s != null ? s.underwater : DEFAULT_UNDERWATER;
    }

    /** 水面是否结冰 (未知 key false). */
    public static boolean iceCover(String biomeKey) {
        SurfaceStyle s = TABLE.get(biomeKey);
        return s != null && s.iceCover;
    }

    private TdSurfaceBlockMap() {
    }
}