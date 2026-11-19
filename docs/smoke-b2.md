# B2 冒烟测试说明 (max 亲跑)

日期: 2026-11-20 凌晨 | 构建: terrain-diffusion-horizons-0.1.0.jar (57696B, commit 728cbdc)

## 环境 (已就绪, 勿动)

- mods: 新 TDH jar 已部署 (01:13) + lmaxs-terrain-diffusion-fork (11-14) + DistantHorizons-3.3.3
- stale 配置 terrain-diffusion-horizons.toml (04:22 混合体) 已移入 D:\temp 备份, 启动时重新播种
- 全新种子或既有 TD 世界均可 (既有世界更佳: LOD 与 A 批可比对)

## 操作步骤

1. PCL2 启动 TEST 1.20.1-Forge_47.4.10 实例
2. 进入 TD 世界 (既有世界直接进)
3. 等 DH 开始拉 LOV (小地图外围出现灰色/轮廓 LOD)
4. 观察远处 LOD 是否包含村庄/神殿等结构痕迹 (B2 无视觉层, 预期与 A 批一致)
5. `tp` 到远处 (LOD 生成圈边缘) 再 `locate village` 最近村庄
6. 退出世界 (保留退出前 DH 拉完 LOD 的时长, 建议 2-3 分钟)
7. 关游戏, 把 logs/latest.log 留着, 回来告诉我

## 日志看点 (我会读, 你不用找)

- "撒点参数装配: N 个集合 [villages, ...]" — 期望 15
- "车道核心已注册: TdStructureSpreadCore"
- "region(...)完成: N 项: villages@chunk(x,z)=block(...)" — 撒点命中输出
- "缓存命中" — 异步刷新环路闭环判据 (不出现也 OK, 属重拉短路证据)
- 退世界: "车道缓存已清 (N region)" — 非 TD 世界退出应为静默
- "TDH 生成器注册完成 (seed=...)"

## locate 对照 (第 5 步)

- 若 tp 点在 oracle 已生成的 LOD 圈内: locate 到的村庄应在撒点命中列表中
- locate 结果可能在列表外 (locate 带 TD 群系过滤, 属 L3 层, 列表 ⊇ locate 是正常方向)

## 意外情况

- 崩溃: 截图/发 crash-report 路径
- 装配 0 集合: 说明 ResourceManager 读取链路有问题, 也发我
## 冒烟结果 (2026-11-20 02:07-02:18, 世界"新的世界(14)", seed=5303470317317397359)

### 六看点判定

- 撒点参数装配 15 集合: PASS (02:11:34 白名单全列)
- 车道核心已注册: PASS (02:08:02)
- region 完成行: PASS (格式 = region(...)完成: N项: type@chunk(x,z)=block(x,z), 56 条 villages)
- 缓存命中 (待验#6): PASS 出现方向 (11 条, 02:17:11-48, tp 后 LOD 重拉阶段, 异步刷新环路闭环)
- 退世界车道缓存已清: PASS (3702 region, level unload)
- 生成器注册: PASS (seed=5303470317317397359)

### 15 类型撒点分布 (3702 region, 约 6 分钟活跃期, 车道单线程, 吞吐约 10 region/s)

ancient_cities 95, buried_treasures 577, desert_pyramids 64, igloos 60, jungle_temples 62, mineshafts 216, ocean_monuments 62, ocean_ruins 141, pillager_outposts 10, ruined_portals 31, shipwrecks 100, swamp_huts 60, trail_ruins 50, villages 56, woodland_mansions 7

数量级与 vanilla spacing/separation 直觉一致 (高频撒点最多, 80 块间距的 mansion 最稀)。

### 事件链贯通

首调 (02:11:59) - 车道线程池创建 (1 核) - region 完成流 - 缓存命中 (tp 后) - 退世界清理。TDH 错误行 0。

### locate 耗时侧写

village_plains 2220ms / village_snowy 36944ms (snowy 稀 - locate 扫远, 属 TD 种子特性, 非 TDH 范畴)。

### 遗留

- locate 返回坐标未入日志 (LocateCommand 仅记耗时, 坐标走聊天栏), 包含性验证 (oracle 包含 locate) 待补: 若有聊天记录坐标则比对 56 条 villages 撒点, 否则标记为后续验证项, 不阻塞 B2 收口。

## locate 包含性验证 (同源数学最终验证)

聊天栏坐标从 ChatComponent 日志行找回 (LocateCommand 本体只记耗时):

- village_plains: [7424, ~, 880] (593 格外, 耗时 2220ms)
- village_snowy: [3664, ~, -4064] (6211 格外, 耗时 36944ms)

### plains: 精确命中 (PASS)

- oracle 撒点: region(116,13) @ 02:14:06: villages@chunk(464,55)=block(7424,880)
- locate 返回: [7424, ~, 880]
- 坐标逐位相等。撒点 oracle 与 vanilla 结构摆放数学同源的正向硬证据。

### snowy: 未测 (NOT TESTED, 非失败)

locate 目标 chunk(229,-254) 属 region(57,-64), 该 region 日志零出现 —— LOD 拉取未覆盖 (玩家距 6211 格)。oracle 只在被动查询的 region 上有数据, 圈外无数据属预期。最近 oracle villages (chunk(224,-194), 60.2 chunk 外) 属另一已计算 region, 无矛盾。

### 结论

同源撒点数学验证通过 (正向精确命中 + 无反例)。B2 冒烟全项收口。
