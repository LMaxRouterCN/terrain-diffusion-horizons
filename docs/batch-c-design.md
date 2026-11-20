# C 批设计稿 — L3 结构组装与剪影 (batch-c-design)

2026-11-20 23:5x 定稿. 输入: C1 侦查 (docs/c1-recon.md, 记忆 142-145) + 四轮材料战 (ChunkGeneratorMixin/TDBS/LTP 全文, mojmap 源 10 文件, TDH 侧 8 文件). 出处随行标注, 推断单独声明.

## 0. 范围

L3 = ①BF 滤 (biome 谓词过结构) + ②组装 (vanilla structure.generate → placeInChunk 在哑 level 上出真方块) + ③剪影 (方块列折叠进 DH LOD). L4 (逐株地物) 不在本批.

## 1. 数据流 (全链)

    features 车道线程 (TDH-features-lane):
      SpreadCore.computeRange(外扩界) ──→ List<StructureHit>          [撒点, B2 既有]
        per hit: TdStructureAssembleCore
          ├─ 消元循环 (多 entry 集合: setLargeFeatureSeed + 加权消元)   [§3.1]
          ├─ tryGenerateStructure 复刻: 谓词 + structure.generate 十参   [§3.2]
          │    └─ 高度/biome 全部走 TD (far-lookup 会话 + TDBS) — 与 locate 同链
          ├─ placeInChunk: bbox 逐 chunk, postProcess 写真方块进 ProtoChunk [§3.3]
          │    └─ 随机流 = setDecorationSeed 确定流, 可与 vanilla 逐块一致
          └─ 列折叠: 每列最顶非空块 → LaneSegment (clip 到 region 界)     [§3.4]
    generateInternal (DH 池线程):
      列循环内: 结构段与地形/水/air 段合并 → dataPoints                 [§2.6]

## 2. 组件 (文件级)

### 2.1 TdStructureContext (新, dh/structure/)
活对象包, 静态 volatile 单例, DhCompat@LevelLoad 装配 / @Unload 清 (模式同 DhRefreshHook).
字段: ServerLevel level / ChunkGenerator generator (getChunkSource().getGenerator()) /
RandomState randomState (getChunkSource().randomState()) / StructureTemplateManager templateManager
(level.getServer().getStructureManager(), 命名陷阱: getter 名是 getStructureManager 返回 TemplateManager) /
Registry<Structure> structureRegistry / Registry<Biome> biomeRegistry (ProtoChunk 构造参) /
Map<String, List<StructureEntry>>: 集合名 → (Holder<Structure>, weight) 列表 (json structures[] 名字在此解析成 Holder, miss 警告剔除).

### 2.2 StructureSetParams (扩)
加 List<StructureEntryName(String structure, int weight)> entries; parse() 顺手解析 structures[] (json 平坦: structure 键 + weight). record 主构造加参, 撒点字段不动.

### 2.3 TdStructureSpreadCore (小改)
compute() 体内的格枚举+判界抽成 public computeRange(seed, chunkMinX/Z, chunkMaxX/Z) → List<StructureHit>; 原 compute 变为 computeRange(region 界) 一行转发 (撒点行为零变, B2 冒烟不回归).

### 2.4 TdStructureAssembleCore (新, features 车道核心)
上下文注入型核心 (B1 "核心不碰 registry" 原则在 L3 正式破戒, 立项令预告过: 车道骨架纯度不变, 核心依赖注入). compute():
  1. ctx = TdStructureContext.get(); null → 空载荷 (未装配/非 TD 世界)
  2. 界 = region 界外扩 padChunks (config, 默认 16 = 256 块 ≥ mansion 尺度)
  3. hits = SpreadCore.INSTANCE.computeRange(seed, 外扩界)
  4. beginFarLookup() try { per hit: 组装(§3.1-3.3) } finally endFarLookup()  [ThreadLocal 会话, 车道线程自洽]
  5. 折叠产出 LaneSegment, 只收落入 region 界的列 (§3.4)

### 2.5 TdSilentLevel (新, 哑 WorldGenLevel)
crash 驱动补面: 全接口方法初始 throw UnsupportedOperationException(方法名), 跑 TEST 按异常逐面填. 初始已知面:
  - 方块后端: Map<ChunkPos, ProtoChunk> (五参便捷构造: pos, UpgradeData.EMPTY, levelHeightAccessor, biomeRegistry, null); getChunk(x,z) miss 即建
  - getSeed / registryAccess / getMinBuildHeight / getHeight / setBlockState(委托 ProtoChunk) / getBlockState
  - 风险: postProcess 内 getBiome / heightmap 查询等未知面 → 补面轮次未知, 工程重心所在
注意: generate 阶段不需要方块容器 (第十参 = LevelHeightAccessor, L82 签名实证) — 哑 chunk 只在 placeInChunk 阶段被写.

### 2.6 合并点 (TdDhWorldGenerator.generateInternal 小改)
列循环前: 载荷 = LaneManagers.features().pollOrSubmit(...) 的返回值 (现在被丢弃, L152-153) → 建 per-region 列索引: int[cols*cols] structTop + String[] structBlock (dl0 64x64).
列循环内 (solidTop/coverTop 之后):
  structTop > solidTop 时:
    - 结构段 [solidTop, min(structTop+1, maxY)) 发结构块 (列高 = 结构顶)
    - 水段被压: waterTop <= structTop → 水段不发; 否则水段照旧 (结构段在水下, 取可视顶 = 水面 — monument 语义)
      → 规则: 列可视顶 = max(coverTop, structTop); 段发射序: bed 段 [bottom, solidTop) + 水段 [solidTop, min(waterTop,·)) + 结构段 [max(solidTop, waterTop), structTop+1)
    - biome 用列自身 style.biome (非草方块无 tint, 结构块色不受影响)
air 尾段从列可视顶起 (DH 段堆叠不变量, L1 帽段事故教训 [长期记忆: 118 前后]).
异步模式: 首算返回 null, 重拉后二次 generate 命中缓存表 — 环路 B2 已验证, 无新机制.

## 3. 关键算法

### 3.1 消元循环 (mojmap 逐指令, createStructures 体内实证)
    WorldgenRandom wr = new WorldgenRandom(new LegacyRandomSource(0L));
    wr.setLargeFeatureSeed(seed, chunkX, chunkZ);            // 一次
    int total = Σ weight;                                     // 剩余总权
    while (!entries.isEmpty()) {
        int j = wr.nextInt(total); int k = 0;
        for (e : entries) { j -= e.weight; if (j < 0) break; ++k; }
        if (tryGenerate(entries[k])) return;
        total -= entries[k].weight; entries.remove(k);        // 同一随机流继续
    }
单 entry 集合: 无随机直调 (vanilla 同款分支). ruined_portal_nether 的 tag 是 nether biomes, TDBS 永不返回 → 谓词失败 → 自动淘汰, 零特判.

### 3.2 tryGenerateStructure 复刻 (定义体全文实证)
    int refs = 0;                                            // fetchReferences 离线新 chunk 恒 0 [C1 §1]
    Predicate<Holder<Biome>> pred = structure.biomes()::contains;
    StructureStart start = structure.generate(ra, generator, biomeSource, randomState,
        templateManager, seed, chunkPos, refs, levelHeightAccessor, pred);
    start.isValid() → 成功; 否则 false (消元循环重抽)
GenerationContext record 11 参含 WorldgenRandom random [L163] — 来源待读 (Structure.generate 体内, 疑实例字段 this.random; 写码首日读 Structure.java L60-100). 洞 #1.
不调 structureManager.setStartForStructure (那是 chunk 存储侧, L3 直接拿返回值用).

### 3.3 placeInChunk + 随机流 (applyBiomeDecoration 实证)
    WorldgenRandom rand = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
    long decSeed = rand.setDecorationSeed(worldSeed, chunkX*16, chunkZ*16);   // setSeed 覆盖初始 → 确定流
    rand.setFeatureSeed(decSeed, 0, stepOrdinal);                             // 结构步内 index 0
    start.placeInChunk(silentLevel, structureManager(null), generator, rand, writableBox, chunkPos);
setDecorationSeed 实现体待读 (WorldgenRandom.java 已在 D:\temp\c1src\moj). 洞 #2.
每 bbox chunk 一次 placeInChunk; box = chunk 全域 (getWritableArea 同义, bounds = chunk 边界).
随机 parity 预期: 逐块与 vanilla 一致 (残破传送门风化孔洞同位) — 验收时可抽验, 不作硬靶.

### 3.4 列折叠
bbox 逐列: ProtoChunk sections 自顶向下找首个非空块 → (topY, blockId, 连续段底 bottomY).
→ LaneSegment(blockX, blockZ, bottomY, topY+1, blockId) (半开, 与 DH 协议一致).
完全嵌入地形 (topY+1 <= solidTop) 的列不产出 (LOD 不可见, 同 vanilla).
bottomY 为信息性载荷 (合并器只用 top+block).

### 3.5 region 外扩
扫描界 = region 界外扩 padChunks; 产出 clip: 只收 blockX/Z ∈ [region 界) 的列. 双向覆盖: start 在 region 内 bbox 伸出 / start 在外扩区 bbox 伸入, 同一 clip 规则.

## 4. 线程模型

- 车道单线程 (TDH-features-lane daemon); far-lookup 会话 ThreadLocal<int[]> [LTP 实证] → 车道线程 begin/end 自成闭环, 与服务器线程 locate 会话互不污染
- 降级路径: approximateHeightmap miss → mixin 不设返回 → vanilla 真噪声柱 (正确性永远保住, 性能 = locate 同款)
- 风险登记: fetchHeightmap miss 时 LTP 内部落 genHeightmap (真 tile 计算) — 非服务器线程跑它是否安全未证 (LTP 72KB 未全读); TEST 首跑观察 (炸/卡 → 日志可见 → 对策: 组装前置 heightmap 预热或改同步)
- ProtoChunk 生命周期 = 单结构组装内, 用完即弃 (瞬时内存 ~几十 MB 上限, 量级安全)

## 5. 验收协议

1. 编译过 (gradle build)
2. TEST 服: TD 世界加载, 组装核心装配日志 + 车道产出日志 (段数/结构名)
3. /locate 对照: locate 命中 ↔ 车道 hit ↔ 剪影可见 (三角一致)
4. 视觉: 远处村庄/神殿剪影立于地形 (dl0 LOD); 水上结构 (沉船/monument) 水面语义
5. 帧率: 段增量可控 (结构列占比小, 预期 < 植被列增量)

## 6. 折损声明 (承 C1 §5)

- rivers/carving 不在高度捷径内 (fork javadoc 自述): 结构放置不依赖
- ancient_cities: TD 地下补丁 vs vanilla 3D climate 的布点偏差, 未量化 (观察点: 命中率 vs locate)
- 剪影粒度: DH 列模型 = 每列一个可视顶块 — 结构内部垂直细节 (多层楼) 压平为列高, 这是 LOD 的物理上限, 非实现缺陷

## 7. 写码清单 (顺序)

| # | 文件 | 动作 |
|---|------|------|
| 1 | StructureSetParams.java | 扩 entries 字段 + parse |
| 2 | TdStructureSpreadCore.java | 抽 computeRange (行为零变) |
| 3 | TdStructureContext.java | 新建 (活对象包) |
| 4 | TdSilentLevel.java | 新建 (哑 level, crash 驱动) |
| 5 | TdStructureAssembleCore.java | 新建 (组装核心) |
| 6 | TdDhWorldGenerator.java | 合并点 (pollOrSubmit 返回值 + 列循环) |
| 7 | DhCompat.java | 装配 Context + 注册核心 + unload 清 |
| 8 | TdConfig.java | padChunks 配置 |
写码首日先读两洞: Structure.generate 体内 random 来源 / setDecorationSeed 实现体.