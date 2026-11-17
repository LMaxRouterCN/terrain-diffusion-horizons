package com.lmaxrouter.terraindiffusionhorizons.dh;

import java.util.HashMap;
import java.util.Map;

/**
 * TD 群系 key -> LOD 植被统计参数表 (L1).
 *
 * <p>[临时方案] [长期记忆: 106 max 指示: feature 层近似一律标临时待替代]
 * 密度值 = 观感标定 (vanilla 1.20.1 各群系植被量的定性 LOD 折算),
 * 非 PlacedFeature 数据的定量复刻; L4 地物同源落地后由真实 feature
 * 密度数据替代, 本表届时退役.</p>
 *
 * <p>语义: density (0-255) = DL0 列级命中权重 (生成器用世界块坐标确定性
 * 哈希判定, 同列永不跳变); 命中列在实心段顶叠 [heightMin, heightMax]
 * 块高的树冠段. DL0 一列 = 64x64 块, 单棵树亚列级 — "树"在此分辨率下的
 * 形态是列级树冠毯, 统计近似, 不逐棵. 水列不植被.</p>
 *
 * <p>键空间与 TdSurfaceBlockMap 对齐 (TD catalog 群系 key);
 * 无条目 = 该群系无植被 (沙漠/沙滩/雪盖裸原/峰系/恶地/石质/海洋/河流/
 * 下界/末地/洞穴群系).</p>
 */
public final class TdVegetationMap {

    /** 一个群系的统计植被参数: 密度 + 树冠方块 + 高度域. */
    public static final class VegStat {
        /** 列级命中权重 0-255 (观感标定). */
        public final int density;
        /** 树冠方块 ID (原版; 各群系用对应树叶, 竹/蘑菇群系特殊). */
        public final String canopy;
        /** 树冠段最小高度 (块). */
        public final int heightMin;
        /** 树冠段最大高度 (块). */
        public final int heightMax;

        private VegStat(int density, String canopy, int heightMin, int heightMax) {
            this.density = density;
            this.canopy = canopy;
            this.heightMin = heightMin;
            this.heightMax = heightMax;
        }
    }

    private static final Map<String, VegStat> TABLE = new HashMap<>(48);

    private static void def(String key, int density, String canopy, int hMin, int hMax) {
        TABLE.put(key, new VegStat(density, canopy, hMin, hMax));
    }

    static {
        // ---- 平原系: vanilla 极稀疏孤树 ----
        def("minecraft:plains", 8, "minecraft:oak_leaves", 3, 5);
        def("minecraft:sunflower_plains", 8, "minecraft:oak_leaves", 3, 5);
        def("minecraft:meadow", 20, "minecraft:oak_leaves", 3, 5);

        // ---- 森林系 ----
        def("minecraft:forest", 200, "minecraft:oak_leaves", 3, 6);
        def("minecraft:flower_forest", 60, "minecraft:oak_leaves", 3, 6);
        def("minecraft:birch_forest", 200, "minecraft:birch_leaves", 4, 7);
        def("minecraft:old_growth_birch_forest", 220, "minecraft:birch_leaves", 5, 9);
        def("minecraft:dark_forest", 235, "minecraft:dark_oak_leaves", 4, 8);
        def("minecraft:cherry_grove", 90, "minecraft:cherry_leaves", 3, 5);
        // pale_garden 非 1.20.1 群系, 表面表已防御性收录故跟齐; pale oak 方块
        // 1.20.1 不存在, 用 oak_leaves 观感近似 [临时]
        def("minecraft:pale_garden", 90, "minecraft:oak_leaves", 3, 6);

        // ---- 针叶系: spruce 高瘦, 树冠段高 ----
        def("minecraft:taiga", 170, "minecraft:spruce_leaves", 6, 11);
        def("minecraft:old_growth_pine_taiga", 190, "minecraft:spruce_leaves", 7, 13);
        def("minecraft:old_growth_spruce_taiga", 200, "minecraft:spruce_leaves", 7, 13);
        def("minecraft:snowy_taiga", 150, "minecraft:spruce_leaves", 6, 11);
        def("minecraft:grove", 110, "minecraft:spruce_leaves", 6, 11);
        def("minecraft:snowy_plains", 10, "minecraft:spruce_leaves", 4, 8);

        // ---- 丛林系 ----
        def("minecraft:jungle", 230, "minecraft:jungle_leaves", 5, 10);
        def("minecraft:sparse_jungle", 70, "minecraft:jungle_leaves", 4, 8);
        def("minecraft:bamboo_jungle", 235, "minecraft:bamboo", 6, 14);

        // ---- 稀树草原: acacia 平顶伞状, 冠矮 ----
        def("minecraft:savanna", 40, "minecraft:acacia_leaves", 3, 5);
        def("minecraft:savanna_plateau", 40, "minecraft:acacia_leaves", 3, 5);
        def("minecraft:windswept_savanna", 35, "minecraft:acacia_leaves", 3, 5);

        // ---- 湿地: 矮橡树 / 红树 ----
        def("minecraft:swamp", 90, "minecraft:oak_leaves", 3, 5);
        def("minecraft:mangrove_swamp", 200, "minecraft:mangrove_leaves", 4, 7);

        // ---- 山地 ----
        def("minecraft:windswept_hills", 40, "minecraft:spruce_leaves", 4, 8);
        def("minecraft:windswept_forest", 120, "minecraft:spruce_leaves", 4, 8);
        def("minecraft:windswept_gravelly_hills", 30, "minecraft:spruce_leaves", 4, 8);

        // ---- 特殊 ----
        def("minecraft:wooded_badlands", 45, "minecraft:oak_leaves", 3, 6);
        // mushroom_fields: 巨型蘑菇帽子 (无 tint, 直接方块色)
        def("minecraft:mushroom_fields", 25, "minecraft:red_mushroom_block", 3, 6);

        // 无条目 = 无植被 (见类 javadoc; 密度值均观感标定, 待实测调整)
    }

    /** 群系植被参数 (无条目/未知 key = null = 无植被). */
    public static VegStat vegetation(String biomeKey) {
        return TABLE.get(biomeKey);
    }
}