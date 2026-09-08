# 视觉系统设计方案（v2：GPU 深度缓冲驱动）

> **版本记录**
> - v1：section 遍历 + 三个 Mixin Hook（cullTerrain / EntityRenderDispatcher / BlockEntityRenderDispatcher）+ 软件遮挡。问题：可见性是"区块级视锥体"，墙后的物体被误收；软件遮挡是近似。
> - **v2：采集核心改为"读 GPU 深度缓冲 → 反投影 → 坐标直查"**。可见性是逐像素遮挡真值；方块/方块实体精确、实体近似；NBT 序列化 / 存储 / 持久化全部复用 v1；记忆世界改为**纯累积语义**（只增不删）。
> - **v2.1（实现确认）**：深度捕获落地为**手写 GL PBO 回读**（vanilla `copyTextureToBuffer` 仅支持 color 附件，无法读深度）；注入点改为 `LevelRenderer.renderLevel` **TAIL**（矩阵/相机直接取方法参数）；深度语义经源码核实精确化（玻璃/水看穿、**岩浆写深度**、CUTOUT 半透明按 alpha 丢弃、药水/亮度与深度正交）；反投影定案为**全量 + 去重**（不做像素降采样，见 §4.3）。
> - **v2.2（Phase 2+3 方案定稿，实现待审阅）**：反投影 / 三路查询方案经源码核实定稿并写入本文档（§4-§6、§8、§11-§12）。**两处关键更正**：(1) **`modelViewMatrix` 纯旋转**——`renderLevel` 传入的模型视图矩阵只含旋转、相机平移在顶点着色器完成（`terrain.vsh`），反投影须 **`world = camPos + inverse(P×V)×ndc`**（§4.1，早期公式缺 `camPos`）；(2) **实体判定收紧**——删除"缓冲=天空→可见"捷径，改为"缓冲表面点落在实体 AABB（外扩 0.2）内才可见"，防假阳性（纯累积语义下假阳性比假阴性更糟）。
> - **v2.3（天空阈值修正）**：天空判定由 `d < 0.999` **收紧为 `d < 1.0f`**（清空值精确 1.0；代码用 `1.0f − 1e-6f` 防读回舍入，按 §4.1 公式恰好覆盖到远平面）。原因：深度非线性（1/z 压缩）下 **`d(47 格) ≈ 0.999`**，旧阈值会把 **~47 格以外的所有可见对象（方块 / 方块实体 / 实体）当天空丢弃**（RD 12 / far≈768 下即 2.5~12 区块整段）。三处同步修正：§4.2 天空行、§5.3 实体判定、§6.2 `nonSkyPixels` 定义；§12 远平面行原"不影响 0.999 阈值逻辑"的说法更正。
> - **v2.4（实体改为正向查询）**：§5.3 实体判定从**反向锚点采样**（实体 → 投影盒中心/8 角共 9 锚点 → 查深度；薄/扁/部分被挡实体的可见像素不落在锚点上 → 漏检）改为**正向像素归属**——每个非天空像素的原始表面点 W，先按 `SectionPos`（16³）桶**粗过滤**"该 section 是否有实体"，有才逐个 `AABB.contains(W)` 精判；只对 air 格的点匹配（消"方块在前 + 实体外扩盒盖住表面点"的假阳性）。复杂度与反向同量级甚至更低（绝大多数像素 O(1) 桶 miss 跳过）。亚像素 / 透明 / 发光实体仍漏（深度物理极限，见 §10）。
> - **v2.5（实体判定：air 过滤 → 深度排序）**：§5.3 精判的"W 所在格为 air"前提错误——**方块是网格地址、实体是连续曲面，二者可共格**（高草丛/门/水里的实体）；共格时像素记录面是实体、W 落在实体身上但格非空气 → 实体被误杀。改为**深度排序**：射线与实体**未外扩盒**的近交点距离 `t_entry`，`|W−camPos| < t_entry`（记录面在实体本体之前 = 方块面/实体在墙后）→ 跳过，否则记录面就是实体本体或其伸出部分 → 可见。外扩盒（0.45）保留在 contains 判断兜模型伸出；实体路的逐格 getBlockState 缓存删除；对 CUTOUT 挡体的假阳性抑制从"按格分类"变为"按真实深度排序"，更精确。
> - **v2.6（天空阈值精确化：`1.0f−1e-6f` → 远平面极限深度 `d_far`）**：v2.3 的 `1e-6` 容差不精确——它是魔法数，正确边界是**远平面极限深度**：标准透视矩阵下远平面 NDC z=+1 → depth=(+1+1)/2=**恰为 1.0**（§4.1 公式 `d(far)=1.0000651−0.0500065/far` 亦收敛到 1.0）。判定改为 `d ≥ d_far`（=1.0），`d_far` 由实际投影矩阵推导、**不硬编码**；DEPTH32 存 float32、天空/清空读回精确 1.0，**无需容差**（`1e-6` ≈ 8.4 ULP，会吞 `d∈[1−1e-6,1.0)` 的真实远表面，far=768 时约 z∈[756,768] 段）。同步修正 §4.2/§5.3/§6.2/§8/§12。
> - **v2.7（半透明方块可见性：新增 §5.4 第四通道）**：TRANSLUCENT 层（玻璃/水/染色玻璃/冰/黏液块/蜜块/红石线/下界传送门）不写 main 深度，深度通道天然获取不到它们本身（§5.1 air 近侧回退只能偶然捡到"紧贴可见不透明块"那一类；天空背景的独立玻璃/水完全漏掉）。新增**独立通道**：粗筛（视锥内 Section 按**渲染层映射**收集半透明候选，palette 驱动）+ 精筛（投影候选覆盖的**全部**像素，逐像素"射线-AABB + 深度比较"，统一判定式覆盖"前面有不透明面 / 贴天空 / 被遮挡"三况）。明确弃用"9 锚点反向采样"（与 §5.3 实体 v2.4 同一教训：锚点不完整 → 部分遮挡漏检）；bbox 为投影超集，**必须带射线-AABB** 防假阳性。
> - **v2.8（§5.3 肢体伸出盒漏判 + §5.4 相机在盒内修正）**：审阅发现两处几何漏洞。(1) **§5.3 实体**：记录面 W 落在实体**伸出 AABB 之外的肢体**（牛角/平举手）时，射线与未外扩盒不相交（`t_entry=null`）被跳过；且**肢体在盒前**（手伸过洞口，`t_entry≠null` 但 `|W−camPos|<t_entry`）同样被跳过——仅肢体露出的实体漏检。修复：精判扩为**三分支**并新增**"记录面非方块"判别**——`cell(W + dir·ε)`（沿射线远侧 ε 格）为空气 ⇒ 背后无方块 ⇒ 记录面是实体几何（肢体）→ 可见；**为何不查 W 自身格**：float32 会把方块面上的 W 震进相邻空气格 → 对墙旁隐藏实体假阳性（纯累积语义更糟），`W+dir·ε` 保证落回方块体。判别需读 blockstate → 该子步落渲染线程。另加**相机在实体盒内 → 可见**早退。(2) **§5.4 半透明**：相机在方块内（游泳/站玻璃里）时 slab 求交返回**负 t_entry**——统一判定式 `Z_opaque ≥ t_entry−δ` 对负值天然判可见，但**约定不明的实现把负值当不相交会漏检贴脸水/玻璃**。修复：精筛前显式 `B.AABB.contains(camPos) → 可见` 早退 + 规定 AABB.clip **必须返回带符号 t_entry**（起点在盒内为负），不得把负值当不相交。
> - **v2.9（源码核查修订，`decompiled_src_vf/client` 三轮并行核对）**：(1) **`entitiesForRendering()` 返回全部已加载实体、不做视锥裁剪**（`ClientLevel` L327 = `byId.values()`），真实裁剪在 `LevelRenderer.extractVisibleEntities` L812-842（距离 `shouldRenderAtSqrDistance` / section 可见度 / 相机所在实体 / LocalPlayer / 无渲染器的 EnderDragonPart）——§5.3 快照必须**在 `extractVisibleEntities` @TAIL 复刻 L821-826 裁剪谓词**（`LevelRenderState.entityRenderStates` 不可用：存 DTO 缺 AABB/id/uuid/rot/motion/health，且 `reset()` L593 在 TAIL 前已清空；`entitiesForRendering()` 原样全收会假阳性），否则"列表有实体、深度是其后方块"→ **假阳性**（纯累积语义最忌）。(2) **vanilla `AABB.clip` 不支持带符号 t_entry**：起点在盒内返回 empty、终点在盒面 s==1.0 亦拒——§5.3/§5.4 须**手写 slab 求交**，§12 该项待核实实为反驳。(3) **`AABB.contains` 半开区间**（`>=min && <max`）：画/画框的可见面恰在盒 maxZ 面 → 被整体拒之门外——改闭区间/带 epsilon。(4) **绊线（tripwire）画进 weather 目标、永不写 main 深度**——§5.4 名单补盲区。(5) **叶子默认 SOLID**（`cutoutLeaves` 选项默认关，开"漂亮叶子"才 CUTOUT）。(6) 实现必需项：`late_debug`（F3 gizmo）在 TAIL 前清深度 → 检测跳过；resize 重建深度纹理 → 每帧 TAIL 重取；`glReadBuffer(GL_NONE)` 补原语；`PACK_ROW_LENGTH` 被 Mojang 置 width 不复位 → 显式管理 pack 状态；GL 行序自底向上 → 垂直翻转；`executePendingTasks` 超时 0 轮询 → PBO 双缓冲；全景截图临时 resize 4096 → 缓冲动态分配；**`entityTranslucent` 默认写深度**（史莱姆外皮/隐形盔甲架/玩家手臂，对 §5.3 反而有利，alpha 阈值 0.1）；透明物品实体进 item_entity 目标 → 深度不可见（已知限制）。
> - **v2.10（逻辑审阅修正：图形配置依赖 + §5 假阳性/一致性）**：(1) **图形配置依赖（重大）**——`Minecraft.useShaderTransparency()`（= `options.improvedTransparency`，默认 false，仅 Fabulous 非 Mac 开）决定半透明层是否独立写深度。默认 Fancy/Fast 下**无独立 translucent target**，TRANSLUCENT/TRIPWIRE 地形直接渲染进 main target 且 pipeline 默认 depthWrite=true（`RenderPipeline` L483）→ **玻璃/水/绊线写主深度**（§3.1.1/§10.8 的"看穿/盲区"仅 Fabulous 成立）。修正：捕获时按 `useShaderTransparency()` **双路径分支**——Fabulous 走原方案（主路径看穿 + §5.4 第四通道）；Fancy/Fast 跳过 §5.4、把 TRANSLUCENT 并入 §5.1 主路径直查、接受"半透明后物体不可见"的最近表面语义（§3.1.1/§5.4/§8/§10/§11 同步修正）。(2) **§5.4 候选盒改实际渲染形状**：水（顶 1/8）/玻璃板/红石线等渲染形状 ≪ 格子，满格 AABB 会让"射线穿过空气部分"在天空背景下误判可见——改用 `state.getShape()` / 流体高度 `getHeight()` 缩放（§5.4）。(3) **§5.4 粗筛须过滤未编译/不可见 section**（复刻 `isSectionCompiledAndVisible`），否则未渲染的半透明方块在深度=远处处假阳性。(4) **§5.3 桶外扩统一 `inflate(0.5)`**（此前桶 0.45 vs 命中 0.5 自相矛盾，0.05 壳漏检带仍在）。(5) **§5.1 近侧回退加"实体相交验证"**：W→cand 线段与任何实体盒相交则跳过回退，防薄实体贴墙时穿透采集后方被遮挡方块。(6) **§5.4 bbox 像素循环显式裁剪屏幕边界**（越界钳 1.0 会静默读成天空 → 假阳性，§4.2）。(7) **§5.4 粗筛谓词类型修正**：`getChunkRenderType` 返回 `ChunkSectionLayer`，比对 `ChunkSectionLayer.TRANSLUCENT`（勿用 `RenderType.translucent()`）。(8) **§5.2 壁挂方块实体朝向过滤**：壁挂式 BE 朝向背对相机且无可视反射 → 不序列化。(9) **§5.3 实体快照 AABB 按 partialTick 插值对齐渲染帧**（`getBoundingBox()` 是 tick 位置，防快速实体位移差漏检）。(10) **方块实体生命周期失步（ghosting）**：`block_entities.nbt` 增量合并永不删除 → 方块被替换后旧 BE 残留；`TerrainRestorer` 在方块类型变化且新方块无 BE 时主动清除旧 BE（§7.2/§7.3）。
> - **v2.11（逻辑审阅修正 ×10，正文各节已同步）**：(1) **§5.3 肢体判别反推不安全**——"背后无方块⇒实体几何"在两类情况下不成立：记录面是**另一实体身体**（实体非方块，被前方实体完全遮挡的后方实体被判可见）、记录面是**薄/部分方块**（压力板/按钮/红石线/雪层/铁轨，ε 后方即空气伪装成肢体）。修复：肢体判别改为**非他体排除 + 非薄方块排除 + 前向空扫**（§5.3）。(2) **§5.4 粗筛成本失控**——"稀疏"不成立，海洋候选可达 10⁵~10⁶、每候选 8 角投影为 O(候选数) 非屏幕面积。修复：候选**中心投影预裁剪** + **屏幕栅格合并**，成本表补粗筛行（§5.4/§9）。(3) **captureRequested 双注入点同帧竞态**——标志在 `extractVisibleEntities`@TAIL 与 `renderLevel`@TAIL 之间置位时深度有、实体快照无。修复：帧内首注入点消费标志、TAIL 校验"本帧已采实体快照"否则推迟（§3.2/§8）。(4) **捕获→解析跨帧偏斜**——depth(帧N) vs resolve 时 world 状态(帧N+1/2)；"无跨帧错位"仅覆盖捕获内部。修复：声明 1~2 帧陈旧容差（§3.2/§8）。(5) **半像素偏移**——深度在像素中心采样，原 §4.1 用角点 NDC 有 ~0.5·像素角距·距离 系统偏移。修复：统一像素中心 `2(x+0.5)/W−1`（§4.1/§4.3/§5.4）。(6) **远距 float32 深度量化误差界错误**——"≤768 格 <1.2cm" 仅 ≤~100 格成立（300 格≈11cm、768 格≈0.7 格）。修复：误差界改为 z 的函数（§4.2/§10.5）。(7) **§5.4 三况措辞矛盾**——"前面有不透明面→可见"方向写反，应为"背景有不透明面"（§5.4）。(8) **肢体贴墙（距后表面<ε）漏检**——单格 `cell(W+dir·ε)` 与 §5.1 实体相交验证不对称。修复：改沿射线向前扫至盒近面，中间有方块判遮挡（保守，宁可漏贴墙肢体也不假阳性）（§5.3）。(9) **marker 盔甲架 0×0 盒退化 slab 求交**——零体积→除零/NaN。修复：slab 对零/负外扩盒返回 null（§5.3/§10.10/§12）。(10) **实体插值缺 prev 位置字段**——快照字段清单无 `xo/yo/zo`，不能用 deltaMovement 代理。修复：补 `getXo/getYo/getZo`（§5.3）。
> - **v2.12（绊线采集：候选驱动 + main 深度，替代"读 weather 目标"）**：§10.8 原"另加读 weather 目标通道"**废止**。**1.21.11 无独立 string 方块**（源码核实：`Blocks.java` 无 `Blocks.STRING`、无 `StringBlock`）——"绊线/线"即 `minecraft:tripwire`（`TripWireBlock`，TRIPWIRE 层，`ItemBlockRenderTypes` L19-20 唯一映射）。其 Fabulous 下处境与半透明方块**相同**：画进 weather 目标、**不写 main 深度**，main 深度记录其**背后**表面——§5.4 统一判定式 `Z_opaque ≥ t_entry − δ` 对绊线天然成立（背景有不透明面/贴天空 → 可见，与玻璃同）。故**粗筛候选谓词由 `ChunkSectionLayer.TRANSLUCENT` 扩展为 `TRANSLUCENT ∪ TRIPWIRE`**，精筛 / 射线-AABB / 深度比较**全部复用 main 深度**，**无需第二路 PBO、不读 weather 目标**（weather 目标混雨雪、需额外反投影路径、且违背 §10.1 覆盖层语义）。配置分支自洽：Fabulous 经扩展后的 §5.4、Fancy/Fast 经 §5.1（回退 main 写深度）。剩余限制降级为"薄线亚像素漏检"（近距可靠、远距/占屏 <1px 同 §10.3）。同步 §3.1.1/§一/§5.4/§6/§8/§9/§10/§11/§12。
> - **v2.13（记忆世界更新触发：快照驱动 + mtime 门控）**：记忆世界三个 restorer 原按 `pollIntervalTicks`（默认 20 tick = 1s）**定期整文件读 + 解压**——文件未变也空转，且 `block_entities.nbt` 增量累积无限增长使浪费随积累放大。改为**每 tick 对源文件做 mtime stat**（`Files.getLastModifiedTime`），**mtime 未变 → 不读不解析**；mtime 变化才走 `readFile` + 指纹门控（内容未变仍不更新世界）。**mtime 仅在读取成功后推进**（文件写入半截读到 null → 保留旧 mtime 下轮重试，防永久跳过）。效果：快照落盘后 ≤1 tick 生效（原 ≤1s）、空闲成本趋零、**无跨进程推送通道**（两 mod 独立进程，见 §二）——比字面"收到 vision/snapshot 就触发"更稳健。`pollIntervalTicks` 默认降为 1（mtime stat 节拍，仍可在 memory.json 调）。同步 §7.4 / §11 Phase 4。
> - **v2.14（§5.2 壁挂朝向过滤作用范围收紧）**：v2.10 的"壁挂式 BE 朝向背对相机跳过序列化"实现作用范围过宽——判据只查 `HORIZONTAL_FACING` 属性，而**箱子/熔炉/木桶/漏斗/发射器/潜影盒**等方块实体同样带该属性，导致这些**可见 BE 的 NBT 被整条漏掉**。修正：过滤前先按方块类白名单 `WallSignBlock` / `WallHangingSignBlock` / `WallBannerBlock` / `WallSkullBlock`（墙挂告示牌/悬挂告示牌/横额/头颅）限定为壁挂式；源码核实四者以 `HorizontalDirectionalBlock.FACING`（=`BlockStateProperties.HORIZONTAL_FACING`，同一单例引用）为朝向，白名单后 `hasProperty(HORIZONTAL_FACING)` 判据仍成立。同步 §5.2。
> - **v2.15（记忆世界视角跟随：采集侧朝向字段 + 每次更新后传送）**：`agentPos` 原只记录观察者方块坐标、不含朝向，且记忆世界只在进入时传送一次 → 观察者移动/转身后玩家视角不跟随。修正：① **采集侧**三个源文件顶层新增 `agentYaw`/`agentPitch`（`camera.yRot()`/`camera.xRot()`，度）随 `agentPos` 一并落盘（`DepthSnapshot` 快照携带相机朝向，`VisionBlockEntityStore` 在朝向变化时标记 dirty → `block_entities.nbt` 随视角刷新）；② **记忆侧** `MemoryRestorer.tick` 在 mtime 变化读取后**额外比较 agent 视角**（与内容指纹解耦：仅转头/移动也触发），视角变化返回新 `AgentPose`，`MemoryWorldManager` 据此 `teleportTo(agentPos, yaw, pitch)`；进入时的一次性传送并入"每次更新后跟随"，`setupPlayer` 只留创造+飞行。向后兼容：旧文件无朝向字段读 NaN → 沿用玩家当前朝向。同步 §6.1/§7.5。
> - **v2.16（天空阈值实现修正：`d_far` 由「投影推导」改为固定 1.0）**：v2.6 的 `depthOfFarPlane` 从投影矩阵反推远平面距离，但假设了 gluPerspective 布局（`m23=2fn/(f-n)`、`m32=-1`）；而 JOML `Matrix4f.perspective` 实为 `m23=-1`、`m32=2fn/(n-f)`（m23/m32 **语义互换**）。用错误布局反推得 `far≈1.11 → dFar≈0.55`，使 `d ≥ d_far` 把 depth≈0.98 的真实地形整体误判成天空 → `nonSkyPixels=0`、三 store 落盘空、记忆世界无变化。修正：远平面在深度缓冲中的值**恒为 1.0**（远平面 NDC z=+1 → depth=(+1+1)/2=1.0，天空/清空同为 1.0），`d_far` 直接固定 1.0、删除投影推导。同步 §4.2/§12。
> - **v2.17（记忆世界禁用重力方块下落）**：记忆世界只复现 agent 看到的**表面**方块，支撑它的底层方块因被遮挡未采集 → 沙/沙砾/混凝土粉末/铁砧/龙蛋等重力方块复原后下方悬空，`onPlace` 排的 2-tick 延时 tick 立即触发 `FallingBlockEntity.fall` → 方块下落、复现被破坏。修正：记忆侧新增**三个 Mixin**，分别对 `FallingBlock.tick`、`ScaffoldingBlock.tick`、`PointedDripstoneBlock.tick` **@HEAD cancellable**——当 `MemoryWorldManager.isMemoryWorld(level)`（按世界名判断，与 `onServerTick` 过滤一致）时 `ci.cancel()`，让方块停在记录位置。三者下落机制各异但都汇聚在 `tick` 内触发：`FallingBlock` 在 `tick` 里 `FallingBlockEntity.fall`；`ScaffoldingBlock`（独立于 `FallingBlock`）在 `tick` 里重算 `DISTANCE`/`BOTTOM`，`DISTANCE` 达 7 时 `FallingBlockEntity.fall`（已 7）或 `destroyBlock`（刚变 7）；`PointedDripstoneBlock`（实现 `Fallable`，非 `FallingBlock` 子类）在 `tick` 里对石笋 `spawnFallingStalactite`、对失去支撑的石柱 `destroyBlock`。为何注入 tick 而非"放置瞬间"：下落由 `onPlace`/`updateShape` 排的延时 tick 触发且相邻方块放置会互相再排 tick，**临时关闭无效**，必须在 tick 层持续禁用。NBT 内容不变（采集端照常记录，纯累积语义不动）。同步 §7.6。
> - **v2.18（agent 坐标精度：方块 → 双精度眼睛坐标）**：`agentPos` 原用 `BlockPos.containing(cameraPos)` 取整到**整数方块坐标**，相机在格内任意位置都塌缩到整点 → 记忆世界只能把玩家传到「方块中心 + 方块脚部」的近似位置，视角还原误差可达 ~1 格。修正：① 采集侧 `ObjectResolver` 直接传相机位置 `Vec3`（双精度 = 游戏坐标精度），三个 store 的 `agentPos` 由整数字符串改为**双精度字符串**（`Double.toString` 无损往返）；`agentYaw`/`agentPitch` 本就用 float（= 游戏 `yRot()`/`xRot()` 精度）不变；② 记忆侧 `AgentPose.pos` 由 `BlockPos` 改为 `Vec3`，`readPose` 用 `Double.parseDouble` 解析（兼容旧整数格式），`teleportToPose` 传送到**精确眼睛坐标**——因 `cameraPos` 是眼睛位置（`camera.position()`），脚部放 `眼睛 − getEyeHeight()` 使记忆世界玩家眼睛精确落在 agent 采集时的眼睛位置；`samePose` 位置比较由 `BlockPos.equals`（1 格容差）收紧为 1 mm（`POS_EPSILON`）。同步 §6.1/§7.5。
> - **v2.19（视觉设置同步：FOV 落盘 + 记忆世界应用）**：真实世界（采集侧）与记忆世界原本各自用本地 `options.txt` 的视场角——采集侧可经 `settings/set` 改 FOV，记忆侧无任何控制，两侧视场角可能不一致 → 站到同一位置同一朝向看到的视场不同。修正：① **采集侧** `DepthSnapshot` 快照新增 `fov`（`options.fov().get()`，`OptionInstance<Integer>`，整数度 = 游戏精度），`ObjectResolver` 把 `agentFov` 一并写入三个源文件顶层（`agentFov` 字段）；② **记忆侧** `AgentPose` 新增 `fov`（旧文件无该字段读哨兵 `-1`），`teleportToPose` 后经 `Minecraft.execute` 在渲染线程 `options.fov().set(...)` 对齐。动态 FOV（疾跑/水下/望远镜）已烤进投影矩阵（§3.3）不重复记录；基础 FOV 之外（渲染距离/画面品质/宽高比）仍不同步（见 §7.7 已知限制）。同步 §6.1/§7.5/§7.7。
> - **v2.20（记忆世界冻结流体流动）**：记忆世界只复现 agent 看到的流体（水/岩浆）表面，其水源/支撑方块可能因被遮挡未采集 → 复原后流体立即流动、向邻格扩散、或（流动流体失去水源时）蒸发成空气，破坏"冻结复现"。修正：记忆侧新增 `FlowingFluidMixin`，对 `FlowingFluid.tick` **@HEAD cancellable**——当 `MemoryWorldManager.isMemoryWorld(level)` 时 `ci.cancel()`。流体的扩散/蒸发/水位重算统一由 `FlowingFluid.tick` 驱动（`ServerLevel.tickFluid` → `FluidState.tick` → `FlowingFluid.tick`；水 `WaterFluid` 与岩浆 `LavaFluid` 均为 `FlowingFluid` 子类且都未重写 `tick`），取消后 `getNewLiquid`（水位重算）、`spread`（邻格扩散）、流动流体蒸发均停止。为何注入 tick 而非放置瞬间：流动由 `onPlace`/`updateShape`/`neighborChanged` 排延时 tick 触发且相邻方块放置会互相再排 tick，须 tick 层持续禁用（同 §7.6 重力）。已知遗留：岩浆的 `randomTick`（`LavaFluid.randomTick` 向可燃方块蔓延火焰，非流动）不在禁用范围。同步 §7.8。
> - **v2.21（记忆世界全局冻结 + 世界时间同步）**：记忆世界是现实世界**某一瞬间**的冻结复现，所有由 tick 推进的变化都应停止且时间应对齐——不止已处理的重力（v2.17）与流体流动（v2.20），还有火焰蔓延、TNT 爆炸、红石信号传输、活塞伸缩、作物生长、冰块/积雪融化、熔炉/漏斗/刷怪笼工作、箭矢飞行、TNT 引信、经验球合并、昼夜/天气推进等。逐块/逐实体写 Mixin 无法穷举，须在**分发层**一次性冻结（§7.9）：① `ServerLevelMixin` 三个注入点 @HEAD cancellable——`tickBlock`（排程方块 tick：红石/活塞/TNT/火焰/树叶衰减/重力…）、`tickFluid`（流体 tick，涵盖 v2.20）、`tickNonPassenger`（实体 tick，覆盖 AI/运动 + TNT 引信/箭矢寿命/经验球合并等自毁——因自毁在实体自身重写的 `tick()` 里不调 `super.tick()`，只能拦调用方）；② `LevelMixin` 的 `tickBlockEntities` @HEAD cancellable（熔炉/漏斗/刷怪笼/活塞动画…）；③ 三条 gamerule 运行时一次性设置——`random_tick_speed=0`（随机 tick：作物/冰/雪/藤蔓/岩浆点火）、`advance_time=false`+`advance_weather=false`（昼夜/天气推进）；④ 三个"非 tick"陷阱——`setBlock` 即时邻居更新改**静默放置**（flag `UPDATE_CLIENTS | UPDATE_SKIP_ALL_SIDEEFFECTS`=818，挂墙方块不因放置顺序被破坏、红石线/绊线保留采集形态、无掉落），实体自毁/寿命靠 `tickNonPassenger` 冻结标记覆盖，方块实体初始状态停于 NBT 进度（不处理，测试期核验）。v2.21 另同步**世界时间**（§7.10）：采集侧把快照时刻 `dayTime` 落盘三个源文件顶层，记忆世界复原时 `setDayTime` 对齐到采集值（`advance_time=false` 只冻结推进、不阻挡设值）。合并策略按已决"验证后删除"：v2.17 三个重力 Mixin + v2.20 `FlowingFluidMixin` 被全局冻结完全涵盖，已移除。同步 §7.9/§7.10。
> - **v2.22（记忆世界减量：射线可见性删除，待实施）**：记忆世界此前是**纯累积**（只增不删，§7.1）——旧方块/实体一旦被记录就永不消失，现实里被挖掉/移走/爆炸后留下"幽灵"。本版引入**有几何证据的减量**：对每个被看到物体的**精确表面点**（`hits.blockHits()` 里的 nudged 落点），从相机做体素 DDA 到该点，记忆世界里**实心 + 不透明**（`Block.isShapeFullBlock`）且被射线穿过的方块/实体格投票删除——「被穿过 ⟺ 现实里不存在」是充要条件（实心不透明物若在，射线必在近表面终止，正是深度缓冲测到的值）。关键边界：① 射线终点用**表面点**而非方块中心（防斜墙擦边误删整面墙）；② 只认**实心 + 不透明**方块可删（玻璃/水/栅栏/压力板"看穿"恰是仍存在的旁证）；③ 只清**表面之前**，不碰表面格及之后（未知保持冻结）；④ **天空射线**是最强删除信号（深度≥远平面 → 到远平面全空，清掉移入开阔空间的旧物）；⑤ **相机所在格**必然空气，无条件即时删；⑥ **投票 + 跨帧累积**（阈值 K≥2，防浮点擦边；真实存在的实心方块票数恒 0，安全自收敛，宁欠勿过）；⑦ 实体减量统一：冻结实体**占用格全部被证明为空**才删（"本次没看到"≠删，可能被遮挡）。数据流：采集侧需补**表面点落盘**（terrain.nbt 每可见方块条目带 surface point，或采集侧直接跑 DDA 写紧凑"清空票格"列表 + sky 射线输出）；记忆侧新增 `RemovalVoter` 通道，删除时同步清理各 restorer 的 applied 表 + `EntityRestorer.FROZEN`，防指纹同步回放。语义转变：纯累积 → **增删收敛到当前可见状态**（被删=被证明不存在的部分，余下仍冻结），§7.1"绝不移除"据此修订。同步 §7.1/§7.11。
> - **v2.23（减量重构：反向通道 + 采集侧逐块深度判定，取代记忆侧 DDA 投票）**：v2.22 的"表面点 + 天空射线 → 记忆侧 DDA 投票（K≥2 跨帧累积 + 证据门控）"有**稀疏采样盲区**——被挖的块背后是单面平墙时仅 1 条表面射线穿过该格（表面点按块去重）、小块/远块在 24px 天空网格下可能 0~2 条；单次运行票数不足 K 时**证据门控**（内容不变只投一次）使**静态玩家**票数永久卡死 <K，永远删不掉。而"深度图传记忆侧 + 逐像素 DDA"不可行（854×480 深度 1.6MB、4K 33MB/快照，渲染线程写盘卡帧；逐像素 DDA 秒级 CPU）——但其准确度判断本身成立（全密度射线只减少"该删没删"、不增加"不该删删了"）。**修正**：把查询反过来——深度图**留在采集侧**（本就有），记忆侧通过**反向通道**把自己**当前存在**的实心不透明块 + 冻结实体占用格写成 `memory_cells.bin`（按 agentPose 距离球过滤，Over-inclusive 安全，几十 KB）；采集侧每快照读取，对每个记忆格做 **§5.4 式逐块投影判定**（投影 8 角 → bbox → 逐像素射线-AABB + `Z_opaque ≥ t_far − δ`，**该格自身投影内 ≥2 像素被证明越过它** → 格在现实中已消失）→ 输出 `deletions` 写 terrain.nbt 顶层；记忆侧读取后 818 置空 + clearStale + 冻结实体占用格全在 deletions 内才 discard。**判定从"跨帧 K≥2 累积"改为"单次快照内 ≥2 像素越过"**——静态玩家一次快照即可删、无指纹门控、无跨帧依赖；逐块判定天然覆盖"背后天空 / 背后不透明块 / 部分遮挡"三况，**v2.22 的 `surface`/`skyRays` 落盘删除**（完全取代）。`removalMaxRayDist` 默认降为 96（深度量化误差 δ 仅 ≤~100 格内可靠，§4.2/§10.5，宁欠勿过）。剩余边界：只删实心不透明格（流体/透明/非实心仍永成幽灵）；记忆世界离线 → 无 cells → 采集侧无删除证据（优雅降级）。同步 §一/§二/§6.1/§7.1/§7.11/§8/§9/§10/§11/§12。
> - **v2.24（两深度锚点：translucent 目标深度 + 首层透明面深度 pass）**：Fabulous 下 translucent 独立目标自身持有深度（先 `copyDepthFrom(main)`、再由 TRANSLUCENT 组在前 LEQUAL 覆盖），与 main 深度组成"首个半透明面 / 首个不透明面"双锚点（逐像素 `translucentDepth ≤ mainDepth`），逐像素产出"水面→水底"完整可见范围。§5.4 新增工序 B（`translucentDepth < mainDepth` 像素反投影精确落位首层半透明，O(像素)、无射线-AABB）+ 工序 C 残留瘦身（首层候选整跳过，残留射线-AABB 只作用于嵌套半透明与绊线）。读取时机经源码核查修正：translucent 深度定稿于 main pass 内 `renderGroup(TRANSLUCENT)`（`ChunkSectionsToRenderMixin.renderGroup` @TAIL 排第二路 PBO），`targets.clear()`/`frame.execute()` 后目标已释放不可读。同步 §3.3/§5.4/§11 Phase 7。
> - **v2.25（半透明掉落物正向归属：工序 D，§5.3.1）**：Fabulous 下玻璃/药水类掉落物画进独立 item_entity 目标、不写 main 深度（§3.1.1），§5.3 深度归属拿不到它们（W 永远落不到其盒上）。但它们已在 `extractVisibleEntities` 实体快照中（含 partialTick 插值 AABB）。修正：新增**工序 D**——对快照中未被 §5.3 报告的 ItemEntity，复用 §5.4 统一判定式 `Z_opaque ≥ t_entry − δ` 正向枚举盒覆盖的全部像素判定可见性（三况统一：背景有不透明面/贴天空/被不透明遮挡），复用 §5.3 盒/slab 求交与 §5.4 bbox 投影几何；**无需新增深度目标/PBO**。Fancy/Fast 下掉落物写 main 深度、§5.3 已覆盖，本工序跳过（与 §5.4 配置分支同构）。剩余：物品 glint 光效层本身仍无深度（不影响物品本体采集）、大堆物品平片摊开 ~3 格超出盒、亚像素掉落物漏（均 §10 已知限制）。同步 §一/§3.1.1/§5.3/§5.3.1/§10/§11 Phase 8。
> - **v2.26（工序 C 重写：双锚点区间射线推进，取代"候选枚举 + 8px 栅格合并"）**：v2.24 工序 C 的候选粗筛含**完整性漏洞**——8px 屏幕栅格合并（同格只留最近，v2.11）把嵌套半透明候选（深水柱中间水格、多层玻璃里层）在精筛**之前**丢弃，违反 §5.4"三者并集 = 全部可见物体"论证，深湖/海洋无法完整复现水柱。修正：**弃用候选集，改为逐像素正向归属**（借 §5.3 实体同构的结构，但核心测试须换——实体法的 W-in-box 依赖"实体写深度、W 落身上"，透明方块不成立，改为区间推进 + 统一判定式）——仅对 `translucentDepth < mainDepth`（存在透明区间）的像素，沿射线体素推进到首个不透明面（Z_opaque），跨 section 用 `maybeHas(透明)` 整节跳步、逐格按统一判定式 `t_entry ≤ Z_opaque − δ` 上报射线实际穿过的**每一个**透明格（含全部嵌套层）。删除粗筛 8 角投影 / 8px 合并 / 首层跳过逻辑；工序 B（首层精确落位 + Z_translucent 锚点）保留。成本从"有损上限 ≈6400 候选"变为"有界推进 = 答案体积"。同步 §一/§3.1.1/§5.4/§5.3.1/§9/§10/§11 Phase 9。
> - **v2.27（方块实体可采集字段分层：严格观察边界，§5.2.1）**：§5.2"方块可见即存 BE 全量 NBT"未区分「外观/状态」与「内容/内部」——现实中箱子/熔炉内部、讲台书本、蜂巢住户等须**交互**才可见，视觉快照不应在无交互时采到。经对 1.21.11 全部 **49 个注册方块实体**逐类源码审计：**客户端 BE 副本只含 `getUpdateTag`/`getUpdatePacket` 同步写入的字段**（区块包 `ClientboundLevelChunkPacketData.BlockEntityInfo.create` L153-158、刷新包 `ClientboundBlockEntityDataPacket.create` L37-39），而容器家族（箱子/陷阱箱/木桶/发射器/投掷器/漏斗/潜影盒/合成器/酿造台/熔炉系/末影箱）**无 update 覆写 → 客户端副本恒空**，内部 Items 仅服务端持久化——"无交互不采内部"此前**靠机制巧合成立、未成契约**（设计审查结论）。修正：§5.2.1 把 BE 字段固化为 **L0 结构 / L1 可观察 / L2 交互内部** 三层，产出前按 **typeId 白名单**过滤；判别主准则＝「该字段信息是否被方块自身渲染/方块状态在**无交互**下呈现」。结论面：**绝大多数类型整类放行**（视觉/配置/机制型无交互内部，白名单=客户端可达全部键）；真正要特殊处理的只有**表 B 两个隐藏内容字段**（`decorated_pot.item` 罐内隐藏单格物、`brushable_block.item` 刷扫揭示前隐藏物，剥离）与**表 D 真实奖励/配置**（vault/trial_spawner 仅服务端、不可达）；campfire/shelf/vault `display_item`/spawner 预览等**世界内可见陈列**照采。未登记 typeId **默认拒采（fail-closed）**，防未来版本某容器把 Items 暴露进 getUpdateTag 即静默越权。实施待审阅（代码步另起，见 §5.2.1 末尾）。同步 §5.2/§6.2。
> - **v2.28（交互内容记忆：L2 独立通道 → 容器内容记录与复现，§5.2.2）**：vision 复现的容器是**空壳**（§5.2.1 剥 Items），agent 在真实世界开过的容器其内部也应成为可读记忆——把"每次交互会话结束（open 绑定 + commit 提交）时的**最终内容**"按坐标写入**独立文件 `containers.nbt`**（定案：独立。不并入 L1 `block_entities.nbt`：高频视觉写者与低频交互写者同文件必竞态丢记录，且 L1 含 Items 违 §5.2.1 纯度），记忆侧新增**容器内容通道**（mtime 门控 + 指纹 + 放置钩子/文件变化/重启重放 + 每轮询对账回填）把记录灌进记忆世界服务端容器 BE——**打开记忆容器 = vanilla 读回 agent 对该容器最近一次所见**。范围含**大箱子整 54 格**（末影箱排除：内容=玩家 EnderInventory、非世界态）：经 1.21.11 源码核实，double 菜单 = `CompoundContainer(RIGHT 半, LEFT 半)`、与点击哪半无关（`ChestBlock.getBlockType`：RIGHT→FIRST / LEFT→SECOND，`DoubleBlockCombiner` 点击 SECOND 半时自动把邻居归为 first）→ 54 格 = [RIGHT 0-26][LEFT 27-53]，采集侧按各半自身方块状态 `TYPE` 拆成**两条 per-pos 记录**，记忆侧填法不变、无合成容器/几何复刻负担。定案：每次提交**覆写该容器所覆盖的全部键**（single↔double 迁移删旧键），记忆侧对账保证"记忆 = 最近一次提交" → 已录容器在记忆世界为**只读参照**（探索者搬动会被下轮对账回卷，v1 不放宽取物）。未交互容器保持空壳——与"无交互不采内部"一致。同步 §5.2/§5.2.1。
> - **v2.29（末影箱纳入：玩家态末影箱记忆，玩家态记忆首例，§5.2.2）**：v2.28 因"末影箱内容＝玩家 `EnderInventory`（玩家态非世界态）"而排除——本版以**并入**方案纳入。机制事实（服务端源码核实）：`EnderChestBlock.openScreen` 对**任意**末影箱都开 `player.getEnderChestInventory()` + `ChestMenu.threeRows`，`EnderChestBlockEntity` 仅 `implements LidBlockEntity`（无容器/无槽）→ **方块零内容，复现只能写记忆世界本地玩家的末影箱**，故打开任意一块存在的末影箱都读到该记忆内容；采集端以 **open 绑定格 block id=`minecraft:ender_chest`** 判定会话（27 格菜单与单箱/木桶/潜影盒同型，菜单本身不可分辨）。记录并入 `containers.nbt` **顶层 `enderInventory` 键**（与 per-pos 条目同一 commit 写者、同一 CONTAINER 通道 poll，无第二写者）；记忆侧在记录存在时把 27 格写入本地 server player 末影箱并随容器对账回填 → **全局 + 只读**（探索者搬动即回填、不能用记忆末影箱存自己的东西，已接受）。玩家态记忆族 v1 种子：待该族（主物品栏/护甲/经验等）扩到第二个成员时拆为独立 `player_state.nbt`/通道（并入为临时落位）。同步 §5.2/§5.2.1/§5.2.2。
> - **v2.30（采集侧会话化落地：容器/末影箱内容提交机制，stevex-template，§5.2.2 末）**：v2.28/29 定下文件契约与拆分规则，本版把**采集侧会话化机制**落定并实现（§5.2.2 末「采集侧会话化机制（v2.30）」块）。机制 = 客户端 tick **open 绑定**（END_CLIENT_TICK 屏幕迁移检测；首个容器屏幕 tick 用当时准星命中块绑定并校验∈持物容器家族/末影箱）+ **commit 触发**（`container/close` WS 点在关箱前**同步读最终内容**提交；tick 兜底在探测到未走 WS 的关屏时以缓存的最近内容提交）+ **内容来源＝打开的菜单容器区**（客户端 `containerMenu.slots` 前导、`slot.container ≠ 玩家物品栏` 的槽 = 方块容器区；区序/槽号与服务端 `CompoundContainer` 同构）+ **double 拆分**（区 54 格按各半实际 `state.type`：RIGHT 半←[0,27)、LEFT 半←[27,54)，邻居半经四邻扫描同 block+同 facing+互补 type 定位；区 27 格 = 本半 + 删伙伴旧键）+ **末影分流**（绑定格 block id=`minecraft:ender_chest` → 覆写顶层 `enderInventory` 27 格 + 该格 `items=[]` per-pos 出现记录）+ **物品序列化** `ItemStack.CODEC`（= `{id,count?,components?}`，与记忆侧解析对称；1.21.11 无 `save(registryAccess)` 便捷法）。残留边界更新：commit 只发生在受控关箱点（WS `container/close`，主路径；agent 自动流恒走此），Esc 等非受控关屏依赖 tick 兜底缓存（内容可能落后 ≤1 帧，极小窗口）。同步 §5.2/§5.2.1/§5.2.2。
> - **v2.31（生物群系全链路记录与复原：独立 biomes.nbt 逐 cell 通道，见 `生物群系复原设计方案.md`）**：视觉链路此前**无任何群系采集**（唯一 biome 取样是 `f3` 的玩家脚下单点，与视觉链路无关），而复原端 `createVoidWorldDimensions` 给每维度**钉死单一群系**（主世界 plains / 下界 nether_wastes / 末地 the_end）→ 在雪原/沼泽/丛林拍的场景复原后一律按平原渲染草/树叶色偏、天空/雾、降水——"方块都在，肤色错了"，是唯一一类"看到了却还原不了"的环境信息。修正（全链路，用户定案）：**采集侧新增群系通道**——群系存在区块里、以 **4×4×4 quart cell** 单值存储（`LevelChunkSection.biomes`，私有只读），故新增 `VisionBiomeStore`（union 单调累积，独立 `stevex/vision/biomes.nbt`，格式 `{timestamp, cells:{"qx,qy,qz": biomeId}}`，cell key=quart 坐标）**逐 cell 写、绝不逐方块写**（同 cell 所有方块 `getBiome` 必然相同＝存储保证，非比较判断）；采样源 = `ObjectResolver.resolve` 末尾的本帧可见方块集 + **相机 cell 锚点**（零额外扫描，terrain 已含 §5.4 透明补采）；只对不在 union 的 cell 解析（O(新增)），新增>0 才整体覆盖写。`snapshot` 的 `storeStats` 增 `"biomes":{cells,added}`。**记忆端新增 `BiomeRestorer`**：mtime 门控 + `prevIds` 快照 diff 出新 cell → 归组 chunk（`qx>>2,qz>>2`）入 `pendingChunks` → 每 tick `drainPending` 对**已加载**块 apply（限量 64/tick，未加载留待补填）。写入完全复用 vanilla `/fillbiome` 公开范式：`chunk.fillBiomesFromNoise(resolver, randomState().sampler())`（resolver 对记录 cell 返回记录 holder、未记录返回 `chunk.getNoiseBiome` 现状 → 只覆盖不污染）+ `markUnsaved()`（落盘持久）+ `chunkMap.resendBiomesForChunks`（→ `ClientboundChunksBiomesPacket` 客户端即时重着色），**零 Mixin / 零新包**。`MemoryWorldManager` 静态 `BIOME` + `onServerStart` 链 + tick 插在 `CONTAINER` 后 `DELETION` 前（BIOME 不改变方块，与减量无耦合）+ `forceRefresh`；`MemoryConfig` 增 `biomeFile`/`resolveBiomeFile`；**`StevexTest.java` 零改动**（补填偏差：tick-scan 取代 CHUNK_LOAD 事件，块随传送加载、≤1 tick 补填）。仅主世界、只覆盖可见 cell、注册表须与采集端共享（解析失败 WARN 跳过）。同步 §4.3/§5.4/§一/`生物群系复原设计方案.md`。
> - **v2.32（世界类型区分：视觉链路携带维度 + 记忆世界按维分桶镜像复原，见 `世界类型区分与镜像复原设计方案.md`）**：此前视觉链路全部数据**不含维度信息**——agent 在下界/末地（或任一 mod 维）采集的内容以主世界坐标放进记忆主世界（下界岩浆池悬主世界天上；累积 union 文件 `block_entities.nbt`/`biomes.nbt` 的裸 `"x,y,z"`/`"qx,qy,qz"` 键与主世界同坐标必然互相覆盖）。修正（全链路，用户定案：**通用维度 id 全记录 + 单文件分维度桶 + 镜像跟随复原**）：**采集端**各视觉 store 写盘统一为顶层 `{ "currentDimension": <dimId>, "worlds": { <dimId>: <本维正文> } }`（两端各一份 `WorldsFile.wrap/read` 助手）——快照型（terrain/entities）只覆写当前维桶、其余维桶原样保留（换维不再清掉上一维"最后可见快照"），累积型（block_entities/biomes）外层按维、union 在各自维桶内做，`containers` 的 per-pos 记录分桶而**末影箱 `enderInventory`（玩家态）保持顶层全局**（真实 MC 末影箱跨维同一）；`ObjectResolver` 把 `level.dimension()` 透传到各 store；`snapshot` 响应顶层补 `"dimension"`；反向通道 `memory_cells.bin` 格式升 **version=2**（头部在 version 之后带 UTF-8 维 id 段，`MemoryCellsReader.CellsData` 增 `dimension`），`DeletionJudge`/调用方只对 `cells.dimension() == 本快照维` 判删（镜像落后时宁缺勿滥、绝不跨维误删；version=1 旧文件无维标签 → 删除证据置空）。**记忆端**：`MemoryWorldManager` 每 tick 以 terrain.nbt 顶层 `currentDimension`（`TerrainRestorer.activeDimensionId()`）为活动维权威源，白名单直取 vanilla 三维 ServerLevel 驱动、未知自定义维 300-tick 节流告警 + 降级（数据保留不崩）；各复原器（terrain/entity/BE/biome/container）**状态按维隔离**、只 apply `level.dimension()` 自己的维桶（坐标永不跨维碰撞；读取代际 × 每维一次交付，换维过渡 tick 也不错写）；减量/反向通道同限活动维；**镜像跟随**把记忆玩家按该维桶 agent 姿态在管线底部 `teleportToPose(player, 活动维 level, pose)` 跨维传送（`TeleportTransition` changeDimension 公开路径，无新 Mixin），门控 = **跨维 ‖ 姿态变化**（同姿态数值跨维也须换）。兼容：读侧无 `worlds` 键 → 旧文件整份视为 overworld 桶，旧存档无迁移。同步 §5.2.2/§6.1/§7.1/§7.11/§一/`已实现内容.md`。
> - **v2.33（记忆世界传送门冻结：传送门=纯装饰，见 `传送门冻结设计方案.md`）**：记忆世界复现了 agent
>   看到的下界/末地传送门方块，但探索者站入后 vanilla 会真传送——目标虚空维（v2.30 `voidNetherEnd`）没有现成
>   出口门 → `NetherPortalBlock.getExitPortal` 走 `PortalForcer.createPortal` 就地**新建 obsidian 门**污染镜像；
>   末地门 `EndPortalBlock.getPortalDestination` 会 `EndPlatformFeature.createEndPlatform` 铺平台、在末地侧还会弹
>   End credits。修正（记忆端，用户定案：**完全冻结**）：1.21.11 传送门旅行是纯实体驱动——方块 `entityInside`
>   先查 `Entity.canUsePortal(false)` 才 `setAsInsidePortal` 登记 `PortalProcessor` 累积，满时限后
>   `Entity.handlePortal`（baseTick:503）以 `canUsePortal(false)` 放行旅行 → `getPortalDestination`（门创建/平台在
>   此）→ `teleport`。该布尔是下界门/末地门/末地折跃门/跨维珍珠 owner 判定的**统一闸门**，故新增单个 Mixin
>   `com.example.mixin.PortalTravelMixin` 在**基类** `Entity.canUsePortal(boolean)` HEAD 强制返回 false（记忆世界
>   `ServerLevel` 判定；LivingEntity/Creaking 覆写均 `&& super` → 必生效）→ 整条链第一步短路：不登记、不累积、
>   不旅行、永不创建目的地门/平台/credits；客户端 `animateTick` 粒子声音照常 → 门变装饰、可自由穿过。
>   为什么 v2.21 全局冻结（§7.9 排程 tick/冻结实体 tick 分发）没覆盖：传送门使用走玩家自身移动碰撞
>   `entityInside` + `baseTick→handlePortal`，非排程 tick、玩家 tick 又未被停。镜像跟随（v2.32）跨维摆放经
>   `teleportTo`→`Entity.teleport(TeleportTransition)`，不经 `canUsePortal` → 不受影响。采集端零改动。
>   同步 §7.9/§7.1/§一/`已实现内容.md`。
> - **v2.36（半透明方块减量：流体 + 满格透明的 translucent 深度在场判定，§7.12，⏳ 设计待审阅）**：§7.11 减量只删"实心 + 不透明"格（边界①），而 **Fabulous 下水画进 translucent 独立目标、不写 main 深度**——main 深度场对水无"在场/不在场"信号差（水在时记录池底、不在时仍记录同一池底）→ 排干的水成幽灵（§10-13 原样接受）；水若硬塞给不透明判据更会在 Fabulous 下**每帧把活水误判消失**（水不挡 main 射线、恒 `Z_opaque ≥ t_far`）。修正（方向定案）：把消失判据**同构平移到 translucent 深度场**——该目标每像素 = `copyDepthFrom(main)` 后被 TRANSLUCENT 组在前 **LEQUAL** 覆盖（"首个半透明面"，恒 `translucentDepth ≤ mainDepth`，v2.24 已核实），"无半透明在前"被自编码为主深度拷贝（`t == m`）而非独立空值。故对记忆上报的半透明格 C（流体 / 满格透明）用 §7.11 同款几何（8 角投影 bbox + 逐像素 slab + `距离(场值) ≥ t_far − δ`，≥2 像素）判消失：活格必写 `t < t_far` → 保留；被移除 → 原边界像素集体转 `t==m ≥ t_far` → **整片一轮删**；不透明墙挡前读 `t==m==墙 < t_far` → 欠删保护——**镜像场自编码遮挡，单一数值判据天然不误删活水**（与 main 场判据唯一的语义差异：无表面哨兵由"天空 1.0"换成"主深度拷贝"）。判据场随采集通道选：Fabulous 下水/满格透明走 translucent 场；岩浆恒 SOLID 写 main（§10-2）、Fancy/Fast 下水/岩浆/玻璃块都写 main → 走现有 main 场判据，只需放宽 reporter/applier 的 `isSolidOpaque` 门槛到流体 ∪ 满格透明。表层所有权边界：逐格在场证明只对"拥有该像素首个半透明面"的格成立，整片消失一轮删、**部分收缩**残留浅层水时深层格欠删（残水自罩、视觉无害，与不透明判据"被挡欠删"同类）；**满格透明方块（玻璃块/染色玻璃/冰等）与流体同判据、本版一并纳入**（reporter 谓词放宽为"流体 ∪ 满格透明"、applier 相应放行）、**非满形状透明块（玻璃板/栅栏/压力板…）需几何感知采样才可判**、本版欠删（接受）。两端改动：`memory_cells.bin` 升 **version=3**（opaque 段 + 单独 translucent 段：流体 ∪ 满格透明）；采集侧 `fabulous && hasTranslucentDepth()` 时对 cells.translucent 段追加 translucent 场判定；`DeletionApplier` 对"记忆世界里当前仍是流体/满格透明"的删除条目放行 818 置空（非满形状/段外仍拒删）。同步 §7.11/§7.12/§8/§9/§10/§11/§12。

---

## 一、设计变更说明（v1 → v2）

| 维度 | v1 | v2 |
|---|---|---|
| 可见性来源 | `visibleSections`（区块级视锥体裁剪） | GPU 深度缓冲（逐像素遮挡真值） |
| 采集驱动 | 遍历可见 section 的全部非空气方块（O(4096×N)） | 深度反投影 → 坐标去重 → 直查 |
| 方块可见性 | 全收（含墙后），靠软件遮挡近似过滤 | **精确**：只反投影到的方块才被记录 |
| 方块实体 | 从 CompiledSectionMesh 取（section 级） | 搭方块便车：方块可见 → 方块实体可见 |
| 实体 | 遍历 `entitiesForRendering()` 按 section 过滤 | **正向像素归属**：渲染实体列表快照（v2.9）+ SectionPos 桶粗过滤 + 闭区间 `AABB.contains` + 深度排序 + 肢体判别精判（近似）；**玻璃/药水类半透明掉落物由工序 D 补采（v2.25，§5.3.1）** |
| 半透明/绊线方块 | 不获取（深度看穿） | **第四通道（v2.7，§5.4）——仅 Fabulous（`useShaderTransparency()=true`）**：两深度锚点（v2.24）+ **区间射线推进正向归属（v2.26）**——对透明区间像素沿射线推进、上报全部可见透明格（含嵌套/绊线，**TRANSLUCENT ∪ TRIPWIRE 层，v2.12**），精确；**Fancy/Fast 下并入主路径**（§5.1 直查半透明/绊线表面，接受其后物体被遮挡，见 §3.1.1/v2.10） |
| 实体全量 NBT | Tier-2 按需（`vision/entity`，TTL 缓存） | **不变**（深度只识别可见 uuid，不序列化） |
| 记忆世界 | 快照 + `scannedSections` 移除权威 | **纯累积 → 有证据减量（v2.22→v2.23）**：记录每次看到的内容；被"**反向通道 + 采集侧逐块深度判定**"（v2.23，取代 v2.22 记忆侧 DDA 投票）证明消失的实心不透明块/实体才删 |
| 数据层 | `VisionBlockEntityStore` / `VisionTerrainStore` / `VisionEntityStore` | **原样复用** |

**核心判断**：深度缓冲给出的是"每个像素最近不透明表面的深度"（逐像素精确），反投影后得到精确的世界坐标。**坐标即索引**——方块/方块实体是网格，坐标直查；实体不是网格，坐标只能匹配。深度方案替代的是"采集来源 + 可见性判定"，NBT 序列化 / 存储格式 / 持久化文件 / 按需查询端点全部保留。

---

## 二、整体架构

```
┌──────────────────────────────────────────────────────────────────────┐
│  API 线程（WebSocket）                                                │
│    vision/snapshot → 置 captureRequested 标志 → latch 等待             │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────┐
│  渲染线程  LevelRenderer.renderLevel()                                │
│    ┌─────────────────────────────────────────────┐                   │
│    │  addMainPass:                               │                   │
│    │    OPAQUE组(不透明+切出) → 实体 → 方块实体     │                   │
│    │    → translucent(独立目标) → tripwire(weather)│                  │
│    │    只有前三者写入 main target 深度纹理          │                   │
│    └────────────────────────┬──────────────────────┘                   │
│                             │                                          │
│  [Mixin Hook #1]  LevelRenderer.renderLevel @TAIL                     │
│    ┌────────────────────────▼──────────────────────┐                   │
│    │  若 captureRequested（tryCapture）：            │                   │
│    │  ① 矩阵/相机：直接取 renderLevel 方法参数        │                   │
│    │  ② 手写 GL PBO 回读深度（GL_DEPTH_ATTACHMENT） │                   │
│    │     queueFencedTask 异步，回调置快照 + latch     │                   │
│    └────────────────────────┬──────────────────────┘                   │
│                             │ （之后 GameRenderer 才清深度 1.0）        │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ 回读完成
┌──────────────────────────────▼───────────────────────────────────────┐
│  可见性判定（Unprojector：API 线程纯数学；ObjectResolver：渲染线程）    │
│    ┌──────────────────────────────────────────────┐                   │
│    │ 反投影：像素 → NDC → inverse(P×V) → 相机相对坐标 │                   │
│    │    → +camPos → 沿射线推 ε → 方块格去重（存点）   │                   │
│    │    同步按 SectionPos 桶筛出实体候选像素（存原始点）│                  │
│    └───────────────────────┬──────────────────────┘                   │
│                            │                                          │
│    ┌───────────────────────▼──────────────────────┐                   │
│    │ 四路查询（§5：坐标直查 + 半透明/绊线通道）       │                   │
│    │  ① 方块：BlockState + air 近侧回退             │                   │
│    │  ② 方块实体：→ saveWithFullMetadata            │                   │
│    │  ③ 实体（正向）：SectionPos 桶 → 深度排序精判  │                   │
│    │     （AABB.contains + 射线-AABB，见 §5.3）      │                   │
│    │  ④ 半透明/绊线方块（§5.4，v2.26）：区间射线推进  │                   │
│    │     → 逐像素归属全部可见透明格（含嵌套/绊线）    │                   │
│    └───────────────────────┬──────────────────────┘                   │
│                            │                                          │
│    ┌───────────────────────▼──────────────────────┐                   │
│    │ 数据层复用（与 v1 相同）                        │                   │
│    │  block_entities.nbt（增量）                    │                   │
│    │  terrain.nbt（快照，只存可见方块）              │                   │
│    │  entities.nbt（快照，只存可见实体）             │                   │
│    └──────────────────────────────────────────────┘                   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ 文件（terrain.nbt 含 v2.23 deletions / entities / block_entities）
┌──────────────────────────────▼───────────────────────────────────────┐
│  记忆世界（另一客户端）                                                 │
│    TerrainRestorer：放置/更新（818 静默放置）                          │
│    DeletionApplier：应用 deletions（818 置空 + clearStale）           │
│    EntityRestorer：放置/移动/冻结；占用格全在 deletions 内才 discard    │
│    MemoryRestorer：方块实体，增量合并（不变）                          │
│    MemoryCellReporter（v2.23 反向通道）：当前实心不透明块+冻结实体占用格  │
│      按 agentPose 距离球过滤 → cells 文件（几十 KB，mtime 门控）        │
└──────────────────────────────▲───────────────────────────────────────┘
                               │ cells 文件（反向：记忆 → 采集）→ 采集侧 DeletionJudge 逐块判定
```

---

## 三、深度捕获（DepthCapture）

### 3.1 渲染管线关键事实（1.21.11 源码核实）

以下事实全部基于 `decompiled_src_vf/client` 反编译源码：

| 事实 | 位置 | 含义 |
|---|---|---|
| 渲染器是 **FrameGraph 化**的 | `LevelRenderer.renderLevel` L515-590 | 所有 pass 在 `frame.execute()` 里按序执行；`graphics` 走 `RenderSystem.getDevice()` 设备抽象 |
| 主 pass 绘制顺序 | `LevelRenderer.addMainPass` L647 → L663-666 → L709-712 | **OPAQUE 组**（SOLID+CUTOUT 层）→ 实体（submitEntities）→ 方块实体（submitBlockEntities）→ **TRANSLUCENT 组 → 独立 translucent 目标**（先 `copyDepthFrom(main)`）→ TRIPWIRE → weather 目标。**只有前三者写 main target 深度** |
| **深度在帧内被清空** | `GameRenderer.renderLevel` **L796** | 画手持物品前 `clearDepthTexture(main depth, 1.0)` |
| GUI 模糊也清深度 | `GuiRenderer` L238 | 有 blur 后 GUI 内容时清深度 |
| 深度生命周期 | L788 之后 → L796 之前 | **唯一干净的捕获窗口**，见 3.2 |
| vanilla 回读是 **color-only** | `GlCommandEncoder` L684-689 | `copyTextureToBuffer` 把源纹理绑到 **GL_COLOR_ATTACHMENT0** 读回——深度纹理挂 color 附件 FBO 不完整 → `glReadPixels` 报 **GL_INVALID_FRAMEBUFFER_OPERATION（1286）**。深度回读必须**手写 GL PBO**（见 §3.4） |
| 同款机制 | `Screenshot.java` L80 | 截图就是用 `copyTextureToBuffer` 拷颜色纹理 |
| 深度纹理访问 | `RenderTarget.getDepthTexture()` L118 | 返回 `GpuTexture` |

**关键结论**：
1. 深度缓冲是**帧内瞬态**的——帧结束（GUI 渲染）后是垃圾（全 1.0），**必须在捕获窗口内读**；
2. **vanilla `copyTextureToBuffer` 是 color-only**——源绑到 GL_COLOR_ATTACHMENT0，深度纹理挂上去 FBO 不完整 → 1286。深度回读必须**手写 GL PBO**（见 §3.4）；
3. main pass 里 TRANSLUCENT 组（水/玻璃/染色玻璃）的深度行为**配置相关（v2.10）**：**仅当 `Minecraft.useShaderTransparency()`（Fabulous）**时渲染进**独立 target**、不写 main 深度（深度看穿）；**默认 Fancy/Fast 下无独立 target**，TRANSLUCENT 直接画进 main target 且 pipeline 默认 depthWrite=true → **写主深度**（半透明后物体被遮挡）；**绊线同理（v2.12）**——TRIPWIRE 组在 Fabulous 下画进 weather 目标、不写 main 深度，Fancy/Fast 回退 main 写深度；**岩浆例外**（SOLID 层，见 §3.1.1）；**v2.24（两深度锚点）**：Fabulous 下 translucent 独立目标**自身也持有深度图**——先 `copyDepthFrom(main)`、再由 TRANSLUCENT 组在更近处 LEQUAL+depthWrite 覆盖 ⇒ **每像素 `translucentDepth ≤ mainDepth`**；读取它即得"首个半透明面"锚点（水面/玻璃面），与 main 深度（首个不透明面）组成双锚点，逐像素产出"水面→水底"完整可见范围（见 §5.4）；
4. 深度纹理格式 `DEPTH32`（4 字节 float/像素），范围 [0,1]（1.0=远/天空/clear 值），行号 y 自底向上（GL 原点左下角）。

### 3.1.1 深度图的精确语义（源码核实）

块 → 渲染层映射在 `ItemBlockRenderTypes.getChunkRenderType`（L361-369），层 → pipeline / 目标在 `ChunkSectionLayer`（L8-11）+ `ChunkSectionLayerGroup`（L8-10）：

| 内容 | 渲染层 / pipeline | 是否写 main 深度 | 源码依据 |
|---|---|---|---|
| 不透明方块（含半砖等全部默认块） | SOLID → SOLID_TERRAIN | ✅ | `RenderPipeline` 默认 `writeDepth=true`（L483）、深度测试 LEQUAL（L478）；`ChunkSectionLayer` L8 |
| **玻璃 / 玻璃板 / 染色玻璃 / 冰 / 黏液块 / 蜜块 / 红石线 / 下界传送门** | TRANSLUCENT → TRANSLUCENT_TERRAIN | ⚠️ **配置相关（v2.10）**：Fabulous（`useShaderTransparency`）→ 独立 translucent 目标、不写 main 深度；**默认 Fancy/Fast → 画进 main target 且 depthWrite=true（pipeline 默认）→ 写主深度** | `ItemBlockRenderTypes` L312-353；`LevelRenderer` L705-710（`copyDepthFrom` 仅 chain 非空时执行）；`ChunkSectionLayerGroup.outputTarget()` L28-37 回退 main；`RenderPipeline` L483 默认 depthWrite=true |
| **铁栅栏 / 铁链 / 叶子 / 火把 / 门 / 梯子 / 铁轨 / 农作物等** | CUTOUT → CUTOUT_TERRAIN | ✅（alpha≥0.5 处） | `ItemBlockRenderTypes` L23-27 等；`terrain.fsh` L91-95 `#ifdef ALPHA_CUTOUT if (color.a < 0.5) discard;`——在写深度**前**丢弃，只在实体像素上写深度，缝隙看穿。**⚠️ 叶子默认例外（v2.9）**：`cutoutLeaves` 选项默认关 → 叶子走 SOLID（深度上不透光），仅开启"漂亮叶子"才 CUTOUT（`LevelRenderer` L294） |
| **岩浆**（流体） | **SOLID**（流体映射未列岩浆） | ✅ | `ItemBlockRenderTypes` L355-358 只有水 → `getRenderLayer` else 返回 SOLID → 深度上岩浆始终不透光 |
| 水（流体） | TRANSLUCENT | ⚠️ **配置相关（v2.10）**：同玻璃（Fabulous 不写 main 深度 / Fancy-Fast 写主深度） | `ItemBlockRenderTypes` L356-357 |
| **绊线 / 线（tripwire/string）** | TRIPWIRE → TRIPWIRE_TERRAIN | ⚠️ **配置相关（v2.10/v2.12）**：Fabulous → weather 目标、不写 main 深度（**可采集**：§5.4 透明格判定已含 TRIPWIRE 层，主深度看穿 + 区间射线推进归属（v2.26），见 §5.4/§10.8）；**默认 Fancy/Fast → 回退 main target、depthWrite=true → 写主深度**（§5.1 可直接拾取，无盲区） | `LevelRenderer` L711-712；`ChunkSectionLayerGroup` L28-37/L33；`RenderPipeline` L483 |
| 实体 / 方块实体 | 实体 pipeline / 方块实体 pipeline | ✅（多数） | `LevelRenderer.addMainPass` 内 `submitEntities`/`submitBlockEntities` 后 `endBatch` 画进 main target。**v2.9 细化**：`ENTITY_TRANSLUCENT` 亦默认写深度（史莱姆外皮/隐形盔甲架/玩家手臂，`RenderPipelines` L239）；**透明掉落物（玻璃类/药水）与物品 glint 画进独立 item_entity 目标、深度不回写 main**（`RenderTypes` L261 / `Sheets` L50-51）；不写深度的实体效果：`ENTITY_TRANSLUCENT_EMISSIVE`/`ENTITY_NO_OUTLINE`/`EYES`/`ENTITY_SHADOW`（`RenderPipelines` L257/L275/L315/L339） |
| 粒子 / 云 / 天气 / 手持物品 / GUI | 独立 target / 后处理 | ❌ | `LevelRenderer` particles/clouds/weather 目标；GUI 后处理 `POST_PROCESSING_SNIPPET`（NO_DEPTH_TEST + depthWrite=false） |

**与深度正交的效果（全部颜色域，从不碰深度）**：
- 药水**失明** → `BlindnessFogEnvironment`（雾环境，改雾参数）；
- 药水**黑暗** → `LightTexture` 光图 gamma（L114 `darknessGamma`）；
- **抗火药水** → 只改 `LavaFogEnvironment.setupFog` 的**雾距离**（L26-32：有抗火 0→5，无 0.25→1.0）；
- **环境亮度 / 光照** → lightmap（Sampler2）+ vertexColor，仅片段颜色。

**一句话语义**：深度图 = **"几何上最近的不透明表面"**。物理上挡路的（含岩浆、铁栅栏实体像素）被记录；视觉上透明但不挡几何的（水、玻璃、染色玻璃、**绊线**）**在 Fabulous 下被看穿、在默认 Fancy/Fast 下因写深度而被记录为最近表面**（v2.10 配置分支；v2.12：绊线与半透明同语义，Fabulous 下经 §5.4 扩展谓词采集）；光照 / 药水 / 雾与深度无关。

### 3.2 捕获窗口与注入点

- **注入点（已实现）**：`LevelRendererMixin` 注入 `LevelRenderer.renderLevel` 的 `@At("TAIL")`：
  - 该点世界已渲染完毕——`addMainPass` 内 OPAQUE 地形 + 实体 + 方块实体已写入 main 深度，是**完整的世界深度**；
  - GameRenderer 在 `renderLevel` 返回后才 `clearDepthTexture(main depth, 1.0)`（画手持物品前）——TAIL 在清除**之前**；
  - **TAIL 注入必须声明目标方法全部 10 个参数**（Mixin 严格匹配完整签名）——投影/模型视图矩阵/相机直接取方法参数，**无需重建模型视图、无需 RenderSystem 状态**；
- **门控**：`captureRequested` 标志——API 线程 `requestCapture()` 置位并新建 latch，渲染线程在下一帧 TAIL 执行 `tryCapture(...)`，PBO 回调完成置快照并 countDown，API 线程 `awaitSnapshot(超时)` 取回。**不做每帧捕获**，与 v1 的"API 驱动"一致。**⚠️ 双注入点同帧握手（v2.11）**：实体快照在 `extractVisibleEntities` @TAIL 采集、深度在 `renderLevel` @TAIL 采集，两者共用 captureRequested——若标志在帧内两注入点**之间**被置位（即渲染进行中），本帧 TAIL 会捕获深度却无实体快照（或快照过期），整帧快照缺实体。规定：**标志由帧内第一个注入点消费**（`extractVisibleEntities` 先于 `renderLevel` 执行，正常帧由前者消费并采实体快照）；`renderLevel` TAIL 捕获前**校验"本帧已采实体快照"**，未采集则放弃本帧深度、保留标志推迟到下一帧——保证深度图与实体快照永远同帧（见 §8 接线）。
- **数据一致性**：捕获在渲染线程该帧内完成，深度与相机/矩阵同帧快照，**无跨帧错位**（该断言范围 = **捕获内部三件套**：深度/相机/矩阵，见 §3.3）。**⚠️ 捕获→解析偏斜（v2.11 声明）**：`ObjectResolver.resolve` 经 `Minecraft.execute` 实际运行在捕获帧之后 1~2 帧，其读取的 blockstate / section 可见性 / `getShape`/`getHeight` 相对深度图**晚 1~2 tick**——按"**世界状态允许 1~2 帧陈旧**"接受（§8；个别场景误报本帧未渲染/漏报刚变化的方块，纯累积语义可容忍）；如需严格对齐，把 resolve 排队进捕获同帧。
- **捕获前哨（v2.9）**：
  - `late_debug` pass（F3 开启且 always-on-top gizmo 非空）会在 TAIL 前 `clearDepthTexture(main, 1.0)`（`LevelRenderer` L799-801）→ 捕获到全天空假深度，须检测并**跳过该帧**；
  - 主目标 resize 时深度纹理被 `destroyBuffers` 重建（`RenderTarget` L30-34）→ **每帧 TAIL 重新 `getDepthTexture()` 并重挂临时 FBO**，不可缓存句柄。

### 3.3 捕获的数据

| 数据 | 来源 | 说明 |
|---|---|---|
| 深度纹理 | `mainRenderTarget.getDepthTexture()` | `DEPTH32`（4 字节 float/像素），范围 [0,1] |
| **translucent 目标深度（v2.24）** | `levelRenderer.getTranslucentTarget().getDepthTexture()`（**仅 Fabulous 非 null**） | 同 DEPTH32、同尺寸、**同投影** → 与 main 深度逐像素 1:1 对齐；内容 = `copyDepthFrom(main)` 后被 TRANSLUCENT 组（水/玻璃等）在前覆盖 → "**首个半透明面**"锚点。绊线（TRIPWIRE→weather 目标）、掉落物（item_entity 目标）不在其中。第二路 PBO 与 main 同帧 TAIL 回读（§3.4 同法） |
| 深度尺寸 | `depth.getWidth(0)/getHeight(0)` | = 窗口 framebuffer **物理像素**（`glfwGetFramebufferSize`；`Minecraft` L494/L1422-1423 按 `window.getWidth()/getHeight()` 建/同步 main target），**1:1 无缩放**；GUI scale 只影响 GUI 层，不影响主目标；按原生分辨率读回，**无采样/降采样** |
| 投影矩阵 | `LevelRenderer.renderLevel` 方法参数 `projectionMatrix`（Mixin TAIL 直接取） | **含 FOV 动态变化（疾跑/水下/望远镜）、bob、晕眩 skew**——是世界 pass 实际用的这份 |
| 模型视图矩阵 | 方法参数 `modelViewMatrix` | 无需重建、无需 RenderSystem 状态；**只含旋转、平移在顶点着色器**（见 §4.1） |
| 相机位置 | 方法参数 `camera.position()` | 反投影校验用 |
| 时间戳 | 捕获时 `System.currentTimeMillis()` | 快照标识 |

> 矩阵来源已确认：TAIL 注入直接取方法参数——**不依赖 `RenderSystem.getProjectionMatrix()` 存在性、不依赖局部变量表、不需要重建模型视图**，实现期三个待核实项全部消解（见 §12）。

### 3.4 异步回读（手写 GL PBO，已实现）

**为什么不能直接用 `copyTextureToBuffer`**：vanilla 实现（`GlCommandEncoder` L684-689）把源纹理绑定到 **GL_COLOR_ATTACHMENT0** 读回——深度纹理挂 color 附件 → FBO 不完整 → `glReadPixels` 报 GL_INVALID_FRAMEBUFFER_OPERATION（1286）。`copyTextureToBuffer` 是 **color-only**。

**手写 GL 深度回读**（沿用 vanilla 的异步 PBO 机制，`GlStateManager` 原语）：

```
① 创建 PBO（GL_PIXEL_PACK_BUFFER，usage GL_STREAM_READ）+ 临时 read FBO；
② 深度纹理挂 GL_DEPTH_ATTACHMENT（GL_DEPTH_COMPONENT）；
③ glCheckFramebufferStatus 验证完整 → glReadPixels(GL_DEPTH_COMPONENT, GL_FLOAT)
     读进 PBO —— GPU 异步填充，CPU 不等待；
④ RenderSystem.queueFencedTask(callback) 插 fence —— 回调在 executePendingTasks()
     （Minecraft 帧循环 "gpuAsync" 阶段）轮询到 fence 完成后于渲染线程触发：
     glMapBufferRange(GL_MAP_READ_BIT) → ByteBuffer.asFloatBuffer().get(depth[])
     → glUnmapBuffer → 删 PBO → 置快照 + countDown latch；
⑤ 出错路径分别上报：FBO 不完整 / glReadPixels GL error / PBO map null，不吞异常。
```

- **GL 实现要点（v2.9 源码核实）**：
  - `GlStateManager` **未暴露 `glReadBuffer`**——深度-only 读 FBO 默认 READ_BUFFER=COLOR_ATTACHMENT0（无 color）在严格驱动上可能 `GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER`，需自补 `glReadBuffer(GL_NONE)` + `glCheckFramebufferStatus` 断言；
  - `GlCommandEncoder` L687 设 `PACK_ROW_LENGTH=width` 且**从不复位**——读回须显式设置/恢复 `GL_PACK_ROW_LENGTH`/`GL_PACK_ALIGNMENT` 并恢复 read/draw FBO 绑定，否则污染 Mojang 后续截图读回；
  - **GL 行序自底向上**（原点左下角）——数组按 GL 行序直接用：`depth[y*width+x]`、`ndcY=2(y+0.5)/height−1`（§4.1 约定），**无需翻转**；
  - `RenderSystem.executePendingTasks` 用**超时 0 轮询**（L256-270）——fence 未完成时回调可能推迟多帧，PBO 须**双缓冲**、不可复用；
  - **resize / 全景截图**：窗口 resize 重建深度纹理、全景截图把主目标临时 resize 成 4096×4096（`Minecraft` L2681-2741）——缓冲按 `target.width/height` **动态分配**、每帧重取句柄。
- **非阻塞、零卡顿**：PBO 填充由 GPU 异步完成，CPU 在 fence 完成前不等待；回调只碰深度数组 + latch，不碰游戏数据；
- **语义**：读到的是"**最近一渲染帧**的世界深度"。API 端若在帧间请求，**置标志等下一帧**（最多 1 帧延迟，16ms，可接受）；超时（如 5s 无帧）由 `awaitSnapshot` 返回 null 上报，且错误消息区分失败原因。

---

## 四、反投影（Unprojector）

### 4.1 数学（v2.2 更正：需加回相机位置）

对深度纹理像素 `(x, y)`（GL 行序，`y` **自底向上**：行 0 = 最底行）及其深度值 `d`：

```
① 像素 → NDC（**v2.11：像素中心约定，勿用角点**）
   ndcX =  2·(x + 0.5) / width − 1   // 深度在像素中心采样 → 用中心 NDC；角点 2x/W−1 会引入
   ndcY =  2·(y + 0.5) / height − 1  // ~0.5·像素角距·距离 的垂直视线方向系统偏移（30 格≈3.8cm、100 格≈13cm）
   ndcZ =  2·d − 1                   // 深度缓冲 d∈[0,1]（1.0=远/天空）

② NDC → 相机相对坐标
   clip = inverse(P × V) × (ndcX, ndcY, ndcZ, 1)；clip.w ≤ 0 → 丢弃
   相对坐标 r = (clip.x / clip.w, clip.y / clip.w, clip.z / clip.w)

③ 相机相对 → 世界坐标
   world = camPos + r
```

**⚠️ 关键（v2.2 更正）**：`renderLevel` 的 `modelViewMatrix` **只含旋转、不含平移**
（`GameRenderer` L761-762：`new Matrix4f().rotation(inverseRotation)`）；相机平移在顶点
着色器完成（`terrain.vsh` L26-27：`ProjMat * ModelViewMat * (pos + ChunkPosition − CameraBlockPos)`）。
因此 `inverse(P×V)` 反推的是**相机相对坐标**，必须加回 `camPos` 才是世界坐标。
早期 §4.1 的 `world = inverse(P×V)×clip` 缺了这一项（已更正）。

得到的 `(x, y, z)` 就是这个像素上最近表面的**精确世界坐标**。这是 deferred rendering 的标准操作；投影矩阵把世界映射到屏幕有多准，逆矩阵就能反推多准。

### 4.2 精度与边界处理（v2.2 定稿）

| 问题 | 处理 |
|---|---|
| **命中点在面平面上** | 深度像素的命中点恰在某可见面平面（整数坐标）。**沿射线离相机更远推 ε**：`nudged = world + normalize(world − camPos)·ε`（ε=0.05）再 `floor`——min 面（块[10,11] 的 x=10 → 10.05 → floor 10）与 max 面（草顶 y=65 → 64.95 → floor 64）**均正确**（逐例验证）；最薄实心面（压力板 1/16=0.0625）> ε，不穿格 |
| **深度量化**（float32） | 1/z 压缩非线性，误差随距离显著增大：30 格 ≈1.1mm、100 格 ≈1.2cm、**300 格 ≈11cm、768 格 ≈0.7 格**（**v2.11 更正**：原"≤768 格 <1.2cm"仅 ≤~100 格成立）；近中距 ε=0.05 覆盖，远距由**近侧回退**兜底（见 §5.1）；**不查 6 邻居**（防实体邻格假阳性） |
| **air 命中**（实体表面/掠射） | 反投影点落在空气格 → 试近侧候选 `BlockPos.containing(nudged − normalize(nudged−camPos)·ε)`；仍 air 则丢弃（实体由 §5.3 通道处理） |
| **天空/无内容像素** | 深度 = 远平面极限值 `d_far`（清空值 1.0；天空亦写远平面 1.0）→ **直接跳过**：`d ≥ d_far`。**`d_far` 固定为 1.0（v2.16）**——标准透视矩阵下远平面 NDC z=+1 → depth=(+1+1)/2=**恰为 1.0**（按 §4.1 公式 `d(far)=1.0000651−0.0500065/far` 亦精确收敛到 1.0）；原 v2.6 从投影矩阵 m22/m23/m32 反推远平面距离，但 JOML `Matrix4f.perspective` 布局（`m23=-1`、`m32=2fn/(n-f)`）与 gluPerspective 假设相反，反推得 `d_far≈0.55` 误判天空 → v2.16 删除推导、直接固定 1.0。**不需要 `1.0f−1e-6f` 容差**（v2.6）：DEPTH32 存 float32，天空/清空读回**精确等于 1.0**；`1e-6` ≈ 8.4 ULP，会把 `d∈[1−1e-6, 1.0)` 的真实远表面吞成天空（far=768 时约 z∈[756,768] 段，虽超出渲染距离、实践无害，但边界错误且随配置漂移）；个别像素即使读回略低于 1.0，反投影落在未加载区块 → `getBlockState` 空气 → 安全丢弃。⚠️ **更不可用 0.999**：深度非线性下 `d(47 格) ≈ 0.999`，会把 ~47 格以外的对象当天空丢弃（v2.3） |
| **near 裁剪面内** | 反投影 `clip.w ≤ 0` → **丢弃**（落在相机内/背后） |
| **出屏对象** | `project()` 显式拒绝 `w≤0` / `|ndc|>1` / 像素越界（`ndc.y→+1` 时行号会等于 height，`depthAt` 越界钳到 1.0 会静默读成天空） |
| **深度非线性** | 透视投影下深度约 1/z 非线性，但**逆矩阵公式天然正确处理**，无需手动线性化 |

### 4.3 去重：全量反投影为默认，不做像素降采样（决策已定，实现定稿）

- **全量反投影 + 去重是默认**：每像素反投影 → 沿射线推 ε → `floor` → `BlockPos.asLong` → 写入 `Long2ObjectOpenHashMap<Long, Vec3>`（**存 nudged 点**，供 §5.1 近侧回退用；不能用纯 `Set<Long>`）。反投影约 40 ops/像素（4×4 矩阵乘 + 除 w + 取整 + 哈希插入）：
  ```
  410K 像素 × ~40 ops ≈ 1600 万 ops ≈ 10-20ms（纯数学，API 线程执行，不卡帧）
  ```
  去重是**无损的像素精简**——410K 像素 → 唯一方块几千个，冗余（约 40×）在映射上自然坍缩；
- **零分配实现**：不逐像素建 `Vec3`/`dir`——`len = √(r·r)`，`scale = 1 + ε/len`，`nudged = camPos + r·scale`（等价 `world + normalize(world−camPos)·ε`），只有唯一方块才分配 `Vec3`；
- **实体候选像素另存原始点（v2.4）**：扫描时对每个非天空像素算**原始表面点 `W = camPos + r`**（不推 ε，实体匹配用），按 `SectionPos`（16³）桶筛——只有落在"有实体 section"的像素才保留 `W`（供 §5.3 正向匹配）；无实体 section 的像素 O(1) 丢弃，零额外内存。方块路仍用推 ε 后的去重点（两套点用途不同，见 §5.3；v2.5 起精判走深度排序、不再查格）；
- **像素降采样不做默认**（原稿"可选降采样"撤回）：
  - 全量已够便宜，降采样省下的 ~10ms 感知不到；
  - 降采样 = 人为制造漏检（薄/远方块占屏 < 采样间距），而在**纯累积语义**下漏检的方块可能永远进不了记忆世界；
  - 角度均匀采样（投影渲染距离边界方块中心）无法削减近场冗余——近处方块仍被 (R/d)² 条射线命中（3 格方块吃几千采样点），只有去重才真正消除冗余；
- **不计算空位**：深度图当 **O(1) 可见性查询器**（对任意候选方块，投影其中心 → 比较方块距离 vs 深度值，方块距离 ≤ 深度+ε 即可见），仅当未来需要"自由空间/导航"时按需做（见 §7.1 纯累积语义）——空位对记忆世界更新无影响，显式记录空位 = 往虚空里存空气；
- 降采样仅在未来**每帧/高频快照**场景下作为性能兜底（网格步长或自适应细分）。

---

## 五、对象查询（四路，ObjectResolver）

去重后的唯一坐标分三类直查（`ObjectResolver.resolve`，渲染线程），加上**半透明方块独立通道**（§5.4，**仅 Fabulous**；Fancy/Fast 下并入 §5.1 主路径，v2.10）。**NBT 序列化全部复用 v1 现有方法**；半透明方块的输出与 §5.1 相同（`TerrainBlockSnapshot`），合并进 terrain 通道。

### 5.1 方块（精确）

```
对每个 (BlockPos.asLong → nudged)：
  pos = BlockPos.of(key)
  state = level.getBlockState(pos)
  · 非空气 → TerrainBlockSnapshot(pos, blockId, stateProps, ts)
  · 空气   → 近侧回退：cand = BlockPos.containing(nudged − normalize(nudged−camPos)·ε)
            → **实体相交验证（v2.10）**：先验线段 W→cand 是否与 §5.3 桶内任一实体盒相交
              ——相交 ⇒ 记录面是实体表面（薄实体贴墙/肢体）⇒ **跳过回退**，防穿透采集后方被遮挡方块；
              不相交 ⇒ cand 非空气则用，仍空气则丢弃（实体表面——§5.3 正向查询在像素级独立处理，不依赖此处的去重点）
```

- `blockId`（注册名）+ `stateProps`（BlockState 属性表）复用 `VisionCollector` 现有方法（改 package-private 暴露）；
- **近侧回退不会引入假阳性（v2.10 收紧）**：近侧候选若为非空气，通常本会成为该像素的记录面（矛盾）——所以它只在掠射/薄块等罕见误落时生效。**但记录面可以是实体**：薄实体（厚度 < ε=0.05，贴墙盔甲架肢体/衣角等）表面距墙 < ε 时，nudged 推 ε 会越过实体落进后方墙格 → 被遮挡方块被误记录。因此回退前必须做**实体相交验证**：W→cand 局部线段与 §5.3 桶内任何实体盒相交 ⇒ 该像素属于实体表面 ⇒ 跳过回退（实体由 §5.3 负责）；
- 产出：`TerrainBlockSnapshot(pos, blockId, stateProps, timestamp)`。

### 5.2 方块实体（搭方块便车）

- 方块可见 → 该方块 `state.hasBlockEntity()` → `level.getBlockEntity(pos)`（非 null）→ `be.saveWithFullMetadata(level.registryAccess())` 序列化 **完整 NBT**；
- `typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString()`；
- 只对**可见方块**序列化，天然比 v1（section 内全部方块实体）更省；
- **壁挂朝向过滤（v2.10，v2.14 收紧作用范围）**：方块可见 ≠ 方块实体可见——**仅壁挂式 BE**（墙挂告示牌 / 墙挂悬挂告示牌 / 墙挂横额 / 墙挂头颅，即 `WallSignBlock` / `WallHangingSignBlock` / `WallBannerBlock` / `WallSkullBlock`，四者均以 `HorizontalDirectionalBlock.FACING`（=`BlockStateProperties.HORIZONTAL_FACING`，同一单例引用）为朝向）在渲染面背对相机且无其他可视反射时，跳过序列化（方块背面可见、BE 正面被挡的假阳性）；判定复用 §5.4 同一投影/深度手段或简化为朝向·视线点积。⚠️ **不得对所有带 `HORIZONTAL_FACING` 的方块实体套用**——箱子/熔炉/木桶/漏斗/发射器/潜影盒等同样带该属性，但内容与朝向无关，方块可见即应序列化（v2.14）；
- 产出：`BlockEntitySnapshot(pos, typeId, blockId, stateProps, nbt, timestamp)`。
- **字段分层（v2.27，§5.2.1）**：上述 `saveWithFullMetadata` 序列化的是**客户端 BE 副本**（天然只含 `getUpdateTag`/`getUpdatePacket` 同步字段，容器内部从不进来），产出前再按 **typeId 白名单**过滤——容器家族 NBT 恒空、`decorated_pot.item`/`brushable_block.item` 等隐藏内容剥离；白名单外的键/未登记 typeId 一律拒采。规则与逐类字段清单见 §5.2.1。

> 边界情况：物品展示框、盔甲架、画在 MC 里是**实体不是方块实体**，走的 §5.3。

### 5.2.1 方块实体可采集字段分层（严格观察边界，v2.27）

**背景**：§5.2 的"方块可见 → 存 BE 全量 NBT"把**外观/状态**与**内容/内部**混为一谈。现实约束是——箱子/熔炉里的物品、讲台上的书、蜂巢的住户、可疑沙里埋的宝物，**不与之交互（开 GUI / 持键操作 / 刷扫 / 挖掘破坏）就拿不到**；纯视觉观察只能得到外观。对"只靠眼睛"的 vision/snapshot，必须**逐字段**规定什么可采集，否则要么把隐藏内容越权采下，要么把可见展示误删。

**机制事实（客户端副本为何天然是"可达边界"）**：
- 客户端 BE 副本的数据源 = 区块包/刷新包里的 `getUpdateTag`（`ClientboundLevelChunkPacketData.BlockEntityInfo.create` L153-158；`ClientboundBlockEntityDataPacket.create` L37-39 委托 `BlockEntity.getUpdateTag`），应用走 `loadWithComponents`（`ClientPacketListener.handleBlockEntityData` L1460-1467）——**客户端副本只含有 `getUpdateTag`/`getUpdatePacket` 覆写写入的字段**；
- `saveCustomOnly = saveAdditional`（`BlockEntity` L147-149）、`saveWithoutMetadata = saveAdditional + components`（L134-137）、`saveWithFullMetadata` = 前者 + id/x/y/z（L108-119）；base `getUpdateTag` 返回空（L215-217）；
- **例外（不进普通同步面）**：命令方块 GUI 由 `ServerPlayer.openCommandBlock` 用 `saveCustomOnly` 作 saver **单独发包**（`ServerPlayer` L1394-1395）。
- 采集在**客户端**执行 → `saveWithFullMetadata` 只能拿到"客户端副本 = 同步面"。**服务端副本**（`saveAdditional` 全字段，含容器 Items）在采集进程内根本不存在——这是本方案"无交互不采内部"的**结构性保证**（设计审查结论，Q-B）。

**分层定义**（对 §5.2 产出的 nbt 内部做分层；L0 不走 nbt，在 record 顶层）：

| 层 | 含义 | 采集 |
|---|---|---|
| **L0 结构** | pos / typeId / blockId / stateProps（record 顶层字段，非 nbt） | 恒采（面向/液位/占用位等**可见方块状态**都在 stateProps） |
| **L1 可观察** | 客户端可达 **且** 语义上无交互即由渲染/方块状态呈现的字段 | 采入 nbt（typeId 白名单） |
| **L2 交互内部** | 须开菜单/持键交互/刷扫/破坏才可见的内容；只存服务端持久化或独立通道 | **视觉快照禁采**；交互后经 container/*、sign/*、book/* 等端点查（§6.2） |
| （运行时瞬态） | 不落盘的 tick/动画字段（开合动画、isActive 推导、beamSections、刷扫进度…） | 客户端副本本就没有，不采也不管 |

**判别主准则（逐字段）**：字段所描述的信息**是否被方块实体自身的渲染/方块状态在无交互下呈现给观察者**。是 → L1；否（内容物隐藏、须交互或破坏才揭示）→ L2。推论即用户的观察：**绝大多数方块实体根本没有"交互才能得的内部信息"**——视觉/配置/机制/装饰字段全部无交互可见，可整类放行；有 L2 的只集中在"**内容容器** + **内容承载**"两类。

**审计决策清单（1.21.11 全部 49 个注册方块实体，`decompiled_src_vf/client` 逐类核实）**：

**表 A 容器家族——客户端恒空，NBT=∅（白名单=∅，只存 L0）**。判别依据：无 `getUpdateTag`/`getUpdatePacket` 覆写 → 客户端副本继承 base 空 tag；Items 仅存服务端 `saveAdditional`（`DataComponents.CONTAINER` + LootTable 机制）。

| typeId | 中文名 | 客户端可达 | 服务端内部 / 交互口 | 视觉快照 |
|---|---|---|---|---|
| `chest`（含 10 铜质变体）、`trapped_chest` | 箱子（含铜质变体）、陷阱箱 | ∅ | 容器 27 格、LootTable；开 ChestMenu | NBT=∅ |
| `barrel` | 木桶 | ∅ | 27 格；BarrelMenu | NBT=∅ |
| `dispenser` / `dropper` | 发射器 / 投掷器 | ∅ | 9 格；DispenserMenu | NBT=∅ |
| `hopper` | 漏斗 | ∅ | 5 格、`TransferCooldown`；HopperMenu | NBT=∅ |
| `shulker_box`（16 色） | 潜影盒 | ∅ | 27 格；开合动画=triggerEvent 瞬态；ShulkerBoxMenu | NBT=∅ |
| `crafter` | 合成器 | ∅ | 9 格、`crafting_ticks_remaining`、`disabled_slots`、`triggered`；CrafterMenu | NBT=∅ |
| `brewing_stand` | 酿造台 | ∅ | 5 格、`BrewTime`、`Fuel`；BrewingStandMenu（进度/燃料条） | NBT=∅ |
| `furnace` / `smoker` / `blast_furnace` | 熔炉 / 烟熏炉 / 高炉 | ∅ | 3 格、cooking 进度、lit 燃料、`RecipesUsed`；FurnaceMenu（进度/燃料条）；`LIT` 点燃位=blockstate | NBT=∅ |
| `ender_chest` | 末影箱 | ∅ | **BE 无任何字段**——内容=玩家末影箱 `EnderInventory`（服务端玩家侧）；EnderChestMenu | NBT=∅ |

**表 B 内容承载（非容器）——客户端部分可达，逐字段判 L1/L2**：

| typeId | 中文名 | 客户端可达字段 | 服务端内部字段 | 判定（白名单） |
|---|---|---|---|---|
| `jukebox` | 唱片机 | ∅ | `RecordItem`、`ticks_since_song_started`（唱片不进同步面） | NBT=∅；`HAS_RECORD`=stateProps；唱片内容=交互（换/退/破坏） |
| `lectern` | 讲台 | ∅ | `Book`、`Page`（仅 LecternMenu 打开经容器同步） | NBT=∅；`HAS_BOOK`=stateProps；书本=L2 经 book/* 端点 |
| `chiseled_bookshelf` | 雕纹书架 | ∅ | 6 格书、`last_interacted_slot` | NBT=∅；`slot_0..5_occupied`=stateProps（可视占用）；书=L2 |
| `beehive` | 蜂巢 / 蜂箱（蜜脾容器） | ∅ | Occupant 蜜蜂列表、`flower_pos` | NBT=∅；`HONEY_LEVEL`=stateProps；住户=L2 |
| `campfire` | 营火（含灵魂营火） | 食物物品 ×4（**渲染于篝火顶的烧烤物，无交互即见**） | `CookingTimes`/`CookingTotalTimes`（进度不同步） | L1=食物照采；进度字段不可达；`lit`=stateProps |
| `shelf` | 搁架 / 陈列架 | 陈列物品 ×3、`align_items_to_bottom`（**架上陈列渲染可见**） | — | L1 全采（世界内即见陈列物） |
| `decorated_pot` | 饰纹陶罐 | `sherds`（可见纹样）+ **`item`**（TAG_ITEM/`DataComponents.CONTAINER` 罐内隐藏单格物） | LootTable/Seed（未定物时） | L1=`sherds`；**剥 `item`**（隐藏，破坏才揭示） |
| `brushable_block` | 可疑的沙子/沙砾 | `item` + `hit_direction` | LootTable/Seed（未定物时）；刷扫进度=运行时 | L1=`hit_direction`；**剥 `item`**（揭示前隐藏，且揭示进度不同步、无法忠实复现） |

**表 C 无交互内部型——整类放行（白名单=客户端可达全部键）**。这些类型正是"可据此排除的大量方块"：无任何须交互才得的隐藏内容，客户端可达字段=观察者所见。

| typeId | 中文名 | 客户端可达字段（=白名单） | 备注 |
|---|---|---|---|
| `sign` / `hanging_sign` | 告示牌 / 悬挂告示牌 | `front_text`、`back_text`（SignText.DIRECT_CODEC=文字+颜色+可点击命令）、`is_waxed` | 文字无交互即见；`playerWhoMayEdit` 运行时、不序列化 |
| `banner` | 旗帜（16 色） | `patterns`、`CustomName`（saveWithoutMetadata 含组件） | 图案为装饰数据，直接可见 |
| `skull` | 头颅（骷髅头/僵尸/苦力怕/龙首等系列） | `profile`、`note_block_sound`、`custom_name` | 头颅皮肤可见 |
| `conduit` | 潮涌核心 | `Target`（攻击目标 EntityReference，渲染用） | isActive/effectBlocks 客户端 tick 自算，非 NBT |
| `beacon` | 信标 | `primary_effect`/`secondary_effect`、`Levels`、`CustomName`、`lock` | BeaconMenu 只用于"改"，字段本身同步即可见（光柱色/层数） |
| `mob_spawner` | 刷怪笼 | `SpawnData`（即将生成实体 NBT）+ 延迟/调参键 | 方块迷你旋转预览即渲染该怪；`SpawnPotentials`（轮换候选表）仅服务端=L2 不可达 |
| `creaking_heart` | 吱吱怪之心（树心，生成苍白花园木灵） | `creaking`(UUID) | 无内容 |
| `end_gateway` | 末地折跃门 | `Age`、`exit_portal`、`ExactTeleport` | 无内容 |
| `structure_block` | 结构方块 | name/author/metadata/pos/size/rotation/mirror/mode/ignoreEntities/strict/powered/showair/showboundingbox/integrity/seed | 配置本就在同步面，无隐藏内容 |
| `jigsaw` | 拼图方块 | name/target/pool/final_state/joint/placement_priority/selection_priority | 同上 |
| `command_block`（含 chain/repeat） | 命令方块（+连锁/循环命令方块） | **∅**（无 update 覆写） | 命令/`LastOutput` 等仅服务端；仅 GUI 打开时 openCommandBlock 单独发包（`ServerPlayer` L1394-1395）=L2 → NBT=∅ |
| `test_block` | 测试方块 | `mode`、`message`、`powered` | `triggered` 运行时 |
| `test_instance_block` | 测试实例方块 | `data`(test/size/rotation/ignore_entities/status/error_message) + `errorMarkers` | — |
| `copper_golem_statue` | 铜傀儡雕像（含氧化变体） | **∅**（getUpdateTag 未覆写） | 自定义名仅 `CUSTOM_NAME` 组件、不同步 → NBT=∅ |
| 无 NBT 机制型：`end_portal`、`bed`、`daylight_detector`、`comparator`、`bell`、`enchanting_table`、`sculk_sensor`、`calibrated_sculk_sensor`、`sculk_catalyst`、`sculk_shrieker` | 末地传送门（方块）、床、阳光传感器、红石比较器、钟、附魔台、幽匿感测体、校定幽匿感测体、幽匿催发体、幽匿尖啸体 | **∅** | L0 仅结构；`bed` 颜色=blockstate、`comparator` 的 `OutputSignal` 仅 saveAdditional（信号经邻近更新传达非 BE）、bell/附魔台=纯动画 tick 不序列化、sculk 系 saveAdditional 仅运行时（`last_vibration_frequency`+listener）无内容 |
| `moving_piston`（`piston`） | 移动中的活塞（活塞推进时的方块实体） | 瞬态：存储方块+进度（渲染用） | 仅活塞动画 1~2 tick 存在、随后被普通方块替换，正常快照几乎采不到；采到也无内容，L1 |

**表 D 展示与真实状态分离（vault / trial_spawner）**：二者把"给玩家看的展示"与"服务端真实进度/奖励"分开存——展示走同步面（L1），真实状态仅服务端（L2 不可达）。

| typeId | 中文名 | 客户端可达字段 | 服务端内部（saveAdditional / CODEC） | 判定 |
|---|---|---|---|---|
| `vault` | 宝库（试炼密室） | `shared_data`：`display_item`（**旋转展示的样例奖励物，可见**）、`connected_players`、`connected_particles_range` | `config`（loot_table/key_item/activation/deactivation_range/…）、`server_data`（`items_to_eject` 真实奖励队列、`rewarded_players`、`state_updating_resumes_at`） | L1 采 `shared_data`（display_item=外观）；真实奖励/已奖励态=L2，经开锁交互后掉落，不进 BE 快照 |
| `trial_spawner` | 试炼刷怪笼 | `spawn_data` + `next_mob_spawns_at`（仅 ACTIVE；即将生成的怪以旋转预览呈现） | StateData.Packed 其余（registered_players/current_mobs/cooldown_ends_at/total_mobs_spawned/ejecting_loot_table）+ FullConfig | L1 采 `spawn_data`；配置/玩家表=L2；`ominous`=blockstate |

**"排除大量方块"的数量结论**：49 个注册项中——表 A 容器家族 13 类 + 表 C 中 `command_block`/`copper_golem_statue`/无 NBT 机制型/`moving_piston` 等，客户端可达即空或全可见，**白名单即"整类放行或恒空"**，无逐字段裁剪负担；真正需要"白名单里剥字段"的仅两处（`decorated_pot.item`、`brushable_block.item`）与两处"只采展示子集"（vault `shared_data`、trial `spawn_data`）。特殊处理面积极小。

**实现契约（§5.2 `recordBlock` 目标形态；代码步待审阅后另起）**：
- 仍在**客户端** BE 上 `saveWithFullMetadata`（服务端副本含 L2，任何路径不得触碰）；产物按 `typeId → 允许键集合` 过滤，键集合 = 上表"客户端可达字段"列**减**去标"剥"/"L2"者，未知键剥除；
- 过滤后可为空（表 A 及若干 ∅ 行）→ 允许空 NBT，L0 照存；记忆世界按 `hasBlockEntity` 复原对空 BE 无害（§7.2/§7.3）；
- **未登记 typeId（新版本/模组方块）→ fail-closed**：只存 L0 + 空 NBT 并告警，进"待归类"清单，由人工按上表复跑后再放行；**禁止**以"同步面全部键"作通配兜底——否则未来某容器一旦把 Items 暴露进 `getUpdateTag` 即静默越权；
- 升级版本须重跑本审计（重点核对容器家族是否新增 update 覆写、表 B/D 是否新增可达内容字段）；
- 观察边界与 §7 纯累积外观冻结一致：BE 内部内容缺失不影响"冻结所看到的外观"；L2 信息在 agent 实际交互后经既有 mod 端点（`container/*`、`sign/*`、`book/*` 等，§6.2）查询，不经 vision/snapshot。

#### 5.2.2 交互内容记忆：容器 / 末影箱（L2 独立通道；世界态 per-pos 容器 + 玩家态末影箱首例，v2.28 → v2.29）

**目标场景**：agent 在真实世界打开过一个容器并取放物品 → 把该容器**内部**记成一条"交互记忆"，写入一个**独立文件**；记忆世界复现到该容器时按记忆**填好内容**。此后无论是人还是另一个 agent 探索记忆世界，**打开那个容器就等于读回 agent 对该容器的记忆**（所见内容一致，vanilla 菜单即可，无需任何自定义读法）。

> 注意定位：这与 §5.2/§5.2.1 的采集侧"记录/快照"不是同一条通道——§5.2.1 把 L2 从**视觉快照**里剥掉（L1 文件永不含 Items）；本节把同一份 L2 内容在**获得许可时点**（agent 真实开箱后）经**独立文件**存下来，供记忆侧灌入。二者不冲突：一个"禁录于无交互"，一个"显式记于交互后"。

**定案（已按用户选择落定）**：**A = 独立文件** `containers.nbt`；**B = 大箱子采录完整 54 格**（见下，源码核实可行且记忆侧无额外负担）；**C = 每次获取（会话提交）即覆写记忆，记忆侧对账保证同步**；**D = 末影箱纳入、并入 `containers.nbt`（v2.29，玩家态首例，见下「末影箱：玩家态末影箱记忆」）**。

**为什么独立文件 `containers.nbt`，不并入 L1 `block_entities.nbt`（定案 A）**：
- **单写者性**：`containers.nbt` 只有**交互提交路径**一个写者、低频（事件驱动），整文件 read-modify-write 无竞态；`block_entities.nbt` 由 `VisionBlockEntityStore` **每帧视觉快照**整文件覆盖写（纯累积合并，快照节奏）。若合并存储，交互记录须在每次视觉落盘时被反复合并回去——高频写者踩低频写者，竞态/丢记录，且两个指纹系统纠缠。
- **L1 纯度（§5.2.1）不被打破**：`block_entities.nbt` 语义＝"无交互可观察外观"，混入 Items 违反契约、污染 §7 外观指纹语义。L2 内容**只**经"交互 → `containers.nbt` → 记忆侧灌容器"这条显式通道进入记忆世界。
- （备选"同一文件顶层加 `containers` 键"可行但需视觉写者并入交互合并逻辑，弃用。）

**范围（记忆内容＝容器 BE 的持物 + 末影箱玩家态内容；大箱子整 54 格）**：
- 采录对象：持物容器 BE，即打开为 vanilla 容器菜单、内容落在**其自身槽位**者：chest（含铜质变体同 typeId）/ trapped_chest / barrel / dispenser / dropper / hopper / shulker_box / crafter / brewing_stand / furnace / smoker / blast_furnace。其中只有 **chest 家族**（chest/铜 chest/trapped_chest）能两两合成**大箱子**。
- **末影箱（`ender_chest`）＝玩家态特例（v2.29 纳入，详见下「末影箱：玩家态末影箱记忆」）**：内容不存于 BE、存于**玩家 `EnderInventory`**（玩家态，非世界态），方块本身零内容 → 复现只能写记忆世界**本地玩家的末影箱**、不是某块方块的槽。v2.28 因"玩家态无法按块复现"而排除；v2.29 改为把"agent 末影箱内容"按**玩家态记忆**纳入（并入方案，见下）。
- **大箱子（double）采录完整 54 格**——机制事实（1.21.11 服务端源码核实，见本节末）：
  1. 成 double 时两半各自方块状态带 `TYPE ∈ {LEFT, RIGHT}`；`ChestBlock.getBlockType`：RIGHT→`FIRST`、LEFT→`SECOND`；
  2. `ChestBlockEntity` 用 `DoubleBlockCombiner.combineWithNeigbour` 组容器，`first = isFirst ? 本半 : 邻居`——**点击 RIGHT 半 → first=RIGHT 本半；点击 LEFT 半 → first=邻居 RIGHT 半**。结论：**无论点哪一半，`first=RIGHT 半`、`second=LEFT 半`，纯由方块状态决定、与点击无关**；
  3. 服务端菜单容器 = `CompoundContainer(first, second)`，54 格按 `first.getContainerSize()`=27 切分 → **菜单槽 0-26 = RIGHT 半、槽 27-53 = LEFT 半**；客户端菜单槽序与服务端同构（`ChestMenu.sixRows` 双侧一致，`container/get` 读的即此序）。
  - 推论：**54 格可精确拆回两块，只依赖各半自身方块状态的 `TYPE`**——无需读服务端 BE、无需复刻几何邻接序。RIGHT 半（`TYPE=RIGHT` 的那块，可能是点击块也可能是邻居）← 槽 0-26；LEFT 半 ← 槽 27-53；各自本地格号 0-26。
  - 因此**采集侧无需区分单/双记录格式**：double 一次提交拆成**两条 per-pos 记录**（schema 与单块完全相同），记忆侧填法不变、无合成容器概念。
- 非槽位内容承载（讲台书 / 雕纹书架 / 唱片机 / 蜂巢等）走既有端点各自通道（§5.2.1 表 B），与本节"容器持物复现"不重叠，不在此列。

**文件契约（`containers.nbt`；采集侧写，记忆侧读）**：
```
{ version: 1,
  containers: {                                    // 键 = "x,y,z"（真实世界坐标，与 L1 文件同系）
    "x,y,z": {                                     // double 拆两键（RIGHT 半一键、LEFT 半一键），各半独立
      "typeId": "minecraft:chest",                 // 校验/告警用（BE 类型注册 id）
      "block":  "minecraft:chest",                 // 方块 id：BE/块缺失时记忆侧自足建块用
      "state":  {"facing":"north","type":"right"}, // 可省：方块状态属性。double 半件必带 type 使两侧配对一致
      "items":  [ {"slot": 0, "item": <ItemStack 序列化 tag>}, … ]   // 仅非空格；slot = 该 BE 本地槽号
    }, … },
  enderInventory: { "items": [ {"slot": 0, "item": <ItemStack 序列化 tag>}, … ] }
      // v2.29：agent 末影箱 27 格快照。非 pos 顶层键（玩家态），槽序 0-26 = EnderInventory 固定槽序
}
```
- `item` tag = 采集侧 `ItemStack.save(registryAccess)`（1.21.11 组件式 `id/count/components`；客户端副本即完整玩家可见数据，往返无信息损失）。**语义＝提交时刻最终内容**（agent 取放之后的结果，非 get 中途态）。
- **提交规则（定案 C：每次会话提交 = 覆写该容器全部键，latest-wins）**：
  - 粒度取**会话结束**（open 绑定 → 读 → close/commit），而非 `container/get` 每 peek：get 途中是编辑中间态，提交点才稳定；如改为每 peek 覆写会产生中途态回卷噪声（不采纳）。
  - 一次提交涉及的键 = 该容器当前覆盖的两半（single=1 键；double=RIGHT+LEFT 两键同批 upsert）。**double↔single 迁移**：本次提交为 27 格（邻半被挡/移除使菜单回 27）→ 除写本半外**删除伙伴旧键**，避免残留半旧半新；若上次 single 本次变 double → 新增伙伴键。
  - 只触碰本容器键；`containers.nbt` 单写者低频 → 每次提交整文件 read-modify-write 安全。
  - **末影会话路由（v2.29）**：commit 先按绑定格 block id 分流——`minecraft:ender_chest` → 覆写**顶层 `enderInventory` 键**（27 格，latest-wins，非 pos）**并**为该格写一条 `items=[]` 的 per-pos 出现记录（保证记忆世界有可开的末影箱块，即使它从未进视觉快照）；其余容器 → 按上两行 pos upsert。

**记忆侧新通道 `ContainerMemoryApplier`**（镜像既有结构：mtime 门控 + 内容指纹 + 每通道一个类）：
- **接入点**：`MemoryWorldManager` 静态单例；`onServerTick` 顺序 …RESTORER → **CONTAINER** → DELETION → CELLS（放 RESTORER 之后：先确保容器 BE 已由 `MemoryRestorer.place` 放好）。`forceRestore()` 一并 `forceRefresh()`。
- **读取**：mtime 门控轮询 `containers.nbt`（`MemoryConfig` 增 `containerFile`，探测链仿既有源文件：`stevex/vision/containers.nbt` → 各回退路径）；变化才 readFile，按指纹比对 `applied` 表。
- **应用/对账时机**（任一触发 `tryFill(pos)`）：
  1. **放置钩子**：`MemoryRestorer.place()` 放了某 `instanceof Container` 的 BE → 回调 `tryFill(pos)`（解决"记录已在、BE 后到"的正序）；
  2. **文件变化**：`containers.nbt` 变化 → 逐变化 pos `tryFill`；BE 尚不存在 → 进 **pending 集**每轮重试；
  3. **重启 / forceRefresh 重放**：全量过一遍（pending 中目标 BE 已被删则丢弃）；
  4. **每轮询对账（定案 C 的"保证同步"）**：对**文件内现存键**比对 BE 当前内容与记录，不一致即回填。成本＝遍历文件内键、每键比对其 BE 槽，容器数量级小、可忽略。
- **`tryFill(pos)`**：
  1. `be = level.getBlockEntity(pos)`。为 `null` 时读世界格：
     - 世界格=**空气** 且记录带 `block/state` → 静默建块 + 建 BE（`loadStatic(pos, state, {id,x,y,z}, registryAccess)` 最小 NBT），使该容器**自足出现**（"开过箱但该格从未被视觉见过"时唯一复现途径；若 TERRAIN 后续同格有数据则以 TERRAIN 为准、先行块被覆盖——外观记忆优先）；
     - 世界格=**同 block 容器但缺 BE** → 仅补挂 BE（正常不触发：容器家族 nbt 恒非空、必建 BE）；
     - 世界格=**别的方块**（与记忆冲突）→ 跳过 + 告警（不覆盖已复现外观）。
  2. `be instanceof Container c` → 逐 `(slot, itemTag)`：`ItemStack.parse(registryAccess, tag)` 成功且非空 → `c.setItem(slot, stack)`；解析失败/未知模组物品 → 该格置空 + 每 tag 一次告警（**fail-safe**，一格坏不崩整个容器）；越界 slot 忽略。double 的两键各自命中自己那半的 BE，本地槽 0-26。
  3. 填完一次 `setChanged()`。
- 填的是**服务端 BE = 探索者 vanilla 菜单直读的容器**；容器 BE 本不 tick、记忆世界又冻结 BE tick（§7.9）→ 内容静止（熔炉不烧、漏斗不吸），恰是"冻结的记忆"。

**末影箱：玩家态末影箱记忆（v2.29 并入方案）**：
- **机制事实（服务端源码核实）**：`EnderChestBlock.openScreen` 对**任意**末影箱都取 `container = player.getEnderChestInventory()` 并开 `ChestMenu.threeRows(containerId, inv, container)`（与开哪块无关）；`EnderChestBlockEntity extends BlockEntity implements LidBlockEntity`——**无 Container / 无槽 / 无 `saveAdditional`**，方块零内容。
- **两条推论**：① 复现**只能写记忆世界本地玩家的末影箱**（方块无槽可填）→ 打开**任意一块存在的末影箱**都读到该记忆内容（无法、也无意义按块区分）；② 末影菜单与单箱 / 木桶 / 潜影盒同为 `ChestMenu.threeRows`（27 格），**采集端无法靠菜单分辨末影箱** → 用 open 绑定格 block id = `minecraft:ender_chest` 判定会话（分流见上「提交规则」）。
- **记忆侧应用**（同一 `ContainerMemoryApplier` tick，不新增文件 / 通道）：文件变化 / 重启重放 / 每轮询对账任一触发、且 `enderInventory` 记录存在 → 把 27 格写入**本地 server player `getEnderChestInventory()`**（`ItemStack.parse` 逐格，fail-safe 同容器：坏格置空 + 每 tag 一次告警）；随后每轮询以记录为权威对账，本地改动即回填。
- **后果（全局 + 只读，P1 已接受）**：记录存在期间，本地玩家自己的末影箱被记录覆盖、搬动即回填——探索者**不能用记忆世界的末影箱存取自己的东西**；无记录（agent 从未开过末影箱）→ 不写、保持本地原样（新世界为空）。
- **可开性**：依赖记忆世界里有可开的末影箱块——由视觉快照（agent 开箱时该块在准星下→视野内→必被快照）或该格的 `items=[]` per-pos 出现记录（见「提交规则」）保证。
- **玩家态记忆族定位**：末影箱是首例**玩家态记忆**（内容挂 agent 玩家、非世界方块），是"agent 玩家态快照"族的 v1 种子。**并入 `containers.nbt` 为临时落位**：该族扩到第二个成员（主物品栏 / 护甲 / 手持 / 经验等，触发时机与容器不同）时拆为独立 `player_state.nbt` + 独立通道，把 `enderInventory` 迁出——采集侧 commit 分流点与记忆侧"写玩家末影箱"的隔离方法两处预留，使届时拆解为**搬迁而非重构**。`containers.nbt` 文件名语义自此变宽＝"agent 交互内容记忆（世界态 per-pos 容器 + 玩家态末影箱）"。

**行为与边界**：
- **已录容器在记忆世界 = 只读参照（定案 C 的直接推论）**：任何本地改动（搬入/搬出）都会在下一轮对账被**回填覆盖**——探索者**无法从已录容器取走物品**（取走即回卷）。这是"记忆恒等于最近一次提交"的代价；若日后要支持"从记忆取物/消耗"，需对该键放宽对账（关回填/一次性语义），v1 不放开、记档为已知取舍。
- **double 读取一致性**：探索者无论点哪半，服务端都按同一 `(RIGHT, LEFT)` 组序 → 与采集时的拆分自洽；两半以各自 `state.type` 重建（或自足创建）后顺序必然一致。
- **未交互的容器**在记忆世界仍是空壳（无记录）——与"无交互不采内部"一致；视觉外观照常复现（§5.2.1 空 NBT 的 BE 照建）。
- **时序同态**：先开（真实世界）后看见 → RESTORER 放空壳 BE → CONTAINER 填；先看见后开 → 记录晚到 → 文件变化分支重填同格。殊途同归。
- 一致性：BE 类型与记录不符（被换成别的容器）→ 按"implements Container + 世界格校验"容忍（类型差异不影响内容语义）。
- **性能**：填/对账只在触发时机触碰目标 BE，不做无目标全图扫描。

**机制事实（1.21.11 服务端源码核实，供审计）**：`ChestBlock.getBlockType`（RIGHT→`DoubleBlockCombiner.BlockType.FIRST`，LEFT→`SECOND`）；`DoubleBlockCombiner.combineWithNeigbour`（`first = isFirst ? 本半 : 邻居`，故点击 LEFT 半时邻居 RIGHT 半仍为 first）；`CompoundContainer.getItem/setItem`（以 `container1.getContainerSize()` 切分，两 chest 半各 27）；服务端菜单 `ChestMenu.sixRows(containerId, inv, new CompoundContainer(first, second))`。

**配置新增**（记忆侧）：`containerFile`；`containerReconcileOnPoll`（默认 **true**，定案 C）；`containerPollIntervalTicks`（可省，默认同既有轮询节奏）。

**残留边界（不再是待定）**：
- 已录容器只读（回填覆盖），"从记忆取物" v1 不支持，见上；
- 末影箱（玩家态）已纳入：全局 + 只读（本地玩家末影箱被记录覆盖、搬动回填），且须世界存在可开块（视觉快照 / 出现记录保证）；
- 非容器内容承载走各自通道，不在此；
- 采集侧**会话化提交点**机制已定稿并实现，见本节末「采集侧会话化机制（v2.30）」块。

---

#### 5.2.3 采集侧会话化机制（v2.30，实现于 stevex-template；记忆侧已实现 §5.2.2「记忆侧新通道」）

采集侧是 **WS 事件驱动**（无常驻采集循环）：agent 用 `key/use-once`（`KeyMapping.click(use)`）开箱、经 `container/get/slot/button` 编辑、`container/close` 关箱；开箱后视觉通道 `block_entities.nbt` 照常把容器当 L1 空壳采集。本节把「open 绑定 + 提交触发 + double 拆分 + 末影分流 + 物品序列化」落地为具体机制：

**模块**（均新，`stevex-template`）：
- `vision/ContainerMemoryStore`：`containers.nbt` 持久化（镜像 `VisionBlockEntityStore` 的 load/整文件 save 模式；**事件驱动**低频写，非每帧）；内存镜像启动时 load，commit 时整文件 read-modify-write。
- `vision/ContainerMemoryTracker`：会话状态机 + 每 tick open/close 探测 + region 读取 + commit 组装。
- `SteveXClient`：注册 `END_CLIENT_TICK` 调 tracker；`ContainerApi.closeContainer`：关箱前先 `ContainerMemoryTracker.commitFromClose(mc)`（同步读最终内容提交）再 `closeContainer()`。

**① open 绑定**（tick 探测，非 use 调用点——`key/use-once` 只是异步按键，无法同步取到结果块）：
- `END_CLIENT_TICK` 比较 `mc.screen` 迁移：当**进入** `AbstractContainerScreen`（且 `containerMenu` 非 `InventoryMenu`）时，用**当帧准星命中块**绑定。可靠性依据：容器开箱须准星对块，而 brain 在开箱后必须先 poll `container/get` 才知已开、期间不会转头 → 首个容器屏幕 tick 的准星命中 = 被开的块。
- 校验命中块 `blockId ∈` **持物容器家族 ∪ {ender_chest}**（家族名单＝视觉 §5.2.1 `BlockEntityFieldPolicy.STRIP` 的容器行，单一来源）。命中块不合家族（如合成台/附魔台）→ 会话=null（本屏不再尝试绑定，防每 tick 重绑）。绑定格为命中块**当时**的 `block/state/typeId`（typeId：优先 `level.getBlockEntity(pos).getType()`，缺则 BE 注册表 `isValid(state)` 反查）。
- 会话记录：`{pos, blockId, stateProps, typeId, isEnder}` + 每 tick 缓存最近一次 region 内容（指针拷贝，兜底用）。

**② 内容来源 = 打开的菜单容器区**（读**菜单**不读客户端 BE——1.21.11 客户端容器 BE 副本恒空、内容只在开着的菜单里）：
- region = `containerMenu.slots` 前导、`slot.container != 玩家 Inventory` 的槽序列（持物容器菜单只有「方块容器区 + 玩家物品栏」两个容器对象）；区槽 `slot.index` = 服务端容器槽号（菜单槽序与服务端 `ChestMenu`/`CompoundContainer` 同构）。
- 区域长度由区槽数得：chest 家族 double=54 / 其余单容器=自身尺寸（27/15/… 区槽数即真值，无需按块猜）。

**③ 提交触发**（定案 C：**会话结束**才覆写，避免 get/click 中途态）：
- 主路径 = `container/close`（agent 自动流恒走此）：WS 处理器在**关箱前同一主线程任务**里 `commitFromClose`——此时先于本任务入队的全部 `container/slot` 已在主线程 FIFO 执行完，读到的即最终内容。**先读后关**，关箱后 `containerMenu` 已被重置、无法再读。
- 兜底路径 = tick 探测到**未走 WS 的关屏**（Esc 等）：以缓存内容提交（可能落后 ≤1 帧；非自动流、极小窗口，记录为残留）。`container/close` 已提交则标记，tick 关屏不再重复提交。

**④ double 拆分**（§5.2.2 契约的机制实现）：
- region 54：四邻扫描找伙伴半（同 block + 同 `facing` + 互补 `TYPE` RIGHT↔LEFT）。按各半**实际** `state.type` 分配：`TYPE=RIGHT` 半 ← region [0,27)（本地槽 0-26）；`TYPE=LEFT` 半 ← [27,54)。两条 per-pos 记录同批 upsert（键=各半 pos）。
- region 27 且绑定 `TYPE ∈ {LEFT,RIGHT}`（邻半被挡/移除致菜单回 27）：只写本半 region [0,27)，并按连接方向（LEFT→facing 顺时针 / RIGHT→facing 逆时针）**删除伙伴旧键**——double↔single 迁移。
- region 54 但四邻找不到伙伴（理论不发生）：跳过 + 告警，防错写。

**⑤ 末影分流**（v2.29 契约）：绑定格 `blockId = minecraft:ender_chest` → 27 格 region **全部写顶层 `enderInventory`**（latest-wins，槽=region 槽号），并**为该格**写一条 `items=[]` 的 per-pos 出现记录（保证记忆世界有可开的末影箱块）。

**⑥ 物品序列化**：`ItemStack.CODEC.encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), stack)`（1.21.11 组件式，产 `{id,count?,components?}`；无 `ItemStack.save(registryAccess)` 便捷法，与记忆侧 `parse` 同一路径对称）。仅非空格。

**⑦ 覆盖范围与性能**：commit 只触碰本会话覆盖的键（同批 upsert + 迁移删除），`containers.nbt` 无第二写者 → 每次提交整文件 read-modify-write 安全；tick 探测只比较屏幕实例 + 每开箱一次取准星，零热路径成本。

### 5.3 实体（正向像素归属 + SectionPos 桶粗过滤 + 渲染实体列表快照 v2.9）
**为什么正向而非反向（v2.4 定稿）**：深度缓冲每像素只有深度标量、**没有"这个像素属于哪个实体"的 ID**；反投影出的表面点 W 落在实体身上时，所在格是空气。要回答"这是哪只牛"，必须把 W 匹配到候选实体——实体没有网格地址，只能测"点在谁的盒内"。

**反向方案（v2.2）的缺陷**：对每个候选实体投影盒中心/8 角共 9 个锚点，查锚点像素的最近表面是否落在盒内。便宜但**锚点不完整**——薄/扁/部分被挡实体的可见像素往往不落在 9 条固定射线上 → 漏检。v2.4 改为**正向**：每个非天空像素的表面点都有机会被归属，像素完整，天然覆盖薄/扁/部分被挡（只要占屏 ≥1px）。

**正向判定（v2.5 定稿 + v2.11 肢体判别：SectionPos 桶粗过滤 + AABB.contains + 深度排序 + 肢体判别精判）**：

```
① 粗过滤：SectionPos（16³）桶索引，快照时构建（O(N)）
   对每个候选实体 E（**实际被渲染的实体**，v2.9 定稿——**新增第二注入点 `extractVisibleEntities` @TAIL**（私有方法，
     参数含实际 `frustum`），在其后对 `level.entitiesForRendering()` **复刻同方法 L821-826 的裁剪谓词链**、
     对幸存实体快照完整 Entity 数据（`getBoundingBox()/getId/getUUID/getType/getYRot/getXRot/getDeltaMovement/getXo/getYo/getZo/onGround/getHealth/blockPosition`，
     渲染线程同帧读取）；**AABB 按渲染帧插值对齐（v2.10）**——`getBoundingBox()` 是 tick 位置，渲染模型在
     partialTick 插值位置：`box = getBoundingBox().move(lerpX, lerpY, lerpZ)`（`lerp = (cur − prev)·partialTick`，
     **v2.11：`prev` 必须取 `getXo/getYo/getZo`（上一 tick 位置）**——勿用 `getDeltaMovement()` 代理：
     碰撞/传送后运动矢量与帧间位移不一致，插值盒会偏离渲染位置），
     防快速实体（疾跑/末影龙）像素 W 与快照盒相对偏移导致匹配漏检）：
       drawn = (shouldRender(frustum,cam) || hasIndirectPassenger(player))
             && (isOutsideBuildHeight(y) || isSectionCompiledAndVisible(blockPos))
             && (entity != camera.entity() || camera.isDetached() || (LivingEntity && sleeping))
             && (!(entity instanceof LocalPlayer) || camera.entity()==entity)
     **`LevelRenderState.entityRenderStates` 不可用（v2.9 核查）**：它是 `List<EntityRenderState>`（DTO），只有
       entityType/x/y/z/boundingBoxWidth/Height——**无完整 AABB、无 id/uuid/rotation/motion/onGround/health、无回指
       Entity 的引用**，且 `levelRenderState.reset()` 在 renderLevel L593 紧挨 TAIL（L594）→ **TAIL 时已被清空**；
     **勿用 `entitiesForRendering()` 原样全收**：返回全部已加载实体、不做视锥裁剪（`ClientLevel` L327 = `byId.values()`），
     含距离外/被剔除/相机所在实体/LocalPlayer/无渲染器的 EnderDragonPart，会对"列表有实体、深度是其后方块"**假阳性**
     （纯累积语义最忌）——复刻谓词即消解此问题（对 EnderDragonPart 等调用同一 `shouldRender`，行为与 vanilla 逐字节一致）；
     跳过 isRemoved()/isLocalPlayer()）：
     box = E.getBoundingBox().inflate(0.5)         // v2.10：统一 inflate(0.5)，与命中盒/vanilla 视锥盒对齐（此前桶 0.45 vs 命中 0.5 自相矛盾，[0.45,0.5] 壳层像素桶查询漏检带仍在）
     for section in box 相交的所有 SectionPos:
       bucket[section.asLong()].add(E)
   完整性：E 的盒若包含 W，必与 W 所在 section 相交 → E 必在该 section 桶内，
           只需查 W 所在这一个 section，无需查邻居。

② 精判（只对"所在 section 有实体"的像素，由 §4.3 扫描时筛出）：
   对每个非天空像素的原始表面点 W（= camPos + r，**不推 ε**）：
     b = bucket[SectionPos.of(W)]
     b 为空 → 跳过（快速路径，绝大多数像素）
     for E in b:
       camPos ∈ E.getBoundingBox()？是 → E 可见（相机在实体内部，环绕可见；**无需像素**，早退）
       W ∈ E.box.inflate(0.5)？否 → 跳过（W 侧向偏离实体盒——含共格方块表面离实体较远的像素）
            **v2.10**：桶与命中盒**统一 `inflate(0.5)`**（对齐 vanilla 视锥盒 `getBoundingBoxForCulling().inflate(0.5)`；
            此前桶 0.45 会在 [0.45,0.5] 壳层漏掉桶查询；Sniffer 用 0.6、Illusioner 更大仍属已知缺口 §10.10）；
            且 `AABB.contains` 是**半开区间**（`>=min && <max`，L259-261），会把
            坐落在盒 max 面的像素拒之门外——**画/画框的可见面恰在盒 maxZ**（`Painting`/`ItemFrame` 0.0625 厚薄片）→
            侧向判定须改**闭区间/带 epsilon**
       dir = normalize(W − camPos)
       t_entry = 射线(camPos, dir) 与 E.getBoundingBox()（**未外扩**）的近交点距离
                 （**手写 slab 求交**，v2.9：vanilla `AABB.clip` 不支持——起点在盒内返回 empty（非负 t）、
                  终点在盒面 s==1.0 亦拒；slab 须返回**带符号** t_entry：起点在盒内为负，勿把负值当不相交；
                  null = 射线不穿盒）
       · t_entry == null（射线不穿盒）→ **肢体判别 A（v2.11 收紧，防两类假阳性）**：
           记录面在盒外。**三项旁证全过**才判 E 可见（伸出盒外的肢体：牛角/平举手）：
             a. **非他体**：W 不在桶内任一**其他**实体盒（`inflate(0.5)`）内——记录面若是另一实体的身体
                （实体非方块、cell 必空气），不排除会对"被前方实体完全遮挡的后方实体"判假阳性；
             b. **非薄方块**：`cell(W − dir·ε)` 与 `cell(W)` 均非薄方块（`!isShapeFullBlock` 或渲染形状厚度 < ε
                ——压力板/按钮/红石线/雪层/铁轨：薄方块表面被深度记录时 ε 后即空气，伪装成"肢体"；查 W 自身格
                覆盖 float32 把方块面上的 W 震进相邻格的误落，见下注）**且** `cell(W + dir·ε)` 为空气；
             c. **前向空扫**：沿射线从 W 向远处扫（步长 ε，上限 `|W−camPos| + max(2ε, 1 格)`）一路空气。
           全过 → E 可见；任一不满足 → 跳过（W 是盒旁/盒外的方块表面或其他实体表面）
       · |W − camPos| ≥ t_entry → E 可见（记录面在实体本体或其近面之后）
       · |W − camPos| < t_entry（记录面在盒前）→ **肢体判别 B（v2.11 收紧）**：
           先过旁证 **a（非他体）**——记录面若是另一实体的身体（在盒前挡住 E），不排除会对被遮挡的 E 判假阳性；
           沿射线从 W 向远处扫（步长 ε）至盒近面 `t_entry`：
             **存在非空气格** → 中间有方块 → 方块表面在实体之前 = 遮挡 → 跳过
             **一路空气** → 记录面是实体自身的肢体（伸过洞口的手）→ E 可见
           （v2.11 由单格 `cell(W+dir·ε)` 改为**向前扫至盒近面**：肢体距背后表面 <ε（贴墙手）时单格会把
           方块误判成"无方块"；扫能区分"中间确有方块"（判遮挡，保守）与"只有实体肢体"（判可见）——
           宁可漏贴墙肢体也不假阳性，与 §5.1 近侧回退的实体相交验证方向互补）

③ 判定规则：任一命中像素 = 可见（宽松）；重叠歧义（W 同时落 A、B 两盒）→ 全报
④ 产出：EntityLightSnapshot（id/uuid/type/pos/rot/motion/onGround/health，Tier-1 轻量）
```

天空像素在源头排除（`d ≥ d_far`（=1.0）不参与，见 §4.2）——无"天空→可见"捷径问题；不透明实体贴天空时自身写深度、表面点 W 落在盒内 → 仍可见。

**为何深度排序（v2.5，替代 air 过滤）**：v2.4 的"W 所在格为 air"前提错误——**方块是网格地址、实体是连续曲面，二者可共格**（高草丛 / 门 / 水里的实体）。共格时像素记录面是实体、W 落在实体身上，但格是非空气 → 实体被误杀。正确判据不是"格是否空气"，而是"**记录面是否在实体本体之前**"：
- `t_entry` 用**未外扩盒**：方块在实体前方 ≤0.5（v2.10 统一外扩）时，其表面点 W 虽落进**外扩盒**，但 `|W−camPos| < t_entry` → 跳过——假阳性被精确排除，且不依赖格分类，对 CUTOUT（栅栏/树叶）挡体同样生效；
- 共格实体：W 在实体本体上 → `|W−camPos| ≥ t_entry` → 可见；
- 外扩盒（0.5，v2.10 统一）保留在 contains 判断里，把模型伸出 AABB 的肢体（胳膊/牛角）纳入候选；肢体本身的可见性由 **v2.11 肢体判别**（见下）兜底，与深度排序互补。

**为何不推 ε / 不用方块路去重点**：方块路按格去重（每格一个点）——对方块够用（格=方块）；对实体错：实体占的像素可与背景**共用一格**，存下的点可能不是实体表面 → 漏。正向必须用**每个非天空像素的原始表面点 W**；粗过滤先把量筛小（无实体 section 的像素 O(1) 丢弃）。

**为何深度排序用"射线-AABB 求交"而非"深度差值比较"**：深度 1/z 非线性，固定深度 ε 无法覆盖全距离；"射线与实体盒求交"是纯几何量，`t_entry` 与 `|W−camPos|` 同为世界距离、直接可比，无调参、对小模型与大实体都稳健。`AABB.contains(W)` 只回答"W 是否在盒内"、不回答"实体盒是否在记录面之前"——故二者配合（contains 管侧向对齐，`t_entry` 管前后排序）。

**为何"肢体伸出盒外 / 在盒前"不会漏判（v2.8，v2.11 收紧）**：深度排序只回答"记录面是否落在盒的深度区间"，对**伸出 AABB 的模型几何**不充分——牛角/平举的手超出实体盒，当**只有肢体露出**（主干在墙后）或**肢体伸在盒前**（手伸过洞口）时，记录面是实体自身的几何，但 `t_entry==null`（射线不穿盒）或 `|W−camPos|<t_entry`（在盒前）都会被误跳。**v2.11 修正判别式**：原"查 `cell(W+dir·ε)`，空气 ⇒ 肢体"的反推**不安全**——空气也可能来自"另一实体的身体"（实体非方块）或"薄/部分方块"（压力板/按钮/红石线/雪层/铁轨，深度记录在其薄表面上、ε 后即空气），这两类都会伪装成"肢体"造成假阳性（纯累积语义最忌）。现改为**三项旁证**：① **非他体**——W 不在桶内任一其他实体盒内（实体-实体堆叠：前方实体身体上的像素不得为后方实体背书）；② **非薄方块**——`cell(W ± dir·ε)`/`cell(W)` 均非薄方块且 `cell(W+dir·ε)` 为空气；③ **前向空扫**——沿射线从 W 向前扫至盒近面（判别 A 无盒则扫到 `+max(2ε,1 格)`）一路空气。**为何查远侧格而非只查 W 自身格**：方块面在整数平面，float32 反投影误差（~1.2cm@100 格）会把 W 震进相邻空气格——只查 W 自身格会把"盒旁/盒前的方块面像素"误判成肢体 → 对墙后实体假阳性（纯累积语义下假阳性比假阴性更糟）；`W + dir·ε`（ε=0.05 > 误差）保证落回方块体；`W − dir·ε` 覆盖"薄方块被深度记录、W 恰落其近面/远面"的情形。**保守取舍**：肢体距背后表面 <ε（贴墙手/抵墙牛角）时，扫的第 1 步即命中方块 → 判遮挡漏检——**宁可漏贴墙肢体也不假阳性**（与 §5.1 近侧回退的实体相交验证方向互补：§5.1 防"方块被误报"、此处防"实体被误报"）。判别需读 blockstate → **该子步在渲染线程**（ObjectResolver 内；只对到达"不穿盒 / 在盒前"分支的候选像素读，经桶过滤后数量小）。

**匹配的歧义与对策**：

| 情况 | 对策 |
|---|---|
| 模型伸出 AABB（胳膊、牛角） | 桶/命中盒统一外扩 0.5（对齐 vanilla 视锥盒，v2.9）；**仅肢体露出的情况由 v2.11 肢体判别兜底**（非他体 + 非薄方块 + 前向空扫；贴墙肢体保守漏） |
| 贴墙扁实体（画/画框）可见面在盒 maxZ | `AABB.contains` 半开区间必拒（v2.9）→ 改闭区间/带 epsilon；或依赖射线求交主判据 |
| Marker 盔甲架（0×0 盒） | 模型照画但盒退化 → 像素无法归属（v2.9 已知限制；vanilla 用 4 格回退盒 `EntityRenderer` L70-72） |
| 大堆物品平片 Z 向摊开 | 渲染范围可达 ~3 格（`ItemEntityRenderer` L93-107）远超任何 inflate → 漏检（v2.9 已知限制） |
| 带 display block 的矿车 | 盒纵向延伸（`AbstractMinecartRenderer` L160-163）未被覆盖（v2.9） |
| EnderDragon 主盒 + 8 个 part | part 无渲染器永不画却在列表 → 快照须过滤（v2.9） |
| 多个实体屏幕重叠 | W 落多盒 → 宽松全报（纯累积语义可接受）；后续可"盒中心最近"精化 |
| 亚像素 / 小目标 | 占屏 <1px → 深度像素被背景占据 → 漏判（§10 已知限制） |
| 透明/发光部分不写深度 | 玻璃/药水类掉落物 → **工序 D 补采（v2.25，§5.3.1）**；幽灵/附魔光等效果层 → 记录面是后面的东西 → 漏判（已知限制，接受） |

**复杂度**：构建 O(N)（N × 跨 section 1~8）；查询——绝大多数非天空像素落在无实体 section → O(1) 桶 miss 跳过；仅实体附近像素做 contains + 射线-AABB ≈ 实体附近像素数 × 桶内实体数（正常场景 1~10），与反向 `N×9` 同量级或更低；v2.11 肢体判别只在"不穿盒 / 在盒前"分支执行（他体排除 O(桶内实体数) 次 contains、薄方块排除 1~2 次 blockstate、前向扫步长 ε 至盒近面/1 格），该像素子集经桶过滤后数量小。

**全量 NBT 策略**：深度只产出**可见实体的 uuid**，**不序列化 NBT**——全量 NBT 仍走 `vision/entity`（Tier-2，TTL 缓存 1000ms，LRU 256），防止每帧对所有可见实体做全量序列化引发 GC 风暴。

### 5.3.1 半透明掉落物（ItemEntity）正向归属（工序 D，v2.25）

**背景**：Fabulous 下玻璃/药水类掉落物（物品模型含半透明材质：玻璃/玻璃板/染色玻璃、瓶子/药水/喷溅/滞留药水等）画进**独立 item_entity 目标**、**不写 main 深度**（§3.1.1，v2.9）——§5.3 的正向像素归属拿不到它们：主深度记录的是它们**背后**的不透明表面，W 永远落不到其盒上 → 漏检。**普通不透明掉落物（铁剑/钻石/泥土等）写 main 深度、§5.3 正常采集，不受影响。**

**核心洞察**：半透明掉落物对 §5.3 的处境 = 半透明方块对 §5.1 的处境——都是"深度看穿、深度记录背后表面"。§5.4 统一判定式 `Z_opaque ≥ t_entry − δ` **不要求 W 落在物体身上**（那正是失效原因），只要求"主深度（不透明背景/天空）在该物体之后"，对掉落物天然成立。而掉落物已在 `extractVisibleEntities` 实体快照中（含 partialTick 插值 AABB，v2.10）——缺的只是可见性判定。**候选从"工序 C 的可见透明块"换成"实体快照里未被 §5.3 报告的 ItemEntity"**，判定式原样复用（工序 C v2.26 起亦为区间推进正向归属，二者同构）。

**工序 D（§5.3 之后执行，仅 Fabulous）**：

```
① 候选：快照中未被 §5.3 报告的 ItemEntity（typeId == "minecraft:item"）
   半透明掉落物必在此集合（§5.3 天然够不到）；被不透明物遮挡的不透明掉落物也在，
   判定式会正确判不可见——不预过滤，正确性等价、免渲染层判定 API 依赖
② 可见性判定（逐候选，复用 §5.4 工序 C 判定式）：
   候选盒 B = 实体的 getBoundingBox()（partialTick 插值 AABB，§5.3 快照；零/退化盒跳过）
   ⓪ B.contains(camPos) → 可见，早退（相机在掉落物堆里）
   ① 投影 B 的 8 角 → 屏幕 bbox（像素中心约定 v2.11），显式裁剪到 [0,width)×[0,height)
   ② for p in bbox：
       ray = 相机 → p；t_entry = 手写 slab 求交（带符号，§5.3 同款）
       Z_opaque = p 处主深度还原的物理距离（贴天空 → ∞）
       Z_opaque ≥ t_entry − δ → 可见，break
      全 bbox 不相交 / 全被挡 → 不可见
③ 上报：与 §5.3 相同 addEntity（light snapshot：id/uuid/type/pos/rotation/motion/onGround/health）
   进实体 store + snapshot 响应；全量 NBT 仍走 Tier-2 vision/entity
```

**三况正确性**（与 §5.4 逐字一致）：
- **水下玻璃瓶**：主深度 = 水底（不透明面在瓶后）→ `Z_opaque ≥ t_entry` → **可见**；
- **贴天空**：`Z_opaque = ∞ ≥ t_entry` → 可见；
- **被墙挡**：主深度 = 墙（不透明面在瓶前）→ `Z_opaque < t_entry` → 不可见。

**为何正向枚举而非盒中心/锚点采样**：同 §5.4 弃用"9 锚点"的教训——薄/扁/部分被挡的掉落物可见像素不落在固定锚点上 → 漏检；正向覆盖盒的全部像素，占屏 ≥1px 即不漏。

**为何不读 item_entity 目标深度（备选，已弃）**：item_entity 目标虽持有深度，但需**第三路 PBO + 新反投影通道**，且其深度语义（是否 `copyDepthFrom(main)`、glint 是否写深度）需全新源码核实，违背 §10.1 覆盖层语义倾向；工序 D 达相同可见性结论（"实体盒级"粒度 vs "像素级"精确面），§5.3 已接受实体盒级宽松语义——不值得加一路深度。

**成本**：候选 = 视锥内未被 §5.3 报告的 ItemEntity（掉落物稀疏，通常数十）；每候选 bbox 覆盖几~几十像素、首像素命中即 break。远小于 §5.4 工序 C 的区间射线推进（水体场景）。

**配置分支**：仅 Fabulous（`useShaderTransparency()`=true）。Fancy/Fast 下掉落物写 main 深度、§5.3 已覆盖，本工序跳过（与 §5.4 同构）。

**已知限制**：
- 大堆物品平片 Z 向摊开 ~3 格超出 `getBoundingBox()` → 漏（§10 已知，接受）；
- 亚像素掉落物（占屏 <1px）→ bbox 全被背景像素占据 → 漏（§10 通用）；
- 掉落物盒级判定：可见性粒度是"实体盒"而非像素精确面（宽松语义，同 §5.3）；
- 物品 glint 光效层仍无深度——但附着于物品本体，本体（不透明写深度 / 半透明由工序 D）正常采集，不影响。

### 5.4 半透明/绊线方块（两深度锚点：首层透明面深度 pass + 区间射线推进正向归属，v2.24/v2.26）

**背景**：TRANSLUCENT 层（玻璃/玻璃板/染色玻璃/冰/黏液块/蜜块/红石线/下界传送门/水）与 **TRIPWIRE 层（绊线，v2.12）**的深度行为**配置相关（v2.10）**：**仅 Fabulous（`useShaderTransparency()=true`）**下渲染进**独立 translucent / weather 目标**、**不写 main 深度**（§3.1.1）——深度图记录的是它们**背后**最近的不透明表面，反投影 + §5.1 天然获取不到半透明/绊线方块本身，需独立通道。**默认 Fancy/Fast 下**半透明/绊线直接画进 main target 且写主深度——本通道不执行，改由 §5.1 主路径直查（见下"配置分支"）。**1.21.11 无独立 string 方块**，绊线即 `minecraft:tripwire`（TRIPWIRE 层唯一成员，源码核实见 §12）。

**配置分支（v2.10 双路径动态自适应）**：捕获时检测 `Minecraft.getInstance().useShaderTransparency()`：
- **true（Fabulous）**：执行本通道（§5.4）——主深度看穿半透明/绊线，**双锚点夹出透明区间、区间内沿射线推进逐格归属**（v2.26）拾取半透明/绊线方块本身；
- **false（Fancy/Fast，默认）**：**跳过 §5.4**——半透明/绊线方块写主深度，§5.1 主路径已能直查其表面；接受**"半透明后物体不可见"的最近表面语义**（透过玻璃/水的方块与实体被物理遮挡、不上报）。Phase 5 验证须在两种配置下分别通过。

**为什么不用"9 锚点反向采样"**：对候选方块投影 8 角 + 中心共 9 锚点查深度，锚点**不完整**——部分遮挡（柱子挡住玻璃墙一半，中心与角全落在遮挡面）会漏检整个方块。与 §5.3 实体 v2.2→v2.4 同一教训（"薄/扁/部分被挡的可见像素不落在锚点上 → 漏检"），改**正向**：锚点集 = 方块在屏幕上覆盖的**全部**像素。

**注意（v2.9）**：半透明**实体**（`entityTranslucent`，史莱姆外皮/隐形盔甲架等）**默认写 main 深度**，与半透明/绊线**方块**不同——它们被 §5.3 深度像素直接记录、无需本通道；本通道只处理半透明/绊线**方块**（绊线为方块非实体，v2.12）。

**两深度锚点设计（v2.24）**：Fabulous 下**一份帧同时给出两个锚点**（§3.3）：
- `mainDepth`（主深度）＝首个**不透明**面（水底/玻璃后物体）；
- `translucentDepth`（translucent 目标深度）＝首个**半透明**面（水面/玻璃面）——`copyDepthFrom(main)` + TRANSLUCENT 组在前覆盖 ⇒ **不变量 `translucentDepth ≤ mainDepth`**。

逐像素分类：`==` → 无半透明遮挡，可见面即不透明面；`<` → 半透明面在前，锚点给出"首个可见面"（近）与"首个不透明面"（远），二者之间＝真实可见范围（水面→水底）。

**深度只给两个锚点，区间内的嵌套半透明由区间射线推进补齐（v2.26）**。三通道分工：
- **工序 A（§5.1，不变）**：`mainDepth` 反投影 → 全部不透明方块（含水下物体）；
- **工序 B（下方新增）**：`translucentDepth < mainDepth` 的像素反投影 → **首层半透明方块**（精确水面/玻璃面）；
- **工序 C（v2.26 重写：区间射线推进正向归属）**：对透明区间像素沿射线推进到 Z_opaque，逐格上报射线实际穿过的**每一个**可见透明格 → **全部嵌套半透明**（首层之后的每层透明）与**绊线**（TRIPWIRE 在 weather 目标，工序 B 不可见）。

**完整性论证**：不透明可见 ⇔ 沿射线首个不透明面 = mainDepth（工序 A）；半透明可见 ⇔ 射线在首个不透明面**之前**穿过其实际形状、`t_entry ≤ Z_opaque`（本通道谓词）——首层由工序 B 精确落位、**首层之后每一层由工序 C 区间推进逐一枚举**；三者并集 = 该像素全部可见物体；实体（含水下掉落物）走 §5.3、与深度锚点正交。**v2.26**：v2.24 的候选粗筛（8px 栅格合并，同格只留最近）会在判定**之前**丢弃嵌套候选、破坏本条论证（深湖中间水格系统性漏检），本版改区间推进后论证成立。

**谓词实现注意（v2.26 修复，源码核实 §3.1.1）**：`isSemiTransparentLayer(BlockState)` 必须**双分支**与 `SectionCompiler` 同构——①非空流体（水）按 `ItemBlockRenderTypes.getRenderLayer(FluidState)` 判层（水 → TRANSLUCENT）；②空流体方块按 `getChunkRenderType(BlockState)` 判层（TRANSLUCENT/TRIPWIRE）。若只查后者：`TYPE_BY_BLOCK` 不含 `Blocks.WATER`，水恒判 SOLID → 工序 B 命中率为 0、推进的 section 级 `maybeHas(isSemiTransparentLayer)` 对含水节返回 false（整节 SEC_EMPTY 跳步、水格一律不访问）——"Fabulous 只见水底不见水面"的全部原因。修复后：水节 SEC_HAS → 工序 C 逐格枚举整个水柱（水面格 + 中间水格，至不透明河床止），岩浆流体层为 SOLID → 仍返回 false（写主深度、§5.1 拾取），与渲染事实一致。

**工序 B：首层透明面深度 pass（O(全像素)，与 §5.1 对称，无射线-AABB）**：

```
for p in 全部像素:
    if translucentDepth(p) < mainDepth(p):       # 该像素有半透明遮挡
        S = unproject(p, translucentDepth(p))    # 首层半透明面表面点（复用 Unprojector，度量同 main）
        pos = BlockPos.containing(S)
        st  = level.getBlockState(pos)
        if isSemiTransparentLayer(st):           # 半透明层方块 → 精确落位
            addTerrain(terrain, pos, st, timestamp)
        # else：含水不透明方块（箱子+水）→ 跳过——水挂在 waterlogged 状态，箱子由工序 A 放
    # ==：无半透明 → 跳过（不透明面由工序 A 处理）
```

**工序 C：区间射线推进正向归属（v2.26，取代 v2.24"候选枚举 + 8px 栅格合并 + 残留精筛"）**：

**为何重写**：v2.24 的候选粗筛含**完整性漏洞**——粗筛按 8×8 屏幕栅格**同格只留最近候选**（v2.11，为控海洋/大湖 10⁵~10⁶ 候选成本），深水柱各级水格投影重叠在同一格 → **嵌套候选在进精筛前被丢弃**（深湖中间水格、多层玻璃里层系统性漏检），与上方完整性论证矛盾。候选集 + 有损粗筛这条路本身不可救——粗筛同时是成本上限和完整性破坏者，须整体改为正向归属。

**核心思想**：与 §5.3 实体同构的**正向像素归属**——弃用"候选集"概念，逐像素用双锚点夹出透明区间 `[Z_translucent, Z_opaque]`，沿射线推进把区间内射线**实际穿过**的每一个透明格归属出来（含全部嵌套层），无任何有损合并。

**为何不能照搬 §5.3 实体法**：实体法的核心测试 W-in-box（`AABB.contains(W)`）依赖"实体写 main 深度、深度记录的表面点 W 落在实体身上"。透明方块**恰恰相反**——不写 main 深度、W 落在背景上，`contains(W)` 恒为假。能借的是**结构**（预过滤列表 + 正向像素归属 + 去重），核心测试须换成"射线穿过其实际形状且 `t_entry ≤ Z_opaque`"（本通道统一判定式）；桶粗过滤换为**区块级 `maybeHas(透明)` 跳步**（透明块在 W 之后、桶定位失效）。

**算法**（仅 Fabulous；每像素独立）：

```
对每个像素（仅 translucentDepth < mainDepth —— 存在透明区间；== 则无透明在前，跳过）：
  ① ray = pixelRay(px,py)（世界射线方向，像素中心约定 v2.11）
     Z_opaque = unproject(mainDepth) 的欧氏距离；d ≥ d_far（天空）→ Z_opaque = +∞
  ② voxel DDA 沿 ray 从相机推进到 Z_opaque：
     · 跨入新 section：section.getStates().maybeHas(isSemiTransparentLayer)==false
           → 整节跳步到下一 section 边界（O(1)，非透明节不逐格）
     · 逐格（仅透明节内）：st = level.getBlockState(pos)
           isSemiTransparentLayer(st)==false → 推进下一格
           true → 求"实际渲染形状"与射线的带符号 slab 交（水用 fluid.getHeight 缩放 Y_max、
                   普通透明块用 getShape，v2.10 同款；真不相交 → 推进下一格）
                   t_entry ≤ Z_opaque − δ → 上报该格（BlockPos.asLong 去重），推进下一格
     · 当前距离 ≥ Z_opaque → 停止（越过首个不透明面，其后被遮挡）
```

**正确性**：
- 该像素可见的全部透明格 = 射线在 Z_opaque 前**实际穿过**的透明格，逐一被检查 → **无漏**（深水柱每一层、玻璃+水、两层玻璃全部枚举）；
- 被不透明遮挡的透明格：射线先到 Z_opaque 即停 → 推不到 → 不报；
- 贴天空：Z_opaque=+∞，section 跳步保证只枚举"含透明"的节，不空走空气/远平面；
- 相机在盒内（游泳/站玻璃里）：起点在盒内，slab 带符号 t_entry 为负 → 判可见（§5.4 约定沿用）；
- 半透明-半透明重叠：MC translucent 混合渲染、后到前排序、两层都可见 → 全报是正确语义、非假阳性（同 v2.24）；
- 含水方块（箱子+水）：箱子的 blockstate 非透明层 → 不报水，箱子由工序 A（行为不变）。

**为何推进而非"候选枚举 + 精筛"**：候选枚举的粗筛（8 角投影 O(候选数) + 8px 合并）有损——嵌套候选被合并丢弃、完整性断裂；推进法无候选集、无有损近似，成本 = Σ(区间像素 × 射线穿过的透明格数) = **恰好答案体积**（section 跳步砍掉全部非透明节）。

**成本（v2.26 修订）**：
- 只有 `translucentDepth < mainDepth` 的像素（存在透明区间）才推进；纯不透明/天空场景 ≈ 0 开销；
- 推进总量 = Σ(区间像素 × 透明区间内格数)；单层水每像素 ≈ 1 格、深湖 ≈ 水深；满屏深湖最坏几十 ms 量级（Phase 9 实测）；
- **删除** v2.11 粗筛开销（10⁵~10⁶ 候选 8 角投影 + 8px 合并）与 v2.24 首层判别逻辑；工序 B（O(全像素) 单趟反投影）保留承担首层精确落位。

**线程与实现注意**：
- 推进读 blockstate / palette / section（渲染线程约束 §8）；射线-形状求交 + 深度读为纯数据。整个推进在渲染线程一次完成（与现状粗筛一致），**无需第二次 PBO**；
- **度量统一**：`t_entry` 与 Z_opaque 同坐标系（都欧氏距离——复用 §4.3 的 W 反投影；或都视轴距离），混用会错；δ≈0.05~0.1 容差吸收 float32 量化（§4.2；**v2.11**：该容差仅 ≤~100 格内成立，远距离量化误差超 δ，见 §4.2/§10.5）；
- **像素约定一致（v2.11）**：pixelRay / `depthAt` 读回 / Z_opaque 反投影**全部用像素中心 NDC**（§4.1）——角点/中心混用会引入半像素系统偏移，使 Z_opaque 与 t_entry 在 >~40 格外失配（半像素偏移已超 δ）；
- DDA 用标准体素推进（跨 section 按 SectionPos 边界取整跳步、逐格按 block 边界取整）；
- 子像素方块（占屏 <1px、射线从格间隙穿过）仍漏——§10.3 已知极限。

**输出**：与 §5.1 相同的 `TerrainBlockSnapshot(pos, blockId, stateProps, timestamp)`，合并进 terrain 通道（§6.1），**不新增 store**。水柱中间水格逐格上报后，记忆侧深湖/海洋可完整复现水柱（水冻结 v2.20 保证静态复现）。

---

## 六、数据层复用与输出

### 6.1 现有 store（接线改动，v2.2）

| store | 文件 | 策略 | v2.2 改动 |
|---|---|---|---|
| `VisionBlockEntityStore` | `block_entities.nbt` | 增量合并 | 不变；`sync(snapshots, agentPos, agentYaw, agentPitch, agentFov)`（朝向/FOV 变化亦标记 dirty，v2.15/v2.19） |
| `VisionTerrainStore` | `terrain.nbt` | 快照覆盖 | `sync(blocks, deletions, agentPos, agentYaw, agentPitch, agentFov)` —— **删 `scannedSections` 参数/字段**；内容 = 本次**可见方块**（含半透明/绊线方块：Fabulous 经 §5.4、Fancy/Fast 经 §5.1 主路径，v2.10/v2.12；同一 `TerrainBlockSnapshot` 流）**+ 顶层 `deletions`（v2.23：被证明消失的记忆格，§7.11）** |
| `VisionEntityStore` | `entities.nbt` | 快照覆盖 | `sync(entities, agentPos, agentYaw, agentPitch, agentFov)` —— **删 `scannedSections` 参数/字段**；内容 = 本次**可见实体** |
| `collectEntityNbt(uuid, force)` | — | Tier-2 按需 | 不变 |

- **接线**：三个 store 由 `VisionCollector` 持静态单例（`getStore()` / `getTerrainStore()` / `getEntityStore()`），`VisionApi.snapshot` 统一调用；`blockId`/`stateProps` 改 package-private 供 `ObjectResolver` 复用；
- **删 `scannedSections`**：采集侧三个 store 不再写该字段（Phase 3）；记忆侧 restorer 对该字段的读取与指纹一并移除（Phase 4）——纯累积语义由 restorer 删除移除权威直接实现；
- `agentPos`（观察者**眼睛**坐标，直接存相机位置 `cameraPos` 双精度，v2.18 起不再取整到方块）与 `agentYaw`/`agentPitch`（观察者朝向，`camera.yRot()`/`camera.xRot()`，v2.15，本就用 float = 游戏精度）、`agentFov`（基础视场角，`options.fov().get()` 整数度 = 游戏精度，v2.19）仍写入文件顶层，供记忆世界玩家传送 + 视角跟随 + 视场角同步。
- **减量（v2.23）**：`terrain.nbt` 顶层另含 `deletions`（`List<BlockPos>`，采集侧 `DeletionJudge` 对记忆侧 `memory_cells.bin` 逐块判定输出，见 §7.11）；v2.22 的 `surface`/`skyRays` 字段**不再落盘**（减量证据被逐块判定完全取代）。

### 6.2 API 输出

`vision/snapshot`（当前：Phase 1 深度采集验证）：
- 返回深度图元信息：`width`/`height`（= 深度纹理原生尺寸）、`depthMin`/`depthMax`、`nonSkyPixels`（非天空像素数，`d < d_far`，v2.6 天空阈值=远平面极限深度 `d_far`=1.0，勿用 0.999、勿用 1−1e-6）、`cameraPos`、`timestamp`；
- 语义校验：`nonSkyPixels` 占比 ≈ 场景实际可见几何量；`depthMin` 随距离变化（near=0.05、far≈renderDistance×4 下，2 格 ≈ 0.975）。

`vision/snapshot`（Phase 2+ 目标，v2.2 定稿）：
- 保留 Phase-1 元信息：`width`/`height`/`depthMin`/`depthMax`/`nonSkyPixels`/`cameraPos`/`timestamp`（min/max/nonSky 在 `visibleBlockHits` 顺带统计，不二次扫描）；
- `visibleBlockCount` / `blockEntityCount` / `entityCount`：可见对象数（`visibleBlockCount` 含半透明/绊线方块：Fabulous 经 §5.4、Fancy/Fast 经 §5.1，v2.10/v2.12）；
- `blockEntities[]`：可见方块实体（`{pos, typeId, block, state, nbt}`，复用 `nbtToJson`）——**nbt 为 §5.2.1 分层白名单过滤后字段**：容器家族等客户端恒空类型 nbt=∅、`decorated_pot.item`/`brushable_block.item` 等隐藏内容已剥除，仅含 L1 可观察字段；
- `entities[]`：可见实体（Tier-1 轻量：id/uuid/type/pos/rotation/motion/onGround/health）；
- `storeStats`：`{terrain:{blocks}, blockEntities:{new,updated,skipped}, entities:{entities}}`；
- 完整可见方块表**默认不入 JSON**（进 `terrain.nbt`）；是否提供 `includeBlocks` 参数**待定**（审阅时决策）。

`vision/entity?uuid=…&force=…`：不变，实体全量 NBT 按需。

---

## 七、记忆世界（语义变更）

### 7.1 核心决策：纯累积语义

> **"记录每次看到的所有内容，不在之内的不更新即可。"**

- 每个采集周期，文件里有什么 → 记忆世界**放置/更新**它；
- 文件里没有的 → **不更新、不移除**；
- 由此**删除整套 `scannedSections` 移除权威逻辑**（v1 用它区分"挖掉"和"没扫到"，v2 不再需要——因为被遮挡/移出视野的对象本来就不该被删）。
- **v2.22/v2.23 修订**："绝不移除"改为"**有几何证据才移除**"（减量）：记忆世界里实心 + 不透明的方块/实体格被证明在现实中已消失 → 删除；被遮挡/移出视野（无证据）的对象仍保持冻结记忆。证据来源 v2.22 为"相机→表面/天空的射线穿过"（记忆侧 DDA 投票，有稀疏采样盲区）；**v2.23 重构为"反向通道 + 采集侧逐块深度判定"**（§7.11）——逐块覆盖无盲区、单快照判定、静态玩家可删，且不增加误删。

### 7.2 各通道改动

| 通道 | v2 行为 |
|---|---|
| `TerrainRestorer` | 读 `terrain.nbt`，放置/更新方块；**删除移除权威**（不再 setBlock 空气）。**v2.10 方块实体生命周期失步修正**：当某位置**方块类型改变且新方块无方块实体**时，主动清除该位置的旧方块实体（`block_entities.nbt` 增量合并永不删除旧条目 → 否则新方块位置残留旧 BE 的 ghosting） |
| `EntityRestorer` | 读 `entities.nbt`，放置/移动/冻结实体；**删除移除权威**（不再 discard） |
| `MemoryRestorer`（方块实体） | 增量合并，**不变**（清除逻辑移交 `TerrainRestorer`，见上） |

### 7.3 行为后果（设计意图，需确认）

- **有几何证据才移除（v2.22/v2.23 修订）**：被挖掉的方块、被杀掉的实体**保留**，直到被证明在现实中已消失——v2.23 减量只删"实心不透明格被 ≥2 条独立射线穿过整格"（§7.11），被遮挡 / 移出视野 / 超出 `removalMaxRayDist` 的对象因无证据**仍保留**（记忆直觉不变，符合"记忆"语义）；
- **只存可见**：记忆世界 = 所有**曾经可见**的方块/实体的累积（换场景后旧场景保留，新场景追加）；
- **被遮挡 ≠ 消失**：实体走进墙后 → 不再可见 → 不再更新 → 留在记忆世界**最后一次可见位置**（冻结态）；
- **方块实体失步防护（v2.10）**：纯累积下 `block_entities.nbt` 增量合并永不删除 → 方块被替换后旧 BE 残留（"石头里的箱子实体"类 ghosting）。`TerrainRestorer` 须在方块类型变化且新方块无 BE 时联动清除旧 BE（§7.2），不能仅依赖增量合并。

### 7.4 更新触发机制（快照驱动 + mtime 门控，v2.13 定稿）

> 先决约束：记忆世界与采集器是两个**独立进程**（记忆世界 = "另一客户端"，本身没有 WebSocket 通道，见 §二），因此**无法**字面地"收到 vision/snapshot API 调用就触发"。

- **触发本质**：三个源文件（`terrain.nbt` / `entities.nbt` / `block_entities.nbt`）**只在采集器侧 `vision/snapshot` 被调用并落盘时才变化**——记忆世界要响应的唯一事件就是"文件刚被重写"。故以**文件 mtime 作为快照到达信号**；
- **每 tick mtime stat**：`pollIntervalTicks` 默认降为 **1**（每 tick）。每个 restorer 每 tick 对源文件做一次 `Files.getLastModifiedTime`（stat，纳秒级）；**mtime 与上次成功读取的记录一致 → 直接返回，不读、不解压文件**；
- **mtime 变化才读**：mtime 变化 → 才走原有 `readFile` + **内容指纹门控**（文件被重写但内容相同 → 读一次、指纹未变 → 仍不更新世界）；
- **mtime 仅在读取成功后推进**：文件写入中途读到半截 → `readFile` 返回 null → **保留旧 mtime → 下轮重试**（与现有"读失败不碰 `appliedFingerprint`"语义一致，防永久跳过）；
- **源文件缺失**：重置 `lastMtime = null` + `appliedFingerprint = null`，文件重新出现后自然触发首次读取。

**效果**

| 指标 | v1（轮询整文件） | v2.13（mtime 门控） |
|---|---|---|
| 更新延迟 | ≤ `pollIntervalTicks`（默认 20 tick = 1s） | 快照落盘后 **≤1 tick（50ms）** |
| 空闲成本 | 每秒整文件读 + 解压 + 指纹 | 每秒 3 次 stat ≈ **零** |
| `block_entities.nbt` 增量累积 | 文件无限增长 → 每次轮询浪费放大 | 只在快照落盘后读，浪费不累积 |
| 跨进程通道 | 无 | 无（无需新增） |

### 7.5 视角跟随（每次更新后传送 + 调视角，v2.15；位置精度 v2.18）

> 背景：`agentPos` 原本只记录观察者所在**方块坐标**、不含朝向，且记忆世界只在进入时传送一次 → 观察者移动/转身后玩家视角不跟随。故采集侧新增朝向字段、记忆侧改为每次更新后跟随；v2.18 再把位置精度提升到**双精度眼睛坐标**（= 游戏坐标精度）。

- **采集侧字段**：三个源文件顶层写 `agentPos`（观察者**眼睛**坐标 = 相机位置，双精度，v2.18 起不再取整到方块）与 `agentYaw` / `agentPitch`（`camera.yRot()` / `camera.xRot()`，度，float = 游戏精度）、`agentFov`（基础视场角，`options.fov().get()`，整数度，v2.19）；`VisionBlockEntityStore` 在朝向/位置/FOV 变化时标记 dirty → `block_entities.nbt` 随视角刷新（记忆世界据此感知视角更新，即使方块实体未变）；
- **记忆侧跟随**：`MemoryRestorer.tick` 在文件 mtime 变化读取后，除内容指纹门控同步方块实体外，**额外比较 agent 视角（位置 + 朝向）**——视角变化 → 返回新 `AgentPose`（位置为 `Vec3` 双精度）；`MemoryWorldManager` 据此对玩家 `teleportTo(眼睛 − eyeHeight, yaw, pitch)`。**与内容指纹解耦**：仅观察者转头/移动（方块实体未变）也会触发跟随；
- **眼睛 → 脚部换算（v2.18）**：`cameraPos` 是相机**眼睛**位置（`camera.position()`），而 `teleportTo` 设置的是**脚部**位置；故传送时把脚部放 `agentPos.y − getEyeHeight()`，使记忆世界玩家眼睛精确落在 agent 采集时的眼睛位置（还原同一视角）；
- **进入时传送并入跟随**：原"进入记忆世界一次性传送到 agentPos"改为每次更新后执行；`setupPlayer` 只负责创造 + 飞行（防掉虚空），首个文件读取自然触发首次传送；
- **向后兼容**：旧整数 `agentPos`（如 `"100,64,96"`）仍可被 `Double.parseDouble` 解析为 100.0/64.0/96.0；旧文件无 `agentYaw`/`agentPitch` → 朝向读为 NaN → 传送沿用玩家当前朝向；视角比较对 NaN 视为相等（避免旧文件每次更新都重传）；
- **去抖**：yaw/pitch 差 < 0.5° 视为未变，过滤轻微转头抖动；位置差 < 1 mm（`POS_EPSILON`）视为未变（v2.18 由 `BlockPos.equals` 的 1 格容差收紧为 1 mm，过滤双精度下的亚像素抖动）。

### 7.6 重力方块禁用（v2.17，⚠️ **已并入 §7.9 全局冻结，本实现已删除**）

> 背景：记忆世界只复现 agent 看到的**表面**方块，其下方/后侧支撑方块因被遮挡从未采集 → 沙/沙砾/混凝土粉末/铁砧/龙蛋、脚手架、滴水石锥等"会下落"的方块复原后失去支撑，下落会破坏复现。以下三个 Mixin 的实现已按 §7.9"合并策略"移除——下落统一由 `tickBlock` 全局冻结覆盖，本节省机理背景与源码事实，实施以 §7.9 为准。

- **方案：tick 层持续禁用**。记忆侧新增**三个 Mixin**，统一模式——`@Inject(method="tick", at=@At("HEAD"), cancellable=true)`，当 `MemoryWorldManager.isMemoryWorld(level)` 时 `ci.cancel()`。记忆世界识别按**世界名**（`getWorldData().getLevelName().equals(MemoryConfig.get().worldName)`，与 `onServerTick` 过滤一致）——记忆世界与普通主世界同用 `minecraft:overworld` 维度，无法按维度区分；
- **为何不在"放置瞬间"关闭**：下落由 `onPlace`/`updateShape` 排的**延时** tick 触发，且相邻方块放置会互相触发 `updateShape` 再排 tick——临时关闭一段 tick 后开关会被后续排的新 tick 绕过，必须**在 tick 处持续**禁用；
- **三者的下落机制（源码核实）**：

  | 方块 | 类型关系 | `tick` 里的下落/破坏动作 |
  |---|---|---|
  | 沙/沙砾/混凝土粉末/铁砧/龙蛋 | 继承 `FallingBlock` | `FallingBlock.tick`：`isFree(下方)` 且 `y≥minY` → `FallingBlockEntity.fall` |
  | 脚手架 `ScaffoldingBlock` | 独立于 `FallingBlock` | `tick` 重算 `DISTANCE`/`BOTTOM`；`DISTANCE==7` 时——上一状态已 7 → `FallingBlockEntity.fall`，刚变 7 → `destroyBlock` |
  | 滴水石锥 `PointedDripstoneBlock` | 实现 `Fallable`（非 `FallingBlock` 子类） | `tick`：石笋（尖端朝上、失去支撑）→ `destroyBlock`；否则 → `spawnFallingStalactite`（石柱及下方相连尖端转下落实体） |

  三个 Mixin 分别对应 `FallingBlockMixin` / `ScaffoldingBlockMixin` / `PointedDripstoneBlockMixin`。取消 `tick` 对脚手架还额外冻结了 `DISTANCE`/`BOTTOM`（不被重算），对滴水石锥同时阻止石笋破坏与石柱下落；
- **已知遗留（不属下落，未处理）**：滴水石锥的 `randomTick`（石柱生长 + 向炼药锅转移流体）依赖 `DRIPSTONE_BLOCK`+水源且概率极低，不影响"冻结复现"的常规场景，暂不在禁用范围；脚手架的下落本身无独立 `randomTick`；
- **NBT 与纯累积语义不变**：采集端照常记录方块状态，`terrain.nbt` 内容不变；`isMemoryWorld` 只影响**记忆世界进程**内的物理判定，采集器进程不受影响。

### 7.7 视觉设置同步（FOV，v2.19）

> 背景：真实世界（采集侧）可经 `settings/set` 改基础视场角，而记忆世界无任何 FOV 控制、用本地 `options.txt` 默认 70°——两侧视场角可能不一致，站到同一位置同一朝向看到的视场不同。

- **采集侧**：`DepthSnapshot` 快照新增 `fov`（`options.fov().get()`，`OptionInstance<Integer>`，整数度 = 游戏精度，合法范围 [70,110]），`ObjectResolver` 把 `agentFov` 与 `agentPos`/`agentYaw`/`agentPitch` 一并写入三个源文件顶层；
- **记忆侧**：`AgentPose` 新增 `fov`；旧文件无 `agentFov` → 读哨兵 `-1`；`teleportToPose` 传送后经 `Minecraft.execute` 在渲染线程 `options.fov().set(...)` 对齐（与当前值相等则跳过，避免每次传送都写 options.txt）；
- **去抖 / 兼容**：`samePose` 中 FOV 任一侧为 `-1` 视为相等（旧文件每次更新不重传）；FOV 变化也触发一次传送（沿用视角跟随的既有触发路径）；
- **动态 FOV 不重复记录**：疾跑/水下/望远镜等造成的 FOV 动态变化已烤进采集时的投影矩阵（§3.3），反投影/可见性自洽；这里只同步**基础** FOV（滑块值）；
- **已知限制（仍不同步）**：渲染距离、模拟距离、画面品质（Fast/Fancy/Fabulous）、窗口宽高比、视角晃动（bobView）仍各自用本地选项，未纳入同步。其中渲染距离/画面品质会影响"哪些方块被采集"与"哪些方块被显示"，如需严格一致需另行同步（待定）。

### 7.8 流体流动冻结（v2.20，⚠️ **已并入 §7.9 全局冻结，本实现已删除**）

> 背景：记忆世界只复现 agent 看到的流体（水/岩浆）**表面**，其水源方块、支撑/围挡方块可能因被遮挡未采集 → 复原后的水/岩浆失去约束，立即流动、向邻格扩散、或（流动流体失去水源时）蒸发成空气，破坏"冻结复现"。`FlowingFluidMixin` 已按 §7.9"合并策略"移除——流动统一由 `tickFluid` 全局冻结覆盖，本节省机理背景与源码事实，实施以 §7.9 为准。

- **方案：tick 层持续禁用**。记忆侧新增 `FlowingFluidMixin`，统一模式——`@Inject(method="tick", at=@At("HEAD"), cancellable=true)`，当 `MemoryWorldManager.isMemoryWorld(level)` 时 `ci.cancel()`；
- **为什么是 `FlowingFluid.tick` 单点**：流体的扩散/蒸发/水位重算**全部**汇聚在 `FlowingFluid.tick`——`ServerLevel.tickFluid` 在流体排程 tick 到期时经 `FluidState.tick` 分发到它；水（`WaterFluid`）与岩浆（`LavaFluid`）都是 `FlowingFluid` 子类且**均未重写 `tick`**，故一个 Mixin 同时覆盖两者。取消后：
  - 非源流体水位重算（`getNewLiquid`）停止 → 流动流体的 `LEVEL` 不再变化；
  - 邻格扩散（`spread`/`spreadToSides`/`spreadTo`）停止 → 不再生成新的水/岩浆格、不再破坏邻格方块（`beforeDestroyingBlock` 掉落/`fizz`）；
  - 流动流体蒸发成空气（`newFluidState.isEmpty()` → setBlock 空气）停止 → 流体停在记录状态；
- **为何不在"放置瞬间"关闭**：流动由 `onPlace`/`updateShape`/`neighborChanged` 排的**延时** tick 触发，且相邻方块放置会互相触发 `updateShape` 再排 tick——临时关闭无用，必须 tick 层持续禁用（与 §7.6 重力同一理由）；
- **已知遗留（不属流动，未处理）**：岩浆的 `randomTick`（`LavaFluid.randomTick`，`isRandomlyTicking()==true`）会向附近可燃方块**蔓延火焰**——这是岩浆的火灾隐患、不是流体流动，暂不在冻结范围；水无 `randomTick`（`WaterFluid` 未开 `isRandomlyTicking`）；
- **NBT 与纯累积语义不变**：采集端照常记录流体方块状态（`minecraft:water`/`minecraft:lava` 的 `LEVEL`），`terrain.nbt` 内容不变；`isMemoryWorld` 只影响**记忆世界进程**内的流体 tick，采集器进程不受影响。

### 7.9 全局冻结：所有 tick 驱动的场景变化（v2.21，已实施）

> 背景：记忆世界是现实世界**某一瞬间**的冻结复现。凡是由 tick 推进的场景变化都应停止——不止已处理的重力（§7.6）与流体流动（§7.8），还包括火焰蔓延、TNT 爆炸、红石信号传输、活塞伸缩、作物生长、冰块/积雪融化、熔炉/漏斗/刷怪笼工作、箭矢飞行、TNT 引信倒计时、经验球合并、昼夜与天气推进等。逐一给每个方块/实体写 Mixin 无法穷举，必须在**分发层**一次性冻结，把所有变化拦在"入口"处。

**变化分类（1.21.11 源码核实）**：tick 推进的场景变化共 8 类，v2.21 已全部覆盖——

| # | 变化 | 分发入口 | 冻结手段（v2.21） |
|---|---|---|---|
| ① | 实体 AI / 运动 + 自毁/寿命（TNT 引信、箭矢寿命、经验球合并/过期、物品旋转） | `ServerLevel.tickNonPassenger` → `Entity.tick()` | `EntityRestorer.freeze`（NoAI/NoGravity/零速度/无敌）+ **`tickNonPassenger` 冻结标记** |
| ② | 重力方块下落 | `ServerLevel.tickBlock` → `FallingBlock.tick` 等 | **`tickBlock` 全局冻结**（原 v2.17 三个 Mixin 已删除） |
| ③ | 流体流动 / 蒸发 | `ServerLevel.tickFluid` → `FlowingFluid.tick` | **`tickFluid` 全局冻结**（原 v2.20 `FlowingFluidMixin` 已删除） |
| ④ | 排程方块 tick（红石/活塞/TNT/火焰/树叶衰减…） | `ServerLevel.tickBlock` → `BlockState.tick` | **`tickBlock` @HEAD cancellable** |
| ⑤ | 随机方块 tick（作物/冰/雪/藤蔓/岩浆点火…） | `ServerChunkCache.tick` → `ServerLevel.tickChunk`（`tickSpeed=random_tick_speed`） | **gamerule `random_tick_speed=0`** |
| ⑥ | 方块实体 tick（熔炉/漏斗/刷怪笼/活塞动画…） | `Level.tickBlockEntities` → `TickingBlockEntity.tick` | **`tickBlockEntities` @HEAD cancellable** |
| ⑦ | 昼夜 / 天气推进 | `ServerLevel.tickTime`（`ADVANCE_TIME`）/ `advanceWeatherState`（`ADVANCE_WEATHER`） | **gamerule `advance_time=false`、`advance_weather=false`** |
| ⑧ | 自然刷怪 / 自定义刷怪 | `ServerChunkCache.tick` 自然刷怪 + `tickCustomSpawners` | **gamerule `spawn_mobs=false`**（v2.30，建档随 LevelSettings 写入 + 首次进入再设兜底） |

**全局冻结实现（两个 Mixin，四个注入点）**：

- `ServerLevelMixin`（`@Mixin(ServerLevel.class)`），三个注入点：
  - `tickBlock(BlockPos, Block)` @HEAD cancellable：`isMemoryWorld(this)` 时 cancel。排程方块 tick 的唯一分发入口——`ServerLevel.tick` → `blockTicks.tick(..., this::tickBlock)` → `state.tick`（`ServerLevel.java:801`）。冻结红石、活塞、TNT、火焰、树叶衰减、重力方块（②）等**所有**排程方块 tick。
  - `tickFluid(BlockPos, Fluid)` @HEAD cancellable：流体 tick 的唯一分发入口（`ServerLevel.java:793`），冻结流体流动/蒸发（③）。
  - `tickNonPassenger(Entity)` @HEAD cancellable：`isMemoryWorld(this) && EntityRestorer.isFrozen(entity)` 时 cancel。实体每 tick 的唯一分发入口（`ServerLevel.java:808`），冻结实体 tick——覆盖 AI/运动 + **自毁/寿命**（TNT 引信、箭矢/三叉戟 `life`、经验球 `Age` 与合并、物品旋转）。为何拦这里而非 `Entity.tick`：自毁逻辑在实体自身重写的 `tick()` 里且**不调 `super.tick()`**（如 `PrimedTnt.tick` 把引信递减到 `explode()` 全写在自身方法内，`PrimedTnt.java:96-119`），对 `Entity.tick` @HEAD 注入根本拦不到；`tickNonPassenger` 是所有实体 tick 的调用方，一个注入点覆盖全部。
- `LevelMixin`（`@Mixin(Level.class)`）：`tickBlockEntities()` @HEAD cancellable，`this instanceof ServerLevel sl && isMemoryWorld(sl)` 时 cancel。方块实体 tick 的唯一分发入口（`Level.java:524`，`ServerLevel.tick` → `tickBlockEntities()`，`ServerLevel.java:434`），冻结熔炉/漏斗/刷怪笼/活塞移动方块动画等。

> 注：`tickBlock`/`tickFluid`/`tickNonPassenger` 在 `ServerLevel`、`tickBlockEntities` 在 `Level`——两个 Mixin 目标类不同。HEAD 取消后排程 tick 条目已从 `LevelTicks` 队列出队（不累积）；复原期 `onPlace`/`updateShape` 排的新 tick 每帧被取消、无害。

**gamerule（v2.21，运行时一次性设置）**：

- `random_tick_speed=0`（`GameRules.RANDOM_TICK_SPEED`，默认 3）：`ServerChunkCache.tick` 读它作 `tickSpeed`（`ServerChunkCache.java:379`）并传 `ServerLevel.tickChunk(chunk, tickSpeed)`；`tickChunk` 里 `tickSpeed>0` 分支整体跳过（`ServerLevel.java:505`）→ 作物生长、冰/雪融化、藤蔓蔓延、岩浆 `randomTick` 点火（§7.8 已知遗留）**全部停止**。
- `advance_time=false`（`GameRules.ADVANCE_TIME`）+ `advance_weather=false`（`GameRules.ADVANCE_WEATHER`）：`tickTime` 里 `ADVANCE_TIME` 门控 `setDayTime`（`ServerLevel.java:466`）、天气推进受 `ADVANCE_WEATHER` 门控 → 昼夜/天气不再推进（时间对齐由 §7.10 `setDayTime` 单独设值，不冲突）。
- `spawn_mobs=false`（`GameRules.SPAWN_MOBS`，v2.30）：`ServerChunkCache.tick` 读它作 `doMobSpawning`（`ServerChunkCache.java:378`）——false 时 `spawningCategories` 置空（:381-385）且跳过 `tickCustomSpawners`（:406-409）→ **自然刷怪 + 自定义刷怪（⑧）全停**。双落点：新建世界建档时随 LevelSettings 的 GameRules 写入（`PrimaryLevelData.getGameRules() = settings.gameRules()`，从 tick 0 起生效并持久化）；首次进入记忆世界 `applyFreezeGameRules` 再设一次，兜底改版前创建的旧记忆世界。注：`EntityRestorer` 放置的实体走 `addFreshEntity`、不经自然刷怪路径，不受此开关影响。
- 设置时机：`MemoryWorldManager.onServerTick` 的 `playerReady` 一次性块（进入记忆世界后设一次）。`spawn_mobs=false` 另在建档时随 LevelSettings 一并写入（v2.30，见上）。gamerule 是世界级状态、随记忆世界存档持久化，只影响记忆世界。
- 本版本 gamerule 已 snake_case 改名，实现一律以 `GameRules` 常量字段为准（`RANDOM_TICK_SPEED` / `ADVANCE_TIME` / `ADVANCE_WEATHER` / `SPAWN_MOBS`…），勿用 vanilla 的 `randomTickSpeed`/`doDaylightCycle` 字符串。

**三个"非 tick"陷阱（v2.21 已解决，分发层冻结覆盖不到）**：

1. **`setBlock` 即时邻居更新 → 静默放置**：复原 flag 从 `3`（`UPDATE_ALL`）改为 `Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS`（= 2 | 816 = 818）。源码依据：`Level.java:240`（bit1 → `updateNeighborsAt`）、`Level.java:247-252`（bit16 未置位 → 形状更新传播）、`LevelChunk.java:320`（bit1 门控 `affectNeighborsAfterRemoval`）、`LevelChunk.java:328`（bit512 未置位 → `onPlace`）、`WallTorchBlock.updateShape`（支撑方向变化 + `!canSurvive` → 返回 AIR 破坏，`WallTorchBlock.java:83-93`）、`RedStoneWireBlock.onPlace`（放置即重算电量，`RedStoneWireBlock.java:303-305`）。818 = 无邻居更新 + 跳过形状传播 + 跳过 `onPlace` + 抑制掉落（bit32）+ 抑制旧 BE 移除副作用（bit256），保留 bit2（客户端 `sendBlockUpdated` 同步）与光照（`queueCheckLight`）。效果：**所有方块形态以 NBT 记录为准**——挂墙方块不因放置顺序被破坏、红石线/绊线保留采集时连接与电量、被替换的旧方块不掉落物、无临时中间态。`TerrainRestorer.place` 与 `MemoryRestorer.place` 两处都改。
2. **实体自毁 / 引信 / 寿命 → `tickNonPassenger` 分发层冻结**：`EntityRestorer.freeze` 给放置实体打**冻结标记**——本版本已无 `getPersistentData`（全源码搜索为空），改用静态 `Set<Entity>`（`IdentityHashMap` 支撑），`freeze()` 加入、实体 `isRemoved()` 时清理；新增 `EntityRestorer.isFrozen(Entity)` 供 Mixin 查询。`ServerLevelMixin.tickNonPassenger` @HEAD 对标记实体 cancel——一个注入点覆盖 TNT 引信/箭矢寿命/经验球合并与过期/物品旋转/其余一切 tick 计时器，未来新增自毁类实体无需再写 Mixin。玩家不受影响（标记只打给 restorer 放置的实体）；Mob 的 `checkDespawn` 在 `tickNonPassenger` **之前**独立执行（`ServerLevel.java:414`），`setPersistenceRequired` 兜底；`snapTo`/`teleportTo` 已调 `setOldPosAndRot`（`Entity.java:1720`），冻结后客户端插值仍静态正确。
3. **方块实体初始状态**：冻结 `tickBlockEntities` 后 BE 停在 NBT 记录的初始进度（熔炉烧制进度/漏斗内容/刷怪笼延迟）——正符合"冻结复现"，**不是缺陷**。绝大多数 BE（箱子/告示牌/熔炉）读 NBT 字段即正确渲染、不依赖首次 tick。默认不做任何事；测试期逐类核验渲染与序列化，若某 BE 依赖首次 tick 自举而显示异常，对该类型放置后补调一次初始化（不写通用逻辑）。

**补充：`runBlockEvents`（方块事件）无需单独冻结**：`Level.runBlockEvents`（`ServerLevel.java:388`）分发音符盒发声、铃铛、活塞延伸等**方块事件**——它们只在世界发生变化时被排队（活塞延伸由 `neighborChanged`/`checkIfExtend` → `addPistonEvent` 触发）。冻结后世界静止、不再有新事件入队，故无需禁用；若要在复原过程中也彻底静止，可一并冻结（可选，非必需）。

**合并策略（已决：验证后删除）**：v2.17（`FallingBlockMixin`/`ScaffoldingBlockMixin`/`PointedDripstoneBlockMixin`）与 v2.20（`FlowingFluidMixin`）的注入点全部落在 `tickBlock`/`tickFluid` 下游，被全局 `tickBlock`/`tickFluid` 冻结完全涵盖 → **已从 `mixins.json` 移除并删除源文件**。全局冻结在更上层、覆盖更广，逐块 Mixin 只能穷举、不可扩展。采集侧与纯累积语义不变：三个 NBT 照常记录，`isMemoryWorld` 只影响记忆世界进程内判定。

### 7.10 世界时间同步（dayTime，v2.21）

> 背景：记忆世界是现实世界**某一瞬间**的复现，昼夜也应停在采集那一刻——不仅"不推进"，还要**对齐**到采集时的 `dayTime`（太阳/月亮/天光角度与采集一致）。采集侧把快照时刻的世界时间落盘，记忆世界复原时 `setDayTime` 到同一值。

- **采集侧**：`DepthSnapshot` 新增 `dayTime`（`level.getDayTime()`，long，游戏时间单位），`ObjectResolver` 把 `dayTime` 一并写入三个源文件顶层（`dayTime` 字段）。`VisionBlockEntityStore` 增量脏标记：`dayTime` 变化时标记 dirty 才刷新文件；
- **记忆侧**：`MemoryRestorer` 读 `dayTime`（旧文件无该字段 → 哨兵 `-1`），在 mtime 门控放行后、与 pose/fingerprint 解耦独立判断——`dayTime >= 0 && dayTime != lastDayTime` 时 `level.setDayTime(dayTime)` 并推进 `lastDayTime`（agent 站桩不动时，时间照常随每次采集对齐）。`onServerStart()` 重置 `lastDayTime`；
- **与冻结不冲突**：`advance_time=false` 只阻止 `tickTime` 自增，`setDayTime` 直接设值仍然生效 → 世界时间冻结在采集值，太阳/月亮/天光与采集一致；
- **已知限制（未同步）**：天气（晴/雨/雷）未落盘——记忆世界沿用本地天气；`advance_weather=false` 后本地天气保持进入时状态，可能与采集时刻不一致（如需严格一致须另行落盘 `raining`/`thundering` 并在进入时 `resetWeatherCycle`+设置，待定）。刷怪（⑧）经 `spawn_mobs=false`（建档 + 首次进入，v2.30）全停，不依赖 `dayTime`/光照。

### 7.11 减量：反向通道 + 采集侧逐块深度判定（v2.23，✅ 已实现 2026-08-25）

> **为什么重构 v2.22（审阅结论）**：v2.22（已实施 2026-08-25）由"表面点 + 天空射线 → 记忆侧 DDA 投票（K≥2 跨帧累积 + 证据门控）"驱动，有两类缺陷：
> ① **稀疏采样盲区**：表面点按可见方块去重（每块一个），被挖掉的方块背后若是**单面平墙**，只有 1 条表面射线穿过该格；小块/远块在 24px 天空网格下可能 0~2 条。单次运行票数 < K 时，**证据门控**（内容不变只投一次）使**静态玩家**（证据不变）的票数永久卡死 <K → 永远删不掉；
> ② **"深度传记忆侧 + 逐像素 DDA"不可行**：854×480 深度 1.6MB、4K 33MB/快照（渲染线程写盘卡帧）；逐像素 DDA 千万~亿级格访问（秒级 CPU）。但"逐像素"的准确度判断本身成立——**全密度射线只减少"该删没删"，不增加"不该删删了"**（真实存在的实心块任何射线都到不了它后方）。
> **结论**：保留逐像素级的判定完整度，但**把查询反过来**——不逐像素 DDA，而是对"记忆世界里确实存在的每个块"用深度图做 O(1) 判定（§4.3"深度当可见性查询器"思路）；深度图**不跨进程传输**，由本来就持有它的采集侧判定；记忆侧通过**反向通道**把现状上报。

**核心原理（与 v2.22 相同，充要条件不变）**：某**实心 + 不透明**格 B 在现实中若存在，任何从相机出发的射线碰到它近表面即**终止**（正是深度测到的值）。故——**「存在某像素射线穿过了记忆格 B 的整格（该像素深度 ≥ B 远面距离）」⟺ B 在现实中已不存在**。删除有几何证明，非启发式。

**判定机制（采集侧 `DeletionJudge`，复用 §5.4 全部几何工具）**：

- 输入：① 本次快照的深度图 + 相机/投影（采集侧已有）；② 记忆侧上报的**当前存在**的实心不透明块 + 冻结实体占用格清单（cells 文件，见下）。
- 对每个记忆格 B（先跳过本次可见集 `currentTerrain` 内的格——可见格由 §5.1 放置/更新路径处理，不参与减量）：
  1. 投影 B 的 8 角 → 屏幕 bbox（像素中心约定 §4.1，循环前裁剪到屏幕范围 §5.4）；
  2. 逐 bbox 像素 p：射线(camPos→p) 与 B 的 AABB 做**手写 slab 求交**（§5.4）得 `t_entry`/`t_far`（近/远面距离）；不相交 → continue；读该像素深度还原 `Z_opaque(p)`（欧氏距离，与 t 同度量 §5.4）；**`Z_opaque(p) ≥ t_far − δ` → 该像素的射线穿过了 B 的整格 → 越过计数++**；
  3. **越过计数 ≥ `removalPixelThreshold`（默认 2）→ B 被证明消失 → 进 `deletions` 列表**。
- **为何 ≥2 而非 1**：单像素可能是浮点擦边/深度量化误读（§4.2）；≥2 是 B 自身投影内**多条独立射线一致证明**。**误删保护从"跨帧 K≥2 累积"改为"单次快照内 ≥2 像素越过"**——判定单快照完成，**无需跨帧、无需门控，静态玩家一次快照即可删**。
- **为何逐块而非逐像素 DDA**：DDA 对每个像素沿射线逐格走，多数像素穿的是"记忆世界里没有块"的空区域（浪费）；逐块只测"确实存在的块"的投影像素，成本 O(视锥内记忆块数 × 投影像素)（毫秒级），并天然覆盖**背后是天空 / 背后是不透明块 / 被部分遮挡**三况——**v2.22 的 surface/skyRays 两类证据被完全取代**。
- **可见块保护（双保险）**：深度测试本身已保护可见块（可见块的深度 = 自身表面 < t_far，不判越过）；`currentTerrain` 预筛只是省一次投影的优化。
- **更新 vs 删除分工**：格被换成**另一个可见方块** → 深度 = 新方块表面 < t_far → 不判消失 → 由 §5.1 放置/更新路径覆盖；只有**消失**（背后露出更深表面/天空）才由减量删除——二者不冲突。

**反向通道（记忆 → 采集，cells 文件）**：

- **记忆侧 `MemoryCellReporter`**：维护"当前记忆世界的实心不透明块 + 冻结实体占用格"集合（随放置/更新/删除维护，或写时全量扫描），按 agentPose 做**距离球过滤**（`|cell − agentPos| ≤ removalMaxRayDist`，**Over-inclusive：只缩距离、不做精确视锥**——采集侧用真实投影矩阵判定，越界格无像素命中、自然跳过，过滤只为了缩小清单）→ 写成 `memory_cells.bin`（BlockPos long 数组，几十 KB）。**触发**：世界变化 || 姿态变化 > ε（复用 §7.5 视角比较）|| 每 `memoryCellsWriteIntervalTicks`（默认 10）兜底；**内容指纹门控**（内容未变不重写）+ **原子写**（临时文件 + rename）+ mtime 仅在成功后推进（同 §7.4 半截写防护）。
- **采集侧读取**：快照时对 `memory_cells.bin` 做 mtime stat（未变不读），读入解析 → 交 `DeletionJudge`。**记忆侧离线 / 文件缺失 → 无删除证据 → 优雅降级为只增不删**（恢复后自愈：记忆侧下次写 cells，采集侧重新判定）。

**落盘与整合（`deletions` 进 terrain.nbt，记忆侧 `DeletionApplier`）**：

- 采集侧 `VisionTerrainStore.sync` 顶层写 `deletions`（BlockPos long 列表）；**`appliedFingerprint` 必须纳入 `deletions`**——否则"仅删除变化、方块不变"的快照会被 §7.4 指纹门控误跳过（若指纹由全文计算则天然包含）。
- 记忆侧在 terrain / entity / blockEntity **三通道 sync 之后**应用（增量先于减量，顺序不变）：
  - 对每个 deletion 格：当前格为实心不透明且不在本次可见集 → `setBlock(pos, air, 818)`（复用 §7.9 静默 flag）+ `MemoryRestorer.clearStale(pos)`；已是空气 / 可见 → no-op（**幂等**，支持重复收到）；
  - 冻结实体：不在当前帧实体快照内、且**全部 AABB 占用格 ∈ deletions** → `EntityRestorer.discard(uuid)`（"本次没看到"≠删，"占用格被证明为空"才删，语义不变）；
  - 相机所在格（记忆侧已知 agentPos）：无条件即时删（保留 v2.22 快路径，受 currentTerrain 防护）。

**语义转变（v2.22 已定，v2.23 使其收敛更快更完整）**：纯累积（只增不删）→ **增删收敛到当前可见状态**。被删 = 被证明不存在的部分，余下仍冻结；未证明的（被遮挡 / 移出视野 / 超出 removalMaxRayDist）保持记忆。

**已知边界（v2.23）**：
① 只判**实心 + 不透明**格 + **v2.36 的流体/满格透明格**可删（cells version≤2 文件只含前者 + 实体占用格）——被移除的**非满形状**透明/半实心方块（玻璃板/栅栏/压力板/红石线）仍永不删（v2.22 同，接受；未来方向：几何过滤，见 §7.13 设计草案）；排干的水/岩浆及**满格透明方块**（玻璃块/冰）的**半透明减量见 §7.12（v2.36，✅ 已实现 2026-09-08）**——Fabulous 下 translucent 场同构判据、Fancy/Fast 与岩浆走 main 场（放宽 reporter/applier 门槛：translucent 段 = 水 ∪ 满格透明、岩浆归 main 段）；
② **`removalMaxRayDist` 默认 96（详解见下）**：逐块判定依赖"深度 vs t_far 距离比较"，深度量化误差 δ 仅 ≤~100 格内可靠（§4.2/§10.5：300 格≈11cm、768 格≈0.7 格）——超出部分保守保留（宁欠勿过）；
③ 渲染距离外 / 相机背后 / 从未入镜的格永不证明空 → 永久保留（符合记忆语义）；
④ 单快照证据已足够，无需跨帧累积；agent 移动越多、进入判定范围的格越多；
⑤ 记忆侧离线 → 无 cells 文件 → 停止删除（恢复后自愈）；cells 相对深度**滞后一快照**（记忆先应用上一快照才上报现状）——滞后无害：应删未删的旧格下轮补删，已删/可见格判定为保留。

**`removalMaxRayDist` 详解（判定可靠距离上限）**：

- **两重作用**：① 记忆侧 `MemoryCellReporter` 用它做 **cells 文件的距离球过滤**——只上报 `|cell − agentPos| ≤ removalMaxRayDist` 的实心不透明块 + 冻结实体占用格（以 agentPos 为球心的球内格才进 `memory_cells.bin`；球外格永不进清单 → 永不判定、永不删）；② 采集侧 `DeletionJudge` 只对清单内格判定 → 它同时就是"能证明一个格已消失的最远距离"。
- **为何从 v2.22 的 768 降到 96**：v2.22 里它是**天空射线长度**（相机→`dir·maxRayDist`，证明整条到远处的路全空），768 = 渲染距离 12 chunk，判定方式是"射线逐格路过"，深度误差不构成威胁。v2.23 判定方式改变：逐块判定比较**两个欧氏距离**——深度还原的 `Z_opaque(p)`（该像素最近表面离相机距离）vs B 格远面 `t_far`，判据 `Z_opaque ≥ t_far − δ`；该比较依赖 δ 吞掉深度量化误差，而深度是 **1/z 非线性**存储、误差随距离急剧放大（§4.2/§10.5 已核实）：

| 距离 | float32 深度量化误差 |
|---|---|
| ~30 格 | ≈ 1mm |
| ~100 格 | ≈ 1.2cm |
| 300 格 | ≈ 11cm |
| 768 格 | ≈ **0.7 格** |

δ 默认 5cm（§5.4 同款）。≤~100 格内最坏误差 1.2cm ≪ δ，比较可靠；768 格处误差 0.7 格 ≫ δ，`Z_opaque ≥ t_far − δ` 会**双向失真**：
  - **该删没删**：量化让深度读得略小 → 判"没越过" → 保留（无害方向，宁欠勿过）；
  - **不该删删了**：量化让深度读得偏大、越过 `t_far − δ` → 把**真实还在的块**误判为消失（有害方向，记忆里永久失真）。
- **行为后果**：≤96 格可可靠判删；96~192 格（渲染距离内但超球）**保守保留**（哪怕现实确实没了也不删）；球随 agent 移动/转身，旧物进入球内被补判（agent 移动越充分、收敛越完整）；它是**可靠性上限而非"删除激进程度"**——调大是"判定更远"而非"删得更猛"，超出 δ 可靠区会引入误删风险。
- **一句话**：`removalMaxRayDist` = 判定"记忆格在现实中已消失"这一证明的可靠距离上限；默认 96 把深度量化误差（δ 仅 ≤~100 格内可被 5cm 容差覆盖）钉在可靠区内，宁可多留幽灵也不冒误删真实方块的风险。

**实施记录（v2.23，✅ 已实现 2026-08-25；v2.22 已实施内容按此删除）**：

- **采集侧（stevex-template）**：
  - 移除：`ObjectResolver.computeSkyRays`、`TerrainBlockSnapshot.surface` 落盘（`blockHits` 内部 nudged 点保留，§5.1 近侧回退仍用）；
  - 新增：`DeletionJudge`（读 cells 清单 → 逐块 bbox 投影 + 射线-AABB + `Z_opaque ≥ t_far − δ`，越过 ≥2 → deletions）；`VisionTerrainStore.sync` 增加 `List<BlockPos> deletions` 参数、顶层写 `deletions`。
- **记忆侧（stevex-test-template）**：
  - 删除：`RemovalVoter`（DDA 投票 + `evidenceFingerprint` + 跨帧 votes 全部移除）；
  - 新增：`MemoryCellReporter`（cells 集合 + 距离球过滤 + 原子写 `memory_cells.bin`）；`DeletionApplier`（解析 `deletions` → 818 置空 + clearStale + 冻结实体 discard + 相机格快路径）；
  - `TerrainData.appliedFingerprint` 纳入 `deletions`。
- **配置**：`MemoryConfig`：`removalEnabled`（默认 true）/ `removalPixelThreshold`（2）/ `removalMaxRayDist`（96.0）/ `memoryCellsWriteIntervalTicks`（10）。

### 7.12 半透明方块减量（水 + 满格透明）：translucent 深度在场判定（v2.36，✅ 已实现 2026-09-08；待游戏内验证，见 Phase 10）

> **实施记录（v2.36，2026-09-08）**：记忆侧 `BlockStateUtil` 新增 `isWaterFluid`/`isNonWaterFluid`/`isFullTransparentCell`（`isShapeFullBlock && !canOcclude`）/`isDeletableContent`；`MemoryConfig.removalTranslucentEnabled`（默认 true）。`MemoryCellReporter` cells 文件升 **version=3** 两段布局——opaque/main 段 = 实心不透明 ∪ 冻结实体占用格 ∪ **岩浆**（非水流体），translucent 段 = **水 ∪ 满格透明**；opaque 段之后追加 translucent 开关 byte + translucent count + longs。`DeletionApplier` deletion 主循环守卫放宽到 `isDeletableContent`（相机格快路径仍只删实心不透明）。采集侧 `MemoryCellsReader` 解析 version=3 translucent 段（version≤2 旧文件 → 空段，行为等同 v2.23）；`DeletionJudge` 抽出共享判据主循环、新增 `testTranslucent`（逐像素读 translucent 目标，防御性判空集）；`ObjectResolver` 按「Fabulous && hasTranslucentDepth && 开关 → translucent 场 / Fancy-Fast → 并入 main 场 / Fabulous &&（!hasT || !开关）→ 空集」路由 translucent 段，main 段恒走 main 场。**岩浆走 main/opaque 段而非 translucent 段**（Fabulous 下岩浆恒写 main，PBO 降级时仍可由 main 场判删，设计已知边界①）——实现定案为选项 A，下述原文中"流体（水/岩浆）"的统一表述据此修订。

> **背景（§7.11 边界① 的缺口）**：v2.23 减量只对"实心 + 不透明"格成立，判据依赖"存在即截断 main 深度、消失则 main 深度落到远面之后"。而 **Fabulous 下水/满格透明（玻璃块、染色玻璃、冰等写 translucent 渲染层的满格方块）画进 translucent 独立目标、不写 main 深度**（§3.1.1/§5.4）：水在时 main 深度记录的是池底/水后表面、不在时记录的仍是同一池底 → **main 深度场对这类对象不产生任何"在场/不在场"信号差** → 排干的水成幽灵。把它们硬塞给现有不透明判据更糟：Fabulous 下它们不挡 main 射线，活对象恒满足 `Z_opaque ≥ t_far`，**每帧都会被误判消失**。
>
> **修正方向（定案）**：把消失判据**从 main 深度场平移到 translucent 深度场**。translucent 目标每像素 = `copyDepthFrom(main)`（v2.24 已核实，§3.3/§5.4/§12）后被 TRANSLUCENT 组在前 **LEQUAL** 覆盖，语义是"**首个半透明面**"，恒 `translucentDepth ≤ mainDepth`；"无半透明在前"被自编码成主深度拷贝（`t == m`）而非独立空值。于是**同一条**逐像素比较（射线穿过格 C 的整格？）即可判 C 表层消失，且**镜像场天然保守、不会误删活水**。

**判定机制（translucent 场上的同构镜像；几何工具全复用 §5.4/§7.11）**：

- 输入：① 本快照 main + translucent 双深度 + 相机/投影（§3.3）；② 记忆侧上报的**translucent 段**清单 = **水 ∪ 满格透明方块**（cells 文件 version=3 新增；岩浆写 main、走 main/opaque 段，见下 reporter 改动）。
- 对每个 translucent 记忆格 C（水或满格透明；先跳过本次可见集 `currentTerrain`——可见格由 §5.1/§5.4 放置/更新路径处理，不参与减量）：
  1. 投影 C 的 8 角 → 屏幕 bbox（像素中心约定 §4.1，循环前裁剪到屏幕范围 §5.4）；
  2. 逐 bbox 像素 p：射线(camPos→p) 与 C 的 AABB 手写 slab 求交（§5.4）得 `t_entry`/`t_far`；读该像素 **translucent 目标**深度还原 `Z_translucent(p)`（欧氏距离，同度量；`t ≥ d_far`（=1.0 天空/远平面，v2.16）→ ∞）；**`Z_translucent(p) ≥ t_far − δ`（δ=0.05）→ 射线穿过了 C 的整格 → 越过计数++**；
  3. 越过计数 ≥ `removalPixelThreshold`（默认 2）→ C 表层消失 → 进 `deletions` 列表。
- **读的是 translucent 目标全体像素，不是只读 `t < m` 像素**：`t == m`（主拷贝）同样参与比较——它恰好是"无半透明在前"的编码。判定式与 §7.11 main 场唯一差异只在**哨兵**：不透明场"无表面 = 天空 1.0"，translucent 场"无半透明 = 主深度拷贝"。
- **为何单一数值判据天然不误删活水 / 不假删被遮挡物**：translucent 目标 = `min(主拷贝, 首个半透明面)`，三类情形已编码进同一个数值——

  | 情形 | translucent 值 t | `t ≥ t_far` 比较 |
  |---|---|---|
  | C 仍在（满形状格的自身面 / 更近半透明在前） | 满形状活半透明（水/玻璃块）必写自身面 < C 的 t_far | 不成立 → **保留**（不误删活水/活满格玻璃；非满形状薄物的空余像素见下） |
  | C 被舀干，背后露天空/坑底 | `t == m` == 天空(1.0)/远处墙 ≥ t_far | 成立 → **gone**（水鬼主场景） |
  | 不透明墙新挡在 C 前方 | `t == m ==` 墙距离 < t_far | 不成立 → **欠删保留**（墙占位交 §5.1 更新路径） |
  | C 表层消失、背后另有一片更远的水/玻璃 | `t < m` 但 t ≥ t_far | 成立 → **gone**（远处半透明是独立对象） |

  `copyDepthFrom(main)` 正是让镜像法免于"不透明遮挡被读成消失"假删除的关键——v2.24 为工序 B/C 引入的机制在此被第二次复用。

**表层所有权边界（半透明固有，与不透明判据"被挡欠删"同族）**：

- 逐格在场证明只对**拥有该像素首个半透明面**的格成立：水的顶层、独立玻璃块。**被淹没/被上层罩住的深层格不产生自己的 translucent 片元**（流体面剔除，§5.4 工序 C 区间推进同为"只枚举会写片元的可见格"）→ 整片还在时判保留；
- **整片水被完全舀干**：原边界像素集体转 `t==m ≥ t_far` → **整片记录的格一轮全删**（含深水柱中间格——它们表层消失由"无半透明在前"同时证明，无需逐格拥有片元）；
- **部分收缩（残留浅层水）**：残水表面在深层幽灵格 t_far 之前 → 深层欠删。但残水自身半透明、罩住下层视觉无害，且桶舀水的现实终态通常全干/留独立源——主场景（全干）被完整解决。欠删方向无害（宁欠勿过），接受；
- **满格透明方块（玻璃块/染色玻璃/冰等：`isShapeFullBlock` 且非 opaque）与水同判据，本版一并纳入范围**——它们填满整格，镜像法前提"在场 = 该格沿射线整段被自身近面终结"成立（活玻璃块每颗穿格像素都写自身近面 < t_far，与活水同理）。reporter 的 translucent 段谓词即"水 ∪ 满格透明"（岩浆恒写 main、归 main/opaque 段），判据/几何对二者零改动；applier 对"当前仍是流体（水/岩浆）或满格透明"的删除条目放行。
- **真正不能进本判据的是非满形状透明块（玻璃板/栅栏/铁栏杆/压力板/红石线/绊线等）**——不是"半透明物体做不到消失判断"，而是判据前提对它们不成立：薄物不填满 1×1×1 格，格内空余部分的像素在物体**活着**时射线就已从薄体旁穿格而过、直达背后/天空（`t ≥ t_far`）→ 一个活玻璃板单快照内就有远超 ≥2 像素投"消失"票 → **活体被误删**（有害方向，违"宁欠勿过"，比它要治的幽灵更糟）。这与不透明判据从一开始就把 opaque 限制在 `isShapeFullBlock` 是**同一原因**——限制项是"**非满形状**"而非"半透明"；薄栅栏/铁栏杆写 main、玻璃板/红石线写 translucent，换场不解决"空余像素"问题。要纳入它们的**旧路线**——把采样从"全格 bbox"换成"物体实际渲染形状 AABB"（cells 携带状态、采集侧重建 shape、只测薄板/柱的投影 + 轮廓与亚像素容差）——属次级工程；§7.13 给出更轻的替代：保留整格 bbox 判据、对越票像素追加「射线∩被记住形状」几何相交过滤（未立项、v2.36 不含）。其余透明方块维持 §7.11 边界①"永成幽灵（接受）"。岩浆见下（走 main 场，范围天然涵盖）。

**判据场选择（渲染配置 + 该格现实写哪张场决定，不得混用，与 §5.4 采集通道同构）**：

- **Fabulous && `hasTranslucentDepth()`**（translucent 目标在场）→ **translucent 段**（水/满格透明）对 **translucent 场**判定（`DeletionJudge.testTranslucent`，逐像素读 translucent 目标）——必要（Fabulous 下它们只写 translucent 目标、不写 main，只在这张场上可证消失）；**main/opaque 段**（实心不透明 ∪ 冻结实体占用格 ∪ 岩浆）仍走 main 场（`DeletionJudge.test`），两段判据并行、删格并入同一 deletions。
- **Fancy/Fast**（无独立 translucent 目标，水/岩浆/玻璃块都写 main）→ translucent 段并入 **现有 main 场判据**（§7.11 原样，直接对 translucent 格跑 `DeletionJudge.test`），`removalTranslucentEnabled` 不生效；判据代码零新增（只放宽 reporter 段谓词 + applier 守卫）。
- **Fabulous && !`hasTranslucentDepth()`**（第二路 PBO 软失败，§3.4 降级）→ translucent 场不可用：**任何写 translucent 目标的对象（水/满格透明）绝不可喂 main 场**（Fabulous 下它们不写 main、喂 main 场恒假消失，上文背景），该帧这类格的删除空集（幽灵暂留，优雅降级，PBO 恢复后自愈）；岩浆在 main/opaque 段（写 main）→ 仍可走 main 场判、不受影响。实现按该格现实的采集通道（§5.4 判定它写哪张场）路由。

**反向通道与两端改动（cells 文件 version=2 → version=3）**：

- **记忆侧 `MemoryCellReporter`**：cells 集合并为<b>两段</b>（version=3）——**opaque/main 段** = 现有"实心不透明块 + 冻结实体占用格" + **非水流体（岩浆）**；**translucent 段** = **水 + 满格透明**。对 `TerrainRestorer.appliedBlocks(dim)`（已应用方块表含 blockId）的每格按其记忆世界现状 `BlockState` 判定分段：水（`fluidState.is(WATER)`）→ translucent；非流体 → `isShapeFullBlock(state) && !state.canOcclude()`（玻璃块/染色玻璃/冰/遮光玻璃等满格透明）→ translucent；其余非水流体（岩浆）→ main（恒写 main、归 main 段——避免 Fabulous PBO 降级时被当写 translucent 对象而空判）。段谓词与 §5.4 采集侧"会写自身近面的整格"同口径，薄物/非满形状被挡在段外。同一距离球（≤ `removalMaxRayDist`）过滤。文件布局升 **version=3**：version=2 头（dimension 段）之后 opaque/main 段 long 数组之后追加 **translucent 开关 byte + translucent 段 count + translucent long 数组**，两段并列、各带独立 count；**version≤2 旧文件 → translucent 段空** → 采集侧只按主路径删（岩浆/非 Fabulous 水），行为等同 v2.23，无迁移负担。
  **为何逐格上报而非整水体/整玻璃**：纯累积记忆只知"自己放过哪些格"、不知对象边界；上报全部 translucent 段格后由采集侧逐格在场判定天然收敛（活格保留、消失的删），语义与不透明格完全对称。
- **采集侧 `ObjectResolver`**：现有 `DeletionJudge.test`（main 场）管 **main/opaque 段**（实心不透明格 + 冻结实体占用格 + 岩浆）照旧；对 **translucent 段**按当前图形配置路由（上文判据场选择）：`fabulous && hasTranslucentDepth() && cells.translucentEnabled()` → `DeletionJudge.testTranslucent`（translucent 场判定，bbox/slab/δ/阈值全复用，逐像素比较对象换成 translucent 目标）；非 Fabulous（Fancy/Fast，水/满格透明写 main）→ `DeletionJudge.test`（并入 main 场）；Fabulous &&（!`hasTranslucentDepth()` || !`translucentEnabled`）→ translucent 段判据空集（水/满格透明绝不可喂 main 场——恒假消失；PBO 恢复 / 开关重开自愈）。两段删格并入同一 `deletions` 列表（随 terrain.nbt 顶层落盘），记忆侧无需区分来源。判定与 §5.4 采集同度量（同 δ = 0.05）。
- **记忆侧 `DeletionApplier`**：对 deletion 格**按内容放行**——deletion 主循环守卫从 `isSolidOpaque` 放宽到 `isDeletableContent`（= 实心不透明 ∪ 流体（水/岩浆）∪ 满格透明：`isShapeFullBlock && !canOcclude`），只有记忆世界当前内容属于可删集才执行 818 置空（translucent 段条目与 main 段岩浆条目同规则；被删满格透明格即从记忆世界清除）；**不得**因此对非满形状/段外方块放行（玻璃板/栅栏/压力板/红石线的拒删保持 §7.11 边界①——它们本就进不了段，双保险防误删）。currentTerrain 可见格跳过、相机格快路径（仍只删实心不透明，v2.22 语义）、幂等性同 §7.11。
- **配置**：复用 `removalEnabled`/`removalPixelThreshold`/`removalMaxRayDist`；新增 `removalTranslucentEnabled`（默认 true，仅 Fabulous 生效）可选开关——随 cells 文件 version=3 头部下发（translucent 开关 byte），Fabulous && `hasTranslucentDepth()` 时采集侧据此路由 translucent 段（false → 判据空集、幽灵暂留）；Fancy/Fast 下不生效（水/满格透明写 main、本就由 main 场判据覆盖）。

**已知边界（v2.36）**：

① translucent 场判据与渲染配置/第二路 PBO 强耦合：非 Fabulous 或 PBO 软失败时写 translucent 目标的对象（水/满格透明）消失判定空集（岩浆写 main、走 main 场不受影响）；② 表层所有权——部分收缩残留浅层水时深层幽灵欠删（残水自罩、视觉无害）；**满格透明方块（玻璃块/冰）本版已纳入**；非满形状透明块（玻璃板/栅栏/压力板等）需几何过滤才可判（§7.13 轻量草案，未立项）——本版仍未纳入、欠删（接受）；③ 深度量化可靠距离仍 ≤ `removalMaxRayDist`=96（δ 仅 ≤~100 格内可靠，§7.11 `removalMaxRayDist` 详解——流体的 t_far 与 main 场同为欧氏距离比较，误差界不变）；④ 记忆侧离线 → 无 cells → 不删（同 §7.11，优雅降级自愈）；⑤ 会话间切换图形配置（Fabulous ↔ Fancy）造成的跨配置陈旧细胞走**当前**配置的场判定，方向保守（欠删不误删）。

**验证清单（待游戏内，Phase 10）**：

- 桶舀水源致整片水消失 → **单次快照内**整片记录的水格（含深水柱中间格）判删、记忆世界水消失；
- 敲掉/移动独立玻璃块、染色玻璃、冰 → 判删、记忆世界清除；活玻璃块不被误删；被更近墙遮挡的活玻璃欠删保留；
- 活水池塘/海洋/深湖不被误删（逐像素 `t < t_far`）；水面嵌套/水下玻璃不连带误删；
- 部分排水（留浅层）→ 深层幽灵欠删但视觉无害；岩浆被舀/流走 → main 场判据删除；
- Fancy/Fast：水/玻璃块写 main → 放宽 reporter/applier 后走现有判据删除；Fabulous PBO 软失败 → 本帧水/玻璃块跳过、不误删、恢复后自愈；
- 玻璃板/栅栏/压力板等非满形状透明块仍不删（防误删回归）。

---

### 7.13 非满形状方块减量（几何过滤草案：整格 bbox 预判 + 形状相交过滤，未立项，未来方向）

> **状态**：v2.36（§7.12）把减量范围扩到流体 ∪ 满格透明，**非满形状方块（写 main：栅栏/铁栏杆/门/台阶/活板门/梯子/火把/压力板…；Fabulous 下写 translucent：玻璃板/红石线…）仍整类欠删**，§7.12 末将其归为"需换成实际渲染形状 AABB 采样、属次级工程"。本节记录经论证的**轻量替代**：不必换采样体积——**保留整格 bbox 判据原样，只对"越票"像素追加一个「射线 ∩ 被记住形状」的几何相交过滤**。方向已与用户讨论定稿，**未立项、未实现**（v2.36 之后候选；代码另起须先经本节审阅）。

**根因（为何非满形状不能直接套整格判据）**：整格 bbox 判据的假证据只来自一种像素——**穿过格内空余区域**的射线。非满形状的"在/不在"信号差只存在于**几何区**（薄体实际占的那部分格）；空余区在物体**活着**时射线就不被终止（直达背后/天空，`Z ≥ t_far`），与"物体已消失"在外观上无差别 → 全部计入会让一个活玻璃板单快照内就有远超 ≥2 像素越票 → 误删活体。§7.11/§7.12 因此把谓词钉在 `isShapeFullBlock`——**限制项是"形状是否填满整格"，与透明度、写哪张场无关**。

**两步法（第一步与现判据零改动）**：

1. 对记忆格 C，沿用 §7.11/§7.12 同款整格判定：8 角投影 → 屏幕 bbox → 逐像素射线-AABB slab + 读 C 实际写的场（§5.4 路由）`Z ≥ t_far − δ` → 越票。**这一层的判据、哨兵、几何一律不改**；
2. 对每张越票 p 追加**形状相交过滤**：用 C 的**被记住 BlockState** 重建实际 VoxelShape（cells 携带状态），判「射线(camPos→p) ∩ 形状 ≠ ∅」。不相交（该射线只穿格内空余区，活着时本就不被终止、无作证价值）→ **作废该票**；相交 → 保留为真消失证据。越过数仍 ≥ `removalPixelThreshold` 才进 deletions。

**为何是 sound（只消原假证据源、不引入新假删）**：

| 像素情形 | 整格预判（Z ≥ t_far − δ 越票） | 形状相交过滤 | 结果 |
|---|---|---|---|
| 活体、射线过空余区 | 越票（假象） | 不相交 → 作废 | ✓ 消除唯一假证据源 |
| 活体、射线过几何区 | 不越票（近面深度 < t_far） | （本就不进过滤） | ✓ 活体安全 |
| 已消失、射线过几何区 | 越票 | 相交 → 保留 | ✓ 真证据 |
| 已消失、射线过空余区 | 越票 | 不相交 → 作废 | ✓ 剔除无意义像素 |

**关键推论**：活体穿过几何区的像素在**第一步就被自己的近面深度排除**（近面 < t_far，进不了越票集），过滤阶段见到的"相交越票"只可能是"几何已消失、露出背景"。因此过滤器**只需一个相交谓词**，无需对真实形状做逐像素 t_near/t_far 簿记、也无需改预判参照距离（仍是整格远面 + δ）——这就是它比"采样体积换成实际渲染形状 AABB"轻的地方。**满格方块是本法的退化情形**（几何 = 整格 bbox → 过滤恒真 → 与 §7.11/§7.12 现判据完全一致），可统一取代而非另开一套并行判据。

**实现必要条件**：

- **cells 携带 BlockState（文件再升级）**：`MemoryCellReporter` 把"仍在记忆世界的非满形状格"（opaque ∪ 非满透明）按记忆现状状态序列化——连接形态已编码进状态属性（栅栏/墙/红石线的 `north/east/south/west/up`、门/活板门朝向/开合等），重建形状忠实；采集侧 `DeletionJudge` 用该状态重建 VoxelShape。多数非满方块 VoxelShape ≈ 渲染形状；渲染比碰撞更精细处（红石线等）以"重建形状近似 + 亚像素容差"处理。加入 opaque / translucent 段还是新增"几何段"，未来定夺（不影响判据本体）。
- **场选择沿用 §7.12 判据场选择 + §5.4 路由，不得混用**：不透明非满 / CUTOUT（栅栏/铁栏杆/门/梯子…）写 main → main 场判定（任意配置）；半透明非满（Fabulous 下的玻璃板/红石线…）写 translucent → **仅 Fabulous && `hasTranslucentDepth()` 时可判**，Fancy/Fast 下它们写 main → 走 main 场。写 translucent 的对象绝不可喂 main 场（Fabulous 下恒假消失，同 §7.12）。
- **轮廓/亚像素/斜掠是唯一残余脆弱点（工程而非逻辑）**：光栅化中"像素中心射线命中形状"与"该像素存在该形状 fragment"在贴边像素可能不一致。方向可控、**宁丢勿错**——对贴边/斜掠的越票（射线在形状内弦长过短，如 < 数个 δ）降权或作废（grazing 命中本只在轮廓边缘产生 fragment），配合既有 ≥2 多票阈值全消误删。§7.12 已提及的"投影 + 轮廓与亚像素容差"即此。
- **applier 语义转折**：格由 judge 经几何过滤证明消失后，applier 的"非满形状拒删"双保险须**放开**——改为与 §7.11/§7.12 同构的"记忆世界当前该格仍是同一几何格（内容对得上）才 818"（内容不符 → 跳过、交放置/更新路径）。这是把"整类拉黑"升级为"诚实几何判定"的语义前提，也是本方案唯一的"放宽"点——由几何过滤承担防误删职责。

**仍修不了的部分（与几何过滤正交，保持原边界）**：

- **Fabulous 绊线**：活着时不写任何已读深度场（§10.8），几何过滤反而更糟（重建出形状、测到的是背后表面 → 恒判消失）。它是"信号缺失"类而非形状类——须另读 weather 深度场，或仅 Fancy/Fast 可删；
- **形状为空的方块**（下界传送门方块）：无体积可交，删不了（可接受）；
- 被永久遮挡后移除、距离 > `removalMaxRayDist`=96（深度量化超 δ 可靠区）、占屏 <2px（薄物最易触达）→ 依旧欠删不误删（宁欠勿过）；
- **半满形状 BE 方块（箱/床/旗…）被整体移除**：格本身可判删后，还须配套"格被判删 → 清该格 BE 内容记忆"的路径（BE 现只在原位被无 BE 新方块替换时 `clearStale`，见 §7.2/§7.3）。

**代价**：只作用于距离球内候选格。对活体，越票仅来自空余区像素（bbox 投影的一小部分），过滤把其全部作废 → 恒 0 票；对消失格，过滤只是形状相交判定。量级 O(候选格 × bbox 像素 × 形状相交)，与 §7.11 现判据同阶。

**建议验证（若立项，并入对应 Phase）**：敲掉独立玻璃板/铁栏杆/红石线 → 判删、记忆世界清除；活玻璃板/栅栏（含靠墙、部分遮挡）不被误删；斜视角薄板（真实形状重建）不漏判；Fabulous 下红石线随 translucent 场删、Fancy/Fast 走 main 场删；远距薄物优雅欠删；Fabulous 绊线仍不删（回归预期）。

---

## 八、线程模型

| 线程 | 职责 |
|---|---|
| **API 线程**（WebSocket） | `requestCapture()` 置标志 → `awaitSnapshot(超时)` 取深度快照（含 TAIL 捕获的实体 AABB 列表）→ **读记忆侧 `memory_cells.bin`（mtime 门控，v2.23 反向通道）** → 用捕获的 AABB 建 **SectionPos 桶** → **`Unprojector.visibleBlockHits`**（纯数学，扫描同时产出方块去重点 + 实体候选像素原始点 W）→ 经 `Minecraft.execute` 交集合给渲染线程查询 → 组装 JSON 响应 |
| **渲染线程** | `tryCapture`（Mixin TAIL）：手写 GL PBO 回读深度 + 快照矩阵/相机；**快照实际被渲染实体列表**（**第二注入点 `extractVisibleEntities` @TAIL** 复刻 L821-826 裁剪谓词，v2.9 定稿；`LevelRenderState.entityRenderStates` 不可用——DTO 缺 AABB/id 且 renderLevel TAIL 前已 reset；`entitiesForRendering()` 原样全收会假阳性）→ `queueFencedTask` 回调置快照并 countDown → **`ObjectResolver.resolve` 四路查询**（方块直查 / 方块实体 NBT / 实体闭区间 contains + 深度排序 + 肢体判别精判 / 半透明·绊线 §5.4——**仅 Fabulous，v2.10/v2.12**）→ **`DeletionJudge` 逐块判定（v2.23：cells → 逐块投影 + 深度判定 → deletions；**v2.36**：`fabulous && hasTranslucentDepth()` 时对 cells.translucent 段（流体 ∪ 满格透明）追加 translucent 场判定）** → NBT 序列化 → 三 store 落盘 |
| **回读回调** | `queueFencedTask` 回调（渲染线程触发），只拷贝深度数组 + 置快照 + countDown，不碰游戏数据 |

- **反投影 / 去重可在 API 线程**：纯矩阵乘 + 哈希去重，快照数据已与游戏解耦，无竞态；
- **四路查询的核心仍在渲染线程**：`ClientLevel.getBlockState`（方块直查）与 NBT 序列化从非渲染线程读取有竞态；实体 AABB 在 TAIL 捕获进快照后即与游戏解耦，**桶构建与纯几何判据（contains / 射线-AABB / 深度比较）可在 API 线程**；**v2.11 肢体判别子步（他体排除 + 薄方块排除 + 前向扫，读 blockstate）须在渲染线程**（ObjectResolver 内，候选像素经桶过滤后数量小）；
- 与 v1 一致：渲染线程的查询/序列化是帧间执行的 CPU 密集 burst（采集时），代价可控。

**具体接线（v2.2 定稿，沿用 `VisionApi.entityQuery` 的 latch 模式）**：

```
API 线程: requestCapture → awaitSnapshot(5000)（null → 超时错误）
         → 用快照内实体 AABB 建 SectionPos 桶（纯数据）
         → 读记忆侧 memory_cells.bin（mtime 门控，v2.23 反向通道）→ 内存格清单
         → new Unprojector(snap) → visibleBlockHits(d_far, 0.05)   // d_far=1.0（远平面极限深度，v2.6）
              // 纯数学 10-20ms；扫描产出 ① 方块去重点（nudged）+ ③ 实体候选像素（原始点 W）
         → Minecraft.execute {                                       // 渲染线程
              ObjectResolver.resolve(unproj, hits, entityCandidates, bucket, level, ts)
                // ① 方块直查  ② 方块实体 NBT  ③ 实体 contains + 深度排序 + 肢体判别
              DeletionJudge.test(memoryCells, depth, camera, proj, currentTerrain)  // v2.23 → deletions
              → 三 store sync（terrain 含 deletions / blockEntities / entities） }
         → await latch(5s) → 组装 JSON
```

- `hits`（`Long2ObjectOpenHashMap<Vec3>`）经 execute 闭包 + latch 安全发布；
- `Unprojector` / JOML 对象单线程使用（API 线程用完交渲染线程，无并发写）；
- store 落盘在渲染线程内完成（一次性 burst，API 触发，非每帧）；
- **配置分支下发（v2.10/v2.12）**：`tryCapture` 时读取 `Minecraft.getInstance().useShaderTransparency()` 随快照携带；`ObjectResolver.resolve` 据此决定是否执行 §5.4 第四通道（`false` 时跳过、半透明/绊线并入 §5.1 主路径）。
- **同帧握手（v2.11）**：`requestCapture` 置位后，帧内第一个注入点（`extractVisibleEntities` @TAIL）消费标志并采实体快照；`renderLevel` TAIL 捕获前校验"本帧已采实体快照"，未采集则放弃本帧、保留标志推迟到下一帧——深度图与实体快照永远同帧（§3.2）。
- **解析偏斜容差（v2.11）**：`Minecraft.execute` 使 resolve 实际运行在捕获帧之后 1~2 帧，其读取的 world 状态允许 1~2 tick 陈旧（§3.2"数据一致性"）；如需严格对齐，把 resolve 排队进捕获同帧。

---

## 九、性能分析

| 环节 | 成本 | 说明 |
|---|---|---|
| 深度回读 | 手写 GL PBO 异步回读（854×480 ≈ 1.6MB），**每 snapshot 一次**，非每帧 | GPU 异步填充，CPU 不等待（fence 轮询，不卡帧） |
| 反投影 | O(像素)：全量 854×480=41 万次矩阵乘 + 去重 | 约 40 ops/像素，全量 ≈ 10-20ms；不做降采样（见 §4.3），可在 API 线程执行 |
| 方块查询 | O(唯一方块数)：去重后几千个，每块一次区块读 | 远小于 v1 的 O(4096×N)（几十万次） |
| 方块实体 NBT | O(可见方块实体数)：只对可见的序列化 | 比 v1（section 内全部）更省 |
| 实体判定 | O(实体附近像素 × 桶内实体数)：SectionPos 桶粗过滤 + `AABB.contains` + 射线-AABB + 肢体判别（v2.11，他体/薄方块排除 + 前向扫） | 绝大多数像素 O(1) 桶 miss 跳过，可忽略 |
| 半透明/绊线通道（§5.4，仅 Fabulous） | **v2.26 区间射线推进**：O(Σ 区间像素 × 射线穿过的透明格数)——仅 `translucentDepth < mainDepth` 像素推进，跨 section 用 `maybeHas(透明)` 整节跳步（非透明节 O(1)）；**总量 = 答案体积**，无候选粗筛、无 8px 合并 | 单层水每像素 ≈1 格、深湖 ≈ 水深；满屏深湖最坏几十 ms（Phase 9 实测）；**v2.26 删除** v2.11 粗筛（10⁵~10⁶ 候选 8 角投影 + 8px 栅格合并）与 v2.24 首层判别；工序 B（v2.24）首层透明面深度 pass = O(全像素) 单趟反投影仍承担首层精确落位；**v2.12**：绊线随推进枚举、开销并入透明格数，可忽略 |
| 减量判定（v2.23，采集侧 `DeletionJudge`） | O(视锥内记忆块数 × 投影像素)：几千~几万格 × 几~几十 px ≈ 几十万次深度读 + §5.4 式射线-AABB，每快照一次、毫秒级 | 取代 v2.22 记忆侧 DDA 投票（跨帧 K≥2 + 证据门控已删）；**单快照内 ≥2 像素越过即判删**，无跨帧依赖、静态玩家一次快照即可删 |
| 半透明减量（v2.36，流体 + 满格透明，translucent 场判定） | 附加 O(视锥内 translucent 格数 × 投影像素)——translucent 深度与 main 同帧两路 PBO 已读回（§3.4），无新增读回；仅 Fabulous 且 `hasTranslucentDepth()` 时执行 | 判定式/δ/阈值与 §7.11 main 场同款，比较对象换成 translucent 目标（"首个半透明面 ∪ 主拷贝"）；PBO 软失败 → 该帧水/玻璃块跳过、不误删；岩浆/非 Fabulous 水走 main 场无此开销 |
| 反向通道（v2.23，v2.36 cells 文件） | 记忆侧：O(累积块数) 距离过滤 + 原子写（写时，ms）；采集侧：mtime stat + 读（几十 KB，version=3 增流体段） | 记忆侧离线 → 无 cells 文件 → 无删除证据，优雅降级 |
| **对比 v1** | 地形遍历从"几十万次 getBlockState"降到"几万次反投影 + 几千次查询" | **更准（只可见）且更省**；代价是新增深度管道 |

---

## 十、已知限制与决策

1. **覆盖层语义（几何可见性）**：深度缓冲反映**世界几何遮挡**，不含手持物品、GUI、屏幕特效、粒子、云。这些是画面上的覆盖层——对"世界里能看到什么"的语义**应故意忽略**。雾是可配置项：若需要"雾后不算可见"，按距离对反投影点做雾强度裁剪（默认不做）。
2. **深度语义（v2.10 配置分支，见 §3.1.1）**：TRANSLUCENT 层（水/玻璃/染色玻璃/冰/黏液块等）与 TRIPWIRE 层（绊线，v2.12）的深度行为取决于 `useShaderTransparency()`——**Fabulous 下不写 main 深度**（深度看穿"透过水/玻璃看到后面的方块"，半透明/绊线方块经 §5.4 获取，v2.12 谓词已含 TRIPWIRE）；**默认 Fancy/Fast 下写 main 深度**（半透明/绊线是"最近表面"、其后物体被遮挡，§5.1 直查，§5.4 跳过）。**岩浆例外**——流体映射未列岩浆 → SOLID 层 → **恒写深度**，深度上岩浆始终不透光；铁栅栏/叶子等 CUTOUT 只在 alpha≥0.5 处写深度（缝隙看穿）。**v2.24（两深度锚点）**：Fabulous 下 translucent 独立目标自身持有第二份深度（首个半透明面），与 main 深度合并可同时得到"水面位置 + 水底物体位置"，逐像素给出"水面→水底"完整可见范围（见 §5.4）。**v2.26**：水柱中间透明格经区间射线推进逐格枚举，深湖/海洋可完整复现水柱（见 §5.4）。
3. **亚像素 / 透明目标漏判**：占屏 <1px 的远处小实体、发光/透明实体不写深度 → 漏（v2.4 正向查询下薄/扁/部分被挡已覆盖，仅剩深度物理极限）；半透明/绊线方块占屏 <1px 或射线恰好从格间隙/细线旁穿过同样漏（§5.4；**v2.12**：绊线极薄，远距更易触发）。对"找真正看到的"语义可接受。
4. **版本耦合**：捕获依赖 `LevelRenderer.renderLevel` 方法签名（TAIL 注入）+ `GlStateManager` GL 原语 + `queueFencedTask` 异步机制，锁 1.21.11；MC 升级需重写捕获层（数据层不受影响）。
5. **深度量化**：远距离反投影误差靠 epsilon 沿射线推 + **近侧回退**处理（见 §4.2/§5.1），有极小概率误判相邻方块（float32 精度下 30 格 ≈ 1mm、100 格 ≈ 1.2cm；**v2.11 更正**：>100 格后随 1/z 急剧变粗——300 格 ≈ 11cm、768 格 ≈ 0.7 格，此时 ε 不再覆盖、依赖近侧回退兜底；近中距仍远超方块粒度，实际无需担心）。
6. **只报可见**：agent 失去"附近有什么"的全景（墙后实体不再上报）——这是刻意选择。
7. **药水 / 光照 / 雾与深度正交**：失明（`BlindnessFogEnvironment` 雾）、黑暗（`LightTexture` 光图 gamma）、抗火药水（`LavaFogEnvironment` 雾距离）、环境亮度（lightmap）全部是**颜色域**效果，作用于 fragment shader 的 fog/color，**从不碰深度**——深度图不受它们任何影响（源码依据见 §3.1.1）。
8. **绊线采集与剩余限制（v2.9，v2.10/v2.12/v2.26 修订）**：绊线（`minecraft:tripwire`，TRIPWIRE 层；**1.21.11 无独立 string 方块**）在 **Fabulous** 下画进 weather 目标、不写 main 深度，但 **v2.12 起可采集、v2.26 随区间射线推进逐格枚举**——与半透明同构（main 深度看穿、记录背后表面），透明格判定含 TRIPWIRE 层、推进中逐格复用统一判定式 `Z_opaque ≥ t_entry − δ`，**无需读 weather 目标**（weather 目标混雨雪、需第二路 PBO + 新反投影路径，且违背 §10.1 覆盖层语义）。**默认 Fancy/Fast 下**回退 main target 且写深度 → §5.1 直接拾取。**剩余限制**：绊线极薄，远距/占屏 <1px 时射线从细线旁掠过 → 漏（同 §10.3，近距可靠）。
9. **透明掉落物 / 物品 glint 深度不可见（v2.9，v2.25 修订）**：玻璃/药水类掉落物与物品 glint 画进独立 item_entity 目标、深度不回写 main——**v2.25 起掉落物实体本体经工序 D（§5.3.1）补采**（Fabulous 下复用 §5.4 统一判定式判定可见性，无需读 item_entity 目标）；**物品 glint 光效层本身仍无深度**——但它附着于物品本体（不透明本体写深度、半透明本体由工序 D），不影响物品采集，深度物理极限降级为"仅光效层不可见"，接受。Fancy/Fast 下无独立 item_entity 目标，掉落物回退 main 写深度、本就可见。
10. **实体盒覆盖缺口（v2.9/v2.10）**：marker 盔甲架（0×0 盒但模型照画）、大堆物品平片 Z 向摊开（~3 格）、带 display block 的矿车纵向延伸——这些像素归属不到 → 漏检。**v2.10 已统一 `inflate(0.5)` 对齐 vanilla 视锥盒**（此前 0.45 的 0.05 壳已消解）；剩余缺口仅 `getBoundingBoxForCulling` 覆盖更大的实体（Sniffer 0.6 / Illusioner 更大，见 §5.3/§12）。**v2.11 补防**：marker 盔甲架 0×0 盒参与 §5.3 **未外扩盒**的 slab 求交属零体积退化——slab 求交对零/负外扩盒**直接返回 null（不判相交）**，防除零/NaN，该实体留给 contains/侧向判定兜底。
11. **F3 调试 gizmo（v2.9）**：`late_debug` pass 在 TAIL 前把主深度清成 1.0 → 捕获到全天空假深度，须检测跳过该帧。
12. **resize / 全景截图（v2.9）**：窗口 resize 重建深度纹理、全景截图临时 resize 4096×4096 → 缓冲须动态分配、每帧重取句柄。
13. **减量边界（v2.23，v2.36 修订流体 + 满格透明部分，详见 §7.11/§7.12）**：只删"实心 + 不透明"格 + v2.36 的流体/满格透明格（cells version≤2 文件只含前者 + 冻结实体占用格）——被移除的**非满形状**透明/半实心方块（玻璃板/栅栏/压力板）仍永成幽灵（接受；未来方向：几何过滤，见 §7.13 设计草案）；排干的水/岩浆及满格透明减量见 **v2.36（§7.12，✅ 已实现 2026-09-08）**——Fabulous 下 translucent 场同构判据、Fancy/Fast 与岩浆走 main 场（放宽 reporter/applier 门槛：translucent 段 = 水 ∪ 满格透明、岩浆归 main 段）；`removalMaxRayDist=96` 外的格保守保留（深度量化误差超出 δ 可靠区，§4.2/§10.5）；记忆世界离线时无删除证据（优雅降级，恢复后自愈）。
14. **两深度锚点边界（v2.24，v2.26 修订，见 §5.4）**：工序 B 首层透明面深度 pass 仅在 Fabulous（translucent 目标非空）可用——Fancy/Fast 无独立目标，回退 §5.1 最近表面语义（水面掩盖水底）；嵌套半透明的**精确面**深度给不出——**v2.26 起由工序 C 区间射线推进逐格归属、方块级粒度（水柱中间水格全部枚举）**；绊线（weather 目标）与掉落物（item_entity 目标）不在 translucent 深度内——绊线恒走工序 C 区间推进、掉落物走实体通道（§5.3）；透明/不透明共面边界靠 δ 容差兜底。
15. **半透明方块减量边界（v2.36，流体 + 满格透明，见 §7.12）**：translucent 场判据只在 Fabulous 且第二路 PBO 成功（`hasTranslucentDepth`）时存在——非 Fabulous 或 PBO 软失败时水/玻璃块的消失判定空集（岩浆写 main 不受影响，仍可删）；表层所有权限制——部分收缩残留浅层水时深层幽灵欠删（残水自罩、视觉无害），玻璃板/栅栏等**非满形状**透明块仍不删（进不了 translucent 段，防误删回归，接受）；判据与采集同度量、深度量化可靠距离上限 `removalMaxRayDist`=96 不变。

---

## 十一、实施路径

### Phase 1：深度捕获（✅ 已完成）
- [x] Mixin 钩 `LevelRenderer.renderLevel` TAIL，验证捕获窗口内深度内容正确
- [x] 手写 GL PBO 深度回读（GL_DEPTH_ATTACHMENT + glReadPixels + `queueFencedTask`），替代 color-only 的 `copyTextureToBuffer`
- [x] `captureRequested` 门控 + latch（API 置位 → 下一帧捕获 → 回调 → `awaitSnapshot`）
- [x] 端到端验证：`vision/snapshot` 返回 `{"ok":true,"width":854,"height":480,"depthMin":0.9749755,"depthMax":1,...}`

### Phase 2：反投影 + 去重（✅ 已实现）
- [x] `Unprojector`：像素 → NDC → inverse(P×V) → **相机相对坐标 → +camPos** → 世界坐标（**全量**，不做像素降采样，见 §4.3）
- [x] 跳过天空像素（`d ≥ d_far`，=1.0，v2.6 远平面极限深度）；沿射线推 ε（0.05）→ `BlockPos.asLong` 去重（`Long2ObjectOpenHashMap<Long,Vec3>`，**存 nudged 点**）；同步按 SectionPos 桶筛出实体候选像素（**存原始点 W**，供 §5.3）
- [x] 精度处理：`clip.w ≤ 0` 丢弃 + air 近侧回退（见 §4.2/§5.1）

### Phase 3：四路查询 + API 改造（✅ 已实现）
- [x] 方块直查（`getBlockState` + air 近侧回退；复用 `blockId`/`stateProps`）
- [x] 方块实体直查 + `saveWithFullMetadata(registryAccess)` + typeId
- [x] 实体**正向像素归属**：TAIL 捕获实体 AABB 列表 → SectionPos 桶粗过滤 + `AABB.contains` + 深度排序 + **肢体判别**（v2.11：非他体 + 非薄方块排除 + 前向空扫，产出可见 uuid）
- [x] **半透明/绊线方块（v2.7/v2.10/v2.12，§5.4）**：按 `useShaderTransparency()` 分支——Fabulous：编译可见 Section 粗筛候选（palette 驱动，`ChunkSectionLayer.TRANSLUCENT ∪ TRIPWIRE` 判定）+ **实际渲染形状 AABB** + **bbox 屏幕裁剪** + 射线-AABB/深度比较 → 合并进 terrain 通道；Fancy/Fast：**跳过 §5.4**，半透明/绊线并入 §5.1 主路径（**v2.26 修订**：粗筛候选路径被区间射线推进取代，见 Phase 9）
- [x] `ObjectResolver.resolve`（方块直查 / 方块实体 NBT / 实体正向匹配 / 半透明·绊线四路）+ 三 store 接线（terrain/entity 删 `scannedSections` 字段）；`VisionApi.snapshot` 输出可见对象

### Phase 4：记忆世界语义改造（✅ 已完成 2026-08-06，stevex-test-template）
- [x] `TerrainRestorer` / `EntityRestorer` 删除移除权威，改纯累积（不再 setBlock 空气 / 不再 discard；`scannedSections` 读取与指纹一并移除）
- [x] 方块实体 ghosting 防护（v2.10）：`TerrainRestorer` 方块类型改变且新方块无 BE 时主动清除旧 BE（`MemoryRestorer.clearStale`）；`MemoryRestorer` 加世界权威校验（当前世界方块与 BE 记录方块不一致 → 跳过放置，自愈重启/文件残留）
- [x] 更新触发（v2.13）：三个 restorer 的 `tick()` 改 **mtime 门控**——每 tick 仅 stat 源文件 mtime，mtime 未变不读不解压；mtime 仅在读取成功后推进（写中途读到 null 保留旧值下轮重试）；`MemoryConfig.pollIntervalTicks` 默认 20 → 1（§7.4）
- [ ] 验证：转身后旧场景保留、新场景追加、被挖方块保留
- （store 侧 `scannedSections` 字段删除已并入 Phase 3；restorer 读到空集合自动纯累积）

### Phase 5：验证（1 天）（⏳ 未执行，待验证）
- [ ] 站在墙后：墙后方块/实体不出现在响应
- [ ] 透过水/玻璃：**Fabulous 下**能看到后面的方块（深度看穿）；**Fancy/Fast 下**半透明为最近表面、其后物体不可见（§5.4 跳过，§5.1 直查半透明）——**两种配置各验证一遍**
- [ ] 绊线（v2.12）：Fabulous 下经 §5.4 区间推进（v2.26，TRIPWIRE 层随推进枚举）近距可拾取、贴墙/贴天空可见、被遮挡不可见；Fancy/Fast 下经 §5.1 拾取
- [ ] 视野内外切换：可见集正确增减
- [ ] 快速实体（疾跑/末影龙）：插值 AABB（v2.10）匹配无偏移漏检
- [ ] 性能：snapshot 耗时、帧率影响

### Phase 6：减量·反向通道 + 采集侧逐块判定（v2.23，✅ 已实现 2026-08-25）
> v2.22 的"表面点+天空射线→记忆侧 DDA 投票"因稀疏采样盲区（静态玩家票数卡死 <K）与证据门控矛盾被重构为 v2.23：深度图留在采集侧，记忆侧反向上报现有格，采集侧逐块判定（§7.11）。
- [x] 记忆侧 `MemoryCellReporter`：维护"实心不透明块 + 冻结实体占用格"集合，按 agentPose 距离球过滤（≤ `removalMaxRayDist`），内容指纹 + 原子写（临时文件+rename）+ mtime 成功后推进 + 10 tick 兜底 → `memory_cells.bin`
- [x] 采集侧 `DeletionJudge`：快照时读 cells 文件（mtime 门控）→ 逐块投影 8 角 bbox（剪裁）→ 逐像素射线-AABB + `Z_opaque ≥ t_far − δ`，**越过计数 ≥2 → 判消失** → `deletions` 写 terrain.nbt 顶层（`VisionTerrainStore.sync` 加参数）
- [x] 记忆侧 `DeletionApplier`：解析 `deletions`（`appliedFingerprint` 纳入）→ `setBlock(pos, air, 818)` + `MemoryRestorer.clearStale(pos)` + 冻结实体占用格全在 deletions 内才 `discard` + 相机格快路径
- [x] 删除 v2.22 实现：`computeSkyRays`、`TerrainBlockSnapshot.surface` 落盘、记忆侧 `RemovalVoter`（DDA + evidenceFingerprint + 跨帧 votes）
- [ ] 验证：被挖掉方块（露天空 / 背后平墙 / 小块远块）在**单次快照**内消失；可见方块 / 被遮挡方块 / 被替换方块不受误删；记忆世界离线时采集侧优雅降级；`removalMaxRayDist=96` 外保守保留

### Phase 7：两深度锚点——首层透明面深度 pass + §5.4 残留瘦身（v2.24，✅ 已实现 2026-08-27）
> v2.24 设计定稿（2026-08-26）：Fabulous 下同时读回 translucent 目标深度，与 main 深度组成"首个可见面 / 首个不透明面"双锚点，逐像素产出"水面→水底"完整可见范围（§5.4）。
- [x] `DepthCapture`：第二路 PBO 读回 `levelRenderer.getTranslucentTarget().getDepthTexture()`（仅 Fabulous 非 null，与 main 同帧 TAIL、同尺寸/投影，§3.4 同法）——软失败降级（无目标 / 无深度纹理 / 尺寸不符 / PBO map 空 → translucentDepth=null，工序 B 空集、工序 C 行为等同 v2.23）
- [x] `DepthSnapshot`：增 `translucentDepth[]` 字段 + `hasTranslucentDepth()` / `translucentDepthAt(x,y)`
- [x] `ObjectResolver`：新增工序 B（`translucentDepth < mainDepth` 像素反投影落位首层半透明，O(像素)、无射线-AABB；返回 `LongSet` 首层面供工序 C 跳过）；§5.4 改工序 C（候选 `firstSurfacePlaced.contains(pos.asLong())` 整候选跳过 + 残留射线-AABB）
- [ ] 验证（待游戏内）：单层水（水面精确 + 湖底物体同帧）；玻璃+水嵌套（水靠工序 C 残留枚举，**v2.26 起由区间推进覆盖，见 Phase 9**）；Fancy/Fast 回退（无 translucent 目标 → 原最近表面语义）；含水方块（水挂 waterlogged，工序 B 跳过不冲突）

**实施记录（v2.24，✅ 已实现 2026-08-27）**：
- `DepthCapture`：`issueDepthRead(GpuTexture)` 抽公共 PBO 读回（主深度硬失败抛异常、translucent 软失败→null）；回调 `queueFencedTask` 内同时 `mapDepth` 两路，构造带 `translucentData` 的 `DepthSnapshot`，两 PBO 一并删除。
- `ObjectResolver`：`resolve()` 在 translucent 分支先 `queryFirstTranslucent`（工序 B）得 `firstSurface` 再传 `queryTranslucent(..., firstSurface)`（工序 C）；`queryTranslucent` 增参 + 精筛循环对已放候选 `continue`。
- 编译：`gradlew compileJava` 通过（BUILD SUCCESSFUL）。

**实施记录（v2.24 修复第二轮，2026-08-27，源码级核查后修正读取时机）**：
- **根因修正**：v2.24 初版在 `renderLevel` TAIL 读 `getTranslucentTarget()` 恒 null（`targets.clear()` L590 已清句柄）；第一轮修复把读取挪到 `targets.clear()` 之前，但核查 `FrameGraphBuilder` 发现**这仍太晚**——translucent 是帧内内部资源，`InternalVirtualResource.release()`（L249-256）在 `frame.execute()` 期间最后一个消费它的 pass（透明度后处理链）结束时就把 `physicalResource` 置 null；`getTranslucentTarget().get()`（= `Objects.requireNonNull`）在 execute 返回后的任何注入点都抛 NPE → 回读软失败、工序 B 恒空。
- **正确时机**：translucent 深度定稿于 main pass 内 `copyDepthFrom(main)` + `renderGroup(TRANSLUCENT)`（LEQUAL 深度写，`ChunkSectionsToRender.renderGroup` L26-60），此刻 vanilla 自身正解引用该目标（L33 `group.outputTarget()`）→ 物理资源必然存活。**新增 `ChunkSectionsToRenderMixin`**：注入 `renderGroup` @TAIL，`group == TRANSLUCENT` 时调用 `DepthCapture.prepareTranslucentRead()`（PBO 读命令排在 TRANSLUCENT 组绘制命令之后，GPU 先绘制再读回，数据确定正确）。
- **清理**：移除 `LevelRendererMixin.onBeforeTargetsClear`（execute 后读取必失败，且会把 renderGroup 注入已取得的好读覆盖成 null）；`prepareTranslucentRead` 增防重入丢弃。
- 编译：`gradlew compileJava` 通过（BUILD SUCCESSFUL）。

### Phase 8：半透明掉落物正向归属（工序 D，v2.25，✅ 已实现 2026-08-27）
> v2.25 设计定稿（2026-08-27）：Fabulous 下玻璃/药水类掉落物画进 item_entity 目标、不写 main 深度（§3.1.1），§5.3 深度归属拿不到；它们已在实体快照中（含 AABB），缺的只是可见性判定——复用 §5.4 统一判定式 `Z_opaque ≥ t_entry − δ` 正向枚举盒覆盖像素（§5.3.1）。
- [x] `ObjectResolver`：`resolve()` 实体通道之后、仅 Fabulous（`useShaderTransparency()`）执行工序 D——候选 = 快照中未被 §5.3 报告的 ItemEntity（`EntityType.ITEM` typeId 判定）；逐候选复用 §5.3 盒/slab 求交 + §5.4 判定式（⓪ 相机在盒内早退 → ① 8 角投影 bbox 裁剪 → ② 逐像素射线-AABB + 主深度还原 `Z_opaque ≥ t_entry − δ`）；可见 → 复用 `addEntity`（light snapshot）进实体 store + snapshot 响应
- [ ] 验证（待游戏内）：水下玻璃瓶 / 贴天空玻璃瓶 / 被墙挡玻璃瓶（不可见）/ 瓶后瓶（宽松全报）/ 附魔玻璃瓶；普通不透明掉落物不受影响；Fancy/Fast 下跳过验证（§5.3 已覆盖）

**实施记录（v2.25，✅ 已实现 2026-08-27）**：
- `ObjectResolver`：新增 `queryTranslucentDrops`（工序 D 候选筛选，typeId 判定 + reported 排除）+ `isDropVisible`（§5.4 统一判定式）；`resolve()` 提取 `fabulous` 布尔，`queryEntities` 之后构建 reported UUID 集合并调用工序 D，附加诊断日志 `drops(D)`。
- 编译：`gradlew compileJava` 通过（BUILD SUCCESSFUL）。

### Phase 9：工序 C 重写——双锚点区间射线推进（v2.26，✅ 已实现 2026-08-28）
> v2.26 设计定稿（2026-08-28）：v2.24 工序 C 的候选粗筛（8px 屏幕栅格合并，同格只留最近）把嵌套半透明候选在进精筛**之前**丢弃，深湖/海洋中间水格系统性漏检，违反 §5.4 完整性论证。重写为逐像素**区间射线推进正向归属**（§5.4）：弃用候选集，对 `translucentDepth < mainDepth` 像素沿射线推进到 Z_opaque，跨 section 用 `maybeHas(透明)` 整节跳步，逐格按统一判定式 `t_entry ≤ Z_opaque − δ` 上报射线实际穿过的**每一个**可见透明格（含全部嵌套层）。
- [x] `ObjectResolver`：删除工序 C 的候选枚举（section 遍历 + palette 展开）+ 8px 栅格合并（`GRID`）+ 首层跳过（`firstSurfacePlaced` 入参）；新增区间射线推进（voxel DDA + section `maybeHas` 跳步 + 逐格形状求交 + `t_entry ≤ Z_opaque − δ` 上报去重）；保留工序 B（`queryFirstTranslucent`）与 `fabulous` 分支；降级回退 `queryTranslucentFallback`/`queryTripwireCandidates`（translucent 目标缺失时，行为等同 v2.24 候选+精筛）
- [x] 验证（深水湖/海洋水柱完整复现，2026-08-28 用户实测通过）
- [ ] 验证（待补测）：玻璃+水嵌套（两层全报）；单层水（水面精确 + 湖底物体同帧）；被不透明遮挡的透明格不报；绊线仍可拾取；Fancy/Fast 回退不变；含水方块（箱子+水）不冲突

**实施记录（v2.26，✅ 已实现 2026-08-28）**：
- `ObjectResolver`：新增 `queryTranslucent`（voxel DDA 区间推进 + section `maybeHas` 整节跳步 + `SEC_TERMINATE/EMPTY/HAS` 状态机 + 逐格形状求交 + `t_entry ≤ Z_opaque − δ` 上报去重），删除候选枚举/8px 合并/首层跳过；保留 `queryFirstTranslucent`（工序 B）与 `fabulous` 分支；软失败降级路径（`queryTranslucentFallback` / `queryTripwireCandidates`）保留。
- **谓词修复（水漏判根因，2026-08-28）**：`isSemiTransparentLayer` 双分支——非空流体按 `ItemBlockRenderTypes.getRenderLayer(FluidState)` 判层（水 → TRANSLUCENT），空流体方块按 `getChunkRenderType` 判层（TRANSLUCENT ∪ TRIPWIRE）——与 `SectionCompiler` 同构，修复"Fabulous 只见水底不见水面"（详见 §5.4 谓词实现注意）；水节 `maybeHas` → SEC_HAS，工序 C 逐格枚举整个水柱。
- 验证过程：海洋场景 t/m 双深度诊断（t<m=88% 证实深度机制正常、反投影 t 命中率 0% 定位谓词根因）→ 谓词修复后水柱完整上报（用户实测确认）→ 移除 `[TEST-CODE]` 诊断块。
- 编译：`gradlew compileJava` 通过（BUILD SUCCESSFUL）。

### Phase 10：半透明方块减量（水 + 满格透明）——translucent 深度在场判定（v2.36，✅ 已实现 2026-09-08，待游戏内验证）
> v2.36 实施（2026-09-08，✅ 已实现；范围定案见 §7.12）：排干的水/岩浆及被移除的满格透明（玻璃块/染色玻璃/冰）因 §7.11 只删实心不透明格而永成幽灵。修正 = 在 translucent 深度场上同构复刻 §7.11 判据（translucent 目标 = 主深度拷贝被 TRANSLUCENT 组在前覆盖，"无半透明"自编码为 `t==m` → 单值比较天然不误删活对象）；cells 分两段——**opaque/main 段**（实心不透明 ∪ 冻结实体占用格 ∪ **岩浆**）+ **translucent 段 = 水 ∪ 满格透明**；判据场随采集通道选（Fabulous 下水/玻璃 → translucent 场；Fancy/Fast 水/玻璃写 main → 现有 main 场 + 放宽 reporter/applier 门槛；岩浆写 main → 恒走 main 段，PBO 降级不受影响）。
- [x] `memory_cells.bin` version=3：记忆侧 `MemoryCellReporter` 两段布局（opaque/main 段 long 数组 + translucent 开关 byte + translucent 段 count/longs；`appliedBlocks(dim)` 取格 + 现状核验分段：水/满格透明 → translucent、实心不透明/岩浆 → main + 同距离球/指纹/原子写）；version≤2 旧文件 translucent 段空、行为等同 v2.23
- [x] 采集侧：`DeletionJudge` 增 `testTranslucent` 判定变体（读 cells.translucent 段；复用 bbox/slab/δ；比较 translucent 目标 `Z_translucent ≥ t_far − δ`、≥2 像素，防御性判空集）；`ObjectResolver` 按「Fabulous && hasTranslucentDepth && `translucentEnabled` → translucent 场 / Fancy-Fast → 并入 main 场 / Fabulous &&（!hasT || !开关）→ 空集」路由 translucent 段，删格并入同一 `deletions`
- [x] 记忆侧 `DeletionApplier`：deletion 主循环守卫放宽到 `isDeletableContent`（实心不透明 ∪ 流体（水/岩浆）∪ 满格透明）；相机格快路径仍只删实心不透明；玻璃板/栅栏等非满形状/段外仍拒删
- [x] 配置：`removalTranslucentEnabled`（默认 true，仅 Fabulous 生效；随 cells version=3 头下发）
- [ ] 验证（待游戏内，Phase 10）：桶舀水整片消失一轮删（含深水柱中间格）；敲掉/移动玻璃块、染色玻璃、冰 → 判删、活玻璃不误删、被挡玻璃欠删；活水/活岩浆不误删；部分排水欠删但视觉无害；Fabulous PBO 软失败水/玻璃跳过、Fancy/Fast 走 main 场删除；玻璃板/栅栏等非满形状回归不删

---

## 十二、实现期需核实的源码清单

| 待核实项 | 状态 | 位置 |
|---|---|---|
| 矩阵来源（RenderSystem vs @Local） | ✅ 已解决：Mixin TAIL 直接取 `renderLevel` 方法参数（projectionMatrix / modelViewMatrix / camera），无需 RenderSystem 状态、无需重建模型视图 | `LevelRendererMixin` / `LevelRenderer.renderLevel` L461-472 |
| 深度回读方式 | ✅ 已解决：手写 GL PBO（vanilla `copyTextureToBuffer` 仅 color-only，深度挂 color 附件 FBO 不完整 → 1286） | `DepthCapture` / `GlCommandEncoder` L684-689 |
| 深度纹理格式及读取方式 | ✅ 已解决：`DEPTH32`，4 字节 float/像素，范围 [0,1]，按纹理原生尺寸读回 | `RenderTarget` / 实际读回验证 |
| `getTransparencyChain()` 非空 → 半透明进独立 target（**v2.10：配置相关**） | ✅ 已核实：仅 `useShaderTransparency()`（= `options.improvedTransparency`，默认 false，Fancy/Fast 显式 false、Fabulous 非 Mac true）时创建 translucent/item_entity/particles/weather/clouds 独立 target；**为 null 时无独立 target，`ChunkSectionLayerGroup.outputTarget()` 回退 main，`TRANSLUCENT_TERRAIN`/`TRIPWIRE_TERRAIN` 继承 pipeline 默认 depthWrite=true → 写主深度** | `Minecraft` L2306-2308 / `Options` L197 / `GraphicsPreset` L56/77/98 / `LevelRenderer` L524-531/L706 / `ChunkSectionLayerGroup` L28-37 / `RenderPipeline` L483 |
| 块 → 渲染层映射（玻璃 TRANSLUCENT / 铁栅栏 CUTOUT / 岩浆 SOLID / 水 TRANSLUCENT） | ✅ 已核实 | `ItemBlockRenderTypes` L18-358 / `ChunkSectionLayerGroup` L8-10 |
| 药水 / 光照 / 雾是否影响深度 | ✅ 已核实：全部颜色域（雾 / 光图 / gamma），不碰深度 | `LightTexture` L114 / `LavaFogEnvironment` L26-32 / `BlindnessFogEnvironment` / `terrain.fsh` L96 |
| CUTOUT 半透明是否丢弃（不写深度） | ✅ 已核实：`ALPHA_CUTOUT` discard 在写深度前 | `terrain.fsh` L91-95 |
| **modelView 纯旋转（无平移）** | ✅ 已核实：`new Matrix4f().rotation(inverseRotation)` → 反投影须 `+camPos` | `GameRenderer` L761-762 |
| **相机平移在顶点着色器** | ✅ 已核实：`ProjMat * ModelViewMat * (pos + ChunkPosition − CameraBlockPos)` | `terrain.vsh` L26-27 / `globals.glsl` |
| **远平面实际值** | ✅ 已核实：`far = max(renderDistance*4, cloudRange*16)`（12 chunk ≈ 768，非固定 10000）。⚠️ **天空阈值必须随远平面收紧**：深度非线性下 `d(47 格) ≈ 0.999`，旧的 0.999 阈值会把 ~47 格外对象当天空丢弃 → v2.3 改为 `d ≥ 1.0f`，v2.6 精确化为**远平面极限深度 `d_far`**（标准透视矩阵下恰为 1.0，原由投影推导、**v2.16 因 JOML 布局 m23/m32 互换致推导出错而改为固定 1.0**；见 §4.2/§5.3/§6.2） | `GameRenderer` L455-457 |
| **JOML 矩阵语义** | ✅ 已核实：`mul(right, dest) = this*right`（→ P·V 用 `proj.mul(mv, new)`）；`invert()` 原地返回 this | `Matrix4f` L1147 / L2701 |
| **Entity API（1.21.11）** | ✅ 已核实：`onGround()`（L687）、`isLocalPlayer()`（L370）、`getHealth()` 仅 `LivingEntity`（L1146）、`getBoundingBox()`（L3356）、`isRemoved()`（L3902）、`getId/getUUID/getDeltaMovement/getYRot/getXRot` | `Entity` / `LivingEntity` |
| **方块实体序列化** | ✅ 已核实：`saveWithFullMetadata(HolderLookup.Provider)` 返回 `CompoundTag` | `BlockEntity` L108 |
| **世界坐标→屏幕 / 查询 API** | ✅ 已核实：`AABB.inflate`（L195）/`contains` 半开 `>=min && <max`（L259-261，**v2.9：半开区间把落在盒 max 面的画/画框像素拒之门外**，§5.3 须闭区间/epsilon）、`BlockPos.containing`（L91-93）/`asLong`（L107）/`of`（L87）；**`ClientLevel.entitiesForRendering()`（L327）为反驳**（见下行） | `AABB` / `BlockPos` / `ClientLevel` |
| 实体渲染包围盒获取（`getBoundingBoxForCulling`） | ✅ **v2.9 核实**：视锥盒实际是 `getBoundingBoxForCulling().inflate(0.5)`（渲染模型 ⊆ 该盒，L69）→ §5.3 用 `getBoundingBox().inflate(0.5)`（0.45 留 0.05 壳）；**覆盖缺口**：Marker 盔甲架 0×0 盒（`ArmorStand` L59/L628-634）、物品平片堆摊开 ~3 格（`ItemEntityRenderer` L93-107）、带 display block 矿车纵向延伸（`AbstractMinecartRenderer` L160-163） | `EntityRenderer` L69/L89-90 / 各实体渲染器 |
| **SectionPos 桶（v2.4）** | ✅ 已核实：实现用 `SectionPos.blockToSectionCoord`（int 重载）+ `SectionPos.asLong(sx,sy,sz)` 建桶（`ObjectResolver.buildEntityBucket`，实体盒按 min/max section 全范围分配）；查询用 `SectionPos.asLong(blockToSectionCoord(W))` 定位（`Unprojector`/§5.3）；`SectionPos.of(BlockPos)`/`SectionPos.blockToSectionCoord` 亦用于 §5.4 区间推进的 section 跳步（v2.26，替代原粗筛 section 遍历） | `SectionPos` / `ObjectResolver` |
| **快照捕获实体列表（v2.4/v2.9）** | ❌ **v2.9 反驳：`ClientLevel.entitiesForRendering()` 返回全部已加载实体、无视锥裁剪**（= `byId.values()`，L327-329）；真实裁剪在 `extractVisibleEntities` L821-826（距离 `shouldRenderAtSqrDistance` / section 可见度 L1458-1463 / 相机所在实体 L825 / LocalPlayer L826 / EnderDragonPart）。**v2.9 定稿：`LevelRenderState.entityRenderStates` 亦不可用**——存 `EntityRenderState`（DTO，无 AABB/id/uuid/rot/motion/health、无 Entity 引用），且 `levelRenderState.reset()` L593 在 TAIL L594 前已清空；**方案：第二注入点 `extractVisibleEntities` @TAIL 复刻 L821-826 谓词链，对 `entitiesForRendering()` 幸存者快照完整 Entity 数据**（`getBoundingBox()/getId/getUUID/getType/getYRot/getXRot/getDeltaMovement/getXo/getYo/getZo/onGround/getHealth/blockPosition`，渲染线程纯字段读取、确认安全；v2.11：`xo/yo/zo` 供 partialTick 插值）；`EntityRenderDispatcher.shouldRender`（L124）/`isSectionCompiledAndVisible`（L1458）均 public 可调 | `ClientLevel` L327 / `LevelRenderer` L812-842/L1458 / `EntityRenderDispatcher` L124 / `Entity` / `LivingEntity` |
| **射线-AABB 求交（v2.5/v2.8）** | ❌ **v2.9 反驳：vanilla `AABB.clip` 不适用**——返回 `Optional<Vec3>`（一个交点坐标非 t）、起点在盒内返回 empty（非负 t）、终点在盒面 s==1.0 亦拒（L290-307/L370-395）；**须手写 slab 求交**（带符号 t_entry，起点盒内为负）——§5.3/§5.4 相机在盒内判定（v2.8）依赖此约定。**v2.11**：slab 对**零/负外扩盒**（marker 盔甲架 0×0）**直接返回 null（不判相交）**，防除零/NaN | `AABB` |
| **肢体判别 blockstate（v2.8，v2.11 扩）** | ✅ 已核实：`ObjectResolver` 肢体判别 A/B 均已实现——非薄方块排除用 `Block.isShapeFullBlock(st.getShape(level,pos))`（查 `cell(W±dir·ε)`/`cell(W)`），前向空扫用 `level.getBlockState(...).isAir()`（步长 ε，A 至 `|W−camPos|+max(2ε,1格)`、B 至盒近面），非他体排除用桶内其他实体盒 contains；该子步在渲染线程读取（ObjectResolver 内） | `ObjectResolver`（§5.3 肢体判别 A/B） |
| **半透明候选谓词（v2.7/v2.10/v2.12，v2.26 修复水漏判）** | ✅ **v2.9 核实**：映射按 `TYPE_BY_BLOCK` 静态表（红石线/下界传送门显式列入 TRANSLUCENT，L312-353）；`isTranslucent()` 在客户端仅 1 处调用且与渲染层映射无关（`LevelRenderer` L996）；名单另有浮冰/气泡柱/遮光玻璃；透明格判定比对 `ChunkSectionLayer.TRANSLUCENT`（v2.10 类型修正，v2.26 起用于推进逐格判定）；**绊线（v2.12 修订）**：`Blocks.TRIPWIRE` 是 TYPE_BY_BLOCK 唯一 TRIPWIRE 条目（L19-20）、**1.21.11 无独立 string 方块**；透明格判定谓词扩展为 `TRANSLUCENT ∪ TRIPWIRE`（v2.26 区间推进逐格复用），Fabulous 下可采集、**无需读 weather 目标**；**v2.26 修复（水）**：`TYPE_BY_BLOCK` **不含 `Blocks.WATER`**（`getChunkRenderType(水)` 返回 SOLID），水按流体入组——`isSemiTransparentLayer` 须对**非空流体**走 `ItemBlockRenderTypes.getRenderLayer(FluidState)`（水 → TRANSLUCENT，§5.4 谓词实现注意），与 `SectionCompiler` 双分支一致；Fancy/Fast 回退 main 写深度，§5.1 可拾取 | `ItemBlockRenderTypes` L19-20/L312-358/L355-358/L395-398 / `SectionCompiler` L67-72 / `LevelRenderer` L711-712 / `ChunkSectionLayerGroup` L28-37 |
| **late_debug 清深度（v2.9）** | ✅ 核实：F3 gizmo 非空时 `clearDepthTexture(main, 1.0)` 在 TAIL 前（L799-801）→ 捕获须检测跳过 | `LevelRenderer` |
| **resize 重建深度纹理（v2.9）** | ✅ 核实：`destroyBuffers` 重建（L30-34）→ 每帧 TAIL 重取 `getDepthTexture()` 并重挂 FBO | `RenderTarget` / `Minecraft` L1415-1424 |
| **`glReadBuffer` / pack 状态（v2.9）** | ✅ 核实：`GlStateManager` 无 `glReadBuffer` 原语（须自补 GL_NONE）；`PACK_ROW_LENGTH=width` 被设后不复位（L687）→ 读回须显式管理 | `GlStateManager` / `GlCommandEncoder` |
| **PBO 双缓冲（v2.9）** | ✅ 核实：`executePendingTasks` 超时 0 轮询（L256-270）、回调可能推迟多帧 → PBO 不可复用 | `RenderSystem` |
| **entityTranslucent 写深度（v2.9）** | ✅ 核实：ENTITY_TRANSLUCENT 默认 depthWrite=true（L239-248）→ 半透明实体写 main 深度（对 §5.3 有利）；实体 alpha 阈值 0.1 处 discard（`entity.fsh` L24-26） | `RenderPipelines` / `entity.fsh` |
| **叶子默认 SOLID（v2.9）** | ✅ 核实：`cutoutLeaves` 选项默认关 → 叶子 SOLID 不透光；开启"漂亮叶子"才 CUTOUT | `LevelRenderer` L294 / `ItemBlockRenderTypes` L363-365 |
| **透明物品实体目标（v2.9）** | ✅ 核实：玻璃类/药水掉落物与物品 glint 画进 item_entity 目标、深度不回写 main | `RenderTypes` L261 / `Sheets` L50-51 |
| **逐块判定复用 §5.4（v2.23）** | ✅ 已核实：`DeletionJudge` 复用 §5.4 几何工具——投影 8 角 → bbox 屏幕裁剪（钳到视口）→ 逐像素手写 slab 求交（带符号 t，返回 `double[]{t_entry, t_far}`，空盒返回 null）→ 深度还原 `Z_opaque`（欧氏距离）比较 `Z_opaque ≥ tFar − δ`（DELTA=0.05），越过计数 ≥ `removalPixelThreshold` → deletions；比较基准用**块远面** `t_far`（§5.4 用近面 `t_entry`） | `DeletionJudge` / §5.4 |
| **反向通道 cells 文件（v2.23）** | ✅ 已核实：`MemoryCellReporter` 用 `Files.write(tmp, buf)` + `Files.move(ATOMIC_MOVE)`（回退 `REPLACE_EXISTING`）原子写，小端 ByteBuffer 布局（magic SCEL + version + threshold + maxRayDist + count + longs）与采集侧 `MemoryCellsReader` 逐字节对应；采集侧读时 `Files.getLastModifiedTime` mtime 门控、解析失败保留旧 mtime 下轮重试（半截写防护同 §7.4）；`TerrainData.fingerprint()` 已含 `deletions`（§7.4 门控不误跳过） | `MemoryCellReporter` / `MemoryCellsReader` / `Files` |
| **记忆侧距离球过滤（v2.23）** | ✅ 已核实：`MemoryCellReporter.computeCells` 只做 `|cell−agentPos|² ≤ removalMaxRayDist²` 距离球过滤（球心 = 玩家眼睛 `eyeY`），不缩角度（Over-inclusive 成立）；球外格不进 cells 文件 → 采集侧对它们无像素命中、自然跳过；触发 = mutationVersion 变化 || 每 `memoryCellsWriteIntervalTicks` 兜底 || 内容指纹变化 | `MemoryCellReporter` |
| **translucent 目标深度可读（v2.24）** | ✅ 已核实：`LevelRenderer.getTranslucentTarget()` 在 Fabulous（`useShaderTransparency()`）下返回独立 translucent RenderTarget、Fancy/Fast 为 null（`ChunkSectionLayerGroup.outputTarget()` L32 引用）；`addMainPass` L705-706 `copyDepthFrom(main)` + L710 TRANSLUCENT 组 LEQUAL 覆盖 ⇒ 其深度 = "首个半透明面"且 `≤ mainDepth`；同投影同尺寸 → 第二路 PBO 可直接读 | `LevelRenderer` L222-234/L705-710 / `ChunkSectionLayerGroup` L28-37 / `RenderTarget.getDepthTexture` L118 |
| **fluid/透明面剔除对 translucent 逐格在场判定（v2.36）** | ✅ 判据已实现（2026-09-08，§7.12）；渲染面剔除细部仍待游戏内观察：水/岩浆流体的渲染面剔除（淹没/内部面是否写 translucent 片元）与满格透明方块的面剔除，只影响"整片消失单帧全删 vs 多帧收敛"的快慢，不影响判据安全（活对象必在暴露面写片元 → `t < t_far` 保活；消失必 `t==m`） | `FlowingFluid`/`FluidRenderer` / `SectionCompiler` / `GlassBlock` |
