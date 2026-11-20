package com.lmaxrouter.terraindiffusionhorizons.dh.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.entity.EntityTypeTest; // entity 子包 [EntityGetter import 块实证]
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * 哑世界 (L3; 设计稿 docs/batch-c-design.md §2.5).
 *
 * <p>结构组装的方块收集器: piece.postProcess 在本对象上写方块, 落进懒建的
 * ProtoChunk (五参便捷构造, mojmap L50 实证) — 纯内存后端, 真世界零副作用.
 * 方块写入路径已裁决 (SinglePoolElement/StructureTemplate 源码实证):
 * placeInWorld 全部 setBlock 走传入的 ServerLevelAccessor 参数 (本类), getLevel()
 * 只用于取 server / templateManager (只读), 返回真身安全.</p>
 *
 * <p><b>crash 驱动补面</b>: 未确认的接口面缺漏由 javac 编译错清单补全,
 * 运行期未用面按异常栈逐面填 — 设计稿既定工作流, 非缺陷. 桩面分两级:
 * no-op (事件/粒子类, 结构生成必不触; tick 类 2026-11-21 转正: 液体方块放置必经调度, 改为静默丢弃 [长期记忆: 153]) 与 UOE (难度/光照类, 触发即打方法名).</p>
 *
 * <p>已知坑 (本类已防): ProtoChunk 五参构造的 sections 数组全 null,
 * 直接 setBlockState 会 NPE — setBlock 前按需补建 LevelChunkSection
 * (读路径遇 null 段按空气处理, 不产生分配).</p>
 */
public final class TdSilentLevel implements WorldGenLevel {

    private final TdStructureContext.Snapshot ctx;
    private final BiomeSource biomeSource;
    /** 组装期 chunk 池: bbox 逐 chunk placeInChunk 时懒建, 生命周期 = 单次 region 组装. */
    private final Map<ChunkPos, ProtoChunk> chunks = new LinkedHashMap<>();
    /** getRandom 兜底 (piece 级随机全部显式传参, 此实例仅满足接口面). */
    private final RandomSource random = RandomSource.create();

    TdSilentLevel(TdStructureContext.Snapshot ctx) {
        this.ctx = ctx;
        this.biomeSource = ctx.generator().getBiomeSource();
    }

    /** 折叠源: 本组装写过的全部 chunk (TdStructureAssembleCore 列折叠消费). */
    Collection<ProtoChunk> chunks() {
        return chunks.values();
    }

    private TdSilentLevel self() {
        return this;
    }

    // ==== 方块后端 (BlockGetter + LevelWriter) ====

    @Override
    public BlockState getBlockState(BlockPos pos) {
        ProtoChunk chunk = getOrCreateChunk(pos.getX() >> 4, pos.getZ() >> 4);
        LevelChunkSection s = sectionOf(chunk, pos.getY());
        return s == null ? Blocks.AIR.defaultBlockState()
                : s.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // ProtoChunk blockEntities 独立 map (ChunkAccess 面), 不触 null section
        return getOrCreateChunk(pos.getX() >> 4, pos.getZ() >> 4).getBlockEntity(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int limit) {
        ProtoChunk chunk = getOrCreateChunk(pos.getX() >> 4, pos.getZ() >> 4);
        int y = pos.getY();
        if (y < getMinBuildHeight() || y >= getMaxBuildHeight()) {
            return false; // 界外防御 (piece 受 box 限制理论不达)
        }
        ensureSection(chunk, y);
        return chunk.setBlockState(pos, state, false) != null;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return setBlock(pos, state, flags, 512);
    }

    // ==== chunk 后端 (LevelReader) ====

    @Override
    public ChunkAccess getChunk(int cx, int cz, ChunkStatus status, boolean load) {
        return getOrCreateChunk(cx, cz); // 哑世界永远"有" chunk; status/load 语义不适用
    }

    @Override
    public boolean hasChunk(int cx, int cz) {
        return true; // 哑世界全供给 (读取走 ProtoChunk 懒建)
    }

    // ==== 高度面 (关键: 走 generator.getBaseHeight -> mixin iterateNoiseColumn -> far-lookup coarse) ====

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        // 会话内 = coarse 捷径 (与 /locate 同链); 会话外 = 真噪声柱 (理论不达: 组装全程包裹)
        return ctx.generator().getBaseHeight(x, z, type, ctx.level(), ctx.randomState());
    }

    // ==== biome 面 (BiomeManager.NoiseBiomeSource; TDBS 同源) ====

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(pos)); // LevelSimulatedReader 面: 按真实流体状态判定 (段读, 未写=empty, 不硬编码 false 防语义漂移)
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos)); // LevelSimulatedReader 面: 按真实方块状态判定 (段读; getHeightmapPos 不补: LevelReader default 版同签名胜出)
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int limit) {
        return false; // LevelWriter 面: 哑世界无方块可炸 (签名=编译器错误消息原文)
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        return false; // LevelWriter 面: 哑世界不响应拆除 (piston 语义, 结构生成不触达; 签名=编译器错误消息原文)
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return biomeSource.getNoiseBiome(x, y, z, ctx.randomState().sampler()); // 四参版 [BiomeSource L146 实证]; Climate.Sampler 由 RandomState.sampler() 供 (L126), 类型推断免 import
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return biomeSource.getNoiseBiome(x, y, z, ctx.randomState().sampler()); // 无缓存语义 = 直接采样, 同源 (四参版同上)
    }

    @Override
    public BiomeManager getBiomeManager() {
        return ctx.level().getBiomeManager(); // 真身只读 (噪声扰动采样, 结构放置同源)
    }

    // ==== 世界元数据 (LevelReader / LevelAccessor / WorldGenLevel) ====

    @Override
    public long getSeed() {
        return ctx.level().getSeed();
    }

    @Override
    public RegistryAccess registryAccess() {
        return ctx.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return ctx.level().enabledFeatures();
    }

    @Override
    public int getMinBuildHeight() {
        return ctx.level().getMinBuildHeight();
    }

    @Override
    public int getMaxBuildHeight() {
        return ctx.level().getMaxBuildHeight();
    }

    @Override
    public int getSeaLevel() {
        return ctx.level().getSeaLevel();
    }

    @Override
    public net.minecraft.world.level.dimension.DimensionType dimensionType() {
        return ctx.level().dimensionType();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public int getSkyDarken() {
        return 0; // 无光照语义 (满亮), 结构放置无依赖
    }

    @Override
    public Difficulty getDifficulty() {
        return Difficulty.NORMAL; // 结构放置无难度分支
    }

    @Override
    public LevelData getLevelData() {
        return ctx.level().getLevelData();
    }

    @Override
    public RandomSource getRandom() {
        return random;
    }

    @Override
    public List<? extends Player> players() {
        return List.of(); // EntityGetter 面: 哑世界无玩家 (EntityGetter L32 实证)
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> filter) {
        return List.of(); // EntityGetter 面: 哑世界无实体 (返回 List<T> 非 List<? extends T> — 后者协变方向反, 编译器"无法实现"实证接口返回 List<T>; 且 List<T> 对两种接口形态均协变兼容)
    }

    @Override
    public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) {
        return List.of(); // EntityGetter 面: Entity 版重载 [mojmap EntityGetter.java L24 实证签名]; 哑世界无实体
    }

    @Override
    public long dayTime() {
        return ctx.level().dayTime(); // LevelTimeAccess 面: 真值透传 (结构放置不读, 满足接口)
    }

    @Override
    public net.minecraft.world.level.BlockGetter getChunkForCollisions(int cx, int cz) {
        return this; // CollisionGetter 面: 自身即 BlockGetter (碰撞查询走本类方块后端)
    }

    @Override
    public ServerLevel getLevel() {
        // 只读用途 (server / templateManager); 方块写入已裁决走本类 setBlock, 真身安全
        return ctx.level();
    }

    @Override
    public MinecraftServer getServer() {
        return ctx.level().getServer();
    }

    @Override
    public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
        return ctx.level().getWorldBorder(); // 只读 (界内判定)
    }

    @Override
    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        // 抄 LevelReader default 式 (L56): 经 getBiome (扰动采样) 取色
        return resolver.getColor(getBiome(pos).value(), pos.getX(), pos.getZ());
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0f; // 剪影无光照依赖
    }

    // ==== no-op 桩 (事件/粒子: 结构生成必不触; 触了也无损 — 不给 crash 信号) ====

    @Override
    public void playSound(Player player, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    @Override
    public void addParticle(ParticleOptions data, double x, double y, double z, double xs, double ys, double zs) {
    }

    @Override
    public void levelEvent(Player player, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(GameEvent event, Vec3 position, GameEvent.Context context) {
    }

    // ==== tick 通道: 静默吞并 (原 UOE 绊线转正) [长期记忆: 153] ====
    // 实跑实证 (2026-11-21): 模板放置含液体方块 (RuinedPortal 岩浆 / OceanRuin·Monument 水) 必经
    // LiquidBlock.updateShape -> scheduleTick -> getFluidTicks; 沙砾类下落走 getBlockTicks;
    // LevelAccessor.schedule 默认法构造 ScheduledTick 时还会取 nextSubTickCount.
    // 三处 UOE 每次击杀整个 placeInChunk (首跑 22495 次). 哑 Level 一次性放置, 无 tick 循环,
    // 调度进来也无从执行 -> 静默丢弃是唯一正确语义.
    // 其余 UOE 桩 (难度/chunkSource/lightEngine) 继续当绊线, 触发即打方法名.

    /** 静默 tick 容器: 调度静默丢弃, 查询一律 false/0 — 哑 Level 无未来, 不存在待执行 tick. [长期记忆: 153] */
    private static final class SilentTickAccess<T> implements LevelTickAccess<T> {
        @Override
        public void schedule(ScheduledTick<T> tick) {
            // 丢弃: 哑 Level 没有未来, 任何 tick 都不会被执行
        }

        @Override
        public boolean hasScheduledTick(BlockPos pos, T type) {
            return false;
        }

        @Override
        public boolean willTickThisTick(BlockPos pos, T type) {
            return false;
        }

        @Override
        public int count() {
            return 0;
        }
    }

    /** 无状态单例, block/fluid 各一份 (泛型不同, 无法共用实例). */
    private static final SilentTickAccess<Block> SILENT_BLOCK_TICKS = new SilentTickAccess<>();
    private static final SilentTickAccess<Fluid> SILENT_FLUID_TICKS = new SilentTickAccess<>();
    /** 实体难度快照: Difficulty(NORMAL, dayTime=0, gameTime=0, moonPhase=0); 构造器签名 recompile jar javap 实证. [长期记忆: 154] */
    private static final DifficultyInstance SILENT_DIFFICULTY = new DifficultyInstance(Difficulty.NORMAL, 0L, 0L, 0.0F);

    /** 同 tick 内调度排序去重契约 (ScheduledTick.subTickOrder): 车道单线程放置, 自增即唯一. */
    private long subTickCount;

    @Override
    public long nextSubTickCount() {
        return ++subTickCount;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return SILENT_BLOCK_TICKS;
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return SILENT_FLUID_TICKS;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return SILENT_DIFFICULTY; // [长期记忆: 154] 实体链 finalizeSpawn 参数求值位必经 (decompile 5 现场实证); 哑世界实体即蒸发, 快照数值无副作用面
    }

    @Override
    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
        throw new UnsupportedOperationException("TdSilentLevel.getChunkSource");
    }

    @Override
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
        throw new UnsupportedOperationException("TdSilentLevel.getLightEngine");
    }

    // ==== 信号面 (剪影无红石; 0 直给, 不走 wire default 链) ====

    @Override
    public int getSignal(BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction direction) {
        return 0;
    }

    // ==== 内部 ====

    private ProtoChunk getOrCreateChunk(int cx, int cz) {
        return chunks.computeIfAbsent(new ChunkPos(cx, cz),
                p -> new ProtoChunk(p, UpgradeData.EMPTY, ctx.level(), ctx.biomeRegistry(), null));
    }

    /** 段读: null 段 = 未写过 = 空气 (不补建, 读路径零分配). */
    private LevelChunkSection sectionOf(ProtoChunk chunk, int y) {
        if (y < getMinBuildHeight() || y >= getMaxBuildHeight()) {
            return null;
        }
        return chunk.getSections()[chunk.getSectionIndex(y)];
    }

    /** 段写: null 段按需补建 (ProtoChunk 构造后 sections 全 null, 直写 NPE). */
    private void ensureSection(ProtoChunk chunk, int y) {
        int idx = chunk.getSectionIndex(y);
        LevelChunkSection[] arr = chunk.getSections();
        if (arr[idx] == null) {
            arr[idx] = new LevelChunkSection(ctx.biomeRegistry()); // 1.20.1 只有 (Registry) 单参便捷构造 [L33 实证]; 段底 y 由调用方自算
        }
    }
}
