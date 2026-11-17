package com.lmaxrouter.terraindiffusionhorizons.dh;

import java.util.HashMap;
import java.util.Map;

/**
 * TD 群系 key -> LOD 植被参数表 (L1 虚拟树格版, 替代旧 density 观感标定表).
 *
 * <p>[勘误] 旧版 javadoc 称 "DL0 一列 = 64x64 块" 有误: dl0 一列 = 1x1 块
 * (TdLodTranslator 实证, [长期记忆: 123]).</p>
 *
 * <p>数据来源: vanilla 1.20.1 (Forge 47.4.0) 客户端 jar 的
 * data/minecraft/worldgen/{biome, placed_feature, configured_feature} json 逐项推导;
 * json 未含的蘑菇茎高 / 竹竿高 / 竹噪声参数 javap 反编译补齐.
 * cell 边长 / 干高参数 / 冠几何全部有 json 出处, 无观感手拍值.</p>
 *
 * <p>语义 (虚拟树格): 世界按 cell 边长划格, 每 cell 确定性哈希恰放一棵树 —
 * 位置格内抖动, 树种按池权重 (千分比), 干高 = trunkBase + U(0,randA) + U(0,randB)
 * (双独立哈希, vanilla trunk_placer 双 nextInt 同构). 同 cell 同哈希 = 同一棵树.
 * 生成器按 radiusCells 邻域反查 (格差 > radiusCells 的树心到本列最小水平距离
 * = radiusCells*cell + 1, 必大于池内最大冠半径, 静态校验兜底).</p>
 *
 * <p>近似声明 (L1 统计层, 非逐株复刻):
 * ① placed count = 尝试上限, 未扣 heightmap / would_survive 过滤 (vanilla 实生成
 *    约再打 8-9 折; cell 按上限推导, 观感偏密, TEST 实测后可整体放大);
 * ② 树冠形状块级退化: blob / spruce 锥 / cherry 垂叶 / acacia 伞 / mangrove 散布 /
 *    dark oak 双团 统一为 canopyR + canopyH + conical 三参数;
 * ③ 2x2 粗干 (dark oak / mega 系) 压单列干;
 * ④ jungle bush (0.5 权重灌木) 与 jungle 群系 bamboo_light 竹丛剔除 (块级无感);
 * ⑤ 键空间与 TdSurfaceBlockMap 对齐 (TD catalog 群系 key);
 * ⑥ 无条目 = 该群系无植被 (沙漠/沙滩/雪盖裸原/峰系/恶地/石质/海洋/河流/下界/末地/洞穴).</p>
 *
 * <p>[临时] [长期记忆: 106] feature 层近似一律标临时待替代, L4 地物同源落地后本表退役.</p>
 */
public final class TdVegetationMap {

    /**
     * 一种树型: 干/冠方块 ID + 干高参数 + 冠几何.
     * 干高 = trunkBase + U(0,randA) + U(0,randB) (vanilla trunk_placer 双 nextInt 同构).
     * 冠: canopyR = 水平半径 (Chebyshev), canopyH = 竖直厚度;
     * conical = 锥形冠 (spruce/pine 系, 距树心越远冠段越窄底越高);
     * doubleStemPermille = 双倍茎概率 (千分比, 巨型蘑菇专属, 其余 0).
     */
    public static final class TreeType {
        public final String log;
        public final String leaves;
        public final int trunkBase;
        public final int randA;
        public final int randB;
        public final int canopyR;
        public final int canopyH;
        public final boolean conical;
        public final int doubleStemPermille;

        TreeType(String log, String leaves, int trunkBase, int randA, int randB,
                 int canopyR, int canopyH, boolean conical, int doubleStemPermille) {
            this.log = log;
            this.leaves = leaves;
            this.trunkBase = trunkBase;
            this.randA = randA;
            this.randB = randB;
            this.canopyR = canopyR;
            this.canopyH = canopyH;
            this.conical = conical;
            this.doubleStemPermille = doubleStemPermille;
        }
    }

    /** 池条目: 树型 + 权重 (千分比, 池内求和 = 1000, 静态校验). */
    public static final class PoolEntry {
        public final TreeType type;
        public final int weight;

        PoolEntry(TreeType type, int weight) {
            this.type = type;
            this.weight = weight;
        }
    }

    /** 一个群系的植被参数: 树格层 + 竹噪声列层. */
    public static final class VegStat {
        /** 树格边长 (块) = round(sqrt(256 / 每chunk尝试棵数)). */
        public final int cell;
        /** 树型池. */
        public final PoolEntry[] pool;
        /** 竹噪声列层 (bamboo_jungle; 列命中概率 = vanilla 噪声 count / 256). */
        public final boolean bamboo;
        /** 池内最大冠半径 (生成器快速剔除 + 覆盖校验). */
        public final int maxCanopyR;
        /** 冠反查半径 (格) = (maxCanopyR - 1) / cell + 1. */
        public final int radiusCells;

        VegStat(int cell, boolean bamboo, PoolEntry... pool) {
            this.cell = cell;
            this.pool = pool;
            this.bamboo = bamboo;
            int mr = 0;
            for (PoolEntry pe : pool) {
                mr = Math.max(mr, pe.type.canopyR);
            }
            this.maxCanopyR = mr;
            this.radiusCells = (mr - 1) / cell + 1;
        }
    }

    // ==== 树型单例 (字段 = vanilla configured_feature json 原值, 注释标出处) ====

    // oak.json / oak_bees_0002: straight_trunk_placer(4,2,0) + blob(2,3)
    private static final TreeType OAK =
            new TreeType("minecraft:oak_log", "minecraft:oak_leaves", 4, 2, 0, 2, 3, false, 0);
    // fancy_oak.json: straight_trunk_placer(3,11,0) + fancy(blob族, 2,4)
    private static final TreeType FANCY_OAK =
            new TreeType("minecraft:oak_log", "minecraft:oak_leaves", 3, 11, 0, 2, 4, false, 0);
    // birch_bees_0002: straight_trunk_placer(5,2,0) + blob(2,3)
    private static final TreeType BIRCH =
            new TreeType("minecraft:birch_log", "minecraft:birch_leaves", 5, 2, 0, 2, 3, false, 0);
    // super_birch_bees: straight_trunk_placer(5,2,6) + blob(2,3)
    private static final TreeType SUPER_BIRCH =
            new TreeType("minecraft:birch_log", "minecraft:birch_leaves", 5, 2, 6, 2, 3, false, 0);
    // spruce.json: straight_trunk_placer(5,2,1) + spruce(r=uniform(2,3)取下界, 锥形; 冠厚近似 6)
    private static final TreeType SPRUCE =
            new TreeType("minecraft:spruce_log", "minecraft:spruce_leaves", 5, 2, 1, 2, 6, true, 0);
    // pine.json: straight_trunk_placer(6,4,0) + pine(r=1, h=uniform(3,4); 冠厚近似 4)
    private static final TreeType PINE =
            new TreeType("minecraft:spruce_log", "minecraft:spruce_leaves", 6, 4, 0, 1, 4, true, 0);
    // mega_pine.json / mega_spruce.json: giant_trunk_placer(13,2,14) + mega_pine_foliage
    // (r=0 声明值, 实际锥形展开观感近似 r5 厚 10) [近似声明②]
    private static final TreeType MEGA_PINE =
            new TreeType("minecraft:spruce_log", "minecraft:spruce_leaves", 13, 2, 14, 5, 10, true, 0);
    private static final TreeType MEGA_SPRUCE = MEGA_PINE; // 同几何 (json 字段一致)
    // dark_oak.json: dark_oak_trunk_placer(6,2,1) + dark_oak_foliage(r=0 声明值, 双团实际
    // 展开, 压单列近似 r2 厚 3) [近似声明③]
    private static final TreeType DARK_OAK =
            new TreeType("minecraft:dark_oak_log", "minecraft:dark_oak_leaves", 6, 2, 1, 2, 3, false, 0);
    // cherry_bees_005: cherry_trunk_placer(7,1,0) + cherry(r=4, h=5; 冠厚近似 4)
    private static final TreeType CHERRY =
            new TreeType("minecraft:cherry_log", "minecraft:cherry_leaves", 7, 1, 0, 4, 4, false, 0);
    // acacia.json: forking_trunk_placer(5,2,2) + acacia(r=2, 伞形近似厚 2)
    private static final TreeType ACACIA =
            new TreeType("minecraft:acacia_log", "minecraft:acacia_leaves", 5, 2, 2, 2, 2, false, 0);
    // jungle_tree.json: straight_trunk_placer(4,8,0) + blob(2,3)
    private static final TreeType JUNGLE_TREE =
            new TreeType("minecraft:jungle_log", "minecraft:jungle_leaves", 4, 8, 0, 2, 3, false, 0);
    // mega_jungle_tree: mega_jungle_trunk_placer(10,2,19) + jungle_foliage(2,2)
    private static final TreeType MEGA_JUNGLE =
            new TreeType("minecraft:jungle_log", "minecraft:jungle_leaves", 10, 2, 19, 2, 3, false, 0);
    // swamp_oak: straight_trunk_placer(5,3,0) + blob(3,3)
    private static final TreeType SWAMP_OAK =
            new TreeType("minecraft:oak_log", "minecraft:oak_leaves", 5, 3, 0, 3, 3, false, 0);
    // mangrove.json: upwards_branching_trunk_placer(2,1,4) + random_spread(3)
    private static final TreeType MANGROVE =
            new TreeType("minecraft:mangrove_log", "minecraft:mangrove_leaves", 2, 1, 4, 3, 3, false, 0);
    // tall_mangrove: upwards_branching_trunk_placer(4,1,9) + random_spread(3)
    private static final TreeType TALL_MANGROVE =
            new TreeType("minecraft:mangrove_log", "minecraft:mangrove_leaves", 4, 1, 9, 3, 3, false, 0);
    // huge_red_mushroom: 茎 4+nextInt(3), 1/12 概率茎翻倍 [javap AbstractHugeMushroomFeature];
    // 帽 foliage_radius=2 厚 2 (半球)
    private static final TreeType RED_MUSH =
            new TreeType("minecraft:mushroom_stem", "minecraft:red_mushroom_block", 4, 2, 0, 2, 2, false, 83);
    // huge_brown_mushroom: 茎同红 (含 1/12 双倍茎, getTreeHeight 在 AbstractHugeMushroomFeature 红褐共用);
    // 帽 foliage_radius=3 厚 1 (扁盘)
    private static final TreeType BROWN_MUSH =
            new TreeType("minecraft:mushroom_stem", "minecraft:brown_mushroom_block", 4, 2, 0, 3, 1, false, 83);

    // ==== 表 ====

    private static final Map<String, VegStat> TABLE = new HashMap<>(48);

    private static void def(String key, int cell, boolean bamboo, PoolEntry... pool) {
        TABLE.put(key, new VegStat(cell, bamboo, pool));
    }

    static {
        // ---- 平原系: trees_plains 加权 (19:1) 0.05 棵/chunk ----
        def("minecraft:plains", 72, false, new PoolEntry(OAK, 667), new PoolEntry(FANCY_OAK, 333));
        def("minecraft:sunflower_plains", 72, false, new PoolEntry(OAK, 667), new PoolEntry(FANCY_OAK, 333));
        // meadow: trees_meadow = rarity_filter 100 (~0.01 棵/chunk, 极稀)
        def("minecraft:meadow", 160, false, new PoolEntry(SUPER_BIRCH, 500), new PoolEntry(FANCY_OAK, 500));

        // ---- 森林系 ----
        // trees_birch_and_oak 加权 10.1 棵/chunk; 池 oak.72/birch.2/fancy.08 (顺序条件)
        def("minecraft:forest", 5, false,
                new PoolEntry(OAK, 720), new PoolEntry(BIRCH, 200), new PoolEntry(FANCY_OAK, 80));
        // trees_flower_forest 加权 6.1 棵/chunk (池同 forest)
        def("minecraft:flower_forest", 6, false,
                new PoolEntry(OAK, 720), new PoolEntry(BIRCH, 200), new PoolEntry(FANCY_OAK, 80));
        // trees_birch 加权 10.1 棵/chunk, birch 单型
        def("minecraft:birch_forest", 5, false, new PoolEntry(BIRCH, 1000));
        // birch_tall 加权 10.1 棵/chunk; 池 birch.5/super_birch.5 (顺序条件)
        def("minecraft:old_growth_birch_forest", 5, false,
                new PoolEntry(BIRCH, 500), new PoolEntry(SUPER_BIRCH, 500));
        // dark_forest_vegetation count 16 棵/chunk;
        // 池顺序条件: dark_oak.617/oak.222/birch.062/fancy.025/褐菇.025/红菇.049
        def("minecraft:dark_forest", 4, false,
                new PoolEntry(DARK_OAK, 617), new PoolEntry(OAK, 222), new PoolEntry(BIRCH, 62),
                new PoolEntry(FANCY_OAK, 25), new PoolEntry(RED_MUSH, 49), new PoolEntry(BROWN_MUSH, 25));
        // trees_cherry 加权 10.1 棵/chunk, cherry 单型 (大冠 r4)
        def("minecraft:cherry_grove", 5, false, new PoolEntry(CHERRY, 1000));
        // pale_garden 非 1.20.1 群系, 表面表已防御性收录故跟齐; pale oak 方块 1.20.1
        // 不存在, oak 观感近似 [临时]
        def("minecraft:pale_garden", 5, false, new PoolEntry(OAK, 1000));

        // ---- 针叶系 ----
        // trees_taiga 加权 10.1 棵/chunk; 池 spruce.667/pine.333
        def("minecraft:taiga", 5, false, new PoolEntry(SPRUCE, 667), new PoolEntry(PINE, 333));
        def("minecraft:snowy_taiga", 5, false, new PoolEntry(SPRUCE, 667), new PoolEntry(PINE, 333));
        // trees_grove 加权 10.1 棵/chunk; 池 spruce_on_snow.667/pine_on_snow.333 (几何同基型)
        def("minecraft:grove", 5, false, new PoolEntry(SPRUCE, 667), new PoolEntry(PINE, 333));
        // trees_old_growth_pine_taiga 加权 10.1 棵; 池顺序条件
        // spruce.450/mega_pine.300/pine.225/mega_spruce.025
        def("minecraft:old_growth_pine_taiga", 5, false,
                new PoolEntry(SPRUCE, 450), new PoolEntry(MEGA_PINE, 300),
                new PoolEntry(PINE, 225), new PoolEntry(MEGA_SPRUCE, 25));
        // trees_old_growth_spruce_taiga 加权 10.1 棵; 池顺序条件
        // spruce.445/mega_spruce.333/pine.222
        def("minecraft:old_growth_spruce_taiga", 5, false,
                new PoolEntry(SPRUCE, 445), new PoolEntry(MEGA_SPRUCE, 333), new PoolEntry(PINE, 222));
        // trees_snowy 加权 (9:1) 0.1 棵/chunk, spruce 单型
        def("minecraft:snowy_plains", 51, false, new PoolEntry(SPRUCE, 1000));

        // ---- 丛林系 ----
        // trees_jungle 加权 50.1 棵/chunk; 池顺序条件 bush.45 剔除后归一
        // jungle.545/mega_jungle.273/fancy.182 [近似声明④]
        def("minecraft:jungle", 3, false,
                new PoolEntry(JUNGLE_TREE, 545), new PoolEntry(MEGA_JUNGLE, 273), new PoolEntry(FANCY_OAK, 182));
        // trees_sparse_jungle 加权 2.1 棵/chunk; 池顺序条件 bush 剔除后 jungle.818/fancy.182
        def("minecraft:sparse_jungle", 15, false,
                new PoolEntry(JUNGLE_TREE, 818), new PoolEntry(FANCY_OAK, 182));
        // bamboo_vegetation 加权 30.1 棵/chunk; 树层 0.75 有效 (mega.7/fancy.05, 默认分支
        // 草丛剔除) = 22.6 棵/chunk; 池顺序条件 bush 剔除后 mega.942/fancy.058;
        // 另挂竹噪声列层 (bamboo placed: noise_based_count(80, 0.3, 160))
        def("minecraft:bamboo_jungle", 3, true,
                new PoolEntry(MEGA_JUNGLE, 942), new PoolEntry(FANCY_OAK, 58));

        // ---- 稀树草原: trees_savanna 1.1 棵/chunk; 池 acacia.8/oak.2 ----
        def("minecraft:savanna", 15, false, new PoolEntry(ACACIA, 800), new PoolEntry(OAK, 200));
        def("minecraft:savanna_plateau", 15, false, new PoolEntry(ACACIA, 800), new PoolEntry(OAK, 200));
        // trees_windswept_savanna 加权 2.1 棵/chunk (池同)
        def("minecraft:windswept_savanna", 11, false, new PoolEntry(ACACIA, 800), new PoolEntry(OAK, 200));

        // ---- 湿地 ----
        // trees_swamp 加权 2.1 棵/chunk, swamp_oak 单型 (冠 r3)
        def("minecraft:swamp", 11, false, new PoolEntry(SWAMP_OAK, 1000));
        // trees_mangrove count 25 棵/chunk; 池 tall.85/normal.15 (气根与呼吸根不辨识 [近似声明②])
        def("minecraft:mangrove_swamp", 3, false,
                new PoolEntry(TALL_MANGROVE, 850), new PoolEntry(MANGROVE, 150));

        // ---- 山地: trees_windswept_hills 加权 0.1 / trees_windswept_forest 3.1 棵/chunk;
        //      池顺序条件 spruce.667/oak.3/fancy.033 ----
        def("minecraft:windswept_hills", 51, false,
                new PoolEntry(SPRUCE, 667), new PoolEntry(OAK, 300), new PoolEntry(FANCY_OAK, 33));
        def("minecraft:windswept_gravelly_hills", 51, false,
                new PoolEntry(SPRUCE, 667), new PoolEntry(OAK, 300), new PoolEntry(FANCY_OAK, 33));
        def("minecraft:windswept_forest", 9, false,
                new PoolEntry(SPRUCE, 667), new PoolEntry(OAK, 300), new PoolEntry(FANCY_OAK, 33));

        // ---- 特殊 ----
        // trees_badlands 5.1 棵/chunk, oak 单型 (wooded_badlands 表面表跟齐收录)
        def("minecraft:wooded_badlands", 7, false, new PoolEntry(OAK, 1000));
        // mushroom_island_vegetation 无 count 修饰 = 每 chunk 恰一次尝试; 池 红.5/褐.5
        def("minecraft:mushroom_fields", 16, false,
                new PoolEntry(RED_MUSH, 500), new PoolEntry(BROWN_MUSH, 500));

        // ---- 完整性校验 (fail fast) ----
        for (Map.Entry<String, VegStat> e : TABLE.entrySet()) {
            int sum = 0;
            for (PoolEntry pe : e.getValue().pool) {
                sum += pe.weight;
            }
            if (sum != 1000) {
                throw new IllegalStateException("植被池权重和 != 1000: " + e.getKey() + " (" + sum + ")");
            }
            // 覆盖充分性: 格差 > radiusCells 的树心到本列最小水平距离
            // = radiusCells*cell + 1, 必大于池内最大冠半径
            if (e.getValue().radiusCells * e.getValue().cell + 1 <= e.getValue().maxCanopyR) {
                throw new IllegalStateException("冠反查半径不足: " + e.getKey());
            }
        }
        // 无条目 = 无植被 (见类 javadoc)
    }

    /** 群系植被参数 (无条目/未知 key = null = 无植被). */
    public static VegStat vegetation(String biomeKey) {
        return TABLE.get(biomeKey);
    }
}