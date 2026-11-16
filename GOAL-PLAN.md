# Terrain Diffusion Horizons - GOAL-PLAN

## 一句话
接管 Distant Horizons 的 LOD 远距生成, 用 terrain-diffusion 管线批量推理直供数据点, 替代 vanilla chunk 生成+降采样, 数量级提速.

## 身份坐标
- 名字: Terrain Diffusion Horizons (= Terrain Diffusion 全名 + Distant Horizons 后半; 2026-11-14 max 定名, 前案 Distant Diffusion 因品牌可识别度不足被否决)
- modid: terraindiffusionhorizons
- 包名: com.lmaxrouter.terraindiffusionhorizons
- 目录: D:\Documents\mcmod\Terrain Diffusion Horizons
- 目标环境: MC 1.20.1 Forge + DH 3.3.3 (forge-fabric 版)
- 许可证: MPL-2.0 (全文 16726B, 自 S2a 737e510 起入库)
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

## DH API 素材 (3.3.3, 可行性报告 javap 实锤, S1 复验)
- 注册: DhApi.worldGenOverrides.registerWorldGeneratorOverride(IDhApiLevelWrapper, IDhApiWorldGenerator)
- 路线 A (采用): IDhApiWorldGenerator.generateLod + IDhApiFullDataSource.setApiDataPointColumn
- 路线 B (弃): AbstractDhApiChunkWorldGenerator chunk 级粒度, 对管线是浪费
- 数据点: DhApiTerrainDataPoint.create(detailLevel, blockLight, skyLight, bottomY, topY, blockStateWrapper, biomeWrapper)
- 能力声明: getSmallestDataDetailLevel / getLargestDataDetailLevel
- 生成模式: PRE_EXISTING_ONLY / SURFACE / FEATURES / INTERNAL_SERVER (本模组吃 SURFACE 档)

## 风险清单 (按杀伤力排序)
1. wrapper 获取 (最大不确定): 数据点要 IDhApiBlockStateWrapper / IDhApiBiomeWrapper 实例, API 未见公开工厂
   兜底 a: 反射 core 内部工厂 (钉死 3.3.3); 兜底 b: 拦截 DhApiBlockStateWrapperCreatedEvent 拿现成实例
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
4. DH 版本跟随策略 (3.3.3 之后有 stable 时升不升)

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
- WrapperFactory_forge.INSTANCE (public static final, common.wrappers 包 = core 内部非 api 面, DH 升版漂移风险, compileOnly 钉 3.3.3)
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
- 遗留: 主线等 max 重测 (案 II yBottom=4032 修复已提交+部署, 哈希见 git log, 验证清单见下方案 II); 副线: .gitignore (libs/, build-last.log) / .agent 三文件处置 / GitHub 建仓三连问, 均待 max 决策

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

## S2b 侦查案卷 (2026-11-15, javap + TD 源码全实证, 定案版)

### generateLod 全参数 (TestGenericWorldGenerator LVT 实名 + WorldGenerationQueue 调用点互证)
- 签名: generateLod(int chunkPosMinX, int chunkPosMinZ, int posX, int posZ, byte detailLevel,
  IDhApiFullDataSource dataSource, EDhApiDistantGeneratorMode mode, ExecutorService pool,
  Consumer&lt;IDhApiFullDataSource&gt; resultConsumer) → CompletableFuture&lt;Void&gt;
- detailLevel 是 API 级 = core 级 - 6 (调用点实证 getDetailLevel-6); API dl0 = 64 块/列
- chunkPosMin 单位疑 chunk(16块), posX/posZ 疑数据源 min 角块坐标 → 运行时首调日志定案
  (换算集中在 TdLodTranslator 单点, 联调时一改全改)
- 接口 default 体抛 UnsupportedOperationException, 必须 override; preGeneratorTaskStart/close 抽象必实现

### 列写入协议 (官方 generateInternal 黄金模板破译)
- setApiDataPointColumn(x, z, list): x/z 为数据源内局部索引 [0, getWidthInDataColumns())
- 列内 list 自底向上无缝平铺; 点七参 (detailLevel, blockLight, skyLight, bottomY, topY, block, biome)
- 官方双点: 实心 (0,0,15,0,h) + 空气 (0,0,15,h,顶); 顶用 levelWrapper.getMaxHeight() 不抄硬编码 256
- 全列填完后 resultConsumer.accept(dataSource) 恰一次; 异常时不调 consumer 直接 return
- wrapper 按字符串获取会抛 IOException, 官方整个包 try-catch

### 注册链 (TestWorldGenBindingEvent 字节码实证)
- DhApiEventRegister.on(DhApiLevelLoadEvent.class, listener) 挂事件
- onLevelLoad(param): levelWrapper = param.value.levelWrapper
- DhApi.worldGenOverrides.registerWorldGeneratorOverride(levelWrapper, generator) — 静态字段 DhApi.worldGenOverrides (非 Delayed 系)
- levelWrapper.getWrappedMcObject() → ServerLevel: 维度过滤 + worldSeed 捕获入口

### wrapper 入口 (修正 S1 的 WrapperFactory_forge 方案 → API 面优先)
- DhApi$Delayed.wrapperFactory 静态字段: getBiomeWrapper(String, level) /
  getDefaultBlockStateWrapper(String, level) / getAirBlockStateWrapper()
### 档位体系与 TDH 策略 (本轮修正)
- EDhApiDetailLevel 实值 (core 级): BLOCK=dl0/1块, CHUNK=dl4/16块, REGION=dl9/512块
- TestGen 声明 [0,12] API 级 = core [6,18] 全接 (测试姿态)
- TDH 策略: 只供 API dl0 (64 块/列), 粗档交 DH 原生 DOWN_SAMPLED (细→粗)
  - 理由: 粗档直查 TD = 2^(dl+6) 宽密集块级矩形, dl6 即 16384^2 样本 climate 5ch 直接 GB 级爆内存,
    TD 无 stride 稀疏查询接口; dl0 一个数据源 = 4096^2 块 ≈ 2 个 hydrology tile, ~200MB 短命可接受
  - 信息保真: TD 有效分辨率 30m/px (scale1), 64 块采样 ≈ 2:1 欠采样, 无实质损失
  - largest=0 声明下 DH 对粗档的 DOWN_SAMPLED 行为未实证, 运行时验证; 不行退 [0,12]+分块查询兜底

### TD 侧数据链 (fork 源码实证)
- getRiverTerrainData(i1, j1, i2, j2, withBiomes): i=Z, j=X, 块坐标 (fetchHeightmap javadoc 原文)
- RiverTerrainData: elevation float[](米!) / climate float[5ch] / biomeIndexes short[] /
  waterMask byte[] / waterSurface float[](米) / width(X向) / height(Z向); 一维行主序 idx = z*width + x
- 值为海拔米, 方块 y = HeightConverter.convertToMinecraftHeight(short):
  正值 m/resolution + 63; 负值 -sqrt(|m|+10) + sqrt(10) - 1 + 63; resolution = native30/scale
  (直接复用 TD 类, 不重写公式防漂移; float→short 转换精度 &lt;1m 无害)
- getSeed() vs ServerLevel.getSeed(): seed-0 兜底防护 (getInstance 窗口期静默错数据风险)
- WorldScaleManager.isTerrainDiffusionWorldActive(): TD 世界现成判定, 注册守卫用
- TerrainBiomeRegistry.instance().keyForIndex(short) → Settlement.key()/fallbackKey()/isRiver()/
  isFrozenRiver(); biome_catalog.json key = 标准 ID (minecraft:plains) — S4 消费
- 阻塞语义: submitToInferenceThread().get() 无超时, 在 DH 池线程阻塞等 GPU 推理, 正确形态

### S2b 实现范围 (修正: 水提前进 S2b; 真群系/表面规则 = S4)
- 水提前理由: withBiomes=false 时 waterMask/waterSurface 随查询白送 (buildRiverTerrainData 源码实证),
  一个 if 分支换烟雾测试直接可见河流; 案卷旧文"水=S4"作废
- getReturnType() 必须 override 返回 API_DATA_SOURCES (default 是 VANILLA_CHUNKS, 不改则 DH 分流到
  generateChunks, generateLod 永不被调 — 本轮 javap 实证 default 方法体)
- mode 过滤 (S2b 姿态): SURFACE/INTERNAL_SERVER 干活, FEATURES/PRE_EXISTING_ONLY 跳过 (联调日志定案)
- 首调日志 dump generateLod 全参数 (posX/posZ 单位 + 实际请求档位定案用)
- seed 不符 → 跳过本数据源 (不调 consumer), 日志限流

## S2b 进度
- [x] 侦查封箱 (本案卷)
- [x] TdLodTranslator (纯函数: DH 参数 → TD 查询矩形; 列 → 中心块采样)
- [x] TdDhWorldGenerator (generateLod = runAsync 壳 + generateInternal 真逻辑)
- [x] DhCompat 真注册 (LevelLoad 事件 + 守卫过滤 + worldGenOverrides)
- [x] TdConfig (JSON 三键)
- [x] 烟雾 → max 已实测 TEST 联跑 (2026-11-15): 开机雷根修 6ba4e32 (versionRange [2.3,9.4)); 实测暴露远距零生成 → 已定案 2026-11-15 05:12 首调日志: mode=FEATURES 被守卫 2 静默拒 (DH 队列 mode 直传 config chunkGeneratorMode, 默认 FEATURES, 合法值无 SURFACE → 守卫 SURFACE 分支为死分支, 黑名单反拦默认流量); 修法: 守卫 2 反转白名单只拒 PRE_EXISTING_ONLY; 已修+部署 TEST, 待重测; build 绿链: fc5aa05 + 1770612 + 6ba4e32 (3e5f985 假绿案见 memory 080)


## 2026-11-15 07:10 · 数据正确性案根因定案 (S2b 重测轮, max 睡眠期间完成)

**重测结果**: LOD 显示 = 全链路通, 零生成案正式结案。新症状: 地形重复 / 网格不对齐 / 冲天石柱 / 精细度坏 / 部分块闪现闪消。

**根因 (双重 64 倍, 三路独立实证)**:
1. pos 单位错: generateLod 的 posX/posZ 单位 = 数据源边长(块) = 2^(apiDl+6), dl0 即 64 块, 不是块坐标。
   证据: (a) WorldGenerationQueue 调用点字节码: DhSectionPos.getX/getZ, 核心dl = apiDl+6, 段坐标 1 单位 = 2^核心dl 块; (b) DhWorldGenerator.generateLod 开头 powerOfTwo(apiDl+6) = 数据源边长; (c) 双会话首调 chunkMin/pos 比值恒 4 (chunk 16 块 x 4 = 64)。
2. span 错: FullDataSourceV2 恒 64x64 数据列, dl0 列宽 1 块, 总边长 64 块。旧代码 span = columnWidthBlocks x widthInColumns = 4096, 大 64 倍。

**症状映射**: 约 16 个数据源全查原点附近同一 4096x4096 区域 (pos 未乘 64, 值仅 20 上下), 写入循环只取返回数组左上 64x64 角落 -> 每块 LOD 贴同一片地 = 重复+错位; 错位数据下采样 = 精细度坏。冲天石柱当时推测"模型边缘垃圾高度", 已改判 (见下方案 II: 实为 yBottom 4032 回绕, 元案独立 bug)。放大消失/闪现闪消未归因, 案 II 修后复查。

**修法 (已应用 d759a33, 已构建部署, 重测验证生效 2026-11-15 20:11 会话: 首调乘 64 精确落 chunkMin, 每 tile 自采区域, 生成提速)**: TdLodTranslator.dataSourceBlockBounds:

    int size = columnWidthBlocks(apiDetailLevel); // 数据源边长(块), dl0 = 64
    int bx = posX * size;
    int bz = posZ * size;
    return new int[]{bz, bx, bz + size, bx + size};

widthInColumns 参数保留 (dl>0 时列宽 2^dl, 未来扩展); 建议同时在生成器加 data.width 与 cols 一致性校验。

**明早验证清单**: (1) 修后重测: 重复/错位应消失 (2) TD 64x64 小查询的 data.width 契约 (应 = 64) (3) i/j 轴序 (Z,X) 视觉实证 (4) 残留症状单独再评 (5) 单查询采样 16.7M -> 4K, DH 生成速率应显著回升。

**环境事实存档**: 06:40 会话 mode=FEATURES (守卫 2 放行路径实证), ds=FullDataSourceV2, 全程零 warn; 世界 105 sqlite 5.5MB; latest.log 06:54:59; java 已退出。

---

### 数据正确性案 II: yBottom=4032 冲天石柱 (2026-11-15 夜, memory 092)

**重测 (20:11 会话, max 开新世界 seed=3306015219772967962, 世界 105 基线保全)**: 坐标修复现场验证生效 — 首调 pos=(21,-26)x64 精确落 chunkMin=(84,-104), 每 tile 自采区域, 新世界 DH 库 28MB (105 全生涯 5.5MB), max 体感"更快"。但症状主体仍在: 冲天石柱遍地且更快。

**根因 (实证)**: 生成器实体段 bottom 喂 levelWrapper.getMinHeight() = -64 (主世界); DH 存储层 y 为 12 位无符号, -64 回绕 = 4032 (-64 & 0xFFF; 孤儿值 4033 = -63 & 0xFFF 同律); bottom(4032) > top(64~82 原样入库) 几何倒挂 → 主渲染路径画 y70~4032 冲天石柱 (柱顶随地形起伏, 柱底钉 4032), 邻接数据加载路径抛 "Negative y size for the renderDataPoint" (14.6 万条/13min ≈ 10MB/min 刷屏, Y-:4032 占 99.996%)。旧会话同错 10.4 万条 = 元案独立 bug, 与坐标修复无关; 坐标修复使坏 bottom 从集中一片变遍地 = "更快"闭环。官方模板 (0,h)+(h,顶) 的 0 是承重契约 (存储 y 非负), 偏离踩雷。

**修法 (已应用 bff0ce4; 2026-11-15 22:14 部署, javap 验尸真绿)**: TdDhWorldGenerator.generateInternal: 新增 dataBottom = max(minY, 0) 作实体段底; solidTop/waterTop 钳位下界 minY → dataBottom; 顶保持绝对坐标 (实证正确)。钳位保证 bottom ≤ top 恒成立。y<0 区间不写段 (地下不可见, 与官方模板同构)。

**部署与验尸证据 (091/090 证据原则)**: 四件套配方构建 (TD_MIRROR=1 + GRADLE_USER_HOME=F:\MOVED\.gradle + JAVA_HOME=zulu-17 + TEMP/TMP=D:\temp) 20s BUILD SUCCESSFUL, 13KB jar 部署 TEST mods (mtime 2026-11-15 22:14:22); javap 直验部署物 (mods 里游戏实际加载那份, 非 build/libs): getMinHeight:()I → iconst_0 → invokestatic Math.max:(II)I → istore dataBottom, 反汇编存 D:\temp\tdh-javap.txt, 排除假绿 (080 前科)。构建链绕路两轮根因 = exec 环境缺四件套 + 通道假读数误导 (DNS 污染本身是真坑但非本案死因), 教训入 memory 093。

**案 II 验证清单 (重测看点)**: (1) Negative y size 刷屏应归零 (2) 石柱应消失 (3) 地形高度正确 — 若整体下沉 64 = DH 语义实为相对 y → 顶转相对 (分支预案, memory 092) (4) 重复/错位/tile 复制粘贴感复查 (石柱 bug 曾主导视觉, 修后才能判) (5) 放大消失/闪现闪消若残留 = 独立案 (6) DH 生成速率维持回升。

**案 II 重测终局 (2026-11-15 深夜, max 实测通过, 案销)**: 视觉问题全清 — Negative y size 刷屏归零, 石柱消失, 复制粘贴感/错位无残留; 生成速率 500 chunks/sec, 87% 时间满速, ETA 30min (原版 RD5 + DH RD512 全图完成)。看点 (3) 分支预案命中: LOD 整体矮 ~64 → DH 存储层 y 语义 = 距世界底偏移 (与 bottom=0 契约自洽), 待小修: 顶转相对 (top - minY)。新独立案: 地表方块全石头 (裸 DH 无此问题; 疑 TD 管线只输出高度图、表面方块类型未喂, 根源待查证后定论, 勿提前下结论)。
### 案 III: LOD 整体矮 64 (案 II 看点(3) 分支预案命中, 2026-11-16 00:18 修复, memory 095)

**根因**: DH 存储层 y 语义 = 距世界底偏移 (相对 y), 与案 II 的 bottom=0 契约互证 — bottom=0 被解释为世界底而非绝对 y=0。案 II 修复时 top 保持绝对坐标 → 渲染位置 = 世界底(绝对-64) + 绝对y → 整体矮 |minY| = 64。

**修法 (08606e2, 已部署 TEST)**: generateInternal 内全部 y 统一转相对 (yRel = yAbs - minY):
- dataBottom = 0 (相对语义: 0 即世界底, 主世界对应绝对 -64)
- maxY = getMaxHeight() - minY (主世界 384)
- solidTop / waterTop 各 -minY (clampY 钳位域随之相对化)

**证据链 (090/091 原则)**: diff 三 hunk 逐字验证 → 构建 35s 绿 (schtasks tdhb1115) → 产物 13123B → 部署 MD5 双端一致 (F531D151AA2B2313570C2CAECFCD421A) → javap 直验部署物: 两处 convertToMinecraftHeight 后均见 iconst_1 iadd + iload minY + isub + clampY (偏移 425-440 / 471-486), 旧 jar 同检索零匹配 → push fa60d99..08606e2 → 回滚锚 D:\temp\tdh-010-s2b.bak。

**重测清单 (待 max)**: (1) 必须新世界或删旧世界 data\DistantHorizons.sqlite — 旧库缓存矮64 LOD, DH 不重刷已生成数据 (2) RD5 真实地形与 LOD 衔接地平线高度差应归零, 水面高度同查 (3) 生成速率应不变 (y 改动不碰 xz/数据量) (4) 若仍不对: 嫌疑 convertToMinecraftHeight 本身或 top+1 边界语义, 拿具体坐标点高度差逐一对。

### 案② 地表全石 定性与选项 (2026-11-16, 呈 max 待答)

**定性: S2b 设计债, 非 bug**。TdConfig surfaceBlock 默认 minecraft:stone (S2b 占位, 注释明写 S4 换真数据); 本模组走高度图翻译, 方块类型天然缺席, 裸 DH 逐 chunk 扫真实方块故无此问题。TD 查询面 (getRiverTerrainData) 侧 waterMask/waterSurface 随 withBiomes=false 白送, 但方块类型不在输出里。

**选项 (2026-11-16 夜呈报, 未答不动)**: 0 实例 config 改 grass_block (零代码, 全图均匀草绿) / a 高度启发式假数据 (水下沙-低地草-高山石-山顶雪, ~25行, 视觉可信, 算 S2c) / b TD 真群系 (withBiomes=true + keyForIndex 映射, S4 正主, 查询负载 ~3.5 倍) / c 保持石头等 S4。

## 2026-11-16 02:30 · 案①+案②α 双修落地 (commit 923275a, 部署+验尸全绿)

**案① (LOD 顶面高 1 格) 定案与修法**:
- 根因: convertToMinecraftHeight 语义 = "第一个空气 y" — density 函数数学 / FeaturePlacer 使用姿势 / mixin 注释 /
  DH 半开区间端点 / 实测高 1 格, 五证合一; 旧码 +1 属双重补偿 → LOD 实体段整体高 1 格。
- 修法: solidTop / waterTop 两处删 +1。
- 字节码验尸 (javap -p -c 直验部署物): convert 调用点 (偏移 450/506) 后直接 iload+isub, 无 iconst_1 iadd;
  局部槽 12→13 (withBiomes 新局部) 佐证验的是新码。

**案②α (群系驱动表面方块/色调)**:
- TdSurfaceBlockMap (新): 65 群系全表 (主导表面/水下顶部/冰盖三元组), vanilla surface rule 的
  "LOD 输入可达分支" 剪枝复刻 — 数据 (biomeIndexes) 与规则 (群系->主导方块) 均与 chunk 生成同源;
  NETHER/END/CAVE 16 条纯防御 fallback。
- TdConfig: +useBiomeSurface (withBiomes 门控; 内存 ~520MB vs ~150MB TEST 实测; false 一键退纯几何),
  +surfaceBlockOverrides 稀疏覆盖 (仅地表字段); surfaceBlock/fillerBiome 降为 fallback。
- TdDhWorldGenerator: withBiomes 门控查询; biomeIndexes → keyForIndex → ColumnStyle (ConcurrentHashMap 缓存,
  DH 池多线程, 良性竞争); 水列顶部用群系水下方块 (河床/海床); 冻河/冻洋水段顶换冰; 群系 wrapper 进
  data point (DH 远距渲染吃 biome tint, 色调随群系)。
- API 风险预杀: 编译期 td_jar 与 TEST 部署物 MD5 双端一致 (5314B5B7...), javap 部署物 keyForIndex/instance
  全 public → 编译面=运行面, NoSuchMethodError 不可能。

**部署与验尸证据链 (090/091 原则)**:
- build 37s 真绿 (EXIT=0 + 大小写敏感 BUILD SUCCESSFUL + mtime, 080 假绿前科不复犯)。
- jar 13123B → 18090B; MD5 SRC=DST=70FDE76E5E55B93CE41CC79655308900; 回滚锚 D:\temp\tdh-pre-alpha.bak
  (F531D151... = 08606e2 版原部署物, 血统可溯)。
- zip 字节级直搜: 表类 grass_block/orange_terracotta/minecraft:ice 全 True;
  Generator minecraft:ice/keyForIndex/surfaceBlockOverrides 全 True。

**重测指引 (max)**:
- 必须新世界, 或删 saves\<世界>\data\DistantHorizons.sqlite (LOD 缓存不刷新, 旧数据掩盖新生成)。
- 验收: 案① LOD 顶面与近处真实地形齐平; 案② 草原绿/沙漠黄/雪原白/恶地橙 + 远景色调随群系 + 冻河冰面。
- 观察项: TerrainBiomeRegistry 懒 init 双载 (评估低风险, 首报重测日志); 内存峰值 (true 档 ~520MB 量级)。

**本轮事故与遗留**:
- 指令格式事故三起 (12 条 replace 双块未闭合全灭 / 部署块孤立标签毒化 / memory+remember 内容嵌标签截断),
  全部零副作用恢复, 教训入 memory 099/100。
- .gitignore 工作区损毁 (注释丢失/条目丢失/粘连; 非本会话所改, 疑框架手笔), 未 commit 未恢复, 待 max 定夺;
  副作用: libs/ 与 build-last.log 脱离忽略名单 (status 挂未跟踪)。
## 2026-11-16 凌晨 · 案②α 表面审计全案 (侦查关门, 8 项待 max 拍板, 未动一行)

### W 水判定双嫌疑 (必修)
- 嫌疑一 (边缘水): waterMask 值 1-7 是抗锯齿格, TD chunk 侧对 <8 的水格跳过不落地,
  本模组现码 (waterMask[idx] != 0) 照收 → LOD 河道边缘比 chunk 宽一圈
- 嫌疑二 (幽灵水): waterSurf 为 NaN 时 (short)NaN = 0 → convertToMinecraftHeight(0) = 63,
  地形顶 < 63 的列凭空多出一段水到海平面, 高发河道边缘带
- 修法 (一行): fluvial 判定改 (waterMask[idx] & 0xFF) >= 8 && Float.isFinite(waterSurf[idx])

### O 干海床 (定案, 建议必修)
- 根因: BiomeClassifier 源码 isOcean = elevation < 0f + 注释明文海洋 cell 的 elevation
  是负米海床深度 (deep_* 门限 < -250) → Generator 负值走压缩曲线 (-250m → y49),
  上叠空气 → LOD 海洋 = 渲染 gravel/sand 表面的巨型干坑; Generator 全文 318 行零海平面处理
- y63 海平面三路实锤: HeightConverter / ScaledAltitude / ReefSpires / SeaStack 四常量全 63;
  FarLookupHeightMixin 撞水返回 max(surface, 63) 行为证明; 0m ↔ y63 恒等锚点 (任意 scale 不变,
  负海拔压缩曲线最小落 y62 → 海洋列水段恒 >= 1 格, 无零厚度边角)
- 修法: oceanCol = elev[idx] < 0f → 水段 [海床, seaTop]; seaTop = convert(0) - minY 循环外常量;
  与 fluvial 互斥 (applyRiverBiomesFromWindow 对 ocean cell continue), 防御取 coverTop 最大;
  frozen_ocean 冰盖走现有 iceCover 免费生效
- 遗留矛盾: max 实测未报干海床 (测试区无海 or 未到海边, 信息在 max 手里)

### A1 river 表行 (必修)
- river / frozen_river 表行 sand → grass_block (vanilla 河群系河岸是草, 河床归 C 或水下字段)

### A2 注释纠错 (必修)
- 表 L29: underwater 字段 "河床/海床" 注释失真 (当前仅 fluvial 水列生效; O 批后海床才真走此字段)
- 表 L55: old_growth podzol 注释错 (podzol 属 feature 层表述需修)
- 表 L110: 河流段注释错
- Generator 侧 "1=水下" 注释同步修

### B1/B2/B3 (拍板项)
- B1: snowy_plains / snowy_taiga 保 snow_block (建议保)
- B2: snowy_beach → sand (倾向改)
- B3: old_growth → podzol (倾向改)

### C 河床分档 (建议做, ~5 行)
- fluvial 水列底段: strength < 72 → sand (弱水经验配比 沙 62.5% 砾 37.5%),
  >= 72 → gravel (强水 砾 85.2% clay 14.8%); strength = (waterMask[idx] & 0xFF) 与 W 共用;
  需 resolveWrappers 加 gravel/sand 两 wrapper (per-column, 不进 styleCache)

### 三耦合 (拍板联动)
- 表 L29 underwater 注释 ↔ O: O 批 = 注释天然对, 不批 = 必改
- river.underwater 字段 ↔ C: C 批 = 死字段 (注释即可), 不批 = 应改 gravel
- 对称性: O 杀 ocean.surface (海洋列全水列), C 杀 river.underwater, 落地后表语义干净:
  surface 管旱地 / underwater 管海床 / fluvial 河床归强度; 底段优先级 fluvial > ocean > surface

### 落点行号 (已锁定, 拍板后纯机械执行)
- 表 TdSurfaceBlockMap (152 行): L29 / L55-57 / L72-77 / L78 / L110-112
- Generator TdDhWorldGenerator (318 行): L189-235 区水判定流 + resolveWrappers

### 8 项决策面板 (待 max 回字母组合)
W 必修 / A1 必修 / A2 必修 / B1 建议保 / B2 倾向改 / B3 倾向改 / C 建议做 / O 建议必修
回复后执行: 改 → build 三判据 → commit 前验 diff → 部署 MD5 双验 → javap → push

### 其他遗留
- chunk 强制薄流层 LOD 缺失 = 已知损失, 不追
- .gitignore 工作区损毁 (前轮遗留, 待 max 定夺)
## 2026-11-16 早晨 · 案②α 审计执行落地 (8 项全批, 已部署 TEST 待重测)

### 执行记录
- max 拍板: W/O/A1/A2/C 执行 + B3 改 podzol + B1/B2 保 snow_block (B2 顶沙白雪归 snow precip 群系统一近似)
- 表: river/frozen_river surface 改 grass_block (A1); old_growth 系改 podzol (B3); underwater 注释改海床语义 (A2); 雪盖/河流段注释纠错 (A2)
- Generator: waterMask 0-255 强度语义 (>=8 有效, <8 边缘抗锯齿格) + Float.isFinite 双杀 (W); oceanCol = elev<0 填水段 [海床, seaTop] (O); 河床强度分档 弱水<72 沙62.5%/砾37.5%, 强水 砾85.2%/黏土14.8%, 空间散列键=世界块坐标 跨数据源稳定 (C); sand/gravel/clay 三 wrapper 懒取, per-column 不进 styleCache; close 三置空; 残留旧注释清理 (与新逻辑矛盾的两行)
- 构建: BUILD SUCCESSFUL in 47s, EXIT 0, jar 18575B (旧 18090B, +485B)
- commit 4d78783 (2 files, +52/-16)
- 部署: TEST mods 同名覆盖; MD5 双端一致 3BA214EF8BE07BB9A59B5E1BB822EFD9; 回滚锚 D:\temp\tdh-pre-audit-923275a.bak (旧版 18090B)
- javap 部署物直验: iand / Float.isFinite / fcmpg / imul x2 / sand+gravel+clay 字符串 / podzol x2 全中

### 重测清单 (TEST 实例)
- 前置: 新世界或删 saves 下世界 data 内 DistantHorizons.sqlite (旧 LOD 不带新表面)
- 看点: 河道边缘不再宽一圈 / 无幽灵水 / 近海干海床消失变海面 / 河床沙砾黏土混合 / 河岸草色 / old_growth 系暗棕 podzol

### 遗留
- .gitignore 已由 max 修复 (2026-11-17, 15 行标准列表)
- DH 版本议题销案: 实体从来都是 3.3.3, "2.3.2-b" 是 mods 内 jar 文件名误名 (memory 080/087)
- chunk 强制薄流层 LOD 缺失 = 已知损失, 不追
- mods 内 DH jar 已正名 DistantHorizons-3.3.3-1.20.1-forge-fabric.jar (去中文前缀, max 授权, 2026-11-17)
- .agent 三文件定夺: 入库, .gitignore 不加 .agent/ 条目 (max, 2026-11-17)
- 源码 L23 注释已更新: modid 实测验证 + 2.3.2-b 勘误注记
- README 双语正式版: max 过目通过, 两处手改用词已同步英文
