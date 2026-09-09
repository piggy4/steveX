/**
 * 连续键（按住型）方法集：`params.pressed:true` = 按住、`pressed:false` = 松开。
 *
 * 批量时序 runner（src/web/server/mod_batch.js）用它在序列结束 / 中止 / 连接断开时
 * 兜底补发松开（pressed:false），保证玩家不会因异常中断而持续移动。列表与旧浏览器端
 * agents.js 的 continuousKeyMethods 同源，现上移为服务端/前端共享常量（单点维护）。
 */
const PRESSABLE_METHODS = Object.freeze([
  'key/up',
  'key/down',
  'key/left',
  'key/right',
  'key/jump',
  'key/sprint',
  'key/sneak',
  'key/attack',
  'key/use'
])

/** O(1) 判定用的 Set。 */
const PRESSABLE_SET = new Set(PRESSABLE_METHODS)

function isPressableMethod(method) {
  return PRESSABLE_SET.has(method)
}

module.exports = { PRESSABLE_METHODS, PRESSABLE_SET, isPressableMethod }
