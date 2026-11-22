const decimal = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i
const operations = new Set(['dig', 'collect', 'craft', 'right'])
const effectCommands = new Set([...operations, 'equip', 'hotbar', 'unequip', 'drop','attack','interactentity','slotclick','moveslot','putaway','transfer','fish','furnace','enchant','anvil','station','trade'])
const queries = new Set('pos hp hunger oxygen inventory slotinfo nearby gamemode xp time weather window findplayer findblock recipes'.split(' '))
const finite = value => decimal.test(value) && Number.isFinite(Number(value))
const coordinate = value => finite(value) && Math.abs(Number(value)) <= Number.MAX_SAFE_INTEGER
const relativeCoordinate = value => value==='~'||coordinate(value.startsWith('~')?value.slice(1):value)
const integer = (min, max = Number.MAX_SAFE_INTEGER) => value => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) >= min && Number(value) <= max
const registryName = value => /^(?:minecraft:)?[a-z0-9_]+$/i.test(value)
const slotReference=require('./slot_scope').isSlotRef
const field = (name, check, expected, values) => ({ name, check, expected, values })
const enumField = (name, values) => field(name, value => values.includes(value.toLowerCase()), values.join(' | '), values)
const contracts = Object.fromEntries('pos hp hunger oxygen inventory gamemode xp time weather window jump stop collect'.split(' ').map(name => [name, { usage:name, min:0, fields:[] }]))
Object.assign(contracts, {
  inventory:{usage:'inventory [slots]',min:0,fields:[enumField('mode',['slots'])]},
  slotinfo:{usage:'slotinfo <ref>',min:1,fields:[field('slot',slotReference,'a typed slot reference')]},
  fish: {usage:'fish',min:0,fields:[]},
  attack: {usage:'attack <entityName|id>',min:1,fields:[field('target',()=>true,'entity name fragment or exact numeric id')]},
  collect: {usage:'collect [entityId]',min:0,fields:[field('entityId',integer(0),'nonnegative entity id')]},
  slotclick: {usage:'slotclick <ref> [left|right|shift-left]',min:1,fields:[field('slot',slotReference,'a typed slot reference'),enumField('button',['left','right','shift-left'])]},
  moveslot: {usage:'moveslot <sourceRef> <destRef> [count]',min:2,fields:[field('source',slotReference,'a typed slot reference'),field('destination',slotReference,'a typed slot reference'),field('count',integer(1,64),'item count 1..64')]},
  putaway: {usage:'putaway <ref>',min:1,fields:[field('slot',slotReference,'a typed slot reference')]},
  transfer: {usage:'transfer <deposit|withdraw> <sourceSlot> [count]',min:2,fields:[enumField('direction',['deposit','withdraw']),field('sourceSlot',slotReference,'explicit source reference'),field('count',integer(1,64),'item count 1..64')]},
  wake: {usage:'wake',min:0,fields:[]},
  respawn: {usage:'respawn',min:0,fields:[]},
  jump: {usage:'jump [durationMs]',min:0,fields:[field('durationMs',integer(1,30000),'integer milliseconds 1..30000')]},
  sneak: {usage:'sneak <on|off|tap>',min:1,fields:[enumField('mode',['on','off','tap'])]},
  right: {usage:'right <click|down> [hand|off-hand] | right up',min:1,fields:[enumField('operation',['click','down','up']),enumField('hand',['hand','off-hand'])]},
  dig: {usage:'dig [stop|<x> <y> <z>]',min:0,fields:['x','y','z'].map(name=>field(name,v=>coordinate(v)&&Number.isSafeInteger(Number(v)),'safe integer block coordinate'))},
  interactentity: {usage:'interactentity <entityName|id> [normal|at] [hand|off-hand]',min:1,fields:[field('target',()=>true,'one entity name fragment or numeric entity id'),enumField('mode',['normal','at']),enumField('hand',['hand','off-hand'])]},
  nearby: { usage:'nearby [type|name|all] [radius]', min:0, fields:[field('filter',()=>true,'entity type/name/all'),field('radius',integer(1,128),'integer 1..128')] },
  findplayer: { usage:'findplayer <name>', min:1, fields:[field('name',()=>true,'one player-name fragment')] },
  findblock: { usage:'findblock <blockName|#tag> [radius] [count] | findblock tags [filter] | findblock tag <#tag>', min:1, fields:[field('blockName',v=>registryName(v)||/^#(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$/i.test(v),'exact block name or #namespace:tag'),field('radius',integer(1,128),'integer 1..128'),field('count',integer(1,16),'integer result limit 1..16 (default 1)')] },
  recipes: { usage:'recipes <itemName>', min:1, fields:[field('itemName',registryName,'exact registry name, optional minecraft: prefix')] },
  look: { usage:'look angle <yaw> <pitch> | look at <x> <y> <z> | look block <x> <y> <z>', min:3, fields:[enumField('mode',['angle','at','block']),...['a','b','c'].map(name=>field(name,()=>true,'decimal or ~ relative value'))] },
  goto: { usage:'goto <x> <y> <z>', min:3, fields:['x','y','z'].map(name=>field(name,relativeCoordinate,'finite decimal or ~ relative coordinate within safe numeric range')) },
  move: { usage:'move <forward|back|left|right|sprint|stop> [durationMs]', min:1, fields:[enumField('control',['forward','back','left','right','sprint','stop']),field('durationMs',integer(1,30000),'integer milliseconds 1..30000')] },
  swing: { usage:'swing [hand|off-hand]', min:0, fields:[enumField('hand',['hand','off-hand'])] },
  follow: { usage:'follow <playerName>', min:1, fields:[field('playerName',()=>true,'one exact player name')] },
  wait: { usage:'wait <ticks>', min:1, fields:[field('ticks',integer(1),'positive safe integer physics ticks')] },
  craft: { usage:'craft <itemName> [times] [auto|hand|table] [recipeIndex]', min:1, fields:[field('itemName',registryName,'exact registry name, optional minecraft: prefix'),field('times',integer(1,64),'integer recipe executions 1..64'),enumField('station',['auto','hand','table']),field('recipeIndex',v=>/^[1-9]\d*$/.test(v)&&Number.isSafeInteger(Number(v)),'positive safe integer catalog index')] },
  equip: { usage:'equip item <itemName> [destination] | equip slot <slot> [destination] | equip <itemName> [destination]', min:1, fields:[field('source',()=>true,'item name, item or slot'),field('selection',()=>true,'item name, slot or destination'),enumField('destination',['hand','head','torso','legs','feet','off-hand'])] },
  hotbar: { usage:'hotbar <index>', min:1, fields:[field('index',integer(1,9),'keyboard hotbar index 1..9')] },
  unequip: { usage:'unequip [hand|head|torso|legs|feet|off-hand]', min:0, fields:[enumField('destination',['hand','head','torso','legs','feet','off-hand'])] },
  drop: {usage:'drop <itemName> [count] | drop slot <slot> [count]',min:1,fields:[field('itemName',registryName,'exact registry item name or slot'),field('selection',()=>true,'item count or inventory: or hotbar: reference'),field('count',integer(1),'positive item count')]},
})

function normalizeFailure(name, value) {
  if ((!contracts[name]&&!effectCommands.has(name)) || value?.ok !== false) return value
  const errorCode = value.errorCode || (value.cancelled ? 'CANCELLED' : queries.has(name) ? 'QUERY_FAILED' : 'ACTION_FAILED')
  const prefix = `[${errorCode}] `
  const oldError = typeof value.error === 'string' && value.error ? value.error : 'Command did not complete'
  const reason = oldError.startsWith(prefix) ? oldError.slice(prefix.length) : oldError
  const error = prefix + reason
  const oldOutput = typeof value.output === 'string' ? value.output : ''
  const text = oldOutput.startsWith(prefix) ? oldOutput.slice(prefix.length) : oldOutput
  // Keep partial effects and detailed recipe feedback verbatim, visible to Brain.
  const output = text ? `${prefix}${text}${!text.includes(reason) ? '\n' + reason : ''}` : error
  return { ...value, errorCode, error, output }
}
function invalid(name, message, argument) {
  const spec = contracts[name], error = `${message}. Usage: ${spec.usage}`
  return normalizeFailure(name, { ok:false, errorCode:'INVALID_ARGUMENTS', error, usage:spec.usage, ...(argument ? { argument } : {}),
    ...(name==='attack'?{hit:false}:{}),
    ...(effectCommands.has(name) ? { action:name, status:'failed', confirmed:false, target:null, changes:[], evidence:[] } : {}),
    ...(name === 'craft' ? { phase:'preflight', effect:'none', completedExecutions:0, producedCount:0 } : {}) })
}
function validateArguments(name, args) {
  if(name==='drop'){
    if(!Array.isArray(args)||args.some(v=>typeof v!=='string'||!v||/\s/.test(v)))return invalid(name,'Expected exact name [count] or slot inventory: or hotbar: reference [count]')
    if(args[0]?.toLowerCase()==='slot')return args.length>=2&&args.length<=3&&require('./slot_scope').storageIndex(args[1])>=0&&(args[2]===undefined||integer(1)(args[2]))?null:invalid(name,'drop slot requires inventory: or hotbar: reference and optional positive count')
    return args.length>=1&&args.length<=2&&registryName(args[0])&&(args[1]===undefined||integer(1)(args[1]))?null:invalid(name,'Expected exact item name and optional positive count')
  }
  if (name === 'findblock' && Array.isArray(args) && typeof args[0] === 'string' && ['tags','tag'].includes(args[0].toLowerCase())) {
    const mode=args[0].toLowerCase()
    if (args.length > 2 || (mode === 'tag' && args.length !== 2)) return invalid(name, 'Expected tags [filter] or tag <#tag>')
    if (args[1] !== undefined && (typeof args[1] !== 'string' || !(mode === 'tag' ? /^#(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$/i : /^[a-z0-9_./:#-]+$/i).test(args[1]))) return invalid(name, 'Invalid tag name/filter; tag lookup requires #')
    return null
  }
  if (name === 'equip') return require('./equip_input').parseEquip(args) ? null : invalid(name, 'Use an exact item name or a inventory: or hotbar: reference; destination: hand/head/torso/legs/feet/off-hand')
  if (name === 'right' && Array.isArray(args) && typeof args[0] === 'string' && args[0].toLowerCase() === 'up' && args.length !== 1) return invalid(name, 'right up takes no hand argument')
  if (name === 'look') {
    const reason = require('./look_input').validateLook(args)
    return reason ? invalid(name, reason) : null
  }
  if (name === 'move' && Array.isArray(args) && typeof args[0] === 'string' && args[0].toLowerCase() === 'stop' && args.length !== 1) return invalid(name, 'move stop takes no duration')
  const spec = contracts[name]
  if (!spec) return null
  if (!Array.isArray(args) || args.length < spec.min || args.length > spec.fields.length) return invalid(name, `Expected ${spec.min === spec.fields.length ? spec.min : spec.min + '..' + spec.fields.length} arguments`)
  if (name === 'dig') {
    if (args.length === 1 && typeof args[0] === 'string' && args[0].toLowerCase() === 'stop') return null
    if (args.length !== 0 && args.length !== 3) return invalid(name, 'Expected no arguments, stop, or three integer coordinates')
  }
  for (let i=0;i<args.length;i++) {
    const f = spec.fields[i], value = args[i]
    if (typeof value !== 'string' || !value || /\s/.test(value) || !f.check(value)) return invalid(name, `${f.name} must be ${f.expected}`, f.name)
  }
  return null
}
function exceptionFailure(name, error, signal) {
  const cancelled = error?.name === 'AbortError' || signal?.aborted === true
  const code = signal?.reason?.code || error?.code
  const errorCode = code === 'ACTION_TIMEOUT' ? code : cancelled ? 'CANCELLED' :
    (typeof code==='string'&&/^[A-Z][A-Z0-9_]+$/.test(code)?code:({NoPath:'PATH_NOT_FOUND',Timeout:'PATH_TIMEOUT',GoalChanged:'PATH_INTERRUPTED',PathStopped:'PATH_INTERRUPTED',Disconnected:'DISCONNECTED'})[error?.name])
  return normalizeFailure(name, { ok:false, cancelled, errorCode, error:error?.message || String(error) })
}
const wrapped = Symbol('command-contract')
function feedback(name,value) {
  if(!effectCommands.has(name))return normalizeFailure(name,value)
  return normalizeFailure(name,{action:name,status:value?.ok===false?(value.cancelled?'cancelled':'failed'):value?.confirmed===true?'completed':'requested',
    confirmed:value?.confirmed===true,evidence:[],...value})
}
function withContract(name, handler) {
  const subcommand = {rightclick:'click',rightdown:'down',rightup:'up'}[name]
  if (subcommand) {
    const guarded = withContract('right', function(bot,args,agent,options) { return handler.call(this,bot,args.slice(1),agent,options) })
    return function(bot,args=[],agent,options) { return guarded.call(this,bot,[subcommand,...args],agent,options) }
  }
  if (!contracts[name] || handler[wrapped]) return handler
  const guarded = async function (bot, args = [], agent, options = {}) {
    const failure = validateArguments(name,args)
    if (failure) return failure
    const normalized = name === 'dig' && args.length === 1 ? ['stop'] : args.map((value,i)=>contracts[name].fields[i].values ? value.toLowerCase() : value)
    try { return feedback(name,await handler.call(this,bot,normalized,agent,options)) }
    catch (error) { return feedback(name,exceptionFailure(name,error,options.signal)) }
  }
  guarded[wrapped] = true
  return guarded
}
module.exports = { contracts, effectCommands, validateArguments, normalizeFailure, exceptionFailure, withContract }
