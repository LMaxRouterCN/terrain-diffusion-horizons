package com.lmaxrouter.terraindiffusionhorizons;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TDH 配置 (TOML 版; max 2026-11-18 指令: JSON -> TOML 带中文注释,
 * 位置 config/terrain-diffusion-mc/ 与主模组同文件夹).
 *
 * <p>播种式落盘: 首次运行 (文件不存在) 写出带注释的默认模板; 之后读用户手改的文件.
 * 文件损坏时回退默认值且不覆盖坏文件 (日志警告, 便于人工排查).
 * 运行期零自动写盘 — 落盘只发生在播种时.</p>
 *
 * <p>解析: night-config (Forge 运行时自带; 编译期 compileOnly 借道 mavenCentral).
 * 取值只走 javap 实证过的 getOrElse(List 路径) 形式, 单串 dotted 路径行为不依赖.</p>
 */
public final class TdConfig {

    /** 配置目录: 与主模组 (terrain-diffusion-mc) 同住. */
    private static final Path CONFIG_DIR =
            FMLPaths.CONFIGDIR.get().resolve("terrain-diffusion-mc");
    /** 配置文件. */
    private static final Path CONFIG_PATH =
            CONFIG_DIR.resolve("terrain-diffusion-horizons.toml");
    /** 旧版 JSON 配置 (Gson 时代), 检出提示手动迁移; 不自动迁移不删 (处置归 max). */
    private static final Path LEGACY_JSON_PATH =
            FMLPaths.CONFIGDIR.get().resolve("terraindiffusionhorizons.json");

    // ---- 配置字段 (与 TOML 键一一对应; 默认值 = 字段初始化值) ----

    /** [general] 总开关: false = 不注册生成器, DH 走原生路径 (改动需重启游戏生效). */
    public boolean enabled = true;

    /** [terrain] 表面方块 fallback (原版 ID): useBiomeSurface=false 或群系解析失败时使用 (纯几何模式旧路径). */
    public String surfaceBlock = "minecraft:stone";

    /** [terrain] 填充群系 fallback (原版 ID): 纯几何模式 (useBiomeSurface=false) 下 data point 携带的群系. */
    public String fillerBiome = "minecraft:plains";

    /** [terrain] true = TD 群系驱动表面方块 (与 chunk 生成同源); false = 纯几何占位 (内存 ~150MB). */
    public boolean useBiomeSurface = true;

    /** [terrain.surfaceBlockOverrides] 群系 key -> 方块 ID 稀疏覆盖, 仅覆地表字段 (水列方块与冰盖不受影响; 空 = 全用内置表). */
    public Map<String, String> surfaceBlockOverrides = new LinkedHashMap<>();

    /** [vegetation] L1 花草色斑开关: 群系花池查表, 列顶 1 格按 vanilla 密度概率换花色 (远景观感; 统计近似, L4 地物同源后由真实数据替代). */
    public boolean vegetationColoring = true;
    /** [vegetation] 草丛色斑开关 (留口): 草丛与草方块共用群系 grass colormap, 换上大概率等于没换, 默认关;
     *  若实测 DH 采样使两色分家, 开此即启用 (TdFlowerMap 草池数据已备). */
    public boolean grassColoring = false;

    // ---- 车道配置键 ([structure]/[features]; B1 四键立骨架 [docs/batch-b-design.md §4.1], B2/C/L4 逐批扩充至 11 键) ----

    /** [structure] 结构车道总开关: false = 结构车道不计算 (B2 载荷: TdStructureOracle 同源撒点). */
    public boolean structureEnabled = true;

    /** [structure] 结构车道异步: true = 地形 LOD 先发布, 车道后台算完刷新补全;
     *  false = 同步模式, generate 调用栈内联, 地形 LOD 扣住等车道算完才显示 (一次成型无闪烁). */
    public boolean structureAsync = true;
    /** [structure] 扫描外扩 (chunk; L3 组装: bbox 跨 region 界的结构防截断; 默认 16 = 256 块 >= mansion 尺度). */
    public int structurePadChunks = 16;
    /** [structure] 列折叠段数上限 (C-light 多段折叠; 1 = 旧单段行为 = 配置级回滚; 小 = 省 FPS). */
    public int structureColumnSegments = 6;

    /** [structure] 贴地换色深度 (B 案: 埋地结构顶段距地表 <= 此值时表面 1 格换结构块色; 负 = 关闭). */
    public int structureSurfaceBlendDepth = 3;

    /** [features] 地物车道总开关 (vanilla 群系装饰复刻, L4; L1 花草色斑是同步快路径, 开关在 [vegetation] 段). */
    public boolean featuresEnabled = true;

    /** [features] 地物车道异步 (语义同 structure.async). */
    public boolean featuresAsync = true;
    /** [L4] 装饰步集: GenerationStep.Decoration 枚举名表 (装配期解析, 大小写不敏感; 空/全 miss = 装饰 no-op).
     *  默认步集 A (决策①): 植被 + 顶雪 — LOD 可见层; 矿/湖/泉类步对 LOD 无益不入. */
    public List<String> decorationSteps = List.of("vegetal_decoration", "top_layer_modification");
    /** [L4] 装饰门控 (决策⑤, API 级档位): detailLevel > 此值的 region 不跑装饰 (0 = 只 dl0, 与地形车道同档). */
    public int decorationMaxDetailLevel = 0;

    /** [L4·排障] 装饰窗 heightmap prefetch (方案1): far-lookup 会话外预取并驻留, 装饰期
     *  mixin 高度查询走 resident 真值 (修方格错树); false = 回退旧近似路径 (且无水背景数据). */
    public boolean decorationPrefetch = true;

    /** [L4·排障] 装饰三态水背景 (方案2): 数据窗内背景 = 床下草/水柱/水上空气 (修湖上浮树浮花);
     *  false = 回退旧两态 (y < envH 草). 依赖 prefetch 数据, prefetch 关闭时此开关无效. */
    public boolean decorationWaterBackground = true;

    // ---- 加载与播种 ----

    private static volatile TdConfig instance;

    /** 懒加载单例; 首次调用发生在 mod 构造期 (主线程, 无竞争), 双检锁防御性保留. */
    public static TdConfig get() {
        TdConfig cfg = instance;
        if (cfg == null) {
            synchronized (TdConfig.class) {
                if (instance == null) {
                    instance = load();
                }
                cfg = instance;
            }
        }
        return cfg;
    }

    private static TdConfig load() {
        Logger log = TerrainDiffusionHorizons.LOG;
        // 旧 JSON 检出: 提示手动迁移 (无自动迁移 — 无存量用户; 旧文件保留, 处置归 max)
        if (Files.isRegularFile(LEGACY_JSON_PATH)) {
            log.info("检出旧版 JSON 配置 {} (弃用中), 新配置在 {}; 手改值请自行迁移",
                    LEGACY_JSON_PATH, CONFIG_PATH);
        }
        if (Files.isRegularFile(CONFIG_PATH)) {
            try {
                // 只读解析: parse(String) 不碰磁盘写入, 坏文件原样保留
                Config parsed = TomlFormat.instance().createParser().parse(Files.readString(CONFIG_PATH));
                return fromToml(parsed);
            } catch (Exception e) {
                // ParsingException / IO 全兜底: 回退默认, 不覆盖坏文件 (便于人工排查)
                log.warn("TOML 配置解析失败, 回退默认 (不覆盖坏文件): {}", e.toString());
            }
            return new TdConfig();
        }
        // 播种: 无文件时写一份带注释的默认模板出去
        TdConfig cfg = new TdConfig();
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_PATH, TEMPLATE);
        } catch (Exception e) {
            log.warn("默认配置落盘失败 (无害, 下次启动再试): {}", e.toString());
        }
        return cfg;
    }

    /** 从解析后的 TOML 树取值 (全部 getOrElse 带默认: 缺键/类型不符 = 默认值, 全表兜底). */
    private static TdConfig fromToml(UnmodifiableConfig c) {
        TdConfig cfg = new TdConfig();
        cfg.enabled = c.getOrElse(List.of("general", "enabled"), Boolean.TRUE);
        cfg.surfaceBlock = c.getOrElse(List.of("terrain", "surfaceBlock"), cfg.surfaceBlock);
        cfg.fillerBiome = c.getOrElse(List.of("terrain", "fillerBiome"), cfg.fillerBiome);
        cfg.useBiomeSurface = c.getOrElse(List.of("terrain", "useBiomeSurface"), Boolean.TRUE);
        cfg.vegetationColoring = c.getOrElse(List.of("vegetation", "coloring"), Boolean.TRUE);
        cfg.grassColoring = c.getOrElse(List.of("vegetation", "grassColoring"), Boolean.FALSE);
        // [B1] 车道四键 (缺键/类型不符 = 默认值)
        cfg.structureEnabled = c.getOrElse(List.of("structure", "enabled"), Boolean.TRUE);
        cfg.structureAsync = c.getOrElse(List.of("structure", "async"), Boolean.TRUE);
        // [L3] 组装扫描外扩 (chunk): bbox 跨 region 界的结构防截断 (默认 16 = 256 块 >= mansion 尺度)
        cfg.structurePadChunks = c.getOrElse(List.of("structure", "pad_chunks"), 16);
        // [C批·C-light] 列折叠段数上限 / [C批·B案] 贴地换色深度
        cfg.structureColumnSegments = c.getOrElse(List.of("structure", "column_segments"), 6);
        cfg.structureSurfaceBlendDepth = c.getOrElse(List.of("structure", "surface_blend_depth"), 3);
        cfg.featuresEnabled = c.getOrElse(List.of("features", "enabled"), Boolean.TRUE);
        cfg.featuresAsync = c.getOrElse(List.of("features", "async"), Boolean.TRUE);
        // [L4] 装饰步集: TOML 数组 -> List<String> (非串元素剔除; 缺键 = 字段默认)
        Object rawSteps = c.get(List.of("features", "decoration_steps"));
        if (rawSteps instanceof List<?> stepList) {
            cfg.decorationSteps = stepList.stream()
                    .filter(String.class::isInstance).map(String.class::cast).toList();
        }
        cfg.decorationMaxDetailLevel = c.getOrElse(List.of("features", "decoration_max_detail_level"), 0);
        // [L4·排障] 方案1/2 开关 (缺键/类型不符 = 默认 true)
        cfg.decorationPrefetch = c.getOrElse(List.of("features", "decoration_prefetch"), Boolean.TRUE);
        cfg.decorationWaterBackground = c.getOrElse(List.of("features", "decoration_water_background"), Boolean.TRUE);
        // overrides: 子表 [terrain.surfaceBlockOverrides]; 缺失/类型不符 = 空 map
        Object raw = c.get(List.of("terrain", "surfaceBlockOverrides"));
        if (raw instanceof UnmodifiableConfig sub) {
            for (Map.Entry<String, Object> e : sub.valueMap().entrySet()) {
                if (e.getValue() instanceof String block) {
                    cfg.surfaceBlockOverrides.put(e.getKey(), block);
                }
            }
        }
        return cfg;
    }

    /** 默认 TOML 模板 (播种写盘; 注释即配置的正式文档). */
    private static final String TEMPLATE = """
            # ============================================================
            # Terrain Diffusion Horizons 配置
            # 修改后重启游戏生效 (游戏启动时读取一次).
            # ============================================================

            [general]
            # 总开关: false = 关闭本模组, DH 自己生成远景 (需重启游戏生效).
            enabled = true

            [terrain]
            # 兜底表面方块 (原版 ID): 群系表面关闭或解析失败时使用.
            surfaceBlock = "minecraft:stone"
            # 兜底群系 (原版 ID): 群系表面关闭时远景携带的群系 (影响草色).
            fillerBiome = "minecraft:plains"
            # true = 表面方块按群系 (草/沙/雪, 与原版一致); false = 全用兜底方块 (内存 ~150MB).
            useBiomeSurface = true

            # 群系 key -> 地表方块覆盖 (原版 ID), 只改地表那一格, 空 = 全用默认.
            # 取消注释并按需增改:
            # [terrain.surfaceBlockOverrides]
            # "minecraft:plains" = "minecraft:snow_block"

            [vegetation]
            # 花草色斑: 远景的草地按该群系花的密度点缀花色 (只换颜色, 不改高度).
            # 近似效果; 开启下方 [features] 的远景植被后, 有植物的区域由真实植物接管.
            coloring = true
            # 草丛色斑: 默认关 — 远景里草丛与草方块本来同色, 无需开.
            # 若远景发现草丛与草方块颜色分家, 再开此项.
            grassColoring = false

            [structure]
            # 远景结构 (村庄/神殿/矿井等): 位置与原版一致, 细节简化.
            # 总开关: false = 不生成远景结构.
            enabled = true
            # 异步: true = 地形先出图, 结构算完自动补上 (不拖慢地形出图);
            #       false = 同步, 等结构算完一起显示 (一次成型, 无刷新闪烁).
            async = true
            # 扫描外扩 (区块数): 结构可能横跨扫描边界, 外扩防止大型结构被截断 (默认 16 已足够).
            pad_chunks = 16
            # 结构每列的方块层数上限 (如小屋的柱/墙/顶分层):
            # 1 = 旧行为 (每列一层); 层数多越还原, 渲染开销也越高, 6 为实测平衡值.
            column_segments = 6
            # 埋地结构表面染色: 埋在地下的结构 (路/农田) 顶面距地表 <= 此值时,
            # 地表 1 格换用结构方块的颜色; 负数 = 关闭. 悬空结构不受影响.
            surface_blend_depth = 3

            [features]
            # 远景植被 (树/花/雪盖): 位置与原版一致; 花草色斑的开关在上方 [vegetation] 段, 不受此段影响.
            # 总开关: false = 不生成远景植被.
            enabled = true
            # 异步: 同 [structure].async.
            async = true
            # 参与的生成步 (合法值见下方, 大小写不敏感).
            # 默认 = 植被 + 顶雪 (树/花/草/蘑菇/雪盖).
            # 合法值: raw_generation, lakes, local_modifications, underground_structures,
            #   surface_structures, strongholds, underground_ores, underground_decoration,
            #   fluid_springs, vegetal_decoration, top_layer_modification
            # 空数组 = 关闭远景植被.
            decoration_steps = ["vegetal_decoration", "top_layer_modification"]
            # 档位上限: 更远的档位不跑远景植被.
            # 默认 0 = 只最近的档位 (更远的档位一格对应的地太宽, 放不下单格植物).
            decoration_max_detail_level = 0
            # 高度修正: 提前查好该处的地面高度, 植物放到真实高度上
            # (修远景植被的方格状错位和错位的树); false = 关闭, 回到旧算法.
            decoration_prefetch = true
            # 水面判定: 放植物前先查此处是否有水 (修湖上浮树浮花);
            # false = 关闭. 依赖高度修正, 该项关闭时本项无效.
            decoration_water_background = true
            """;
}