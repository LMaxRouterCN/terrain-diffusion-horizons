package com.lmaxrouter.terraindiffusionhorizons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TDH 配置 (案②α 五键版).
 *
 * <p>播种式落盘: 首次运行在 config/ 写出默认 JSON, 之后读用户手改的文件;
 * 文件损坏时回退默认值且不覆盖坏文件 (日志警告, 便于人工排查).</p>
 */
public final class TdConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 配置文件: config/terraindiffusionhorizons.json */
    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("terraindiffusionhorizons.json");

    // ---- 五个配置键 (案②α: 表面方块群系驱动) ----

    /** 总开关: false = 不注册生成器, DH 走原生路径 (改动需重进世界生效). */
    public boolean enabled = true;

    /** 表面方块 fallback (原版 ID): useBiomeSurface=false 或群系解析失败时使用 (S2b 旧主路径降级). */
    public String surfaceBlock = "minecraft:stone";

    /** 填充群系 fallback (原版 ID): 纯几何模式 (useBiomeSurface=false) 下 data point 携带的群系. */
    public String fillerBiome = "minecraft:plains";

    /** 案②α: true = TD 群系驱动表面方块 (withBiomes 查询 + 表映射, 与 chunk 生成同源); false = 纯几何占位 (内存 ~150MB). */
    public boolean useBiomeSurface = true;

    /** 案②α: 群系 key -> 方块 ID 稀疏覆盖, 仅覆地表字段 (水列方块与冰盖不受影响; 空 = 全用内置表). */
    public Map<String, String> surfaceBlockOverrides = new LinkedHashMap<>();

    // ---- 加载与播种 ----

    private static volatile TdConfig instance;

    /** 懒加载单例; 首次调用发生在 DhCompat 注册前 (mod 构造期, 主线程, 无竞争). */
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
        if (Files.isRegularFile(CONFIG_PATH)) {
            try {
                TdConfig cfg = GSON.fromJson(Files.readString(CONFIG_PATH), TdConfig.class);
                if (cfg != null) {
                    return cfg;
                }
                log.warn("配置解析为 null, 回退默认: {}", CONFIG_PATH);
            } catch (Exception e) {
                log.warn("配置读取失败, 回退默认 (不覆盖坏文件): {}", e.toString());
            }
        }
        // 播种: 无文件 (或损坏) 时写一份默认出去
        TdConfig cfg = new TdConfig();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(cfg));
        } catch (Exception e) {
            log.warn("默认配置落盘失败 (无害, 下次启动再试): {}", e.toString());
        }
        return cfg;
    }
}
