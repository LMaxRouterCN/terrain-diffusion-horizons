# Terrain Diffusion Horizons - GOAL-PLAN

## 一句话
接管 Distant Horizons 的 LOD 远距生成, 用 terrain-diffusion 管线批量推理直供数据点, 替代 vanilla chunk 生成+降采样, 数量级提速.

## 身份坐标
- 名字: Terrain Diffusion Horizons (= Terrain Diffusion 全名 + Distant Horizons 后半; 2026-11-14 max 定名, 前案 Distant Diffusion 因品牌可识别度不足被否决)
- modid: terraindiffusionhorizons
- 包名: com.lmaxrouter.terraindiffusionhorizons
- 目录: D:\Documents\mcmod\Terrain Diffusion Horizons
- 目标环境: MC 1.20.1 Forge + DH 2.3.2-b (forge-fabric 版)
- 许可证: MPL-2.0 (正式文本待补)
- 版本: 0.1.0 起, 三位式

## 依赖拓扑 (2026-11-14 max 拍板)
- 独立模组, 与 TD fork 仓库解耦
- compileOnly 软依赖 DH, 运行时在场检测注册 (先例: TD fork TerraBlenderSurfaceCompat)
- 用 Accessor mixin 钩 TD 内部类 WorldPipeline (com.github.xandergos 包)
- 目标兼容面: xandergos 原版 TD / 上游 fork / LMaxs fork 三方同签名 (未实证, S1 javap 验证)
- TD 侧零改动零 API 开口; 若 mixin 签名路线不可行, 升级方案 = TD fork 暴露 API (最后手段, 需 max 批准)

## 架构 (正交三件)
- core: TdLodTranslator - 纯函数, LOD 列坐标 -> 管线像素坐标 -> 数据点列, 零状态零副作用
- shell: TdDhGenerator - 实现 IDhApiWorldGenerator, 调度全交 DH 回调, 零轮询
- 注册外壳: DhCompat - DH 在场检测 + 注册, 不在场 = 功能静默关
- 配置: 播种式落盘 (先例: TD carver_altitudes.json)

## 尺度换算 (可行性报告实锤)
- 模型 native 30 m/px (world_pipeline_config.json); scale2 -> decoder 输出 60 m/px
- DH LOD detail level 6 = 64 blocks/列; 60 vs 64 每列 6.7% 偏移, 双线性插值吸收
- 更近档位 (16/32) 由 DH 自带 DOWN_SAMPLED 步骤处理, 本模组只供最粗档
- 一个 256px decoder tile 约 15km x 15km, 填数万 LOD 列
- 推理在 GPU (CMP 30HX, DirectML) 与游戏线程解耦

## DH API 素材 (2.3.2-b, 可行性报告 javap 实锤, S1 复验)
- 注册: DhApi.worldGenOverrides.registerWorldGeneratorOverride(IDhApiLevelWrapper, IDhApiWorldGenerator)
- 路线 A (采用): IDhApiWorldGenerator.generateLod + IDhApiFullDataSource.setApiDataPointColumn
- 路线 B (弃): AbstractDhApiChunkWorldGenerator chunk 级粒度, 对管线是浪费
- 数据点: DhApiTerrainDataPoint.create(detailLevel, blockLight, skyLight, bottomY, topY, blockStateWrapper, biomeWrapper)
- 能力声明: getSmallestDataDetailLevel / getLargestDataDetailLevel
- 生成模式: PRE_EXISTING_ONLY / SURFACE / FEATURES / INTERNAL_SERVER (本模组吃 SURFACE 档)

## 风险清单 (按杀伤力排序)
1. wrapper 获取 (最大不确定): 数据点要 IDhApiBlockStateWrapper / IDhApiBiomeWrapper 实例, API 未见公开工厂
   兜底 a: 反射 core 内部工厂 (钉死 2.3.2-b); 兜底 b: 拦截 DhApiBlockStateWrapperCreatedEvent 拿现成实例
2. DH beta API 漂移: compileOnly + 运行时检测双保险
3. 表面方块规则: LOD 列表面 (草/石/水/雪) 从 climate + height 推导, 规则引擎活
4. 联机拓扑: DH 远距生成在客户端, 模型 2GB 在服务端 -> 客户端带模型或走网络协议 (大工程, 二期)

## 阶段计划
- [x] S0 仓库 init + 本文档 (2026-11-14 夜, max 睡前授权: 取名 + 自主推进)
- [x] S1 侦查: 完成, 案卷见下 (mixin 整个不需要, 全公开面直调; wrapper 用 WrapperFactory_forge.INSTANCE)
- [ ] S2 骨架: Forge mod 工程 + DhCompat 空注册 + 烟雾启动 (网络配方: TD_MIRROR=1 + td-mirror.init.gradle)
- [ ] S3 wrapper 攻坚 (预留 1-2 天, 白天 max 盯场时打)
- [ ] S4 列填充 + 表面规则 (草/石/水/雪)
- [ ] S5 调优 + max 实测

## 待 max 决策 (醒后过目)
1. 名字撞名检查 (curseforge/modrinth, 我无网络把握)
2. 联机范围 (报告建议二期)
3. 发布渠道 + push 时机 (本地 commit 已留, push 全部等你)
4. DH 版本跟随策略 (2.3.2-b 之后有 stable 时升不升)

## S1 侦查案卷 (2026-11-14, javap/read 全实证)

### 数据获取链 (定案)
- 主入口: LocalTerrainProvider.getRiverTerrainData(i1, j1, i2, j2, withBiomes) - public static
  - 返回 RiverTerrainData: float[] elevation + float[] climate(5ch) + short[] biomeIndexes + byte[] waterMask + float[] waterSurface + width/height
  - 内部走 HydrologyProvider 共享缓存 (与 chunk 生成同源, 缓存命中白捡)
- 备选: fetchHeightmap (实例方法, 独立 region cache) / WorldPipeline.get (原始输出, 无水文学, 弃)

### 坐标语义 (定案)
- i/j 是块坐标, i=Z, j=X (fetchHeightmap javadoc 实证); scale 内部消化, 调用方只管块
- DH 侧坐标 x 在前 -> 翻译层换轴: TD.i=Z 对 DH.z, TD.j=X 对 DH.x

### 线程模型 (定案)
- 静态门面全走 INFERENCE_EXECUTOR 单线程 + SEED_LOCK 读锁, .get() 无超时
- DH executor 线程阻塞等 TD 推理线程: 正确形态, GPU 推理单流串行, seed 一致性免操心
- seed 变更走写锁排他 (changeSeedFromExplorer), 运行中换种子也能跟上

### seed-0 兜底陷阱 (风险, 已定防护)
- getInstance(): INSTANCE null 时不抛, awaitLoad 后以 seed=0 兜底 new -> 静默错误数据
- 防护: generateLod 入口对比 LocalTerrainProvider.getSeed() vs 注册时捕获的 world seed, 不符跳过本列
- init 后到会走 setSeed 分支自愈, 防护只覆盖窗口期

### Wrapper 工厂 (定案)
- WrapperFactory_forge.INSTANCE (public static final, common.wrappers 包 = core 内部非 api 面, DH 升版漂移风险, compileOnly 钉 2.3.2-b)
- getBiomeWrapper(String, level) / getDefaultBlockStateWrapper(String, level) / getAirBlockStateWrapper() / getWaterBlockStateWrapper(level)
- 字符串入口 + biomeIndexes short -> catalog key -> wrapper, biome 规则引擎 (原风险 #3) 作废

### S2 切分 (新决策)
- S2a: mod 壳 + DH compileOnly (发行 jar 现成) + DhCompat 空注册 + 烟雾启动 -> 不依赖 TD 构建, 可独立验证
- S2b: TdBridge + 翻译层 + 真实 generateLod -> 硬依赖 TD 构建产物 jar (compileOnly), 与 D7/D8 验收构建合并
- 原风险清单 #1 (wrapper 获取) 正式销案, #3 (表面规则) 大幅缩水 (水=waterMask+waterSurface, 群系=biomeIndexes, 剩: 雪线/表面方块选择的薄层规则)

### 剩余待验证 (写码时运行时实测)
- [ ] setApiDataPointColumn(int, int, List) 参数序 (javap 无参数名, 疑 x,z; 运行时打印验证)
- [ ] WorldScaleManager 非 TD 世界行为
- [ ] DH generateLod detailLevel -> 块区域换算
- [ ] generateLod 跳过列时 DH 的行为 (保留旧数据 or 空)

## S2a 案卷 (2026-11-14 夜, 文件落盘)
- 结构: 单项目扁平 (仅 Forge 1.20.1; 无 multiversion/无 fabric/无 mixin/无 AT/无 ONNX)
- 构建链: ModDevGradle legacyforge 2.0.77 + parchment 2023.09.03 + Java 17, 与 TD fork 刻意同链同版本
  -> 插件/依赖/parchment 全命中 F:\MOVED\.gradle 缓存, TD_MIRROR 四件套直接复用, 零新增网络需求
- compileOnly 双 jar: dh_jar + td_jar 路径走 gradle.properties (正斜杠), S2a 挂上不消费, S2b 起吃
- reobfJar: jar finalizedBy, 1.20.1 Forge 生产 SRG 命名 (TD fork 同姿势)
- DH 挂钩姿势: mods.toml 软依赖 + 代码侧 ModList.isLoaded 双宿主检测 (缺席静默闲置)
- 烟雾启动 = 裸 runClient (不带 DH/TD): 验证壳 + mods.toml 解析 + 无宿主退化路径
- 待验证 (S2b 运行时): DH modid 是否 "distanthorizons"; TD fml id "terrain_diffusion_mc" 已从源实证
- 遗留: LICENSE 已定稿 (MPL-2.0 全文, mozilla.org 正源); 首构建时间由 max 醒后拍板

## S2b 前置 API 签名案卷 (2026-11-14 夜, javap 实证)
- generateLod 全签名: generateLod(int x, int z, int minX, int minZ, byte detailLevel,
    IDhApiFullDataSource, EDhApiDistantGeneratorMode, ExecutorService, Consumer<IDhApiFullDataSource>)
    返回 CompletableFuture<Void> (参数名是猜测, 排序实证; [待验证-S2b 运行时])
- DhApiTerrainDataPoint.create(detailLevel, blockLight, skyLight, bottomY, topY, blockStateWrapper, biomeWrapper)
    静态工厂唯一入口 (构造器私有), 7 字段全公开 final
- DhApi.worldGenOverrides: IDhApiWorldGeneratorOverrideRegister 实例字段 (S1 已查注册器: DH.isClientOnly() 检查 + currentLevelWrapper != null 后 register)
- IDhApiWorldGenerator 抽象方法仅 2: preGeneratorTaskStart() + close(); generateLod 为 default, override 才有产出
- IDhApiFullDataSource 写面 (setApiDataPointColumn) 待 S2b 开工首读
- 彩蛋: DhApi.HEY_YOU_YOURE_FINALLY_AWAKE 字符串常量, 验证回调可用
