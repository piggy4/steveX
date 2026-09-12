# BE 载体方块减量设计方案（v2.38）

> 状态：**设计待审阅，未实现**（2026-09-09 立项）。
>
> 立项源：docs/非满形状方块减量设计方案.md（v2.37）§13 附录——BE 载体方块完整删除（失败模式 F1–F3、
> 方案 B 定案）。本文档把 §13 方案展开为可实施设计。**前置依赖 v2.37**（本版把 BE 非满格并入 v2.37 的
> cells v4 几何段并复用其拾取盒几何过滤；v2.37 未落地前本版无从判 BE 非满格方块消失）。
>
> 一句话问题（§13 复述）：告示牌/旗帜/头颅/花盆/床/箱子等**带方块实体（BE）的方块**不是单个方块，而是
> "方块 + BE 负载"两条记录耦合；BE 负载走一条**累积、永不删、对空位有回放权威**的通道（block_entities.nbt）。
> 现有删除只删世界里的方块本体，删不掉也持久不了 BE 记录 → 删了必复活（§13.1 对称性断裂）。

## 0. 一句话方案

**把判据与删除彻底分层：判据只管"方块本体已消失"（v2.37 几何过滤，与是否带 BE 无关）；新增"唯一删除
原语" = 现有 deletions 清单，让它在产出同帧同时持久修剪采集侧 BE / 容器两个累积存储（block_entities.nbt、
containers.nbt），再随各自文件落盘——记录没了，复活根因（§13.1）消除。** 删除的原子性由"同 snapshot、同
tick、一次清单命中全部通道"保证，不合并文件；持久性由采集侧修剪落盘承担（记忆侧改不了采集文件）。

## 1. 背景与前置

### 1.1 记忆系统的三通道不对称（§13.1，已核实到代码）

| 通道 | 落盘方式 | 代码锚点 | 删除能力 |
|---|---|---|---|
| terrain（方块本体） | 每帧整体覆写 = 本次可见集 | [VisionTerrainStore.java:117-143](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionTerrainStore.java#L117-L143) | deletions → 世界置空；文件天然缺位 → 持久 |
| block_entities（BE 负载） | 增量累积、只增/改、永不删旧 | [VisionBlockEntityStore.java:130-172](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionBlockEntityStore.java#L130-L172) | 只有记忆侧 `clearStale`（内存级瞬时遗忘，[MemoryRestorer.java:120-123](../vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/MemoryRestorer.java#L120-L123)）→ 不持久 |
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
- **v2.38（本版）**：①判据对 BE 格的**方块**本体消失照常成立（谓词放行 BE 格进 cells，无新增判据逻辑）；
  ②补**删除原语跨通道**，让删格原子、持久地命中 terrain / block_entities / containers。

## 2. 目标范围与非目标

### 2.1 纳入（四项子特性）

| 子特性 | 内容 | 依赖 |
|---|---|---|
| F1 **删除原语跨通道持久化**（核心） | deletions 同帧修剪采集侧 block_entities.nbt + containers.nbt 并落盘；BE/容器记录随方块消失持久移除 | 无（对现行满格 BE 立即生效） |
| F2 **判据纳入"可深度判据的 BE 非满块"** | 谓词放行 BE 格进 cells 几何段；只证方块本体消失 | v2.37 cells v4 + 几何过滤 |
| F3 **容器通道协同** | 容器 reconcile 不再对"本代际被判删、世界为空气"的格自足回放 | F1 |
| F4 **多格/配对最小规则** | 床双格随现实整床消失天然双格同判；双格大箱幸存半格单格化、容器记录按 BE 宿主格删 | F1+F2 |

### 2.2 明确排除 / 非目标

| 排除 | 理由 |
|---|---|
| **BE 内容的独立"消失证明/更新"** | 内容（告示牌文字/箱子物品）对深度/几何不可见，只能作为方块消失的**从属品**删除；其*变更*走正向采集通道（本版不动） |
| **~~绊线/绊线钩~~、无深度信号的 BE 类**（注册表扫描锁定） | ~~判据无信号则方块本体也证不了 → 沿 v2.37 排除表欠删~~<br>**2026-09-12 更新（v2.37 七次修订 §15）**：**绊线/绊线钩已移出本行**——改由 v2.37 §15「信号缺失族校正通道」（真实世界状态直读，与几何判据并列）判删，本版不欠删；**无深度信号的 BE 类**在**判据侧**同样适用该通道（状态直读与是否有 BE 无关），但其**执行侧依赖本版（v2.38）的唯一删除原语**（否则删了方块、留下 BE / 容器负载 = §13 失败模式）——故本版结束时它们仍欠删，原语就位后**加表即纳入**（v2.37 §15.5 第 3 条；接口侧 cells 信号缺失段已预留 `blockId` 字段，无需改格式） |
| **花盆等"足迹 ⊋ 拾取盒"、多格语义不清者**（扫测锁定，见 §3.4/§10 边界） | 沿用 v2.37 方向约束与范围，个别仍欠删（安全方向） |
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
| **BE 非满块方块消失**（本版 F2） | dropPos(pos) | 置空 | **移除 nbt 记录**（内容随之从属删除） | 若有容器记录 → 移除 |
| **满格 BE 消失**（熔炉等，现已有） | dropPos(pos)（F1 收口） | 置空 | 移除记录 | 若有容器记录 → 移除 |

### 3.3 原子性与持久性来自哪里

- **原子性**：修剪发生在 deletions **产出的同一个 resolve 调用内**、BE store / 容器 store **落盘之前**
  （采集侧快照编排见 §4.1），不存在"部分命中"的时间窗；
- **持久性**：block_entities.nbt / containers.nbt 的文件本体被改写（被删条目缺位）。记忆侧重读（mtime 变）
  → 记录不存在 → 无回放；重启 → 文件里也没有 → 不复活。记忆侧 `clearStale` 保留为"本 tick 立即遗忘"，
  不再承担持久职责。

## 4. 采集侧

### 4.1 删除原语挂点（改动核心）

`ObjectResolver.resolve` 现状（[L145-213](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/ObjectResolver.java#L145-L213)）：

```
…四路查询 → deletions 计算(L158-177) → biomeStore.sync(L193)
→ getTerrainStore().sync(terrain, deletions, …)         (L206, deletions 写 terrain.nbt)
→ getStore().sync(blockEntities, …)                     (L208, BE store 落盘，尚不知 deletions)
→ getEntityStore().sync(…)  → return ResolveResult(…)
```

本版在其上：在 L206–L208 之间（BE store 落盘**前**）插入两次修剪，再让 store 落盘：

1. `VisionBlockEntityStore.applyDeletions(dimensionId, deletions)`——从该维累积表移除这些 pos 的条目；
2. `ContainerMemoryStore.applyDeletions(dimensionId, deletions)`——移除容器记录；
3. 二者置 dirty → 各自 `save()`（BE store 随后被本帧 `sync` 再落一次幂等覆盖；容器 store 可能本帧无交互
   提交，**须因 dirty 强制 save**，保证记忆侧能读到修剪结果）。

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

### 4.4 判据纳入 BE 非满格（F2，依赖 v2.37）

- v2.37 谓词 §4.1 第 4 步 `level.getBlockEntity(pos) == null` 排除 BE；本版放开为："BE 格进入几何段，
  判据只对其**方块本体**消失作证"。放行前以**注册表扫描**（v2.37 §10 第 3 条）对每类 BE 非满块确认：
  其本体方块写哪个深度场（main/translucent，路由见 v2.37 §5.2）、拾取盒 ⊆ 渲染足迹 → 才纳入；
- 预计多数可判：告示牌/悬挂牌（CUTOUT→main）、头颅、花盆、装饰陶罐、钟、讲台、雕纹书架、床、箱子等
  本体几何清晰；横幅等渲染特判的以实测为准——不可判者退回欠删（安全方向，不出现在 cells）；
- **判据不新增任何逻辑**：BE 非满格与普通非满格共用 v2.37 `testShaped` 几何过滤，blockId 只用于路由
  （v2.37 §5.2/§5.3）。cells v4 条目已含 blockId + 格内盒，无需扩展格式。

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
  幸存格由**正向通道**（下帧可见 terrain）更新为 single——删除不用管。容器记录删除规则：BE 宿主格被删
  则内容记录随之删；宿主格幸存则记录保留。**实施期须核对**：双格箱 BE 宿主格在现实中归属哪半、及
  半删后 MC 对物品归属的实际行为（见 §10 验收）。
- 其余"一对多"（如长桌类非本清单）不做，扫测锁定为欠删。

## 7. 决策定案（待审阅）

| 决策 | 候选 | 定案 | 理由 |
|---|---|---|---|
| A：删除原语 | 合并文件（A 方案）vs **deletions 唯一原语（B）** vs 存在性绑定 terrain（C） | **B**（立项于 v2.37 §13.3） | 原子性靠同帧同 tick，持久性靠采集侧落盘；改动最小、贴合现有 deletions；合并也管不住 containers |
| B：修剪位置 | 记忆侧持久写 vs **采集侧 resolve 内、store 落盘前** | 采集侧 resolve 内 | block_entities/containers 文件是采集侧写的、记忆侧只读；deletions 就在同函数（[ObjectResolver.java:158-210](../vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/ObjectResolver.java#L158-L210)），零新信号 |
| C：容器自足回放 | 保留 vs 加 deletionGuard 跳过 | **guard 跳过（仅空气+本代际被判删）** | 不破坏"视觉未放好前容器先补壳"的冷启动语义；只堵"刚被删又回放"的对抗 |
| D：BE 判据纳入 | 全量纳入 vs 注册表扫描分类后纳入 | **扫描分类后纳入**（v2.37 §10 第 3 条同一扫描） | 只把"本体写深度场 + 拾取盒⊆足迹"者放进 cells；横幅等退回欠删 |
| E：BE 内容生命周期 | 内容独立判删/更新 vs **从属方块删除** | 从属 | 内容不可被几何证明；变更走正向通道（非本版） |
| F：多格 | 配对删除逻辑 vs 现实语义 + 最小规则 | **现实语义最小规则**（床整床删、双箱单格化交正向通道） | 逐格判据 + 正向更新已覆盖绝大多数；配对仅实施期核对双箱 BE 宿主 |

## 8. 正确性论证

- **复活根因消除**：复活 = 累积 BE 记录 + 空气位回放权威。采集侧修剪后记录缺位 → 文件无、重放无、重启无
  （§3.3）。记忆侧 guard 只堵瞬态窗口。
- **不引入新假删**：删除仍由判据（≥2 越票、跳过可见集）门控；修剪只对判据命中的 pos 生效，与删格同源
  同置信。BE 记录与 terrain 块来自同一帧观察，同 pos 同步消失方向自洽。
- **满格 BE 一并收口**：F1 挂现有 deletions，熔炉/木桶的潜在复活（v2.23 潜伏洞）随本版修复，无需改判据。
- **内容保护**：BE/容器内容永无独立删除资格（决策 E）——判据假阳性最多导致一次整格删除（方块+从属负载
  一起清），不产生"方块还在、负载被单独误删"的半态；欠删仍只留幽灵，由正向通道修正。
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

## 10. 验证清单（实施后，游戏内）

1. **F1 复活回归**：敲掉告示牌 → 记忆世界消失；随后 (a) 重启、(b) 同维另处 BE 被改动触发文件重写，均
   **不复活**、无文字残留（§13.5 第 1 条正式化）；
2. 敲掉满格熔炉/木桶 → 不复活（修 v2.23 潜伏洞）；
3. **F2 判据**：活牌不误删；敲牌即删（含墙牌/站立牌）；换牌（改文字/换位置）走正向更新正常，不出现旧
   记录回放盖新牌；
4. **F3 容器**：敲掉箱子 → 与容器 reconcile 不对打：格不复活、物品记录随修剪消失（容器文件缺位）；容器
   方块仍在时照常覆写内容（回归）；
5. **F4 多格**：整床敲 → 双格同删、无半格残留；双格大箱敲半 → 幸存格单格化（正向）、记录按 BE 宿主格删
   （实测核对物品归属与宿主格）；
6. **注册表扫描**：BE 非满块全量分类（可判/欠删/无信号），横幅等实测定位渲染层 → 锁定排除清单；
7. 判据假阳性防护回归：活体贴边/抖动不误删；欠删类（横幅/被挡）留幽灵、由正向修正；
8. 兼容：无 v2.37 时（cells 无几何段）本版对 BE 非满块自动退化为欠删；旧文件加载无 WARN。

## 11. 涉及文件清单

| 侧 | 文件 | 动作 |
|----|------|------|
| 采集 | `ObjectResolver.java` | 改：resolve 在 terrain sync 后、BE/容器 store 落盘前插入 applyDeletions（L206-210 间） |
| 采集 | `VisionBlockEntityStore.java` | 改：新增 `applyDeletions(dim,pos)`；dirty→save 复用 |
| 采集 | `ContainerMemoryStore.java`（+ Tracker 视形状） | 改：新增 `applyDeletions(dim,pos)` + 强制 save |
| 采集 | `DeletionJudge.java` | 不改（v2.37 已泛化 `testShaped`；BE 格走同一过滤） |
| 记忆 | `BlockStateUtil.java` | 改：谓词放开 BE 格（扫测分类门控后），仍与 reporter/applier 同源 |
| 记忆 | `MemoryCellReporter.java` | 改：BE 非满格并入几何段上报（cells v4 条目不变） |
| 记忆 | `ContainerMemoryApplier.java` | 改：tick 增 `deletionGuard`；空气 + 判删格 → 跳过自足回放 |
| 记忆 | `MemoryWorldManager.java` | 改：`CONTAINER.tick(level, deletions-as-guard)`（次序不变） |
| 记忆 | `DeletionApplier.java` | 改：内容守卫扩至 BE 非满格（方块本体在可删集内） |
| 文档 | v2.37 文档 §13 / 视觉api设计方案.md §7.13 / 已实现内容.md | 实施后状态同步 |

## 12. 版本与后续

v2.38（BE 载体方块减量），前置 v2.37。工作流：本文档审阅通过 → 先完成 v2.37 代码并验收 → 再按 §11
实施本版 → 更新 v2.37 文档 §13（附录 → 已实现）与视觉api设计方案.md §7.13 / 已实现内容.md。
非目标（内容独立消失语义、横幅类深度信号、绊线）记入 §9/§2.2，长期已知边界。
