package com.lmaxrouter.terraindiffusionhorizons.dh;

import java.util.HashMap;
import java.util.Map;

/**
 * TD 群系 key -> 花草色斑样式表 (L1 植被上色层, 列级概率近似).
 *
 * <p>同源依据 [长期记忆: 124 方法: 1.20.1 客户端 jar worldgen 解包 %TEMP%\van_wg_x]:
 * vanilla 花草 = placed_feature (每 chunk 种子数) x configured random_patch
 * (每种子 tries 次尝试, 命中放 1 个方块 state); LOD 无过程只有结果,
 * 两层压缩为列级概率: 列顶概率 permille = 种子数 x tries x k / 256,
 * k = 单次尝试落地综合命中率 (观感标定 0.05, 全局唯一经验常数, TEST 后校准).
 * 池条目与权重照抄 configured json 的 state provider, 出处逐条注释.</p>
 *
 * <p>近似声明 (有意取舍, 非 bug):
 * 1. noise_threshold_count (placed) 取 above/below 中值 (cherry 7.5, plains 花 9.5, tall_grass_2 3.5);
 * 2. noise_threshold_provider (plains 高低噪分区) / noise_provider (flower_forest 空间分区)
 *    / dual_noise_provider (meadow) 统一近似为池内权重随机 — 丢失 vanilla 成片同种花结构, 换列级轻机制;
 * 3. 双格植物 (sunflower/lilac/rose_bush/peony/tall_grass/large_fern) 取 lower 半默认 state
 *    (与 vanilla json 的 to_place 一致, wrapper 亦取默认);
 * 4. pink_petals 16 态 (facing x flower_amount) 采默认单态 — 贴图采样对朝向/数量无感;
 * 5. 蘑菇/枯丛/甘蔗/南瓜/仙人掌/藤蔓/西瓜/睡莲/发光地衣: 非花草色语义或水生, 不收
 *    (蘑菇 rarity 256/512 = 每 256/512 chunk 试一次, 本身不可见);
 * 6. 草池 = 留口数据: 发射端默认不消费 (cfg.grassColoring=false), 草丛与草方块共用群系
 *    grass colormap, 换上大概率等于没换; 若 TEST 发现 DH 采样使两者分家, 开 config 即启用, 本表零改动.</p>
 *
 * <p>无条目群系 = 无花草 (海洋/河流/雪峰系/grove/蘑菇岛/下界/末地/洞穴).</p>
 */
public final class TdFlowerMap {

    /** 池条目: 方块 ID + 池内权重 (照抄 json weight 或期望贡献比, 见各条目注释). */
    public static final class Entry {
        public final String state;
        public final int weight;

        Entry(String state, int weight) {
            this.state = state;
            this.weight = weight;
        }
    }

    /** 一个群系的花草色斑样式 (permille = 列顶命中概率, 千分比). */
    public static final class FlowerStat {
        /** 花池 (null = 无花, 如 mangrove_swamp). */
        public final Entry[] flowers;
        /** 列顶是花的概率 permille. */
        public final int flowerPermille;
        /** 草池 (null = 无草; 留口, cfg.grassColoring 开启才消费). */
        public final Entry[] grasses;
        /** 列顶是草的概率 permille. */
        public final int grassPermille;

        FlowerStat(Entry[] flowers, int flowerPermille, Entry[] grasses, int grassPermille) {
            this.flowers = flowers;
            this.flowerPermille = flowerPermille;
            this.grasses = grasses;
            this.grassPermille = grassPermille;
        }
    }

    private static final Map<String, FlowerStat> TABLE = new HashMap<>(48);

    private static Entry e(String state, int weight) {
        return new Entry(state, weight);
    }

    private static void def(String key, int fp, Entry[] fl, int gp, Entry[] gr) {
        TABLE.put(key, new FlowerStat(fl, fp, gr, gp));
    }

    // ==== 共享池 (跨群系复用的 provider 池, 不可变数组共享安全) ====

    /** flower_default 池 (weighted_state_provider: poppy 2 : dandelion 1). */
    private static final Entry[] DEFAULT_FLOWERS =
            { e("minecraft:poppy", 2), e("minecraft:dandelion", 1) };
    /** patch_grass 池 (simple_state_provider: grass 单态). */
    private static final Entry[] GRASS_ONLY = { e("minecraft:grass", 1) };
    /** plains 草混合: patch_tall_grass_2 (66 permille) : patch_grass_plain (47) 贡献比 7:5. */
    private static final Entry[] PLAIN_GRASS =
            { e("minecraft:tall_grass", 7), e("minecraft:grass", 5) };
    /** 森林花: flower_default (poppy 2 : dandelion 1) + forest_flowers 灌木
     *  (simple_random_selector: lilac/rose_bush/peony/lily 各 lower 等权). */
    private static final Entry[] FOREST_FLOWERS = {
            e("minecraft:poppy", 2), e("minecraft:dandelion", 1),
            e("minecraft:lilac", 1), e("minecraft:rose_bush", 1),
            e("minecraft:peony", 1), e("minecraft:lily_of_the_valley", 1) };
    /** 丛林草: patch_grass_jungle (weighted: grass 3 : fern 1, 排除 podzol 上方). */
    private static final Entry[] JUNGLE_GRASS =
            { e("minecraft:grass", 3), e("minecraft:fern", 1) };
    /** 针叶草: patch_taiga_grass (weighted: grass 1 : fern 4). */
    private static final Entry[] TAIGA_GRASS =
            { e("minecraft:grass", 1), e("minecraft:fern", 4) };

    static {
        // ==== 平原系 ====
        // plains 花: flower_plains = noise(4,15)~9.5 / rarity32 ~ 0.3 种子 x tries64 x k
        //   ~ 1 朵 -> 4 permille; 池 = noise_threshold_provider 近似 (类注 2):
        //   噪 <-0.8 约 21% 全 dandelion, 高噪 79% x 1/3 high 池 (poppy/azure/oxeye/cornflower),
        //   79% x 2/3 low 池 (4 郁金香) -> 8 : 3x4 : 5x4
        // 草: tall_grass_2 66 + grass_plain 47 = 113 permille
        def("minecraft:plains", 4, new Entry[]{
                e("minecraft:dandelion", 8),
                e("minecraft:poppy", 3), e("minecraft:azure_bluet", 3),
                e("minecraft:oxeye_daisy", 3), e("minecraft:cornflower", 3),
                e("minecraft:orange_tulip", 5), e("minecraft:red_tulip", 5),
                e("minecraft:pink_tulip", 5), e("minecraft:white_tulip", 5)}, 113, PLAIN_GRASS);
        // sunflower_plains: patch_sunflower (rarity3 ~ 0.33 种子 x 96 ~ 1.6 朵) +
        //   plains 花 (~1 朵) -> 贡献比 62:38 -> 10 permille; 池 sunflower 15 : plains 花各 1 (62.5:37.5)
        def("minecraft:sunflower_plains", 10, new Entry[]{
                e("minecraft:sunflower", 15),
                e("minecraft:dandelion", 1), e("minecraft:poppy", 1), e("minecraft:azure_bluet", 1),
                e("minecraft:oxeye_daisy", 1), e("minecraft:cornflower", 1),
                e("minecraft:orange_tulip", 1), e("minecraft:red_tulip", 1),
                e("minecraft:pink_tulip", 1), e("minecraft:white_tulip", 1)}, 113, PLAIN_GRASS);
        // ==== 繁花系 (主力可见群系, 密度忠实 vanilla) ====
        // flower_forest: flower_flower_forest (count3/rarity2 = 1.5 种子 x 96) +
        //   flower_forest_flowers 灌木 (rarity7 ~ 0.16 种子 x 96) ~ 8 朵 -> 32 permille;
        //   花池 = noise_provider 11 花均匀 (各 3, 类注 2) + 灌木 lilac/rose_bush/peony 各 1
        //   (lily 双来源 3+1); 草 = patch_grass_badlands (1 种子 x 32) ~ 6
        def("minecraft:flower_forest", 32, new Entry[]{
                e("minecraft:dandelion", 3), e("minecraft:poppy", 3), e("minecraft:allium", 3),
                e("minecraft:azure_bluet", 3), e("minecraft:red_tulip", 3),
                e("minecraft:orange_tulip", 3), e("minecraft:white_tulip", 3),
                e("minecraft:pink_tulip", 3), e("minecraft:oxeye_daisy", 3),
                e("minecraft:cornflower", 3), e("minecraft:lily_of_the_valley", 4),
                e("minecraft:lilac", 1), e("minecraft:rose_bush", 1), e("minecraft:peony", 1)},
                6, GRASS_ONLY);
        // meadow: flower_meadow (1 种子 x 96 = 4.8 朵, 池 8 态中草占 2 归草池) -> 花 14 permille;
        //   草 = 花内草 5 + tall_grass_2 66 + grass_plain 47 = 118
        def("minecraft:meadow", 14, new Entry[]{
                e("minecraft:allium", 1), e("minecraft:poppy", 1), e("minecraft:azure_bluet", 1),
                e("minecraft:dandelion", 1), e("minecraft:cornflower", 1), e("minecraft:oxeye_daisy", 1)},
                118, PLAIN_GRASS);
        // cherry_grove: flower_cherry = noise(10,5)~7.5 种子 x 96 ~ 36 朵 -> 140 permille
        //   (k 偏大嫌疑, TEST 校准); 池 = pink_petals 16 态采默认单态 (类注 4); 草同 plains 113
        def("minecraft:cherry_grove", 140,
                new Entry[]{ e("minecraft:pink_petals", 1) }, 113, PLAIN_GRASS);
        // ==== 森林系 (花 = flower_default 0.1 + forest_flowers 灌木 0.09 ~ 0.5, 取 1; 草 = patch_grass_forest count2 x 32 ~ 12) ====
        def("minecraft:forest", 1, FOREST_FLOWERS, 12, GRASS_ONLY);
        def("minecraft:birch_forest", 1, FOREST_FLOWERS, 12, GRASS_ONLY);
        def("minecraft:old_growth_birch_forest", 1, FOREST_FLOWERS, 12, GRASS_ONLY);
        def("minecraft:dark_forest", 1, FOREST_FLOWERS, 12, GRASS_ONLY);
        // ==== 丛林系 (花 = flower_warm rarity16 ~ 0.8 -> 1; 草 = patch_grass_jungle count25 x 32 ~ 156) ====
        def("minecraft:jungle", 1, DEFAULT_FLOWERS, 156, JUNGLE_GRASS);
        def("minecraft:sparse_jungle", 1, DEFAULT_FLOWERS, 156, JUNGLE_GRASS);
        def("minecraft:bamboo_jungle", 1, DEFAULT_FLOWERS, 156, JUNGLE_GRASS);
        // ==== 稀树草原 (花 = flower_warm ~ 1; 草 = patch_grass_savanna count20 x 32 ~ 125 + patch_tall_grass rarity5 ~ 4 = 129, 池 32:1) ====
        def("minecraft:savanna", 1, DEFAULT_FLOWERS, 129,
                new Entry[]{ e("minecraft:grass", 32), e("minecraft:tall_grass", 1) });
        def("minecraft:savanna_plateau", 1, DEFAULT_FLOWERS, 129,
                new Entry[]{ e("minecraft:grass", 32), e("minecraft:tall_grass", 1) });
        // windswept_savanna 草 = patch_grass_normal (count5 x 32 ~ 31)
        def("minecraft:windswept_savanna", 1, DEFAULT_FLOWERS, 31, GRASS_ONLY);
        // ==== 针叶系 (花 = flower_default ~ 1) ====
        // taiga 草 = patch_grass_taiga_2 (1 种子 x 32 ~ 6; 池 grass1:fern4) + patch_large_fern (rarity5 ~ 4)
        def("minecraft:taiga", 1, DEFAULT_FLOWERS, 10,
                new Entry[]{ e("minecraft:grass", 1), e("minecraft:fern", 4), e("minecraft:large_fern", 3) });
        def("minecraft:snowy_taiga", 1, DEFAULT_FLOWERS, 10,
                new Entry[]{ e("minecraft:grass", 1), e("minecraft:fern", 4), e("minecraft:large_fern", 3) });
        // old_growth 草 = patch_grass_taiga (count7 x 32 ~ 44; 池 grass1:fern4) + large_fern ~ 4 = 48, 池 2:9:1
        def("minecraft:old_growth_pine_taiga", 1, DEFAULT_FLOWERS, 48,
                new Entry[]{ e("minecraft:grass", 2), e("minecraft:fern", 9), e("minecraft:large_fern", 1) });
        def("minecraft:old_growth_spruce_taiga", 1, DEFAULT_FLOWERS, 48,
                new Entry[]{ e("minecraft:grass", 2), e("minecraft:fern", 9), e("minecraft:large_fern", 1) });
        // ==== 湿地 (swamp 花 = flower_swamp blue_orchid rarity32 ~ 0.4 -> 1; 草 = patch_grass_normal ~ 31; mangrove 无花) ====
        def("minecraft:swamp", 1,
                new Entry[]{ e("minecraft:blue_orchid", 1) }, 31, GRASS_ONLY);
        def("minecraft:mangrove_swamp", 0, null, 31, GRASS_ONLY);
        // ==== 长尾 (花 = flower_default ~ 0.4 取 1 最小可见单位; 草 = patch_grass_badlands 1 种子 x 32 ~ 6) ====
        def("minecraft:beach", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:snowy_beach", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:stony_shore", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:snowy_plains", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:ice_spikes", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:desert", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:badlands", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:eroded_badlands", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:wooded_badlands", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:windswept_hills", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:windswept_forest", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        def("minecraft:windswept_gravelly_hills", 1, DEFAULT_FLOWERS, 6, GRASS_ONLY);
        // (grove/snowy_slopes/frozen_peaks/jagged_peaks/stony_peaks/mushroom_fields/
        //  海洋/河流/下界/末地/洞穴: 无花草条目, 不进表)
    }

    /** 群系花草样式 (未知 key 返回 null = 无花草). */
    public static FlowerStat stat(String biomeKey) {
        return TABLE.get(biomeKey);
    }

    private TdFlowerMap() {
    }
}