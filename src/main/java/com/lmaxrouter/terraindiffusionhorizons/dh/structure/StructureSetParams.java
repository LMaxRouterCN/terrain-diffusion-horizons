package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * vanilla structure_set 的 random_spread 撒点参数 (B2; 设计稿 docs/batch-b-design.md §4.3).
 * [L3 扩展 2026-11-21: entries 字段 — structures[] 名单进参数层, registry 解析在 TdStructureContext]
 *
 * <p>参数来源: 服务端 ResourceManager 读 worldgen/structure_set/*.json
 * (单人端即 vanilla 权威; fork 无覆盖、TEST 实例无 openloader 已实证 2026-11-19).
 * 撒点数学不在本层 — TdStructureSpreadCore 直调 vanilla WorldgenRandom 本尊,
 * 本类只承载 json 参数.</p>
 *
 * <p>白名单 = overworld 适用集合 (15 个); strongholds (concentricRings 型,
 * ring positions 为 lazy 暴力搜索) 与异维度集合 (end_cities / nether_complexes /
 * nether_fossils — json 不带维度信息, 收进来会在主世界产生幻影命中) 不在
 * B2 范围 [设计稿 §4.3 首批范围].</p>
 *
 * <p>注: 2026-11-20 曾因 PowerShell 替换事故全文件损毁 (r->e, p->u),
 * 本版为整文件重写, public 修饰直接写入不再走 exec 替换.</p>
 *
 * @param spacing             格边长 (chunk)
 * @param separation          格内保留区 (potential 偏移上限 = spacing - separation)
 * @param salt                盐 (进 setLargeFeatureWithSalt)
 * @param frequency           频率 (>= 1 = 恒通过)
 * @param frequencyMethod     频率方法后缀 (default / legacy_type_1 / 2 / 3)
 * @param triangular          spread_type = triangular (json 缺省 linear [推断, /locate 可证伪])
 * @param exclusionOtherSet   排除区对方集合名 (null = 无排除区)
 * @param exclusionChunkCount 排除区半宽 (chunk; 双闭正方形 [字节码实证])
 * @param entries             structures[] 名单 (L3 消元循环输入; 名字原样含命名空间,
 *                            Structure 实例解析在 TdStructureContext 装配期)
 */
public record StructureSetParams(int spacing, int separation, int salt, float frequency,
                                 String frequencyMethod, boolean triangular,
                                 String exclusionOtherSet, int exclusionChunkCount,
                                 List<StructureEntry> entries) {

    /** structures[] 单条目: 结构 key 原样 ("minecraft:village_plains") + 权重 (L3). */
    public record StructureEntry(String structure, int weight) {
    }

    /** B2 overworld 白名单 (json 文件名; 2026-11-19 vanilla 全量 json 盘点定稿). */
    public static final Set<String> OVERWORLD_SETS = Set.of(
            "ancient_cities", "buried_treasures", "desert_pyramids", "igloos", "jungle_temples",
            "mineshafts", "ocean_monuments", "ocean_ruins", "pillager_outposts", "ruined_portals",
            "shipwrecks", "swamp_huts", "trail_ruins", "villages", "woodland_mansions");

    private static final Gson GSON = new Gson();

    /**
     * json Reader -> 参数; 非 random_spread 型或坏数据返回 null (调用方跳过该集合).
     * spacing <= separation 判坏: 与 vanilla validate 同款校验
     * ("Spacing has to be larger than separation" [字节码 lambda$validate$1]).
     * [L3] structures[] 一并解析; 空/全无效 entry 名单返回 null (vanilla validate:
     * 集合至少一 entry, 同义拒).
     */
    public static StructureSetParams parse(Reader reader) {
        JsonObject root = GSON.fromJson(reader, JsonObject.class);
        if (root == null) {
            return null;
        }
        JsonObject placement = root.getAsJsonObject("placement");
        if (placement == null || !"random_spread".equals(suffix(placement, "type"))) {
            return null; // concentricRings 等他型: 白名单外双保险
        }
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        if (spacing <= separation) {
            return null; // 白名单内不应出现; 防御 (vanilla 同款校验)
        }
        int salt = placement.get("salt").getAsInt();
        float frequency = placement.has("frequency") ? placement.get("frequency").getAsFloat() : 1.0f;
        String method = placement.has("frequency_reduction_method")
                ? suffix(placement, "frequency_reduction_method") : "default";
        boolean triangular = placement.has("spread_type")
                && "triangular".equals(suffix(placement, "spread_type"));
        String otherSet = null;
        int chunkCount = -1;
        if (placement.has("exclusion_zone")) {
            JsonObject ez = placement.getAsJsonObject("exclusion_zone");
            otherSet = suffix(ez, "other_set");
            chunkCount = ez.get("chunk_count").getAsInt();
        }
        List<StructureEntry> entries = parseEntries(root);
        if (entries.isEmpty()) {
            return null; // 集合无有效 entry: 组装无从下手 (vanilla validate 同义拒)
        }
        return new StructureSetParams(spacing, separation, salt, frequency, method,
                triangular, otherSet, chunkCount, entries);
    }

    /**
     * structures[] -> entry 名单 (L3; villages.json 实证形状: structure 键 + weight).
     * 名字原样保留命名空间 ("minecraft:village_plains" 不剥前缀 — ResourceLocation
     * 直接可解析, 剥了反而丢信息); weight 缺省 1 (防御: vanilla 官方 json 全显式,
     * codec 层面 optionalFieldOf 默认值未考证, 按最常见的 1 处理).
     */
    private static List<StructureEntry> parseEntries(JsonObject root) {
        List<StructureEntry> entries = new ArrayList<>();
        if (!root.has("structures")) {
            return entries;
        }
        for (JsonElement el : root.getAsJsonArray("structures")) {
            if (!el.isJsonObject()) {
                continue; // 防御: 坏条目跳过
            }
            JsonObject o = el.getAsJsonObject();
            if (!o.has("structure")) {
                continue; // 防御: 无结构键
            }
            String structure = o.get("structure").getAsString();
            int weight = o.has("weight") ? o.get("weight").getAsInt() : 1;
            if (weight > 0) {
                entries.add(new StructureEntry(structure, weight));
            }
        }
        return entries;
    }

    /** "minecraft:xxx" -> "xxx" (无冒号原样返回). */
    private static String suffix(JsonObject obj, String key) {
        String v = obj.get(key).getAsString();
        int i = v.lastIndexOf(':');
        return i >= 0 ? v.substring(i + 1) : v;
    }
}