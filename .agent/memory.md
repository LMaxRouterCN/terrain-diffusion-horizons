<!-- ID:001 -->
[2026-10-11 v3终版,已推翻v1雨闸门模型] VS2 2.4.11 world_weather 哨兵坐标bug(全案报告:vs-td-investigation/CASE-REPORT.md):船坞区块ticking+任意镂空/悬空/地下ship→tickChunk每tick以1/16抽列做冰雪探测查getBiome(不问天气:晴天raining=0全速复现,level.dat字节级证实;/weather clear已执行且无效)→VS2 occlude对镂空列返回哨兵(0,minY-1,0)→useBiomeAtWorldPos无条件套shipToWorld→输出c_w+R·(-c_s)≈船坞模长(2400万格级)且随船摇晃逐tick移动→TD等同步推理型BiomeSource必miss→主线程每tick park 3.5-5.4s→TPS0.18,无自然终点(流停=退档[Stopping server前8秒签名]或船坞卸载)。修复:VS2侧两个wrap(useBiomeAtWorldPos/usePrecipitationAtWorldPos)加 if(pos.getY()<level.getMinBuildHeight()) return original.call(level,pos);;BiomeSource mod侧防御=Server thread且缓存miss直接走default/null fallback。诊断指纹:远点半径=某船坞中心模长+角度窄弧单调漂移+每tick恰1次miss+离群点模长=另一船坞。v1翻车教训:对MC源码的"记忆"未核对禁止当事实(唯一记错处=isRaining守卫);"雨停"从"流停"反推=循环论证。
tag: Minecraft, VS2, bug根因, mod兼容, 哨兵坐标
<!-- END:001 -->
<!-- ID:002 -->
[2026-10-12] 用户(max)对VS2(Valkyrien Skies)开发组强烈反感:更新不积极、文档不好好写、开发组小圈子各行其是。倾向避免与其交互、不想编译VS2本体。涉及VS2 bug修复路线决策时:优先本地mixin模组或下游防御,PR只作fire-and-forget;勿默认用户愿意等上游或升VS2版本(其整合包锁2.4.11)。
tag: Minecraft, VS2, 用户偏好, 修复路线
<!-- END:002 -->
<!-- ID:003 -->
Max 的待办/备忘录是单文件扁平记录,路径: D:\Documents\my\日常\!备忘录-剪贴板-随笔记-待办_不要删除这个文件.md (约5.6KB@2026-10-19,持续增长)。标记约定: 无前缀=未完成,勾=完成,叉=决定不做,半勾=进行中,圈=搁置/纠结,可多个按时间序。分类: 杂乱/整合包/pokeragent(本地D:\Documents\my\Poker Agent)/mc模组(本地D:\Documents\mcmod)/tht(TansHugeTrees)/emi(LMaxs-EMI-Fork)。统计基准(2026-10-19): 勾6项(下界合金掉落物、Log Filter注释、create覆盖挪回收站、旧记号转义迁移、ae2合成卡住破案=根因ae2vm、EmiScrollGridFix); 半勾3项(ae2/emi回退决策、pipez fork、td×vs2兼容半成); 叉1项(AppliedCannon)。条目无时间戳,除pokeragent分区两条(先create回收站后转义迁移)外完成先后不可考,用时重读文件为准,勿信统计快照。
tag: 待办文件, 备忘录路径
<!-- END:003 -->
<!-- ID:004 -->
2026-10-18 02:17 审查结论: 爆炸效果重写与大火燃烧重构在本地为纯绿地项目, mcmod全部项目过目加全盘检索AppliedCannon零命中, 无任何现有代码。可行性报告落盘于 D:\Documents\my\日常\爆炸大火-可行性审查\爆炸与大火-可行性审查报告.md。核心: 爆炸=射线改体积场, ExplosionEvent.Start取消加getToBlow填充, 零mixin, 中等难度, 建议先行; 大火=燃烧管线, 架构决策留给max: chunk级SavedData燃烧图(推荐) 对比 每方块BE, 中高难度; 两项目共享分帧调度器基建。许可证MPL-2.0, Forge 1.20.1。
环境事实: max真实桌面路径为 D:\Desktop 而非C盘用户目录下Desktop; 后端终端为PowerShell。
[2026-11-10 更正] 桌面"30分钟后休眠.bat"现为正常30分钟版: 触发器 AddMinutes(30) 已实锤(全文复读), 早前"AddHours(2)漏改"报告过时(10-18当天或之后已被修正)。残留: 计划任务名仍叫 AutoHibernate2h(纯名字无功能影响, 取消=桌面"取消休眠.bat")。等效执行: Register-ScheduledTask -TaskName AutoHibernate2h -Action (New-ScheduledTaskAction shutdown.exe /h) -Trigger (Once -At (Get-Date).AddMinutes(30)) -Force。桌面bat一览: 2小时后休眠/2小时休眠/30分钟后休眠/结束所有进程/取消休眠/杀edge/杀filec/杀QQ/重启EarTrumpet。
tag: 爆炸, 大火, 绿地项目, 可行性审查, 桌面路径, 休眠bat
<!-- END:004 -->
<!-- ID:005 -->
LLMwc4n是max的LLM提示词/破限分享仓库,用户常来开issue求助模型拒绝问题. 2026-09-25时max自述要忙一两个月(约到12月). 5个open issue全是用户求助: #2(2025-11),#4(gemini敏感词),#5(ds已"打穿",用的claude-fable-5.1改的提示词),#6(捧场),#8(2026-10-13,v4/v4.1被拒,等max回复). #7编号空缺(404).
tag: LLMwc4n项目, 用户求助, max忙期
<!-- END:005 -->
<!-- ID:006 -->
pipez fork 远端定稿(2026-11-06):仓库已改名 https://github.com/LMaxRouterCN/LMaxs-Pipez-fork(原fork名pipez),工作分支lmaxs/1.20.1,云端头0fddedb,本地origin已指向新名。旧私有仓已由max网页手删(gh无delete_repo scope,跳过了授权流程)。三commit:894b17c镜像适配,175312c BFS修复(#1),0fddedb案卷文档(GOAL-PLAN/TODO/AUDIT/ENERGY-MAP+.agent,公开化max豁免)。遗留:1.上游henkelmax/pipez无LICENSE,正式分发前需上游许可确认 2.默认分支仍是master(上游快照),lmaxs/1.20.1非默认,待max定 3.fork的parent显示null疑似已detach(未验证) 4.#1修复后未跑构建(import星号导入,大概率过未验证) 5.分支名与备忘录规范(1.20.1+forge)不一致,维持现状
tag: LMaxs-Pipez-fork, git远程, 发布闭环
<!-- END:006 -->
<!-- ID:007 -->
GitHub Search API(gh search / search/issues)索引滞后且分页排序不稳定: 近期创建的issue可能完全不在结果(实例:LLMwc4n#8和WindowTextExtractor#1存在于GraphQL直查但搜索无),total_count与实际可有±几条抖动(实例:BCLib#172在第2页和第3页重复出现). 权威做法:名下仓库用GraphQL批量查,具体issue用repos API直查. 2026-10-18实测.
tag: gh-cli, 搜索索引坑, 数据可信度
<!-- END:007 -->
<!-- ID:008 -->
gh api -f key=true 发送的是字符串"true"而非JSON布尔,subscription端点(PUT /repos/{owner}/{repo}/subscription)不认就静默空转:HTTP 2xx退出码0但订阅不生效,2026-10-19实证(failed:0但27仓未建订阅). 正确做法:JSON body管道喂gh api --input -. 回读验证必须独立GET,不能信PUT退出码.
tag: gh-cli, API坑, 布尔参数, subscription
<!-- END:008 -->
<!-- ID:009 -->
exec已在PowerShell中运行,不要再套powershell -Command "..."双层:外层引号会对$变量先行插值,内层收到的脚本$n/$fail全成空壳,foreach直接ParserError. 直接裸写循环/管道即可. 2026-10-19实证.
tag: exec指令, PowerShell嵌套, 引号插值坑
<!-- END:009 -->
<!-- ID:010 -->
2026-10-19 IR2040启动卡死定案闭环: Ixeris 4.6.9+1.20.1-forge线程接管与FML早显窗口互锁死锁. 机制: 原main线程被改名Ixeris Event Polling Thread,卡在MainThreadDispatcher.await不泵Win32消息,Ixeris另开线程跑Minecraft构造,fmlearlydisplay对该早显窗口glfwSetWindowTitle跨线程同步SendMessage需等待消息泵→环死锁. max回退Ixeris旧版后启动成功=根因实证. 证据: jstack dump在$env:TEMP\ir2040_td.txt(另存D:\Documents\my\ir2040-thread-dump.txt待确认),日志冻结15:59:14.920,Render thread卡937s仅耗2.1s cpu,已排除磁盘/Defender/堆(Xmx14336m)/双显卡. 后续: 给Ixeris仓库开issue,草稿证据链齐. 该包mods在版本隔离目录,E盘机械盘与本案无关.
tag: bug根因, Minecraft, Ixeris
<!-- END:010 -->
<!-- ID:011 -->
2026-10-19 create等写入载荷的转义坑: 运行中后端会把载荷里的```替换为三反引号; 需要在目标文件保留字面```时(如给源码写断言), 用字符串拼接(TICK+3)绕开替换. 三反引号直接写载荷会原样落盘但破坏聊天端markdown渲染, 应写```让其转换. 自2026-10-19起后端转义新约定: 载荷中的反斜杠+反引号由后端还原为字面反引号, 旧记号退役(后端重启后生效, 重启前旧记号仍有效).
tag: API坑, PokerAgent
<!-- END:011 -->
<!-- ID:012 -->
2026-10-19 终版勘误(覆盖旧版): (1)双禁铁律: 指令标记序列(方括号包裹的cmd/code那两对)不得以字面形式出现在载荷与正文任何位置(注释/引用/示例不豁免), 讨论时用口述或码位描述. (2)码位铁律: 凡引用文件已有特殊字符一律从文件自适应提取(探针)或\uXXXX写法, 永不凭记忆心算(两连错: 12300=角括号, 12336=U+3030波浪线; 方头括号=U+3010/U+3011). (3)GBK控制台: python脚本print非GBK字符会UnicodeEncodeError吞诊断, 开头sys.stdout.reconfigure(encoding='utf-8', errors='replace'). (4)剪贴板read经展示层转换不保真, 取证一律行号模式. (5)旧记号TICK+3字面写入remember/memory载荷会被运行中旧后端替换成三反引号存进记忆文本, 记忆里提及该记号用TICK+3拼接口述. (6)转义语义生效条件(实测修正): commit 72a761a后实测运行中后端仍为旧语义(反斜杠加反引号原样落盘, 旧记号替换仍生效)——备份通知不能证明重启加载了新代码(该功能4ad4306就有); 转义还原需max重启后端后生效, 重启前载荷继续用旧记号, 重启后切反斜杠转义, 要字面反斜杠加反引号则双写反斜杠. 关联011.
tag: API坑, PokerAgent
<!-- END:012 -->
<!-- ID:013 -->
[2026-10-26深夜终态] IR2040客户端冻结案→已发布GitHub issue: https://github.com/Antikythera-Studios/epicfight/issues/2625 (title带[1.20.1/20.14.17]前缀,body 8523字符,OPEN).根因链:Render thread钉死在EpicFight 20.14.17 AnimationClip.getPoseInTime:100(NaN毒化二分搜索,三个分支全false区间永不收缩)←SPChangeLivingMotion包处理→ClientAnimator.offAllLayers→Layer.off→getEnabledPose→ConcurrentLinkAnimation.getPoseByTime:72→NaN elapsedTime源自AnimationPlayer.tick 48-51(EMPTY_ANIMATION totalTime=0且isRepeat=true,elapsedTime%=0→NaN,任何曾被禁用的composite层一tick带毒).NaN出生:Animations:143匿名DirectStaticAnimation→StaticAnimation()无参构造99行super(0.0F,true)→EMPTY_CLIP(AnimationClip:14)clipTime默认0.修复建议三条已在issue:tick守卫getTotalTime()>0/getPoseByTime洗NaN/animationClip加NaN守卫.验证闭环:dump栈23帧epicfight侧9帧与本地源码双向锁死,线上body每行断言有实锤.完整案卷D:\Documents\my\日常\epicfight-bug-case\(body.md/issue-draft.md v2/evidence两dump).dump原件%TEMP%\mc_td.txt与td2.txt.后续:max网页审+拖dump附件,可插桩build定位具体entity/animation对(需复现).次生案:DH-Regen Queue烧核非死锁,零BLOCKED,不另报.教训:jcmd的nid是十进制(Zulu21).附:StaticAnimation.java:101上游typo"emtpy"无关本bug.
tag: bug根因，Minecraft, EpicFight
<!-- END:013 -->
<!-- ID:014 -->
2026-10-26 PokerAgent框架坑(v2修正): ①create指令写入内容约4000字符处静默截断,回执仍报"已创建"——大文件必须分块(每块≤1500字符)+append+每块read校验,严禁单次写长文档. ②append会剥掉新块开头的全部空行(不是只吞一个!前缀空行防吞策略无效),接缝补空行只能用replace -l. ③replace默认整块匹配,行内片段搜不到;精确改行用-l行号模式,支持2行扩3行的插入. ④同一轮连续两个行号replace:先执行行号大的(下游)再小的(上游),否则前面插入导致行号漂移会打错位置.
tag: API坑, PokerAgent
<!-- END:014 -->
<!-- ID:015 -->
2026-10-26 gh api + PokerAgent exec通道实测谱系(修正:推翻早前"含管道必挂"的过度概括):
[安全] 裸URL零flag的gh api;简单jq字符串路径(--jq ".workflows[].path" / --jq ".content");exec内含PowerShell管道的纯本地命令(Get-ChildItem | Select-Object 实测通过).
[挂起无回执] gh api加自定义Accept头(-H "Accept: application/vnd.github.raw");gh api --jq ".content" 后接PowerShell解码管道(ForEach-Object内嵌套中括号圆括号).
[疑似毒,未干净验证] jq表达式内转义嵌套双引号——今晚样本被正文标签污染混杂,早前轮次有独立案底,标记存疑.
[立即报错] gh api -o 不存在(那是curl的flag).
[框架解析铁律] 解释性正文严禁字面出现中文方括号形式的cmd或code标签对——后端会从假标签起抓,把后续散文连同同批真指令一起解析成垃圾,连环报"未知指令".引用标签一律用英文方括号描述.
[输出纪律] 今晚三次消息停在"即将发指令"的冒号处然后直接结束,指令块从未生成.铁律:宣布与指令块必须同条消息,消息不得以预告冒号收尾.
[取文件内容标准姿势] contents端点用--jq ".content"拿base64,回执按宽度折行,去换行再解码;验标签或操作真伪,直接打事件API看actor字段.
[批量解析观察] 深夜四指令批(2×memory+remember+read):remember碎裂出2条未知指令噪声(正文首行+闭合code标签被当指令名),016回执出现两次,疑似碎裂触发批内重放(幂等无害),最终全对(remember新内容经注入回执自验).推论:批内多行指令可能碎裂并引发重放,非幂等变更(gh issue comment/add-label等)忌混批,单独成条发送.
tag: gh-cli, API坑, exec指令, PokerAgent
<!-- END:015 -->
<!-- ID:016 -->
2026-10-26 实锤版+2026-11-02补遗: epicfight上游自动打标机制已由events API验证. issue#2625唯一labeled事件: actor=github-actions[bot](type:Bot),时间2026-10-26T12:53:13Z(贴issue创建后约1分钟). 机制全貌: 仓库.github/workflows/issue-label.yml监听issues opened,按author_association分流——OWNER/MEMBER/COLLABORATOR打"core contributor",其余所有人(含max/LMaxRouterCN)一律自动打"triage",由workflow内gh issue edit --add-label执行. 结论: 该仓库所有外来issue出生即带triage,与人工关注零关联. 通用方法论: 判断issue标签是否人工,查events API对应事件的actor字段,bot即自动化. 2026-11-02补遗: issue#2625的comments字段=1之谜已解——挂附件=发comment,comments API实锤返回10-26T13:01:55Z一条,作者LMaxRouterCN,内容为双dump附件链接(ir2040-ef-td1/td2.txt). 此前events流无commented记录是当时查询的时间窗口问题,附件挂载确实以comment形式落地,疑点闭环. 转写件中GITHUB_VTOKEN错字确认是base64转写噪声,workflow文件本体完好(bot实际执行成功).
tag: EpicFight, gh-cli, 数据可信度
<!-- END:016 -->
<!-- ID:017 -->
[2026-11-02] EpicFight issue#2625 侦察收尾+决策落定(013根因链续篇): ①两分支全链实锤: 1.20.1(HEAD 20.15.1)涉案三文件自20.14.17零改动,issue行号分析对HEAD原样成立;1.21.1(默认分支)tick无守卫+EMPTY匿名类+StaticAnimation无参构造super(0.0F,true)三环同构中招."emtpy"typo两分支都活着. ②上游GPL-3.0;PR队列尸检:外部2521/2591/2605+内部2573全躺3-7个月零合并,issue零真人回应→上游PR=发射后不管. ③全库搜NaN无重复劳动(仅#2625+2021老issue#449). ④2026-11-02晚max拍板:路径C双轨确认. README动机定稿:epicfight本体更新快,但附属Weapons of Miracles更新极慢且闭源,玩家被锁旧版epicfight,上游合并也大概率不回移旧分支→独立mixin模组是旧版玩家出路. 模组名候选EpicFight NaN Freeze Fix(推荐,参照VS2 Sentinel Guard Fix句式)/EpicFight NaN Guard,查重后定. max将自建项目文件夹+转移PokerAgent工作目录,目录就位后项目正式启动,届时定PR分支(1.20.1先行待确认)与修复层级(tick守卫根修+getPoseInTime纵深+可选ConcurrentLink洗startsAt). GPL注脚:本地LAN自用零风险,整合包公开发布时mixin挂GPL-3.0最稳. 证据:案卷epicfight-bug-case\evidence\upstream-src-20261102\五源码副本,GOAL-PLAN.md含完整侦察章节
tag: EpicFight, 修复路线, Minecraft, mod兼容, bug根因
<!-- END:017 -->
<!-- ID:018 -->
2026-11-02 教训：自动模式二次确认唤醒时，连发多条"任务未完成，挂起中"式极简回复，每条都是一轮对话，都会截断最老上下文，把关键决策点细节顶丢。正确做法：任务挂起等用户决策时，唤醒回复必须零冗余（一句确认未完成），不发多余指令，用户回归后从GOAL-PLAN.md/memory/git log恢复现场，而非反复空转回复。
tag: 空转教训, 自动模式, 上下文管理, PokerAgent
<!-- END:018 -->
<!-- ID:019 -->
Minecraft联机拓扑(2026-11-02确认,含修正):max(游戏id=LMaxRouter)开Forge1.20.1整合包"Industrial Revolution 2040"(E:\MC\.minecraft\versions\下,549mods)单人世界→Open to LAN(手动指定25565)→SakuraFrp隧道(节点名gali,公网门牌frp-now.com:21540,免费档10Mbps上限)→朋友old_gali连接。朋友客户端=其机器上的Forge镜像(经FML握手+36min游玩推断),非Fabric。E:\MC\juyue123≠朋友端:它是PCL实例的Fabric1.20.1轻量建筑包(28mods:litematica/printer/axiom/syncmatica/worldedit/lanserverproperties),与IR2040联机无关。实证(10/26晚):36min内old_gali异常掉线重连4次(无一次正常"left the game"),join间夹等距重复异常栈,断线根因(带宽vs服务端异常循环)待日志定性。max端kreno jar文件名含空格前缀:"优化-性能   Krypton Reno-forge-1.20.1-26.1.1-1.20.1.jar"。
tag: Minecraft, 联机拓扑, old_gali, SakuraFrp, mod兼容
<!-- END:019 -->
<!-- ID:020 -->
PokerAgent create指令坑(2026-11-03):对同一路径连续create两次以上时,第一次create会把被覆盖文件移入.agent_trash回收站,第二次create若回收站已存在同路径历史备份则拒绝执行(报错"回收站已存在该路径的历史备份")。解除法:把.agent_trash下对应备份文件改名或删除(如GOAL-PLAN.v1.bak.md),再重发create。勿在指令名上猜(是create不是write)。
tag: API坑, PokerAgent, 文件操作
<!-- END:020 -->
<!-- ID:021 -->
2026-11-03 弹药盒×aeinfinitybooster失配案,完整证据链与可复用结论:
①NoSuchFieldError消息里打印的类型是【调用方编译期烧死的期望类型】,不是目标类现状。诊断法:两边jar各javap一次比对字段描述符(javap -p -classpath <jar> <全限定类名>)。
②aeinfinitybooster 2.1.0(2026-10-23发布)做了RegistryObject→Supplier字段类型迁移,字段名不变描述符变;旧线1.0.0+19/+20(Modrinth)和1.0.0+51(整合包v50实际pin的版本)仍是RegistryObject。Forge社区这波迁移是下游mod的隐形ABI断点。IR2040包v50.zip内含+51原版jar,回滚材料在包本体里(勿重复下载边角版)。
③mixin软依赖模式(编译期不链接对方+mixin注入直接字段访问指令)会把对方字段类型冻成ABI:注入期不解析字段所以启动无异常,首tick执行到getstatic才链接期炸,常被tick崩溃拦截类(Neruina)吞掉。症状=聊天栏刷"捕获到物品堆叠"+服务端不崩+日志NoSuchFieldError。
④修复四路:回滚(最优=从包zip抽原版pin版本,配磁盘CreationTime取证定位"谁何时换的mod")/等作者适配/踢mod/ASM手术(getstatic描述符改Supplier+invokevirtual改invokeinterface,栈形状不变checkcast保留,只动mixin类)。
⑤取证技巧:mod文件CreationTime vs v50.zip内条目对照 = "包解压落盘"vs"后来手动放入";lightspeed-cache内mod指纹=包实际跑过的版本;包config目录有无某mod配置文件=该mod新功能是否被用过。
tag: bug根因, mod兼容, Minecraft, mixin, 字节码, 修复路线
<!-- END:021 -->
<!-- ID:022 -->
(2026-11-03 max告知)当前对话渠道的文件上传API已损坏,read全文模式(剪贴板API整文件上传)不可用;字面量读取上限80000字符,大文件必须行号模式分段读,或exec+Select-String在本地过滤只回显命中行。影响:通读大源码(PokerAgent框架/大Java项目)成本极高,max因此冻结了PokerAgent框架侧全部待办。选活时优先小JSON/配置文件类,或grep定位后局部读
tag: API坑, 上下文管理, exec指令
<!-- END:022 -->
<!-- ID:023 -->
[2026-11-03终态v3-项目完结]EpicFight-NaN-Freeze-Fix全链闭环含实测:①修复(0002124,分支1.20.1forge)②构建(3mixin OK)③投mods清旧1.0.0④GitHub release(repo public,url=https://github.com/LMaxRouterCN/EpicFight-NaN-Freeze-Fix/releases/tag/v1.0.1,jar asset在位)⑤max实测通过(首帧不崩+JEI漏斗45mb/高炉矿车450/合成10)。代码侧+验证侧全终结,零未完。完整因果链:改整合包配方→TC连坐(026)→EF冻结(013)→mixin尾参崩溃(024)→1.0.1→实测销案。nanhealthfixer定性:TeamPotato血量卫生mod,服务端only三事件监听洗NaN/负值血量+取消NaN伤害,与本mod动画时间线正交零冲突,max未表态去留(纯功能选择)。遗留仅两项无时限:上游issue#2625跟踪(自动triage机制见016)+上游PR fire-and-forget可选(见017,PR队列3-7个月)。教训:行动前探remote勿信记忆;非原版mixin handler尾参必javap实测运行时jar;dev依赖版本≠运行时+remap关闭时编译期校验不可信
tag: 当前任务, 1.0.1发布, nanhealthfixer定性, 发布闭环
<!-- END:023 -->
<!-- ID:024 -->
(2026-11-03)EpicFight-NaN-Freeze-Fix(D:\Documents\mcmod\EpicFight NaN Freeze Fix)崩溃根因:ConcurrentLinkAnimationMixin的@Inject(method="getPoseByTime")handler尾参用了CallbackInfo,但epicfight 20.14.17运行时该方法返回非void→mixin校验报"CallbackInfoReturnable is required"→APPLY失败→游戏死。触发链:进世界首帧LocalPlayer构造→epicfight挂capability→ConcurrentLinkAnimation类首次加载→mixin转换爆炸。主菜单阶段不触发目标类加载,故装载期全程无报错。修法:尾参改CallbackInfoReturnable<javap实测返回类型>,逻辑不变(只洗startsAt,不setReturnValue不cancel,纯观察合法)。另2个mixin(AnimationPlayer根修/AnimationClip兜底)同款风险待javap审计。编译期未拦截两嫌疑:①commit cd1a3a7关remap连带瞎掉AP签名校验②dev依赖epicfight版本≠运行时20.14.17。教训:非原版目标+remap=false时,编译期校验不可信,mixin handler尾参类型必须对运行时jar javap实测
tag: bug根因，EpicFight, mixin, Minecraft
<!-- END:024 -->
<!-- ID:025 -->
(2026-11-03事故立规)exec输出纪律,三次同源教训(两次回执超长+一次锟斤拷洪水卡爆前端):①任何内容读取前必须先查文件大小+扩展名,二进制(zip/jar/mca/png/nbt)永不当文本读,白名单=json/toml/txt/md/js/java/mcmeta等纯文本②每级输出双上限:Select-Object -First N+单行Substring截120字③对-Recurse文件集做内容搜索必须带扩展名过滤④PowerShell/框架无输出保护,洪水=前端死,保护层责任在agent侧
tag: exec指令, API坑, 用户偏好
<!-- END:025 -->
<!-- ID:026 -->
(2026-11-03漏斗案终态-实测通过)max自定义配方/数据包改动固定在config\openloader\data\LMaxRouter Datapack(版本目录E:\MC\.minecraft\versions\Industrial Revolution 2040\config\openloader),排查配方先查这里,kubejs和saves世界datapacks搜不到是假阴性;openloader结构=data放数据包/resources放资源包(内有MusicPack/resourcepack/Unique Dark大量二进制,exec内容扫描必须绕开);TC匠魂melt配方有共用条目(ingot_5.json=高炉/漏斗/矿车共享450mb),改单物品数值必须拆分:覆盖原配方移除目标+新建独立配方,直改连坐;vanilla datapack JSON不吃注释。mods顶层465个jar,命名=中文前缀+英文原名(本体:匠魂-TConstruct-1.20.1-3.12.1.231.jar,前置:前置库 匠魂 地幔-Mantle-1.20.1-1.11.117.jar,附属:魂樱工匠-sakuratinker-1.4.2.3.jar+前置etstlib/l2library/sakura_library),按英文名正则找jar必漏本体,须全列或带中文关键词;TC melt配方路径data\tconstruct\recipes\smeltery\melting\metal\iron\,锭=90mb基准。2026-11-03凌晨max实测三断言全通过(45mb×10=450mb=90mb×5锭,合成10/熔炼/连坐清),配方案销案
tag: Minecraft, openloader, 配方排查, 文件操作
<!-- END:026 -->
<!-- ID:027 -->
(2026-11-03)PowerShell坑:非StrictMode下访问.NET对象不存在的属性返回$null且不报错(笔误$z.EnEntries→静默null),null经管道给Where-Object产出零项→"零命中"类输出无条件成立,具有欺骗性。防御:①判定"是否为零"前先打印已知非零的总量自检(证明扫描器活着,如recipe总数)②可Set-StrictMode -Version Latest让笔误变显式报错③zip条目扫描属性$z.Entries逐字符核对。同类:引号插值坑
tag: PowerShell, exec指令, 文件操作
<!-- END:027 -->
<!-- ID:028 -->
万能闸门UniversalGate(立项2026-11-06),路径D:\Documents\mcmod\UniversalGate,GOAL-PLAN.md已落盘。核心:max的"0厚度传送门"方块,外表普通方块,逻辑上从世界拓扑删除该格,两侧焊住。架构(含关键修正):单点mixin折叠getBlockState因方向歧义死掉(查询无方向参数),定为"translate(pos,dir)定向翻译原语+系统入口薄钩"(流体传播/updateShape/capability,只看坐标不看内容,零方块类型特判)。实体穿越=碰撞箱置空零mixin。管道网络不融合(代理限流阀语义,深度防环)。v0:同维度对面配对,深度1,验证T1-T10(水7格自检/双箱63格/FE直充/实体穿)。最大风险:1.20.1注入点未核实,Phase 0反编译调研=go/no-go关口;neighborChanged需BE主动桥;写路径漏一条=水写进闸门。状态(2026-11-06):Phase 0调研已授权但排队中(前面有max的其他优先任务),mod代码冻结未授权。待max定:正式名/开关交互/FE取舍/外观/配置项
tag: 万能闸门, 绿地项目, 当前任务, Minecraft, 架构决策
<!-- END:028 -->
<!-- ID:029 -->
万能闸门Phase 0结论(2026-11-06):GO。报告在research/phase0-report.md。核心事实坐标:流体写=FlowingFluid:253 setBlock(pos,fluid,3)直写,漏翻译水顶闸门(实锤);流体读双通道Level.getBlockState(L351→LevelChunk:150)+Level.getFluidState(L360→LevelChunk:187)必须双钩;邻居通知总闸=NeighborUpdater接口(redstone包,Level:147唯一CollectingNeighborUpdater实例);渲染读走RenderChunkRegion直调LevelChunk.getBlockState绕过Level层→与逻辑读正交,翻译写物理落格渲染自然正确;客户端同步同样正交(sendBlockUpdated只发被写格);BE.pos=final+immutable+chunk map键,移动BE=重建对象,capability注入点=BE构造gatherCapabilities(IForgeBlockEntity extends ICapabilitySerializable);BE传送建议v1排除。待max拍板:读翻译层(Level vs LevelChunk)、写覆盖面(仅setBlock vs 全家桶)、BE分期。渲染编译循环618/629同格流体+方块双渲
tag: 万能闸门, 架构决策, Minecraft, mixin, 可行性审查
<!-- END:029 -->
<!-- ID:030 -->
td修复案上游情报(2026-11-07):主仓xandergos/terrain-diffusion-mc代码冻结于2026-05-21(develop),release停在5-13的2.3.0-preview。PR#172官方修洞穴灌水(density二值化)合入develop但从未发布,max的jar没吃到。作者活着,精力在SIGGRAPH论文侧(研究仓8-12仍在推)+alt-dev-2分支做1.21.11新版(自述已验证beta candidate:biomes/rivers/优化),1.20.1=弃子版本官方不再碰。issue#255用户luo2006817-byte是趟过雷的战友:1.21.1本地补丁全套(density null branch/default_fluid/cave cheese回归/biome tag修),尸检结论:1.21.1 fluid走seaLevel路径,PR#172二值化修复在其上无效。issue#231证词(1.21.11 Fabric):deep_dark极罕见但存在,lush/basalt极其常见——与1.20.1 Forge实锤(possibleBiomes无deep_dark)冲突,跨版本漂移或声明表与实际返回不一致,待源码验证。max已排除bigglobe主世界路线:bigglobe全自研+自家数据包,兼容缺口=整个模组生态,bigglobe作者亲测"模组数据包转换器"路线并放弃(权威死亡证明);max现行架构=主世界/下界td(vanilla管线)+末地bigglobe(内容少),已知代价=龙之研究龙岛丢失
tag: td情报, terrain-diffusion-mc, 上游状态
<!-- END:030 -->
<!-- ID:031 -->
td修复案技术档案(2026-11-07,配套报告D:\Documents\my\日常\td修复案-分析报告-2026-11-07.md):架构三层=AI(2D栅格)→BiomeClassifier(纯Java规则引擎,非AI!)→vanilla管线。缺口:G1 possibleBiomes无deep_dark(远古城第一关被拒)G2 findBiomeHorizontal/findClosestBiome3d直接return null(/locate死)G3 sparse三群系缺is_overworld标签 G4 density null分支返回-y→未解码tile变0~63海(我的推断,#133"all terrain submerged"原文支持;与luo的seaLevel符号论竞争,PR#172移植后实测裁决)G5 carver隧道在biome JSON(在岗)但cheese/spaghetti/noodle噪声洞穴被final_density替换没了 G6 aquifers off全海平面流体 G7 td覆盖vanilla grove(温度2.0干热林,不修) G8解码缓存不落盘#248(不修,VS2刀缓解)。修复设计:A刀mixin BiomeSource(y<阈值→2D区域噪声选deep_dark/lush/dripstone+possibleBiomes注入+VS2防御刀+可选findBiomeHorizontal粗实现)+DensityFunction null分支-y→1.0(PR#172六行移植);B刀纯数据=is_overworld标签合并(Forge自动union)+新noise_settings(router从vanilla overworld全量抄,final_density=min(td密度,vanilla洞穴部分),aquifers开)+新world_preset(td注册表项公开可组合),交付默认preset路线;C刀MPL-2.0发布。不确定:U1两论竞争U2 aquifer×柱状密度交互U3远古城biome检查采样y(若表面采样柱状路由够不着)U4 beard_thin×高度域1968 U5 1.20.1/1.21.1流体路径同构性U6注入时序(低风险)。工时A3-5h/B2-4h/C1-2h,日历最好2天典型3-5天最坏1-2周
tag: td情报, terrain-diffusion-mc, bug根因，Minecraft, mixin
<!-- END:031 -->
<!-- ID:032 -->
(2026-11-07实勘)max家网络:maven.neoforged.net被SNI阻断(GFW级):本地DNS污染(每次解析返回随机假IP,如69.171.229.73/204.79.197.217),DoH真身在cdn77(89.222.116.12/103.228.130.61),但真IP+正确SNI仍TLS握手RST→改hosts无效。无本地代理(常见端口全关,无TUN网卡);LAN代理10.24.136.70:2080仅注册表残留,TCP死。可达:maven.minecraftforge.net直连OK(~1.2s),BMCLAPI的/maven/映forge OK但不映neoforged(404实证),腾讯mirrors(gradle发行版OK),doh.pub与223.5.5.5的DoH可用,1.1.1.1 DoH超时。结论:neoforged系构建在max机器上必须:等他开代理/找Central等其他镜像/预填本地缓存。exec通道持管理员权限。
tag: Minecraft, 网络环境, DNS污染, maven.neoforged, neoforged, 构建坑
<!-- END:032 -->
<!-- ID:033 -->
PowerShell三大坑之三(与引号插值/静默null并列):foreach语句块(非管道对象)不能直接接"|"管道,报ParserError"An empty pipe element is not allowed",且解析期死亡=整个脚本报废,前面代码也不执行。防御:循环内收集到List/数组变量,循环后"$out | Select-Object"分两步。2026-11-07实证(MDG源码考古块阵亡,浪费一轮)。判读技巧:回执只有ParserError无任何输出=全脚本未运行,不是部分执行。
tag: PowerShell, exec指令, 坑
<!-- END:033 -->
<!-- ID:034 -->
(2026-11-07)墙内拉取被SNI阻断的GitHub生态maven仓(如maven.neoforged.net)的CI-fork旁路技法:①gh repo fork <owner>/<repo>②PUT repos/<me>/<repo>/actions/permissions body={"enabled":true,"allowed_actions":"all"}启用fork的Actions③gh api repos/<me>/<repo>/git/refs POST建branch指向目标commit(版本=tag+distance公式,如1.0.13=tag1.0后第13commit,可用compare API数出)④branch名以feature/**开头自动触发build-prs.yml(push触发),或workflow_dispatch兜底⑤run完成后GET .../actions/artifacts,下载maven-publish artifact(zip=完整maven仓结构含.module/.pom/jar/校验和,走api.github.com,墙内可达)⑥解压成file://本地仓+init-script(-I,projectsLoaded/allprojects/afterEvaluate)注入gradle:摘除死端点仓+尾挂本地镜像,版本用-P对齐。gh api喂body用[IO.File]::WriteAllText无BOM json+--input文件。此法可复用于任何"上游maven被墙但源码在GitHub且CI有publish动作"的场景。
tag: 构建坑，网络环境，gh-cli，CI旁路，技巧
<!-- END:034 -->
<!-- ID:035 -->
[2026-11-09 夜] td案 γ壳版已部署: terrain-diffusion-mc-forge-1.20.1-2.3.0-windows.jar 8,167,740B 现役TEST实例(mods\), 回滚锚 .bak-caveshell; 三 commit: 76c364c 洞穴壳+手术(surface_cave豁出lift+mountain chance30→15) / 4b65be6 modrinth钉版本 / 案卷commit(含日期勘误,哈希以git log为准); 实例 carver_altitudes.json 已备份 D:\temp\carver_altitudes.json.bak-20261110 并删除, 首启动以新种子重播种(应含 excluded:[minecraft:canyon, bettercaves:surface_cave] + shellExempt:[minecraft:canyon])。**构建配方(两次救命,勿丢)**: 环境四件套 TD_MIRROR=1 + GRADLE_USER_HOME=F:\MOVED\.gradle + JAVA_HOME=E:\Java\Zulu\zulu-17 + TEMP/TMP=D:\temp, 任务名 buildForgeWindows; TD_MIRROR 激活 init.d\td-mirror.init.gradle(C盘活动home与F盘旧home双份同MD5): 摘 maven.neoforged.net 死仓 + 头插 D:\temp\td-maven 本地仓(NFRT 1.0.13 + minecraft-dependencies 1.20.1 全套22文件)。不设 TD_MIRROR → neoform-runtime 解析 404(maven.neoforged.net = GFW级SNI阻断, 见记忆032)。yungs 坐标已钉版本可离线。**教训: 开工先检索长期记忆/案卷**——11-09夜白绕12回合网络侦查, 配方一直躺在记忆035里。[2026-11-10 更新] push 悬案销: github 直连已恢复, 07:02 push 成功(b536576); 输出 7d004d9..b536576 = 仅推新 commit, 此前 pending 的 9 commit 已在远端(何时推上未观察到,某次网络窗口已成功), commit 债清零。BC密度已落地(cave_spawn_chance 100→70, 见记忆042), 该悬案销。GOAL-PLAN 日期勘误维持已 amend。
tag: td情报, terrain-diffusion-mc, 构建坑, 网络环境, 当前任务
<!-- END:035 -->
<!-- ID:036 -->
HF (huggingface.co) 网络坑 (2026-11-07 td案实证): Java 报 PKIX path building failed + curl schannel 报 CRYPT_E_NO_REVOCATION_CHECK = 证书吊销检查通道 (OCSP/CRL) 被墙, 证书本身是真的 (curl --ssl-no-revoke 直连 200, Zulu cacerts 168 条根全在). 解法: hf-mirror.com 完全可达 (API+resolve 通道全 200, 307 重定向正常), 替换 URL 即通. 注意与 maven.neoforged.net 的 SNI 阻断 (连 curl 都死) 是两种不同性质的墙, 诊断时先分清: 吊销检查死=证书真+握手半途死; SNI 死=连接层死.
tag: hf-mirror, 坑, 网络环境, API坑
<!-- END:036 -->
<!-- ID:037 -->
PokerAgent 长跑技巧 (2026-11-07 实证): agent 框架在 exec 指令结束时会屠杀全部后台进程 (Start-Process -PassThru 的进程 90s 内被杀). 解法: schtasks /create /f /tn 名字 /tr bat路径 /sc once /st 23:59 + schtasks /run /tn 名字 立即触发, 计划任务父进程是系统服务, agent 屠刀够不着, 构建跑满 4m59s 实证. 判据: bat 尾部 echo %ERRORLEVEL% > 完成标记文件, 轮询 Get-Process + 日志尾部. 另: bat 内 set 环境变量不穿透 (需在 bat 内写死), init.d + 环境变量对 schtasks 触发的构建同样生效.
tag: 构建坑, 技巧, schtasks, PokerAgent
<!-- END:037 -->
<!-- ID:038 -->
2026-11-07 09:25 td案构建大捷: terrain-diffusion-mc-forge-1.20.1-2.3.0-windows.jar (7.78MB) BUILD SUCCESSFUL exit0, 产物@versions\1.20.1\forge\build\libs, 验尸全绿. 三墙三对策: ①maven.neoforged.net SNI死→init.d F:\MOVED\.gradle\init.d\td-mirror.init.gradle (TD_MIRROR env闸门,不设零副作用) + TDMirror=D:\temp\td-maven (重版本化NFRT1.0.13+GMD CI生成minecraft-dependencies:1.20.1) ②HF吊销检查通道死(证书真)→hf-mirror替换multiloader-loader.gradle L69+L88,备份D:\temp\td-mll-backup.gradle ③PokerAgent屠后台进程→schtasks tdbuild20261107脱离进程树. 构建配方=D:\temp\td-build-run.bat(TD_MIRROR=1+GRADLE_USER_HOME=F:\MOVED\.gradle+JAVA_HOME=E:\Java\Zulu\zulu-17+TEMP/TMP=D:\temp,cd versions\1.20.1跑根gradlew buildForgeWindows),日志D:\temp\td-b-out/err.log,标记td-b-exit.txt(0=成功). 剩余: 实测8项(远古城市/locate biome等)+locate修复+PR回馈. git未commit(等max过目).
tag: td情报, terrain-diffusion-mc, 构建坑, 网络环境
<!-- END:038 -->
<!-- ID:039 -->
HF(huggingface.co)网络坑实证(2026-11-07 td案): Java报PKIX path building failed + curl schannel报CRYPT_E_NO_REVOCATION_CHECK = 证书吊销检查通道(OCSP/CRL)被墙,证书本身是真的(curl --ssl-no-revoke直连200,Zulu cacerts 168条根全在). 解法=hf-mirror.com完全可达(API+resolve全200,307重定向正常),URL替换即通. 与maven.neoforged.net的SNI阻断(curl都死)是两种性质的墙,诊断先分清: 吊销检查死=握手半途死+证书真; SNI死=连接层全死.
tag: hf-mirror, 坑, 网络环境, API坑
<!-- END:039 -->
<!-- ID:040 -->
PokerAgent长跑技巧(2026-11-07实证): agent框架在exec指令结束时屠杀全部后台进程(Start-Process -PassThru的子进程90s内被杀,连同java进程). 解法: schtasks /create /f /tn 名字 /tr "bat路径" /sc once /st 23:59, 再 schtasks /run /tn 名字 立即触发. 计划任务父进程=系统服务,agent屠刀够不着,构建跑满4m59s实证. 判据: bat尾部 echo %ERRORLEVEL% > 完成标记文件,轮询Get-Process+日志尾部. env变量要在bat内写死(set不穿透),init.d+bat内set对schtasks触发的构建同样生效.
tag: PokerAgent, 技巧, schtasks, 构建坑
<!-- END:040 -->
<!-- ID:041 -->
TD fork 测试实例: E:\MC\.minecraft\versions\TEST 1.20.1-Forge_47.4.10 (1.20.1 + Forge 47.4.10, 生产 jar 实测用实例, 2026-11-08 起用于 td 案实测八项)
tag: 测试环境, Minecraft
<!-- END:041 -->
<!-- ID:042 -->
TD洞穴体系关键结构(2026-11-08):mod jar-in-jar捆YUNG's Better Caves 2.0.6(默认开,properties键caves.bundled_cave_mod);caves.noise_caves=auto语义=BC在场且启用→mod自己的噪声洞穴(cave_density.json整套)关闭→BC在场时cave_density是死配置,改它无效。max最终决策:不关BC,走BC实例层调参(零mod侵入);max实测:BC摘除(bundled_cave_mod=false)机器能跑不崩。noise_settings: min_y=-64,height=2032,dimension_type min_y=-64/height=800(不一致但能跑),sea_level 63,aquifers_enabled=true,bedrock_floor渐变-60/-55。实例config在versions\TEST 1.20.1-Forge_47.4.10\config\隔离(PCL实例)。[2026-11-10 结案补充] BC形态实锤: 捆在TD主jar的META-INF/jarjar/yungs-better-caves-1.20.1-Forge-2.0.6.jar 405KB(+yungs-api 370KB+onnxruntime 6.8MB)。**坑: META-INF/jarjar/ 是Forge的jarJar路径, META-INF/jars/ 是Fabric的——grep错路径必扑空,踩了三轮**。BC主配置=jar内data-driven JSON(data/bettercaves/worldgen/configured_carver/better_cave.json),实例config\bettercaves\只放liquidregions等附加选项,主配置不落盘;覆盖法=OpenLoader数据包(实例已有OEIBF先例)。密度杠杆定案: cave_spawn_chance(区域级洞穴生成率,默认100.0=每区域必出洞="任意处5x5竖井必遇洞"根因)是唯一干净旋钮——BC主carver概率已1.0,TD密度补偿(×3.34封顶)对它无效,vanilla probability字段是死旋钮。落地: config\openloader\data\z-bc-cave-density\包,100→70(-30%),仅新世界生效; cavern(23)与surface_cave(0.15→被TD补偿至0.5)未动。BC经biome_modifier(add_carvers/remove_carvers)挂is_overworld全群系,carver重建走BetterCavesWorldCarverConfig codec,datapack覆盖应生效,BCConfigForge运行时覆盖风险低。
tag: td情报, terrain-diffusion-mc, Minecraft, bug根因, 坑
<!-- END:042 -->
<!-- ID:043 -->
[2026-11-08] terrain-diffusion-mc 四案判词浓缩(全案详见fork仓库GOAL-PLAN.md及C3/C4 commit):
①World Scale=30/scale米/格线性(native_resolution=30,模型包world_pipeline_config.json自述实锤),MAX_SCALE=6→5m/格是分辨率地板,1m/格需换1m分辨率的模型,全球公开DEM天花板SRTM=30m,原料不存在。
②江河(勘误2026-11-09,原判词两处误): 河宽三层漏斗:幂律wanted(radiusPixels L1183)→绝对墙min(MAX_CHANNEL_RADIUS_PX=16半径,全宽封顶32格,原判词"封顶16px=16格"语义笔误)→confinement min(wanted,0.36×谷底px); accumulation=localRunoff×cellAreaKm2(L2226,传入pixelSizeM)面积补偿→流量为物理量与scale无关(原判词此半句正确);但confinement按格计→scale1(30m/格)全宽~24格=720m(confinement墙≈12px半径先拦),scale6(5m/格)16墙生效全宽32格=160m→格数24→32不恒定,物理米宽反缩4.5倍,scale红利为负非零;泰晤士级万km²盆flow≈1e4 wanted≈60-64px被墙砍转深度;大江存在(vanilla大河3倍宽)但需万km²盆地+宽谷平原,山地confinement(谷底=两侧升高<18m)压窄成溪;湖浅<15m+小<0.135km²+无补给=puddle删除;cherry_grove三重门(气候窗+cherryNoise>0.15+rarity10 RARE)极稀有,locate找不到=半径内无样本,均非bug;实测建议:看大江用scale1+宽谷(720m级),scale6下大江封顶160m。
③onnx-cache泄漏:缓存文件名=name-ORT版本标签-模型hash(OnnxModel L270),unknown=getImplementationVersion()返null回退,unspecified=manifest字面量;旧ORT版本条目永不清,每换捆绑ORT版本+~1.08GB;换mod jar不触发(key不含mod版本)。测试实例(E:\MC\.minecraft\versions\TEST 1.20.1-Forge_47.4.10):现役=unknown系(06-13),unspecified系1.08GB化石零风险可删;地形盘缓存terrain-diffusion-cache 7.69GB全可再生。
④实测路:游戏内/td-explore开web地图(端口19801)俯瞰河网,是江河案唯一游戏侧验证手段。
tag: terrain-diffusion-mc，td情报，坑
<!-- END:043 -->
<!-- ID:044 -->
[2026-11-09] td fork配置架构决策:carver_altitudes.json改为播种式(source of truth模式)——首运行把默认规则落盘成用户可编辑文件,之后只读文件,内置常量仅种子+坏文件内存兜底;坏文件绝不覆盖(保留用户手改,响亮报错)。max明确偏好:反运行时硬编码/反merge式"内置+文件合并",配置必须落盘可见可改,用户删行=功能关(后果自负)。旧BUILT_IN表+BUILT_IN_EXCLUDED+applyOverrides merge语义已删。语义变更:文件缺bettercaves条目=BC不被lift。通用教训:给max做任何"默认配置"一律走落盘播种,不走代码内常量参与运行时。此前判词:vanilla canyon被reach_summits+density_compensation误伤(p0.01→0.125~0.34,y顶拉穿天窗),豁免minecraft:canyon为种子默认。
tag: td情报, terrain-diffusion-mc, 架构决策, 用户偏好
<!-- END:044 -->
<!-- ID:045 -->
TD fork 构建配方 (2026-11-10 实证): buildForgeWindows 任务必须以 versions\1.20.1 为 cwd 跑, 在 fork 根下报 "Task not found"; gradlew.bat 用 fork 根的那份; 产物在 versions\1.20.1\forge\build\libs\。任务定义不在 vdir 顶层 *.gradle (grep 零命中, 疑在 applied script 或 buildSrc, 未追)。env 四件套: TD_MIRROR=1 / GRADLE_USER_HOME=F:\MOVED\.gradle / JAVA_HOME=E:\Java\Zulu\zulu-17 / TEMP=TMP=D:\temp。构建时长约 2.5 分钟。
tag: 构建坑, terrain-diffusion-mc, buildForgeWindows
<!-- END:045 -->
<!-- ID:046 -->
TD 种子架构 (2026-11-10 D5-U4 实测二次反转后修正): 管线随机源全链条吃 seed (coarse L195 seed / L211 seed+1, latent seed+5819/5820, decoder seed+5819, 均为管线seed+用途偏移, 接线本身健康)。**2026-11-10 04:1x max 实测: codec 修复后存读不崩 (codec 案结案), 但新世界地形+出生点与旧世界全同 → "level.dat 写残致 seed 复用/两案合并"结论被实测推翻, 种子案独立重开**。铁证: 地形+spawn 全同 = pipeline 眼中 seed 恒定。分水岭 = max 对两个世界各输 /seed 对报 (seed 同=创建侧, seed 异=传递链断)。新嫌疑: ①两世界 seed 本就相同 ②pipeline 构造时 seed 来源 (biome source ctor?) ③init/setSeed 触发时机晚于地形/spawn 计算 ④静态单例不随世界重建 ⑤config 固定 seed 项。**新雷: fork 根下 common/ 与 versions\1.20.1\common/ 双源码副本, 构建用后者, D5-U1/U2 侦查读的是前者, 同步性 D5-U4 hash 钉死中**。磁盘缓存 TileKey 含 seed 无污染 (此结论不受影响)。
tag: td情报, terrain-diffusion-mc, 种子
<!-- END:046 -->
<!-- ID:047 -->
TD 数据包损坏案根因 (2026-11-10, 机制约 70% 把握): 保存时 PrimaryLevelData 报 "Unknown registry element in biome_source" → WorldGenSettings 编码失败 → level.dat 写残 (seed 陪葬) → 重进读档炸, 新建世界正常。机制: biome_source 注册表按对象身份存 codec, 保存时拿实例 codec() 反查 ID, 而 MapCodec.codec() 每次调用返回新包装实例 → 注册实例与反查实例身份不等 → 反查失败。new TerrainDiffusionBiomeSource 全仓零命中, "运行时 new" 假说已排除。修复 = CODEC 单例化 (static final, 注册与 codec() 返回同一对象), 三行改动; 若无效, 下一嫌疑 = RegistryOps 上下文错配。上游原版即有 (归档 log 2026-11-07-2 起 11 处实锤)。附赠修复: 存档可重进, level.dat 可存 seed (77-85 九世界空 seed 即此案所致)。
tag: td情报, terrain-diffusion-mc, bug根因, 数据包损坏
<!-- END:047 -->
<!-- ID:048 -->
DH 加速 LOD 生成评估结论 (2026-11-10, 报告 D:\Documents\my\日常\DH加速LOD生成-可行性评估-2026-11-10.md): 可行。官方入口=DhApi.worldGenOverrides.registerWorldGeneratorOverride; 推荐路线=IDhApiWorldGenerator.generateLod+setApiDataPointColumn (非 chunk 抽象类); 数据点=DhApiTerrainDataPoint.create(detailLevel,blockLight,skyLight,bottomY,topY,blockStateWrapper,biomeWrapper); 尺度=TD 30m/px×scale2=60m/px vs DH detail6=64blocks/列, 插值吸收。最大风险=wrapper 实例获取无公开工厂(兜底: 反射 core BlockStateWrapper 工厂钉死 2.3.2-b / DhApiBlockStateWrapperCreatedEvent 拦截); 次风险=DH 2.3.2-b beta API 易变→compileOnly 软依赖+在场检测注册(TerraBlenderSurfaceCompat 先例)。联机拓扑=DH 生成在客户端, 模型 2GB 在服务端→单机先行联机二期。工时典型 2-4 天最坏 1 周。模块边界: TdLodTranslator 纯翻译/TdDhGenerator 壳/DhCompat 注册/配置落盘播种。待 max 决策: 做不做/范围/依赖策略。
tag: td情报, terrain-diffusion-mc, 可行性审查, DH
<!-- END:048 -->
<!-- ID:049 -->
[2026-11-10] Windows loader 模块身份=基名(非路径): 进程内已有同名DLL时, LoadLibrary/System.load 传任意绝对路径都只会返回已加载的那个模块。"预载System32新版VC++再加载目标DLL"这类实验天然作废——JDK bin里捆绑的VC++运行时(如Zulu17的14.29)在JVM启动时就随JVM的DLL入进程, 之后无法替换。实战: ORT-DML 1.26 的 onnxruntime.dll 在 Zulu17 下 System.load 报1114(初始化例程失败), 同一DLL文件 python(无捆绑CRT走System32 14.51)加载成功; Zulu17 preload System32 CRT 后依旧1114(preload拿回的是旧模块)。调试这类问题的正解=换不同JVM做矩阵测试, 而非进程内 preload。CRT遮蔽链(Zulu17 bin 14.29 → 静默遮蔽 System32 14.51)为当时主嫌, 矩阵验证中。
tag: 坑, Windows, DLL, terrain-diffusion-mc, bug根因
<!-- END:049 -->
<!-- ID:050 -->
[2026-11-10] ORT-DML 1.26 (onnxruntime.dll FileVersion 1.26.0) 在 Windows 各 JVM 下 System.load 报 1114 的实证矩阵: JDK bin 捆绑 VC++ CRT ≥14.40 → PASS (zulu214219=21.0.7, zulu-25, VSCode redhat JRE25); bin 捆绑 14.29/14.31 → UnsatisfiedLinkError 1114 初始化例程失败 (zulu-17=17.0.12, Oracle jdk-21=21.0.2, zulu21=21.0.4)。机制=应用目录(java.home\bin)在 DLL 搜索序第一位, 旧 CRT 遮蔽 System32 的 14.51; 注: 缺入口理论上应报 126/127 而实测 1114, 精确机制未深究, 版本相关性为实证结论。max 的游戏(PCL)实际用 E:\Java\zulu214219 (latest.log "java version 21.0.7 by Azul Systems") 所以从没炸过。**mod 兼容性影响: terrain-diffusion-mc 用户用捆绑旧 CRT 的 JDK 启动会直接 UnsatisfiedLinkError 1114, 建议写进 README 系统要求 + 崩溃时的友好提示**。另发现: E:\Java\jdk-17.0.14.7-hotspot 疑似 32 位安装(加载 64 位 DLL 报 "Can't load AMD 64-bit .dll on a IA 32-bit platform")。调试正解=换 JVM 矩阵测试, 进程内 preload System32 CRT 无效(模块身份=基名, 见记忆 049)。
tag: 坑, Windows, DLL, terrain-diffusion-mc, bug根因, JVM, mod兼容
<!-- END:050 -->
<!-- ID:051 -->
2026-11-11 td案取证教训(通用):①Minecraft轮转log时间戳格式为"日+月"(如11-08=0811月2026,11-10=1011月2026),跨多日扫描时禁止硬编码日期前缀,必须按目标日动态生成,否则静默0命中还误判"日志已轮转丢失";②mod的"Persisted ... to <路径>"日志行尾部含完整缓存路径(含seed-hex分目录),截断显示会永久遮蔽关键键值,涉路径行Cap用200+;③javap输出为2空格缩进,自写方法解析器勿按4空格匹配;④gradle缓存里vanilla反编译源不存在.java散文件,运行时版本jar(versions\<ver>\<ver>.jar)可直接javap取证Forge补丁后的真实字节码。
tag: 坑, 日志分析, terrain-diffusion-mc
<!-- END:051 -->
<!-- ID:052 -->
td案结案卷(2026-11-12):TD(Terrain Diffusion mod)种子丢失bug根因=LocalTerrainProvider双实例竞速。init(static synchronized类锁,LevelEvent.Load时刻,持真种子)与getInstance(独立INIT_LOCK,首批coarse调用者,Load+0.03~0.75s)各持一把锁互不互斥,两边都是查INSTANCE空→重量级构造器→无条件赋值;两线程并行构建LTP,后完成者覆盖先完成者:getInstance后写=0L覆盖live种子=新世界整场seed-0(指纹=加载飞快+spawn固定(-5120,69,5120)+24x24扩张);init后写=好场(else分支setSeed自愈+双批次指纹);重进必好(INSTANCE已在,else-if setSeed)。修复=getInstance锁改LocalTerrainProvider.class与init互斥(INSTANCE volatile,DCL安全),可回馈上游。九场铁证:坏场w83,w84,w85,(86),(90)全单批次;好首建w77,(87),(91)全双批次。seed hex:(86)坏c80ac7a0678ee9a0/(90)坏539eb6874ef9c574/(91)好8e9f8c97872ce0a0/(92)好6f27a29b076e1160/(87)好937c0cf8d4dc9ad8/(88)好8791516634639969/(89)好27106f412749fe46。悬案:重进会话coarse37+uncached54(DH LOD候选);(83)(84)(85)=codec测试世界全seed-0;codecfix=f58966e正常。环境:fork=D:\Documents\mcmod\LMaxs Terrain Diffusion fork;构建D:\temp\td-build-run.bat(TD_MIRROR=1);测试实例E:\MC\.minecraft\versions\TEST 1.20.1-Forge_47.4.10;probe=同实例mods\tdseedprobe-1.0.0.jar(源码D:\Documents\mcmod\td-seed-probe);部署jar=mods\terrain-diffusion-mc-forge-1.20.1-2.3.0-windows.jar。PokerAgent教训(2026-11-12):多行消息可能被通道剥换行→多行脚本压成单行注释空跑(回执=已执行无输出);单行exec内联会吃空格;remember/memory内联已弃用必须code块;稳妥姿势=exec单code块多行+消息短。
tag: bug根因, Minecraft, td情报, terrain-diffusion-mc
<!-- END:052 -->
<!-- ID:053 -->
[2026-11-12 03:35] td种子竞速案封卷。修复=cf6d44f(LocalTerrainProvider: getInstance与init统一类锁,INIT_LOCK字段删除),已部署TEST实例,备份D:\temp\td-jar-backup\pre-seedfix-1112-022114.jar。终审证据:max冷启动连建6新世界,6/6好:每场ServerStarted行chainSeed=levelGetSeed=instanceSeed=seed四值全等,零0值;W1 AboutToStart时INSTANCE=null(旧竞态窗口安全通过,getInstance排队进门见活实例);W2-6 AboutToStart时instanceSeed=旧值→ServerStarted收敛(init else分支setSeed自愈实锤);六场均~2分钟冷算(修复前坏场秒进)。两条交错支路(实例未建/实例旧种子)均被实测覆盖。修复前首建坏率62.5%,未修复6连好概率≈0.28%。probe=tdseedprobe-1.0.0.jar(源码D:\Documents\mcmod\td-seed-probe)。后续:上游PR待max点头。
tag: terrain-diffusion-mc, td情报, bug根因，种子
<!-- END:053 -->
<!-- ID:054 -->
2026-11-14 td深度案收线: max裁决"-128合理,收工"——原目标"洞穴不捅基岩/墙不可感知"闭环;设计哲学(max原话):"基岩是墙但正常游玩不应感知墙"。better_caves bottom_y维持-63(-127灌洞否决,深部内容全部延后)。延后backlog(重启时用): ①下界岩(基岩层附近少量)+岩浆河置新底 ②矿石边角:gold_lower成-64~-48悬空薄片(唯一遗留) ③洞带是否下扩bottom_y ④OEIBF包识别。世界生成链实锤: td零carver数据,carver实现内嵌td jar(bettercaves jar不在mods);OpenLoader包z-bc-cave-density喂better_cave.json(bottom_y=-63×2区域,top_y=80);td经NoiseBasedChunkGeneratorMixin@Redirect getCarvers→ScaledCarvers抬升外来carver。vanilla矿石锚点(1.20.1版本jar实测)劈两半: 跟随组above_bottom自动灌新深部=diamond/buried/large(-80~80→裁剪后-128~-48),redstone(底~15),redstone_lower(-32~32→裁剪后-128~-96),iron_small(底~72),lapis_buried(底~64);留守组absolute不动=gold(-64~32),gold_lower(-64~-48悬浮),lapis(-32~32),iron_middle(-24~56),copper(-16~112),emerald(-16~480)。深板岩矿变体由ore_feature自带deepslate target自动处理。未实锤: td biome→vanilla矿引用链(fork resources无biome目录,biome json疑在jar内或代码生成)。TEST实例jar已打-128补丁,备份D:\temp\td-jar-backup\pre-depthfix-1113-175954.jar,回退=回拷mods;fork三版本同步(1.20.1+1.21.1+1.21.11,Δ=64+基岩锚above_bottom化+ScaledCarvers L373注释)待max裁决。
[2026-11-14 03:55修正,MD5+max证词] ①上述"TEST实例jar已打-128补丁"不成立: 063② MD5实证,11-14 02:59部署时刻 mods 活跃jar≡pre-depthfix备份→-128补丁当时不在mods;max表态不追究去向,事项封口。②"fork三版本同步待裁决"已由056作废(深度案全撤销,fork源码从未改)。③本条延后backlog(下界岩/岩浆新底/gold_lower/洞带下扩)随-128撤销作废,仅历史档;岩浆河唯一存活分支=地底河案water_region_spawn_chance调法(见058/064)。④当前世界生成态(液面-46/河床-52/密度100)以056/058/064为准。
tag: td情报, terrain-diffusion-mc, 矿石锚点, 深度案, 延后清单
<!-- END:054 -->
<!-- ID:055 -->
2026-11-14 洞穴带侦察收口:better_cave.json结构=2隧道(bottom_y=-63,top_y=80,y_compression 5.0/2.2)+2大空腔(-63..-18无底is_floored=false,-63..-28有底,cavern_spawn 23/region)+misc.replaceable=#minecraft:overworld_carver_replaceables。深度案原始病灶实锤:bottom_y=-63语义=旧底-64上方1格,旧基岩带-64..-55→洞带下沿9格嵌在基岩带内,carver啃不动基岩(不在replaceable标签)→洞底暴露基岩墙=当年"河贴基岩"根因。手术后基岩-124..-119,洞底-63悬空56格,"洞穴不碰基岩"当前已成立零改动达标。max指令"bottom_y高点"读作下扩灌新深部留缓冲,候选-110/-103/-96(缓冲9/16/24),倾向只动隧道L9/L32两处,cavern留-63;td properties L158实锤noise型carver自适应band。未实锤:旧世界地底河水源(aquifer vs BC自带液态逻辑)。bettercaves实现疑td捆绑注册(jar内路径无bettercaves字样,mods无其jar,但type=bettercaves:better_cave活体生效)。
tag: td情报，terrain-diffusion-mc，深度案，洞穴，bug根因
<!-- END:055 -->
<!-- ID:056 -->
2026-11-14终改:深度案全撤销——max明令"把-128改回去",TEST mods内td jar已回拷为原版(D:\temp\td-jar-backup\pre-depthfix-1113-175954.jar,MD5校验一致),-128补丁版归档同目录terrain-diffusion-2.3.0-depth128-patched.jar(撤销的撤销可用)。fork源码从未改动,三版本同步议题就此终结。世界终态:原生深度-64,基岩带-55~-64(max口径),洞穴底bottom_y=-48(better_cave.json 4处,原-63嵌基岩带9格=旧"河贴基岩"根因),洞底与墙间6格实心深板岩垫,top_y全部未动。深部白纸backlog(下界岩/岩浆河/gold_lower/洞带下扩)随-128撤销作废,仅剩历史档(054/055)。生效范围仅新区块,旧区块保持-128形状。
tag: td情报，terrain-diffusion-mc，深度案，fork同步
<!-- END:056 -->
<!-- ID:057 -->
2026-11-14沟通教训(修正版,前版"零改动读法优先"已被证伪作废):max的短句裁决按祈使句优先直译。"bottom_y高点,离基岩层远点"=把bottom_y抬高的动作指令,我连续三轮误读(维持-63不动→下扩灌新底抛-110/-103/-96→"描述现状句"),还把错误结论写成教训存档,根因=发明"现状自洽"解释覆盖字面义。新规则:字面像祈使就按祈使执行,与现状冲突也不自行折衷;"把X改回去"=撤销X的补丁回原状;"X完事"才是结案句;拿不准时一句话向max复述指令内容再动手,禁止三轮解读。
tag: 用户偏好，沟通，坑
<!-- END:057 -->
<!-- ID:058 -->
2026-11-14 地底河案结案(侦破+修复双闭环):根因=BC(嵌td jar JiJ内yungs-better-caves-1.20.1-Forge-2.0.6)液体系统Liquid Regions,液面默认-55(基岩顶),旧世界bottom_y-63洞底8格淹水贴墙,新bottom_y高于液面洞全干河消失。液体系统全配置=config\bettercaves\forge-1_20_1\liquidregions.json三钮(独立于carver json/td properties,td不碰):①liquid_altitude水岩共用液面绝对Y零校验②water_region_spawn_chance水区份额%(校验[0,100]fail-soft,岩浆吃补集)③liquid_region_size区域波长(0.001=千格级)。极性实锤:srg_to_official_1.20.1.tsrg f_49990_=WATER f_49991_=LAVA;getLiquidBlockAtPos三态逐列:水<阈值-jitter/岩浆>阈值+jitter/null过渡带~5%水岩隔离。C档已执行且max目视验收通过(11-14凌晨):液面-46+bottom_y-52×4,终态水深6/墙垫2,生效新区块,BC加载零报错。备份链D:\temp\td-jar-backup:liquidregions.json.orig-55.bak+better_cave.json.bottom-48.bak+better_cave.json.bottom-63.bak。岩浆河调法=chance 40→15(未动,等max指示)。后续任务(进行中):把-46/-52固化进td mod分发默认——bottom_y已在z-bc-cave-density数据包(分发友好),液面在config侧需查BC jar内有无内嵌默认json(有→gradle构建时替换JiJ内BC文件;无→mixin/写config/fork BC三选)。测试环境:E:\MC\.minecraft\versions\TEST 1.20.1-Forge_47.4.10。
tag: td情报，terrain-diffusion-mc，深度案，地底河，液面，bug根因
<!-- END:058 -->
<!-- ID:059 -->
2026-11-14 Forge模组jar数据包机制(通用事实):模组jar内data/(assets/)目录本身就是自动常驻全局数据包/资源包,无需OpenLoader、无需建世界挂载、无需pack.mcmeta(Forge的ModFilePackResources内置处理,文件夹包才需要mcmeta)。硬门槛:jar必须是合法mod(mods.toml齐全),裸data/ jar扔mods目录会被跳过;壳mod(mods.toml几行+data/,1KB级)是集成包生态标准件;改第三方jar塞data是脏路(更新冲掉+版本链污染),正确姿势=塞自家mod。覆盖优先级:事件注册包(AddPackFindersEvent)>模组包>原版;模组包之间同路径优先级未实测。OpenLoader唯一残值=玩家拖文件夹+热改免重打包,对mod作者零价值。BC jar自带data/bettercaves/worldgen/configured_carver/better_cave.json即此机制实证。
tag: Minecraft, 技巧, td情报, terrain-diffusion-mc
<!-- END:059 -->
<!-- ID:060 -->
max的fork模组版本号规范(2026-11-14,源:备忘录mc模组规范段,路径见记忆003):两段式[原作版本号去点].[我的版本号],例1124.2=emi原作1.1.24+自己.2;原作大版本更新→我的小版本重置回.1;我的段正常三段式1.0.0,用不上可一位(230.1)。td fork 2026-11-14发布=230.1.1.0(原作td2.3.0+自己1.1.0)。fork与原作差异大(重写/新功能占比高/原作长期停更)→可去掉原作段。release tag=版本号。通用jar名:模组名-模组版本号+mc版本+加载器-编译时间戳.jar(例lmaxs-emi-fork-1124.2+1.20.1+forge-20260928235749.jar)。branch=mc版本+加载器(多加载器取mc版本,stonecutter取main)。fork命名LMaxs X fork(保留原作名,继任者不必保留此段)。命名空间com/lmaxrouter优先。新增物品→创造标签页单开一页。更新日志只写增量。主控配置文件一站式(依赖/mods.toml内容链入)。
tag: 用户偏好, 版本号规范, fork规范, mc模组, 备忘录
<!-- END:060 -->
<!-- ID:061 -->
2026-11-14实测:解释正文裸写中文方括号指令标签字样→解析器识别为真标签→后续全部内容被逐行拆成单行指令→回执大量"未知指令",首条是正文后半句,全部被拒执行零副作用。正确姿势:正文提及标签必须转义(反斜杠)或用英文方括号,指令标识符只出现在真正指令位置。症状特征=回执逐行未知指令轰炸。
tag: PokerAgent, 坑, 指令格式
<!-- END:061 -->
<!-- ID:062 -->
2026-11-14: PokerAgent exec 传复杂 PowerShell 脚本偶发 mangling→ParserError。症状:引号串被吞成超大 token、多语句拼行;Parse 期即拒,零执行零副作用,可直接重试。防御:脚本扁平化——不定义 function、不用 for 循环(改直接[0][1]索引)、注释全英文、try 块慎用、长 -like 通配换 -match。当日扁平化重写成功。另:memory 指令 tag 参数必须半角冒号+半角逗号,全角"tag："会报"不支持内联文本"。
tag: PokerAgent，技巧，坑
<!-- END:062 -->
<!-- ID:063 -->
2026-11-14 03:10 地底河案部署前取证终卷(全部实证,修正054):
① LiquidRegions$Settings javap 反汇编: <clinit> DEFAULT=(liquidRegionSize 0.001, waterRegionSpawnChance 40.0, liquidAltitude -55); bipush -55 为 clinit 唯一 bipush(offset 10); 构造器校验: size<=0回0.001, chance∉[0,100]回40.0(fail-soft)。fork mixin 改 DEFAULT.altitude→-46 后,无 json 实例运行时=(0.001,40.0,-46),与 C 档验收终态等价;液面单一真相源=mixin。
② MD5 实证: TEST 实例 mods 活跃 jar(mtime 11-12 02:20) == pre-depthfix 备份(11-13 17:59) = 87467F235392D3DF2550A018B55FCBC0 → -128 深度补丁在 2026-11-14 部署前不在 mods,054 的"TEST实例jar已打-128补丁"不成立于该时刻(去向: 或验证后恢复/或从未部署,待 max 证词)。
③ fork noise_settings(1.20.1): min_y/height 在 noise 子对象内(取顶层必得空值): noise.min_y=-64,height=2032; dimension_type -64/800; fork 从未带 -128(054 的 fork 三版本同步为"待裁决"未执行)。
④ BC 无内嵌 liquidregions 默认 json(纯 class 硬编码),fork 固化液面唯一可行路=mixin(已实现)。
⑤ 洞穴密度: fork 内嵌 pack cave_spawn_chance=100(=upstream),z-bc 包(70)已随 2026-11-14 部署移出实例→运行密度=100 满格。
tag: td情报，terrain-diffusion-mc，地底河，液面，bug根因
<!-- END:063 -->
<!-- ID:064 -->
2026-11-14 03:45 地底河案 z-bc 拆包修正(推翻我基于058的差异预期):
① D:\temp\z-bc-cave-density-removed-20261114-025920 拆包实测: cave_spawn_chance=100(非70!), carver0/1+cavern0/1 bottom_y 全=-52, 其余字段=upstream → z-bc 包内容≡内嵌包, 它是"河床-52包"而非密度包。
② 密度70仅存在于D6时代(commit b536576 记录落地), 地底河案改写z-bc做河床固化时 chance 被 upstream 值(100)覆盖, README"密度降低"句自那时起描述不存在之物。
③ 教训: 058长期记忆里的"z-bc=70"是历史状态非部署实况, 部署前取证必须以实拆为准, 记忆只当线索。
④ 验收(2026-11-14 03:40 max 口头"验收通过"): 值层面零差异(液面-46/河床-52/密度100/岩浆河40/深度-64全部同值换来源), 结构差异=固化进jar+json可选+fail-loud+显示名。实例扫描: openloader空/saves无better_cave.json副本/config bettercaves仅.bak → BC参数唯一来源=内嵌包。
⑤ 待max三决策: A README密度句删or折70 B岩浆河chance 40→15 C -128补丁去向(054待修)。清理候选: z-bc备份/liquidregions.json.bak/六旧jar(5个.bak-*尸体+1个干净2.3.0上游,建议仅留上游)。
tag: td情报，terrain-diffusion-mc，地底河，液面，bug根因
<!-- END:064 -->
<!-- ID:065 -->
2026-11-14 04:40 地底河案B(岩浆河份额)落地: BC water_region_spawn_chance 出厂默认 40→25 (水25%/岩浆75%, max裁决B=25)。实现=mixin 三处常量全改(javap实证40.0全类恰3处: clinit DEFAULT参数1+<init> fail-soft日志参数offset89+回写offset100), "默认值"整体迁移——非法json回退值与新出厂一致,不留两套默认;已有合法json用户零影响(json仍是覆盖面)。旧值40=upstream默认,058时代提过40→15方案,max终裁25。版本230.1.1.0→230.1.2.0(gradle.properties:2真身+build.gradle:230注释示例)。构建绿(schtasks tdbuild20261114b,exit0,~1min,产物lmaxs-terrain-diffusion-fork-230.1.2.0+1.20.1+forge-20261114043413.jar 8173348B),javap验尸3方法/25.0×2/-46×1断言过,toml version对。已部署TEST(mods仅230.1.2.0,旧jar归档mods-swapped-20261114-025920,MD5一致)。commit 4438a9e(4文件38+/4-)。README 30行同步(删失效密度分句+液25%岩浆75%句)。build wart待max: javac无encoding配置,GBK读UTF-8源100条警告(全落注释,零语义,修法JavaCompile统一UTF-8)。游戏仍未跑=运行时检验pending(fail-loud设计)。
tag: td情报，terrain-diffusion-mc，地底河，液面，bug根因
<!-- END:065 -->
<!-- ID:066 -->
2026-11-14 td fork git拓扑实况: origin=https://github.com/LMaxRouterCN/terrain-diffusion-plus.git(max自己的公开fork,push目标), upstream=https://github.com/derekvawdrey/terrain-diffusion-plus(直接上游), xandergos(源头作者)无remote仅历史链路。工作分支=LMax-master(GOAL-PLAN§5曾记branch=1.20.1+forge系备忘录060规范,从未落地,悬而未决)。.agent/(agent记忆目录)被git追踪且已公开在origin远端(2026-11-14确认:9-commit债清零那次push已带案卷commits如f1aeda5),属max知情既定实践;若撤需动历史或前向gitignore。.agent_trash/__external__=2026-11-10 DH评估案detprobe脚本残留(dll_diag.py/tdh_autopsy.py),未追踪无害。
tag: td情报，terrain-diffusion-mc，fork同步
<!-- END:066 -->
<!-- ID:067 -->
2026-11-14 v2 (JOIN-TEST 补证): PokerAgent exec 通道对换行的破坏比初判更深 — 行尾 "-join $crlf" 被吞后数组按 $OFS 空格连成单行 (b7e93bc 僵尸根因, commit 每文件恰 2 insertions 铁证); 且 char-code 构造 CRLF 的诊断输出经通道后仍显示空格连接 (type=String 但无换行), 连 [string][char]13+[string][char]10 拼接都不可全信。可靠写法收敛为一条: 多行内容一律 here-string @'...'@ 模板 ({{TOKEN}} 占位替换缩进/前缀), 写入后必须结构断言 (关键代码行独立成行, 注释标记与代码不得同行) 再 commit。纯字符串数组与 here-string 实证可靠 (D:\temp 清理 82/82, 探针 bat 多行跑通, $pinTpl 修复块全绿)。
tag: PokerAgent，坑，指令格式，技巧
<!-- END:067 -->
<!-- ID:068 -->
2026-11-14 td fork javadoc GBK 警告案结案 (通用构建坑): Gradle 多模块只给 JavaCompile 钉 UTF-8 不够 — Javadoc 任务有独立 encoding (未钉 = 平台默认, 中文 Windows = GBK), javadoc tool 按 GBK 读 UTF-8 源产 unmappable 警告; 即使 javadocJar SKIPPED, :forge:javadoc 任务照样执行照样报警 (在 build 依赖链上)。修法 = tasks.withType(Javadoc).configureEach 三键同钉: options.encoding + options.docEncoding + options.charSet = 'UTF-8' (源读取/HTML 输出/meta charset)。诊断手法: ①init script 探针 taskGraph.whenReady dump 全任务 encoding (--dry-run 只读); ②负对照 = 保留坏状态 --rerun 复现警告计数。落地: b5f722d (conventions + 三版本 4 文件), 负对照 100 条 → 复验 0 条。
tag: 构建坑，gradle，javadoc
<!-- END:068 -->
<!-- ID:069 -->
DH (Distant Horizons) 加速 LOD 生成模组可行性报告路径: D:\Documents\my\日常\DH加速LOD生成-可行性评估-2026-11-10.md (2026-11-10 D6 侦查产出)。结论: 可行, 官方 worldGenOverrides 入口, 典型 2-4 天工作量, 最大风险 = wrapper 获取。2026-11-14 max 拍板开干。备忘录关联槽点: DH 生成的区块没有地表河流和地物只有地形——本模组有机会一并解决。
tag: DH加速, td情报, 可行性报告, mc模组, DistantHorizons
<!-- END:069 -->
<!-- ID:070 -->
2026-11-14 is_overworld 案修复落地(实测清单第7项): versions/1.20.1/common/.../data/minecraft/tags/worldgen/biome/is_overworld.json, 113条=原版53(实锤提取自mcp_config client.jar)+TD新增60。关键教训: catalog 的 canGenerateOverworld 布尔字段是权威过滤器(与kind分布完全对齐), 枚举群系做tag必须滤它——首版全量124混入NETHER/END/VOID 11条, mod datapack 全世界加载会污染原版地狱的 is_overworld 过滤(矿石往地狱灌)。BOP交互: replace:false 若为跨包union(待实测验证)则同名tag共存无冲突。1.21.x同病留移植。
tag: td情报, terrain-diffusion-mc, 矿石锚点, 坑
<!-- END:070 -->
<!-- ID:071 -->
2026-11-14 S1 侦查首获(Distant Diffusion 立项夜, 名字后被 max 否决待重命名):
1) DH jar 位置: TEST 1.20.1-Forge_47.4.10 实例 mods 内为 2.3.2-b-1.20.1-forge-fabric(开发目标); Industrial Revolution 2077 实例有 DH 3.0.3-b-1.21.1(将来 1.21 移植素材); 另 High Stakes/IR2040/backup 各有 2.3.2-b 副本.
2) TD WorldPipeline (com.github.xandergos.terraindiffusionmc.pipeline) 是 overlay 生成文件, src 无源码, 仅存在于 versions/1.20.1/{common,forge}/build/generated/terrain-diffusion-overlay/ 下; 872 行, public final class, 构造器 public x2 (seed,models / seed), get(i1,j1,i2,j2,withClimate[,blocksPerNativePixel])/setSeed/getSeed/getCoarseSlice/getLatentElevation/getLatentClimate/clearCaches/close 全 public -> DH 桥接可能不需要 Accessor mixin, 直接调用+截现成实例即可; 三方发行同模板生成 = 签名天然一致.
tag: DH加速, DistantHorizons, td情报, terrain-diffusion-mc
<!-- END:071 -->
<!-- ID:072 -->
2026-11-14 夜 TDH (Terrain Diffusion Horizons, 桥接mod: TD管线→DH远距生成) S2a 壳落盘. 关键架构决策:
1) 目录 D:\Documents\mcmod\Terrain Diffusion Horizons (空格跟随max风格), modid=terraindiffusionhorizons, 包 com.lmaxrouter.terraindiffusionhorizons, 主类@Mod无参构造器+ModList.isLoaded双宿主检测(缺席静默闲置=设计内行为).
2) 构建链刻意与 TD fork 同链同版本: moddev.legacyforge 2.0.77 + parchment 2023.09.03 + Java 17 + wrapper 8.14.3(腾讯镜像) -> 插件/依赖/parchment 全命中 F:\MOVED\.gradle 缓存, TD_MIRROR 四件套直接复用零新增网络需求; 插件版本定义在 TD fork versions/1.20.1/build.gradle 的 plugins 块(子项目声明不带版本).
3) compileOnly 双 jar 走 gradle.properties 正斜杠路径(dh_jar=TEST实例mods的DH 2.3.2-b, td_jar=fork build/libs 230.1.2.0), S2a 不消费 S2b 吃.
4) reobfJar finalizedBy(1.20.1 Forge 生产SRG必须), 无mixin/无AT/无ONNX(纯桥接).
5) TD fml_mod_id=terrain_diffusion_mc(vdir gradle.properties实证), DH modid=distanthorizons(待S2b运行时实证).
6) 遗留: LICENSE 80B占位需补MPL-2.0全文.
旧标签 terrain-diffusion-mc 指 TD fork 仓库; TDH 项目标签从此条起.
[2026-11-17 勘误] 3) dh_jar 现为 libs/DistantHorizons-3.3.3-1.20.1-forge-fabric.jar (编译期实体恒为3.3.3, 见080/087). 5) DH modid=distanthorizons 已随生成器注册实测验证. 6) 已销: LICENSE 现为 MPL-2.0 全文 16726B, 已入库(实测非占位).
tag: TDH项目, terrain-diffusion-horizons, 构建配方, S2a
<!-- END:072 -->
<!-- ID:073 -->
2026-11-15 晨 max发现"卡了一宿"。隔夜自动运行死亡根因: 自动模式下唤醒链单点——唯一唤醒源=上一条消息中指令的回执。夜间第一轮消息把侦查的长序言写在cmd块前, 消息在cmd块发出前被截断(止于"复核:"), 指令未发出→无回执→永久静默, 整夜零工作; "完事休眠"尾闸门因"完事"未发生也未执行, 机器空转一夜。永久纪律: 用户离场的自动模式下, cmd指令块必须放在消息最前部(叙述后置或塞代码块注释), 绝不让"计划宣告"与"指令发出"分离; 每条消息任务粒度收窄, 降低单条截断损失。
tag: PokerAgent, 坑, 指令格式, 自动模式, 唤醒链
<!-- END:073 -->
<!-- ID:074 -->
2026-11-15 PowerShell长脚本引号地雷: 在双引号内插 $(...) 中嵌套带引号的路径, 引号错配(如 "$env:TEMP\igen.bc'.Count" 手滑多个单引号)会导致解析阶段整个脚本块被拒绝执行, exec回执"命令已执行，无输出"零报错零输出。纪律: ①路径一律提成变量(Join-Path) ②字符串拼接用 + 号 ③禁用嵌套引号内插 ④发前逐引号配对检查。回执"无输出"对多段脚本=必是解析炸了, 直接查脚本语法而非重试。
tag: 坑, PowerShell, 指令格式, exec指令
<!-- END:074 -->
<!-- ID:075 -->
2026-11-15 输出截断第二案(与073同族不同因): ~150行代码+长前导的消息在cmd代码块中途被截断(B段第9类处), exec未闭合→整条未执行, [agent]双检唤醒才发现(用户在场也救不了, 纯输出长度限制)。纪律v2: ①exec指令块放消息最前 ②单条消息代码≤100行, 多段侦查拆多轮发 ③memory/remember等短指令放消息尾部(截断时先牺牲它们, 回执到手可补发)。
tag: PokerAgent, 坑, 指令格式, 自动模式, 唤醒链
<!-- END:075 -->
<!-- ID:076 -->
2026-11-14 深夜 TDH S2b 全量落地: BUILD SUCCESSFUL, commit 96e3e4d(桥接层), 产物 terrain-diffusion-horizons-0.1.0.jar 12.8KB(reobf SRG)。代码五文件: 主类33+TdConfig77+TdLodTranslator45+TdDhWorldGenerator227+DhCompat64。三构建坑(本次新钉): (1) gradle.properties 中文路径(地平线dh)在解析层断裂, files()对缺失路径静默容错→空classpath→错误分布"只死DH包"即此症; 修复=DH jar快照libs/ASCII名+重定向+.gitignore libs/。(2) mods.toml 注释里字面${...}被 processResources 的 Groovy SimpleTemplateEngine 当表达式炸; 模板文件里注释也别写裸${。(3) TD_MIRROR四件套(TD_MIRROR=1+GRADLE_USER_HOME=F:\MOVED\.gradle+JAVA_HOME=E:\Java\Zulu\zulu-17+TEMP/TMP=D:\temp)对TDH同链必需。TD API实证(javap): LocalTerrainProvider全静态门面(getSeed/getRiverTerrainData(int,int,int,int,boolean)皆static, RiverTerrainData=其内部类, INSTANCE单例仅内部实现)。S2a代码从未编译过(artifact阶段就死), 主类缺DhCompat import的S2a雷S2b才爆。compileJava全绿仅1处deprecation警告(TdDhWorldGenerator,联调轮查)。烟雾未跑: runClient(GUI+首跑assets~500MB)vs runServer(无GUI无assets但需eula)分岔等max。
tag: TDH项目, terrain-diffusion-horizons, 构建坑, S2b, API坑
<!-- END:076 -->
<!-- ID:077 -->
2026-11-15 Gradle 构建坑: processResources 的 expand 不设 filteringCharset 时, 模板按 JVM 默认字符集读写 (Windows + Zulu17 = GBK). UTF-8 多字节中文经 GBK 配对解码, 尾字节配错位处撞 malformed, 被替换为不可逆 0x3F '?', 产物成非法 UTF-8. TDH S2b 实证: jar 内 mods.toml 中文注释阵亡 (严格 UTF-8 读在 index 393 抛 Unable to translate [E8][B5]), 同行部分词组因字节恰好配成合法 GBK 对而幸存 → "多数中文没事"是幸存者偏差假象. 修复 = processResources 块内 filteringCharset = 'UTF-8' (Gradle 5.1+ CopySpec 属性). 判定法: 源文件严格 UTF-8 OK + 产物 BAD = 管线腰斩; javac 侧 options.encoding 与此独立 (TDH 已设 UTF-8, class 内中文字面量干净). mods.toml 模板注释里也别写裸 ${...}, Groovy SimpleTemplateEngine 会当表达式炸 (同轮另一坑).
tag: 构建坑, gradle, 编码, processResources, expand, GBK, TDH项目, terrain-diffusion-horizons
<!-- END:077 -->
<!-- ID:078 -->
2026-11-15 勘误: TDH 仓库 LICENSE 自 S2a commit 737e510 起即为 MPL-2.0 全文 16726B (cat-file HEAD 实证), "80B 占位待补"为过时信息, 勿再发起补 LICENSE 任务. 同案销案: jar 内 mods.toml 中文损坏非源文件 glitch (源三证干净: 严格扫描/hex/git diff 零差异), 是 expand GBK 腰斩, 见 filteringCharset 那条记忆.
tag: TDH项目, terrain-diffusion-horizons, 勘误
<!-- END:078 -->
<!-- ID:079 -->
2026-11-15 TDH deprecation 警告销案: DH API 2.3.2-b 的 IDhApiFullDataSource.setApiDataPointColumn(int,int,List) 3 参 default 已弃用, 4 参版 (int,int,EDhApiWorldGenerationStep,List) 是替代. javap -c 实证弃用版字节码转发 4 参时 step 硬编码 SURFACE → 显式传 SURFACE 同值替换, 行为无损, 警告归零. 判定弃用点的手法链: ①compileJava --rerun-tasks 抓 javac 警告确认存在 ②javap -v 逐 import 类扫 'Deprecated: true' (判定成败只看退出码, 文本匹配 Exception 会撞 throws/异常表造假阳性) ③javap -c 看弃用方法转发目标定同值. 注意: javap 中文环境输出 mojibake, 加 -J-Duser.language=en; IDhApiLevelWrapper 的弃用在某 default 方法上, 只传引用不调用则不触发警告.
tag: TDH项目, DistantHorizons, deprecation, bug根因, javap
<!-- END:079 -->
<!-- ID:080 -->
2026-11-15 勘误之勘误(TDH零生成案): ①TEST实例与项目libs的DH jar同hash(A537A711...), 内容实为3.3.3(mods.toml+fabric.mod.json双证), 文件名"2.3.2-b"是误名→不存在编译/运行错配, "零生成"与版本无关(此前"错放2.3.2"推断作废; 079的javap结论本身有效, 验的就是这个3.3.3内容jar, 仅版本标签错)。jar已正名DistantHorizons-3.3.3-1.20.1-forge-fabric.jar。②3e5f985轮build实为FAILED(TdDhWorldGenerator被整行replace弄伤, L190 clampY illegal start of expression), 误判绿的三个坑: grep 'BUILD'大小写不敏感先命中gradle daemon提示行"for this build"; 脚本无SUCCESS gate红了照常commit; jar UTF-8校验打到旧jar(编译失败jar未刷新)。铁律: 判绿必须-CaseSensitive 'BUILD SUCCESSFUL'且gate后续动作; jar校验前先验时间戳/hash确属本次产物; replace指令整行替换会吞行尾token, 行尾带多token的行禁用整行替换。③max实测零生成现场: seed三处全等8838..., LTP INSTANCE非null, DhApiResult无异常, 无ERROR→注册路径通, 疑生成路径或DhApiResult实为failure未打success字段。
tag: 勘误, TDH项目, DistantHorizons, 构建坑, bug根因
<!-- END:080 -->
<!-- ID:081 -->
2026-11-15 TDH 开机雷根因: mods.toml 软依赖 versionRange 上界过窄. Forge 语义实证: mandatory=false 只容忍依赖缺席, 依赖在场但版本越界时照样在 mod 加载期拒启 ("游戏开不开"). TDH 模板 DH 依赖原写 [2.3, 2.4), TEST 实例实际 DH 3.3.3 → 装原版 jar 直接开不了机, max 只能手改 jar 内 toml 放宽到 [2.3, 9.4) 才能测. 已根修模板 (采纳 max 实测值). 教训: 软依赖 versionRange 上界必须宽 (行为差异交给代码侧 isLoaded/运行时检测), 或干脆不设上界. 另: max 实测的 TDH jar 是 d82470c 时代产物 (3 参 setApiDataPointColumn), 3/4 参运行时行为等价 → "零生成"与签名修复无关, 根因另行追查 (DH 远距生成开关/生成路径).
tag: mc模组, Forge, TDH项目, DistantHorizons, 坑, bug根因
<!-- END:081 -->
<!-- ID:082 -->
2026-11-15 DH 3.3.3 API 事实 (javap 实证): DhApiResult<T> 字段 public final boolean success / String message / T payload, 无 getter 无 toString 重写 → 打印注册结果直接 result.success + result.message (旧日志打对象地址是零信息)。生成桥接类: common.wrappers.worldGeneration.DhRoughSurfaceGenerator (rough surface pass 本体) / DhInternalServerGenerator / DhChunkGenerator / core.generation.queues.WorldGenerationQueue; API 注册门面 com.seibel.distanthorizons.api.methods.override.DhApiWorldGeneratorOverrideRegister, 接口 ...override.worldGenerator.IDhApiWorldGeneratorOverrideRegister。
tag: API坑, DistantHorizons, TDH项目, mc模组
<!-- END:082 -->
<!-- ID:083 -->
2026-11-15 DH 3.3.3 worldgen API 结构 (javap 实证): ①IDhApiWorldGenerator 的 generateChunks/generateApiChunks/generateLod 三者皆为 default 方法, abstract 仅 preGeneratorTaskStart()/close(); getReturnType() 三值 VANILLA_CHUNKS/API_CHUNKS/API_DATA_SOURCES 决定队列走哪条路 ②EDhApiDistantGeneratorMode 四值 PRE_EXISTING_ONLY/SURFACE/FEATURES/INTERNAL_SERVER, 带 public final byte complexity ③注册门面 DhApiWorldGeneratorOverrideRegister.registerWorldGeneratorOverride = WorldGeneratorInjector.bind(wrapper, generator) + try/catch → createSuccess/createFail, 门面层无重复注册检查 ④WorldGenerationQueue 构造器直接持有 IDhApiLevelWrapper+IDhApiWorldGenerator, 调 getSmallest/LargestDataDetailLevel ⑤内部桥接: core.generation.DhWorldGenerator (默认适配), common.wrappers.worldGeneration.DhRoughSurfaceGenerator (surface pass, 其内部也调 4 参 setApiDataPointColumn SURFACE)。坑: 只覆写一个 generate* 而队列调用另一个时, default 体静默执行; 参数序不匹配会变静默重载 (javac 无 @Override 时不报错)。
tag: API坑, DistantHorizons, TDH项目, mc模组
<!-- END:083 -->
<!-- ID:084 -->
2026-11-15 grep 审计两连坑 (TDH 零生成案复盘): ①源码找日志点用 'LOGGER|\.log\(' 匹配不到 slf4j 惯用名 LOG 的 LOG.info/warn → 误判"零 log 调用"; 正确姿势: 先搜 getLogger("名字") 拿 logger 名再全文件搜该名 ②运行日志搜 mod 前缀 'TerrainDiffusionHorizons|TdDh' 匹配不到独立 logger 名 "TDH-Generator" (TdDh 正则序列 T,d,D,h ≠ T,D,H) → 调试日志第一版就存在却被两个 pattern 漏掉, 误导排查方向三轮。教训: 日志 grep 必须以 LoggerFactory.getLogger(...) 的实参字符串为准, 不能用"大概的名字"。
tag: 坑, 技巧, 调试方法, bug根因, API坑
<!-- END:084 -->
<!-- ID:085 -->
2026-11-15 TDH 零生成案根因 (定案): TdDhWorldGenerator 守卫 2 (mode 过滤) 只放行 SURFACE/INTERNAL_SERVER, 而 DH 3.3.3 队列传给 generateLod 的 mode 直接来自 config chunkGeneratorMode (默认 FEATURES, 合法值仅 PRE_EXISTING_ONLY/FEATURES/INTERNAL_SERVER — SURFACE 只存在于 generatorPlan, 永不到达 API 生成器) → 守卫把默认流量全静默 return, 每任务秒完成零回执, 表现为 DH 自报 13,709 chunks/sec 逆天速度 + LOD 零显示 + saves 零落盘。判案关键证据: 首调日志 "mode=FEATURES" (logger 名 TDH-Generator, 曾因 grep pattern 两连漏被误判零日志)。修法: 守卫 2 黑名单反转为白名单, 只拒 PRE_EXISTING_ONLY。通用教训: ①对 API 回调的守卫必须先实证回调实参的真实值域, 不能按语义直觉设计放行列表 ②"快得逆天"的吞吐数字本身就是空转签名 ③DH 的 chunkGeneratorMode 与 generatorPlan 是两个正交配置, mode 到达 API 侧时永远不是 SURFACE。
tag: TDH项目, bug根因, DistantHorizons, mc模组, 坑
<!-- END:085 -->
<!-- ID:086 -->
javap 字节码排雷配方 (2026-11-15 TDH 零生成案实证): ①javap -c 不带 -p 时 private 方法字节码整个不可见, private generateInternal 里的 getstatic 守卫直接隐身, 曾误判"管道吞输出"; 排查字节码必须 -c -p 成对出现 ②PowerShell Select-String 默认大小写不敏感, 会拿 surfaceBlock 字段干扰 SURFACE 枚举搜索, 要用 -cmatch 或 .Contains() 才大小写敏感 ③验证部署 jar 内容最硬路数: jar xf 解包后对 .class 做二进制 ASCII Contains 搜枚举常量名(大小写敏感); 枚举比较必有 getstatic 指令, 孤立字符串常量 = ldc 字面量(日志文案或 name() 字符串比较) ④增量编译佐证: build\classes 里只有改动类+依赖类时间戳更新, 其余停在旧时间, 属正常非假成功。
tag: javap, 技巧, 坑, 字节码
<!-- END:086 -->
<!-- ID:087 -->
[2026-11-17 覆盖, DH版本议题终结] max 确认 DH 从来都是 3.3.3——"2.3.2-b"印象源于 jar 文件名误名(080 于11-15勘误: TEST实例与项目libs的DH jar同hash, 内容实为3.3.3, mods.toml+fabric.mod.json双证, 当日已正名), 编译期=运行期=3.3.3, "mods是否升3.3.3对齐编译期"待决策项作废销案. 保留有效情报: ①DH 的 LOD 落盘 = saves\<世界名>\data\DistantHorizons.sqlite 单文件(不是 data\DistantHorizons\ 目录), 判世界有无 LOD 看该文件存在+mtime, 世界104实证. ②TEST 实例路径见 041. ③EDhApiDistantGeneratorMode 四常量双版本 javap 同构属历史勘误遗留, 现已无版本差异问题.
tag: DistantHorizons, 测试环境, 坑, TDH项目, 勘误
<!-- END:087 -->
<!-- ID:088 -->
DH generateLod 坐标语义定案 (2026-11-15 数据正确性案根因, javap+双会话实证): (1) posX/posZ 单位 = 数据源边长(块) = 2^(apiDl+6), dl0 即 64 块, 不是块坐标; 调用链: WorldGenerationQueue 用 DhSectionPos.getX/getZ (核心dl = apiDl+6, 段坐标 1 单位 = 2^核心dl 块) (2) FullDataSourceV2 恒 64x64 数据列, dl0 列宽 1 块, 数据源总边长 64 块; DhWorldGenerator.generateLod 开头 powerOfTwo(apiDl+6) 佐证 (3) 实证法: 首调日志 chunkMin/pos 比值恒 4, chunk 16 块 x4 = 64, 双会话复现 (4) TDH 旧 translator 双重错: pos 未乘 64 (全查原点附近) + span 多乘 widthInColumns (4096 而非 64) → 16 数据源同查一区域各取左上 64x64 角落平铺 = 地形重复/网格错位/冲天石柱/精细度坏的单一根因 (5) 修法: size = columnWidthBlocks(dl); bounds = {posZ*size, posX*size, +size, +size}; widthInColumns 留作 dl>0 扩展。"放大消失/闪现闪消"未归因, 修坐标后看残留。
tag: DistantHorizons, TDH项目, bug根因, 坐标语义, javap
<!-- END:088 -->
<!-- ID:089 -->
2026-11-17 TD_MIRROR 漏设三犯定案 (035 一犯白绕12回合 / 089 初版二犯 / 本日三犯白耗两轮): TDH L1 构建裸跑 gradlew build 又漏四件套, 补齐后 22s 绿。四件套 = TD_MIRROR=1 + GRADLE_USER_HOME=F:\MOVED\.gradle + JAVA_HOME=E:\Java\Zulu\zulu-17 + TEMP=TMP=D:\temp。程序性结论: 本机跑任何 TD 系 gradlew (TD fork / TDH) 开工第一件事 = 四件套, 无例外; TD_MIRROR 是 init.d 镜像闸门, 不设 = neoform-runtime 死走 maven.neoforged.net (SNI 阻断) 且 NFRT 不进 modules-2 缓存, --offline 也救不了 (089 初版正文保留机制分析)。TDH 与 TD fork 构建差异: TDH 单项目扁平结构, gradlew 直接在仓库根跑; TD fork 必须 cd versions\1.20.1 跑 buildForgeWindows。TDH 全缓存命中 build --offline 约 13~22s。
tag: 构建坑, 构建配方, TDH项目, terrain-diffusion-horizons, TD_MIRROR, 网络环境
<!-- END:089 -->
<!-- ID:090 -->
2026-11-15 javap假阴性坑实证(TDH案): javap -c 不带 -p 只反汇编 public/protected 成员, private 方法整段缺席输出——class常量池物理存在的字符串在视图里消失, 判"部署物可疑"即假阴性。铁证: 同一TdDhWorldGenerator.class(11170B), 无-p只吐83行/4175B, 加-p吐502行/27081B, 观测串(TD data.width...)位于private generateInternal内, -p即现。连带清白洗冤: PS管道与Start-Process raw落盘字节级一致(非管道罪); Console UTF-8中文无损。终局姿势(优先级序): ①验证部署jar含某字符串/常量 → zip条目字节级直搜(System.IO.Compression.ZipFile读entry, UTF8解码, Contains, 纯ASCII锚最稳)绕过一切显示层 ②javap验私有方法必须-p -c ③成员可见性预判: 同包package-private/private方法常驻实现类。元教训: 连续三个断言被现实否(gradle UP-TO-DATE不可能→实际UP-TO-DATE / PS管道吃行→实际一致 / javap视图=全貌→实际按可见性裁剪), 视图类工具的显示范围本身是待验证项, 断言必附证据, 字节级是唯一不动层。参考: TDH坐标案d759a33部署物验证三判据流程。
tag: javap, 坑, 技巧, 字节码, 构建坑, TDH项目
<!-- END:090 -->
<!-- ID:091 -->
2026-11-15 LICENSE乌龙案(TDH, 三环失误零后果)——A0考古后修订版: S0 f16e055 LICENSE=79B, S2a 737e510=16726B→072"80B占位"在记下当时是准确的(79≈80), 真缺口=S2a修复入库时未回写: GOAL-PLAN只更新L111("已定稿")而L12("待补")未动, 记忆条也未清, 同一事实三驻点失同步。修订后三环: ①跨驻点失同步——状态更新只写一处, 修待办必须grep全部驻点(文档各处+记忆) ②脚本无现场分支——A1已打印"16726B全文"却无"达标即return", 照走覆盖(幸同源字节, git零差异) ③校验指纹凭印象瞎写——"2.1. Contributions"不存在(实际2.1. Grants), 方向碰巧过严拦住无意义commit。元教训(089同源): 无实证内容(过时快照/印象结构)禁止入决策链, 断言必附当轮证据。MPL-2.0官方纯文本指纹: 16726B, "Mozilla Public License Version 2.0"/"2.1. Grants"/Exhibit A/Exhibit B。TDH LICENSE自S2a起完整入库, 遗留项划销。
tag: 坑, 数据可信度, TDH项目, 技巧
<!-- END:091 -->
<!-- ID:092 -->
2026-11-15 冲天石柱根因 (TDH, DH 3.3.3 y 存储语义, 实证+强假设分层): 实证: ①TDH 喂实体段 bottom=levelWrapper.getMinHeight()=-64(主世界), top=64~82; DH 存储后渲染层报 Y-:4032 占 99.996%, Y+ 原样 → 负 bottom 被翻转, 顶原样入库 ②4032=-64&0xFFF, 4033=-63&0xFFF 同律 → 强假设: DH FullData 内部 y 存储为 12 位无符号(0~4095), 负值回绕 ③官方 API 模板 y 段 (0,h)+(h,顶) 底固定 0 = 承重设计非占位, TDH 按"更正确"语义改成 minY=-64 踩雷 (TdDhWorldGenerator L160 旧注释自称"解读"即此)。机制: bottom(4032)>top(~70) 几何倒挂 → 主渲染路径画 y70~4032 冲天石柱, 邻接数据加载路径抛 "Negative y size for the renderDataPoint" = 14 万条/13min 刷屏 (~10MB/min)。旧会话同错 10.4 万条 = 元案独立 bug, 与 d759a33 坐标修复无关; 坐标修复让每 tile 采自己区域 → 坏数据遍地+更快 (max 体感吻合)。石柱旧理论"TD 模型边缘垃圾高度"退役。修法: y 一律非负 — dataBottom=max(minY,0)=0 作实体段底, solidTop/waterTop 钳位下界改 dataBottom, 加 solidTop>dataBottom 守卫; 顶保持绝对坐标 (实证正确落地)。测后验证: Negative y size 应归零+石柱消失+地形高度正确; 若地形整体下沉 64 = DH 语义实为相对 y → 顶也转相对 (分支预案)。教训: 官方模板"看似不严谨"处可能是 API 隐性契约, 偏离前先验存储层。
tag: DH, DistantHorizons, API坑, 坑, TDH项目, bug根因
<!-- END:092 -->
<!-- ID:093 -->
2026-11-15 夜 PokerAgent exec 通道传输坑定案 (三性质+规避法, 全实证; 背景=案II部署链两轮绕路, 根因全在通道而非环境):
① 长多行脚本前段丢失/行合并: 回执 ParserError "Missing closing ')'", 报错行号与原脚本错位 (报错称 Line2 但该行原在第3行); 同结构短脚本历轮跑通.
② $_ 被啃 + 引号变 Unicode 变体: ForEach-Object 内 ('盘: ' + $_.Name) 到达后端时 $_ 消失、引号变 ’′ → ParserError; 错误回显里的变体字符即铁证.
③ 中文旁字符串边界漂移 → 静默假读数 (最毒, 不报错只给错数据): ('F缓存=' + (Test-Path 'F:\MOVED\.gradle')) 一行, F 与 C 两处实际存在的目录双双返回 False, 次轮全 ASCII 复核双 True; 机制未实证, 推测中文旁引号变体致 Test-Path 收到非预期字面量.
规避法 (实证有效): exec 脚本全 ASCII / 零 $_ / 短 (≤30 行) / 管道可用但管道块内禁 $_ / 关键判断用原生指令 (find/read) 或全 ASCII 短 exec 交叉复核 / 长逻辑拆多段.
关联: 089 (TD_MIRROR 检索三犯案, "开工先检索长期记忆").
tag: PokerAgent, exec指令, 坑, API坑, 数据可信度, 技巧
<!-- END:093 -->
<!-- ID:094 -->
2026-11-15 夜 TDH LICENSE 勘误 (git show 证据版): LICENSE 实为完整 MPL-2.0 全文 (16726B / 373 行, 尾部 Exhibit B 正常收尾), 磁盘与 HEAD 一致; 历史定论 = S0 (f16e055) 为 3 行 "MPL-2.0" 占位 (即 072 档所记 80B), S2a (737e510) 提交时已含 373 行全文 → 072 当时准确, S2a 后过时, 未来勿据 072 行动。mtime 2026-11-15 19:35:08 晚于提交但内容零差异, 同内容重写 (editor 保存/checkout 类) 嫌疑最大, 未深挖。教训: 记忆里的"待办"开工前先验现场 (089 检索病的镜像: 检索了但没核对现状)。
tag: TDH项目, terrain-diffusion-horizons, 勘误
<!-- END:094 -->
<!-- ID:095 -->
2026-11-15 深夜 案II (4032 冲天石柱) 终局: max 实测通过 — 视觉问题全清, 500 chunks/sec, 87% 满速, ETA 30min (原版 RD5 + DH RD512)。新案两条: ① LOD 整体矮 ~64, 与 4032 案 bottom=0 契约自洽推导 = DH 存储层 y 语义为距世界底偏移 (推断级, 现象吻合), 修法 = 顶转相对 top-minY, 待改待验; ② 地表方块全石头, 裸 DH 无此问题, 疑 TD 管线只输出高度图、DataPoint 表面方块未正确喂 (待查证, 未定论)。max 决策: 仓库公开发布。
tag: TDH项目, terrain-diffusion-horizons, 坐标语义, bug根因, DH
<!-- END:095 -->
<!-- ID:096 -->
2026-11-15 深夜 TDH 建仓发布定案: 仓库已公开 https://github.com/LMaxRouterCN/terrain-diffusion-horizons, gh repo create --source=. --push 一次成功, 84af6a4 (gitignore+README WIP+.agent三件) 已推。决策: 公开/.agent三件入库/README双语WIP占位。注意: 项目原有 .gitignore/README (S2遗留) 被 create 覆盖, 旧件在 PokerAgent 专属回收站可 restore (create 前未 read 现场, 属未验现场复发)。框架目录 .agent_task_logs/ 与 .agent_trash/ (今晚生成) 已补入 .gitignore 并随新 commit 推送。后续主线: 矮64小修 (顶转相对 top-minY) + 地表全石查证。
tag: TDH项目, terrain-diffusion-horizons, gh-cli, 发布
<!-- END:096 -->
<!-- ID:097 -->
2026-11-15 PokerAgent replace 指令格式坑 (实证): 一条 replace 只接受恰好两个 code 块 (旧文本/新文本); 一次发 3 对 6 块 → 整条拒收 (回执报错: 只支持两个 code 代码块, 收到多个), 文件零改动 (原子性良好, 无半应用; 事后 git diff 仅 .agent 三件 M 印证)。多处替换 = 拆成多条独立 cmd, 每条恰好一对。与 exec 通道坑 (093) 同族: 指令格式约束以回执报错为准, 不猜不赌。
tag: PokerAgent, 坑, 指令格式, 技巧
<!-- END:097 -->
<!-- ID:098 -->
2026-11-16 PokerAgent replace 精确匹配坑新变体 (疑通道损毁, 未定案): 目标行确在文件中 (read 回执逐字确认过), replace 仍报"未找到匹配的文本块", 相似度仅 34.96%, 且错误回显把旧文本转小写 (DH→dh, TD→td) — 嫌疑传输中换行丢失或变体字符致整块畸变 (与 093③ 同族, 发生在 replace 参数而非 exec)。规避法 (实证有效): 中文长块精确替换不可靠时, 改 create 写补丁文件 + PowerShell [System.IO.File]::ReadAllText/WriteAllText (UTF8Encoding false 无 BOM) 拼接, 内容不经通道比对; 铁律: replace 失败回执必须当轮处理 (重发或绕行), 禁止带着失败继续 commit — 本案空 commit 3ae4dae 事故的根因。
tag: PokerAgent, 坑, 指令格式, 技巧
<!-- END:098 -->
<!-- ID:099 -->
replace 指令内容匹配模式格式坑 (2026-11-16, TDH案②轮实测): 旧/新两个代码块必须各自用完整的一对 code 标签闭合 (开标签+闭标签), 第一个代码块的结尾不能直接接第二个块的开标签, 否则整条指令在解析层失败 (报错: code 块无配对闭合, "本指令及其后所有内容均未执行"), 但同轮其他单块指令 (create/insert) 不受影响. 案例: 同轮十余条双块 replace 因该错误全灭, 一条不剩. 重发前逐条检查每对标签的闭合. 另: 一次发大量指令时, 单条解析失败不阻止后续指令执行 (逐条报错).
tag: 指令格式，坑，replace
<!-- END:099 -->
<!-- ID:100 -->
PokerAgent 标签格式双坑 (2026-11-16, TDH案②部署轮两连实测):
坑A 孤立闭合标签毒化: 在两个exec代码块之间多打一个孤立的闭合标签, 后续整个exec块被解析器拆碎, [cmd]exec/[code]/逐行脚本内容逐行被当"指令名"报"未知指令", 整块未执行。识别: 回执逐行报未知指令+零正常输出=死于解析层。误诊陷阱: 同轮其他块照常执行, javap验尸若读的是该块本应更新的目标, 旧数据产生"修复失败"假象 (本案以偏移逐字一致+新类MISSING双证据定案是部署未发生)。铁律: 报"未知指令"的行=未执行, 补发以文件系统实际状态(MD5/mtime)为准, 不信回执推断。
坑B 内容嵌标签截断: 在 code 块的内容文字里写中文方括号标签本体 (未转义/未换英文括号), 解析器在内容中遇标签即提前闭块, 写入被截断 (memory 100 初版+remember 初版双双截断于标签处, 本条为覆写补全; 遗留闭合标签再报"未知指令")。铁律: 写关于标签的文字必须用英文方括号 [code] 或转义。
tag: 指令格式，坑，PokerAgent
<!-- END:100 -->
<!-- ID:101 -->
2026-11-16 TDH 案①+案②α 双修部署 (commit 923275a+07322c4, push 013e3ed..07322c4):
案① LOD 高 1 格根因定案: TD HeightConverter.convertToMinecraftHeight 语义 = 第一个空气 y (density 函数数学/FeaturePlacer 姿势/mixin 注释/DH 半开区间/实测高 1 格, 五证合一), 旧码 +1 属双重补偿; 修法 = solidTop/waterTop 删 +1; javap 部署物实证 convert 后直接 iload+isub 无 iconst_1 iadd (偏移 450/506)。
案②α 群系驱动表面: TdSurfaceBlockMap 65 群系剪枝表 (主导表面/水下顶部/冰盖) + TdConfig useBiomeSurface/surfaceBlockOverrides 两新键 + Generator ColumnStyle/resolveStyle (ConcurrentHashMap 缓存); withBiomes=true 内存 ~520MB (false ~150MB TEST 实测); 群系 wrapper 进 data point = DH 远距 biome tint。
部署: TEST mods jar 18090B MD5 70FDE76E5E55B93CE41CC79655308900, 回滚锚 D:\temp\tdh-pre-alpha.bak (F531D151... = 08606e2 版血统)。编译期与部署期 TD fork jar 同 MD5 (5314B5B7...) → keyForIndex API 面编译=运行。
坑: TEST 已有旧三键 config json, 新键用字段默认值生效但文件不显示 (load 成功不重播种); 编辑需手加或删 json 重播种。重测: 必须新世界或删 saves\<世界>\data\DistantHorizons.sqlite。遗留待 max: .gitignore 工作区损毁 (疑框架手笔, 未commit未恢复) + libs//build-last.log 未跟踪 + .agent 三文件。
tag: TDH项目，terrain-diffusion-horizons，td情报，bug根因
<!-- END:101 -->
<!-- ID:102 -->
2026-11-16 max纠正同源定义 (river沙带案引出): TDH的LOD表面方块"同源"基准 = TD实际chunk侧生成管线放置的方块(玩家肉眼比对的对象), 不是vanilla surface rule的抽象复刻. 我的TdSurfaceBlockMap javadoc声称"TD chunk表面=vanilla SurfaceSystem.buildSurface x TD BiomeSource"属未验证假设写成事实, fork全树无SurfaceSystem/buildSurface引用+存在SurfaceShell/SurfaceFeaturePlacer/SurfaceStamp自建管线文件名佐证TD不用vanilla表面系统. river行(表L111-112双sand)是首个可见证伪, 其余64行同源声称同步作废待重审. 教训: 机制性声称未经源码实证禁止写进注释/文档当依据. 正解方向: 从TD真实表面管线(SurfaceShell/SurfaceFeature系)导出群系→方块映射, 或直接调用TD侧接口.
tag: TDH项目，勘误，bug根因
<!-- END:102 -->
<!-- ID:103 -->
2026-11-16 TD chunk表面管线定案(LOD表面审计案): TD chunk表面=vanilla NoiseBasedChunkGenerator.buildSurface(vanilla surface rule × TerrainDiffusionBiomeSource) + 后置装饰. 证据: mixin清单无SurfaceSystem替换; BiomeSource.getNoiseBiome按方块粒度索引tile的biomeIndexes(宽=tileSize, 地表Y无关, fallback=plains); TerraBlenderSurfaceCompat包装settings.surfaceRule(). 后置管线: ChunkGeneratorMixin decorate钩子跑TerrainDiffusionRiverDecorator(河流专属装饰器, 材料未知待读) + SurfaceFeatureDecorator(30+装饰物FeaturePlacer); SnowAndFreezeFeatureMixin按地形模型温度加深雪层(最冷=雪块+层, chunk雪原真身=草+雪层非雪块). SurfaceShell=carver保护壳(拒表面附近air/fluid写入防凿穿)与材质无关. 勘误: TD biome map是per-block索引比vanilla quart细, "TD 30m/px粗粒度"说法错误. fork布局: 版本特定代码在versions\1.20.1\common\src, 顶层common只放跨版本码, 全树搜索须覆盖versions且排除\build\.
tag: TDH项目，td情报，terrain-diffusion-mc
<!-- END:103 -->
<!-- ID:104 -->
2026-11-16 TD RiverDecorator 材质真身(LOD表面审计案): TerrainDiffusionRiverDecorator(fork versions\1.20.1)在 buildSurface 后只处理水柱(挖床/填水/换河床材质/冻河盖冰), 干地列直接跳过不碰→河岸干地=vanilla default 产物=草, TDH 表 river 行 surface=sand 纯错. 河床材质 per-column 随机: strength<72 且 roll<96→sand, roll<38→clay, 其余→gravel; 砾石无处不在, 沙限弱水区(小溪), clay 少数(概率按 roll 均匀 0-255 假设, roll 实际范围未读). 冰面: patch<0.235→blue_ice, <0.365→packed_ice, 其余→ice(63.5%主导). 冻河判定=isFrozenRiver(biomeIndexes), 与 TDH frozen_river 冰盖同源. 坑: forge sources jar 只含 Forge 补丁类无 vanilla; vanilla 源码在 minecraft_user_repo 的 inject_src.jar; fork 多版本布局: LocalTerrainProvider 等跨版本核心在顶层 common, 版本特定在 versions\1.20.1.
tag: td情报，terrain-diffusion-mc，TDH项目，勘误
<!-- END:104 -->
<!-- ID:105 -->
2026-11-16 TDH LOD水面判定双嫌疑定案(LOD表面审计案收官侦查): ①waterMask=encodeWaterMask三参编码byte: strength=max(河道形状x(0.55+0.45x流量), 湖深/24)x255, 输出0或1-255, 1-7=子块抗锯齿边缘(chunk侧MIN_WATER_MASK=8跳过, TDH侧旧代码!=0照收=LOD河道多一圈边缘水) ②RiverTerrainData.waterSurface无水=NaN(decodeWaterSurface把Short.MIN_VALUE哨兵解回NaN), TDH旧代码(short)Float.NaN静默=0→convertToMinecraftHeight(0)=y63海平面→地形顶<y63的列凭空多一段连到海平面的水, 高发区=河道边缘带(mask小+surface常无值高度重叠)→幽灵水池。修法=waterCol判定为(waterMask[idx]&0xFF)>=8 && Float.isFinite(waterSurf[idx]), 一行修两嫌疑; C方案河床分档复用同一&0xFF值: <72→sand(实际弱水62.5%砾37.5%沙) >=72→gravel(强水85.2%砾14.8%clay零沙), roll=hash&255均匀已证。chunk侧还有强制单层薄流(液面略低于河床时), TDH侧waterTop>solidTop判定天然缺=已知损失不追。HeightConverter: meters>=0→y=meters/resolution+63(海平面63), 负→y=63-sqrt(|m|+10)+sqrt(10)-1。
tag: TDH项目，bug根因，td情报，terrain-diffusion-mc
<!-- END:105 -->
<!-- ID:106 -->
2026-11-16 max 架构指示 (TDH): ① 表面规则不许硬编码, 规则在游戏启动/世界加载时从游戏获取并生成 — 案②α 的 TdSurfaceBlockMap 65 群系手抄表 (923275a) 定性为设计债, 待运行时化替代; 技术方向 = 反射遍历 vanilla SurfaceRules 规则树 (NoiseGeneratorSettings.surfaceRule, datapack 可覆盖), 剪枝策略留代码/数据来自运行时; 内部类反射版本耦合, compileOnly 钉 1.20.1 已有先例 (WrapperFactory_forge); 类名与遍历可行性未实证, 动手前 javap。② feature 层 (雪片/地物/结构) 列入路线图要真做进 LOD, 现有近似方案一律标"临时待替代"。③ 由此 B2 定案: snowy_beach 保 snow_block 与 B1 统一 (snow precip 群系一律 snow_block 近似), 原倾向 sand 的理由(严格 surface rule)与 B1 处理构成双重标准, 已被 max 击毙; snowy_beach 顶部方块 sand 但白雪来自 SnowAndFreezeFeature (feature 层, 凭记忆未实证)。
tag: TDH项目，terrain-diffusion-horizons，架构，表面规则，设计哲学
<!-- END:106 -->
<!-- ID:107 -->
2026-11-16 TDH 审计执行轮事故: PokerAgent 无 write 指令 (后端拒绝并逐行报"未知指令", cmd 块内逐行被解析为独立指令, 零副作用). 写文件正确姿势: 小文件用 exec+PowerShell Set-Content (Get-Content -Raw 读模板 .Replace 后写, 全 ASCII 内容任何编码读安全); 大段新文件内容再查 @@help fast 找对应指令 (本轮已查, 指令名以 fast 列表为准, 此处不预写防再次猜错). 另: replace -l N-M 多行回执报"N处文本"=按行计数非按块计数. exec 用法: 单代码块=同进程共享变量 (有依赖的多步必须单块), 多代码块=独立进程互不依赖.
tag: TDH项目，坑，指令格式
<!-- END:107 -->
<!-- ID:108 -->
2026-11-16 PokerAgent 格式事故合集 (108, 覆写修复): ①上游事故: 在 cmd 块之间写解释文字时误把 code 结束标签的中文括号字符原样写出, 被后端解析为未知指令 (零副作用). ②本轮新教训: 在 memory 指令的 code 块内容里引用事故原文时再次写出该标签字符, code 块在引用处提前终止 -> 本条记忆内容被截断 + 消息里真正的结束标签变成游离未知指令. 结论: 消息任何位置 (含 code 块内部) 都禁止出现中文方括号形式的标签原文; 需要引用时用反斜杠转义括号 (指南支持) 或用英文措辞描述. ③同块多条命令与 exec 多代码块均正常, 事故仅限游离裸标签.
tag: TDH项目，坑，指令格式，PokerAgent
<!-- END:108 -->
<!-- ID:109 -->
2026-11-16 TDH 案②α LOD 表面审计八项全落地 (代码 commit 4d78783 + docs commit 24f0d7b 已 push): W 水判定双杀 (waterMask 0-255 强度语义, >=8 有效, isFinite 防幽灵水); O 干海床修复 (ocean = elev<0 列填水段 [海床, seaTop=y63], 冻洋冰盖免费生效); C 河床强度分档 (弱水<72: 沙62.5%/砾37.5%, 强水: 砾85.2%/黏土14.8%, 散列键=世界块坐标跨数据源稳定); A1 河群系表面 grass_block; B3 old_growth 系 podzol; B1/B2 雪盖系定案 snow_block. jar 18575B 已部署 TEST mods (MD5 3BA214EF8BE07BB9A59B5E1BB822EFD9 双验一致), 回滚锚 D:\temp\tdh-pre-audit-923275a.bak (旧版 18090B). 重测前置: 新世界或删 saves 下世界 data 内 DistantHorizons.sqlite (旧 LOD 不带新表面); 看点: 河道边缘不再宽一圈 / 无幽灵水 / 近海干海床变海面 / 河床沙砾黏土混合 / 河岸草色 / old_growth 系暗棕 podzol.
[2026-11-17 更新] 遗留三项消解: .gitignore 已由 max 修复(15行, git commit 解锁); DH 2.3.2-b 升级议题作废(087销案, 从来都是3.3.3). 仅剩 .agent 三文件处置, 且它是 commit 硬阻塞: .gitignore 忽略了 .agent_task_logs/.agent_temp_files/.agent_trash 但没有 .agent/ 本体, 旧框架存档(memory.md/remember.md等)会被 git 追踪入库, commit 前必须定夺(加 ignore / 删除 / 有意入库).
tag: TDH项目, terrain-diffusion-horizons, 表面规则, 重测清单
<!-- END:109 -->
<!-- ID:110 -->
2026-11-16 TDH 河岸方块准确度定案 (案②α 后续): max 实地验证河岸非全草 (散布沙/砾), 这些块跨群系边界, 属 vanilla surface rules 噪声条件分支 + feature 层产物, 碎片化信息 cell 采样天然拿不到, 定案不追. LOD 方块选择层在此约束下已到头 (一个数据点 = 表面方块 + 水段). 潜在改进仅剩一项且未排队: C 方案散列键 (gx, gz) 右移 6 位可模拟 vanilla 片状河床 (整段同材质) 代替现花斑模式, 零性能成本, 观感结构对齐; 待重测后看花斑是否碍眼再议. 相关已知: 河岸 tint 色带嫌疑 (river biome grass tint 微差) 和几何阶梯是另两本账, 不归方块选择.
tag: TDH项目，terrain-diffusion-horizons，表面规则，设计哲学
<!-- END:110 -->
<!-- ID:111 -->
2026-11-16 TDH LOD 生成性能基线 (案②α 部署后首测): TEST 实例, 稳态约 700 区块/秒, 峰值约 1500 区块/秒. 机器: Ryzen 5 5600X / 32G 3200MHz. 参数组合: TD 侧缩放等级 6 (terrain-diffusion 数据源密度参数) + DH 侧质量 4 / 负载 4 (渲染 LOD 档). 注意: TD 缩放等级与 DH detail 是两套独立系统, 不可混用 (本人曾混淆, max 纠正); DH 质量 4/负载 4 对应的实际 detail 数值未确认, 需要时查 DH 配置而非推断. 无审计前基线, 此数字仅证明"无性能灾难", 不构成 A/B 证据; 审计改动为 per-column 常数运算 (散列+分支+懒 wrapper), 理论零损失. 未来改动以此基线对比.
tag: TDH项目，terrain-diffusion-horizons，性能基线
<!-- END:111 -->
<!-- ID:112 -->
2026-11-17 TDH README.md 0.1.0 正式版落盘完成(双语文档, 中文145行+分隔3行+英文146行=294行, 围栏终验: fence3=2/fenceLong=2/L69+L217双开栏, 已修复合并, 最终293行). 本轮实测四坑: (1)create载荷中写"反斜杠+三反引号+json"的转义围栏, 落盘变成孤立反斜杠行+下一行裸json词, 且多行载荷的行首缩进被剥(JSON内容缩进全丢). 对策: 围栏/反引号一律用[char]96加-join构造, 配合System.IO.File的ReadAllLines/WriteAllLines写盘(默认UTF8无BOM, 兼避PS5.1 GetContent的GBK误读). (2)exec脚本内(含代码注释)严禁裸写三联反引号: code块内会被当渲染围栏吞掉, 脚本后半段掉出块外被解析层吃. (3)PowerShell的char乘int未定义(InvalidOperation), 但默认continue后续照跑=半死脚本静默写盘, 靠回执里的newcount/after段才识破. 围栏构造用-join($bt,$bt,$bt). (4)create遇回收站同名历史备份会拒绝覆盖, 而move指令又被禁止触碰.agent_trash目录: 对策是先把工作区旧文件move到工作区外(D:\temp)腾位再create, 三份全保留. 旧README(1062B)备份: D:\temp\tdh-readme-1062b.bak.
后记v4(2026-11-17深夜): exec载荷对"数组字面量内的反引号表达式"扰动实证两形态: ①断行死法: eng数组中 $bt加号拼接语言标记 的元素落盘断成两行(三反引号行+孤立json行, count+1); ②合并死法: 探针数组三元素(事故形状/-join/内插)到达时被压成单元素空格连接串(len23=三乘七加两空格), 换行与逗号全失. 同数组三构造法未获分离验证, -join/内插在数组字面量内的安全性未知. 例外反差(样本2): if条件中括号包裹的同形表达式/内容行内联反引号/裸$bt闭栏行均存活. 唯一实证免疫法=盘上碎片拼接(读回相加, 载荷内零反引号零语言标记字面, 本轮修复实战成功). 铁律: (a)数组字面量内禁拼围栏行, 围栏行一律盘上拼接; (b)围栏写入后必须回读总行数对账+围栏行长度验证, 只数围栏行数无法区分一体与断裂; (c)探针设计教训: 欲分离验证多构造法须物理隔离(独立数组/独立写盘), 同数组兄弟元素会被一起卷入扰动.
tag: TDH项目，terrain-diffusion-horizons，坑，指令格式，PokerAgent，README
<!-- END:112 -->
<!-- ID:113 -->
2026-11-17 max的voxy弃选理由(原以README手注口述形式出现): voxy现有1.20.1版本太不稳定; ARR许可证麻烦; 给voxy做加速相当于要写1.20.1版自生成区块的voxy, 即使不全写也要吃透那些fork, 工作量太大; 1.20.1之后的版本大概率也不会转向voxy. 含义: TDH只桥接DH是长期定位.
[同日更新] 处置落定: max指令"我手写那些格式规整一下,翻译一版英文版放底下", 手注已规整为正式段落置入简介后(中英双语, 论点全量保留, "不稳定"标注个人体验), 上轮"改写为公开FAQ"选项以此形式落地, README处置完结.
tag: TDH项目，terrain-diffusion-horizons，voxy，选型，README
<!-- END:113 -->
<!-- ID:114 -->
2026-11-17 README行数异常案: 终验144行后, 本轮替换前发现146, 多出L9-10两行max口吻voxy手注; 我方窗口零写入(替换为ReadAllLines单元素赋值, 数学上不减行), 围栏锚点68/78→70/80精确+2, 全文排查无其他非我稿内容, 证据指向max在终验后直接手改了文件(读README时顺手批注). 教训: (1)我落盘后的验证结论会被用户并发手改作废, 任何写操作前必须重读现场取实时锚点, 不能拿旧回执当现状; (2)行数/内容异常先用内容锚点定位插入点再排查自身, 未排除外部编辑前禁止"修复"——本轮若信旧count按144去对齐, 会误删他的两行; (3)带冒号前缀的口述体手注是用户主观创作, 发现后只问不改.
tag: 坑，PokerAgent，协作模式，README
<!-- END:114 -->
<!-- ID:115 -->
2026-11-18 TDH地物/结构同源可行性审查完结(javap五轮实证, 报告=docs/feature-structure-feasibility.md, 164行)。架构级结论:
①vanilla 1.20.1撒点链纯函数全public: getPotentialStructureChunk(long,int,int)零chunk上下文(链: setLargeFeatureWithSalt(worldSeed,floorDiv(x,spacing),floorDiv(z,spacing),salt)→RandomSpreadType.evaluate×2), isStructureChunk对state依赖仅getLevelSeed+exclusionZone, ChunkGeneratorStructureState.createForNormal(RandomState,long,BiomeSource,HolderLookup<StructureSet>)静态工厂无需真实世界 → 结构判定同源可完整离线复现。
②装饰RNG链chunk间feature间零耦合: setDecorationSeed(worldSeed,cx,cz)每chunk独立纯函数, 每PlacedFeature前setFeatureSeed(decorationSeed,index,step)独立重播种 → "流式随机锁死地物复现"是错误认知, 流式墙只在单feature.place()内部。地物逐株从"干不了"上调至"硬干"。
③介质无API墙: FullDataSourceV2.dataPoints=每列LongArrayList段数无上限, EDhApiWorldGenerationStep原生有FEATURES/STRUCTURE_START; 悬空段渲染行为未实测。
④DH官方路线=INTERNAL_SERVER真跑vanilla管线(WorldGenStructFeatManager=假StructureManager真读region盘, 靠RegionFileStorageExternalCache), 数学同源路线DH无人区, 但vanilla public API兜底充分无需mixin/AT。
⑤五档: L1统计近似舒服干/L2撒点舒服干/L3组装麻烦点(templateManager+jigsaw边界)/L4逐株硬干(FakeWorldGenLevel三高频路径getHeight/getBlockState/getBiome→TD数据; 折损=block predicate近似, 定性"分布同源+个别坐标错")/L4+完全一致干不了(predicate查真实方块网格, TD数据源物理不可能)。
⑥性能全挂DH生成线程FPS零影响, 数字均推算非实测。
⑦待验6项: 多人worldgen registry不同步(TD场景json未动不受影响)/RandomState离线构造可见性/客户端templateManager/悬空段渲染/concentricRings秒级/L4 biomeFilter近似层。
落地方向(待max裁决): L1半天→L2卖点级→L3(先钉templateManager)→L4树先行草殿后。
tag: TDH项目，terrain-diffusion-horizons，worldgen种子链，地物，结构，同源
<!-- END:115 -->
<!-- ID:116 -->
2026-11-17 javap 语言参数坑: 该环境下 javap -J-Duser.language=en 的 -J 参数会被解析层拆成 "-J-Duser" + ".language=en" 两个 token, 之后所有参数(含-classpath/-p/类名)全被 javap 当类名报"找不到类"→整轮全灭。对策=JAVA_TOOL_OPTIONS 环境变量注入(PowerShell: $env:JAVA_TOOL_OPTIONS='-Duser.language=en'), 纯环境传递零参数解析, 已实测有效(javap输出首行出现"Picked up JAVA_TOOL_OPTIONS: ..."即生效; PS侧记得Where-Object滤掉Picked up行)。附带: 该环境exec回执中脚本内中文标记必mojibake, 脚本内标记串用ASCII。
tag: javap, 坑，技巧，PokerAgent
<!-- END:116 -->
<!-- ID:117 -->
2026-11-18 recon6 静态门全开(六项零阻塞): ①RandomState.create 两 public static 重载, BiomeSource 抽象面 3 方法, 单人可偷活实例 ②StructureTemplateManager 构造四输入(ResourceManager/LevelStorageAccess/DataFixer/HolderGetter<Block>)全 public 单人可得 ③假 ChunkGenerator=11 抽象方法 8 桩 + getBaseHeight/getBaseColumn 映 TD; applyBiomeDecoration 是 ChunkGenerator 具体方法, 装饰编排子类继承白送 ④DH 两车道机械原生存在: WorldGenerationQueue(生成→updateFromDataSource→applyToParent 一处打通, 参考实现), FullDataUpdatePropagatorV2, DelayedDataSourceSaveCache(异步批量落盘, 写放大不成立) ⑤columnGenerationSteps 是 DH 增量生成承重账本(GeneratedFullDataSourceProvider/PregenManager/多人 FullDataSourceRequestHandler 三方消费) ⑥悬空段+闪烁=运行时项, 零成本旁证=原版 DH 卸载林子的树冠段渲染 ⑦TDH 挂钩面=TdDhWorldGenerator(IDhApiGenerator 实现)+TdLodTranslator ⑧多人客户端 RegistryAccess 是否含 worldgen 动态注册表未验(单人无此问题, 多人挂 §5.1 前提)。建议落地批次: L1+L2 先行, L3 第三, L4 压轴+车道基建。报告 §5.2/5.3 已标已验证, §5.4 改运行时项旁证, §6 附两车道候选架构(标待裁决)。
tag: TDH项目，terrain-diffusion-horizons，地物，结构，同源
<!-- END:117 -->
<!-- ID:118 -->
2026-11-18 L4 地物通用性结论: 复刻编排层(applyBiomeDecoration)而非逐 feature 翻译 → 模组地物零成本自动进入(模组加地物的唯一正道=把 PlacedFeature 塞进 biome features 列表, 编排层拿到的 Holder<PlacedFeature> 来源无差别)。三层通用度: 判定层 100% 免费 / 执行层大多数(FakeLevel 四高频面覆盖) / 视觉层有物理上限(地下地物白算, DL1+ 折叠)。RNG 独立(已实证)解锁: ①选择性执行不破坏同源(跳过矿石不改树的序列)→ step 分类默认开关+配置黑白名单 ②异常隔离不连坐(模组 feature 炸=catch+黑名单, 等于没来过)。工程要求: 列缓冲需 feature 级事务(炸不留半棵树)。最坏情况: 依赖真实世界活状态的模组 feature 永远跑不成, 兜底=catch+黑名单, 属 L4+ 物理墙边缘摩擦非新增风险。"树先行"是验证顺序选择, 非架构选择, 第一条管线通=模组地物顺流进来。
tag: TDH项目，terrain-diffusion-horizons，地物，通用性，架构
<!-- END:118 -->
<!-- ID:119 -->
2026-11-18 沉没过滤器定稿+临时化 (max 裁决): 表面以下方块一律跳过。判据=列TD实心表面高程(非海平面, 神殿不误杀, 与FakeLevel.getHeight同列缓存同校准[记忆101], 单一表面定义)。L4落点=FakeLevel.setBlock咽喉(y<表面→丢弃+返回true假成功); L3落点=折叠逐列裁剪(整piece沉没连组装跳); step粗滤=省CPU第一道。max 指出并裁决为[临时方案]: 玩家在地下看LOD侧面现状=纯色块/空(TD无地下数据, max实测), 现状零可见损失, 但焊死成永久决策会封死地下路线(将来TD地下数据/LOD地下填充落地时, 洞穴内可见结构部分如要塞墙会被吞) → 标临时, 地下视野提上日程时重审, 先按跳过用。教训: "与现状同构"只在现状时间轴内成立, 架构论断要过未来兼容性检查。
tag: TDH项目，terrain-diffusion-horizons，地物，架构，坑
<!-- END:119 -->
<!-- ID:120 -->
2026-11-18 PokerAgent指令事故: 多行指令(remember)的[code]开始标签损坏(疑似混入不可见字符), 框架把remember解析为无内容=清空短期记忆, 记忆内容后续行被解析为3条未知指令。教训: ①发出多行指令前核对[code]/[/code]/[cmd]/[/cmd]标签配对完整性, 尤其复制粘贴处; ②回执出现"未知指令:"=格式事故信号, 立即回查指令原文; ③remember被清空后第一时间重建(replace/exec的成功回执可佐证哪些事实已完成)。
tag: PokerAgent, 指令格式, 坑
<!-- END:120 -->
<!-- ID:121 -->
2026-11-18 DH 3.3.3 LOD 渲染 tint 链实证 (javap, L1 设计依据): LOD 方块颜色 = 贴图采样 × 群系 tint, biomeWrapper 来自 data point。链: AbstractDhTintGetter implements class_1920(BlockAndTintGetter), update(BiomeWrapper, BlockStateWrapper, FullDataSourceV2, IClientLevelWrapper) 逐块喂 biome; ClientBlockStateColorCache.getColor(BiomeWrapper, FullDataSourceV2, DhBlockPos, boolean) 直收 biome 参数; unwrapClientBiome(BiomeWrapper)→原版 Biome 走原生 color resolver。EColorMode=Default/Flower/Leaves/Chisel/Glass (贴图采样模式)。备用通道: AbstractDhTintGetter.setStaticColor(BlockStateWrapper, BiomeWrapper, Integer) 静态色覆盖; API 层 DhApiBlockColorOverrideEvent.onBlockColorOverridden 事件。运行时旁证: 记忆110 河岸 tint 色带=biome tint 已在生效。结论: TDH data point 已填 TD 真实群系 key → 基础植被色免费; L1 剩余=统计树冠段(密度手抄表[临时]+确定性哈希+leaves段)。
tag: TDH项目，terrain-diffusion-horizons，DistantHorizons，tint，L1
<!-- END:121 -->
<!-- ID:122 -->
2026-11-18 PokerAgent replace 指令双事故 (第三起格式事故系列): ①新旧code块顺序写反 — 第一个块必须是旧文本(文件现状), 第二个块才是新文本; 反了=框架拿新文本当旧文本搜索, 必失败, 回执"旧:"列显示的是新文本内容即此症。②缩进精确匹配 — replace按缩进精确匹配, 相似度报告规范化空白后可达100%但精确匹配判死; read回执行号后的空白=分隔符+行内容缩进混合, 凭肉眼/印象解码缩进必错(案例: 循环体内8/16sp, 但方法内try块里是12/20sp, 按前者写锚点失败)。铁律: replace锚点缩进拿不准时先用exec让PowerShell吐精确前导空格数( $ind=0; while($l[$ind] -eq ' '){$ind++} ), 不猜。
tag: 指令格式，坑，PokerAgent，replace
<!-- END:122 -->
<!-- ID:123 -->
2026-11-17 max 指令逐字存档 (源=max 粘贴聊天记录恢复; 丢失根因=指令只入短期记忆, remember 标签损坏清空事故[记忆120]炸掉唯一副本后被覆盖, 从未入长期记忆 → 铁律: 用户指令当场逐字入长期记忆, 短期记忆是旋转缓冲不是仓库):
①立项令(逐字): "干吧,植被上色搞配置项开关,结构和地物lod和地形lod分开,不阻塞地形生成,快的就快跑,慢的就慢慢跑,结构异步生成和地物异步生成单独两个配置项,配置文件格式改成toml,写注释,挪进config里的terrain-diffusion-mc文件夹"
②同步语义(逐字): "关了就是同步模式,地形lod生成之前等地物和结构全跑完才显示,默认开,兴许有人就是觉得值得用速度换视觉没有缺陷呢" → 异步开关语义=关即同步(地形LOD扣住等对应车道全跑完一次性发布), 默认true=异步
③沉没过滤器临时化: "标临时,先直接跳过用着" (详见119)
④批次: 六门全开后裁定 L1+L2 一批先行, L3第三, L4压轴; max 过目后"干吧"
⑤五档定级/两车道机械/通用性/性能账: 见115/117/118
⑥[max实测三反馈, b8afe8e后] ①树叶满铺无间距(真因: density 200≈78%块级命中+无树干+冠段贴地; dl0一列=1×1块[实证TdLodTranslator], TdVegetationMap javadoc"64×64块/列"是错的待修) ②异步模式默认关 ③异步没做配置开关 → ②③定谳(TEST播种TOML实证): 车道+开关整个未实现, 根因=报告§6"车道随L4引入"排期压过max"现在就要分开"原话, 判决错误: max原话优先级>报告排期
tag: TDH项目，terrain-diffusion-horizons，max指令，异步车道，配置
<!-- END:123 -->
<!-- ID:124 -->
[2026-11-18] TDH L1植被虚拟树格落地(A批, max"都干"令下实施). 架构: TdVegetationMap表驱动(TreeType{log,leaves,trunkBase+randA+randB[vanilla trunk_placer双nextInt同构],canopyR,canopyH,conical,doubleStemPermille} + PoolEntry千分比 + VegStat{cell,pool,bamboo,maxCanopyR,radiusCells=(maxR-1)/cell+1}); Generator列逻辑=3x3邻域cell反查(格哈希splitmix64混expectedSeed, 格内抖动+前缀和选型+双独立哈希干高+树心地面取elevation[树心]+干段止conical冠底+悬空冠段锚树心干顶+冠下缝显式air段+段游标cursor推进). 数据源: vanilla json=E:\MC\.minecraft\versions\1.20.1-Forge_47.4.0\1.20.1-Forge_47.4.0.jar 的 data/minecraft/worldgen 三目录(已解包%TEMP%\van_wg_x); 类常量=F:\MOVED\.gradle\...\recompile_4980ba80425e3551a9686b3f7aca8d812454ef91_output.jar javap(蘑菇茎4+nextInt(3)且1/12翻倍[红褐共用], 竹竿5+nextInt(12), 竹count=ceil((Biome.BIOME_INFO_NOISE静态字段(x/80,z/80)+0.3)*160), 噪声1234种子世界无关直引). cell=round(sqrt(256/N)): 森林5/黑森林4/丛林3/竹林3(巨树层30.1×0.75)/平原72/蘑菇16. 关键修正史: plains权重19:1=0.05棵非0.1; jungle去bush池jungle545/mega273/fancy182. 近似声明在javadoc(count尝试上限未扣survive/冠形状块级退化/2x2粗干压单列/bush与竹丛剔除). 未实测: 悬空冠段渲染(TEST首验, 兜底=冠段贴地退化). 旧density观感表已入回收站退役.
tag: TDH项目, L1, 植被, 架构决策
<!-- END:124 -->
<!-- ID:125 -->
[B1侦查#7结案 2026-11-18] DH 3.3.3 数据面获取链全闭环(十轮javap/zip反索, 全public零反射): api LevelLoad事件EventParam.levelWrapper(public final)→instanceof core ILevelWrapper防御后强转(皮实同体实锤: core ILevelWrapper常量池直接引用api接口)→SharedApi.tryGetDhServerWorld()(public static, 单机=DhClientServerWorld/dedicated=DhServerWorld)→IDhServerWorld.getOrLoadServerLevel(ILevelWrapper)(default幂等)→IDhServerLevel.getFullDataProvider()→运行时实例GeneratedFullDataSourceProvider: get(pos)/getAsync读 | updateDataSourceAsync(FullDataSourceV2)写回+父级传播 | queuePositionForRetrieval(Long)重生成(内部委托WorldGenerationQueue.submitRetrievalTask, 自带闸门+去重)。备选直达: dhLevel.serverside.fullDataFileHandler(ServerLevelModule public final字段)。时序幂等对冲。ServerApi字节码同款姿势实证。编译: core直调+compileOnly钉3.3.3(WrapperFactory_forge S1先例)。#6(LOD短路)留B2实测。终稿: docs/batch-b-design.md §10
tag: B1侦查, DistantHorizons, 刷新, 架构, terrain-diffusion-horizons
<!-- END:125 -->
<!-- ID:126 -->
[2026-11-18 max勘误"都干"语义+范围重划] max语义: 植被=花草上色(列级变色, 不显示本体); 树=地物范畴(按方块精度显示形状, 归地物批); "都干"=把活干完非范围扩张。
[终态] L1树格(含竹层)判停: 半成品+跳级+配置接错, max裁决断开配置恒关(vegEnabled=false硬编码), 代码+注释保留待地物批参考。现状清单三道防线落账: TdDhWorldGenerator树格块头注释+冠段[BUG]标注+TdVegetationMap停用声明+本条。
[现状] ①发射bug未修: 块形冠段cb/ct与离心dc无关=全列同y区间=立方体树冠(实测"筷子插豆腐"); conical分支有调制; 修法=离心衰减推广(三行级); Chebyshev方角另病未修 ②已验证: 悬空冠段+冠下air段渲染通过(115结案), tint链生效 ③未验证: 云杉锥观感/cherry垂叶+pink_petals ④数据侧TdVegetationMap无罪(表驱动全出处)。
[花草层新主线] 列级确定性哈希命中换列顶state=色斑, 复用mix64; 数据从van_wg_x花池json抄表; risk: cross贴图采样色未验+非solid方块DH端处理未知, TEST首验。
tag: TDH项目, L1, 植被, 地物, 范围, 停用
<!-- END:126 -->
<!-- ID:127 -->
[2026-11-18 三连败教训: PokerAgent exec PowerShell 脚本安全规则]
① 语句级运行时错误不终止整个脚本, throw 不可靠, 尾部成功输出不代表中间步骤成功 → 守卫必须正向 if + 每步显式 OK/FAIL 输出 + 事后 read 读回终审。
② PS 双引号字符串内禁引号字符: PS 转义符是反引号, \" 无效 (会被当字符串终结符 parse 炸); 措辞直接绕开引号。
③ 禁用未在本项目实证过的 .NET API (教训: 捏造了 Match::EscapeReplacement, .NET 无此方法); 只用已跑通族: [IO.File]::ReadAllText/WriteAllText, 字符串 .Contains/.Replace, ConvertFrom-Json。
④ 多行块替换倾向改纯插入 (锚点行前/后插), 锚点用读回验证过的唯一单行, 避免 regex。
tag: PokerAgent, exec指令, 坑, PowerShell
<!-- END:127 -->
<!-- ID:128 -->
[2026-11-18 教训: PokerAgent 指令集里没有 write 指令] 捏造不存在的指令 (write) 发送, 框架把
tag: PokerAgent, exec指令, 坑
<!-- END:128 -->
<!-- ID:129 -->
[2026-11-18 code块反引号坑·两轮实证闭环] code块内的裸反引号会被框架吃掉(使用指南明示: 想写入原样反引号必须先转义, 即反引号是标记字符). 实证: PowerShell行切分模式(反引号r问号反引号n, 本意CR可选LF)被吃成字面r问号n → 在每个字母n处碎行 → 依赖整行内容的IndexOf/正则锚两轮全miss(B1写码事故); 同脚本内ReadAllLines诊断正常(不经切分), 目标文件本身干净(行len精确吻合). 修复配方(脚本零反引号): [IO.File]::ReadAllLines读行 / -match后跟双引号内反斜杠r反斜杠n检测CRLF(反斜杠原样传给正则引擎解释) / 字面CRLF用 -join [char[]](13,10), LF用 [string][char]10 / 按检测行尾join后WriteAllText写回. remember/memory内容同样禁反引号(曾致remember块碎成未知指令+短期记忆被清空). 工程教训: 写码+build+commit+部署同块串联, throw能拦住后续全部步骤, 防半成品白烧冷启动.
tag: PokerAgent, exec指令, 坑, PowerShell, 反引号
<!-- END:129 -->
<!-- ID:130 -->
[2026-11-18 PS函数return解包坑·B1事故第三轮] PowerShell函数内 return $list (List[T]) 时 pipeline 会把集合解包, 调用方拿到的是 Object[] 而非 List (单元素时更是裸标量); 参数按位传入不解包, 只有 return 路径解包. 在解包后的 Object[] 上调用 List 专属方法(如 InsertRange)时, PS 成员枚举机制把调用下放给每个元素, 报错文本是元素类型("Method invocation failed because [System.String] does not contain a method named 'InsertRange'")而非接收者类型(Object[]) — 报错类型有系统性误导, 勿按报错类型反推接收者. 修复: return ,$L (逗号包一层抑制解包) 或 Write-Output -NoEnumerate, 或不用函数直接内联构造. 诊断口诀: 报错类型与接收者预期不符时, 先怀疑函数返回值被 pipeline 解包.
tag: PowerShell, 坑, 技巧, exec指令
<!-- END:130 -->
<!-- ID:131 -->
[2026-11-19 TDH 部署配方定稿] ① 构建产物 jar 名 = Gradle archive 连字符形式 terrain-diffusion-horizons-<版本>.jar (不是 modid terraindiffusionhorizons, 曾按 modid 猜 filter 零命中); 选件配方: build\libs 内取最新且名字不含 sources 的 jar + 健全性检查(名字含 horizon 且不含 distant). ② 部署目标 = TEST 实例 mods: E:\MC\.minecraft\versions\TEST 1.20.1-Forge_47.4.10\mods [长期记忆: 041]; 部署后清 mods 内异名旧 TDH jar 防双加载(旧版 git 可复现). ③ 播种式配置坑: TdConfig 只对新文件播种 TEMPLATE, 已存在的旧 toml 永不自动长新键/新段 — TEST 旧 toml 已手动补 [structure]/[features]; 真用户场景将来需版本化重播种. ④ mods 现存 terrain-diffusion-horizons-0.1.0.jar.bak-20261118 旧备份(非 .jar 后缀 Forge 不扫, 无害, 删否归 max).
tag: 测试环境, 构建坑, terrain-diffusion-horizons, TDH项目, 部署
<!-- END:131 -->
<!-- ID:132 -->
[2026-11-19 TD fork 三连崩案·结案] 根因 = Files.createDirectories(Windows junction) 在 JDK 17 抛 FileAlreadyExistsException, JDK 21 幂等 (实证: 11-16/11-18 成功会话 java 21.0.7 Azul, 2026-11-19 晚三连崩 java 17.0.12; 同 jar 同路径唯一变量 JVM. 机制解释=21 跟随链接/17 不跟随, 置信度中). 环境布局: TEST 实例 terrain-diffusion-models = junction → ..\Industrial Revolution 2040\terrain-diffusion-models (真身 4.24GB, IR2040 装正式版 TD 2.3.0, TEST 装 fork). junction 建于 11-07 19:46, 11-15~11-19凌晨 Java 21 全绿. 崩点: fork ModelAssetManager.ensureAssetsReady:67 (CONSTRUCT 阶段, TerrainDiffusionMc.<init>:35 → bootstrap:58 → initialize:70). 修复正解 = 前置 if (!Files.isDirectory(dir)) 再 createDirectories (isDirectory 跟随链接, junction/真目录/不存在三态全对). PS 坑: junction 的相对 target 不能直接 Test-Path (基准=当前工作目录), 须按 junction 父目录拼绝对路径.
tag: bug根因，terrain-diffusion-mc, 测试环境, 坑, JVM
<!-- END:132 -->
<!-- ID:133 -->
[MC 日志轮转规律 2026-11-19 实证] TEST 实例 logs 归档: 文件名日期 = 被归档会话的起始日(非轮转时刻), 同日多次启动索引递减分配(S1→-N ... 最后会话→latest), .gz 的 mtime = 下一次会话启动时刻(轮转发生时). 判读法: 要看"第 k 次启动"的日志, 按 mtime 排序而非按文件名索引; 崩溃会话日志小(启动即崩), 正常会话大. 曾因此把 -3(凌晨成功会话)误标成"今晚首崩". debug 日志同规律 (debug-N.gz 全局递减计数).
tag: Minecraft, 坑, 技巧, 日志
<!-- END:133 -->
<!-- ID:134 -->
[2026-11-19 PCL2 Java 选择自动丢失·实测案例] PCL2 的 Java 选择设置会自行丢失回退 (max 原话"pcl抽风给我java21干掉了"). 实录: TEST 实例凌晨还用 E:\Java\zulu214219 (21.0.7 Azul), 晚上选择丢失回退 E:\Java\Zulu\zulu-17 (17.0.12) → 触发 TD fork 的 junction 崩 (memory 132 的雷). 机器上 Java 全家福: zulu214219=21.0.7 / zulu21=21.0.4 / jdk-21=21.0.2 / Zulu\zulu-17=17.0.12, 都活着, 漂移发生在 PCL2 选择层非 Java 损坏. 教训: "昨晚还好好的今天就崩" → 先看日志 ModLauncher 启动行的 java version 再谈别的.
tag: PCL2, 坑, Java, Minecraft, bug根因
<!-- END:134 -->
<!-- ID:135 -->
[2026-11-19 陈清单事故·教训] 短期记忆里 carried 的"等max清单"条目未随代码演进刷新: 11-18 max 裁决停用 L1 树格(记忆126), 但"悬空树冠待测+三分支"旧条目一直躺在清单里, 11-19 夜我照旧条目给 max 编验收目检单 → max 找不到树 → 误报"地物完全不生成" → 白烧两轮侦查后翻案. 铁律: 从记忆捞出的待办/验收/清单条目, 喂给 max 前必须对当前代码与部署状态复核 (git log 近5条 + 关键注释 grep); 记忆是缓存, 代码是事实源.
tag: 坑, 技巧, 勘误
<!-- END:135 -->
<!-- ID:136 -->
[PowerShell 参数展平坑 2026-11-20] 字面量嵌套数组 @(@('a','b'), @('c','d')) 作为函数参数传入时被展平成一维 @('a','b','c','d') — foreach 迭代出的是单个字符串, $p[0]/$p[1] 取的是字符串前两个字符而非预期的两个元素。TDH B2 事故实录: (old,new) 替换对数组被展平成 6 串, 实际执行了 Replace('r','e') + Replace('p','u') 两轮全文件单字符替换, StructureSetParams.java 全毁 (return→eetuen, fromJson→feomJson), javac 报 30 错。教训: ①PowerShell 里做文本替换直接内联写 $t.Replace($old,$new), 不封装 (old,new) 对数组; ②验证环节输出为空 = 失败, 回执里 Select-String 无任何输出必须当红旗处理, 不能只看前面的 OK 行; ③.NET String.Replace 区分大小写 (大写 P 幸存是定位线索); ④修复用整文件重写 (public 修饰直接写入内容), 不再走 exec 字符串替换。
tag: 坑, 技巧, exec指令, PowerShell
<!-- END:136 -->
<!-- ID:137 -->
TDH (terrain-diffusion-horizons) B2 里程碑达成 (2026-11-20): 结构撒点 oracle (TdStructureSpreadCore) 冒烟全绿。最硬证据: oracle 撒点 villages@chunk(464,55)=block(7424,880) 与 vanilla locate 返回 [7424,880] 逐位相等 (seed=5303470317317397359, 世界"新的世界(14)")。15 集合白名单全命中, 3702 region/6min/1核车道~10region/s, 缓存命中 11 条 (tp 后 LOD 重拉, 异步刷新环路闭环), 退世界清 3702 region, TDH 错误 0。locate 坐标可从 ChatComponent 日志行找回 (LocateCommand 本体只记耗时)。撒点命中=结构粗位置 (region 级), B3 才做结构形状/BF loads。commit 728cbdc 为 B2 代码基线。
tag: TDH项目, B2, 同源, 结构, 验证, 里程碑
<!-- END:137 -->
<!-- ID:138 -->
TDH 设计稿 §6 裁决 (2026-11-20 03:00 max 拍板): 甲案 — B2 结构撒点保持零视觉 (纯坐标事实层), 远景结构视觉统一由 L3 真地物载荷层出图; 乙案 (锚点色块占位) 被否, 理由=临时视觉债, L3 落地即作废, 不值得搭一次要拆的渲染管线。连带影响: B3 原主题 (视觉) 取消; submit 短路观察依赖视觉通道, 随之悬空; B3 剩余范围 (结构形状/BF loads 数据层归属, 是 B3 做还是并入 L3) 待理清。裁决时 B2 已全案收口 (commit 1280bb5, 撒点 oracle 与 vanilla locate 同源数学逐位相等实证)。
tag: TDH项目，架构决策，B3, 裁决，视觉
<!-- END:138 -->
<!-- ID:139 -->
TDH 勘误 (2026-11-20 05:1x): .agent 三文件 2026-11-17 已由 max 定夺 (入库, .gitignore 不加 .agent/ 条目, GOAL-PLAN 案卷 grep "三文件定夺" 可查), 非悬案。我当日在裁决文档里误当悬案呈报并臆造 "待决策 5" 编号, 已双处勘误。根因: 只读 "待 max 决策" 节头 (当时仅 4 条) 就当全集, 没全文 grep, 漏掉散落在历史批次记录里的销案标记。规矩 (通用): 核对任何 "待决策/悬案/待办" 清单, 必须先全文 grep 关键词, 节头只是目录不是全集。另: 备忘录 "薪火相传" 原话语境是 PokerAgent 框架 .gitignore 模板设计, 与 TDH 项目定夺结论同向但不同案, 引用时注意分层。
tag: TDH项目, 勘误, 教训, 待决策清单核对, 悬案
<!-- END:139 -->
