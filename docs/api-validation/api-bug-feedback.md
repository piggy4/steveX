# 浏览器标签与 API 精简反馈回归

2026-11-01。区分自动回归、真实游戏和真实模型验收，不将模拟包覆盖写成实机通过。

## 本次变更

- World name tag：3D 模型每次插值后，同步浏览器原生玩家标签的位置及 CSS2D 使用的世界矩阵，包含寻路停止后的终点；不修改 bot 坐标。部署时须重新运行 `scripts/install-map-player-models.js <BlueMap webroot> <1.21.11 client jar>` 并刷新地图页面。
- 最大血量：main 已有共享 `healthState`，供 hp、Agent debug、状态路由使用。本次保留该实现，新增 Mineflayer 1.21.11 实体属性包回归，验证 `generic.max_health` 与生命提升修饰符计算 20 -> 28 -> 20；没有属性数据时仍为未知，不能硬编码 20。
- hp：回血、中毒、凋零、生命提升均返回等级与时间来源。吸收只在观测到剩余黄心大于 0 时返回剩余量及药水信息，耗尽后即使药水仍在也省略。数值使用生命点（2 点 = 1 颗心），持续时间为客户端 tick 估计，不承诺低 TPS 下精确秒数。
- `inventory slots`：普通空槽省略；选中的空槽返回 `{ "slot": 39, "selected": true }` 这类最小记录。开容器后仍用固定玩家槽号。
- say：保留发送与 `confirmed:false`，输出不再重复消息全文；不能将“请求发送”解释为服务端执行成功。
- findplayer：只对仍在 `bot.entities` 中的有效实体返回坐标，附当前维度；销毁、过期引用或 ID 复用后省略坐标。客户端 tab list 本身无法区分跨维度、同维度远处或暂未追踪；不能从姓名推断另一维度的坐标。

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

本机没有运行 Minecraft/Web 测试服务，也没有提供 DeepSeek 密钥，本轮未执行以上真实游戏/真实模型验收。
