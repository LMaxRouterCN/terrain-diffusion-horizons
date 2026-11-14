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
- [ ] S1 侦查: DH jar javap 复验 + TD WorldPipeline 签名实证 (Accessor mixin 可行性判决)
- [ ] S2 骨架: Forge mod 工程 + DhCompat 空注册 + 烟雾启动 (网络配方: TD_MIRROR=1 + td-mirror.init.gradle)
- [ ] S3 wrapper 攻坚 (预留 1-2 天, 白天 max 盯场时打)
- [ ] S4 列填充 + 表面规则 (草/石/水/雪)
- [ ] S5 调优 + max 实测

## 待 max 决策 (醒后过目)
1. 名字撞名检查 (curseforge/modrinth, 我无网络把握)
2. 联机范围 (报告建议二期)
3. 发布渠道 + push 时机 (本地 commit 已留, push 全部等你)
4. DH 版本跟随策略 (2.3.2-b 之后有 stable 时升不升)
