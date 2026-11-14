package com.lmaxrouter.terraindiffusionhorizons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TDH 配置 (S2b 三键版).
 *
 * <p>播种式落盘: 首次运行在 config/ 写出默认 JSON, 之后读用户手改的文件;
 * 文件损坏时回退默认值且不覆盖坏文件 (日志警告, 便于人工排查).</p>
 */
public final class TdConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 配置文件: config/terraindiffusionhorizons.json */
    private static final Path CONFIG_PATH =
            FMLPaths.CONFIGDIR.get().resolve("terraindiffusionhorizons.json");

    // ---- 三个配置键 (S2b; S4 扩展 TD 表面规则项) ----

    /** 总开关: false = 不注册生成器, DH 走原生路径 (改动需重进世界生效). */
    public boolean enabled = true;

    /** S2b 占位表面方块 (原版 ID); S4 换成 TD biomeIndexes + climate 推导. */
    public String surfaceBlock = "minecraft:stone";

    /** S2b 占位填充群系 (原版 ID); S4 换成 keyForIndex 真映射. */
    public String fillerBiome = "minecraft:plains";

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
