# 浏览器标签与 API 精简反馈回归

2026-11-01。区分自动回归、真实游戏和真实模型验收，不将模拟包覆盖写成实机通过。

## 本次变更

- World name tag：3D 模型每次插值后，同步浏览器原生玩家标签的位置及 CSS2D 使用的世界矩阵，包含寻路停止后的终点；不修改 bot 坐标。部署时须重新运行 `scripts/install-map-player-models.js <BlueMap webroot> <1.21.11 client jar>` 并刷新地图页面。
- 最大血量：共享 `healthState` 供 hp、Agent debug、状态路由使用。实机发现原版首次登录不发送未修改的最大血量属性；现在缺少覆盖值时读取当前版本注册表默认值，不硬编码 20。显式属性及修饰符优先，损坏的覆盖值仍为未知。新增首次登录及 20 -> 28 -> 20 回归。
- hp：回血、中毒、凋零、生命提升均返回等级与时间来源。吸收只在观测到剩余黄心大于 0 时返回剩余量及药水信息，耗尽后即使药水仍在也省略。数值使用生命点（2 点 = 1 颗心），持续时间为客户端 tick 估计，不承诺低 TPS 下精确秒数。
- `inventory slots`：普通空槽省略；选中的空槽返回 `{ "slot": 39, "selected": true }` 这类最小记录。开容器后仍用固定玩家槽号。
- say：保留发送与 `confirmed:false`，输出不再重复消息全文；不能将“请求发送”解释为服务端执行成功。
- findplayer：只对仍在 `bot.entities` 中的有效实体返回坐标，附当前维度；销毁、过期引用或 ID 复用后省略坐标。新增追踪世代防护：login/respawn 后旧实体即使残留在 Mineflayer 缓存，也必须重新被观察后才能返回位置，不把旧坐标标成新维度。不修改 Mineflayer 实体缓存。客户端 tab list 本身无法区分跨维度、同维度远处或暂未追踪。

## 自动验证

本轮全库自动回归 **583/583**；生成目录一致性检查与 PowerShell 语法检查通过。

`node scripts/run-tests.js`。新增 `test/api_bug_regression.test.js` 用真实 Mineflayer 实体插件注入属性、药水、实体销毁事件，覆盖上述反馈边界及标签投影矩阵更新；这不是连接 Minecraft 服务端的实测。

## 填写 DeepSeek API 后复测

仓库安装依赖并启动游戏及本地 Web 服务后，在仓库根目录运行：

```powershell
# 隐藏输入，密钥使用 Windows DPAPI 存入已忽略的 runs/secrets；不要写进聊天或 Git。
powershell -ExecutionPolicy Bypass -File scripts/query-llm-acceptance.ps1 -Configure
$env:STEVEX_ACCEPTANCE_AGENT = 'steveX' # Web 页面中的 agent 名称
$env:STEVEX_ACCEPTANCE_PLAYER = 'piggy985' # 要查询的玩家
$env:STEVEX_ACCEPTANCE_URL = 'http://127.0.0.1:8090'
# 可选：DEEPSEEK_MODEL、DEEPSEEK_BASE_URL（完整 chat/completions URL）
powershell -ExecutionPolicy Bypass -File scripts/query-llm-acceptance.ps1 -Suite bugs
```

也可先运行 `node scripts/api-bug-acceptance.js` 查看计划，不调用游戏或模型。live 每轮只执行 hp、inventory slots、findplayer 三个查询及三次模型解释，不让模型执行命令。结果保存在忽略目录 `runs/api-bug-acceptance/`；解释需人工对照原始返回值审查，不自动认定通过。

在专用测试世界中依次给予回血、中毒、凋零、生命提升、吸收，重复运行脚本核对等级和剩余时间；消耗黄心后再测一次。让另一玩家从主世界进入下界，确认 findplayer 仍可列出姓名但没有旧坐标。模型没有跨维度定位能力。

World 标签仍需浏览器实测：固定镜头让 agent 寻路，核对途中与终点标签跟随，随后转动镜头、切地图和隐藏/恢复页面。say 另用一条长测试消息，核对命令历史保留输入而结果只含发送状态。

以上为 PR 作者阶段的操作指南；维护者随后完成的实测如下。真实 DeepSeek 解释测试仍未执行。

## 维护者实测与补充修复（2026-11-01）

环境：本机原版 Minecraft Java 1.21.11 测试服务端，复用已有世界，Map-A/B/C 三个真实 Mineflayer 玩家，Edge headless 加载实际 World/Agent debug 页面。未调用付费模型；截图与原始结果仅保留在忽略目录 runs。

| 项目 | 结果 |
| --- | --- |
| 默认最大血量 | 首次登录 hp 与网页均为 20 / 20；原版未发送 max_health 覆盖包的情况已修复 |
| 五类药水 | 回血、中毒、凋零、生命提升、吸收均返回等级 2 和剩余时间；持续时间仍是客户端 tick 估计 |
| 最大血量变化 | 生命提升后上限 28，与服务端 attribute get 一致；自定义基础上限 26、恢复 20 均通过 |
| 黄心 | 清除伤害药水后，使用 magic 伤害使黄心 8 -> 5 -> 0；与服务端 AbsorptionAmount 一致；归零后省略吸收字段、文字及效果 |
| 残留吸收效果边界 | 保留药水但黄心为零的省略逻辑有包级自动覆盖；此次原版实机归零时吸收效果同时移除，不将其冒充残留效果实测 |
| 选中空槽 | 普通空槽省略；选中空快捷栏仅返回 slot/selected:true；与服务端 SelectedItemSlot 核对，随后恢复原选择 |
| say | 服务端日志收到长消息；API 返回不含全文回显，保留 confirmed:false |
| 目标换维度 | Map-B 进入下界后，主世界 Map-A 的 findplayer 不再返回旧坐标 |
| 观察者换维度 | Map-A 也进入下界后，旧主世界 Map-C 没有坐标/维度；新观察到的 Map-B 有下界坐标 |
| World 标签 | 真实 goto 从 x=20.5 到 x=27.5；三玩家、32 组运动采样的 CSS2D 世界矩阵与插值模型加标签高度一致；终点静止位置也一致，误差小于 1e-10 格 |
| 浏览器呈现 | Edge 截图确认真实地图、模型和标签可见；页面切到 Agent debug 再恢复 World 后模型与标签重新加载；Agent debug 显示 20 / 20 |

临时寻路平台已清除，三玩家位置已恢复，测试效果与临时吸收属性修改已清理。自动全库复跑 **586/586** 通过（包含观察者自身 respawn 残留实体、注册表默认最大血量两项新增回归）；无失败、跳过或取消。
