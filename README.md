# Terrain Diffusion Horizons (TDH)

[English](https://github.com/LMaxRouterCN/terrain-diffusion-horizons#terrain-diffusion-horizons-tdh-1)

https://github.com/xandergos/terrain-diffusion-mc/issues/260

给 Terrain Diffusion 用的 Distant Horizons 的 LOD 加速生成模组

用 Terrain Diffusion 的 AI 地形模型为 Distant Horizons 提供 LOD 数据源。
远视距的地形不再依赖真实 chunk 生成，而是直接从地形模型查询翻译。

目前不支持 Voxy：现有的 1.20.1 版本不够稳定（个人体验），ARR 许可证也是个麻烦；给 Voxy 做加速，相当于我要实现一个 1.20.1 版的、自生成区块的 Voxy——即使不全部自己写，也需要吃透相关 fork——工作量不现实。1.20.1 之后的版本应该也不会转向 Voxy。

## 性能 / Performance

实测（Ryzen 5 5600X + NVIDIA CMP 30HX，原版渲染距离 5 + DH 渲染距离 512）：

TD 世界缩放 2 + DH 质量 4 / 负载 4：

- 约 30 分钟完成 LOD 生成
- 约 500 chunks/s，约 87% 的时间保持该速度

TD 世界缩放 6：稳态约 700 chunks/s，峰值约 1500 chunks/s。

世界刚创建时 DH 需要预热：大量区块入队并预处理，此期间不会生成 LOD；随后有一小段低速生成期，之后进入正常速度。

## 已知限制 / Known Limitations

- **远景结构与植被是近似还原，不是逐块复刻**：位置和内容由同种子下的原版生成规则决定，但每列只保留少数几层方块（默认上限 6 层，`column_segments`）。已知缺失：沙漠没有仙人掌和枯灌木、没有红树林、水面没有植物、树顶不积雪等。
- **河岸的沙/砾分布与原版有偏差**：这些是原版在群系交界处的细碎点缀，尺度太小，远景采样拿不到
- **远景呈台阶状**：越远的一格代表的地越宽，远处几何呈台阶状，是 LOD 的固有特性，无法避免
- **河谷色带**：远处沿河可见一条色调略异的窄带。河流在数据里是独立群系，草色与两岸略有差异；近距离原版会在群系边界做颜色渐变，远景每格只带一个群系的颜色、没有渐变，差异就以带状露出来

## 特性 / Features

- AI 地形模型直接出 LOD，跳过真实 chunk 生成
- 群系驱动表面方块：草/沙/雪等地表与原版一致
- 水体：河流有自己的水面高度，海洋填到海平面；冻河/冻洋有冰盖
- 河床材质按水流强度分布（沙/砾/黏土），同一位置材质固定
- 远景结构：与原版同位置生成（村庄、神殿、矿井等），细节简化
- 远景植被：树、花草、雪盖，与原版同位置
- 正确的群系颜色：远景的草色/水色与近处一致
- 纯桥接层：无 mixin、无 Access Transformer、无反射

## 依赖与版本 / Dependencies

| 依赖 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.30 |
| Distant Horizons | 3.3.3 |
| Terrain Diffusion | 1.20.1 |

TDH 针对 DH 3.3.3 开发并实测通过。其余版本未测试。

## 安装 / Installation

1. Forge 47.2.30 (1.20.1)
2. `mods/` 放入三个 jar：Distant Horizons、Terrain Diffusion、TDH
3. 按 Terrain Diffusion 的方式创建世界；进世界后 TDH 自动接管远景生成（日志可见 `TDH 生成器注册完成`），非 TD 世界自动退回 DH 原生生成
4. 确认 DH 的 Distant Generation 已开启

## 配置 / Configuration

配置文件：`config/terrain-diffusion-mc/terrain-diffusion-horizons.toml`（首次运行自动生成，带注释说明）

| 键 | 默认 | 说明 |
|---|---|---|
| `general.enabled` | `true` | 总开关，false = 关闭 TDH，DH 自己生成远景 |
| `terrain.surfaceBlock` | `minecraft:stone` | 兜底表面方块（群系表面关闭或解析失败时使用） |
| `terrain.fillerBiome` | `minecraft:plains` | 兜底群系（群系表面关闭时使用，影响远景草色） |
| `terrain.useBiomeSurface` | `true` | 表面方块按群系（与原版一致）；false = 关闭（省内存，全用兜底方块） |
| `terrain.surfaceBlockOverrides` | `{}` | 按群系指定地表方块（没指定的群系用默认） |
| `vegetation.coloring` | `true` | 花草色斑：远景草地上按该群系花的密度点缀花色（只换颜色，高度不变） |
| `vegetation.grassColoring` | `false` | 草丛色斑（默认关：远景里草丛与草方块本就同色，一般无需开） |
| `structure.enabled` | `true` | 远景结构总开关 |
| `structure.async` | `true` | 异步：先出地形，结构算完自动补上；false = 等结构算完一起显示（无刷新闪烁） |
| `structure.pad_chunks` | `16` | 扫描外扩（区块数）：防止横跨边界的大型结构被截断 |
| `structure.column_segments` | `6` | 结构每列的方块层数上限；1 = 旧行为（单层）；层数多还原度高、渲染开销大 |
| `structure.surface_blend_depth` | `3` | 埋地结构表面染色深度（路/农田等）；负数 = 关闭 |
| `features.enabled` | `true` | 远景植被总开关（上面的花草色斑是独立开关，不受此项影响） |
| `features.async` | `true` | 同 structure.async |
| `features.decoration_steps` | `["vegetal_decoration", "top_layer_modification"]` | 参与远景植被的生成步（合法值见配置文件注释，大小写不敏感）；空数组 = 关闭植被 |
| `features.decoration_max_detail_level` | `0` | 档位上限：更远的档位不跑植被（0 = 默认，只最近档位跑） |
| `features.decoration_prefetch` | `true` | 高度修正：修远景植被的方格状错位和错位的树；false = 关闭修正 |
| `features.decoration_water_background` | `true` | 水面判定：修湖上浮树浮花；依赖上一项（高度修正） |

```toml
[general]
enabled = true

[terrain]
surfaceBlock = "minecraft:stone"
fillerBiome = "minecraft:plains"
useBiomeSurface = true

# 群系 key -> 地表方块稀疏覆盖 (可选):
# [terrain.surfaceBlockOverrides]
# "minecraft:plains" = "minecraft:snow_block"

[vegetation]
coloring = true
grassColoring = false

[structure]
enabled = true
async = true
pad_chunks = 16
column_segments = 6
surface_blend_depth = 3

[features]
enabled = true
async = true
decoration_steps = ["vegetal_decoration", "top_layer_modification"]
decoration_max_detail_level = 0
decoration_prefetch = true
decoration_water_background = true
```

内存注意：`useBiomeSurface=true` 约 520MB，false 约 150MB（实测）。
升级新版本后新增键不显示在文件里时，删除 toml 让它重新生成即可。
配置改动需重进世界生效。

## 实现原理 / How It Works

### 架构：纯桥接层

无 mixin、无 Access Transformer、无反射，全部走两端公开 API：DH 的 `DhApi.worldGenOverrides` 与 TD 的 `LocalTerrainProvider` 静态门面。23 个类、三个包，职责正交：

| 包 | 职责 |
|---|---|
| 根包 | mod 入口 + 播种式 TOML 配置（`TerrainDiffusionHorizons` / `TdConfig`） |
| `dh` | DH 注册与生命周期（`DhCompat`）、LOD 翻译核心（`TdDhWorldGenerator`）、坐标翻译（`TdLodTranslator`）、群系表面/花草表（`TdSurfaceBlockMap` / `TdFlowerMap`）、车道刷新钩子（`DhRefreshHook`） |
| `dh.lane` | 异步车道骨架：纯函数核心 + 调度外壳 + 载荷与缓存键（B1） |
| `dh.structure` | 结构撒点（vanilla 同 seed 数学）、哑世界组装、群系装饰（B2/L3/L4） |

注册链：mod 构造期挂 `DhApiLevelLoadEvent` → 世界加载回调 → 三重守卫（主世界 / TD 世界激活 / seed 可得）→ `registerWorldGeneratorOverride`。世界卸载无反注册 API，由生成器自身 `close()` 清引用。只在主世界工作（硬边界）。

### 数据流

DH 生成队列 → `generateLod(posX, posZ, dl0)` → `runAsync` 壳入 DH 池线程 → TD `getRiverTerrainData(块矩形, withBiomes)` 一次拉整块 64×64 四数组（`elevation` 海拔米 / `waterMask` 水强度 0-255 / `waterSurface` 液面米 / `biomeIndexes` 群系索引）+ 结构/装饰车道列段（地形先出图，车道算完刷新补全）→ 双循环每列翻译成若干 `DhApiTerrainDataPoint`（床/表面段、结构折叠段、水段、花草帽段、空气段，列内无缝平铺）→ `setApiDataPointColumn` → `resultConsumer` 恰一次回执。

只供 API dl0（一列 = 1 块，一个数据源 64×64 块，即 core 级 dl6），粗档由 DH 原生 DOWN_SAMPLED 下采样。

### 坐标语义（三处定案）

这三处任何一处理解错，渲染就会整体偏移或者炸出冲天石柱：

1. **TD 的 `HeightConverter.convertToMinecraftHeight` 返回的是第一个空气方块的 y**（density 函数数学、FeaturePlacer 姿势、mixin 注释、DH 半开区间、实测高 1 格，五证合一）。早期代码 +1 属双重补偿，已删。
2. **DH 存储层 y 为 12 位无符号（0~4095），负值回绕**：主世界 minY=-64 会被存成 4032，段底(4032) > 段顶(~70) 几何倒挂，主渲染路径画出 y70~4032 的冲天石柱，邻接路径每分钟刷 10MB 的 `Negative y size` 错误日志。
3. **DH 的 y 语义实为距世界底偏移（相对 y）**：顶用绝对坐标入库，整个地形矮 64 格（=|minY|）。

结论：全部 y 统一转相对 `yRel = yAbs − minY`，`dataBottom = 0` 即世界底。

### 水体判定（双通道）

- **河流（fluvial）**：`waterMask` 是 0-255 的水强度编码，<8 为子块抗锯齿边缘格（TD chunk 侧同样跳过）；`waterSurface` 无水时为 NaN（Short.MIN_VALUE 哨兵解码）。成池条件 = 强度 ≥8 且液面有限且液面高于地形，三者缺一不可。
- **海洋（ocean）**：TD cell 的 elevation 为负即海床深度，水段填 [海床, 海平面 y63]。fluvial 与 ocean 在 TD 侧互斥，代码防御性取液面高者。
- **冰盖**：冻河/冻洋群系的水段方块替换为冰。

### 群系驱动表面

TD chunk 侧的表面方块 = vanilla `SurfaceSystem.buildSurface` × TD BiomeSource，而 BiomeSource 读的正是 LOD 查询的同一份 tile `biomeIndexes`——数据与规则双同源。`TdSurfaceBlockMap` 把 vanilla 主世界 surface rule 中 LOD 输入可达的分支剪枝成 65 群系表（主导表面 / 水下顶部 / 冰盖三字段），消灭系统性偏差，剩余差异只有 64 块降采样的固有模糊。

保真边界（有意取舍）：小于采样格的噪声 patch（粗泥、花岗岩碎斑、恶地条纹）不复刻；old_growth 系 podzol、雪盖系 snow_block 等近似取舍记录在表注释里。

河床材质按水强度分档，确定性空间散列（键 = 世界块坐标，跨数据源稳定，配比为经验值）：

- 弱水（强度 <72）：沙 62.5% / 砾 37.5%
- 强水（≥72）：砾 85.2% / 黏土 14.8%

### 植被（L1 花草色斑）

`vegetation.coloring` 开启时，干列列顶按群系花池密度做确定性哈希换色——命中列顶面取花色、列高不变（花/草为 cross 方块，DH 顶面取色生效）；同一列永不跳变。L4 装饰车道落地的列由真实装饰接管，帽段自动让位；其余列继续由本层补观感。属统计近似、非与 chunk 生成同源。

### 结构与地物车道

两条车道（`structure` / `features`）：计算核心是纯函数，调度外壳决定同步或异步（默认异步——地形 LOD 先出图，车道算完由刷新钩子请求 DH 重拉该 region 补全；同步模式一次成型，无刷新闪烁）。

- 结构：与 vanilla 同 seed 的撒点数学定址（外扩扫描防跨 region 截断），命中后在哑世界（纯内存 ProtoChunk，真世界零副作用）里跑真结构生成器，产出按列折叠成段（上限 `column_segments`）；贴地段表面换色（`surface_blend_depth`）。
- 地物装饰：逐 chunk 复刻 vanilla 群系装饰循环（同款 `setDecorationSeed` 确定流），经装饰视图放置树、花草与雪（背景三态：床 / 水 / 空气，写入优先）；只跑 dl0。

### 性能设计

- 无锁缓存：群系样式 `ConcurrentHashMap`，并发重复构造等价无害，不加锁
- wrapper（方块/群系对象）首次生成时懒取一次，之后命中缓存
- 每列 `ArrayList(3)` 预分配（容量提示，非段数上限；车道列链并入后单列可达十余段）
- 海平面 y63 等常量循环外提
- TD 一次查询拉整块矩形，避免 per-column 查询往返

## 许可 / License

MPL-2.0

---

# Terrain Diffusion Horizons (TDH)

An LOD generation accelerator for Distant Horizons, powered by Terrain Diffusion.

Uses Terrain Diffusion's AI terrain model as the LOD data source for Distant Horizons.
Distant terrain no longer depends on real chunk generation; it is queried and translated directly from the terrain model.

Voxy is not supported: the existing 1.20.1 build is unstable in my experience, and its ARR license is a hassle; accelerating Voxy would mean I'd have to write a 1.20.1, self-generating Voxy—and even a partial implementation means digesting the existing forks—so the workload is not realistic. A switch to Voxy is unlikely for versions after 1.20.1 as well.

## Performance

Measured (Ryzen 5 5600X + NVIDIA CMP 30HX, vanilla render distance 5 + DH render distance 512):

TD world scale 2 + DH quality 4 / load 4:

- Full LOD generation in about 30 minutes
- About 500 chunks/s, sustaining that speed about 87% of the time

TD world scale 6: about 700 chunks/s steady state, about 1500 chunks/s peak.

When a world is freshly created, DH needs a warm-up: a large batch of chunks is enqueued and preprocessed (no LOD will be generated during this phase), then comes a brief low-speed phase, and full speed after that.

## Known Limitations

- **Distant structures and vegetation are approximations, not block-exact replicas**: positions and content follow vanilla's generation rules under the same seed, but each column keeps only a few block layers (default cap 6, `column_segments`). Known gaps: no cactus or dead bushes in deserts, no mangrove trees, no aquatic plants, no snow on tree canopies, among others.
- **Riverbank sand/gravel scatter differs from vanilla**: these are vanilla's fine trimmings at biome borders, at a scale too small for distant sampling to pick up
- **LOD staircase**: at distance one cell covers a wider patch of terrain, so far geometry looks stepped. Inherent to LOD, no fix
- **River valley tint band**: a narrow strip of slightly off tint along rivers, visible at distance. Rivers are a distinct biome in the data, with grass color slightly different from the banks; vanilla blends colors across biome borders up close, while each distant cell carries a single biome color with no blending, so the difference shows as a band

## Features

- LODs straight from the AI terrain model, skipping real chunk generation
- Biome-driven surface blocks: grass/sand/snow match the biome, as in vanilla
- Water: rivers with their own surface level, oceans filled to sea level, ice on frozen rivers/oceans
- Riverbed material by water flow strength (sand/gravel/clay); the same spot always gets the same material
- Distant structures: same positions as vanilla (villages, monuments, mineshafts, ...), detail simplified
- Distant vegetation: trees, flowers, snow cover, same positions as vanilla
- Correct biome colors at distance
- Pure bridge layer: no mixins, no Access Transformers, no reflection

## Dependencies

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.30 |
| Distant Horizons | 3.3.3 |
| Terrain Diffusion | 1.20.1 |

TDH is developed and tested against DH 3.3.3; other versions are untested.

## Installation

1. Forge 47.2.30 (1.20.1)
2. Drop three jars into `mods/`: Distant Horizons, Terrain Diffusion, TDH
3. Create a world the Terrain Diffusion way; on world load TDH takes over distant generation automatically (the log line `TDH 生成器注册完成` marks it); non-TD worlds fall back to DH's native generation
4. Make sure DH's Distant Generation is enabled

## Configuration

Config file: `config/terrain-diffusion-mc/terrain-diffusion-horizons.toml` (generated on first run, with comments explaining each key)

| Key | Default | Description |
|---|---|---|
| `general.enabled` | `true` | Master switch; false = TDH off, DH generates distant terrain on its own |
| `terrain.surfaceBlock` | `minecraft:stone` | Fallback surface block (used when biome surface is off or resolution fails) |
| `terrain.fillerBiome` | `minecraft:plains` | Fallback biome (used when biome surface is off; affects distant grass color) |
| `terrain.useBiomeSurface` | `true` | Biome-driven surface blocks, as in vanilla; false = off (saves memory, the fallback block is used everywhere) |
| `terrain.surfaceBlockOverrides` | `{}` | Per-biome surface block overrides (unlisted biomes use the default) |
| `vegetation.coloring` | `true` | Flower speckle: distant grassland gets flower colors at the biome's natural flower density (color only, height unchanged) |
| `vegetation.grassColoring` | `false` | Grass speckle (off by default: distant tall grass and grass blocks already share one color) |
| `structure.enabled` | `true` | Distant structures master switch |
| `structure.async` | `true` | Async: terrain appears first, structures fill in when done; false = wait for structures (no refresh flicker) |
| `structure.pad_chunks` | `16` | Scan padding (chunks): keeps large structures straddling borders from being clipped |
| `structure.column_segments` | `6` | Block layers per structure column; 1 = legacy single layer; more layers = more faithful, more render cost |
| `structure.surface_blend_depth` | `3` | Buried-structure recolor depth (roads, farmland); negative = off |
| `features.enabled` | `true` | Distant vegetation master switch (flower speckle above has its own switch) |
| `features.async` | `true` | Same as `structure.async` |
| `features.decoration_steps` | `["vegetal_decoration", "top_layer_modification"]` | Generation steps to run (valid values in the config file comments, case-insensitive); empty array disables vegetation |
| `features.decoration_max_detail_level` | `0` | Detail-level cutoff: regions at farther detail levels skip vegetation (0 = default, only the closest level runs it) |
| `features.decoration_prefetch` | `true` | Height fix: fixes grid-shaped artifacts and misplaced trees at distance; false = off |
| `features.decoration_water_background` | `true` | Water check: fixes trees and flowers floating on lakes; needs the height fix on |

```toml
[general]
enabled = true

[terrain]
surfaceBlock = "minecraft:stone"
fillerBiome = "minecraft:plains"
useBiomeSurface = true

# sparse biome-key to surface-block overrides (optional):
# [terrain.surfaceBlockOverrides]
# "minecraft:plains" = "minecraft:snow_block"

[vegetation]
coloring = true
grassColoring = false

[structure]
enabled = true
async = true
pad_chunks = 16
column_segments = 6
surface_blend_depth = 3

[features]
enabled = true
async = true
decoration_steps = ["vegetal_decoration", "top_layer_modification"]
decoration_max_detail_level = 0
decoration_prefetch = true
decoration_water_background = true
```

Memory note: `useBiomeSurface=true` is about 520MB, false about 150MB (measured).
If new keys added by an upgrade do not show up in the file, delete the toml and it regenerates.
Config changes take effect after re-entering the world.

## How It Works

### Architecture: a pure bridge layer

No mixins, no Access Transformers, no reflection—everything goes through the two sides' public APIs: DH's `DhApi.worldGenOverrides` and TD's `LocalTerrainProvider` static facade. 23 classes across three packages, orthogonal responsibilities:

| Package | Responsibility |
|---|---|
| root | mod entry + seeded TOML config (`TerrainDiffusionHorizons` / `TdConfig`) |
| `dh` | DH registration and lifecycle (`DhCompat`), LOD translation core (`TdDhWorldGenerator`), coordinate translator (`TdLodTranslator`), biome surface/flower tables (`TdSurfaceBlockMap` / `TdFlowerMap`), lane refresh hook (`DhRefreshHook`) |
| `dh.lane` | async lane skeleton: pure-function core + scheduling shell + payloads and cache keys (B1) |
| `dh.structure` | structure spread (vanilla same-seed math), silent-world assembly, biome decoration (B2/L3/L4) |

Registration chain: the mod constructor hooks `DhApiLevelLoadEvent`, then the world-load callback runs a triple guard (overworld / TD world active / seed obtainable) and calls `registerWorldGeneratorOverride`. There is no unregister API on world unload; the generator clears its own references in `close()`. Overworld only (hard boundary).

### Data flow

DH generation queue → `generateLod(posX, posZ, dl0)` → `runAsync` shell onto a DH pool thread → TD `getRiverTerrainData(block rectangle, withBiomes)` pulls a whole 64×64 block at once (four arrays: `elevation` in meters / `waterMask` water intensity 0-255 / `waterSurface` water level in meters / `biomeIndexes` biome indices) plus structure/decoration lane column segments (terrain publishes first, lanes refresh in when done) → a double loop translates each column into several `DhApiTerrainDataPoint`s (bed/surface, folded structure, water, flower cap, air; seamlessly tiled per column) → `setApiDataPointColumn` → `resultConsumer` called exactly once.

API dl0 only (one block per column, 64×64 per data source, i.e. core-level dl6); coarser levels come from DH's native DOWN_SAMPLED downsampling.

### Coordinate semantics (three settled points)

Get any of these wrong and the render shifts wholesale or erupts into sky pillars:

1. **TD's `HeightConverter.convertToMinecraftHeight` returns the y of the first air block** (density-function math, FeaturePlacer posture, mixin comments, DH half-open intervals, measured one block high—five independent confirmations). The early +1 was double compensation and has been removed.
2. **DH's storage layer y is 12-bit unsigned (0-4095), negatives wrap around**: an overworld minY=-64 gets stored as 4032; segment bottom (4032) above segment top (~70) inverts the geometry—the main render path draws sky pillars from y70 to y4032, and the neighbor path spams 10MB/min of `Negative y size` errors.
3. **DH's y is really an offset from the world bottom (relative y)**: store absolute coordinates and the whole terrain sits 64 blocks short (= |minY|).

Conclusion: all y values are normalized to relative coordinates, `yRel = yAbs − minY`; `dataBottom = 0` is the world floor.

### Water determination (dual channel)

- **Rivers (fluvial)**: `waterMask` encodes water intensity 0-255; values below 8 mark sub-block anti-aliasing edge cells (skipped on TD's chunk side too); `waterSurface` is NaN when dry (decoded from a Short.MIN_VALUE sentinel). Pool condition = intensity ≥8 AND finite water level AND level above terrain—all three required.
- **Oceans**: negative elevation in a TD cell is seabed depth; the water segment fills [seabed, sea level y63]. Fluvial and ocean are mutually exclusive on the TD side; the code defensively takes the higher surface.
- **Ice caps**: frozen river/ocean biomes get their water segment replaced with ice.

### Biome-driven surface

TD's chunk-side surface block = vanilla `SurfaceSystem.buildSurface` × the TD BiomeSource, and the BiomeSource reads the very same tile `biomeIndexes` the LOD query uses—data and rules share one source. `TdSurfaceBlockMap` prunes the LOD-reachable branches of the vanilla overworld surface rules into a 65-biome table (primary surface / underwater top / ice cap, three fields), eliminating systematic bias; what remains is only the inherent blur of 64-block downsampling.

Fidelity boundaries (deliberate trade-offs): noise patches smaller than a sample cell (coarse dirt, granite blobs, badlands banding) are not reproduced; approximations such as old_growth podzol and snow-block snow cover are documented in the table's comments.

Riverbed material is tiered by water intensity with deterministic spatial hashing (key = world block coordinates, stable across data sources; ratios are empirical):

- Weak water (intensity <72): sand 62.5% / gravel 37.5%
- Strong water (≥72): gravel 85.2% / clay 14.8%

### Vegetation (L1 flower speckle)

With `vegetation.coloring` enabled, dry column tops get deterministic-hash color swaps by biome flower-pool density—hit columns take the flower color on top while the column height stays unchanged (flowers/grass are cross blocks; only the top-face tint changes). The same column never flickers. Columns covered by the L4 decoration lane hand over to real decoration (the cap yields automatically); the rest keep this layer as visual fallback. Statistical approximation, not same-source with chunk generation.

### Structure & feature lanes

Two lanes (`structure` / `features`): pure-function compute cores behind a scheduling shell that picks sync or async (default async—terrain LOD publishes first, and a refresh hook asks DH to re-pull each region once the lane finishes; sync mode renders once, no flicker).

- Structures: same-seed placement math as vanilla (padded scan guards cross-region clipping); hits run the real structure generator inside a silent world (in-memory ProtoChunk, zero side effects), folded into column segments (cap `column_segments`); near-surface segments recolor the top block (`surface_blend_depth`).
- Decoration: replays vanilla's per-chunk biome decoration loop (same `setDecorationSeed` deterministic stream), placing trees, flowers and snow through a decoration view (three-state background: bed / water / air, writes take priority); runs at dl0 only.

### Performance design

- Lock-free caching: biome styles in a `ConcurrentHashMap`; concurrent duplicate construction is harmless, so no locks
- Wrappers (block/biome objects) are lazily fetched once on first use, then served from cache
- Per-column `ArrayList(3)` preallocation (a capacity hint, not a segment cap; with lane column chains merged in, a column can carry a dozen-plus segments)
- Constants like sea level y63 hoisted out of loops
- TD pulls the whole block rectangle per query, avoiding per-column round trips

## License

MPL-2.0
