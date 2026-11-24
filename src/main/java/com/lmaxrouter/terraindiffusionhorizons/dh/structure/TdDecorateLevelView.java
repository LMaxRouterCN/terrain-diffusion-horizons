package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.github.xandergos.terraindiffusionmc.world.HeightConverter;
import com.github.xandergos.terraindiffusionmc.world.ScaledAltitude;
import com.lmaxrouter.terraindiffusionhorizons.TdConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest; // entity 子包 [EntityGetter.java L14 import 实证; mojmap 记忆写错包的勘误]
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData; // storage 子包 [LevelAccessor.java L21 import 实证]
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.LightLayer; // [L4·修复2] getBrightness 转发面 [javap 实证: level 包, 非 lighting 子包]
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 装饰视图 (L4; 设计稿 docs/batch-l4-design.md §3.3).
 *
 * <p>包在 TdSilentLevel 外的读写过滤视图, TdBiomeDecorator 把它递给
 * placeWithBiomeCheck: 结构写入与装饰写入共享同一 chunk 池 (串行先后可见,
 * 溢出保留共享 = vanilla 逐 chunk 完成语义).</p>
 *
 * <p>七面覆写, 其余 45 面全转发 delegate:
 * 读 (getBlockState/getFluidState/isStateAtPosition/isFluidAtPosition) =
 * 写入优先 (peekChunk 段内非 air 真值), 空读回退三态水背景 [L4·排障·方案2: 数据窗内 =
 * 床下草 / 水柱 (fluvial 场, 海平面兜底) / 水上空气 — 修湖上浮树浮花 (旧两态把湖床
 * 报草地骗过 would_survive); 窗外/无数据/关 = 旧两态 y < envH 草 [裁决 §3.3:
 * would_survive / shouldSnow 保活; envH = TD 高度场 (装饰前地形; prefetch 驻留后
 * 走 resident 真值); 表面方块特异性折损登记 §4
 * (沙漠仙人掌/死灌木要 sand, 红树要 mud — TEST 后按 biome 查 TdSurfaceBlockMap 升级)];
 * 写 (setBlock 两态) = writableBox 界内转发界外 false (装饰溢出护栏, 防池膨胀);
 * 高度 (getHeight) = (type, chunk) 双键 int[256] 缓存转发 (SnowAndFreeze 每列必查
 * + HeightmapPlacement 热路径; far-lookup 噪声柱贵, 生命周期 = 单 region).</p>
 */
public final class TdDecorateLevelView implements WorldGenLevel {

    private final TdSilentLevel delegate;
    /** 装饰写入界 (region + pad 的 block box); 界外 setBlock 恒 false. */
    private final BoundingBox writableBox;
    /** 高度缓存: type -> chunkLong -> 列高 int[256] (哨兵 MIN_VALUE = 未采样). */
    private final EnumMap<Heightmap.Types, Map<Long, int[]>> heightCache = new EnumMap<>(Heightmap.Types.class);
    /** [L4·方案2] fluvial 水最小强度 (RiverDecorator.MIN_WATER_MASK 同值 8; 跨 mod 无法
     *  共享常量, 源锚定 TerrainDiffusionRiverDecorator L16). */
    private static final int FLUVIAL_MIN_MASK = 8;
    /** [L4·方案2] 水背景数据 (prefetch 窗内三态源); null = 无数据, 全列回退旧两态. */
    private final HeightmapData waterData;
    /** waterData 窗原点 (块, Z 向); 与 writableBox 同界 (prefetch 同公式). */
    private final int dataMinZ;
    /** waterData 窗原点 (块, X 向). */
    private final int dataMinX;
    /** [L4·方案2] 水背景配置 (构造期缓存; getBlockState 热路径不查 config). */
    private final boolean waterBackground;

    TdDecorateLevelView(TdSilentLevel delegate, BoundingBox writableBox,
                        HeightmapData waterData, int dataMinZ, int dataMinX) {
        this.delegate = delegate;
        this.writableBox = writableBox;
        this.waterData = waterData; // [L4·方案2] 可 null (prefetch 关/失败) = 全列回退旧两态
        this.dataMinZ = dataMinZ;
        this.dataMinX = dataMinX;
        this.waterBackground = TdConfig.get().decorationWaterBackground;
    }

    /** 委托真身 (decorator / 调试侧访问用; 面交互全走本类覆写). */
    TdSilentLevel delegate() {
        return delegate;
    }

    /** 背景模式 (观察期日志用): 三态数据在且配置开. */
    boolean hasWaterBackground() {
        return waterData != null && waterBackground;
    }

    // ==== 覆写面: 读 = 写入优先 + 近似背景回退 ====

    @Override
    public BlockState getBlockState(BlockPos pos) {
        ProtoChunk chunk = delegate.peekChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) {
            LevelChunkSection s = delegate.sectionOf(chunk, pos.getY());
            if (s != null) {
                BlockState st = s.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                if (!st.isAir()) {
                    return st; // 写入优先: 结构 / 先序装饰的真值
                }
                // 段存在但该位 air = 未写 (段因邻居写入而建), 回退背景
            }
        }
        return backgroundState(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * 空读背景 [L4·排障·方案2]: 水数据窗内三态 (床下草 / 水柱 / 水上空气 — 修湖上
     * 浮树浮花: 旧两态把湖床报草地骗过树苗 would_survive); 窗外/无数据/配置关
     * 回退旧两态 (envH 用 MOTION_BLOCKING, SnowAndFreeze 同 type, 缓存共享).
     */
    private BlockState backgroundState(int x, int y, int z) {
        if (waterData != null && waterBackground) {
            int dz = z - dataMinZ, dx = x - dataMinX;
            if (dz >= 0 && dz < waterData.height && dx >= 0 && dx < waterData.width) {
                short bedMetres = waterData.heightmap[dz][dx];
                // 方块 Y = convert(米) - 1 (RiverDecorator L48 同式: convert 返回首自由格)
                int bedTop = HeightConverter.convertToMinecraftHeight(bedMetres) - 1;
                int waterTop = surfaceWaterBlockY(dz, dx); // [L4·修复1] 水顶算术单点维护 (getHeight 分流共用)
                if (y <= bedTop) return Blocks.GRASS_BLOCK.defaultBlockState();
                if (waterTop != Integer.MIN_VALUE && y <= waterTop) {
                    return Blocks.WATER.defaultBlockState();
                }
                return Blocks.AIR.defaultBlockState();
            }
        }
        // [L4·修复3] 背景回退用纯 TD 地形 (terrainHeight): 不含水面/写入修正 —
        // 写入顶会令虚拟地形填满到结构顶 (错误); 已写格子走写入优先路径, 背景只表达地形
        int envH = terrainHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return y < envH ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /**
     * [L4·修复1] 列水面方块 Y (背景三态与 getHeight 表面族分流共用): fluvial 场有效列
     * 返回水面方块 Y; 无效列走海平面兜底 (床顶在海面下 -> 水面方块 = 海平面 - 1);
     * 无兜底 (干列) 返回 MIN_VALUE. 从 backgroundState 提取, 水顶算术单点维护.
     */
    private int surfaceWaterBlockY(int dz, int dx) {
        short bedMetres = waterData.heightmap[dz][dx];
        // 方块 Y = convert(米) - 1 (RiverDecorator L48 同式: convert 返回首自由格)
        int bedTop = HeightConverter.convertToMinecraftHeight(bedMetres) - 1;
        int waterTop = fluvialSurfaceBlockY(dz, dx, bedMetres);
        if (waterTop == Integer.MIN_VALUE && bedTop < ScaledAltitude.SEA_LEVEL - 1) {
            // 海平面型兜底: 床顶在海面下 -> 水面方块 = 海平面 - 1 (HeightConverter 语义 0 米 -> 63 首自由格)
            waterTop = ScaledAltitude.SEA_LEVEL - 1;
        }
        return waterTop;
    }

    /**
     * fluvial 水顶方块 Y; 无效列返回 MIN_VALUE (调用方走海平面兜底). 判据与
     * RiverDecorator L42-44 全同: strength >= FLUVIAL_MIN_MASK &&
     * surface != NO_FLUVIAL_WATER && surface >= bed. 薄流 edge (水面贴床,
     * RiverDecorator 强制单层水) 此处水层为空 — 误差一格不伤判定语义, 折损登记.
     */
    private int fluvialSurfaceBlockY(int dz, int dx, short bedMetres) {
        if (waterData.riverWater == null || waterData.riverWaterSurface == null) {
            return Integer.MIN_VALUE;
        }
        if (waterData.riverWater[dz][dx] < FLUVIAL_MIN_MASK) return Integer.MIN_VALUE;
        short surface = waterData.riverWaterSurface[dz][dx];
        if (surface == HeightmapData.NO_FLUVIAL_WATER || surface < bedMetres) {
            return Integer.MIN_VALUE;
        }
        return HeightConverter.convertToMinecraftHeight(surface) - 1;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState(); // 背景 (三态含水) 参与: 近水判定走真实语义
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(this.getBlockState(pos)); // 走 View 背景 (非 delegate 空气)
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(this.getFluidState(pos));
    }

    // ==== 覆写面: 写 = writableBox 界检查 ====

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int limit) {
        if (!writableBox.isInside(pos)) {
            return false; // 装饰界外丢弃 (溢出护栏; caller 无重试语义, 宁缺勿扩)
        }
        return delegate.setBlock(pos, state, flags, limit);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return this.setBlock(pos, state, flags, 512);
    }

    // ==== 覆写面: 高度 = 纯地形双键缓存 + 廉价修正链现算 ====

    /**
     * [L4·修复1/3] 高度语义链: max(纯 TD 地形, 表面族水面修正, 写入响应顶).
     * 修正项全部现算不入缓存 — far-lookup 采样 (贵) 才进缓存, 水面/写入 (廉价) 每次直读,
     * 永不过期 (缓存终值会在后续写入抬顶后变旧: A 采样 -> B 写入 -> C 查 = IOOBE 复活).
     */
    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return Math.max(Math.max(terrainHeight(type, x, z), surfaceTop(type, x, z)),
                delegate.writtenTop(type, x, z));
    }

    /**
     * 纯 TD 地形高度 (双键缓存, 旧 getHeight 原体): 贵的 far-lookup 采样走
     * (type, chunk) 双键 int[256] 缓存. [L4·修复3] 背景回退 (backgroundState 窗外
     * 两态) 亦走此面 — 背景只表达虚拟地形, 不含水面/写入修正 (已写格子走写入优先路径).
     */
    private int terrainHeight(Heightmap.Types type, int x, int z) {
        Map<Long, int[]> byChunk = heightCache.computeIfAbsent(type, t -> new HashMap<>());
        int[] col = byChunk.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), k -> {
            int[] a = new int[256];
            Arrays.fill(a, Integer.MIN_VALUE); // 哨兵: 未采样列
            return a;
        });
        int ci = (x & 15) * 16 + (z & 15);
        int v = col[ci];
        if (v == Integer.MIN_VALUE) {
            v = delegate.getHeight(type, x, z); // far-lookup TD 高度场 (装饰前地形)
            col[ci] = v;
        }
        return v;
    }

    /**
     * [L4·修复1] 表面族水列修正: 返回水面首自由格; 干列/数据窗外/床族/背景关 = MIN_VALUE.
     * 族判定谓词自证 (零硬编码): type.isOpaque().test(WATER) — 表面族 (WORLD_SURFACE* =
     * NOT_AIR; MOTION_BLOCKING* = blocksMotion || 有流体) 对水 true, 床族 (OCEAN_FLOOR*
     * = MATERIAL_MOTION_BLOCKING, 水不计入) false, 与 vanilla Heightmap$Types 同源.
     */
    private int surfaceTop(Heightmap.Types type, int x, int z) {
        if (waterData == null || !waterBackground) {
            return Integer.MIN_VALUE; // 背景关/无数据: 一切如旧
        }
        if (!type.isOpaque().test(Blocks.WATER.defaultBlockState())) {
            return Integer.MIN_VALUE; // 床族: 水不计入表面, 走 TD 地形值
        }
        int dz = z - dataMinZ, dx = x - dataMinX;
        if (dz < 0 || dz >= waterData.height || dx < 0 || dx >= waterData.width) {
            return Integer.MIN_VALUE; // 数据窗外: 无背景 (回退现状)
        }
        int waterTop = surfaceWaterBlockY(dz, dx);
        return waterTop == Integer.MIN_VALUE ? Integer.MIN_VALUE : waterTop + 1; // 首自由格
    }

    // ==== 转发面 (值语义与 TdSilentLevel 逐面一致) ====

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return delegate.getBlockEntity(pos);
    }

    @Override
    public ChunkAccess getChunk(int cx, int cz, ChunkStatus status, boolean load) {
        return delegate.getChunk(cx, cz, status, load);
    }

    @Override
    public boolean hasChunk(int cx, int cz) {
        return delegate.hasChunk(cx, cz);
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int limit) {
        return delegate.destroyBlock(pos, dropBlock, entity, limit);
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        return delegate.removeBlock(pos, isMoving);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return delegate.getNoiseBiome(x, y, z); // TDBS 直采 (biome 与写入无关)
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return delegate.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public BiomeManager getBiomeManager() {
        return delegate.getBiomeManager(); // 本地 manager (manager 的采样源是 delegate 本体, 链路正确)
    }

    @Override
    public long getSeed() {
        return delegate.getSeed();
    }

    @Override
    public RegistryAccess registryAccess() {
        return delegate.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }

    @Override
    public int getMaxBuildHeight() {
        return delegate.getMaxBuildHeight();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public net.minecraft.world.level.dimension.DimensionType dimensionType() {
        return delegate.dimensionType();
    }

    @Override
    public boolean isClientSide() {
        return delegate.isClientSide();
    }

    @Override
    public int getSkyDarken() {
        return delegate.getSkyDarken();
    }

    @Override
    public Difficulty getDifficulty() {
        return delegate.getDifficulty();
    }

    @Override
    public LevelData getLevelData() {
        return delegate.getLevelData();
    }

    @Override
    public RandomSource getRandom() {
        return delegate.getRandom();
    }

    @Override
    public List<? extends Player> players() {
        return delegate.players();
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> filter) {
        return delegate.getEntities(type, area, filter);
    }

    @Override
    public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) {
        return delegate.getEntities(entity, area, predicate);
    }

    @Override
    public long dayTime() {
        return delegate.dayTime();
    }

    @Override
    public net.minecraft.world.level.BlockGetter getChunkForCollisions(int cx, int cz) {
        return delegate.getChunkForCollisions(cx, cz);
    }

    @Override
    public ServerLevel getLevel() {
        return delegate.getLevel();
    }

    @Override
    public MinecraftServer getServer() {
        return delegate.getServer();
    }

    @Override
    public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
        return delegate.getWorldBorder();
    }

    @Override
    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver); // delegate 内部走本地 manager, 值正确
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return delegate.getShade(direction, shade);
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return delegate.getBrightness(layer, pos); // [L4·修复2] 转发 (default 会打进 getLightEngine UOE 绊线)
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return delegate.getRawBrightness(pos, amount);
    }

    @Override
    public void playSound(Player player, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        delegate.playSound(player, pos, sound, source, volume, pitch);
    }

    @Override
    public void addParticle(ParticleOptions data, double x, double y, double z, double xs, double ys, double zs) {
        delegate.addParticle(data, x, y, z, xs, ys, zs);
    }

    @Override
    public void levelEvent(Player player, int type, BlockPos pos, int data) {
        delegate.levelEvent(player, type, pos, data);
    }

    @Override
    public void gameEvent(GameEvent event, Vec3 position, GameEvent.Context context) {
        delegate.gameEvent(event, position, context);
    }

    @Override
    public long nextSubTickCount() {
        return delegate.nextSubTickCount();
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return delegate.getBlockTicks();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return delegate.getFluidTicks();
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return delegate.getCurrentDifficultyAt(pos);
    }

    @Override
    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
        return delegate.getChunkSource(); // UOE 绊线转发 (crash 驱动保持)
    }

    @Override
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
        return delegate.getLightEngine(); // UOE 绊线转发
    }

    @Override
    public int getSignal(BlockPos pos, Direction direction) {
        return delegate.getSignal(pos, direction);
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction direction) {
        return delegate.getDirectSignal(pos, direction);
    }
}