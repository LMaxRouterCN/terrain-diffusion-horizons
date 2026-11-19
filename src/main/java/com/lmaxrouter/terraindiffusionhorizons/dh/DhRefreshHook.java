package com.lmaxrouter.terraindiffusionhorizons.dh;

import com.lmaxrouter.terraindiffusionhorizons.dh.lane.LaneRefreshHook;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.world.IDhServerWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DH 重拉钩子 (B2; 设计稿 §4.4 PassB, 获取链十轮 javap 终稿 [长期记忆: 125]).
 *
 * <p>链路: 车道算完 -> 本钩子请求 DH 重拉该 region -> DH 再进 generate -> 命中缓存表
 * -> 新 LOD 替换 (设计稿 §3 数据流)。core 直调 + compileOnly 钉 3.3.3
 * (WrapperFactory_forge S1 先例, 非破戒)。</p>
 *
 * <p>防御姿态: 每级 null 静默返回 (世界已卸载 / DH 未起 / provider 不到位 —
 * 数据已在缓存表无损, 兜底 = 视觉延迟到下次自然拉取, 设计稿 §9);
 * 全体 try-catch 自吞 — 钩子失败不折损车道数据, 也不进黑名单 [LaneRefreshHook 契约]。</p>
 */
public final class DhRefreshHook implements LaneRefreshHook {

    public static final DhRefreshHook INSTANCE = new DhRefreshHook();

    private static final Logger LOG = LoggerFactory.getLogger("TDH-Lane");

    /** 当前世界 wrapper (core 面; LevelLoad 时 DhCompat bind, Unload 清 — 生命周期与 DhCompat 对齐). */
    private volatile ILevelWrapper levelWrapper;

    private DhRefreshHook() {
    }

    /** 绑定世界 (DhCompat onLevel 调; api wrapper instanceof core 防御后强转). */
    void bind(ILevelWrapper wrapper) {
        this.levelWrapper = wrapper;
    }

    /** 解绑 (DhCompat unload 调). */
    void unbind() {
        this.levelWrapper = null;
    }

    @Override
    public void requestRefresh(byte detailLevel, int posX, int posZ) {
        try {
            ILevelWrapper wrapper = levelWrapper;
            if (wrapper == null) {
                return; // 世界已卸载, 迟到的车道完成: 静默
            }
            // 懒解析 (LevelLoad 时序未验, 此处规避): 每次刷新现场取 [长期记忆: 125]
            IDhServerWorld serverWorld = SharedApi.tryGetDhServerWorld();
            if (serverWorld == null) {
                return; // DH 世界未起: 数据已落表, 下次 generate 自然命中
            }
            IDhServerLevel level = serverWorld.getOrLoadServerLevel(wrapper); // 幂等
            if (level == null) {
                return;
            }
            // getFullDataProvider 声明于 IDhLevel (返回基类 FullDataSourceProviderV2),
            // 运行时实例 = GeneratedFullDataSourceProvider [获取链终稿]
            if (!(level.getFullDataProvider() instanceof GeneratedFullDataSourceProvider provider)) {
                return; // 非 DH 生成链 provider: 保守放弃
            }
            int size = 1 << (detailLevel + 6); // 数据源边长(块) [坐标语义定案, 记忆 088 同源]
            // encodeContaining + 数据源中心块: 规避段网格对齐疑云 (getMinCornerBlockX 半宽偏移未读全)
            long sectionPos = DhSectionPos.encodeContaining((byte) (detailLevel + 6),
                    new DhBlockPos(posX * size + size / 2, 0, posZ * size + size / 2));
            provider.queuePositionForRetrieval(sectionPos); // Long 自动装箱 [javap 签名]
        } catch (Exception e) {
            LOG.warn("[refresh] DH 重拉请求失败 (数据已落缓存表, 无损): {}", e.toString());
        }
    }
}