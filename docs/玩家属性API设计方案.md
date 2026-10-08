# 玩家属性表 API 设计方案（`player` → `attributes`）

> 状态：**已实施**（2026-10-02 起草并当日审阅通过；同日完成编码，`compileJava` 通过、`node --check` 通过，
> 改动清单见 §10。**待游戏内验收**：§11 的 7 条一条都还没跑）。
> 关联：[观察边界与物品可见性设计方案.md](观察边界与物品可见性设计方案.md)（物品薄读取面）、
> [实体属性观测面设计方案.md](实体属性观测面设计方案.md)（v2.41/v2.42，**生物**的观测面——本方案处理的是**本地玩家自身**，问题不同，见 §7.3）、
> [视觉api设计方案.md](视觉api设计方案.md)。
> 涉及实现：`vendor/stevex-template-1.21.11/.../name/modid/api/PlayerApi.java`、`src/mod/methods.js`、
> `docs/已实现内容.md`（v2.50 块）。

---

## 1. 背景与动机

目标场景：agent 要回答"我手里这把铁剑攻击力多少、还剩多少耐久"。

| 需要的东西 | 现状 | 位置 |
|---|---|---|
| 耐久 | ✅ 已有 | `inventory` / `container/get` 的 `slots[].durability` / `maxDurability`（`InventoryApi.java:47-50`） |
| **物品**给的攻击力 | ✅ 已有，但是**碎片** | `slots[].attributeModifiers[]` = `{attribute, amount, operation, slot}`；铁剑一项：`{minecraft:attack_damage, 5.0, add_value, mainhand}`（`InventoryApi.java:83-95`） |
| **玩家自身**的基础值 | ❌ **没有** | `player` 只输出 `vitals/flags/effects`，属性里仅碰了 `ARMOR_TOUGHNESS` 一项（`PlayerApi.java:74-75`） |

于是 agent 拿着 `5.0` 也算不出界面上的 **6**——缺的是 `Player.createAttributes()` 里那个 `ATTACK_DAMAGE 1.0`（`Player.java:213`）。本方案把这张表补出来。

### 1.1 为什么不在物品条目里直接给最终值（方案 (b)，已否决）

给 `InventoryApi.slotItem` 加 `attackDamage: 6.0` 的代价：

1. **需要 player 上下文**，而 `slotItem` 被 `container/get` 复用——**箱子里的**物品根本不在玩家身上，"最终值"对它没有定义；
2. **"最终值"依赖玩家当前状态**（其他装备、药水效果、`/attribute`）。把它写进物品条目，会被读成"这物品的固有属性"＝**假事实**。

⇒ 拆成两半：**物品给修饰符**（可复用、与玩家无关），**玩家给基底**（可复用、与物品无关），合成规则是 vanilla 公开语义，agent 侧一步加法即可。这也是方案 (a) 的核心理由。

---

## 2. 目标 / 非目标

**目标**

1. `player` 输出新增顶层键 `attributes`：玩家**自身**的全部属性，每项给 `base` 与 `value`（形状见 §4）。
2. 零服务端改动、**零 mixin**（全部走公开 API）。
3. 键名与 `slotItem.attributeModifiers[].attribute` **完全同源**（`Holder.getRegisteredName()`）⇒ agent 可直接配对，不需要映射表。

**非目标**

- 不给生物/其他实体的属性（v2.41 已立那条路，且要过可见性边界；§7.3）。
- 不改 `f3`（§7.4）。
- **不写**属性：不发包、不调 `setBaseValue`。
- 不给"最终伤害"（附魔/暴击/攻击冷却不在属性里，§8）。
- 不新增方法：方法数仍 **51**。

---

## 3. 数据源与同步链路（权威性判定）

### 3.1 客户端持有本地玩家的属性表

`LivingEntity` 构造时以 `AttributeSupplier` 建 `AttributeMap`（`AttributeMap.java:22-24`），玩家的 supplier = `Player.createAttributes()`（`Player.java:210-224`）= `LivingEntity.createLivingAttributes()` 的 20 项（`LivingEntity.java:325-347`）+ Player 追加的 13 项（其中 `movement_speed`、`waypoint_transmit_range` 与前表重复）⇒ **共 31 项**。

实例是**惰性**创建的：`AttributeMap.getInstance` 用 `computeIfAbsent`（`:45-47`），supplier 里没有的属性返回 `null`。

### 3.2 ⚠️ 同步链路只覆盖一部分，且**恰好不含 `attack_damage`**

服务端只下发 `isClientSyncable()` 为真的属性（`ServerEntity.java:282-288` 首次、`:345-350` 变更时），而 `Attribute.isClientSyncable()` 的字段默认是 **false**（`Attribute.java:16`），必须显式 `.setSyncable(true)`。

而 `Attributes.java` 里：

```java
public static final Holder<Attribute> ATTACK_DAMAGE   = register("attack_damage",   new RangedAttribute(...));          // ← 没有 setSyncable(true)
public static final Holder<Attribute> ATTACK_KNOCKBACK= register("attack_knockback",new RangedAttribute(...));          // ← 没有
public static final Holder<Attribute> ATTACK_SPEED    = register("attack_speed",    new RangedAttribute(...).setSyncable(true));
```

⇒ **`attack_damage` / `attack_knockback`（以及 `follow_range` / `tempt_range`）不在同步集里**。

**这条直接决定实现方式**：**不能**用 `getAttributes().getSyncableAttributes()` 枚举——那会**正好漏掉**本方案的第一个目标（`attack_damage`）。

### 3.3 采用的枚举方式

```
for (Holder<Attribute> h : BuiltInRegistries.ATTRIBUTE.listElements())
    AttributeInstance inst = p.getAttribute(h);   // 返回 null = 该玩家没有这个属性
    if (inst != null) → 输出一项
```

- 顺序 = 注册顺序，确定 ⇒ 输出稳定（`LinkedHashMap`）。
- 客户端值是**本地算出来的**：base 来自本地 supplier，modifier 来自本地装备（装备包同步）与本地效果（效果包同步后走 `LivingEntity.onEffectAdded` → `addAttributeModifiers`，`LivingEntity.java:1061`/`:1080`）。所以正常游戏下与服务端一致。
- 但**服务端若用 `/attribute` 改了非同步属性的 base，客户端不会收到更新** ⇒ 记为已知限制（§9 第 1 条）。

### 3.4 通道判定（按"语义包 vs 输入路径"准则）

点控件的视觉状态在客户端**有无服务端回读通道**？—— 属性表**本来就在客户端手里**（不是"回读"，是本地副本），读它不需要任何包、不改变任何状态 ⇒ **语义包**，不涉及输入路径，也不触碰已搁置的 `gui/*`。

---

## 4. 输出形状

`player` 的返回 map 新增一个顶层键（与 `vitals` / `flags` / `effects` 并列）：

```json
{
  "vitals": { "…": "…" },
  "flags":  { "…": "…" },
  "effects": [ … ],
  "attributes": {
    "minecraft:attack_damage": { "base": "1.00", "value": "6.00",
      "modifiers": [ { "id": "minecraft:base_attack_damage", "amount": "5.00", "operation": "add_value" } ] },
    "minecraft:attack_speed":  { "base": "4.00", "value": "1.60",
      "modifiers": [ { "id": "minecraft:base_attack_speed", "amount": "-2.40", "operation": "add_value" } ] },
    "minecraft:armor":         { "base": "0.00", "value": "20.00", "modifiers": [ … ] },
    "minecraft:max_health":    { "base": "20.00", "value": "20.00" }
  }
}
```

⚠️ **数值是字符串不是数字**：三处都走 `AgentWebSocketServer.f2(...)`（`f2 → String.format("%.2f")`），
与 `container/get` 的进度类字段（`burnProgress` 等）以及 `slotItem.attributeModifiers[].amount` 同一口径。
调用方要数值须自行 parse。

| 字段 | 定义 | 判据 |
|---|---|---|
| 键 | 属性注册名 | `Holder.getRegisteredName()`，如 `minecraft:attack_damage` |
| `base` | **不含任何 modifier** 的基础值 = "**玩家自身**"的贡献 | `AttributeInstance.getBaseValue()`（`AttributeInstance.java:40`） |
| `value` | 含全部 modifier 的最终值 | `AttributeInstance.getValue()`（`:138`），内部 `sanitizeValue` 已 clamp 到 `[min,max]` |
| `modifiers` | **仅非空时给**；每项 `{id, amount, operation}` | `AttributeInstance.getModifiers()`（`:56`）；`operation` ∈ `add_value` / `add_multiplied_base` / `add_multiplied_total`（`AttributeModifier.java:39-41`） |

**形状决策**

- **map 而不是数组**：属性是一组**按名取**的固定键（agent 的问题是"我要 `attack_damage`"），map 省一次扫描；数组适合有顺序语义的集合（`slots[]`、`effects[]`）。注册顺序在 map 里依然稳定，便于肉眼比对。
- **`modifiers` 的键名与 `slotItem.attributeModifiers[]` 同名同义**（`id` / `amount` / `operation`，后者多一个 `slot`），agent 可以把"玩家表里的修饰符"和"物品上的修饰符"直接对照。
- **精度 `f2`**：与 `slotItem.attributeModifiers[].amount` 一致（`f2(5.0) → 5.0`、`f2(1.6000000000000005) → 1.6`）。`vitals.armorToughness` 历史上用的 `f1`，不统一，记为已知不一致（§9 第 3 条）。
- **`base` 与 `value` 都给**：`base` 是合成公式的正确操作数（物品侧那一半加在它上面）；`value` 是"此刻实际生效值"（已含装备与效果）。两者用途不同，见 §6。

---

## 5. 判据与不变量

| 不变量 | 说明 |
|---|---|
| **恒存在的完整集合** | 属性表是玩家 supplier 的全量，不存在"缺席＝未知"的情形——没有的属性直接不出现，**不写 null**（这套"缺席 vs null"编码规则针对的是**观测**得来的信息，属性表不属于那类） |
| `modifiers` 非空才写 | 空数组没有信息量，与 `slots[]` 只列非空格同理 |
| `value` 已 clamp | 输出的是 `getValue()`，即已过 `sanitizeValue` 的值，不是原始算式的数 |
| 只读 | 本接口不修改任何状态，重复调用结果一致（除玩家状态本身变化） |

**`modifiers[].id` 能读出什么**（写进 javadoc，避免过度解读）：

| id 形如 | 来源 |
|---|---|
| `minecraft:base_attack_damage` / `minecraft:base_attack_speed` | **主手物品**（`Item.BASE_ATTACK_DAMAGE_ID`，`Item.java:105-106`）——物品的修饰符在装备时被加到玩家表里 |
| `minecraft:effect.strength` 等 | **药水效果**（`MobEffects.java:41-45`） |
| 随机 UUID 形 | `/attribute` 或插件/服务端逻辑 |

⚠️ `AttributeModifier` 只有 `id / amount / operation` 三个字段（`AttributeModifier.java:14`），**不带来源实体或物品**⇒ 只能给到 id 这一层。

---

## 6. 合成规则（写给 agent 的用法）

`AttributeInstance.calculateValue()`（`:147-164`）的运算顺序：

```
value = sanitize( ((base + Σ add_value) × (1 + Σ add_multiplied_base)) × Π(1 + add_multiplied_total) )
```

| 想知道 | 用哪个 |
|---|---|
| 这把剑**自身**给多少攻击 | `slots[].attributeModifiers[attribute="minecraft:attack_damage"].amount` = `5.0` |
| **玩家自身**的基础（空手值） | `attributes["minecraft:attack_damage"].base` = `"1.00"` |
| 此刻**实际生效**的值 | `attributes["minecraft:attack_damage"].value` |
| 铁剑在手时的攻击伤害 | `"5.00" + "1.00" = "6.00"` ✅ 与界面一致 |

**易错点（须写进 `methods.js` 的说明）**：若该物品**已经在主手**，`value` 里**已经含**它，别再叠加一次；`base + 物品修饰符` 只适用于"这把剑还没上手，我想知道拿上会是多少"。

---

## 7. 与既有字段 / 接口的关系

### 7.1 `vitals.armor` / `vitals.armorToughness`：**保留**，不改

- `vitals.armor = floor(attributes["minecraft:armor"].value)`（`LivingEntity.getArmorValue()`，`:1803-1805`）；
- `vitals.armorToughness` ≡ `attributes["minecraft:armor_toughness"].value`（精度不同，见 §9 第 3 条）。

二者变成**冗余但等价**。删除会破坏既有调用方，故保留，并在 `已实现内容.md` 记一句等值关系。

### 7.2 与 `slotItem.attributeModifiers[]` 的分工

| | `slotItem.attributeModifiers[]` | 本方案 `attributes` |
|---|---|---|
| 主体 | **物品**（在哪都行：背包/容器/掉落物） | **玩家自身** |
| 内容 | 该物品**会给**的修饰符 | 此刻**已生效**的修饰符 + base + value |
| 多出的字段 | `slot`（哪个装备槽生效） | `base` / `value` |

### 7.3 与 v2.41/v2.42 生物观测面的区别（**不合并**）

| | v2.41 `snapshot.entities[]` | 本方案 |
|---|---|---|
| 对象 | **别人**（僵尸/村民/其他玩家） | **我自己** |
| 内容 | 装备/名牌/效果/姿态等**可见**属性 | 数值属性表 |
| 约束 | 受"看到才记"的可见性边界约束，且要**落盘 + 复原** | 第一人称自身状态（与 `player`/`inventory`/`f3` 同族），**无**边界问题、**不落盘** |

⇒ 两套并存是正确形态，不是重复。

### 7.4 与 `f3` 的关系

`f3` 目前只读 `ARMOR_TOUGHNESS` 一项（`F3Api.java:195`），**不改**。两套并存记为已知不一致（与 v2.41 §3.1 对 `equipment`/`name` 的处理同一口径）。

---

## 8. 边界：**属性 ≠ 伤害**（必须在文档与 javadoc 里写死）

`Player.attack()`（`Player.java:967-1000`）里那一串才是"这一剑砍多少"：

```java
float baseDamage = … (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);   // ← 本方案的 value ✅ 只保证到这一行
float attackStrengthScale = this.getAttackStrengthScale(0.5F);                  // 攻击冷却 ❌
float magicBoost = attackStrengthScale * (this.getEnchantedDamage(…) - baseDamage);  // 锋利等附魔 ❌
baseDamage *= this.baseDamageScaleFactor();                                     // ❌
baseDamage += attackingItemStack.getItem().getAttackDamageBonus(…);             // ❌
if (criticalAttack) baseDamage *= 1.5F;                                         // 暴击 ❌
```

⇒ 字段名叫 **`value`** 而不叫 `damage`。agent 想要"真实伤害"还得自行叠加附魔/暴击/冷却——这属于**已知能力边界**，不在本方案内解决（`getEnchantedDamage` 是服务端方法，客户端拿不到）。

**附带纠正一个常见误解**：**力量药水不是独立的伤害倍率，而是 `attack_damage` 的属性修饰符**（`MobEffects.STRENGTH` = `+3.0`/级 `ADD_VALUE`，id `minecraft:effect.strength`，`MobEffects.java:41-45`；经 `LivingEntity.java:1061` 应用到属性表）⇒ 它**会**体现在 `value` 里、也**会**出现在 `modifiers[]` 里。

---

## 9. 已知限制 / 待游戏内确认

1. **非同步属性可能与服务端不一致**：`attack_damage` / `attack_knockback` / `follow_range` / `tempt_range` 客户端收不到服务端更新（§3.2）。服务端用 `/attribute` 改过 base 时，本接口给的是**客户端认知**。
2. **待实测**：客户端玩家的 `attack_damage` 是否已含主手物品与效果的修饰符。**代码路径上应该包含**（装备包 / 效果包都会在客户端本地写进同一份 `AttributeMap`），但需 §11 的用例 2/4/5 证实。
3. **精度不统一**：新字段用 `f2`，`vitals.armorToughness` 用 `f1`（历史）。
4. `modifiers[]` 无来源对象，只有 id/amount/operation（§5）。
5. 属性表**项数应为 31**（§3.1 的推导）。若实测不符，以实测为准，并把差异记回本文档。

---

## 10. 改动清单（待审阅通过后执行）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `api/PlayerApi.java` | `PlayerData` 增字段 `attributes`；新增私有 `readAttributes(LivingEntity)`（遍历 `BuiltInRegistries.ATTRIBUTE.listElements()` + `getAttribute()`，跳过 null）；handler 里 `map.put("attributes", d.attributes)`；javadoc 写清 `base`/`value`/`modifiers` 口径、§8 的边界、§5 的 id 解读 |
| 2 | `src/mod/methods.js` | `player` 的 `zh` 补 `attributes` 说明：形状、铁剑算例（5.0 + base 1.0 = 6.0）、"已在主手别重复加"、**"value ≠ 最终伤害"** |
| 3 | `docs/已实现内容.md` | `player` 条目 + 方法清单描述更新 |
| — | 不动 | `f3`、`InventoryApi`、`container/*`；方法数仍 51；无 mixin、无服务端改动 |

**验收标准**：`./gradlew compileJava` 通过；§11 全部用例在游戏内符合预期。

---

## 11. 验收测试序列（游戏内）

前置：单人存档，创造或生存均可，`player` 无参数调用。

| # | 操作 | 期望 |
|---|---|---|
| 1 | 空手调用 `player` | `attributes["minecraft:attack_damage"] = {base:"1.00", value:"1.00"}`，**无 `modifiers`**；`attack_speed` = `{base:"4.00", value:"4.00"}` |
| 2 | **主手**拿一把全新铁剑，再调用 | `attack_damage = {base:"1.00", value:"6.00", modifiers:[{id:"minecraft:base_attack_damage", amount:"5.00", operation:"add_value"}]}`；`attack_speed = {base:"4.00", value:"1.60", modifiers:[{id:"minecraft:base_attack_speed", amount:"-2.40"}]}` |
| 3 | 同时调 `inventory` 交叉核对 | `slots[主手].attributeModifiers` 里 `attack_damage` 的 `amount` = `"5.00"` ⇒ `5.00 + base("1.00") = 6.00` ✅ 与用例 2 的 `value` 吻合 |
| 4 | 把剑**移到背包**（不装备），再调 `player` | `attack_damage.value` 回到 `"1.00"`，且 `modifiers` 里 `minecraft:base_attack_damage` **消失** ⇒ 证明物品修饰符只在装备时进玩家表 |
| 5 | 喝**力量药水**（I 级），再调 | `attack_damage.value` = `"7.00"`（空手）或 `"9.00"`（持剑），`modifiers` 多一项 `{id:"minecraft:effect.strength", amount:"3.00"}` ⇒ 证实 §8 的力量是属性修饰符 |
| 6 | 穿上全套钻石甲，再调 | `armor.value = "20.00"`，且 `vitals.armor == floor(20.00)`；`armor_toughness` 与 `vitals.armorToughness` 数值一致（精度差一位：`"20.00"` vs `"20.0"`） |
| 7 | 数 `attributes` 的键数 | 31（§9 第 5 条），且含 `minecraft:attack_damage`（这条同时验证了 §3.2 的坑：**若用 `getSyncableAttributes()` 实现，这一项会缺席**） |

---

## 12. 遗留 / 后续可选（不在本方案内）

- **最终伤害的合成**：附魔/暴击/冷却三项要 agent 自己算，或未来新增专门接口（客户端拿不到 `getEnchantedDamage`，需另想数据源）。
- **属性写入**（`/attribute` 等价的能力）：本方案明确不做。
- **生物属性**：仍归 v2.41/v2.42 的观测面，不因本方案改动。
