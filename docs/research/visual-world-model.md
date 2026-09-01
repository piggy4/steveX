# 视觉世界状态模型探索

## 定位

`research/visual-world-model` 是 steveX 的独立研究分支，用于探索 Minecraft
部分可观测环境中的真实渲染可见性、外部空间记忆和场景复现。它不替代 `main`
上的 Mineflayer Runtime。

## 与主线的关系

| 分支 | 主要用途 | 运行方式 |
|---|---|---|
| `main` | 多 Agent、Skill、BT、Blackboard、Capsule 与 Benchmark | Mineflayer 无头客户端 |
| `research/visual-world-model` | GPU 深度视觉、NBT 快照和 MemoryWorld | Fabric Minecraft 图形客户端 |

主线继续承担低成本、多进程或单进程多 Agent 实验。视觉分支用于小规模、高保真的
部分可观测实验，并作为 Mineflayer 特权世界查询的对照方案。

## 当前能力

- 通过 Fabric Mod WebSocket API 暴露结构化感知与动作接口。
- 从 GPU 深度缓冲恢复当前画面中可见的方块和实体。
- 将地形、实体和方块实体快照保存为 NBT 文件。
- 在独立 MemoryWorld 中复现视觉快照，并根据观测证据清理过期对象。

## 论文使用边界

当前实现属于视觉世界状态模型原型，不是完整的预测式世界模型，也不是 LLM
语义记忆。论文中应通过感知精度、快照延迟、场景一致性、任务成功率和资源消耗等
指标验证其价值，再决定是否将通用接口合回主线。

建议采用以下实验分组：

1. Mineflayer 特权观测基线。
2. Fabric 部分观测，不启用空间记忆。
3. Fabric 部分观测，启用 MemoryWorld 空间记忆。

## 贡献

该探索的 Fabric Mod、GPU 视觉采集、MemoryWorld 原型和相关 Web 调试能力主要由
[zzq216](https://github.com/zzq216) 实现。后续贡献通过该研究分支继续跟踪，稳定且与
运行后端无关的接口应拆分为小型 PR 合入 `main`。
