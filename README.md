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

- **无地物与结构**：DH LOD 数据点的结构就是每列"表面段 + 树冠段 + 水段 + 空气段"（树冠是统计近似，非真实地物），没有 feature/structure 层；TD 模型输出的也是地形骨架（高度 + 水体 + 群系）。远距观看不受影响。
- **河岸散布沙/砾与原版有偏差**：这些块属于原版表面规则的噪声分支和 feature 层产物，发生在跨群系边界的碎片尺度上，LOD 采样天然拿不到
- **LOD 阶梯感**：64 块降采样的固有几何模糊，无解。
- **河谷色带**：远处沿河可见一条色调略异的窄带。河流在数据上是独立群系，草色按群系取色（grass tint），河流群系与两岸群系的草色有细微差异；原版近距离渲染会在群系边界对颜色做渐变过渡，而 LOD 每列数据只携带一个群系、没有这种过渡，色差就以带状暴露出来。

## 特性 / Features

- AI 地形模型直接出 LOD，跳过真实 chunk 生成
- 群系驱动表面方块：65 群系剪枝表，数据与 chunk 生成同源
- 水体双通道：河流（水强度 + 液面）与海洋（海床 → 海平面水段），冻河/冻洋冰盖
- 河床材质按水强度分档（沙/砾/黏土），确定性空间散列
- 群系 wrapper 进 data point，DH 远距渲染正确色调
- 纯桥接层：无 mixin、无 AT、无反射

## 依赖与版本 / Dependencies

| 依赖 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.30 |
| Distant Horizons | 3.3.3 |
| Terrain Diffusion | 1.20.1（依赖其公开查询 API `LocalTerrainProvider`） |

TDH 针对 DH 3.3.3 开发并实测通过。其余版本未测试。

## 安装 / Installation

1. Forge 47.2.30 (1.20.1)
2. `mods/` 放入三个 jar：Distant Horizons、Terrain Diffusion、TDH
3. 按 Terrain Diffusion 的方式创建世界；进世界后 TDH 通过 DH 的 LevelLoad 事件自动注册生成器（日志可见 `TDH 生成器注册完成`），非 TD 世界自动让行给 DH 原生生成链
4. 确认 DH 的 Distant Generation 已开启

## 配置 / Configuration

配置文件：`config/terrain-diffusion-mc/terrain-diffusion-horizons.toml`（首次运行播种带注释的默认模板；旧版 JSON 检出时日志提示手动迁移）。

| 键 | 默认 | 说明 |
|---|---|---|
| `general.enabled` | `true` | 总开关，false 时不注册生成器，走 DH 原生路径 |
| `terrain.surfaceBlock` | `minecraft:stone` | fallback 表面方块（纯几何模式或群系解析失败时） |
| `terrain.fillerBiome` | `minecraft:plains` | fallback 群系（data point 携带） |
| `terrain.useBiomeSurface` | `true` | 群系驱动表面；false 退纯几何模式 |
| `terrain.surfaceBlockOverrides` | `{}` | 群系 key → 方块 ID 稀疏覆盖，只覆地表字段 |
| `vegetation.coloring` | `true` | L1 植被树冠近似（干列按群系密度叠树冠段） |
| `vegetation.grassColoring` | `false` | 草丛色斑留口（默认关：草丛与草方块共用 grass colormap） |
| `structure.enabled` | `true` | 结构 LOD 车道总开关（同源撒点 + L3 组装） |
| `structure.async` | `true` | 异步：地形 LOD 先出图，结构算完自动刷新补全 |
| `structure.pad_chunks` | `16` | 扫描外扩（chunk），bbox 跨 region 的结构防截断 |
| `structure.column_segments` | `6` | 列折叠段数上限；`1` = 旧单段行为（配置级回滚开关） |
| `structure.surface_blend_depth` | `3` | 贴地换色深度；负数关闭（悬空结构不受影响） |
| `features.enabled` | `true` | 地物 LOD 车道总开关（L1 花草走同步快路径不受此段影响） |
| `features.async` | `true` | 语义同 `structure.async` |

```toml
[general]
enabled = true

[terrain]
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
useBiomeSurface = true

# 群系 key -> 地表方块稀疏覆盖 (可选):
# [terrain.surfaceBlockOverrides]
# "minecraft:plains" = "minecraft:snow_block"

[vegetation]
coloring = true
```

内存注意：`useBiomeSurface=true` 约 520MB，false 约 150MB（实测）。
升级新版本后新增键不显示在文件里时，删除 toml 让其重播种即可。
配置改动需重进世界生效。

## 实现原理 / How It Works

### 架构：纯桥接层

无 mixin、无 Access Transformer、无反射，全部走两端公开 API：DH 的 `DhApi.worldGenOverrides` 与 TD 的 `LocalTerrainProvider` 静态门面。七个类，职责正交：

| 类 | 职责 |
|---|---|
| `TerrainDiffusionHorizons` | mod 入口 |
| `DhCompat` | LevelLoad 事件挂钩 + 守卫链 + 注册生成器 |
| `TdDhWorldGenerator` | `IDhApiWorldGenerator` 实现，核心翻译循环 |
| `TdLodTranslator` | DH 参数 → TD 查询坐标（纯函数，零状态） |
| `TdSurfaceBlockMap` | 65 群系表面样式剪枝表 |
| `TdVegetationMap` | 29 群系植被统计表（L1 树冠近似） |
| `TdConfig` | 播种式 TOML 配置 |

注册链：mod 构造期挂 `DhApiLevelLoadEvent` → 世界加载回调 → 三重守卫（主世界 / TD 世界激活 / seed 可得）→ `registerWorldGeneratorOverride`。世界卸载无反注册 API，由生成器自身 `close()` 清引用。只在主世界工作（硬边界）。

### 数据流

DH 生成队列 → `generateLod(posX, posZ, dl0)` → `runAsync` 壳入 DH 池线程 → TD `getRiverTerrainData(块矩形, withBiomes)` 一次拉整块 64×64 → 四个数组（`elevation` 海拔米 / `waterMask` 水强度 0-255 / `waterSurface` 液面米 / `biomeIndexes` 群系索引）→ 双循环每列翻译成 1~3 个 `DhApiTerrainDataPoint`（表面段 / 树冠段 / 水段 / 空气段；树冠与水互斥）→ `setApiDataPointColumn` → `resultConsumer` 恰一次回执。

只供 API dl0（64 块/列，即 core 级 dl6），粗档由 DH 原生 DOWN_SAMPLED 下采样。

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

### 植被（L1 统计近似）

`vegetation.coloring` 开启时，干列按群系植被密度查表（29 群系，`TdVegetationMap`）生长树冠段；确定性空间散列决定该列是否长树与树冠高度——同一列永不跳变。树冠段与水段互斥。属观感近似、非与 chunk 生成同源；L4 地物同源落地后由真实数据替代。

### 性能设计

- 无锁缓存：群系样式 `ConcurrentHashMap`，并发重复构造等价无害，不加锁
- wrapper（方块/群系对象）首次生成时懒取一次，之后命中缓存
- 每列 `ArrayList(3)` 预分配，data point 段数上限 3
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

- **No features or structures**: a DH LOD data point column is just "surface segment + canopy segment + water segment + air segment" (the canopy is a statistical approximation, not real features)—there is no feature/structure layer; the TD model likewise outputs only the terrain skeleton (height + water + biomes). Not visible at distance.
- **Riverbank sand/gravel scatter differs from vanilla**: these blocks come from noise branches of the vanilla surface rules and the feature layer, at fragment scale across biome boundaries—out of reach for LOD sampling.
- **LOD staircase**: inherent geometric blur from 64-block downsampling. No fix.
- **River valley tint band**: a narrow strip of slightly off tint along rivers, visible at distance. Rivers are a distinct biome in the data, and grass tint is taken per biome; river biomes differ slightly from their banks. Vanilla near rendering blends colors across biome boundaries, while each LOD column carries exactly one biome with no blending, so the difference shows up as a band.

## Features

- LODs straight from the AI terrain model, skipping real chunk generation
- Biome-driven surface blocks: a 65-biome pruned table, sharing its data source with chunk generation
- Dual-channel water: rivers (water intensity + surface level) and oceans (seabed to sea level), ice caps on frozen rivers/oceans
- Riverbed material tiered by water intensity (sand/gravel/clay), deterministic spatial hashing
- Biome wrappers carried in data points, correct tint at DH render distance
- Pure bridge layer: no mixins, no Access Transformers, no reflection

## Dependencies

| Key | Default | Description |
|---|---|---|
| `general.enabled` | `true` | Master switch; false = generator not registered, DH's native path takes over |
| `terrain.surfaceBlock` | `minecraft:stone` | Fallback surface block (pure-geometry mode, or failed biome resolution) |
| `terrain.fillerBiome` | `minecraft:plains` | Fallback biome (carried by data points) |
| `terrain.useBiomeSurface` | `true` | Biome-driven surface; false = pure-geometry mode |
| `terrain.surfaceBlockOverrides` | `{}` | Sparse biome-key to block-ID overrides, surface field only |
| `vegetation.coloring` | `true` | L1 vegetation canopy approximation (dry columns get canopies by biome density) |
| `vegetation.grassColoring` | `false` | Grass speckle toggle (off by default: grass shares the grass-block colormap) |
| `structure.enabled` | `true` | Structure LOD lane master switch (same-seed placement + L3 assembly) |
| `structure.async` | `true` | Async: terrain LOD publishes first, structures refresh in when done |
| `structure.pad_chunks` | `16` | Scan padding (chunks); protects cross-region structures from clipping |
| `structure.column_segments` | `6` | Per-column segment cap; `1` = legacy single-segment behavior (rollback switch) |
| `structure.surface_blend_depth` | `3` | Surface recolor depth; negative disables (floating structures unaffected) |
| `features.enabled` | `true` | Feature LOD lane master switch (L1 flowers take a separate sync path) |
| `features.async` | `true` | Same semantics as `structure.async` |

## Installation

1. Forge 47.2.30 (1.20.1)
2. Drop three jars into `mods/`: Distant Horizons, Terrain Diffusion, TDH
3. Create a world the Terrain Diffusion way; on world load, TDH registers its generator via DH's LevelLoad event (the log line `TDH 生成器注册完成` marks it); non-TD worlds fall back to DH's native generation chain
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
## Configuration

Config file: `config/terrain-diffusion-mc/terrain-diffusion-horizons.toml` (a commented default template is seeded on first run; if a legacy JSON is detected, the log tells you to migrate manually).

| Key | Default | Description |
|---|---|---|
| `general.enabled` | `true` | Master switch; false = generator not registered, DH's native path takes over |
| `terrain.surfaceBlock` | `minecraft:stone` | Fallback surface block (pure-geometry mode, or failed biome resolution) |
| `terrain.fillerBiome` | `minecraft:plains` | Fallback biome (carried by data points) |
| `terrain.useBiomeSurface` | `true` | Biome-driven surface; false = pure-geometry mode |
| `terrain.surfaceBlockOverrides` | `{}` | Sparse biome-key to block-ID overrides, surface field only |
| `vegetation.coloring` | `true` | L1 vegetation canopy approximation (dry columns get canopies by biome density) |

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
```

Memory note: `useBiomeSurface=true` is about 520MB, false about 150MB (measured).
If new keys added by an upgrade do not show up in the file, delete the toml to re-seed it.
Config changes take effect after re-entering the world.

## How It Works

### Architecture: a pure bridge layer

No mixins, no Access Transformers, no reflection—everything goes through the two sides' public APIs: DH's `DhApi.worldGenOverrides` and TD's `LocalTerrainProvider` static facade. Seven classes, orthogonal responsibilities:

| Class | Responsibility |
|---|---|
| `TerrainDiffusionHorizons` | mod entry |
| `DhCompat` | LevelLoad event hook + guard chain + generator registration |
| `TdDhWorldGenerator` | `IDhApiWorldGenerator` implementation, the core translation loop |
| `TdLodTranslator` | DH params to TD query coordinates (pure function, stateless) |
| `TdSurfaceBlockMap` | 65-biome surface-style pruned table |
| `TdVegetationMap` | 29-biome vegetation statistics table (L1 canopy approximation) |
| `TdConfig` | seeded TOML config |

Registration chain: the mod constructor hooks `DhApiLevelLoadEvent`, then the world-load callback runs a triple guard (overworld / TD world active / seed obtainable) and calls `registerWorldGeneratorOverride`. There is no unregister API on world unload; the generator clears its own references in `close()`. Overworld only (hard boundary).

### Data flow

DH generation queue → `generateLod(posX, posZ, dl0)` → `runAsync` shell onto a DH pool thread → TD `getRiverTerrainData(block rectangle, withBiomes)` pulls a whole 64×64 block at once → four arrays (`elevation` in meters / `waterMask` water intensity 0-255 / `waterSurface` water level in meters / `biomeIndexes` biome indices) → a double loop translates each column into 1-3 `DhApiTerrainDataPoint`s (surface / canopy / water / air segments; canopy and water are mutually exclusive) → `setApiDataPointColumn` → `resultConsumer` called exactly once.

API dl0 only (64 blocks per column, i.e. core-level dl6); coarser levels come from DH's native DOWN_SAMPLED downsampling.

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

### Vegetation (L1 statistical approximation)

With `vegetation.coloring` enabled, dry columns grow canopies via a per-biome vegetation density table (29 biomes, `TdVegetationMap`); deterministic spatial hashing decides whether a column gets a tree and its canopy height—the same column never flickers. Canopy and water segments are mutually exclusive. This is a visual approximation and not same-source with chunk generation; it will be replaced by real data once L4 lands.

### Performance design

- Lock-free caching: biome styles in a `ConcurrentHashMap`; concurrent duplicate construction is harmless, so no locks
- Wrappers (block/biome objects) are lazily fetched once on first use, then served from cache
- Per-column `ArrayList(3)` preallocation, data point segments capped at 3
- Constants like sea level y63 hoisted out of loops
- TD pulls the whole block rectangle per query, avoiding per-column round trips

## License

MPL-2.0
