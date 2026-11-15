# Terrain Diffusion Horizons (TDH)

用 Terrain Diffusion 的地形生成能力加速 Distant Horizons 的 LOD 生成。
Accelerates Distant Horizons LOD generation using Terrain Diffusion.

## 状态 / Status

**WIP — 未完成,还在修 bug。** README 未完成,项目仍在快速迭代。
**WIP — not finished, bugs are being fixed.** This README is a stub; the project is under rapid iteration.

## 性能 / Performance

- 原版渲染距离 5 + DH 渲染距离 512,约 30 分钟完成全部 LOD 生成
- 约 500 chunks/s,约 87% 的时间保持满速
- Vanilla RD 5 + DH RD 512: full LOD generation in ~30 minutes; ~500 chunks/s, full speed ~87% of the time

## 已知问题 / Known Issues

- LOD 高度整体偏低约 64 格 (y 坐标语义待修) / LOD heights ~64 blocks too low (y-coordinate semantics)
- 地表方块显示为石头 / surface blocks render as stone
- 不生成地物与结构 / no features or structures generated
- 部分河流和海洋缺失 / some rivers and oceans missing

## 许可 / License

MPL-2.0