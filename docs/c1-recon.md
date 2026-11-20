# C 批 L3 侦查结论 (C1)

2026-11-20 20:30–22:40 自动模式产出 (max 开工令: C 批开工; C1 = L3 开工前侦查, 范围 = BF 滤 (biome 过滤) + 组装 + 剪影相关的开工前事实). 证据链: 1.20.1 mojmap 源码 (mergeWithSources jar 抽取) + javap + TD fork 源码 + TEST jar json 解包. 推断项单独标注, 未冒充实证.

结论先行: L3 边界全部钉死, 无决策级悬案; 未读项 2 个 (§7) 是 L3 开工首日材料, 不是决策.

## 1. vanilla 结构链全图 (createStructures mojmap 源码全文实证)

    ChunkGenerator.createStructures(registryAccess, structureState, structureManager, chunk, templateManager)
      ├─ possibleStructureSets()                  ← 集合级 biome 预滤 (§2.3), B2 冒烟实证 15 集合全过
      └─ isStructureChunk(state, cx, cz)           ← B2 已交付的撒点数学
          ├─ 单 entry 集合: 直接 tryGenerateStructure
          └─ 多 entry 集合: setLargeFeatureSeed + 加权消元循环    ← §3, L3 须复刻
              └─ tryGenerateStructure(entry, sm, ra, rs, tm, seed, chunk, pos, section)
                  ├─ 守卫: chunk 已有该集合任一有效 start → 跳过 (离线新 chunk = no-op)
                  ├─ fetchReferences → 离线新 chunk 恒 0 (getStartForStructure 无 start 返 null)
                  ├─ predicate = structure.biomes()::contains     ← §2.1
                  └─ structure.generate(ra, this, this.biomeSource, rs, tm, seed, pos, refs, chunk, predicate)
                      └─ findValidGenerationPoint = findGenerationPoint + isValidBiome   ← §2.2

    createReferences = 17×17 邻域引用扫描 (在线概念, 离线组装不适用)

## 2. biome 谓词真身 (三问之一)

### 2.1 构造处 (tryGenerateStructure, mojmap 源码)

    HolderSet<Biome> holderset = structure.biomes();
    Predicate<Holder<Biome>> predicate = holderset::contains;

谓词 = 结构 json biomes 字段解析出的 HolderSet 的 contains. 实料 = #minecraft:has_structure/* 单 tag 引用 (TEST jar 解包: village_plains / ancient_city / pillager_outpost / mansion 全如此).

### 2.2 查询点: 单点, 非网格 (Structure.isValidBiome, SRG m_262828_)

- 取 GenerationStub.position — 结构起点, 每结构一个方块点
- quart 化 → biomeSource.getNoiseBiome(x, y, z, climateSampler) → 喂 GenerationContext.validBiome.test
- jigsaw 类起点 Y = startHeight 采样 / heightmap 投影; onTopOfChunkCenter 类起点 Y = chunkGenerator 高度查询 (即 §4 高度权威)

### 2.3 两级过滤

- 集合级: ChunkGeneratorStructureState 构造时按 biomeSource 可能返回的 biome 过滤集合. B2 冒烟实证: TD 源下 15 白名单集合全过
- 对象级: 每次 generate 都跑 §2.1 谓词 (多 entry 消元循环里逐轮跑, §3)

### 2.4 locate 查 biome (三问之二): 查

getNearestGeneratedStructure (RandomSpread 版, m_223198_): StructureCheckResult 缓存未命中 → levelReader.getChunk(cx, cz, ChunkStatus.f_62315_) [推断 = STRUCTURE_STARTS: 命名未对照 SRG 表, 语义由用法锁定 = 强制走完 createStructures].

→ locate 结果 = 撒点 ∩ biome 谓词 ∩ generate 内部其他门卫. B2 oracle = superset 最终确认; L3 验证靶 = 与 locate 全量逐位一致 (§8).

### 2.5 L3 谓词数据源: 活 registry, 非解析 json

structure registry 里的对象 biomes() 已把 tag 解析成 HolderSet — L3 从服务器活 registry 拿对象直用, 不解析 json. B2 读 json 是因为撒点参数 spacing/salt 不在 registry 对象上; 谓词在对象上, registry 是更干净的源. Forge 修改 = registry 里就是改过的对象, 免费兼容.

## 3. 多 entry 集合 (5 个, L3 须复刻)

vanilla 消元循环: new WorldgenRandom(LegacyRandomSource(0)) → setLargeFeatureSeed(seed, cx, cz) → nextInt(totalWeight) 选择 → tryGenerateStructure 失败 (谓词不过 / generate 返 INVALID) → 移除该 entry → 重抽, 直到成功或清空.

- 纯函数 (零 chunk 状态) — B2 直调 vanilla RNG 的路线原样适用
- 逐轮与 biome 谓词交互: 如 villages 抽到 plains 变体但表面是沙漠 → 谓词失败 → 移除 → 重抽 desert 变体. 权重全 1 不免复刻
- 白名单内多 entry 集合 (TEST jar 解包): villages(5) / ruined_portals(7) / mineshafts(2) / ocean_ruins(2) / shipwrecks(2); 其余 10 集合单 entry
- ruined_portal_nether 在 overworld 集合内, tag = nether biomes, TD 源永不返回 nether biome → 该 entry 逐轮自动淘汰, 复刻循环后免费正确, 零特判

## 4. 高度权威: 活 generator + far lookup 会话

- TD 世界 = vanilla NoiseBasedChunkGenerator + TD BiomeSource (fork 无自定义 generator 子类, grep 实证; 两 mixin 均 target NoiseBasedChunkGenerator; preset json 的 generator 块 grep 命中未读全文)
- 维度档案: 高 800 / min_y -64 (dimension_type json)
- 现成捷径: FarLookupHeightMixin @Inject iterateNoiseColumn HEAD + cancellable — LocalTerrainProvider.inFarLookup() 会话内, 高度探针由 TD heightmap 直答 (javadoc 自述专为结构搜索设计; 量化: buried_treasure 半径 100 chunk 搜索 = 4 万列噪声柱 = 分钟级 → heightmap 直答)
- 会话开关 = ChunkGeneratorMixin terrainDiffusion$beginFarLookup / $endFarLookup, 签名 (ServerLevel, HolderSet<Structure>, ...) 吻合 findNearestMapStructure [推断: 挂 locate 头尾, 全文未读 → §7]
- 水判定: stopPredicate 收水 → max(surface, ScaledAltitude.SEA_LEVEL) + build height clamp (ocean monument 海面语义保住)
- L3 方案: DhCompat LevelLoad 已攥 ServerLevel → getChunkSource().getGenerator() 拿活 generator → beginFarLookup() 包住 structure.generate → endFarLookup(). biomeSource 自动 TD, 高度自动贴 TD 表面
- 折损 (javadoc 自述): 减 rivers / carving — 结构放置不依赖这两者

## 5. TD BiomeSource 语义与折损分层 (fork 1.20.1 源码)

getNoiseBiome: quart 自转 block (TDH 直调零坐标换算); Climate.Sampler 参数整体忽略; double-checked locking 线程安全.

- tile 内: biomeIndexes 表面索引; 地下层 Y 参与 — selectUndergroundBiome = 表面 biome 下延 + deep_dark / lush / dripstone 三补丁 (深度阈值 + 区域噪声)
- tile 外 / 数据未到: default biome → PLAINS 兜底 (getOrThrow, 永不 null)

折损分层:

- 地表结构 (村庄/神殿/沉船...): 起点在地表层, Y 无关 → 折损 ≈ 0
- ancient_cities 类深地下: 起点在地下层, 谓词测试的是 TD 地下补丁模型 vs vanilla 3D climate noise → 折损 = 两者布点偏差 (定性, 未量化)
- 高度: §4 (rivers / carving)

## 6. 材料学地图 (NFRT, 2026-11-20 实证)

F:\MOVED\.gradle\caches\neoformruntime\intermediate_results\ 下:

- decompile_*.jar = SRG 名源码 (m_226596_ 形状)
- recompile_*.jar = mojmap 名 class (javap 直读)
- mergeWithSources_*.jar = mojmap 名源码 ← 本轮 CG.java 抽取源, 已落 D:\temp\c1src\moj\

坑: ① token 硬搜前必先验 jar 映射风格 (读文件头方法名形状), 否则全零误判 ② 方法签名参数跨行时单行正则漏匹配 (m_213609_ = createStructures 曾因此误判不存在).

SRG→mojmap 样本: m_226596_=generate / m_262864_=findValidGenerationPoint / m_262828_=isValidBiome / m_223198_=getNearestGeneratedStructure(RandomSpread) / m_213609_=createStructures / m_223235_=getFirstOccupiedHeight / m_226585_=onTopOfChunkCenter.

vanilla worldgen json 固定解包位 = D:\temp\van_wg_x (52 json = structure_set 19 + structure 33; 白名单 15 集合全在; 源 = TEST jar). 旧 %TEMP%\van_wg_x (489 biome json) = B3 tint 时代产物, 弃用不删.

## 7. L3 开工首日材料清单 (未读项, 非决策)

1. ChunkGeneratorMixin 全文 — javadoc 提 "answers from the coarse map instead": locate 链的 biome 查询疑有同款捷径. 影响: L3 要不要自己做 biome 数据预热, 或直接复用 fork 的
2. TerrainDiffusionBiomeSource 内 beginFarLookup/endFarLookup 的宿主方法 (grep 命中但未定位, 不在 getNoiseBiome 体内)

## 8. L3 验证靶

- 全量: 与 /locate 逐位一致 (B2 冒烟已证撒点层逐位; L3 叠谓词后应仍逐位)
- 视觉: 剪影可见层 — 滤错 = 结构上雪山草原, 肉眼可辨 (甲案裁决的免费通道)
- 折损观察点: ancient_cities 命中率 vs vanilla locate 对照 (地下补丁模型的量化入口)

长期记忆: 142 (蓝图核心) / 143 (材料学地图) / 144 (TD 高度捷径) / 145 (van_wg_x 位置).