# B3/L3 边界裁决 — 呈 max

2026-11-20 05:00 自动模式产出 (授权范围 = 不需裁决的准备工作)。本文档只摆选项和事实, 不替你拍板。

## 0. 裁决问题

[长期记忆: 138] §6 裁决 (甲案) 取消了 B3 原主题 (结构视觉), 遗留两个问题:

1. 结构链的 biome 过滤 (下称 BF 滤, memory 138 记作 "BF loads") 是留在 B3 单独做, 还是并入 L3 (C 批) 一起做
2. B 批在哪个 commit 收口

## 1. 事实底座 (证据全在仓库/记忆, 无推断项)

vanilla 结构链三段 + 地物一段:

| 段 | 内容 | 归属 | 状态 |
|---|---|---|---|
| 撒点 spread | RandomSpread 纯函数, 15 集合 oracle | B2 已交付 | 收口于 1280bb5, locate 逐位命中实证 [长期记忆: 137] |
| biome 过滤 BF 滤 | 撒点命中处回调 biomePredicate (结构 biomes 检查 + targetBiomes) | 待裁决 | B2 未做 |
| 组装 assemble | Structure.generate → StructureStart → piece 盒 × 列折叠 → LOD 段 | L3 (C 批) | 未开工 |
| 地物 features | applyBiomeDecoration 逐株 | L4 (D 批) | 未开工, 不涉本裁决 |

关键事实:

- B2 oracle 输出 = 撒点纯函数结果, 是 locate 的超集; 差集 = BF 滤淘汰项 (GOAL-PLAN B2 验收语义)。包含性对照: village 逐位命中 (世界"新的世界(14)" seed 5303...7359); snowy 属圈外 NOT TESTED 非失败。
- BF 滤的输入现成: 撒点命中 (B2 缓存表) + TD biomeIndexes (LTP 现供, [长期记忆: 103] 实证 per-block 索引)。
- BF 滤的近似性 (feasibility 报告 §5.6): vanilla 查 worldgen 3D noise biome, TD 侧 BiomeSource.getNoiseBiome = per-block 索引 tile biomeIndexes, 2D 地表 Y 无关, fallback=plains ([长期记忆: 103]; 勘误: 初版 "粗粒度" 说法系 feasibility 报告旧措辞, 103 已证伪)。折损面 = Y 扁平 + tile 外 fallback=plains。无论哪天做, 这个折损都在, 时间不改善它。

## 2. 选项

- 甲: BF 滤并入 L3, B3 空缺 = B 批直接收口 (推荐)
- 乙: BF 滤做 B3 (数据精修层), 组装仍留 L3

## 3. 对比

| 维度 | 甲 | 乙 |
|---|---|---|
| BF 滤落点 | C 批开工首日 | 立即 |
| 滤的输出谁消费 | assemble 入场即消费 (撒点+过滤是 Structure.generate 的前置门卫) | 无人消费, 缓存表冷存到 L3 开工 |
| 验证通道 | locate 对照 + 剪影可见层免费叠加 (滤错 = 结构上雪山草原, 肉眼可辨) | 仅 locate 对照 |
| 可见进度 | B 批收口 → C 批剪影, 一步见效 | 连续第二个零视觉里程碑 |
| 工程量 | BF 滤代码在 L3 内首日完成 | BF 滤单独成批, 里程碑撑不起来 |

推荐甲的理由: ① BF 滤的存在意义是当 assemble 的入场守卫, 单独做没有任何消费者; ② 你的裁决史一贯以视觉价值为天平 (§6 否乙案的逻辑), 甲不中断"每个里程碑有可感知交付"的节奏; ③ 折损层 (biomeIndexes 近似) 与时序无关, 早做不增益。

乙的适用场景 (唯一): 你想尽早锁定一个"验证级 oracle"全集 (撒点+过滤完整结构位置库), 将来 L4 biomeFilter / 模组结构兼容 / 第三方消费方都要用 — TDH 目前没有这样的消费方。

最坏情况检查 (家规):

- 甲最坏: L3 开工后发现 biome 近似层折损 (2D Y 扁平 / tile 外 fallback) 导致结构过漏 (oracle 里明显该有的结构没通过滤), 需回头磨 biome 近似层 — 属 C 批内事务, B 批契约 (1280bb5) 不受牵连。
- 乙最坏: BF 滤代码冷存到 C 批开工时接口重排改一遍 (输入双稳定, 低概率, 但零视觉里程碑的时间成本是确定支付的)。

## 4. 悬案清账 (旧短期记忆引用, 本轮已逐一验证)

1. B1 两点追认 ([vegetation] 段归属 + executor 自起池): 已落 GOAL-PLAN B1 节, 无待办。
2. tdseedprobe 类测试工具: 未落地。tdseedprobe 实证 = TEST 实例种子探针 mod ([长期记忆: 052][长期记忆: 053], 源码 D:\Documents\mcmod\td-seed-probe), B2 种子验证走的日志四值法没用上它。若你还记得它当时想干什么, 说一声我再干。
3. 备忘录 tdh 分区 → GOAL-PLAN 落正: 未落地。备忘录 tdh 分区现况只有"审查硬编码,配置化"一条活跃 (无时间戳, 与 TDH 当前主线正交), 建议随本裁决一起定。
4. .agent 三文件: 非悬案 — max 2026-11-17 定夺: 入库, .gitignore 不加 .agent/ 条目 (GOAL-PLAN 案卷 grep ".agent 三文件定夺" 可查)。备忘录 ".agent/我有意不忽略,薪火相传" 原话属 PokerAgent 框架 .gitignore 模板设计语境, 结论同向, 仅为旁证。勘误: 本节初版误标 "GOAL-PLAN 待决策条目", 系漏查 11-17 定夺 + 臆造编号, 已就地修正。

## 5. 裁决后动作 (两案通用)

- B 批收口 (commit + push 时机归你)
- C 批 (L3) 前置: templateManager 单人可用性已实证 [长期记忆: 117], jigsaw 边界打磨为已知硬点 [长期记忆: 115]