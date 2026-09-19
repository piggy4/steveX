# BE 载体方块减量设计方案（v2.38）

> 状态：**设计待审阅，未实现**（2026-09-09 立项；2026-09-12 补 §13 核查记录、§7.1 追加决策
> G/G2/H/I/J、§9.1 BE 分类结局表；**同日批 2 扫测完成 ⇒ §14 报告，F2 取消、批 2 改为"§15 加表"
> —— 审阅请与 §7.1 / §9.1 / §13 / §14 这四节一并看**）。
>
> 立项源：docs/非满形状方块减量设计方案.md（v2.37）§13 附录——BE 载体方块完整删除（失败模式 F1–F3、
> 方案 B 定案）。本文档把 §13 方案展开为可实施设计。**前置依赖 v2.37**（v2.37 已落地并验收）。
> 判据有两条并列通道：**几何过滤**（v2.37 §4，适用甲类满格块）与 **§15 状态直读**（v2.37 §15，
> 适用无深度信号族——**扫测证明它是 148 个 BE 方块的正解**，§14.4；原 F2"把 BE 非满格并入几何段"
> 已取消）。
>
> 一句话问题（§13 复述）：告示牌/旗帜/头颅/花盆/床/箱子等**带方块实体（BE）的方块**不是单个方块，而是
> "方块 + BE 负载"两条记录耦合；BE 负载走一条**累积、永不删、对空位有回放权威**的通道（block_entities.nbt）。
> 现有删除只删世界里的方块本体，删不掉也持久不了 BE 记录 → 删了必复活（§13.1 对称性断裂）。

## 0. 一句话方案

**把判据与删除彻底分层：判据只管"方块本体已消失"（几何过滤 / §15 状态直读两条并列通道，均与是否带 BE
无关）；新增"唯一删除原语" = 现有 deletions 清单（**按决策 H 取 `deletions ∪ signalLossDeletions`**），
让它在产出同帧同时持久修剪采集侧 BE / 容器两个累积存储（block_entities.nbt、containers.nbt），再随各自
文件落盘——记录没了，复活根因（§13.1）消除。** 删除的原子性由"同 snapshot、同 tick、一次清单命中全部
通道"保证，不合并文件；持久性由采集侧修剪落盘承担（记忆侧改不了采集文件）。

## 1. 背景与前置

### 1.1 记忆系统的三通道不对称（§13.1，已核实到代码）

| 通道 | 落盘方式 | 代码锚点 | 删除能力 |
|---|---|---|---|
| terrain（方块本体） | 每帧整体覆写 = 本次可见集 | [VisionTerrainStore.java:117-143](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionTerrainStore.java#L117-L143) | deletions → 世界置空；文件天然缺位 → 持久 |
| block_entities（BE 负载） | 增量累积、只增/改、永不删旧 | [VisionBlockEntityStore.java:130-172](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionBlockEntityStore.java#L130-L172) | **无**（2026-09-12 更正，§13.2）：`clearStale` 只重置已应用闸门、在文件仍有记录时反而**促成一次重放**，不是删除能力；记录只能由本版采集侧修剪移除 |
| containers（L2 容器） | 交互提交；世界为空气**自足回放** | [ContainerMemoryApplier.java:215-229](../vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/ContainerMemoryApplier.java#L215-L229) | 无删除概念 → 与删除对打 |

复活的完整机理（例：墙上"入口"告示牌被敲）：
1. 采集侧证方块消失 → deletions → 记忆世界把牌置空（`deleteBlock` 只清世界内方块）；
2. block_entities.nbt 里"入口"记录**还在**（累积存储，从不删旧）；
3. 任何触发——同维别处一个 BE 被改 → 文件 mtime 变，或**服务器重启**（applied 表清空全量重放）——记忆侧
   重读，该格世界为空气 → 复原对空气位**放行**（"BE 记录是其自身方块权威来源"，[MemoryRestorer.java:244-247](../vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/MemoryRestorer.java#L244-L247)）→ 牌连文字放回；
4. 下一帧快照又证它消失又删 → 重启后幽灵牌出现、直到 agent 移动触发新快照才再删的**闪烁**。

> 此洞对**一切带 BE 的方块**成立（含现行可删的满格实心熔炉/木桶——只是其 BE 被采集侧 STRIP 成空壳、
> 数量少，表象被掩盖）。故 v2.38 的删除原语挂在**现有 deletions** 上，天然一并收口满格 BE。

### 1.2 本版与 v2.37 的分工

- **v2.37（先行）**：判据扩展到"非满方块消失判定"（几何源拾取盒、cells v4 几何段、判据场路由），并把
  BE 类从排除表中划出、指出"判方块消失可行，难在完整删除"——§2.2/§7 D/§13。
- **v2.38（本版）**：①判据对 BE 格的**方块**本体消失照常成立——**两条并列通道**：满格块走几何判据（现段），
  无深度信号的 BE 非满块走 v2.37 §15 状态直读**加表**（判据侧无新增逻辑；§14.4 定案，原 F2 取消）；
  ②补**删除原语跨通道**（并集，决策 H），让删格原子、持久地命中 terrain / block_entities / containers。

## 2. 目标范围与非目标

### 2.1 纳入（四项子特性）

| 子特性 | 内容 | 依赖 |
|---|---|---|
| F1 **删除原语跨通道持久化**（核心） | deletions 同帧修剪采集侧 block_entities.nbt + containers.nbt 并落盘；BE/容器记录随方块消失持久移除 | 无（对现行满格 BE 立即生效） |
| ~~F2 **判据纳入"可深度判据的 BE 非满块"**~~ → **F2′：§15 信号缺失表加表** | ~~谓词放行 BE 格进 cells 几何段~~；**改为**把 `乙2 ∪ 丙 = 148` 个无深度信号的 BE 非满块加入 v2.37 §15 状态直读通道的信号缺失表（**§14.4**，"加表即纳入"，采集侧零判据改动） | ~~v2.37 cells v4 + 几何过滤~~ → v2.37 §15 通道 + 本版 F1/H |
| F3 **容器通道协同** | 容器 reconcile 不再对"本代际被判删、世界为空气"的格自足回放 | F1 |
| F4 **多格/配对最小规则** | 床双格随现实整床消失天然双格同判；双格大箱幸存半格单格化、容器记录按 BE 宿主格删 | F1 + **F2′（§15 加表）** |

### 2.2 明确排除 / 非目标

| 排除 | 理由 |
|---|---|
| **BE 内容的独立"消失证明/更新"** | 内容（告示牌文字/箱子物品）对深度/几何不可见，只能作为方块消失的**从属品**删除；其*变更*走正向采集通道（本版不动）。<br>**2026-09-12 追加**：该"正向通道"的**发布端**此前是坏的——内容收缩时既不发客户端也不落盘（实机发现），见 **§18**。本章修好后"变更走正向通道"这个前提才真正成立；**范围不变**（§18 不新增任何判据） |
| **~~绊线/绊线钩~~、无深度信号的 BE 类**（注册表扫描锁定） | ~~判据无信号则方块本体也证不了 → 沿 v2.37 排除表欠删~~<br>**2026-09-12 更新（v2.37 七次修订 §15）**：**绊线/绊线钩已移出本行**——改由 v2.37 §15「信号缺失族校正通道」（真实世界状态直读，与几何判据并列）判删，本版不欠删；**无深度信号的 BE 类**在**判据侧**同样适用该通道（状态直读与是否有 BE 无关），但其**执行侧依赖本版（v2.38）的唯一删除原语**（否则删了方块、留下 BE / 容器负载 = §13 失败模式）——故本版结束时它们仍欠删，原语就位后**加表即纳入**（v2.37 §15.5 第 3 条；接口侧 cells 信号缺失段已预留 `blockId` 字段，无需改格式）。**2026-09-12 扫测定量：该族 = 148 个方块（乙2+丙），且它包含箱子族/告示牌/床/旗帜/头颅——即本版真正要解决的那批（§14.3/§14.4）**。<br>**同日追加（§17 补遗）**：扫测的**乙1 桶（6 个：`brewing_stand`/`hopper`/`comparator`/`daylight_detector`/`sculk_sensor`/`calibrated_sculk_sensor`）**原不在 148 之列，也同样欠删（三处谓词合围）⇒ 已加入同一张表（理由 ③）⇒ **本行的排除范围现只剩"BE 内容语义"与"D 类无渲染体"（§9.1）** |
| **花盆等"足迹 ⊋ 拾取盒"、多格语义不清者**（扫测锁定，见 §3.4/§10 边界） | **扫测门控**（2026-09-12 与 §4.4 统一口径）：实测可判者纳入、不可判者退回欠删；**未测前一律按欠删**——本节与 §4.4 不再两说。<br>**2026-09-12 扫测解除（花盆）**：1.21.11 已把花盆拆成 **38 个独立方块**（`flower_pot` + `potted_*`，见 `Blocks.java`），每个 content 有**自己的烘焙模型**（`models/block/potted_cactus.json` 等，含植物本体）且**没有 BE** ⇒ 足迹 = 模型几何、④ 步天然通过 ⇒ **v2.37 几何段已覆盖它**，"足迹 ⊋ 拾取盒"的顾虑随几何源换成烘焙模型而消失（§14.1） |
| 方块被替换为**另一仍存活对象**（换牌/重建） | 是正向通道的覆盖更新，不是删除；删除只处理"现实里这块没了" |

### 2.3 一个前提性澄清（回答 §13.3 之问）

"完整删除"**不需要把 NBT 内容与方块物理合并成一份文件**。必要的是三性——原子性（一次删命全部记录）、
持久性（跨重启/跨后续任意落盘不复活）、覆盖面（terrain / BE / containers 都生效）。v2.38 用"唯一删除
原语"达成三性，而非合并文件（合并也管不住独立交互通道 containers，见 v2.37 §13.3 决策对比）。

## 3. 机制：判据管方块、原语管整格

### 3.1 唯一删除原语 = deletions 清单

- deletions 已存在且可信：DeletionJudge 从记忆侧 cells 出发、按"现实深度场证方块消失 + ≥removalPixelThreshold
  越票 + 跳过本次可见集"产出（[ObjectResolver.java:145-177](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/ObjectResolver.java#L145-L177)）。
- v2.38 把它从"只喂 terrain"提升为**唯一删除原语**：同 snapshot、同 tick，一次清单同时命中
  terrain / block_entities / containers 三个采集侧存储（删除→dropPos→落盘）。
- 记忆侧拿到同一份清单，只做世界内执行（置空 + clearStale），**不再新增任何删除判据**。

### 3.2 三通道清理矩阵（v2.38 生效后）

| 判据证明 | 原语动作 | terrain | block_entities | containers |
|---|---|---|---|---|
| 普通非满块消失（v2.37） | dropPos(pos) | 置空 | 无记录 | 无 |
| **BE 非满块方块消失**（本版批 2：经 §15 加表进来，走 `signalLossDeletions`，§14.4） | `applyDeletions(pos)`（= 唯一原语；**并集，决策 H**） | 置空 | **移除 nbt 记录**（内容随之从属删除） | 若有容器记录 → 移除 |
| **满格 BE 消失**（熔炉等，现已有） | dropPos(pos)（F1 收口） | 置空 | 移除记录 | 若有容器记录 → 移除 |

### 3.3 原子性与持久性来自哪里

- **原子性**：修剪发生在 deletions **产出的同一个 resolve 调用内**、BE store / 容器 store **落盘之前**
  （采集侧快照编排见 §4.1），不存在"部分命中"的时间窗；
- **持久性**：block_entities.nbt / containers.nbt 的文件本体被改写（被删条目缺位）。记忆侧重读（mtime 变）
  → 记录不存在 → 无回放；重启 → 文件里也没有 → 不复活。记忆侧 `clearStale` 保留为"本 tick 立即遗忘"，
  不再承担持久职责。

## 4. 采集侧

### 4.1 删除原语挂点（改动核心）

`ObjectResolver.resolve` 现状（行号为 **2026-09-12 实测值**，替代原 `L145-213`，见 §13.1）：

```
…四路查询 → deletions 计算(L176-257) → biomeStore.sync(L328)
→ getTerrainStore().sync(terrain, deletions, signalLossDeletions, …)   (L343-345, 写 terrain.nbt)
→ getStore().sync(blockEntities, …)                                    (L346-347, BE store 落盘，尚不知 deletions)
→ getEntityStore().sync(…)  → return ResolveResult(…)
```

本版在其上：在 **L343 之后、L346 之前**（terrain 已落盘、BE store 落盘**前**）插入修剪，再让 store 落盘：

1. `VisionBlockEntityStore.applyDeletions(dimensionId, posSet)`——从该维累积表移除这些 pos 的条目；
2. `ContainerMemoryStore.applyDeletions(dimensionId, posSet, 本代际)`——移除容器记录（**延迟修剪**，见 §7.1
   决策 G：pending 计数 + "观测到活体即撤销"）；
3. 两者各自置 dirty 并 **`save()`**——**不得依赖同帧 sync 代劳**：BE store 的落盘是 dirty 门控
   （[VisionBlockEntityStore.java:166-169](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionBlockEntityStore.java#L166-L169)），
   容器 store 本帧通常无交互提交，不主动置 dirty 就会出现"内存里删了、文件没变"（复活根因仍在，§13.2 第 3 条）。

**`posSet` = `deletions ∪ signalLossDeletions`**（§7.1 决策 H）——两张清单在同一次 resolve 内都已就位，
`L343-345` 已同时写 terrain。

> deletions 与 block_entities 快照来自**同一帧真实世界**：deletions 证明的是"记忆以为存在、现实已无几何"，
> 而 BE 累积表里同一 pos 的记录正是当初观察留存的旧痕迹 → 修剪方向安全、且顺带清掉"方块换成别的东西、
> 旧 BE 残留"的脏数据。跳过本次可见集已在判据层保证，修剪不会命中仍活着的对象。

### 4.2 VisionBlockEntityStore 改动

- 新增 `applyDeletions(String dimension, Collection<BlockPos> pos)`：对 `byDim` 该维桶移除 key
  （`posToKey`），非空即置 dirty；
- `sync`（[L130-172](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionBlockEntityStore.java#L130-L172)）
  现有"增/改、跳过相等"不动；dirty → save 逻辑复用——修剪后由本帧 sync 或本帧末尾落盘统一生效；
- 兼容：旧文件 / 旧逻辑读入路径不变；applyDeletions 对不存在的 key 是廉价 no-op。

### 4.3 ContainerMemoryStore 改动（capture 侧写路径，先核对实际存储形状）

- 容器存储当前由交互通道（ContainerMemoryTracker/Store）在**有交互时**提交，非每帧；须确认其按维、按
  pos 键的表结构与落盘入口（与 BE store 同构则直接复用模式）；
- 新增 `applyDeletions(String dimension, Collection<BlockPos> pos)`：移除容器记录并**强制 save**（即使无
  交互提交也落盘），让记忆侧在下个 poll 读到"记录已删"；
- 声明：容器记录只在**该格方块确实被删除**时移除——若现实只是内容变化而方块仍在，删除判据不会触发，
  记录照常由 reconcile 权威覆写（不变）。

### 4.4 判据纳入 BE 非满格（F2）—— **2026-09-12 批 2 扫测后取消，见 §14.3/§14.4**

> **本节原方案作废**（原文保留在下方，供追溯）。扫测（§14）给出的三条事实推翻了它的前提：
> ① 告示牌/床/箱子/旗帜/头颅/饰纹陶罐/潜影盒/潮涌核心/铜傀儡雕像等**根本没有烘焙模型几何**
> （模型文件里没有 `elements`，几何在 BE renderer 里）⇒ 几何过滤对它们**恒 0 票**；
> ② 箱子族（含铜箱/陷阱箱/末影箱）形状是 `Block.column(14,0,14)`，**非满格** ⇒ 它们今天在任何段之外，
> **F1 对箱子没有输入**；
> ③ F2 的净收益只剩 **6 个方块**（`brewing_stand`/`comparator`/`daylight_detector`/`hopper`/
> `sculk_sensor`/`calibrated_sculk_sensor`），且载荷全部不可镜像；其余 148 个要么无效（无几何）、
> 要么**有害**（有渲染器 ⇒ 足迹 ⊄ 模型几何 ⇒ 误删活体）。
>
> **替代方案 = §14.4：批 2 改为"§15 信号缺失表加表"**（`乙2 ∪ 丙 = 148` 个方块），采集侧零判据改动，
> 执行仍走唯一删除原语，护栏 G/G2/I/J 一字不改。**决策 H（并集）因此从优化项升为本批的必要条件。**

<details><summary>原 §4.4 文本（作废，仅追溯）</summary>

- v2.37 谓词 §4.1 第 4 步 `level.getBlockEntity(pos) == null` 排除 BE；本版放开为："BE 格进入几何段，
  判据只对其**方块本体**消失作证"。放行前以**注册表扫描**（v2.37 §10 第 3 条）对每类 BE 非满块确认：
  其本体方块写哪个深度场（main/translucent，路由见 v2.37 §5.2）、拾取盒 ⊆ 渲染足迹 → 才纳入；
- 预计多数可判：告示牌/悬挂牌（CUTOUT→main）、头颅、花盆、装饰陶罐、钟、讲台、雕纹书架、床、箱子等
  本体几何清晰；横幅等渲染特判的以实测为准——不可判者退回欠删（安全方向，不出现在 cells）；
- **判据不新增任何逻辑**：BE 非满格与普通非满格共用 v2.37 `testShaped` 几何过滤，blockId 只用于路由
  （v2.37 §5.2/§5.3）。cells v4 条目已含 blockId + 格内盒，无需扩展格式。

</details>

### 4.5 一帧数据流（采集侧，v2.38 后）

```
snapshot: DepthSnapshot + entities
  → MemoryCellsReader.read() → cells（含几何段：BE 非满块也上报其方块盒）
  → ObjectResolver.resolve(…)
       查询四路（terrain/blockEntities/entities/…）
       deletions = DeletionJudge.test + testShaped/…    ← 只证方块
       biomeStore.sync
       terrainStore.sync(terrain, deletions)            ← 写 terrain.nbt
       blockEntityStore.applyDeletions(deletions) + sync（落盘，记录已缺位）
       containerStore.applyDeletions(deletions) + save（强制落盘）
       entityStore.sync
```

## 5. 记忆侧

### 5.1 Reporter 与谓词（BlockStateUtil 同源）

- 谓词放开 BE 后，`MemoryCellReporter` 把"BE 非满块"并入几何段上报（判方块盒消失）；**满格 BE** 本就在
  main 段（`isSolidOpaque`，无需改）；
- 谓词仍与 applier 放行同一把尺（同文件同侧），杜绝"上报了删不掉 / 删得到却没上报"的口径漂移；
- cells 里 BE 格的条目仍是 {pos, blockId, boxes}——**不携带 BE 负载**（负载不参与判据，删除时随方块从属清）。

### 5.2 DeletionApplier

- 主循环内容守卫扩到"BE 非满格"（方块本体在可删集内即放行）；删格 = `setBlock(air)` + 世界内 BE 移除
  （setBlock 已做，同现行）+ `clearStale`（沿用）；
- **不做任何持久化删除**——持久由采集侧修剪承担；本类职责止于世界内执行。

### 5.3 ContainerMemoryApplier（F3：容器通道协同）

- 问题：容器 reconcile 每轮把记录覆写到世界，世界为空气时**自足回放**（[L215-229](../vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/ContainerMemoryApplier.java#L215-L229)）→ 与本代际删除对打；
- 改动：`tick(level)` 增加一参 `Set<BlockPos> deletionGuard`；`applyPos` 在世界为空气时，若
  `deletionGuard.contains(pos)` → **跳过放置**（记录保留在缓存，等采集侧修剪后的文件在下个 poll 生效、
  记录自然消失）；
- 挂接（[MemoryWorldManager.java:375-384](../vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/MemoryWorldManager.java#L375-L384)）：
  `terrain.deletions()` 在 CONTAINER.tick 之前已可得 → `CONTAINER.tick(level, new HashSet<>(terrain.deletions()))`，
  **无需改 tick 次序**；

> **★ 2026-09-12 修正（决策 J，必须改）**：上面这条 guard 的**作用域不够**——它是"当帧 deletions"，在镜像侧
> 删格后会**自取消**（该格已不在镜像 ⇒ 记忆侧不再上报该 cell ⇒ 下一帧 deletions 里没有这个 pos）⇒ guard
> 空转、容器 reconcile 照常回放 ⇒ **容器通道按 poll 间隔对打**（§1.1"与删除对打"的机理，原 guard 挡不住）。
> 须改为**会话级墓碑集**，签名扩为 `tick(level, deletionsThisGen, realityBlocks)`。详见 §7.1 决策 J。
- 世界方块仍与记录一致、或 BE 缺失补挂等其它分支不变（视觉方块在 → 照常 reconcile 内容，这是权威覆写语义）。

### 5.4 记忆侧 tick 次序（本版不改序）

```
TERRAIN.tick(level) → 返回 terrain(blocks+deletions+camera)
ENTITY/RESTORER/CONTAINER(skip=deletions)/BIOME
DELETION.apply(level, terrain, currentUuids)   ← 世界内删格
CELLS.tick(level)                              ← 上报下一轮（BE 格已从 cells 撤下）
```

> RESTORER.tick 在 DELETION 前执行：若采集侧已修剪落盘 → BE 文件已无该记录 → 本 tick 不会重放；
> 若采集侧尚未落盘（同一代际竞争）→ 重放后本 tick DELETION 又置空 → 收敛于下代际（文件缺位后稳定）。

## 6. 多格 / 配对（F4，最小规则）

- **床**：现实中敲半张床会破坏整张床 → 双格同时变空气 → 判据对两格各自证方块消失 → 双格同进 deletions
  → 双格同删，天然无"半床非法态"。不需要配对删除逻辑，只需验证两格都上报为几何段 cells。
- **双格大箱**：敲掉一半 → 现实把幸存格单格化（block state 变 single）。逐格判据删被敲那格即可；
  幸存格由**正向通道**（下帧可见 terrain）更新为 single——删除不用管。容器记录删除规则（**2026-09-12 改**）：
  **按 pos 各删**——被敲半的记录随该 pos 进 `posSet` 被修剪，幸存半的记录自然保留（每半本就各有一条
  自己的 per-pos 记录，见 §13.3；**不存在"BE 宿主格"这个概念**）。**实施期须核对**：半删后
  `writeSingleOrMigrate` 的迁移时机（只在**再次开箱交互**时触发）是否会留下指向已不存在坐标的孤儿记录。
- 其余"一对多"（如长桌类非本清单）不做，扫测锁定为欠删。

## 7. 决策定案（待审阅）

| 决策 | 候选 | 定案 | 理由 |
|---|---|---|---|
| A：删除原语 | 合并文件（A 方案）vs **deletions 唯一原语（B）** vs 存在性绑定 terrain（C） | **B**（立项于 v2.37 §13.3） | 原子性靠同帧同 tick，持久性靠采集侧落盘；改动最小、贴合现有 deletions；合并也管不住 containers |
| B：修剪位置 | 记忆侧持久写 vs **采集侧 resolve 内、store 落盘前** | 采集侧 resolve 内 | block_entities/containers 文件是采集侧写的、记忆侧只读；deletions 就在同函数（[ObjectResolver.java:158-210](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/ObjectResolver.java#L158-L210)），零新信号 |
| C：容器自足回放 | 保留 vs 加 deletionGuard 跳过 | **guard 跳过（仅空气+本代际被判删）** | 不破坏"视觉未放好前容器先补壳"的冷启动语义；只堵"刚被删又回放"的对抗 |
| D：BE 判据纳入 | 全量纳入 vs 注册表扫描分类后纳入 | **扫描分类后纳入**（v2.37 §10 第 3 条同一扫描） | 只把"本体写深度场 + 拾取盒⊆足迹"者放进 cells；横幅等退回欠删 |
| E：BE 内容生命周期 | 内容独立判删/更新 vs **从属方块删除** | 从属 | 内容不可被几何证明；变更走正向通道（非本版）——**该通道的发布端缺陷见 §18（2026-09-12 实机发现）** |
| F：多格 | 配对删除逻辑 vs 现实语义 + 最小规则 | **现实语义最小规则**（床整床删、双箱单格化交正向通道） | 逐格判据 + 正向更新已覆盖绝大多数；多格的记录按 **pos 各删**（§13.3，无"BE 宿主格"概念） |

### 7.1 追加决策（2026-09-12 定案 —— 审阅期核查后）

> 背景：核查（§13）确认 F1 补上了 BE 通道**唯一**的删除路径，同时把"删除"从**可自愈**变成**持久**
> ——代价模型变了。故护栏必须与本版**同版到位**，否则 F1 只是把"幽灵闪烁"换成"永久丢内容"。

| 决策 | 候选 | 定案 | 理由 |
|---|---|---|---|
| **G：假阳性代价与护栏** | 不加护栏 vs 统一 K 帧 vs **分通道** | **分通道**（下 §G） | 两条通道载荷的**可重建性相反** |
| **G2：渲染距离钳制** | 只靠 K 帧 vs **判据入口二次距离过滤** | **二次过滤**（采集侧，不改格式） | 未渲染区不写深度 ⇒ **持续性**假阳性，K 帧救不了 |
| **H：原语覆盖范围** | 只覆盖 deletions vs **并集** | **`deletions ∪ signalLossDeletions`** | §2.2"加表即纳入"的承诺；修剪无内容守卫，不违反 §15.2 |
| **I：不产生半态** | 依赖"记忆侧必然实删" vs **修剪前块身份复核** | **块身份复核** | 把 §8 的假设升级为**构造性不变量** |
| **J：F3 guard 作用域** | 当帧 deletions vs **会话级墓碑集** | **墓碑集** | 当帧 guard **自取消** ⇒ 容器通道按 poll 周期对打 |

#### G —— 假阳性代价：两个事实先立住

1. **BE 记录来源 = 每帧观测 ⇒ 载荷可重建**：[ObjectResolver.java:438-449](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/ObjectResolver.java#L438-L449)
   的 `recordBlock` 只在该方块**本帧可见**（射线落格命中）时"搭方块的便车"采集，payload = 客户端同步面
   （`BlockEntityFieldPolicy` 的 PASS 表）。⇒ 被误修剪后，只要该方块**再次被观测**，方块本体（terrain 可见集
   → TerrainRestorer）与记录（`recordBlock` → `store.sync` 重新入表）**同帧一起回填** ⇒ 假阳性代价 =
   **一轮闪烁，可自愈**。故 **BE 记录即时修剪、不加护栏**（加了只会把欠删引到"记录本就不该留"的场合）。
2. **容器记录来源 = 交互 ⇒ 载荷不可重建**：[ContainerMemoryTracker.java:98/135/179](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/ContainerMemoryTracker.java#L98-L179)
   只读客户端容器菜单 `menu.slots`（`onClientTick`），`commitFromClose` 才落盘；而
   [BlockEntityFieldPolicy.java:76-95](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/BlockEntityFieldPolicy.java#L76-L95)
   **刻意 STRIP** 容器族的 `Items`（"Items 仅服务端持久化；无交互内部一律不采"）。⇒ 物品记忆**只在玩家开箱
   时存在过**，观测层面永远拿不回来 ⇒ 一旦修剪，**永久丢失**。这是全系统**唯一**"删除不可逆"的载荷。

⇒ **定案：容器记录延迟修剪**。`ContainerMemoryStore.applyDeletions` 不即时删，改为记
`pendingDel[pos] = 首见代际`，并**每个后续代际重新确认**：

- 该 pos 本代际**未被观测到活体**（`pos ∉ currentTerrain`）⇒ 计数 +1，达 **K = 4 代际** → 真删 + 强制落盘；
- 该 pos 本代际**被观测到活体**（`pos ∈ currentTerrain`，与 `DeletionJudge:221` 同款可见集）⇒ **撤销**
  pending、记录保留——这正是"假阳性且之后被看到"的正常结局；
- **计数器只看采集侧自己的观测（`currentTerrain`），不得依赖该格再次出现在 `deletions` 里**：镜像侧删格后
  记忆侧就不再上报该 cell（`MemoryCellReporter` 只报镜像里存在的块），deletions 会自然消失 ⇒ 靠 deletions
  计数**永远数不到 2**。这是本决策必须写死的点。
- `pendingDel` 是**内存态**（不落盘）：重启后计数从零开始 ⇒ 最坏只是把修剪再推迟 K 代际（安全方向）。

代价：文件修剪被推迟 K 代际 ⇒ 若游戏在窗口内退出，记录随旧文件存活一次重启 ⇒ 退化为 §9 的瞬态窗口
（多一次可见幽灵、随后被删）。**"延迟的代价是瞬态、误删的代价是永久"——不对称，故 K 宜大不宜小**
（K = 4 起，配置项，下限 2）。

#### G2 —— 主护栏：渲染距离钳制（比 K 帧更必要）

判据对"**未被渲染但存在**"的方块会**持续**误判消失：渲染距离外的地形不写深度 ⇒ `depthAt` 读到远平面/天空 ⇒
`zOpaque = ∞` ⇒ 该格投影内**每个像素**都记"越过"（[DeletionJudge.java:286](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/DeletionJudge.java#L286)，几何段同 `:528`）。
而 cells 目前只按 `removalMaxRayDist = 96`（记忆侧）过滤，**与客户端渲染距离无耦合**：渲染距离 4 区块（64 格）
时，65~96 格处的记忆格必然误判，且**永不进入 `currentTerrain`** ⇒ G 的"观测到活体即撤销"救不了。

定案：**采集侧在判据入口按 `min(removalMaxRayDist, 渲染距离保守上界)` 二次距离过滤**，超出者跳过（欠删方向）；
保守上界取 `(renderDistanceChunks − 2) × 16`（视锥 + 区块对齐 + 雾起留余量）。**不改 cells 格式**（把该上界
放进 cells 头字段下发列为后续优化）。这条与 F1/F3 实现解耦（F2 已取消），但**同版实施**——它守的是同一件事：假阳性的新代价。

#### H —— 原语覆盖两条删除清单

`applyDeletions` 的入参 = `deletions ∪ signalLossDeletions`（两张清单在同一 resolve 内都已就位）。

**为什么这不违反 §15.2**：§15.2 禁的是**合并内容守卫**（会让非绊线来源也走放宽口径、让双保险失效）；而修剪
阶段**没有内容守卫**——它的判据是"该 pos 有记录吗"。故合并 pos 集合不放松任何守尺；记忆侧执行侧仍是两套
守卫分开跑（`deleteBlock` / `deleteSignalLossBlock`）。同理 §5.3 的容器 guard 入参也取并集（"某格被判删"这一
事实对容器通道同样成立）。

**为什么必须有**：§2.2 承诺"无深度信号的 BE 类……原语就位后加表即纳入"。若原语只覆盖 `deletions`，那么将来
任何**带 BE 的**方块被加进信号缺失表时，就会出现"删了方块、留下 BE/容器负载"= §13 的失败模式复发。

> **2026-09-12 升级**：这个"将来"**就是本版的批 2 路线**——扫测（§14）证明箱子族/告示牌/床/旗帜/头颅等
> **148 个方块**只能走信号缺失表进来，**H 于是从"防复发的优化"变成"批 2 的必要条件"**：不取并集，加表
> 等于白加（判删了、记录还在、幽灵仍在）。

#### I —— 修剪集 ⊆ 实删集（把 §8 的假设升为不变量）

§8 原称"不产生半态"，但它依赖一个**未写明的假设**：记忆侧**一定**会实删采集侧修剪的每个 pos。若不成立
（守卫拒删：镜像里已是空气 / 已被前一轮改掉 / 口径不符），结果 = **文件里记录没了、镜像里方块还在** = 无载荷
空壳（无字牌、空箱）＝ 正是 §8 承诺不出现的半态。

定案：**修剪前做块身份复核**——只有该 pos 在 cells 里上报的 `blockId`（§13.5 已确认字段存在）落在"记忆侧
可删口径块集"内才修剪。**为何这是构造性保证**：记忆侧守卫最终作用在**镜像**方块上，而镜像方块的 blockId
**就是**该 pos 在 cells 里上报的 blockId（记忆侧上报的就是自己的镜像状态）⇒ "id ∈ 可删块集" ⇒ 记忆侧对同
pos 的同 id 方块必然放行。**保守方向**：块级分类可能**漏**（如"含水非满块"的可删性还依赖 state）⇒ 漏 ⇒
不修剪 ⇒ 欠删（安全）；**不得**为追求覆盖改回"清单即修剪"。

兜底诊断：记忆侧已有 `deleted N (M deletions from judge)`（[DeletionApplier.java:164](../vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/DeletionApplier.java#L164)）
——补一条告警：`M > deleted` 即"收了清单但没删成"；配合采集侧的"已修剪数"即可在验收中直接判定半态。

#### J —— F3 的 guard 必须是会话级墓碑集

§5.3 把 guard 定为"本代际 deletions"，**挡不住对打**，因为该 guard 当帧即**自取消**：

1. 第 N 帧：现实无箱、镜像有箱（记录回放）⇒ 镜像上报该 cell ⇒ 判删 ⇒ `deletions=[P]` ⇒ DELETION 置空
   + guard 挡回放 ✓；
2. 第 N+1 帧：镜像该格已是空气 ⇒ 记忆侧**不再上报该 cell** ⇒ 判删不再产出 P ⇒ `deletions=∅` ⇒ guard 空 ⇒
   容器 reconcile **照常回放** ⇒ 镜像又有箱；
3. 第 N+2 帧：回到 1。⇒ **周期 = poll 间隔的振荡**。

定案：记忆侧维护**会话级墓碑集** `deletedByRemoval`（DELETION 每次实删格时写入），容器 applier 跳过回放的规则 =
"世界为空气 ∧ `pos ∈ deletedByRemoval` ∧ **现实未重新观测到该格是容器**"；**清除条件** = `terrain.blocks`
该 pos 的 blockId ∈ 容器族（= 现实里箱子回来了 ⇒ 记录重新是权威）。这顺带让"假阳性后玩家又建回容器"恢复正常。
冷启动语义不变（墓碑只含**减量通道实删过**的格，不含其它）。签名见 §5.3 的修正注。

## 8. 正确性论证

- **复活根因消除**：复活 = 累积 BE 记录 + 空气位回放权威。采集侧修剪后记录缺位 → 文件无、重放无、重启无
  （§3.3）。记忆侧 guard 只堵瞬态窗口。
- **不引入新假删（但假阳性的代价模型变了，2026-09-12）**：删除仍由判据（≥2 越票、跳过可见集）门控；
  修剪只对判据命中的 pos 生效，与删格同源同置信。但**修剪是持久的**：F1 之前假阳性最多留一个"下一帧
  被记录重放带回来的幽灵"，F1 之后记录被真删 ⇒ 代价分化为两条（§7.1 决策 G）——BE 载荷**可再观测重建**
  （一轮闪烁可自愈），容器物品**不可重建**（永久丢失）⇒ 故须**同版**加 G（容器 K=4 延迟修剪 + 观测到活体
  即撤销）与 G2（渲染距离钳制，抗持续性假阳性）两条护栏。**护栏不是可选项，是 F1 的配套前提。**
- **满格 BE 一并收口**：F1 挂现有 deletions，熔炉/木桶的潜在复活（v2.23 潜伏洞）随本版修复，无需改判据。
- **内容保护**：BE/容器内容永无独立删除资格（决策 E）。**"不产生半态"由不变量保证（决策 I）**：采集侧
  修剪前做块身份复核（cells 的 `blockId` ∈ 记忆侧可删口径块集），而镜像方块的 id 与 cells 上报的 id 同源
  ⇒ **修剪集 ⊆ 实删集**成为构造性质，不再是假设 ⇒ 不会出现"方块还在、负载被单独删掉"的空壳。欠删仍只留
  幽灵，由正向通道修正。
- **容器权威语义保留**：方块仍在 → reconcile 照常覆写内容（决策 C 未破坏）；方块已被判删 → 不回放、等待
  文件修剪后记录消失。

## 9. 已知边界与限制

- **瞬态窗口**：删除到采集侧修剪文件被记忆侧重读之间（≤ poll 间隔），容器 guard 挡自足回放；BE 通道若
  采集侧未来得及落盘就中断，可能在下代际快照到达前多放一次、再被同代际 DELETION 清掉——收敛但可能闪
  一下；采集完全离线期间不重写文件 → 记忆侧重启会按保留文件重放一次，恢复采集后首个快照再删净（可接受）；
- **横幅等渲染特判的 BE 非满块**：本体无可靠深度信号 → 方块消失判不了 → 欠删（范围而非缺漏，扫测锁定）；
- **内容变更 ≠ 删除**：告示牌文字/箱子物品变化由正向通道更新；本版只处理"整块消失"，不处理"内容变"
  的消失语义；
- **多格配对**：双箱半删的物品归属与 BE 宿主格需实施期实测核对；床由现实语义天然覆盖；
- 判据固有边界（占屏<阈值/距离>96/被永久遮挡）沿用 v2.37/§7.12，欠删方向；
- 非 vanilla 维不镜像（v2.32），本版同限。

### 9.1 BE 载体分类与本版结局（2026-09-12 补齐 —— 回答"能解决到什么程度"）

> 分类依据 = 本项目自己的注册表派生物 [BlockEntityFieldPolicy.java:49-111](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/BlockEntityFieldPolicy.java#L49-L111)
> （PASS / ALLOW / STRIP 三表）。"载荷可重建"列决定假阳性的代价，见 §7.1 决策 G。

| 类 | 代表方块 | 载荷 | 可重建？ | 本版结局 |
|---|---|---|---|---|
| **A 容器族**（STRIP 表 A） | 木桶/潜影盒（关盖）/漏斗/发射器/投掷器/合成器/酿造台/熔炉/烟熏炉/高炉（=**甲**）；**箱子/陷阱箱/末影箱/铜箱族**（=**丙**，§14.3 结论 2） | 物品（**交互来源**） | **否** | 满格那几个今天即可删、F1 立即生效；**箱子族** `getShape` = `Block.column(14,0,14)` ⇒ **非满格 ⇒ 今天不产生 deletions ⇒ F1 对箱子没有输入**（原"今天即可删"是错的）⇒ 依赖 **§15 加表（§14.4）**；物品载荷由 G/G2 护栏保护，**残余风险 = 假阳性** |
| **B 内容可见的非满格 BE**（PASS 表） | 告示牌/悬挂牌/旗帜/头颅/潮涌核心/信标/刷怪笼/营火/陈列架/末地折跃门/结构方块/拼图/测试方块 | 文字/纹样/陈列（**观测来源**） | **是** | 幽灵根因 **✓ 解决**；"本体能否判丢失"已由扫测定案（§14.2）：**多数是丙（无几何）+ 少数乙2 ⇒ 一律 §15 加表**，信标/刷怪笼/结构/拼图/测试是**甲**（满格，今天即可删），末地折跃门是**甲0**（`INVISIBLE`，零足迹）；假阳性代价 = 一轮闪烁（自愈） |
| **C 内容不可观测的 BE**（STRIP 表 B/C + ALLOW 剥字段） | 唱片机/讲台/雕纹书架/蜂巢/命令方块/铜傀儡雕像/饰纹陶罐/可疑沙/宝库/试炼刷怪笼/钟/附魔台/阳光传感器/比较器/幽匿族/末地传送门/**床**/移动活塞 | **镜像里本就没有** | —— | 幽灵根因 **✓ 彻底解决**，且**无载荷可丢**（删除时不存在"丢失"问题）；判据侧按 §14.2 分桶走通道，**不再逐行重复**（床/雕像/陶罐=丙，末地传送门/移动活塞=甲0，幽匿族=甲，讲台/钟/附魔台/陈列架/营火=乙2/甲） |
| **D 无渲染体 / 不可见的 BE 方块** | 移动活塞（`piston`）/末地传送门（`end_portal`）；非 BE 的 `barrier`/`light`/`structure_void` 同类 | —— | —— | **不可解，但无害**：无深度信号 ⇒ 判不了 ⇒ 欠删；可它们**不可见** ⇒ 幽灵在视觉上不存在 |

**结论（回答"是否存在不可解决的物体"）**：F1 之后**复活根因已无残留类别**——"记录累积 + 空气位回放权威"这条
机理对所有 BE 载体都被切断（记录缺位 ⇒ 文件无、重放无、重启无）。**残留的不是"某类方块解不了"，而是三类
"场景/条件性"缺口**：

1. **载荷唯一不可逆者 = 容器物品**（A 类，`containers.nbt`）。其余所有类的"内容"要么可再观测重建（B），
   要么镜像里本就没有（C）。⇒ 护栏 G/G2 是**为这一族单独存在**的。
2. **方块本体判不了 ⇒ 幽灵留存的场景**（与 v2.37 同源的判据固有边界，本版不动）：永久遮挡、占屏 < 2 像素
   （远距小方块）、**渲染距离外的格**（G2 已把这类从"误删"改判为"欠删"）、δ 可行窗口闭合的远距贴花
   （v2.39 决策 Q 已记账）、无深度信号的渲染特判（**旗帜待实测**，§10 第 6 条）。除"永久遮挡/配置性渲染
   距离"外，这些都是**延迟**（走近/转视角即判删）而非永久。
3. **实施期必须实测核对的两处**（可能退化为 §13 失败模式）：① **床的 BE 挂在哪半**（1.21.11 的床在 STRIP
   表内有 typeId）——若 BE 只挂某半、而判据只判出另一半，则记录残留 ⇒ 幽灵；此时对策是"多格方块的记录按
   **整块所有格**修剪"；② 双格大箱半删后 `writeSingleOrMigrate` 的迁移时机（§13.3）。

## 10. 验证清单（实施后，游戏内）

1. **F1 复活回归**：敲掉告示牌 → 记忆世界消失；随后 (a) 重启、(b) 同维另处 BE 被改动触发文件重写，均
   **不复活**、无文字残留（§13.5 第 1 条正式化）；
2. 敲掉满格熔炉/木桶 → 不复活（修 v2.23 潜伏洞）；
3. **BE 非满块判删（原 F2 验收 → 改走 §15 加表，§14.4）**：活牌不误删；敲牌即删（含墙牌/站立牌）；换牌（改文字/换位置）走正向更新正常，不出现旧
   记录回放盖新牌；
4. **F3 容器**：敲掉箱子 → 与容器 reconcile 不对打：格不复活、物品记录随修剪消失（容器文件缺位）；容器
   方块仍在时照常覆写内容（回归）；
5. **F4 多格**：整床敲 → 双格同删、无半格残留；双格大箱敲半 → 幸存格单格化（正向）、**被敲半的记录已消失、
   幸存半的记录仍在**（按 pos 各删，§13.3）；另查半删后有无指向已不存在坐标的孤儿容器记录；
6. **注册表扫描 —— 已完成（2026-09-12，报告 §14）**：49 类型 / 201 方块按"形状 × 模型几何 × BE 渲染器"
   机械分桶 ⇒ 甲 44 / 甲0 3 / 乙1 6 / 乙2 17 / 丙 131；**F2 取消**，批 2 改为 **§15 加表**（§14.4）。
   复现 = 重跑 `scan_be_carriers.py`，分桶应不变（游戏版本升级后须重跑）；
7. 判据假阳性防护回归：活体贴边/抖动不误删；欠删类（横幅/被挡）留幽灵、由正向修正；
8. 兼容：无 v2.37 时（cells 无几何段）本版对 BE 非满块自动退化为欠删；旧文件加载无 WARN。

**以下 4 条为 §7.1 追加决策（G/G2/H/I/J）的验收，与上面 8 条同批执行：**

9. **G2（渲染距离钳制）**：把客户端渲染距离调到 **4 区块**，在 65~96 格处各放一个告示牌与一个箱子，走到
   "镜像里有、现实已不渲染"的位置 → **不得**被判删（证明二次距离过滤生效）；把渲染距离调回、走近 →
   恢复正常判定。**这是本次最重要的回归**（无 G2 则必然误删，且永不撤销）。
10. **G（容器延迟修剪）**：敲掉箱子后**立刻退出游戏**（K 窗口内）→ 记录仍在 ⇒ 重启出现**一次**幽灵、随后被
    删（§9 可接受瞬态）；正常走完 K 代际后再退出 → 重启**不复活**。另外：敲掉箱子后站定不动观测 N 个 poll →
    文件应在 K 代际后确实缺位（对照 `containers.nbt` 大小/条目）。
11. **I（不产生半态）**：构造"采集侧判删、记忆侧守卫拒删"的场景（如镜像该格已被换成不同方块）→ 采集侧
    **不得**修剪该 pos；记忆侧应打出 `M > deleted` 告警、采集侧"已修剪数 = 0"——两者同时成立才算护栏对。
12. **J（容器对打）**：敲掉箱子后静置 N 个 poll → 镜像**不得**出现周期 = poll 间隔的箱子闪烁；容器通道日志
    不得出现 place/delete 交替。同时回归冷启动语义：新世界/远处容器照常"先补壳"。

**以下 2 条为批 2 改为"§15 加表"后的验收（§14.4）：**

13. **加表后箱子/告示牌/床不再复活（本版头号目标，取代原 F2 验收 3）**：现实侧敲掉 **箱子（含铜箱/陷阱箱/
    末影箱）**、**告示牌（含墙牌）**、**床**各一 → 镜像与重启后均**不复活**；`containers.nbt` 中该 pos 的
    条目确实消失（箱子族的关键证据：**加表前它不产生任何 deletions**，§14.3 结论 2）。
    反向回归：**玩家在镜像世界里自己搭的箱子/告示牌不得被判删**（候选集只含 `terrain.appliedBlocks`，
    即"镜像回放体"——这是加表方案唯一的语义依赖，必须实测确认）。
14. **记忆侧执行路径已核实（前置核对取消，§14.6）**：`DeletionApplier:135-143` 已执行
    `terrain.signalLossDeletions` → `deleteSignalLossBlock:225-234`（守卫 `isSignalLossBlock`，与上报同源）
    ⇒ 加表一处即上报与执行同时生效。本条的验收改为**实测第 13 条**。

**以下 3 条为实施期（2026-09-12）新增的观测项 —— 都是"欠删方向的盲目必须可见"（同 §10 第 18 条原则）：**

15. **F1 修剪落点日志**（采集侧，实施记录 §16.3 第 11 条）：`[Vision] prune(F1): targets=… | be: pruned/keptByIdentity/
    noRecord | container: pruned/pending/cancelled/keptByIdentity (K=…)`，**只在有事发生时打**（任一计数非零）。
    读法：`be.pruned` = 本轮真删掉的 BE 记录数（第 1/3 条的直接证据）；`container.pending` 在 K 代际内应
    呈 1→2→…→K 的**递增**（第 10 条的在线版）；`keptByIdentity` 恒非零**不是**异常（身份不符 ⇒ 欠删），
    但它**每轮都非零且永不下降**才要查（说明某个 pos 上镜像块 id 与记录长期不符）。
16. **`deletions` 诊断行新增 `maxDist`**：G2 闸门的实际取值。渲染距离调小 / 门限收缩都会让判删数下降——
    不打出这个数，§10 第 9 条的"不得被判删"就会被误读成"识别变准了"。
17. **记忆侧 `refused` 失配告警**（`DeletionApplier`，决策 I 的半态检测器）：两个通道各自打出
    `… REFUSED by guard`。语义 = "判据说该删、守卫拒绝了" —— 若采集侧**同一轮**已修剪了该 pos 的记录，
    这就是 §7.1 决策 I 要防的**半态**（记录没了、镜像方块还在）。读数：第 11 条的"两者同时成立"即指
    **本告警非零 ∧ 采集侧该 pos 的 `be.pruned` 为 0**。
18. **乙1 六块不再欠删**（§17 补遗，2026-09-12 追加）：现实侧敲掉**漏斗 / 酿造台 / 比较器 / 阳光传感器 /
    幽匿感测体（含校定）**各一 → 镜像即时消失、重启不复活；`block_entities.nbt` 中对应条目消失
    （采集侧 `prune(F1)` 行的 `be.pruned` 非零）。反向回归同第 13 条：**玩家在镜像里自建的**这 6 类方块
    不得被判删（依赖同一条候选集语义 = `terrain.appliedBlocks`）。

**以下 4 条为 §18 补遗（2026-09-12 追加 —— BE 内容变更的发布缺口）—— 19 / 20 为必过项：**

19. **BE 内容收缩必须即时可见（本补遗头号目标）**：现实侧往营火放 4 只生兔肉 → 采集 → 镜像复现（现状
    已通过）→ 等烤熟弹出（约 30 s，或直接敲掉营火另算）→ 再采集 → **镜像端营火当场变空**，无需重启、
    无需重进世界、无需走远再回来。反例特征（区分本缺陷与"没再采集"）：`block_entities.nbt` 已是
    `Items: []` 而镜像仍显示食物 = 本缺陷；文件仍是旧内容 = 采集边界（v2.35 §9.1），不是本条。
20. **BE 内容变更必须落盘（19 的持久化对照）**：承上，**正常退出**镜像世界（触发存档，不要 kill 进程）
    → 重启 + 重进 → 营火**仍是空的**。19 过而 20 不过 = 只补了发包、漏了 `markUnsaved`（§18.5-A/B 缺一）。
    直接物证：`saves/MemoryWorld/region/r.-1.0.mca` 中该区块表头写入时间**晚于**本次采集，且条目为
    `Items: []`（`keepPacked` 的真假不影响判定，见 §18.8）。
21. **冻结不变量回归**：修复后重跑 §10 第 1~18 条里任意涉及红石/比较器/容器的反例——镜像世界里
    **红石逻辑不得因本次修复而活过来**（`sendBlockUpdated` 不发邻居更新；落盘刻意用
    `level.blockEntityChanged` 而非 `be.setChanged()`，后者会直接 `neighborChanged` 比较器，§18.5-B）。
    观测项：修复前后 `Deletion apply` / redstone 相关日志与方块状态无差异。
22. **日志可分辨性**：`Sync` 行必须能区分"新放置 / 内容重写 / 跳过"（§18.5-D）。验收读法：第 19 条
    过程中应看到 `rewritten` +1 而非仅 `placed` +1（**这正是本轮误判的那个读数**：旧格式把三种情况混成
    一个 `+1 placed`，见 §18.4 附注）。注意重启后 `applied` 是内存态、重启即空 ⇒ 全部 16 条都会重放
    （`rewritten = 16` 属**正常**，不是异常）；此时若营火**复活**，说明落盘没生效（§18.5-B 漏了）。


## 11. 涉及文件清单

| 侧 | 文件 | 动作 |
|----|------|------|
| 采集 | `ObjectResolver.java` | 改：resolve 在 terrain sync 后、BE/容器 store 落盘前插入 applyDeletions（**L343 之后、L346 之前**；原"L206-210 间"是 v2.37 落地前的行号，照抄会编译不过，§13.1）；另按 G2 传入 `maxDistBlocks`、按 H 传并集。**实施落点（§16.3 第 12 条）**：`buildPruneTargets(deletions, signalLossDeletions, cells)`（并集 + 身份表）→ `getStore().applyDeletions(dim, targets)` → `ContainerMemoryStore.get().applyDeletions(dim, targets, terrain.keySet())`，随后才是 `getStore().sync(...)` |
| 采集 | `VisionBlockEntityStore.java` | 改：新增 `applyDeletions(dim,pos)`；dirty→save 复用。**实施落点（§16.3 第 13 条）**：签名 `Map<BlockPos,String> expected`（value = 该格上报的 blockId，无身份段为空串）、方法内自行 `save()` ⇒ 其后的 `sync` 见到 `dirty=false` 不重复写 |
| 采集 | `ContainerMemoryStore.java`（+ Tracker 视形状） | 改：新增 `applyDeletions(dim, posSet, 代际)`（**延迟修剪**：`pendingDel` 计数 + 观测到活体即撤销，§7.1 决策 G）+ 置 dirty 强制 save；**同构，已有 `remove(dim,posKey)` 可复用**（§13.5）。**实施落点（§16.3 第 1~3 条）**：签名 `applyDeletions(dim, Map<BlockPos,String> expected, Set<BlockPos> observed)`；K 取自 `config/stevex/vision.json` 的 `containerPruneGenerations`（默认 4、下限 2、mtime 热重载）；`pendingDel` **只在内存**、不落盘 |
| 采集 | `DeletionJudge.java` | 判据逻辑不改（v2.37 已泛化 `testShaped`）；**新增 G2 的距离闸**：`judge` / `testShaped` 两个主循环各加一处"`距离 > maxDistBlocks` → 跳过"（与现有"可见集/相机格跳过"并列），入参 `maxDistBlocks` 由 `ObjectResolver` 按 `min(removalMaxRayDist, 渲染距离上界)` 传入。**实施落点（§16.3 第 4~6 条）**：公式收进 `renderDistanceGate(rd, removalMaxRayDist)`（单一来源，`max(0, …)` ⇒ rd ≤ 2 时门限 0 = 全欠删，有意保留）；度量用 `nearestDist(cam, pos)`（格最近点）；`maxDistBlocks` 追加进 `deletions` 诊断行 |
| 记忆 | `BlockStateUtil.java` | 改：**只动 `isSignalLossBlock` 的表**——加入 §14.2 的 **乙2 ∪ 丙 = 148** 个方块（≈15 个族，§14.4）；`isShapedDeletableContent` 第 ④ 步（**有 BE 即排除**）**一字不动**（F2 取消，§4.4/§14.3）。**实施落点（§16.3 第 10 条）**：148 个常量由 `scan_be_carriers.py --emit-java` 机械产出（字段名 148/148 命中）；表内注释写明"勿手写增删、升级游戏版本后重跑脚本"。**§17 补遗追加**：再入 **乙1 的 6 个**（同一张表、理由 ③）；javadoc 的"两类入表理由"改为**三类** |
| 记忆 | `MemoryCellReporter.java` | 改：加表后这些方块**自然走已有的信号缺失段**（`:292`，`blockId` 已预留）⇒ **无新增段、无格式改动**；原本打算的"BE 非满格并入几何段上报"**取消**。**实施落点**：**零结构改动**（只改两处过时注释：信号缺失段的族清单、以及"BE 非满块属于空气/INVISIBLE 那一列"的旧说法） |
| 工具 | `scan_be_carriers.py` + `scan_be_carriers.tsv`（工作区根 `MC_agent/`，与 `decompile_minecraft.py` 同处，**不进两个 mod 仓库**） | 新增（§14）：批 2 扫测工具，游戏版本升级后重跑即自动更新分桶；§14 的全部数字可复现。**实施期追加 `--emit-java`**（§16.3 第 10 条）：复用解析出的 `FIELD_TO_ID`，按 BE 类型分组机械产出乙2 ∪ 丙 的 Java 常量行（148/148 命中；解析不到字段名的行**显式报警**，不静默漏项）。**§17.5 再扩**：筛选改为 `乙2 ∪ 丙 ∪ 乙1`，乙1 单独一段（每行一个 + 尾注释给 BE 类型），总数打印 154/154 |
| 记忆 | `TerrainRestorer.java` | 改（**注释**）：`KEY_SIGNAL_LOSS_DELETIONS` 的 javadoc 原写"绊线 / 绊线钩格"，加表后已过时 ⇒ 改为"信号缺失族格 + 该族 v2.38 扩过两次"（§17.7；纯注释，零行为改动） |
| 记忆 | `ContainerMemoryApplier.java` | 改：`tick(level, deletionsThisGen, realityBlocks)`；空气 + **墓碑集** 命中 + 现实未重新观测到容器 → 跳过自足回放（§7.1 决策 J，**不是**当帧 guard）；顺带修正 `:219` 的错误注释（§13.6）。**实施落点（§16.3 第 9 条）**：`deletionsThisGen` **未采用**——决策 J 是会话级墓碑，帧级 deletions 参数由 J 取代（§7.1 决策 C 不再需要）⇒ 实际签名 `tick(level, realityBlocks)`；`realityBlocks` 只服务于墓碑的清除条件；新增 `tombstoneWithheld` 计数（被墓碑挡下的自足回放数） |
| 记忆 | `MemoryWorldManager.java` | 改：`CONTAINER.tick(level, deletionsThisGen, terrain.blocks)`（次序不变，两者在 CONTAINER.tick 前都已可得）。**实施落点**：实际为 `CONTAINER.tick(level, terrain.blocks())`（同上，deletions 参数取消） |
| 记忆 | `MemoryRestorer.java` | 改（**§18 补遗，2026-09-12，未实施**）：`place()` 补两个**发布**动作 —— **A** 发客户端 `level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)`（方块状态未变时 `Level.setBlock` 在 `Level.java:224` 就 `return false` 了，永远走不到 `sendBlockUpdated`）；**B** 落盘 `level.blockEntityChanged(pos)`（**刻意不用** `be.setChanged()` —— 它还会 `updateNeighbourForOutputSignal` 直接 `neighborChanged` 比较器，破坏 §7.9 冻结不变量）；**C** `loadStatic` 返回 null（BE 没装上）时 `place()` 返回 false（不再让指纹被记为"已应用"，§18.4-2）；**D** `Sync` 行拆成 `placed / rewritten / skipped`，让"沉默的生效失败"可见。验收 = §10 第 19~22 条 |
| 记忆 | `DeletionApplier.java` | 改：**执行守卫无需改动**——`deleteSignalLossBlock`（`:225-234`）的守卫 `isSignalLossBlock` 与上报**同源**，加表即同时覆盖上报与执行（§14.6 已核实）；只需：每次实删时向**会话级墓碑集**写入 pos（决策 J）+ 补 `M > deleted` 失配告警（决策 I）。**实施落点**：墓碑写入点在两条通道**共用**的实删出口 `clearBlock(...)`（一处即覆盖两道）；两通道**分开计数**后各打一条 `… REFUSED by guard` 告警（§10 第 17 条） |
| 记忆 | `RemovalTombstones.java`（**新文件**，§16.3 第 9 条） | 新增：会话级墓碑集（维度 → `pos.asLong()` 集合，`get()` 单例；`record/contains/clear/clearAll/size`）。清除条件 = 现实重新观测到该 pos 的**同 id** 容器，或服务器启动（`onServerStart` → `clearAll()`）|
| 文档 | v2.37 文档 §13 / 视觉api设计方案.md §7.13 / 已实现内容.md | 实施后状态同步 |

## 12. 版本与后续

v2.38（BE 载体方块减量），前置 v2.37（**已落地并验收**）。工作流：本文档审阅通过 → 按 §11 实施
（批 1：F1 + G/G2/H/I/J；批 2：§14.4 的 §15 加表）→ 更新 v2.37 文档 §13（附录 → 已实现）与
视觉api设计方案.md §7.13 / 已实现内容.md。
非目标（**BE 内容的独立"消失证明/更新"语义**）记入 §9/§2.2，长期已知边界
（**2026-09-12 追加**：这条边界**只指"不新增判据"**；既不等于"内容变更已经能用"——实机证明其发布端
是坏的，见 §18 —— 也不因 §18 而改变范围）。
**2026-09-12 变更**：原批 2 = F2（判据纳入 BE 非满格）**取消**，改为 §15 信号缺失表加表（§14.4）——
"横幅类无深度信号"不再是长期边界，它和箱子族/告示牌/床/旗帜/头颅一起进信号缺失表。

**2026-09-12 审阅通过（本条即工作流的"用户审阅"节点）**：审阅结论 = **认可 §14.4 的加表路线（F2 取消）**，
且 **批 1 与批 2 合并为一次实施**（"批"只是审阅期的分期，两条路线共用同一执行原语 F1，分开做会让批 2
先落地而 F1 未就位 ⇒ 加表产出的 deletions 无处可落，等于空转）：① 记忆侧 `SIGNAL_LOSS_BLOCKS` 加 148 个
方块；② 采集侧 F1 修剪 + 决策 H 并集；③ 护栏 G/G2/I/J —— 与验收第 13/14 条（§10）同批执行。
实施记录、实现期偏离与待验事项见 §16；同日追加的**乙1 补遗**（6 个方块永久欠删的修复）见 §17。
**2026-09-12 追加（乙1 补遗）**：本文档 §16.4 列的"乙1 仍欠删"已修 —— 那 6 个方块加入同一张
`SIGNAL_LOSS_BLOCKS`（理由 ③，§17.2），采集侧零改动；验收 = §10 第 18 条。

## 13. 核查记录（2026-09-12，实施前必读）

> **状态更新**：前置 v2.37 **已落地并验收**（含 §15 信号缺失族通道、cells v5）⇒ §12 的前置条件解除。
> 本节为方案审阅期的代码事实核查结果（核查基线 = v2.37 落地后的代码）。**结论层面无一条被推翻**，
> 但下列锚点/措辞/概念问题会直接误导施工。

### 13.1 行号锚点修正（§3.1 / §4.1 / §7B / §11 —— 照抄会插错位置）

`ObjectResolver.java` 被 v2.37 的几何段 + 信号缺失段撑开（约 +90 行）后文档未回填，锚点系统性下移：

| 文档锚点 | 声称内容 | 实际位置 | 偏差 |
|---|---|---|---|
| `:145-177` / `:158-177` | deletions 计算 | `:176-257`（`DeletionJudge.test` 于 `:194`；几何段 `:244`；信号缺失段 `:271-280`） | 下移 45~80 |
| `:193` | `biomeStore.sync` | `:328` | **+135** |
| `:206` | `getTerrainStore().sync(terrain, deletions, signalLossDeletions, …)` | `:343-345` | **+137** |
| `:208` | `getStore().sync(blockEntities, …)` | `:346-347` | **+138** |
| `:206-210`（§11 施工落点）/ `:158-210` | 插入两次修剪 | `:343-349` | **+137** |

**语义方向仍正确**（terrain 落盘之后、BE store 落盘之前），但 §11 的"L206-210 间"按字面施工会插到
deletions 变量尚未产生处 ⇒ **编译不过**。实际窗口 = `:343`（terrain sync 之后）～`:346`（BE sync 之前）。

### 13.2 语义更正三条（结论不变，机理措辞须改）

1. **`clearStale` 不是"该通道的删除能力"**（§1.1 表末列须改）。它只从已应用表删 pos
   （`MemoryRestorer:120-123`）；而 `sync` 的 `toPlace = !key.equals(applied.get(pos))`（`:219-226`），
   清掉后该 pos **必然进入下一次 toPlace**，`place` 对空气位又是**放行**的（`:244-247`）。
   ⇒ 在删除路径上它的实际作用是**促成一次重放**，不是"瞬时遗忘以阻止复活"；`block_entities` 通道此前
   **没有任何删除能力**（代码注释 `DeletionApplier:62/178/239`、`MemoryRestorer:32` 同源措辞亦错）。
   这**加强**了 F1 的必要性。修剪文件后该 pos 无记录 ⇒ `sb == null` ⇒ 不再重放，`clearStale` 变为无害，
   **无须删除该调用**。
2. **cells 几何段存的是 quad 面清单，不是"格内盒"**（§4.4/§5.1 措辞）。实际为
   `ShapedCell(BlockPos pos, String blockId, Quad[] quads)`（`ShapedCellData:63`），格内盒由采集侧从
   quads 派生（`resolveShapedCell`）。"无需改格式"的结论成立，但 BE 非满块的**可判性取决于其方块模型
   quad 能否在采集侧烘出**，不是"盒"的问题。
3. **§4.1 第 3 条"BE store 随后被本帧 sync 再落一次幂等覆盖"不精确**：`sync` 的落盘是 dirty 门控
   （`VisionBlockEntityStore:166-169`）。⇒ `applyDeletions` **必须自己置 dirty 并 `save()`**，不得依赖
   同帧 sync 代劳（否则"修剪在内存里生效、文件没变"，复活根因仍在）。

### 13.3 不可执行项一条

**§6 / §10 第 5 条的"BE 宿主格"概念在实现里不存在**：双格箱在采集侧是**每半各一条 per-pos 记录**
（`ContainerMemoryTracker:236-267`，键 = 该半自身坐标）。规则应改写为"**按 pos 各删**"——被敲半的记录随
该 pos 进 deletions 被修剪，幸存半的记录自然保留（其内容本就按该半槽位独立记录）。验收 5 改为核对
"**被敲半记录已消失、幸存半记录仍在**"，并另查"`writeSingleOrMigrate` 的迁移只在**再次开箱交互**时触发
（`:276-283`）⇒ 半删后是否留下指向已不存在坐标的孤儿记录"。

### 13.4 内部矛盾一处

**花盆**：§2.2 列为"明确排除 / 个别仍欠删"，§4.4 又列进"预计多数可判"。须择一（建议统一到 §4.4 的
扫测门控口径：先扫测再定，未定前归欠删）。

### 13.5 两条对方案有利的核实（降低实施风险）

1. `ContainerMemoryStore` 与 BE store **同构**——`byDim: Map<dim, Map<posKey, StoredContainer>>`
   （`:62`），且**已有 `remove(dimension, posKey)`**（`:96-102`，现调用方仅 double→single 迁移）。§4.3 的
   "须确认"可结案：`applyDeletions` 就是 `remove` 的循环包装，改动量比 §11 描述更小。`posKey` 两侧格式
   一致（均为 `"x,y,z"`，`VisionBlockEntityStore:286` / `ContainerMemoryTracker:373`）。
2. cells 信号缺失段**确有 `blockId` 字段**（写 `MemoryCellReporter:422-428`，读
   `MemoryCellsReader:158/343-374`）⇒ §2.2"加表即纳入、无需改格式"成立（该字段当前已用于诊断，非"预留"）。

### 13.6 一条会掩盖问题的既有错误注释

`ContainerMemoryApplier:219` 注释称"自建块只可能是非实心容器，不是 DELETION 候选，不会与减量冲突"——
**不成立**：`CONTAINER_FAMILY` 含 `furnace/barrel/smoker/dispenser/dropper/hopper/…`
（`ContainerMemoryTracker:69-73`），全是满格可遮光方块，**今天就在 DELETION 候选里**。且容器通道的复活是
**每轮无条件**（`MemoryConfig:81` `containerReconcileOnPoll=true` 为默认 → `ContainerMemoryApplier:219-233`），
比 BE 通道的增量重放更凶——§9 的"瞬态窗口"只覆盖了 BE 通道。该注释应删或改正，否则会被后人当成
"此处已无冲突"的保证。

### 13.7 本记录未覆盖 / 待决 → **已于 §7.1 定案（2026-09-12）**

- 审阅提的三项设计层要求已定案：**假阳性代价与护栏** → 决策 **G**（分通道：BE 即时修剪可自愈 / 容器 K=4
  延迟修剪 + 观测到活体即撤销）；**原语须覆盖 `signalLossDeletions`** → 决策 **H**（并集，且说明为何不违反
  §15.2）；**"不产生半态"依赖两侧守卫覆盖面一致** → 决策 **I**（修剪前块身份复核，把假设升为不变量）。
- **定案过程中新发现的第四项（原方案缺口）**：§5.3 的容器 guard 用"当帧 deletions"会**自取消**
  （镜像删格后该 cell 不再上报 ⇒ 下一帧 deletions 无此 pos）⇒ 容器通道按 poll 间隔对打。已定为决策 **J**
  （会话级墓碑集），§5.3 末已加修正注。
- 另新增 **G2**（渲染距离钳制）：未渲染区不写深度 ⇒ `zOpaque = ∞` ⇒ **持续性**假阳性，K 帧撤销机制救不了。
  这是 F1 改变代价模型后**必须同版到位**的主护栏（§7.1 决策 G2 / §10 验收 9）。
- §9.1 为本轮补齐的"BE 载体分类与本版结局"表（回答"能解决到什么程度 / 有无不可解物体"）。

### 13.8 一条未登记的 typeId（顺带发现）

`minecraft:flower_pot` **不在** `BlockEntityFieldPolicy` 的三张表（PASS/ALLOW/STRIP）里 ⇒ 走
[BlockEntityFieldPolicy.java:138-146](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/BlockEntityFieldPolicy.java#L138-L146)
的 **fail-closed** 分支：只留 `id/x/y/z` + 每类型一次 WARN。

- **无实害**：花盆的"内容"是 blockstate `potted`（植物种类），不在 BE NBT 里 ⇒ 记录本就无载荷，修剪不丢东西。
- **但也把 §13.4 的"花盆矛盾"从另一个角度解掉**：花盆的争议点只在**判据可判性**（模型足迹 / 多格语义），
  与内容无关 ⇒ 归 §9.1 的 B 类，按扫测门控处理即可。
- 建议：实施期把它补进 STRIP（无内容可采），顺手消掉那条启动 WARN。
- **2026-09-12 扫测补充**：vanilla 1.21.11 的 `BlockEntityType` 里 **没有** `FLOWER_POT`（该符号出现 0 次）
  ⇒ **花盆不是 BE 载体**，其上文那条 typeId 的来源应是历史文件数据而非当代注册表（实施期顺手核一下）。
  该结论只加强"无实害"：花盆删除时**没有任何 BE 载荷**存在。

## 14. 批 2 扫测报告（2026-09-12 —— §10 第 6 条的执行结果）

> **工具**：`scan_be_carriers.py`（工作区根 `MC_agent/`，与既有的 `decompile_minecraft.py` 同处、同约定——
> 都依赖 `decompiled_src_vf/`，不进两个 mod 的仓库）。跑法 `python scan_be_carriers.py`，产出同目录
> `scan_be_carriers.tsv`；**游戏版本升级后重跑即可**，本节的数字与分桶都是它的输出。
>
> **六路输入**（全部机械取自 `decompiled_src_vf/client`，不含人工方块清单）：
> ① `BlockEntityType.java` → BE 类型 ↔ 方块（含 `registerBed` 一类辅助方法体内的类名）；
> ② `Blocks.java` → 字段 → id / 方块类 / 属性；③ `ItemBlockRenderTypes.java` → 渲染层；
> ④ `blockstates/*.json` + `models/block/*.json` → 沿 parent 链的元素几何（`builtin/entity` = 无方块模型）；
> ⑤ 方块类源码 → `getShape`/`getCollisionShape` **沿继承链**的覆写者；⑥ `BlockEntityRenderers.java` → 渲染器登记表。
>
> **判据链（v2.37 §4.1）**：`isSolidOpaque`（`isShapeFullBlock && canOcclude`）→ 整格现段；
> 否则 `isShapedDeletableContent`：③ 满格 ⇒ 走现段；④ `level.getBlockEntity(pos) == null` ⇒ **有 BE 即排除**。

### 14.1 机械事实（49 个 BE 类型 / 201 个 BE 方块）

| 事实 | 计数 |
|---|---|
| **有**烘焙模型几何（模型链含 `elements`） | **50** |
| **无**烘焙模型几何（模型只有 `textures.particle`、或 `builtin/entity`、或模型文件不存在） | **151** |
| 形状满格（`getShape()` == `Shapes.block()`，含继承链解析） | **45** |
| 形状非满 | **155**（+1 空形状 = 移动活塞） |
| 渲染层 SOLID / CUTOUT | 172 / 29 |
| **BE 类型登记了渲染器**（`BlockEntityRenderers`） | **27/49 类型，覆盖 176/201 方块** |
| 其中**非满格 + 有几何 + 有渲染器**（F2 的"足迹 ⊄ 模型"危险区） | 17 |

### 14.2 结局分桶（201 个方块一个不漏）

| 桶 | 定义 | 数量 | 结局 |
|---|---|---|---|
| **甲** | 形状**满格** | **44** | 今天已被整格现段覆盖（`isSolidOpaque` / `isFullTransparentCell`）⇒ **本版无判据工作** |
| **甲0** | `RenderShape.INVISIBLE`（零足迹） | 3 | 不入任何段（v2.37 §4.1 第 2 步已修） |
| **乙1** | 非满格 + 有几何 + **无**渲染器 | **6** | **F2 唯一可安全纳入者** |
| **乙2** | 非满格 + 有几何 + **有**渲染器 | **17** | F2 会**误删活体** ⇒ 只能 §15 加表 |
| **丙** | 非满格 + **无**几何 | **131** | 几何过滤恒 0 票（v2.37 排除表第 176 行的运行时自检）⇒ 天生欠删 ⇒ 只能 §15 加表 |

- **甲（44）**：`barrel`、`furnace`/`smoker`/`blast_furnace`、`dispenser`/`dropper`/`crafter`、
  `chiseled_bookshelf`、`beehive`/`bee_nest`、`jukebox`、`sculk_catalyst`、`sculk_shrieker`、`creaking_heart`、
  `suspicious_sand`/`suspicious_gravel`、`command_block`×3、`jigsaw`、`structure_block`、`test_block`、
  `test_instance_block`、`spawner`、`trial_spawner`、`vault`、`beacon`、**`shulker_box`×17（关盖态）**；
- **乙1（6）**：`brewing_stand`、`comparator`、`daylight_detector`、`hopper`、`sculk_sensor`、`calibrated_sculk_sensor`；
- **乙2（17）**：`bell`、`campfire`/`soul_campfire`、`enchanting_table`、`lectern`、`shelf`×12；
- **丙（131）**：**`chest`×9（含全部铜箱）/`trapped_chest`/`ender_chest`**、`sign`×24、`hanging_sign`×24、
  `banner`×32、`bed`×16、`skull`×14、`decorated_pot`、`conduit`、`copper_golem_statue`×8。

### 14.3 三条结论（含对 §4.4 / §9.1 的更正）

1. **§4.4 的覆盖预判是反的，须整段改写**。§4.4 预计"告示牌/悬挂牌/头颅/花盆/装饰陶罐/钟/讲台/雕纹书架/床/
   箱子等**本体几何清晰**、多数可判"。实测：**这些方块的模型文件里没有 `elements`**——`chest.json`/`bed.json`/
   `decorated_pot.json`/`conduit.json`/`oak_sign.json` 只有 `{"textures":{"particle":…}}`，
   `white_banner.json`/`skeleton_skull.json` **根本不存在**；它们的几何在 **BE renderer** 里（27 个类型有渲染器）。
   ⇒ F2 的几何过滤对它们**恒 0 票**。真正在起效的是 v2.37 排除表第 176 行的"运行时自检 ⇒ 自然欠删"，
   而不是 §4.4 的"预计多数可判"。（`shulker_box` 同样无几何，但**形状满格** ⇒ 走整格现段，不受此影响——
   扫测的 2×2 分桶正是为了不把这两件事混为一谈。）
2. **§9.1 A 类"今天即可删"对箱子族不成立**。`ChestBlock.getShape` = `Block.column(14.0,0.0,14.0)`
   （`EnderChestBlock` 同，`TrappedChestBlock` 继承）⇒ **非满格**；非满格 + 有 BE ⇒ 被第 ④ 步排除
   ⇒ **箱子族今天在任何段之外 ⇒ 不产生 deletions ⇒ F1 对箱子没有输入**。容器族里真正满格的只有
   **`barrel` 与 `shulker_box`（关盖态）**。→ §9.1 该行须改为"依赖 §15 加表"。
3. **F2 已无实施价值**：净收益 = **6 个方块**（乙1），且其载荷**全部不可镜像**（`brewing_stand` 的 Items、
   `hopper` 的 Items 都在 STRIP 表；`comparator`/`daylight_detector`/`sculk_sensor` 无内容可采）；
   对 **148** 个方块（乙2+丙）要么**无效**（无几何）、要么**有害**（有渲染器 ⇒ 足迹 ⊄ 模型几何 ⇒
   几何低估 ⇒ **误删活体**，正是 v2.37 §4.1 第 4 步那条"唯一方向错的一类"红线）。且 F2 必须放开 v2.37
   唯一的**自动**安全护栏 ④，等于把 v2.37 §7 决策 A 明令删掉的"白名单式方块族清单"重新引回来
   （每升一个游戏版本都要重验"足迹 ⊆ 模型几何"）。

### 14.4 批 2 改为"§15 信号缺失表加表"（同一执行原语，采集侧零判据改动）

§2.2 早已留好这条路：**无深度信号的族走 v2.37 §15 状态直读通道**，"原语就位后**加表即纳入**"，且 cells
信号缺失段已预留 `blockId` 字段（无需改格式）。扫描给出该表的**确切内容 = 乙2 ∪ 丙 = 148 个方块**
（按方块族的写法 ≈ 15 个族：chest 族 / sign / hanging_sign / banner / bed / skull / decorated_pot /
conduit / copper_golem_statue / bell / campfire / enchanting_table / lectern / shelf；实现时按 `Block`
实例入表，与 `BlockStateUtil` 同源）。

| 侧 | 动作 |
|---|---|
| 采集 | **零判据改动**（`SignalLossCorrector` 已就位：`isLoaded` 守卫 + `getBlockState(pos).isAir()` → absent） |
| 记忆 | `BlockStateUtil.isSignalLossBlock` 的表加入上述族（一处表；reporter/applier 同源） |
| 采集 | **决策 H 由"可选优化"升为必需**：`signalLossDeletions` 今天**不并入 `deletions`**（`ObjectResolver:271-280` 单列），而 F1 的修剪只吃 `deletions` ⇒ **不并集就修剪不到 BE/容器记录**，加表等于白加 |

- **语义与现通道一致**：候选集仍来自记忆侧上报段（`terrain.appliedBlocks` ⇒ 只判**镜像回放体**，不动玩家
  自己在镜像世界里搭的东西），`visible`（本帧观测集）双保险跳过照旧；
- **顺带收益**：状态直读与可见性无关 ⇒ **"永久遮挡 ⇒ 欠删"这条 v2.37 固有边界对这批方块消失**；
- **代价与护栏不变**：加表产出的 deletions 走的是**同一条** F1 原语 ⇒ 决策 G/G2/I/J 的护栏对容器族
  **一字不改地适用**（容器载荷不可重建这一条性质没有因为换证据通道而改变）；
- **执行侧已核实（见 §14.6）**：记忆侧的方块删除执行**已随 v2.37 落地**，且守卫与上报同源 ⇒ 加表一处即
  「上报 + 执行」双覆盖。**加表路线的剩余工作量只有采集侧文件修剪（F1 + 决策 H）与护栏（G/G2/I/J）。**

### 14.5 本报告的复现与边界

- 复现：`python scan_be_carriers.py`（改游戏版本 ⇒ 重跑 ⇒ 分桶自动更新）。分桶是**三输入机械判定**，不含
  人工名单；脚本对"类名解析不到"的行会**显式报警**（当前为 0 行），不会静默错分。
- 边界：本报告只回答"**走哪条通道可判**"，不回答"判据误报率"。乙1 的 6 个方块虽然几何可判，**仍未**纳入
  本版（收益 0、载荷 0）——它们与其余方块一样欠删。**2026-09-12 追加**：原文"直到 §15 加表把它们一并
  纳入"是**错的**——§14.4 定的加表范围只有 `乙2 ∪ 丙`，乙1 并不在其中，故它们不会被加表顺带纳入。
  该缺口由 **§17 补遗**单独修（走同一条信号缺失表，理由见 §17.2）。
- §14 的结论**不改 §7.1 的 G/G2/H/I/J**；唯一的状态变化是 H（并入集）从"优化项"变成"批 2 的必要条件"。

### 14.6 执行侧核实（2026-09-12，加表路线的落点确认）

加表路线要求"上报即执行、执行不另设门槛"。核对结果（`stevex-test-template-1.21.11`）：

| 落点 | 事实 | 结论 |
|---|---|---|
| 上报 | `MemoryCellReporter.java:292` → `signalLoss.put(pos, blockId)`，段格式 v5 已含 `blockId`（`:85`） | 加表即上报，**无格式改动** |
| 采集侧判据 | `SignalLossCorrector.correct(level, cells.signalLossCells(), terrain.keySet())`：`isLoaded` 守卫 + `getBlockState(pos).isAir()` → absent | **零改动**（不认方块族） |
| 记忆侧执行 | `DeletionApplier.java:135-143` 逐 pos → `deleteSignalLossBlock:225-234`，守卫 = `BlockStateUtil.isSignalLossBlock` | **无需改动**：守卫与上报**同源** ⇒ 一处表同时覆盖两侧 |
| 决策 I（不产生半态） | `deleteSignalLossBlock` 只删"镜像里当前确实是该族方块的格"（`:130-131`/`:221-223` 注释明写"记忆侧只能删自己确实持有的东西"） | 该通道**已满足**决策 I 的块身份复核；本版只需补 `M > deleted` 告警 |
| 定义域互斥 | `BlockStateUtil.java:133`：`isShapedDeletableContent` 第 ⑤ 步 = `!isSignalLossBlock` | 加表后这些方块**自动退出几何谓词** ⇒ 与 §4.4/F2 路线天然不重叠，也不会与 `deleteBlock` 抢格（`DeletionApplier:216-219`） |
| 实体判定不受影响 | `DeletionApplier:147-151`：`provenEmpty` 刻意不含 `signalLossDeletions` | 加表**不改变**该边界（"该格方块没了"对实体占用零信息量），无需改 |

**⇒ 加表路线的最终落点收敛为三处**：① 记忆侧 `SIGNAL_LOSS_BLOCKS` 加 148 个方块（`BlockStateUtil.java:98`）；
② 采集侧 F1 修剪 + 决策 H 并集；③ 护栏 G/G2/I/J。**没有"改判据"这一项**——这也是它比 F2 更小的原因。

## 16. 实施记录（2026-09-12，批 1 + 批 2 合并实施）

> **编号说明**：本章原拟编为 §15，与全文多处"§15 加表 / §15 信号缺失表"（指 v2.37 文档的 §15
> 信号缺失族通道）**撞号**，故改为 §16。§17 为同日追加的乙1 补遗。

> 触发 = §12 的审阅通过条；基线 = §14.6 的"三处落点"。本节记**实施事实与实现期偏离**，
> 判据/决策本身（§7.1、§14.4）一字未改。

### 16.1 状态一览

| 落点 | 状态 | 证据 |
|---|---|---|
| ① 记忆侧 `SIGNAL_LOSS_BLOCKS` 加 148 个方块 | **已改** | `BlockStateUtil.java`；由脚本机械产出（§16.3 第 10 条） |
| ② 采集侧 F1 修剪 + 决策 H 并集 | **已改** | `VisionBlockEntityStore.applyDeletions` / `ContainerMemoryStore.applyDeletions` / `ObjectResolver.buildPruneTargets` + 调用次序（§16.3 第 12 条） |
| ③ 护栏 G / G2 / I / J | **已改** | G：`ContainerMemoryStore` 延迟修剪（§16.3 第 1~3 条）；G2：`DeletionJudge.renderDistanceGate/nearestDist`（第 4~6 条）；I：两 store 的身份复核 + 记忆侧 `refused` 告警（第 7、8 条）；J：`RemovalTombstones` + `ContainerMemoryApplier.tick`（第 9 条） |
| 编译 | **两个 mod 均通过** | `./gradlew compileJava --offline`（采集侧、记忆侧各一次，**离线**、零错误）。编译期发现并修正一处签名：`renderDistanceGate` 的 `removalMaxRayDist` 必须是 `double`（`cells.maxRayDist()` 是 double，取 int 会"可能有损失"） |
| 游戏内验收（§10 第 1~18 条） | **未执行** | 见 §16.4 |
| 文档改动提交 | **未提交** | `steveX` 仓库当前 10 改 + 1 新增（`RemovalTombstones.java`）全部工作区态 |
| 实施期附带修正 | **已改** | `ContainerMemoryStore` 类注释里"唯一写者"（第 14 条）、K 的计数口径（第 15 条） |

### 16.2 采集侧一帧内的新次序（v2.38 后）

```
resolve(...)
  ├─ 判据四段：main / translucent / shaped / signalLoss  → deletions, signalLossDeletions   ← 加 G2 门限（第 4~6 条）
  ├─ VisionTerrainStore.sync(terrain, deletions, signalLossDeletions, …)   ← terrain.nbt（并列键不变，§15.2/§7.13）
  ├─ buildPruneTargets = deletions ∪ signalLossDeletions（带身份，第 12 条）
  ├─ VisionBlockEntityStore.applyDeletions(dim, targets)      ← 即时修剪 + 自落盘（第 13 条）
  ├─ ContainerMemoryStore.applyDeletions(dim, targets, terrain.keySet())  ← 延迟 K 代际修剪（第 1~3 条）
  ├─ VisionBlockEntityStore.sync(blockEntities, …)            ← 增量 union（dirty 已 false，不重复写）
  └─ VisionEntityStore.sync / VisionBiomeStore.sync
```

**"同一 resolve 内"已由代码结构保证**：修剪在两 store 的 sync **之前**、terrain 落盘**之后**——即
"判据说的消失"与"负载的消失"在同一帧、同一次文件写序里落地（§3.3 的原子性来源不变）。

### 16.3 实现期偏离与澄清（逐条对照 §11 原文）

1. **代际 K 的定义 = 一次 `applyDeletions` 调用 = 一次 resolve = 一次采集帧**（不是"记忆侧 poll 一轮"）。
   §11 原文的"代际"未定义，实施时钉在这里；K 代际 ≈ K 帧 ≈ K × 帧间隔（poll 周期远大于帧间隔
   ⇒ 按帧计只会**更快**到达 K，方向安全）。
2. **K 的载体 = `config/stevex/vision.json` 的 `containerPruneGenerations`**，默认 **4**、下限 **2**、
   mtime 门控热重载（与 `DecorativeConfig` 同款约定）；文件缺失 / 键缺失 / 非法值一律**保持当前值**，
   不告警、不中断。下限 2 的理由：K=1 等于当帧即删，"观测到活体即撤销"的窗口被抹掉 ⇒ 决策 G 失效。
3. **`pendingDel` 只在内存、不落盘**（§11 未写）。重启后计数从零 ⇒ 最坏把修剪再推迟 K 代际（欠删方向）。
   **计数只看 `terrain.keySet()`**（本帧观测集），**不得**看"该格再次出现在 deletions 里"——镜像侧删格后
   记忆侧就不再上报该 cell，deletions 会自然消失 ⇒ 靠它计数**永远数不到 2**（决策 G 写死的点）。
4. **G2 门限公式收进 `DeletionJudge.renderDistanceGate(rd, removalMaxRayDist)`（单一来源）**，
   `ObjectResolver` 只取值不重算。`max(0, …)` ⇒ **rd ≤ 2 时门限为 0 ⇒ 全格不可判（全欠删）**：
   这是有意保留的**保守退化**（宁可什么都不删，也不删界外活体），**不是**待修缺陷。
5. **G2 的度量 = 格最近点**（`nearestDist`），不是中心 / 远点。安全性**不来自**度量取舍，而来自门限里的
   **2 区块余量**：最近点都在门内的格，其所在区块必已渲染（格内跨度 ≤ √3 格 < 1 区块）⇒ 只判
   **确定已渲染**的格。改用远点会把大量真格误跳（无谓的欠删），改用中心则失去"确定"性质。
6. **G2 的跳过不计入 `shapedUnjudged`**（§11 未写）。该出参只记 δ 窗口闭合；两件事的诊断含义不同
   （"判据不可判" vs "本就不该判"），混在一起会让 §10 第 18 条的读数失真。
7. **决策 I 的 I′ 实现（身份复核范围）**：只在**携带 `blockId` 的段**做逐字比较——信号缺失段
   （`SignalLossCell`）与几何段（`ShapedCell`）；main / translucent 段在 cells 文件里只有裸坐标
   （零足迹设计）⇒ 身份为**空串**，持久层对空串**不作**额外要求。理由：这些段的上报谓词与记忆侧执行
   守卫**同族**，"上报 ⇒ 记忆侧确实持有该方块"**构造性成立** ⇒ 空串已满足决策 I 要的保证；若改成
   "空串即拒绝修剪"，F1 对绝大部分方块**永不生效**（等于取消本版）。另有两条从属设计：**身份不符 ⇒
   连 `pendingDel` 计数也不计**（否则 K 代际后仍会删掉一个身份不符的记录）；同 pos 多段时**保留非空身份**。
8. **决策 I/J 在记忆侧的落点**：`DeletionApplier` 两条通道**分开计数**后各打一条 `… REFUSED by guard`
   告警（原来只打合计，会被相机格快路径掩盖——合计差 = refused 与 deleted 混在一起）；墓碑写入点放在
   两条通道**共用**的实删出口 `clearBlock(...)`，一处即覆盖两道（§11 原文只说"每次实删时写入"，未钉位置）。
9. **决策 J 的落地 = 新文件 `RemovalTombstones.java`**（会话级、维度 → `pos.asLong()` 集、`get()` 单例；
   API `record/contains/clear/clearAll/size`）。**清除条件有且只有两个**：现实重新观测到该 pos 的
   **同 id 容器**（`realityBlocks` 即 `terrain.blocks()`，在 `ContainerMemoryApplier` 内比对）、服务器启动
   （`onServerStart → clearAll()`）。**为何必须是会话级**：帧级 `deletions` guard 会自我抵消并在 poll 周期上
   **振荡**（删→下一帧记录仍在→再删）。**随之而来的签名变化**：`ContainerMemoryApplier.tick` 与
   `MemoryWorldManager` 的调用**不传 `deletionsThisGen`**（§11 原文写了）——**决策 C 被 J 取代**，
   帧级候选集不再是所需输入；实际签名 `tick(level, Map<BlockPos, TerrainRestorer.TerrainBlock> realityBlocks)`，
   新增 `tombstoneWithheld` 计数（被墓碑挡下的自足回放数，供 §10 第 12 条读）。
10. **加表的 148 个常量由 `scan_be_carriers.py --emit-java` 机械产出**（复用脚本里解析 `Blocks.java` 得到的
    `FIELD_TO_ID` 反查表；按 BE 类型分组、每行 3 个；字段名 **148/148 命中**，解析不到的行会**显式报警**）。
    表内写死了重跑义务（"升级游戏版本后重跑脚本，勿手写增删"）。**`MemoryCellReporter` 零结构改动**：
    148 个方块自然落进已有的信号缺失段（`:292`，cells v5 已含 `blockId`）；改的只是两条**已经过时**的注释
    （"BE 非满块不纳入任何段"、"别的方块由正向通道覆盖"）。
11. **F1 诊断行（新增，§10 第 15 条）**：`[Vision] prune(F1): …`，只在有事发生时打
    （`pruned`/`kept`/`pending`/`cancelled` 任一非零）。`noRecord`（该格在持久层本就没有记录）**不**计入
    触发条件——它遍历绝大多数格，不是异常。
12. **`ObjectResolver` 的落点**：新增私有 `buildPruneTargets(deletions, signalLossDeletions, cells)` →
    `Map<BlockPos,String>`（决策 H 的并集 + 决策 I′ 的身份表）；调用次序见 §16.2。并集实现用
    `merge(..., 保留非空身份)`：段之间按设计互斥，此处只为防御"同 pos 两段同判"。
13. **`VisionBlockEntityStore.applyDeletions` 的签名与落盘**：`(String dimension, Map<BlockPos,String> expected)`
    ——value = 该格本代际上报的 blockId（无身份段为空串）；方法内**自行 `save()`**（F1 的"同帧落盘"承诺
    在此兑现），其后的 `sync` 见到 `dirty=false` **不重复写** ⇒ **一次修剪 = 一次整文件写**，不会每帧发生。
14. **`ContainerMemoryStore` 的类注释已改（"唯一写者"不再字面成立）**：F1 给它加了**第二个写路径**
    （`applyDeletions`），与 §5.2.2 定案 A 原文冲突的是"唯一 / 低频"两条措辞。**"无竞态"的结构理由不变**：
    两条路径同在**一个线程**上——交互提交来自屏幕关闭时，resolve 经 `Minecraft.getInstance().execute(...)`
    派发到渲染/客户端主线程（`VisionApi`）。故写序列仍是单线程、整文件 read-modify-write 无需额外同步；
    变化的只是"低频"（修剪为**条件写**：有判删才写）。**未改存储格式、未加锁、未引入 async**。
15. **`applyDeletions` 的计数口径（K 的边界）**：第 1 次判删即计数 1 ⇒ **第 K 次**连续判删（且期间未观测到
    活体）才真删 ⇒ 实际延迟 = K−1 个代际 + 当前帧。§10 第 10 条的"K 代际后文件缺位"按此口径读。

### 16.4 尚未验证 / 待办（诚实清单）

- **游戏内验收一条未做**：§10 第 1~17 条全部待执行（含新增观测项 15~17）。本版**只有编译期证据**
  （两 mod `--offline` 通过），**没有任何运行期证据**——尤其 §10 第 9 条（G2）与第 13 条（加表后箱子/
  告示牌/床不复活）是本次头号与次号目标，必须实测。
- **K = 4 是设计值、不是实测标定值**：第 10 条验收会给出"K 代际后文件确实缺位"的在线读数，据其可调
  `containerPruneGenerations`（改配置即生效，无需重编译）。
- **已知未覆盖**：`containers.nbt` 的记录删除后，**指向已不存在坐标的孤儿记录**（§10 第 5 条末尾）由本轮
  修剪按 pos 覆盖，但仍建议在第 5 条实测时顺带核对；乙1 的 6 个方块（`brewing_stand`/`hopper`/
  `comparator`/`daylight_detector`/`sculk_sensor` 等）**仍欠删**（§14.5 边界，本轮未纳入）
  —— **同日已追加修复，见 §17**。
- **状态同步未做**（§11 末行）：v2.37 文档 §13（附录 → 已实现）、`视觉api设计方案.md` §7.13、`已实现内容.md`
  三处的状态更新**留到验收通过后**一次性做——本版尚只有编译期证据，现在就写"已实现"会把未验证项写成事实。
- **提交**：本轮改动（10 改 1 新增）与本文档同处工作区**未提交**——按工作流等验收结论后再落 commit。

### 16.5 回归修复：容器通道 terrain 判空（2026-09-12 实机启动崩溃）

**现象**：记忆世界一启动即崩（`run/crash-reports/crash-2026-09-12_19.13.32` 与 `_19.30.11` 两份同一处）：
`NullPointerException: Cannot invoke "TerrainRestorer$TerrainData.blocks()" because "terrain" is null`
→ `MemoryWorldManager.onServerTick(MemoryWorldManager.java:383)`。世界能加载、一进 tick 循环就死。

**根因（本版引入的回归）**：§7.1 决策 J 把 `CONTAINER.tick` 的入参从"无"改成
`terrain.blocks()`，但**没判空**。而 `TerrainRestorer.tick` 返回 null 是**常规返回、不是异常**：
未到 `pollIntervalTicks` 周期（`TerrainRestorer:170`）、该维还没有数据（`:207`）、本代际该维已交付
（`:208`）三条都会返回 null ⇒ 崩溃在第一处非 poll tick 必然发生（与档案里的多次重现一致）。

**修法（一处）**：`CONTAINER.tick(level, terrain == null ? null : terrain.blocks())`——遵循该入参
**本来就写在 javadoc 里的契约**（`ContainerMemoryApplier:130` "可为 null = 该帧无视觉数据 → 一律不回放
墓碑格"，`:255` 显式判空），与 `DeletionApplier.apply` 的 `terrain == null → return`（`:95`）同一约定。
判空语义正确：无新鲜现实观测 ⇒ 无从解除墓碑 guard ⇒ 本帧不回放（正是设计要的行为，不是降级）。

**为何编译期没拦住**：`TerrainRestorer.tick` 的返回值只有 javadoc 说"可为 null"，**没有 `@Nullable`
注解**（`ContainerMemoryApplier.tick` 的入参同样只在 javadoc 里写）。加注解可让这类错误在编译期可见
——属可选加固，未纳入本轮回合。

**顺带核查**：`MemoryWorldManager.onServerTick` 内**仅此一处**解引用 terrain（已 grep 全类）；
`DELETION.apply(level, terrain, …)` 自身判空。另两份更早的崩溃档案不属此因：
`crash-2026-09-12_10.08.47` 是读半截 `terrain.nbt`（gzip EOF）——当前树里 5 个 NBT 读取器
（`TerrainRestorer`/`MemoryRestorer`/`ContainerMemoryApplier`/`EntityRestorer`/`BiomeRestorer`）
**全部** `catch (IOException) → return null`（半截重试逻辑自 2026-08-29 起就在），故该崩溃对应更早的
构建，现树内不可复现；若再现再查。

## 17. 乙1 补遗（2026-09-12 追加 —— 6 个方块永久欠删的修复）

> 触发 = §16.4 的"已知未覆盖"里那条乙1 欠删；范围**超出** §14.4 定的加表范围（`乙2 ∪ 丙`），
> 故单立一章而不是塞进 §14/§16。路线仍是同一条信号缺失表，采集侧零改动。

### 17.1 问题：三处谓词合围，一个都不放行

| 段 | 谓词 | 对乙1 的取值 |
|---|---|---|
| 几何段 | `isShapedDeletableContent` | 第 ④ 步"镜像该格**无** BE"⇒ 乙1 是 BE 方块、镜像里带 BE ⇒ **false** |
| 整格段 | `isSolidOpaque` / `isFullTransparentCell` | 非满格 ⇒ **false** |
| 信号缺失段 | `isSignalLossBlock` | 不在表内 ⇒ **false** |

⇒ 落进 `MemoryCellReporter` 的**"其余"列**（该列的原意 = 空气 + `RenderShape.INVISIBLE`，**零足迹**）。
但乙1 **不是零足迹**：它们有模型几何、照常渲染 ⇒ 现实侧敲掉后镜像里留下**可见幽灵且永不消失**，
其 BE 记录也**永不被 F1 修剪**（该格从不进 `deletions` / `signalLossDeletions`）。这不是"少删了没事"，
而是 §1.1 那条"记录累积 ⇒ 复活"的机理在这 6 个方块上原样保留。

受影响 = §14.2 的 **乙1 全桶（6 个）**：`brewing_stand` / `comparator` / `daylight_detector` / `hopper` /
`sculk_sensor` / `calibrated_sculk_sensor`。扫描明细（`scan_be_carriers.py` 乙1 段）确认三输入：
**非满格 + 有模型几何 + 无渲染器**（`noOcclusion`/`lightLevel` 等属性不影响分桶）。

### 17.2 方案 A（**采用**）：加入信号缺失表 —— 第三种入表理由

现表的 javadoc 只写了**两类**入表理由（① 不写已读深度场；② 有 BE 且非满格、几何在 BE renderer 里或不存在）。
乙1 属于**第三类**：**几何可判，但没有任何段收它们**（不是"判据不可用"，而是"谓词合围"）。故本补遗
在同一张表上追加理由 ③，并说明为什么**不**改成走几何段：

1. **乙1 的形状恰是几何判据最弱的一类**：`comparator` / `daylight_detector` 是 y ≈ 1/16 的平板、
   `sculk_sensor` 是贴地平面 —— 正是 v2.39 决策 Q 的 δ 可行窗口与"占屏 < 2 像素"最先失明的形状。
   走几何段在**远距直接窗口闭合**（按构造不可判）、**近距才可能判**；而状态直读**与距离、像素数、
   渲染配置全无关**。⇒ 赌"几何更准"在这 6 个方块上不成立。
2. **状态直读无任何渲染依赖**：不读深度、不读 PBO、与 Fabulous / translucentEnabled 无关，也不需要
   判据场路由与 α 阈值 ⇒ 少一整类失败模式。
3. **零新增代码路径**：加表是本版已经建成并复用过的机制（§14.4）——采集侧零改动、无新守卫、
   无格式改动（cells v5 的 `blockId` 已预留），F1 / G / G2 / I / J **一字不改地适用**。
4. **定义域仍互斥**：入表即被第 ⑤ 步（`!isSignalLossBlock`）挡出几何谓词，与 §14.6 的论证同源。

### 17.3 方案 B（**否决**）：把第 ④ 步改精确为"有渲染器的 BE 才排除"

- 做法：`level.getBlockEntity(pos) == null` → "镜像无 BE **或** 该 BE 类型**无渲染器**"（渲染器存在性在
  客户端查 `BlockEntityRenderers`），于是乙1 进几何段。它确实能**精确**只放行乙1（甲走第 ③ 步、
  乙2 仍被排除），而且是**规则式**而非手工名单——看上去比 A 优雅。
- 否决理由：
  1. 这**就是 F2 的窄化复活**——§14.3 已定案 F2 的净收益只有这 6 个方块（载荷 0），代价是放开 v2.37
     **唯一**的自动安全护栏 ④；
  2. 见 §17.2 第 1 条：乙1 的形状是几何判据的**弱区**，改走几何段拿不到更好的结果（多半仍然欠删），
     却要多验证一条判据路径（判据场路由 + α 阈值 + δ 窗口，逐方块）；
  3. **架构污染**：`MemoryCellReporter` 跑在**服务端上下文**（`ServerLevel`）里，为它引入客户端类
     `BlockEntityRenderers` = 把"客户端怎么渲染"变成持久层分类的依赖，在专用服务端环境直接不可用；
  4. 放宽第 ④ 步需要重新论证"渲染足迹 ⊆ 模型几何"（每个游戏版本都要重验），而**加表完全绕开这个论证**。

### 17.4 风险复核（逐条给结论）

| 复核项 | 结论 |
|---|---|
| 载荷是否可丢 | **零载荷**：6 个全在 `BlockEntityFieldPolicy` 的 **STRIP** 表（`hopper`/`brewing_stand` = 表 A 容器族；`comparator`/`daylight_detector`/`sculk_sensor`/`calibrated_sculk_sensor` = "无 NBT 机制型"）⇒ `block_entities.nbt` 里它们的记录**本就没有内容** ⇒ 即时修剪（决策 G 的 BE 侧）**不丢任何东西** |
| 物品载荷（`hopper`/`brewing_stand`） | 其 Items **不在** `block_entities.nbt`（STRIP 已剥）；若被交互通道采过则落在 `containers.nbt` ⇒ 由**决策 G 延迟修剪 + 决策 J 墓碑**保护，与箱子族完全同路（§16.3 第 9 条），本补遗不新增风险 |
| 误删风险 | 状态直读的"缺席"= **区块已加载 ∧ 非 `VOID_AIR` ∧ 读到空气**（`SignalLossCorrector` 三重守卫，次序不可换）⇒ **不存在 G2 那一类脆弱性**（不依赖渲染距离/遮挡/像素数）；`visible` 双保险照旧 |
| 水logged 情形 | `comparator` / `sculk_sensor` / `calibrated_sculk_sensor` 可含水：现实读到水（非空气）⇒ `stillPresent` ⇒ **不裁决**（欠删，安全方向）；镜像侧含水幽灵由**正向更新**覆盖（与今天一致，无回归） |
| 定义域互斥 | 第 ⑤ 步 `!isSignalLossBlock` ⇒ 入表即退出几何谓词，三段仍互斥 |
| 采集侧 | **零改动**（`SignalLossCorrector` 不认方块族，只读状态） |

### 17.5 实施落点

| 侧 | 文件 | 动作 |
|---|---|---|
| 记忆 | `BlockStateUtil.java` | `SIGNAL_LOSS_BLOCKS` 追加 6 个（第 3 组，脚本产出）；javadoc 的"两类入表理由"→**三类**（新增理由 ③） |
| 工具 | `scan_be_carriers.py` | `--emit-java` 的筛选 `乙2 ∪ 丙` → **`乙2 ∪ 丙 ∪ 乙1`**，乙1 单独一段（每行一个 + 尾注释给出 BE 类型），总数打印随之更新 |
| 记忆 | `MemoryCellReporter.java` | 注释同步（族的清单 + 理由 ③；"其余"列的结论不变——它仍是空气 / INVISIBLE） |
| 采集 | —— | **零改动** |

### 17.6 验收（新增 §10 第 18 条）

18. **乙1 六块不再欠删**：现实侧敲掉**漏斗 / 酿造台 / 比较器 / 阳光传感器 / 幽匿感测体（含校定）**各一 →
    镜像即时消失、重启不复活；`block_entities.nbt` 中对应条目消失（采集侧 `prune(F1)` 行的 `be.pruned` 非零）。
    反向回归：**玩家在镜像世界里自建的**这 6 类方块不得被判删（与 §10 第 13 条依赖同一条候选集语义：
    候选集 = `terrain.appliedBlocks`，即"镜像回放体"）。

### 17.7 实施与编译（2026-09-12）

- **实施**：`BlockStateUtil` 表 + 6 个常量（脚本产出，字段名 6/6 命中）；`scan_be_carriers.py --emit-java`
  扩到乙1；`MemoryCellReporter` 注释同步。**采集侧零改动**（与 §17.2 第 3 条一致）。
- **编译**：两个 mod `./gradlew compileJava --offline` 均通过（零错误、零输出）——与 §16.1 同一口径，
  **仍只有编译期证据**，§10 第 18 条待实测。
- **累计工作区**：11 改 + 1 新增（未提交）。

## 18. BE 内容变更的发布缺口（2026-09-12 实机发现 —— 内容收缩既不发包、也不落盘）

> 触发 = §16.4 之后的实机测试：**营火上正在烤制的食物在镜像里复现成功**（v2.35/v2.38 的正向采集通道
> 两端都对），但**食物烤熟弹出后，镜像里的营火没有变空**。本轮定位到：采集侧与记忆侧"改数据"的部分
> **全部正确**，缺的是**发布**——改完之后既没发给客户端、也没写进磁盘。
>
> **范围声明（重要）**：本章**不新增任何判据**。§2.2 第 1 行 / 决策 E 说的"BE 内容的变更走**正向采集
> 通道**"依然是全部分工；本章修的是那条通道**发布端的缺陷**（此前被默认"已经能用"，实机证明不能）。
> 因此"BE 内容的独立消失证明"仍是长期边界（§9.1），一条不变。

### 18.1 现象与实测证据链（全部来自物证，非推断）

**采集侧（`run/stevex/vision/`，均为 19:44:19 同帧）：**

| 文件 | 内容 |
|------|------|
| `block_entities.nbt` | 三个营火 `-25,63,14` / `-21,63,14` / `-31,63,19` **全部 `Items: []`**（各自 ts = 19:37:20 / 19:44:19 / 19:35:25） |
| `entities.nbt` | 两只 `minecraft:item`（`cooked_rabbit` ×2）落点 `-20.74,63.4375,14.40` 与 `-20.77,63.0,13.82`——即烤熟弹出的那两堆。第一只落在**营火方块自己的占位内、y = 营火顶面** ⇒ "看起来像还在营火上烤" |

⇒ **写侧完全正确**：食物消失与掉落物都被完整采集，同一帧、同一时刻。

**记忆侧（我直接解析了镜像世界的区域文件，不依赖游戏内观察）：**

| 项 | 值 |
|----|----|
| `saves/MemoryWorld/region/r.-1.0.mca` 区块 `(-2,0)` 表头最后写入 | **19:43:41** ⇒ 19:44:21 那次同步之后，**含该营火的区块从未被写过** |
| 该区块内 `-21,63,14` campfire | `Items: [4 × minecraft:rabbit]`（生兔肉，原样）+ `keepPacked: 1` |
| 该区块内 `-25,63,14` campfire | `Items: []` |
| 记忆侧日志 19:44:21 | `Entity sync: +2 spawned, 0 rebuilt, 0 moved` ✓ **生效**；`Sync: +1 placed, total 16 entries` ✗ 见下 |

**`total 16 entries` 与 `block_entities.nbt` 的 16 条逐一对得上** ⇒ 记忆侧确实读到了"空营火"的**新**内容，
且 `+1 placed` 只可能是这一条（同帧内其余 15 条的 ts 都没变），说明 **`place()` 被调用且返回了 true**。

**结论：服务端内存里装的是空营火；磁盘上是 4 只生兔肉；玩家屏幕上看到的是"该区块首次下发时带着的那份内容"。**
重启镜像世界 → 从磁盘读回 4 只 → 屏幕上食物"又回来了"（与多次重启的观察一致）。

### 18.2 根因：内容变了、方块状态没变 ⇒ 两条发布通道同时失效

`MemoryRestorer.place()`（`MemoryRestorer.java:237-273`）做三件事：`setBlock(pos, state, 818)` →
`BlockEntity.loadStatic` → `level.setBlockEntity(be)`，然后 `return true`。内容收缩时**方块状态一字不变**，
于是：

**① 客户端通道断在第一步**（`Level.java:212-237`）：

```java
BlockState oldState = chunk.setBlockState(pos, blockState, updateFlags);
if (oldState == null) { return false; }          // ← 状态相同 → 到此为止
...
this.sendBlockUpdated(pos, oldState, blockState, updateFlags);   // :237，永远到不了
```

`LevelChunk.setBlockState` 的 `if (oldState == state) return null;`（`LevelChunk.java:282`）是源头。
而客户端拿到 BE 内容的**唯一**途径是：

```
ServerLevel.sendBlockUpdated (:1127)
  → ServerChunkCache.blockChanged (:464-471)          // getVisibleChunkIfPresent + ChunkHolder.blockChanged
  → ChunkHolder.blockChanged (:123-127)               // 需 ticking chunk，否则返回 false
  → chunkHoldersToBroadcast
  → ServerChunkCache.broadcastChangedChunks (:357-368) // 每个 tick 的常规路径
  → ChunkHolder.broadcastChanges (:174+)
  → broadcastBlockEntityIfNeeded (:212-231) → blockEntity.getUpdatePacket()
```

**这条链我们一次都没走到** ⇒ 客户端手里的营火内容永远停在"区块首次下发"那一刻。
对照 vanilla 自己怎么发这类"方块没变、内容变了"的更新：`CampfireBlockEntity.markUpdated()` 就是
`level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3)`。**我们缺的就是这一下。**
好消息是营火这一侧**早已实现好**：`getUpdatePacket()`（`CampfireBlockEntity.java:154`）+ `getUpdateTag`
只带 `Items`（:158-165），正是客户端渲染器需要的数据。

**② 落盘通道断在第三步**（`Level.java:698-703` → `LevelChunk.java:400-410` → `:427-455`）：
`Level.setBlockEntity` → `addAndRegisterBlockEntity` → `setBlockEntity` 只做
`blockEntities.put(pos.immutable(), be)`（+ 把旧实体 `setRemoved()`），**既不 `markUnsaved` 也不发包**。
写盘唯一入口是 `Level.blockEntityChanged(pos)` → `getChunkAt(pos).markUnsaved()`（`Level.java:875-879`），
它只由 `BlockEntity.setChanged()` 调用——**我们没有调**。

### 18.3 排除项（逐条给否定证据，避免误判方向）

| 候选原因 | 是否成立 | 证据 |
|----------|----------|------|
| 采集侧没采到"空了" | ✗ | `block_entities.nbt` 三个营火全 `Items: []`（19:44:19） |
| 采集端"只在再被看到时刷新"边界（v2.35 §9.1）挡掉了更新 | ✗ | 同帧新内容已落盘 ⇒ 营火那一刻**在可见集内**、条目被重写 |
| `BlockEntity.loadStatic` 返回 null（静默空转） | ✗ | 它的三条 null 路径全打 ERROR（"Skipping block entity with invalid type" / "Failed to create" / "Failed to load data"），`LevelChunk.setBlockEntity` 的 "state does not allow it" 是 WARN；`latest.log` + `debug.log` **一条都没有** |
| 冻结（`LevelMixin`/`ServerLevelMixin`）挡了发包 | ✗ | 两个 Mixin 只取消 `Level.tickBlockEntities` 与 `ServerLevel.tickBlock/tickFluid/tickNonPassenger`；`ServerChunkCache.tick` → `broadcastChangedChunks` **照常跑**（:357-368） |
| 实体通道同病 | ✗ | 日志 `+2 spawned` 生效；实体走 `addFreshEntity`，有真实生成包 |
| 营火 tick 把食物"吃回去" | ✗ | `tickBlockEntities` 已冻结，`cookTick` 不会跑 |

### 18.4 附带发现两条（独立于上面的缺陷，一并登记）

1. **beacon 的 `Levels: 0` 不是同一个 bug**：`BeaconBlockEntity.levels` **不由 NBT 装载**——每次 `tick`
   用 `updateBase()` 重算（`BeaconBlockEntity.java:166-169`），只在 `saveAdditional` 时写出（:303）。
   记忆世界冻结 BE tick ⇒ 恒为 0 ⇒ **信标光柱不渲染**（采集文件里是 `Levels: 1`）。这是冻结的既定代价，
   记入 §9.1 类边界，不在本章修。
2. **指纹记账把"一次性失败"钉成永久**：`MemoryRestorer.sync` 末尾无条件 `applied.putAll(next)`，而
   `place()` 在 `loadStatic` 返回 null（BE 没装上）时**仍然返回 true** ⇒ 任何一次静默失败都会被记成
   "已应用"，**永不重试**，只能靠重启自愈。属独立健壮性缺陷（见 18.5-D）。

### 18.5 加固方案（**采用 A+B+C+D** —— 2026-09-12 审阅通过，同日实施）

| # | 动作 | 落点 | 理由 |
|---|------|------|------|
| **A** | 发客户端：`level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)` | `MemoryRestorer.place()` 末尾（装上 BE 之后） | 与 vanilla `markUpdated()` 同款；走 §18.2① 的完整链。**只对"内容变了"的 pos 调用**（`sync` 里只有指纹变化的 pos 会进 `place`），不引入每帧流量。**实施**：仅在 `!stateWritten`（`setBlock` 返回 false = 状态没写）时补——状态真的变了的那次 `setBlock` 已经排过同样的队，不重复发 |
| **B** | 落盘：`level.blockEntityChanged(pos)` | 同上 | **刻意不用 `be.setChanged()`**：后者还会 `level.updateNeighbourForOutputSignal`（`Level.java:994-1003`，水平四向扫，命中比较器即**直接** `neighborChanged`）⇒ 会在冻结世界里激活红石/比较器逻辑，破坏 §7.9 的冻结不变量。直接调 `blockEntityChanged` 只做 `markUnsaved`，零副作用。**实施**：无条件调用（与 `!stateWritten` 无关——无论状态有没有写，BE 内容都变了、都必须置脏） |
| **C** | `place()` 返回值语义修正：BE 没能装上（`loadStatic` 返回 null）时返回 **false** | `MemoryRestorer.place()` | 配合 18.4-2：让"没生效"不进 `applied`，下一帧自愈。**实施偏差（重要）**：只改返回值**不够**——`sync()` 末尾无条件 `applied.putAll(next)` 才是病根，故同步改为"失败 pos 把旧指纹留下（本来没有就从 `next` 移除，即 null）"⇒ 下一帧仍满足 `key != applied.get(pos)` 而重试 |
| **D** | 日志可分辨"新放置 / 内容重写 / 跳过"：`Sync` 行拆成 `+N placed(新块)/M rewritten(内容重写)/S skipped` | `MemoryRestorer.sync` | 现在 `+1 placed` 把三种情况混成一个数——**本次误判正是读它读出来的**（§18.4 附注）。这类"沉默的生效失败"必须可见 |

**否决的备选**：
- *在 `place()` 里改用 `setBlock(pos, AIR, …)` + `setBlock(pos, state, …)` 强制状态变化以触发现有发布链* ——
  会触发方块移除/放置的全部副作用（BE 被销毁重建、掉落物抑制依赖 flags 逐个核对、邻块形状更新），
  风险远大于 A+B，且语义上做了两次世界改动。**否决。**
- *只补 B（落盘）不补 A* —— 屏幕上仍看不到更新（玩家看到的是客户端副本），症状不减。**否决。**
- *只补 A 不补 B* —— 当帧可见，但重启后复活（§18.1 的现象原样保留）。**否决。**

**实施落点（一处文件，2026-09-12）**：全部改在 `MemoryRestorer.java`——`place()`（A/B/C 的返回值与两处发布）
与 `sync()`（C 的记账 + D 的日志）。采集侧零改动。**编译**：记忆侧 `./gradlew compileJava --offline`
通过（零错误、零输出）。**运行期证据仍为零**，验收 = §10 第 19~22 条。

### 18.6 与既有结论的关系（范围不变）

- §2.2 第 1 行"BE 内容的变更**走正向采集通道**（本版不动）"、决策 E"变更走正向通道（非本版）"——
  **分工不变**，但"正向通道可用"这个隐含前提此前是**假的**（发布端没接上）。本章修好后该前提才真正成立。
- §9.1 的 BE 分类结局表**不动**：本章既不新增方块入表、也不新增删除原语。
- §16.4"状态同步未做"**照旧**：本章仍是编译期之后的新发现，状态同步依旧等验收。

### 18.7 验收（新增 §10 第 19~22 条）

19~22 条见 §10 末尾（即时可见 / 重启不复活 / 冻结不变量回归 / 日志可分辨性）。

### 18.8 残余缺口与边界（诚实清单）

- **渲染距离外的客户端**：`ChunkHolder.blockChanged` 要求"该区块对某玩家是 ticking chunk"（:123-127），
  否则 `blockChanged` 返回 false、不进广播集。此时不发包**无妨**（没人在看），但 **B（`markUnsaved`）
  必须无条件执行**——否则磁盘永远落后，玩家一走近、或重启，就看回旧内容。
  该"远距离自愈"依赖"区块首次下发时携带的是**实时 BE 数据**"（`LevelChunk.getBlockEntityNbtForSaving`
  优先活体 BE 并写 `keepPacked: false`）——**属推理、未实测**，第 20 条验收会顺带覆盖。
- **落盘时机**：`markUnsaved` 只置脏，真正写盘由定期存档 / `save-all` 触发；因此"重启不复活"的验收
  必须走**正常退出**（触发存档），不能 kill 进程。已写入第 20 条。
- **`keepPacked` 语义**：镜像存档里该营火带 `keepPacked: 1`，说明存档那一刻该 pos 只有"待实体的 NBT"
  （`LevelChunk.pendingBlockEntities`），没有活体 BE。这是区块加载后的**正常瞬态**（活体 BE 懒实例化），
  与本章缺陷无因果，但读存档取证时**必须知道它的含义**，否则会把"pending"误读成"我们的 place 没跑过"。
- **同帧多处内容变更**：A 会对每个变化的 pos 各发一次 `sendBlockUpdated`（合并成一次 section 包由
  `broadcastChanges` 自己处理）；单帧变更数 = 本轮观察到的内容变化数，量级可控。
- **编译期为何没拦住**：与 §16.5 同一类问题——`place()` 的"是否真的装上"只体现在返回值语义里，
  没有任何 `@Nullable` / 返回值校验能让编译器发现"装没装上都返回 true"。C 是行为修正，不是类型加固。
- **`TerrainRestorer` 有同一套记账写法，但不是同一个缺陷**（顺带核实，**不改**）：它也在末尾无条件
  `applied.putAll(nextApplied)`，然而其 `place()` 没有"静默半成功"路径——`setBlock` 返回 false（状态已经
  就是目标态）对地形来说**本身就是成功**（世界已达目标态），只有抛异常才返回 false 并打 WARN。
  残余风险仅"一次异常失败会被钉到重启"，且是**可见**的（WARN 日志），故本轮不动它。
  记在这里是防止后来者按 §18.5-C 的字面去"顺手加固"地形侧而引入行为变化。

### 18.9 实施与状态

- **状态：已实施（2026-09-12）。** 全部改在 `MemoryRestorer.java`：`place()`（A 两处发布 + B + C 返回值）
  与 `sync()`（C 的记账修正 + D 的日志）。采集侧零改动。
- **编译**：记忆侧 `./gradlew compileJava --offline` 通过（零错误、零输出）——**仍只有编译期证据**；
  §10 第 19~22 条待实测。**未提交**（工作区累计 13 改 + 1 新增）。
- **定位阶段的取证**：解析了镜像世界区域文件（区块写入时间戳 + BE 条目）、逐行核对了
  `Level` / `LevelChunk` / `ChunkHolder` / `ServerChunkCache` / `BlockEntity` / `CampfireBlockEntity`。
- 取证方法（可复现）：`block_entities.nbt` 为 gzip NBT，直接解压后按 NBT 规范解析；镜像存档为
  `saves/MemoryWorld/region/r.<rx>.<rz>.mca`——**表头第二张表（偏移 4096 起）的 4 字节是该区块最后写入的
  epoch 秒**，这是判定"服务端到底写没写"的关键物证，比观测屏幕可靠。
