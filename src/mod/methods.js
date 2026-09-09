/**
 * 采集端 mod（stevex-template-1.21.11）WebSocket API 方法清单。
 *
 * 共 50 个方法，分组与参数 schema 已按 mod Java 源码逐条核实
 * （见 steveX_改进方案.md 附录 §十一；websocket 分发在 AgentWebSocketServer /
 * api\*Api.java 各 handler 内，方法名注册与参数读取是两套并存的 ad hoc 逻辑）。
 * 上层通过 POST /api/mod/:method 原样透传，mod 侧对未知方法会返回
 * { ok:false, error:'unknown method: …' }。
 *
 * 条目字段：
 *   method    方法名（含斜杠，透传路径用）
 *   category  分组键（稳定）：continuous / one-shot / camera / query / system /
 *             container / chat / sign / book / vision
 *   description  英文短描述（/api/skills 语义，历史兼容）
 *   zh        中文说明（Mod API 查询器展示 + 搜索用）。带参方法自带
 *             "params 填 {…}" 枚举可填键（? 表示可省略）；无参方法无可填。
 *   paramDefs 参数 schema 数组（权威，供查询器填参与 hints、未来 LLM 工具 schema）：
 *             { name, type: 'bool'|'int'|'double'|'string', required?, def?, hint, sample? }
 *             sample = 查询器预填的示例值（可与 def 不同以示可改）；无参数则 []。
 *
 * 参数读取语义（来自 mod 源码，直接影响填写建议）：
 *   - num()/bool() 读取的键：缺省或类型不符时静默回落默认值（def）。
 *   - (String)/(Number) 强转读取的键：缺省用默认值；类型不符会报错。
 *   - vision/entity.uuid 是唯一硬性必填（缺/空/非 UUID 都报错）。
 *   - 无参方法不读任何键，params 填 {} 或省略均可（mod 侧自动按 {} 处理）。
 */

const METHODS = [
  // ── 持续按键（9）── 按住需显式 pressed:false 松开；序列结束时服务端也会兜底松开
  { method: 'key/up', category: 'continuous', description: 'Hold forward',
    zh: '按住 W 前进；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/down', category: 'continuous', description: 'Hold back',
    zh: '按住 S 后退；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/left', category: 'continuous', description: 'Strafe left',
    zh: '按住 A 向左平移；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/right', category: 'continuous', description: 'Strafe right',
    zh: '按住 D 向右平移；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/jump', category: 'continuous', description: 'Hold jump',
    zh: '按住空格 跳跃；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/sprint', category: 'continuous', description: 'Hold sprint',
    zh: '按住 Ctrl 疾跑；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/sneak', category: 'continuous', description: 'Hold sneak',
    zh: '按住 Shift 潜行；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/attack', category: 'continuous', description: 'Hold attack',
    zh: '按住左键 挖掘/攻击；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },
  { method: 'key/use', category: 'continuous', description: 'Hold use item',
    zh: '按住右键 使用/进食/拉弓；params 填 { pressed }：true=按下持续 / false=松开',
    paramDefs: [{ name: 'pressed', type: 'bool', def: true, hint: 'true=按下并持续 / false=松开', sample: true }] },

  // ── 一次性按键 / 杂项（12）── 无参方法：params 填 {} 即可
  { method: 'key/attack-once', category: 'one-shot', description: 'Attack once',
    zh: '左键单击一次（空挥/攻击目标），无参数', paramDefs: [] },
  { method: 'key/use-once', category: 'one-shot', description: 'Use item once',
    zh: '右键单击一次（放置方块/使用物品），无参数', paramDefs: [] },
  { method: 'key/drop', category: 'one-shot', description: 'Drop held item',
    zh: '丢弃手持物品（Q），无参数', paramDefs: [] },
  { method: 'key/pick-item', category: 'one-shot', description: 'Pick block/item',
    zh: '拾取准星所指方块/物品（中键），无参数', paramDefs: [] },
  { method: 'key/inventory', category: 'one-shot', description: 'Open inventory',
    zh: '打开物品栏（E），无参数', paramDefs: [] },
  { method: 'key/swap-hands', category: 'one-shot', description: 'Swap hands',
    zh: '主副手互换（F），无参数', paramDefs: [] },
  { method: 'key/chat', category: 'one-shot', description: 'Open chat input',
    zh: '打开聊天输入框（T），无参数', paramDefs: [] },
  { method: 'key/command', category: 'one-shot', description: 'Open command input',
    zh: '打开命令输入框（/），无参数', paramDefs: [] },
  { method: 'key/perspective', category: 'one-shot', description: 'Toggle camera perspective',
    zh: '切换视角（F5），无参数', paramDefs: [] },
  { method: 'key/quick-actions', category: 'one-shot', description: 'Toggle quick actions',
    zh: '切换快速动作面板（F3+F），无参数', paramDefs: [] },
  { method: 'key/hotbar', category: 'one-shot', description: 'Select hotbar slot (0-8)',
    zh: '选择热键栏格子；params 填 { slot }：格子下标 0-8（越界静默忽略）',
    paramDefs: [{ name: 'slot', type: 'int', def: 0, hint: '热键栏下标 0-8，越界静默忽略', sample: 3 }] },
  { method: 'tablist', category: 'one-shot', description: 'Online players + scoreboard objective',
    zh: '读取 Tab 在线玩家列表（名字/延迟/队伍）+ 计分板目标，无参数', paramDefs: [] },

  // ── 视角（2）──
  { method: 'camera/turn', category: 'camera', description: 'Turn camera by deltas (yaw, pitch)',
    zh: '按增量旋转视角；params 填 { dx, dy }：dx=左右（偏航）、dy=上下（俯仰），单位度，自动取最短路径',
    paramDefs: [
      { name: 'dx', type: 'double', def: 0, hint: '左右旋转角度（度）', sample: 90 },
      { name: 'dy', type: 'double', def: 0, hint: '上下俯仰角度（度）', sample: -10 }
    ] },
  { method: 'camera/look-at', category: 'camera', description: 'Look at a world coordinate',
    zh: '注视世界坐标；params 填 { x, y, z }：目标坐标（建议三个都填，基于眼睛高度）',
    paramDefs: [
      { name: 'x', type: 'double', def: 0, hint: '目标 X 坐标', sample: 100 },
      { name: 'y', type: 'double', def: 0, hint: '目标 Y 坐标', sample: 70 },
      { name: 'z', type: 'double', def: 0, hint: '目标 Z 坐标', sample: 200 }
    ] },

  // ── 感知 / 查询（5）── 无参方法：params 填 {} 即可
  { method: 'player', category: 'query', description: 'Player state (position/health/facing…)',
    zh: '玩家状态：生命/食物/经验/坐标朝向/药水效果等，无参数', paramDefs: [] },
  { method: 'inventory', category: 'query', description: 'Inventory contents',
    zh: '背包内容：主手/副手/护甲/36 格物品及其组件（附魔/耐久等），无参数', paramDefs: [] },
  { method: 'f3', category: 'query', description: 'F3 debug screen data',
    zh: 'F3 调试数据：坐标/区块/环境(光照/生态/TPS)/准星目标(方块/实体)，无参数', paramDefs: [] },
  { method: 'sound', category: 'query', description: 'Sound environment',
    zh: '当前正在播放的所有声音快照（id/来源/音量/衰减等），无参数', paramDefs: [] },
  { method: 'profiler', category: 'query', description: 'Performance profiler',
    zh: '性能分析饼图递归树（% / 次数 / 子节点；需先 F3+1 采集），无参数', paramDefs: [] },

  // ── 系统（3）──
  { method: 'status', category: 'system', description: '{ busy:bool, error:string } — true when a non-status method is running',
    zh: '系统状态 {busy, error}：busy=是否正有非 status 方法在执行，error=最近一次错误，无参数', paramDefs: [] },
  { method: 'settings/get', category: 'system', description: 'Read settings',
    zh: '读取设置：renderDistance / simulationDistance / fov，无参数', paramDefs: [] },
  { method: 'settings/set', category: 'system', description: 'Write settings',
    zh: '修改设置并保存；params 填 { renderDistance?, simulationDistance?, fov? }：提供哪些就改哪些，返回改后三值',
    paramDefs: [
      { name: 'renderDistance', type: 'int', def: 12, hint: '渲染距离（出现才改）', sample: 12 },
      { name: 'simulationDistance', type: 'int', def: 12, hint: '模拟距离（出现才改）', sample: 12 },
      { name: 'fov', type: 'int', def: 70, hint: '视野（出现才改）', sample: 80 }
    ] },

  // ── 容器（7）──
  { method: 'container/get', category: 'container', description: 'Get open container contents',
    zh: '读取当前打开的容器：类型/槽位物品/携带物品，及熔炉/附魔台/箱子等专用字段，无参数', paramDefs: [] },
  { method: 'container/slot', category: 'container', description: 'Interact with a container slot',
    zh: '点击容器格；params 填 { slot, button?, clickType? }：slot=格下标（先 container/get 看布局），button=0左键/1右键，clickType=0拾取 1快捷移动 2交换 4丢弃…',
    paramDefs: [
      { name: 'slot', type: 'int', def: 0, hint: '容器格下标（先 container/get 查看布局）', sample: 1 },
      { name: 'button', type: 'int', def: 0, hint: '0=左键 / 1=右键', sample: 0 },
      { name: 'clickType', type: 'int', def: 0, hint: '0=拾取 1=快捷移动 2=交换 4=丢弃 5=合成…', sample: 0 }
    ] },
  { method: 'container/button', category: 'container', description: 'Click a container button',
    zh: '点击容器界面顶部的按钮（如熔炉开关/附魔选项）；params 填 { button }：按钮序号（从 0 起），返回 {status, accepted}',
    paramDefs: [{ name: 'button', type: 'int', def: 0, hint: '界面按钮序号（从 0 起）', sample: 0 }] },
  { method: 'container/close', category: 'container', description: 'Close container screen',
    zh: '关闭打开的容器界面，无参数', paramDefs: [] },
  { method: 'container/text', category: 'container', description: 'Type into a container text field',
    zh: '向当前聚焦的输入框写入文字（任意界面，含告示牌/书的输入框）；params 填 { text }：要填入的内容',
    paramDefs: [{ name: 'text', type: 'string', def: '', hint: '要填入的文字', sample: 'hello' }] },
  { method: 'container/drag', category: 'container', description: 'Send one QuickCraft drag phase',
    zh: '发送一次拖拽的单个相位包（QuickCraft，纯原语不取物）。params 填 { phase, slot?, type? }：phase 0=begin 1=add 2=finish，add 必带 slot=container/get 的真实格 index（begin/finish 省略 slot），type 0=均分(默认) 1=每格放1 2=创造克隆。整段手势=先 container/slot clickType:0 拾取源格→phase 0→对每格 phase:1→phase:2，每条隔≥1 tick(waitMs≥50)，期间不得夹其它点击；返回 {status}',
    paramDefs: [
      { name: 'phase', type: 'int', def: 0, hint: '0=begin 1=add 2=finish', sample: 0 },
      { name: 'slot', type: 'int', def: -999, hint: '目标格真实 index（container/get 的 slot）；仅 add 需要', sample: 9 },
      { name: 'type', type: 'int', def: 0, hint: '0=均分(默认) 1=每格放1 2=创造克隆', sample: 0 }
    ] },
  { method: 'container/beacon', category: 'container', description: 'Set beacon effect & consume payment',
    zh: '设置/切换信标效果并扣支付物（等价信标 GUI 的点效果图标+点√，直接发 SetBeacon 包）。params 填 { primary?, secondary? }：效果注册 id 如 "minecraft:haste"，缺省/空=该槽不设。前置：信标界面已开，支付物已用 container/slot 放入支付槽（menu slot 0），服务端无支付物时为空操作；1 级信标主效果可选 haste/speed，二级留空',
    paramDefs: [
      { name: 'primary', type: 'string', def: '', hint: '主效果注册 id，如 minecraft:haste（缺省/空=不设）', sample: 'minecraft:haste' },
      { name: 'secondary', type: 'string', def: '', hint: '副效果注册 id（信标≥4 级才可用；缺省/空=不设）', sample: 'minecraft:regeneration' }
    ] },

  // ── 聊天（3）──
  { method: 'chat/text', category: 'chat', description: 'Type into the open chat box (no send)',
    zh: '把文字填入已打开的聊天输入框，不发送（需先 key/chat）；params 填 { text }：要填入的内容',
    paramDefs: [{ name: 'text', type: 'string', def: '', hint: '填入聊天框的文字（不发送）', sample: 'hello' }] },
  { method: 'chat/send', category: 'chat', description: 'Send a chat message / run a command',
    zh: '发送聊天消息，/ 开头作为命令执行；params 填 { text }：内容，聊天框已开则填入发送，未开则直发（空文本且未开框会报错）',
    paramDefs: [{ name: 'text', type: 'string', def: '', hint: '聊天内容；未开聊天框时必填（空会报错），/ 开头=命令', sample: 'hello' }] },
  { method: 'chat/close', category: 'chat', description: 'Close chat screen',
    zh: '关闭聊天输入框（Esc），无参数', paramDefs: [] },

  // ── 告示牌（3）──
  { method: 'sign/get', category: 'sign', description: 'Read sign text',
    zh: '读取告示牌编辑器：4 行文字与当前编辑行（需先编辑告示牌），无参数', paramDefs: [] },
  { method: 'sign/set', category: 'sign', description: 'Write sign text',
    zh: '设置告示牌某行文字；params 填 { line?, text }：line=行号 0-3（省略=当前编辑行），text=新文字',
    paramDefs: [
      { name: 'line', type: 'int', def: -1, hint: '行号 0-3；省略=当前编辑行', sample: 0 },
      { name: 'text', type: 'string', def: '', hint: '要写入该行的文字', sample: 'hello' }
    ] },
  { method: 'sign/close', category: 'sign', description: 'Close sign editor',
    zh: '保存并关闭告示牌编辑，无参数', paramDefs: [] },

  // ── 书与笔（4）──
  { method: 'book/get', category: 'book', description: 'Read book content',
    zh: '读取书的全部页与当前页（需先编辑书），无参数', paramDefs: [] },
  { method: 'book/set', category: 'book', description: 'Write book content',
    zh: '覆写某页文字；params 填 { page?, text }：page=页号（省略=当前页，==总页数=追加一页，上限 100），text=新文字',
    paramDefs: [
      { name: 'page', type: 'int', def: -1, hint: '页号；省略=当前页；==总页数=追加一页', sample: 0 },
      { name: 'text', type: 'string', def: '', hint: '该页的新文字', sample: 'hello' }
    ] },
  { method: 'book/page', category: 'book', description: 'Turn book page',
    zh: '翻到指定页；params 填 { page }：页号（0 起，越界报错）',
    paramDefs: [{ name: 'page', type: 'int', def: 0, hint: '要翻到的页号（0 起）', sample: 1 }] },
  { method: 'book/close', category: 'book', description: 'Close book screen',
    zh: '保存并关闭书，无参数', paramDefs: [] },

  // ── 视觉（2，对应视觉系统方案 §6.2）──
  { method: 'vision/snapshot', category: 'vision', description: 'Depth capture + visible blocks/entities + store stats',
    zh: '深度采集 + 可见方块/方块实体/实体快照并落盘 NBT（首次最久约 15s），无参数', paramDefs: [] },
  { method: 'vision/entity', category: 'vision', description: 'Full NBT of a single entity by uuid',
    zh: '按 uuid 查询单个实体全量 NBT；params 填 { uuid(必填), force? }：uuid=实体 UUID（来自 f3 准星目标或 vision/snapshot），force=true 跳过缓存强制重扫',
    paramDefs: [
      { name: 'uuid', type: 'string', required: true, hint: '实体 UUID（来自 f3 准星目标或 vision/snapshot）', sample: '00000000-0000-0000-0000-000000000000' },
      { name: 'force', type: 'bool', def: false, hint: 'true=跳过缓存强制重扫', sample: false }
    ] },
]

/** 纯方法名数组（48），供前端 /api/skills 与状态页复用。 */
const METHOD_NAMES = METHODS.map(m => m.method)

/** 按 category 分组的 Map，便于展示。 */
function methodsByCategory() {
  const map = new Map()
  for (const m of METHODS) {
    if (!map.has(m.category)) map.set(m.category, [])
    map.get(m.category).push(m)
  }
  return map
}

module.exports = { METHODS, METHOD_NAMES, methodsByCategory }
