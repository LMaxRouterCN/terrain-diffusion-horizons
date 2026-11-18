# B批设计稿 — 异步车道 / L2 结构撒点 / TOML 配置

**状态**: 待 max 过审（过审后才动码，既定流程）
**依据**: [长期记忆: 123] 立项令逐字存档 / [115] 可行性报告 docs/feature-structure-feasibility.md §5§6 / [117] recon6 静态门 / [118] 地物通用性 / [119] 沉没过滤器定稿
**前置**: A批 L1 植被虚拟树格已落地（commit aad924c，已部署 TEST 待重测）

## 0. 立项令回溯（逐字见 [长期记忆: 123]）

| max 原话 | 架构翻译 |
|---|---|
| 结构和地物lod和地形lod分开，不阻塞地形生成 | 慢计算（结构 oracle/组装）与地形生成分线程，地形 LOD 先发布 |
| 快的就快跑，慢的就慢慢跑 | 快路径（地形+L1 植被）同步即时；慢车道异步补 |
| 结构异步生成和地物异步生成单独两个配置项 | TOML 两键：`structure.async` / `features.async` |
| 配置文件格式改成toml，写注释 | night-config TOML，注释保留 |
| 挪进config里的terrain-diffusion-mc文件夹 | `config/terrain-diffusion-mc/` + 旧配置值迁移 |
| 关了就是同步模式，地形lod生成之前等地物和结构全跑完才显示 | `async=false` → 车道核心在生成线程内联（一次成型，无等待原语） |
| 默认开 | 两键默认 `true` |

## 1. 范围

**做**：车道基建（结构/地物两条）+ TdStructureOracle（L2 撒点同源）+ 配置 TOML 迁移 + 同步语义。
**不做**：L3 结构组装（templateManager + jigsaw）= C 批；L4 逐株地物 = D 批（[115] 批次裁定）。
**边界澄清**：L1 植被是纯算术快路径（列级格哈希，微秒级），不占车道，维持同步内联。车道载荷现阶段 = 结构 oracle（L2，中速）；地物车道骨架先立、键先占位，L3/L4 载荷后续批次填——两键现在就按立项令立全，语义先于载荷到位（[123]⑥ 教训：max 原话优先级 > 报告排期）。

## 2. 车道模型（核心架构决策）

车道 = **计算核心 + 调度外壳**，两层严格解耦：

- **计算核心**：纯函数，region 坐标 → 结构段/地物段列表。无状态、无副作用、无锁，任意线程可跑。
- **调度外壳**：async 开关唯一职责 = 决定核心的执行位置。
  - `true`（默认）：提交 executor。地形 LOD 先发布（快跑），车道算完回写缓存表 + 触发刷新（慢慢跑）。
  - `false`：生成线程内联调用核心。generate 返回时数据完整，LOD 一次成型。

同步模式**不是**"生成线程等待异步线程"（那需要锁/条件变量，病态耦合），而是根本不起异步——同一份核心，两个调用位置，零等待原语。开关切换不触碰核心一行。

## 3. 数据流


DH 生成请求 (region, detailLevel)
│
▼
TdDhWorldGenerator.generate
├─ 快路径（同步，毫秒级）：TD 地形 + L1 植被格哈希
├─ 车道缓存表查询：命中 → 结构/地物段叠加进数据点
└─ 返回完整 dataPoints → LOD 发布
车道线程（async=true 时）：
oracle(region) → 结构段 → 写缓存表（ConcurrentHashMap）
→ 触发刷新（待验#1）→ DH 重拉该 region
→ 再进 generate → 命中缓存 → 新 LOD 替换
async=false：oracle 直接在 generate 调用栈内执行，跳过缓存与刷新

**缓存表是唯一交界面**：车道结果永远作为"生成输入的一部分"经 generate 合并。不存在"修改已发布 LOD 数据"的 API 需求——刷新只是让 DH 对该 region 重新拉取一次。
## 4. 组件设计

### 4.1 TdConfig — TOML 迁移

- 路径：`config/terrain-diffusion-mc/terrain-diffusion-horizons.toml`（文件夹名立项令钦定；文件名带 modid 避免与 TD fork 配置混淆）
- 解析：night-config（Forge 运行时自带，零新依赖），写回保留注释
- 迁移：首启检测旧路径存在 → 值迁移 → 写新位置（用户无感升级）
- 键布局（草案；B1 盘点现有 TdConfig 键后定稿，`tint` 等现有键平移）：


toml
terrain-diffusion-horizons 配置
async 语义（两键同义）：true = 地形 LOD 先发布，车道算完后自动刷新补全；
false = 同步模式，地形 LOD 扣住等车道全部算完才显示（慢，一次成型无闪烁）
[structure]
结构 LOD 总开关
enabled = true
结构车道异步开关
async = true
[features]
地物 LOD 总开关（当前含 L1 植被）
enabled = true
植被上色开关
tint = true
地物车道异步开关（当前 L1 植被为快路径不受影响；为 L3/L4 真地物车道预留语义）
async = true

### 4.2 Lane 骨架

- `LaneCore`（接口）：`List<LaneSegment> compute(long worldSeed, DhLodPos region)` — 纯计算核心
- `LaneManager`：executor（线程数待验#4 定）+ 请求去重（in-flight set）+ 缓存表（ConcurrentHashMap，region 键）
- 合并点：generate 内查表，命中叠加段进数据点
- 异常隔离：单 region 计算炸 = catch + 该 region 进黑名单跳过，不连坐（[118] 异常隔离结论；列缓冲 feature 级事务要求对齐）
- 沉没过滤器：车道段写入时按 [119] 定稿执行——表面以下一律跳过，标[临时]

### 4.3 TdStructureOracle — L2 撒点

- 输入：worldSeed + structure set 撒点参数
- 参数来源（两层通用性分级）：
  - **vanilla 结构集**：离线读客户端 jar `data/minecraft/worldgen/structure_set/*.json`（spacing/separation/salt）——零 runtime 依赖，多人也稳
  - **模组结构集**：runtime registry 遍历（挂多人 registry 同步悬案 [117]⑧，验证后开；模组加地物走 biome features 列表的正道不在此层 [118]）
- 核心：`getPotentialStructureChunk` 纯函数链（[115] javap 五轮实证：setLargeFeatureWithSalt → RandomSpreadType.evaluate ×2，零 chunk 上下文，ChunkGeneratorStructureState.createForNormal 静态工厂离线可造）
- 首批范围：随机撒点型全量（village / temple 系 / mansion / monument / outpost / ancient city 等）；rings 型（stronghold，concentricRings）待验#2 实测后进
- 输出：region → List<(结构集名, chunkX, chunkZ)>
- 验收：同 seed 下 TDH oracle 撒点坐标 vs vanilla `/locate` 逐一对齐（测试代码，max 跑——[91] 数据可信度：眼见为实）

### 4.4 PassB — 回写刷新

- 职责：车道算完 → 写缓存表 → 触发 DH 对脏 region 重拉
- 触发路径（待验#1，B1 首件事 javap 探明，按优先级）：
  1. DH API 面的主动 refresh/invalidate（若有最干净）
  2. `WorldGenerationQueue` 的 updateFromDataSource → applyToParent 机械（[117] 参考实现，DH 内部生成链原生存在）
  3. 兜底：不主动触发——数据已在缓存表，玩家移动/重进世界自然命中（视觉延迟到下次拉取，数据不丢）

## 5. 同步模式语义

max 原话："关了就是同步模式，地形lod生成之前等地物和结构全跑完才显示"。

实现 = `async=false` 时车道核心在 generate 调用栈内联。DH 生成线程串行：地形算完 → 车道算完 → 数据点组装 → 返回。LOD 从出现那一刻就是完整形态，无二次刷新闪烁。

## 6. L2 视觉呈现 — 呈 max 裁决

L2 oracle 只有坐标没有方块布局（方块布局是 C 批 L3 template 组装的事），渲染选择：

- **方案甲（推荐）**：B 批不渲染结构视觉。oracle 结果落缓存表 + 调试日志。理由：撒点级渲染只能画"结构主色块"，方块选择是观感编造，违背数据同源精神；等 L3 一次到位真方块。
- **方案乙**：锚点最小渲染——结构 start 位置画 1-2 列结构主色方块（神殿=砂岩、村庄=原木）。有即时视觉反馈（"远处有个神殿"级别），但方块选择无 vanilla 依据。

**推荐甲**。B 批的可感知交付 = 车道架构行为本身（LOD 秒出不被拖慢 + 开关语义生效）+ oracle 坐标经 `/locate` 对照验证。

## 7. 待验项（B 批内消化）

| # | 项 | 解法 | 里程碑 |
|---|---|---|---|
| 1 | DH 刷新 API 面 | javap 探 EDhApi 全包 | B1 |
| 2 | concentricRings 性能（stronghold） | 实测（[115] 推算秒级） | B2 |
| 3 | 多人 registry 同步 | 联机实测（挂 [117]⑧） | B2 |
| 4 | executor 选型（DH 任务系统 vs 自起池） | javap + [117] 参考实现读 | B1 |
| 5 | 现有 TdConfig 键盘点 | read 源码 | B1 |

## 8. 里程碑切片

- **B1 车道骨架 + TOML**：配置迁移（两键 + 现有键平移）+ Lane 接口 + executor + 缓存表 + 合并逻辑（空载荷）。验收：配置文件带注释生成；`async=false` 内联路径行为生效；build + TEST。
- **B2 结构车道载荷**：刷新链路打通（待验#1）+ TdStructureOracle 撒点 + `/locate` 对照测试。验收：异步模式下 oracle 后台算（日志坐标），与 vanilla 同 seed 对齐。
- **B3 视觉方案落地**：max 裁决 §6 甲/乙后实施。

每步独立 build + TEST + commit，可回退。

## 9. 风险与兜底

| 风险 | 概率 | 兜底 |
|---|---|---|
| DH 无主动刷新 API | 中 | 兜底路径 3（数据不丢，视觉延迟下次拉取）；文档标注已知限制 |
| oracle 与 vanilla 撒点不齐 | 低 | 纯函数链已实证 [115]；不齐 = 参数读错，json 比对排查 |
| 多人 registry 缺失 | 中 | 退 vanilla json 离线表（模组结构集牺牲，vanilla 全保） |
| 车道线程与 generate 竞态 | 低 | 核心纯函数 + CHM；合并最多晚一个刷新周期，无害 |
## 10. B1 前置侦查补录 (2026-11-18 凌晨, javap/zip 实证)

### 待验 #1 结案: 路径 1 (API 主动刷新) 判死
- api 包类名广扫 refresh/invalidat/dirty/regen: 零命中; DhApi 公开面仅 events / worldGenOverrides / overrides 三注入字段 + 版本方法
- events 全 26 事件: 渲染×15 / 生命周期×4 / chunk×2 (DhApiChunkModifiedEvent 为监听方向) / wrapper×3 — 无数据刷新事件
- §4.4 路径 1 作废

### 待验 #4 结案: 刷新链收窄
- WorldGenerationQueue: public submitRetrievalTask(long sectionPos, byte dl) = 生成请求唯一入口 (waiting/inProgress 双 CHM 去重); public setCanRegenerate(boolean) = 再生成闸门 (疑对应 DH "regenerate existing LODs" 用户设置, 翻它不干净, 列备选); 私有链 tryQueueNewWorldGenRequestsAsync→…→startApiDataSourceGenerationEvent = 我们 generator 的调用方
- FullDataUpdatePropagatorV2: 私有 tryQueueWorldGenTask(GeneratedFullDataSourceProvider, long) / tryQueueRegeneration(DhBlockPos) = 块位级精确再生成, "Generated" 前缀疑绕文件缓存短路, 待 -c 验
- PregenManager.startPregen(level, center, radius): 全量圈状 pregen 语义, 不适合精确刷新, 弃
- 修正优先级: ①submitRetrievalTask + 短路验证 (若文件缓存命中短路不调 generator → 退 canRegenerate 或反射私有) ②兜底不变 (数据不丢, 等玩家移动/重进自然重拉)
- 摸 core 先例: WrapperFactory_forge (S1) 同为 core 内部类直调 + compileOnly 钉 3.3.3, 非破戒

### 新增待验 #6/#7 (2026-11-18 更新)
| # | 项 | 解法 | 里程碑 |
|---|---|---|---|
| 6 | 已有 LOD 的 section 再 submit 是否短路 (不调 generator) | 实测 (运行时状态依赖, 静态 javap 参考价值有限) | B2 |
| 7 | WorldGenerationQueue 实例获取路径 — **已结案**: 无需摸队列, 摸 provider 即得 (获取链终稿见下) | 十轮 javap/zip 反索, 全程 public 零反射 | B1 |

### 待验 #7 终稿: 获取链全闭环 (2026-11-18 凌晨, 十轮 javap/zip 反索)

无需摸 WorldGenerationQueue 本身 — queuePositionForRetrieval 内部委托它, 而 provider 有现成 getter。全程 public, 零反射:

    api LevelLoad 事件 EventParam.levelWrapper                (public final 字段)
        │  instanceof core ILevelWrapper 防御后强转
        │  皮实同体实锤: core ILevelWrapper 常量池直接引用 api 接口 (接口继承)
        ▼
    SharedApi.tryGetDhServerWorld()                          [public static]
        │  单机返 DhClientServerWorld / dedicated 返 DhServerWorld, 通吃
        ▼
    IDhServerWorld.getOrLoadServerLevel(ILevelWrapper)       [接口 default, 幂等]
        ▼
    IDhServerLevel  (运行时 = DhClientServerLevel, extends AbstractDhServerLevel)
        │  备选直达: level.serverside.fullDataFileHandler (ServerLevelModule, 字段 public final)
        ▼
    IDhServerLevel.getFullDataProvider()                     [IDhLevel 接口方法]
        │  运行时实例 = GeneratedFullDataSourceProvider
        ▼
    GeneratedFullDataSourceProvider:
        get(sectionPos) / getAsync(pos)             读 FullDataSourceV2
        updateDataSourceAsync(FullDataSourceV2)     写回 + 父级传播 (= IDhLevel.updateDataSourcesAsync 本尊, PassB 直写路径候选)
        queuePositionForRetrieval(Long)              请求重生成 (内部委托 WorldGenerationQueue.submitRetrievalTask,
                                                       自带 canQueueRetrievalNow 闸门 + queuedRetrievalFutureByPos 去重)

注记:
- 时序: api LevelLoad 事件与 core ServerApi.serverLevelLoadEvent 触发先后未验, 但 getOrLoadServerLevel 幂等, 顺序无关
- 同款姿势实证: ServerApi.serverLevelLoadEvent 字节码内部 = SharedApi.getAbstractDhWorld().getOrLoadLevel(wrapper), 与我们完全同款
- 编译姿势: 同 WrapperFactory_forge (S1) 先例, core 类直调 + compileOnly 钉 3.3.3, 非破戒