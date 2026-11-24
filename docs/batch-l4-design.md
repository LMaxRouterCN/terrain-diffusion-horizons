# L4 批设计稿 — 地物装饰 (applyBiomeDecoration feature 步复刻)

> 状态: 呈审 (max 裁决 5 个决策点后动手)
> 侦查完备日: 2026-11-23, 全部结论源码实证, 锚点随文标注
> 修订 v2 (同日): §1.4 重写 — biome 链补证推翻"零改动"结论 (parity 基准=WGR manager; 修法=SilentLevel 本地 manager ~5 行随批, 值不变路径修复); §3.2 "只改三面"笔误闭合; §7 增本地 manager 行

## 0. 一句话

把 vanilla 的 chunk 装饰循环装进 `AssembleCore.compute` 的组装与折叠之间: 结构 placeInChunk 之后、foldColumns 之前, 对 region+外扩 1 环逐 chunk 跑 feature 步, 写入同一 SilentLevel, 一次折叠。LOD 的树/雪/花草从"查表近似"变为"vanilla 同源真流"。

## 1. 侦查结论 (源码实证锚点)

### 1.1 vanilla 装饰循环 (`ChunkGenerator.applyBiomeDecoration` L268-366)


blockpos = 中心 chunk 原点 (minX, minY, minZ)
wr = new WorldgenRandom(new XoroshiroRandomSource(generateUniqueSeed()))   // L278
dec = wr.setDecorationSeed(levelSeed, blockpos.getX(), blockpos.getZ())    // L279
biomeSet = 3x3 邻域 chunk section palette 收集后 retainAll(possibleBiomes)  // L280-289
步表 = featuresPerStep (全局, 与 chunk 无关)
for k in 0…max(11, 步表.size()):
结构步: setFeatureSeed(dec, l, k) + placeInChunk(写域=中心 chunk XZ, y: min+1~max-1)  // 我们跳过 (L3 已承担)
feature 步:
intset = ∪ biomeSet 各 biome 的 features()[k] 经步表 indexMapping 映射出的全局索引
排序后逐 l1: setFeatureSeed(dec, l1, k) + placedFeature.placeWithBiomeCheck(level, this, wr, blockpos)

关键独立性: `int l` 是结构步循环局部变量, feature 步用全局步表索引 l1 (L297/L340) — **结构步缺席不扰动 feature 种子流** (种子按 (dec, l1, k) 独立播种, feature 缺席同样不污染后续流)。

### 1.2 写域 (`ChunkStatus.FEATURES` L96): `writeRadiusCutoff = 1`

`WorldGenRegion.ensureCanWrite` = 中心 chunk ±1 (3x3 写域)。vanilla 每 chunk 各跑一次装饰、互相溢出写入, 最终世界 = 所有 chunk 装饰写入的并集。

**region 同构论证**: region 界内列 x 的 vanilla 写入者 = 距 x ≤1 chunk 的全部装饰; region∪外扩1环 恰好覆盖 region 内所有列的写入者全集 → region+环 逐 chunk 装饰 + 折叠只收 region 界 = region 内列与 vanilla 逐块同构。dl0: region 4x4 chunk + 环 = 36 chunk 装饰。

`WorldGenLevel.ensureCanWrite` default true, SilentLevel 不 override → 3x3 外的偶发溢出写入自然进 chunk 池, 折叠 clip 兜底, 无害。

### 1.3 随机流

- `setDecorationSeed(levelSeed, cx*16, cz*16)` 式同 vanilla; `setFeatureSeed(dec, l1, k)`。
- **构造源必须 Xoroshiro**: WorldgenRandom 委托构造源, setDecorationSeed 本身消费 nextLong 算 dec → Legacy 与 Xoroshiro 同式调用产生不同 dec + 不同流。vanilla 装饰期构造 `XoroshiroRandomSource` (L278)。
- `XoroshiroRandomSource.setSeed` = upgradeSeedTo128bit 全量换状态 (全文实证) → 构造种子无关, 用 0L 确定姿态。

### 1.4 biome 链 (v3 — 呈审后补证修订, 推翻上一版"零改动"结论)

vanilla 装饰期 BiomeFilter 的 parity 基准 = **WGR 自己的 BiomeManager** (WorldGenRegion 构造 L101: `new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed))`), 不是 ServerLevel 的。查询链: getBiome → 8 角 fiddle (pos-2 偏移, 角全落整数 quart 坐标) → source.getNoiseBiome = LevelReader default (L63) → **先** WGR.getChunk 查装饰 cache ProtoChunk 的 palette, 缺失才退 WGR.getUncachedNoiseBiome (L176 → level 直采 TDBS 兜底)。

值等价是**精确**的: palette 灌值 = quart 网格 TDBS 采样 (fillBiomesFromNoise 实证); BiomeManager 8 角全落整数 quart 坐标 → 同 cell 内 palette ≡ 直采, 两条路径逐位同值。

上一版错误: "透传真身 manager = 直采不碰 chunk 存储" — 漏了 LevelReader default 的 chunk hop。真身 manager 的 source = ServerLevel → ServerLevel.getChunk → ServerChunkCache, off-thread 分支 = **supplyAsync(mainThreadProcessor).join() 同步阻塞** (ServerChunkCache L117 实证)。L3 现状 (TdSilentLevel.getBiomeManager 透传真身, getColor L289 必经 getBiome) 每次 biome 查询 = 车道→主线程 round trip: 值对 (真 chunk palette 同源), 路径错 (同步耦合 + 主线程压力)。

**修法 (随 L4 批, ~5 行)**: TdSilentLevel 持有本地 `BiomeManager(this, BiomeManager.obfuscateSeed(worldSeed))` — this 的 getNoiseBiome 已 override 为 TDBS 直采 (L3 实跑验证车道线程安全), zoomSeed 同式 obfuscateSeed(levelSeed) (与 MinecraftServer L337 / WGR L101 同源) → fiddle 流逐位同 vanilla → 精确同源 + 全链车道线程私有, 零接触真服务器。

biome 集合复刻: `fillBiomesFromNoise` = quart 网格直采 biomeSource 灌 palette (ChunkAccess 实证) → 对 3x3 邻域做 quart 全列采样 (12x12x96 ≈ 14k 次 TDBS tile 缓存查表/chunk) 的 distinct 集 ≡ palette 值集。retainAll(possibleBiomes) 在此自动满足 (采样值域 ⊆ TDBS)。

### 1.5 步表重建

`featuresPerStep` 是 private memoized, 但同源重建: `FeatureSorter.buildFeaturesPerStep(List.copyOf(tdbS.possibleBiomes()), h -> generator.getBiomeGenerationSettings(h).features(), true)` — 与 vanilla 构造器 L93-96 逐参相同, 确定性等价。StepFeatureData 的 indexMapping 是 identity map (PlacedFeature 同 registry 实例), 我们从 `generationSettings.features()` Holder.value() 取, 天然同实例。

## 2. L3 既有 parity bug (勘误呈报)

**placeInChunk 随机源错位**: L3 `assemble` 里 placeInChunk 的 wr 用 `new LegacyRandomSource(0L)` 构造 (TdStructureAssembleCore.java), 而 vanilla 结构步 placeInChunk 复用装饰期顶部的 **Xoroshiro** 实例 → 我们的 dec 种子值与 postProcess 内部消费流均与 vanilla 不同 (§1.3 机制)。影响面: 结构 postProcess 随机细节 (jigsaw 细节/装饰变体), 不影响结构几何 (generate 阶段 Legacy 正确, vanilla Structure.java 自建 Legacy — 勿误伤 elimination/tryGenerate)。

**修法**: placeInChunk 的构造行换 `new XoroshiroRandomSource(0L)`, 一行。

## 3. 新增件

### 3.1 `TdBiomeDecorator` (纯计算, feature 包新文件)

输入: ctx (Snapshot), SilentLevel, region 界。过程:


步表 = 懒建缓存 (ctx 级: seed+registry 不变则表不变)
for cz, cx in region∪1 环 (36 chunk, dl0):
blockpos = (cx*16, minY, cz*16)
wr = new WorldgenRandom(new XoroshiroRandomSource(0L))
dec = wr.setDecorationSeed(worldSeed, cx*16, cz*16)
biomeSet = 3x3 邻域 quart 采样 distinct  (~14k 查表)
for k in 配置步集:
intset 收集 + 排序 (§1.1 同式)
for l1: wr.setFeatureSeed(dec, l1, k)
事务开: view = TdDecorateLevelView(level, 中心 chunk, 过滤器)
try: placedFeature.placeWithBiomeCheck(view, generator, wr, blockpos)
catch: 回滚 undo log + 黑名单登记 (registry key), LOG.warn 继续

黑名单跳过不改流 (§1.1 种子独立性)。计算与调度解耦: 装饰器不含缓存/线程策略, 全部纯函数式; 车道/门控/缓存在现有 lane 骨架。

### 3.2 `TdDecorateLevelView` (SilentLevel 的装饰态包装)

delegate SilentLevel, 过滤器面 (biome 查询已由 SilentLevel 本地 manager 承接, 见 §1.4 修法; view 不需改 biome 面):
- `setBlock`: 沉没过滤器 — `y < getBaseHeight(x,z)-4` (近似 stone 区) 的写入吞掉 (返回 true 假装成功), 其余 delegate 并记 undo log (pos, oldState)。
- 其余全部 delegate。

原子性: 存储归 SilentLevel (计算), 过滤/事务归 view (策略), 改过滤策略零触存储。

### 3.3 `TdSilentLevel` 增面: getBlockState 近似背景

未写位置不再恒空气, 改近似地形块 (BushBlock.canSurvive 查 below 的 DIRT tag, 恒空气会击杀全部花草树苗):


h = getBaseHeight(x, z, WORLD_SURFACE)
未写时: y < h-4 → stone; h-4 <= y < h → dirt; y >= h → air
水列 (h < seaLevel): [h, seaLevel) → water
written 块优先; written.isAir() 且近似实心 → 给近似块 (树干穿地点的语义)

折叠读 ProtoChunk 实存, 近似背景不落盘 — 地形段仍由消费端平铺发, 无重叠。

[勘误 11-23 实现偏差呈报] 近似背景最终落在 TdDecorateLevelView 读面 (写入优先, 空读回退), 非 SilentLevel 增面 — SilentLevel 保持纯段读 (null section = air), 策略归 View / 存储归 SilentLevel, 比本节初稿更正交; 两态草/空气而非 dirt/stone 分层 (分层无增益: would_survive 类判定只查 below ∈ DIRT tag); envH 用 MOTION_BLOCKING, 与 SnowAndFreeze 的 getHeight 同 type 共缓存 → 雪锚定与背景判定严格同值。

## 4. 折损清单 (显式声明, 全部单向可控)

| # | 项 | 差异 | 视觉净效应 |
|---|---|---|---|
| 1 | 高度面 | getBaseHeight 噪声柱 vs vanilla 实时 heightmap (含 carving/已写 feature) | 河谷/挖空处树悬空或入水 |
| 2 | 交错序 | 结构全先放 vs vanilla k 步交错 | feature 见到提前占位的结构块而跳过 (vanilla 是先放后被结构覆盖), 重叠区同为结构块, 近零差 |
| 3 | 默认步集 | 仅 VEGETAL+TOP_LAYER (决策①) | 湖/泉/地下矿缺席 — LOD 无消费者 |
| 4 | 冰面 | 流体列未写=空气, SnowAndFreeze 判无水 → 冰盖不生成 | 雪原湖面无冰 |
| 5 | 3x3 溢出截断 | 无 (region+环数学封闭, §1.2) | — |
| 6 | biome 集合 | 无 (quart 采样 ≡ palette, §1.4) | — |
| 7 | 雪锚 | getHeight 恒为装饰前噪声柱 (写入不更新 heightmap); 树列 shouldSnow 遇 written 树干非 air → 整列无雪 | 树冠无雪盖 (vanilla 是冠顶雪); LOD 列顶 = 叶色遮蔽, 近零差 |
| 8 | 表面特异性 | 近似背景恒草方块: 仙人掌/枯灌木 (below 要 sand), 红树 (要 mud) canSurvive false → 灭活 | 沙漠缺仙人掌/枯灌木, 红树林缺红树 (升级路径: TEST 后按 biome 查 TdSurfaceBlockMap) |
| 9 | 水生装饰 | 背景无水层 (决策② 有意): 睡莲/海草/海带 water 检查 false → 灭活; 与 #4 冰同向 | 水面无水生植物 |

## 5. 决策点 (呈 max)

1. **默认装饰步集**: A. 仅 `VEGETAL_DECORATION` + `TOP_LAYER_MODIFICATION` (k=9,10, 观感层最小集) / B. 全 11 步。推荐 A: 地下步 (ore/lake/dungeon) 在 LOD 无消费者, 纯算力。可配 `featureSteps` 列表。
2. **花草帽段共存**: L4 段列禁帽段 (推荐, 高 detail 帽段继续用) / L4 上线全局关帽段。
3. **L3 随机源勘误**: 随本批一并修 (推荐, §2 一行) / 单独批。
4. **TdVegetationMap**: L4 TEST 验证后退役删除 (现 vegEnabled 恒关, 不急)。
5. **detail 门控**: L4 只跑 dl0 (推荐: dl0 一列=1 块天然 1:1; dl1+ 列亚 DP 失真), 配置 `featureMaxDetail = 0`。

## 6. 风险与兜底

- **死循环 feature** (事务 catch 捕异常捕不了死循环, e.g. bamboo/jungle 高密度扫列): 兜底 = 默认步集 A 避开大头 + TEST 观察期; 实测出问题加 feature 级 wall-clock 预算或白名单制。
- **性能**: 36 chunk × ~14k biome 查表 (tile 缓存) + ~2k feature place (含 far-lookup coarse 高度采样) — 全程在异步车道, DH 数据源生成期一次。非每帧。TEST 实测定量。
- **内存**: region 级 ProtoChunk 池随 SilentLevel 生命周期即弃, 车道缓存只存 LanePayload 折叠产物。已实跑同量级 (L3/C 批)。
- **崩溃防御**: feature 级事务回滚 + 黑名单, 单 feature 失败不杀整 region。

## 7. 工作量评估

| 件 | 行数 (估) | 难度 |
|---|---|---|
| TdBiomeDecorator | ~250 | 中 (循环复刻机械, biome 集采样直白) |
| TdDecorateLevelView | ~120 | 低 (delegate 模式) |
| TdSilentLevel 增面 | ~60 | 低 |
| AssembleCore 插入 | ~40 | 低 |
| L3 随机源修正 | 1 | 低 |
| TdSilentLevel 本地 BiomeManager (§1.4 修法, 值不变路径修复) | ~5 | 低 |
| TdDhWorldGenerator L4 载荷列守卫 (决策②: L300 花草掷点加 colChain == null, L4 段列禁 L1 帽段) | 1 | 低 |
| Config 增项 | ~15 | 低 |

合计一晚。TEST 路径同 L3 (TEST 世界开关 + DH 渲染对比)。

> [勘误 2026-11-23] 原估"出露段消费 ~100 行"为误判: 施工 6 落盘后读回消费端全文,
> L539-559 平铺端 (跳埋地段 + air 缝隙垫 + coverTop 游标 + 尾 air 段) 本就是出露段
> 发射路径, L4 段链天然复用, 实际新增代码 = 决策②守卫 1 处条件; 手推树列全链闭环
> (dirt 三明治 / 干段 / 叶段裁剪 / 尾 air)。附带定性: 雪穿树干自愈 (shouldSnow 要
> 落点空气, View 写入优先 → 树干列读 log 不雪, 同 vanilla); 树根 dirt 段进 beard 盒
> 表面换色 = vanilla trunk placer 行为 (树底草换 dirt)。