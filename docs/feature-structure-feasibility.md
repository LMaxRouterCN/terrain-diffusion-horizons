# TDH 地物/结构同源可行性审查报告

日期: 2026-11-18 · 性质: 架构审查
证据基础: vanilla 1.20.1 mojmap (NFRT recompile jar) / DH 3.3.3 (libs) / TD fork 230.1.2.0, 全部 javap 字节码实证, 记忆级判断单独标注
范围: 让 TDH 的 LOD 生成包含地物与结构, 与真实 chunk 保持同源 (同种子 → 同位置)

---

## 0. 结论速览

| 路线 | 定级 | 性能量级 (每 64×64 块 data source, [推算]) | 效果上限 |
|---|---|---|---|
| L1 统计近似 (植被色/微抬) | **舒服干** | 趋近零 | 远景植被观感, 非同源 |
| L2 结构撒点同源 | **舒服干** | <1ms | 结构坐标与真实世界 100% 一致 |
| L3 结构组装 (轮廓折叠) | **麻烦点** | ms 级/结构, 稀疏触发 | 村庄真实 footprint + 高度轮廓 |
| L4 地物逐株 (DL0) | **硬干** | ~ms 级 (含 place) | 树: 同种子同位置; 草: DL0 逐株可见 |
| L4+ 逐株完全一致 (方块级) | **干不了** | — | TD 数据源上物理不可能 |

五档分布: 两档舒服干, 一档麻烦点, 一档硬干, 一档干不了。没有空档。

---

## 1. 证据链

### 1.1 撒点链 (结构在哪) [实证]

- RandomSpreadStructurePlacement.getPotentialStructureChunk(long seed, int x, int z) — **public 纯函数**。
  字节码: new WorldgenRandom(LegacyRandomSource(0)) → setLargeFeatureWithSalt(worldSeed, floorDiv(x,spacing), floorDiv(z,spacing), salt) → RandomSpreadType.evaluate ×2 → ChunkPos。零 chunk 上下文, 零隐藏状态。
- StructurePlacement.isStructureChunk(state, x, z) — public。对 state 的全部依赖: getLevelSeed() 一个字段 + exclusionZone (递归查另一 structureSet 的撒点, registry 在手即可算)。
- ChunkGeneratorStructureState.createForNormal(RandomState, long, BiomeSource, HolderLookup<StructureSet>) — **public static 工厂, 无需真实世界**。

撒点同源的种子链完整闭合, 全部走 public 面。

### 1.2 装饰 RNG 链 (草/树在哪) [实证]

ChunkGenerator.applyBiomeDecoration 字节码显示:

- setDecorationSeed(worldSeed, chunkX, chunkZ) — 每 chunk 独立派生, 纯函数;
- 每个 PlacedFeature 之前 setFeatureSeed(decorationSeed, index, stepOrdinal) — **每次从同一个 decorationSeed 独立重播种**。

推论: **chunk 之间零耦合, feature 之间零耦合**。流式墙只存在于单个 PlacedFeature.place() 内部 (一个 feature 在一个 chunk 内的连续随机消费)。

这是本次审查最大的修正: 前轮推演判「地物逐株 = 干不了」所依据的跨 chunk 流式墙不存在。地物逐株从死档上调。

### 1.3 介质 (DH data point 能装什么) [实证]

- DhApiTerrainDataPoint: detailLevel / blockLight / skyLight / bottomY / topY / blockStateWrapper / biomeWrapper — 每段 = 一个方块状态 + Y 范围。
- FullDataSourceV2.dataPoints 为 LongArrayList[] — **每列一个 list, 段数无 API 硬上限**; columnGenerationSteps 每列一个 byte 的 generation step, EDhApiWorldGenerationStep.FEATURES / STRUCTURE_START 原生在枚举。

「空气-叶-空气」三段三明治、悬空树冠段, API 语义可表达。渲染侧对非贴地段的行为未实测 [待验, §5.4]。

### 1.4 TD 侧供给 [实证]

LTP 全静态, init(long) 收世界种子, 供高度/水/气候/河流, 零方块级信息。方块化在 TDH ColumnStyle (65 群系剪枝表, 见 [长期记忆: 106] 的运行时化设计债)。

### 1.5 DH 官方路线对照 [实证]

WorldGenStructFeatManager extends vanilla StructureManager, 靠 RegionFileStorageExternalCache 按 ChunkStatus 拉真 region chunk。DH 对此题的官方答案 = INTERNAL_SERVER 模式真跑 vanilla 管线 (mimic 对象群: DhLitWorldGenRegion / DummyLightEngine / ...)。数学同源路线 DH 没走过 — TDH 走无人区, 但 vanilla public API 兜底充分, 不需要碰 mixin/AT。

---

## 2. 分档详述

### L1 统计近似 — 舒服干

群系查表密度 → 表面段混植被色 / 高度微抬 1~2 段。挂在现有翻译循环里, 几行的事。确定性哈希保证不跳变。README 现有「无地物」条款的观感级替代, 但属临时近似 [长期记忆: 106 的 max 指示: 近似方案一律标临时待替代]。

### L2 结构撒点同源 — 舒服干

工程: TdStructureOracle 单类。

- 一次性 (世界加载时): 从 RegistryAccess 拿 StructureSet registry → createForNormal(...) 构造 state。RandomState / BiomeSource 的离线构造可见性未逐项实证 [待验, §5.2], 均为 codec/registry 层, 风险低。
- 每 data source: 遍历覆盖的 4×4 chunk (DL0 = 64×64 块) × 各 structureSet 的 isStructureChunk → 起始格集合 → 全局缓存。

边界:
- concentricRings 类 (要塞/末地折跃门) 的 ring positions 是 lazy 暴力搜索, ensureStructuresGenerated 一次性算 128 ring, 秒级开销 — **建议第一期跳过 rings 类** (远距 LOD 里要塞的存在感价值低)。
- 效果表达 = 占位几何 / 高度凸起 / 专属轮廓, 与 L3 解耦。

卖点级特性: LOD 中结构与真实世界同坐标 — 玩家走近时, 远处看到的村庄真的在那里。这是 DH 原生 LOD 没有的。

### L3 结构组装 — 麻烦点

Structure.generate(RegistryAccess, ChunkGenerator, BiomeSource, RandomState, StructureTemplateManager, seed, ChunkPos, referenceCount, LevelHeightAccessor, biomePredicate) 全参数可离线构造, 除:

- StructureTemplateManager: jigsaw 结构的 nbt 模板加载。单人 (integrated server) 资源完整可用。纯客户端构造需要资源管理器 + vanilla 结构模板的客户端可用性 [待验, §5.3]。
- 组装产物 StructureStart 按 (structure, 起始 ChunkPos) 全局缓存, 跨 data source 复用。
- 放置: piece bounding box × 64×64 列折叠, 每列取相交 piece 的 Y 范围 + 主导材质 → data point 段。只处理相交列。

麻烦在 template manager 与 jigsaw 递归的边界打磨, 不在架构。

### L4 地物逐株 (DL0) — 硬干

种子链全 public 可同源:

- FeatureSorter.buildFeaturesPerStep(List, Function, boolean) public static — 需复刻 biome 列表 (**顺序敏感**, 对齐 possibleBiomes(), 可见性待验)。
- 每 chunk: setDecorationSeed → 每 feature setFeatureSeed → placedFeature.place(fakeLevel, chunkGenerator, rng, origin)。

硬骨头 = FakeWorldGenLevel:

- 高频路径仅三个: getHeight (→TD elevation), getBlockState (→列三段近似), getBiome (→TD biomeIndexes); 其余方法面抛 UnsupportedOperationException 即可。
- feature.place 内部的 setBlock 收集进列缓冲 → 折叠成 data point 段。

折损来源 (为什么是硬干不是能干):

- block predicate 语义近似: 真实 chunk 的洞穴/悬空/carver 细节 TD 列没有 → 部分植株位置偏差;
- 高度锚定: TD elevation 与 vanilla heightmap 的语义差 (案①已处理过 +1 问题, [长期记忆: 101]);
- 结果定性: **分布同源 + 个别坐标错**, 不是逐株完美。

建议树先行 (价值最高), 草殿后 (DL0 价值有限, 见 §4)。

### L4+ 逐株完全一致 — 干不了

predicate 查询的目标是真实 vanilla 方块网格 (含洞穴 / carver / 邻 chunk feature 交互)。TD 数据源上不存在此信息; 生成它 = 跑完整 vanilla 管线 = DH INTERNAL_SERVER 的路 — TDH 用不了 (地形是 TD 的)。**分布同源 + 位置近似是物理上限**, 不是工程上限。

---

## 3. 性能账

全部挂 DH 生成线程 (TDH 现有 generateLod 位置, 异步于渲染) — **对 FPS 零影响**, 代价换算成生成延迟。下表数字为**推算值** (依据: 字节码指令构成的量级估算), 非实测, 落地时以实测为准:

| 项 | 每 data source (DL0 = 64×64 块 = 16 chunk) [推算] | 说明 |
|---|---|---|
| L2 撒点判定 | <1ms | 16 chunk × ~15 set × 一次哈希 |
| L3 组装 | ms 级/结构 | 稀疏 (几十 chunk 一结构), 全局缓存跨 data source 复用 |
| L3 放置折叠 | ∝ 相交列数 | 只处理踩中的列 |
| L4 掷骰 | 亚毫秒级 | 16 chunk × 每 feature 几次 nextInt |
| L4 树 place | 每株几十次进列缓冲 | 密度乘出后总量有限 |
| 现有翻译循环 | 基线 | TD 查询为主, 瓶颈所在 |

量级判断: L1+L2 合计对基线是零头; L3 摊薄后趋零; L4 与现有翻译循环同量级或更低 — 总瓶颈仍在 TD 推理。

---

## 4. 效果上限 (性能不降太多的前提 = 挂生成线程)

- **L1**: 远景群系植被色, 一眼成立。
- **L2**: 结构坐标 100% 同源 (多人 TD 场景判定不受影响, 见 §5.1) — 卖点级。
- **L2+L1**: 结构占位 + 周围植被, 远景观感完整。
- **L3**: 村庄/神殿 silhouette 正确 (塔尖高度, 占地形状)。
- **L4 DL0**: 树同种子同位置, 树冠 = 顶部绿段; 草逐株 (1 块高, DL0 一块一列可表达)。
- **DL1+**: 一切折叠为表面色 / 高度毛刺 — 分辨率物理上限, 与「跳过地物」的差别只在毛刺的同源性。
- 组合上限: L2+L3+L4(DL0) = LOD 与真实世界结构坐标一致、树上位置一致、细节随 detail level 衰减; LOD → 真实 chunk 替换边界的视觉跳变最小化。

---

## 5. Caveat 与未验证项

1. **[记忆级/待验] 多人 worldgen registry**: 1.20.1 vanilla 不向客户端同步 datapack 注册表 (StructureSet / PlacedFeature 覆盖)。TD 服务器场景 worldgen json 未动 = vanilla 默认 = 客户端自带副本一致 → **TDH 目标场景不受影响**。服务器改过 worldgen datapack 的场景会失配, 需 fallback 检测。
2. [已验证 2026-11-18] RandomState.create 两个 public static 重载 (输入 = HolderGetter.Provider + 设置 key, 或 NoiseGeneratorSettings + 噪声 HolderGetter); BiomeSource 抽象面仅 3 方法 (codec / collectPossibleBiomes / getNoiseBiome)。单人场景直接取活实例 (integrated server 在场), 连构造都省。
3. [已验证构造面 2026-11-18] StructureTemplateManager 构造 = (ResourceManager, LevelStorageAccess, DataFixer, HolderGetter<Block>), 四输入全 public 且单人端可得; vanilla 结构模板随客户端资源分发。多人纯客户端场景仍挂在 §5.1 前提下。
4. [运行时项, 有零成本旁证] DH 渲染器对非贴地段 (悬空树冠): 原版 DH 中真实区块卸载后 LOD 保留树冠段 (同数据形状), 正常游玩即可观察渲染成立性; 精确行为落地时实测。
5. concentricRings 类 (要塞): 一次性秒级开销, 建议延后。
6. L4 的 biomeFilter: vanilla 查 worldgen 3D biome, TD 侧 biomeIndexes 是近似 — 与现有 ColumnStyle 同一近似层, 不引入新债。

---

## 6. 建议落地顺序

1. **L1** (半天): 植被观感, 独立无依赖, 顺手销 [长期记忆: 106] 的「临时近似」欠账。
2. **L2** (1~2 天): 结构同源坐标 + 占位表达, TDH 卖点级特性。
3. **L3** (数天): 动手前先钉死 §5.3。
4. **L4** (周级): 动手前先钉死 §5.4, 树先行草殿后。

每档独立成层可回退, 不互相阻塞。

执行架构 (已裁决 2026-11-18): 两车道 — Pass A 地形 (TERRAIN 步, 出了就渲染, 速度上限不变) + Pass B 结构/树 (FEATURES 步, 独立低优先级线程池吃 A 的完成队列, 走 updateFromDataSource 增量更新 + 向父级传播), 树速与地形速解耦。DH 侧机械已确认存在: WorldGenerationQueue / FullDataUpdatePropagatorV2 / DelayedDataSourceSaveCache (异步批量落盘)。车道基建随 L4 引入, 增量式无返工。

同步/异步开关 (max 裁决 2026-11-18): 结构与地物各一个独立配置项, 默认开 (异步)。关 = 同步模式: 地形 LOD 在对应车道跑完前不发布, 一次性完整出图, 复刻 vanilla worldgen 的完整性语义, 供愿意以速度换视觉完整性的玩家。两开关独立成栅栏: 只关地物 → 等地物; 只关结构 → 等结构; 全关 → 等全部。同步模式下 feature 跑炸走异常隔离 (catch + 黑名单), 不死锁, 代价仅该 feature 缺席 (与异步同一兜底)。

沉没过滤器 ([临时方案] max 裁决 2026-11-18): 一切生成物位于该列 TD 实心表面高程以下的方块一律跳过。判据 = 列表面高程 (非海平面, 海洋神殿不误杀), 与 FakeLevel.getHeight 同列缓存同校准, 全项目单一表面定义。L4 落点 = FakeLevel.setBlock 单一咽喉 (y < 表面 → 丢弃且返回 true 假成功, feature 内部不感知); L3 落点 = 折叠逐列裁剪 (整 piece 全沉没连组装跳过); step 类别粗滤保留为省 CPU 第一道。[临时] 理由: 现状 TD 无地下数据, 玩家在地下看 LOD 侧面本就是纯色/空, 零可见损失; 地下数据路线 (TD 地下支持或 LOD 地下填充) 提上日程时重审 — 洞穴内可见结构部分 (要塞墙横穿洞穴等) 会被此闸吞掉。随过滤器提供配置开关。

配置基建 (第 0 步, 2026-11-18): TOML 格式带注释, 落 config/terrain-diffusion-mc/ (与主模组同文件夹)。首批项: L1 植被上色开关 / 结构异步开关 (默认 true) / 地物异步开关 (默认 true) / 沉没过滤器开关。