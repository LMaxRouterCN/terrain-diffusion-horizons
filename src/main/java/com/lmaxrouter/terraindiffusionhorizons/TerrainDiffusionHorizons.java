package com.lmaxrouter.terraindiffusionhorizons;

import com.lmaxrouter.terraindiffusionhorizons.dh.DhCompat;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terrain Diffusion Horizons 主类 (S2a mod 壳).
 *
 * <p>定位: 把 Terrain Diffusion (TD) 管线输出桥接为 Distant Horizons (DH) 的远距生成数据源.
 * DH 与 TD 均为软依赖: 缺席任意一方时本 mod 静默闲置, 不影响游戏.</p>
 */
@Mod(TerrainDiffusionHorizons.MOD_ID)
public class TerrainDiffusionHorizons {

    public static final String MOD_ID = "terraindiffusionhorizons";

    public static final Logger LOG = LoggerFactory.getLogger("TerrainDiffusionHorizons");

    public TerrainDiffusionHorizons() {
        // [待验证-S2b] DH 2.3.2-b 的 modid, 暂按 distanthorizons 写
        boolean hasDh = ModList.get().isLoaded("distanthorizons");
        boolean hasTd = ModList.get().isLoaded("terrain_diffusion_mc");
        LOG.info("壳启动: Distant Horizons={} / Terrain Diffusion={}", hasDh, hasTd);
        if (hasDh && hasTd) {
            // 双宿主在场才挂钩; 只有壳先行 (S2a), 桥接实现 S2b 落地
            DhCompat.register();
        } else {
            LOG.info("宿主不全, 兼容层闲置 (这是设计内行为, 非错误)");
        }
    }
}