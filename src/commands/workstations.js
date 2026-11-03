const { itemView, compactItemView } = require('../agent/interaction_memory')
const states = new WeakMap()
const stations = {
  brewing: ['brewing_stand', 'minecraft:brewing_stand'], loom: ['loom', 'minecraft:loom'],
  stonecutter: ['stonecutter', 'minecraft:stonecutter'], smithing: ['smithing_table', 'minecraft:smithing'],
  grindstone: ['grindstone', 'minecraft:grindstone'], cartography: ['cartography_table', 'minecraft:cartography'],
  beacon: ['beacon', 'minecraft:beacon'], crafter: ['crafter', 'minecraft:crafter_3x3']
}

function integer(value, label, min = 0, max = Number.MAX_SAFE_INTEGER) {
  if (value === undefined || !/^\d+$/.test(String(value))) throw new Error(`Invalid ${label}`)
  const n = Number(value)
  if (!Number.isSafeInteger(n) || n < min || n > max) throw new Error(`Invalid ${label}: expected ${min}..${max}`)
  return n
}

function attach(bot) {
  if (states.has(bot)) return states.get(bot)
  const state = { properties: new WeakMap() }
  const update = packet => {
    const w = bot.currentWindow
    if (!w || packet.windowId !== w.id) return
    let properties = state.properties.get(w)
    if (!properties) state.properties.set(w, properties = {})
    properties[packet.property] = packet.value
    bot.emit('workstation:update', w)
  }
  bot._client.on('craft_progress_bar', update)
  const opened = w => {
    if (bot.version === '1.21.11' && w.type === 'minecraft:smithing') {
      // prismarine-windows 2.10 still uses the pre-template layout for smithing.
      w.inventoryStart = 4; w.inventoryEnd = 40; w.hotbarStart = 31; w.craftingResultSlot = 3
      if (w.slots.length < 40) w.slots.length = 40
    }
  }
  bot.on('windowOpen', opened)
  if (bot.currentWindow) opened(bot.currentWindow)
  bot.once('end', () => {
    bot._client.removeListener('craft_progress_bar', update); bot.removeListener('windowOpen', opened); states.delete(bot)
  })
  states.set(bot, state)
  return state
}

function current(bot, types) {
  const w = bot.currentWindow
  if (!w) throw Object.assign(new Error('No window open; aim and use right click first'),{code:'WINDOW_REQUIRED'})
  if (types && !types.includes(w.type)) throw Object.assign(new Error(`Wrong window: ${w.type}; expected ${types.join(' / ')}`),{code:'WINDOW_TYPE_MISMATCH'})
  return w
}
function properties(bot, w = current(bot)) { return attach(bot).properties.get(w) || {} }
function slot(w, value, inventoryOnly = false) {
  const logical=integer(value,'slot',inventoryOnly?9:0,inventoryOnly?44:w.inventoryStart-1)
  const index=inventoryOnly?w.inventoryStart+logical-9:logical
  if (!w.slots[index]) throw new Error(`Slot ${index} is empty`)
  return w.slots[index]
}

function describe(bot, { compact = false } = {}) {
  const w = current(bot), p = properties(bot, w)
  const view = compact ? require('./item_details').brief : itemView
  const data = compact ? {
    type: w.type, windowId:w.id, slotCount: w.slots.length, windowSlotCount:w.inventoryStart, playerSlotRange:[9,44],
    slots: Array.from({ length: w.slots.length }, (_, index) => {
      if(!w.slots[index])return null
      const {slot,...item}=view(w.slots[index]);return {...item,...require('./slot_scope').logicalSlot(w,index)}
    }).filter(Boolean),
    emptySlots: Array.from({ length: w.slots.length }, (_, slot) => slot).filter(slot => !w.slots[slot]).map(slot=>require('./slot_scope').logicalSlot(w,slot)),
    ...(w.selectedItem ? { cursor: view(w.selectedItem) } : {})
  } : { windowId: w.id, type: w.type, windowSlotCount:w.inventoryStart, playerSlotRange:[9,44],
    source: 'mineflayer-window-cache', properties: { ...p },
    slots: w.slots.map((item, index) => {const data=itemView(item);if(data)delete data.slot;return {...require('./slot_scope').logicalSlot(w,index),item:data}}), cursor: itemView(w.selectedItem) }
  const resultSlot=require('./slot_scope').resultSlot(w)
  if(resultSlot!==null)data.resultSlot={...require('./slot_scope').logicalSlot(w,resultSlot),takeOnly:true}
  if (w.trades) data.trades = w.trades.map((t, index) => ({ index, input: view(t.inputItem1),
    secondInput: view(t.inputItem2), output: view(t.outputItem), price: t.realPrice,
    disabled: t.tradeDisabled, uses: t.nbTradeUses, maxUses: t.maximumNbTradeUses }))
  if (w.enchantments) data.enchantments = w.enchantments
  if (w.type === 'minecraft:brewing_stand') data.brewing = { remainingTicks: p[0] ?? null, fuelUses: p[1] ?? null, maxFuelUses: 20 }
  if (w.type === 'minecraft:crafter_3x3') data.disabledSlots = Array.from({ length: 9 }, (_, i) => p[i] === undefined ? null : p[i] === 1)
  if (w.type === 'minecraft:loom' || w.type === 'minecraft:stonecutter') data.selectedIndex = p[0] ?? null
  if (w.type === 'minecraft:beacon') data.beacon = { levels: p[0] ?? null, primaryEncoded: p[1] ?? null, secondaryEncoded: p[2] ?? null }
  if (['minecraft:furnace', 'minecraft:blast_furnace', 'minecraft:smoker'].includes(w.type)) {
    if (compact) {
      const percent = (value, total) => Number.isFinite(value) && Number.isFinite(total) && total >= 0
        ? total === 0 ? 0 : Math.round(value / total * 1000) / 10 : null
      const fuelPercent = percent(p[0], p[1]), progressPercent = percent(p[2], p[3])
      data.furnace = { ...(fuelPercent === null ? {} : { fuelPercent }), ...(progressPercent === null ? {} : { progressPercent }) }
    } else data.furnace = { fuelTicks: p[0] ?? null, totalFuelTicks: p[1] ?? null, progressTicks: p[2] ?? null, totalProgressTicks: p[3] ?? null }
  }
  return data
}

async function guarded(operation) {
  try { return await operation() }
  catch (error) { return { ok: false, error: error.message, ...(error.code?{errorCode:error.code}:{}), cancelled: error.name === 'AbortError' } }
}
function success(bot, output) { return { ok: true, output, window: bot.currentWindow ? describe(bot) : null } }

function confirmedPacket(bot, w, packet, body, expected, signal) {
  signal?.throwIfAborted()
  if (bot.version !== '1.21.11') throw new Error('Raw workstation adapters currently support Minecraft 1.21.11 only')
  if (expected(properties(bot, w))) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const cleanup = () => {
      clearTimeout(timer)
      bot.removeListener('workstation:update', update); bot.removeListener('windowClose', close); bot.removeListener('end', end)
      w.removeListener('updateSlot', slotsChanged)
      signal?.removeEventListener('abort', abort)
    }
    const finish = error => { cleanup(); error ? reject(error) : resolve() }
    const update = window => { if (window === w && expected(properties(bot, w))) finish() }
    const slotsChanged = () => update(w)
    const close = window => { if (window === w) finish(new Error('Window closed before server confirmation')) }
    const end = () => finish(new Error('Disconnected before server confirmation'))
    const abort = () => finish(signal.reason || new Error('Operation cancelled'))
    const timer = setTimeout(() => finish(new Error('No server confirmation; request may have applied, inspect window before retrying')), 5000)
    bot.on('workstation:update', update); bot.on('windowClose', close); bot.on('end', end)
    w.on('updateSlot', slotsChanged)
    signal?.addEventListener('abort', abort, { once: true })
    try { bot._client.write(packet, body) } catch (error) { finish(error) }
  })
}

async function station(bot, args = [], signal) {
  signal?.throwIfAborted()
  if (args[0] === 'open') {
    throw Object.assign(new Error('Aim at the workstation and use right click; station only operates an existing window'),{code:'INVALID_ARGUMENTS'})
  }
  const w = current(bot, Object.values(stations).map(def=>def[1]))
  attach(bot)
  if (args[0] === 'move') {
    throw Object.assign(new Error('station move was removed; use moveslot <inventory:slot|window:slot> <inventory:slot|window:slot> [count]'),{code:'INVALID_ARGUMENTS'})
  } else if (args[0] === 'take') {
    const index = integer(args[1], 'container slot', 0, w.inventoryStart - 1)
    slot(w, String(index)); await bot.putAway(index)
  } else if (args[0] === 'select') {
    current(bot, ['minecraft:loom', 'minecraft:stonecutter'])
    const index = integer(args[1], 'button index', 0, 127)
    const output = w.type === 'minecraft:loom' ? 3 : 1
    await confirmedPacket(bot, w, 'enchant_item', { windowId: w.id, enchantment: index },
      p => p[0] === index && !!w.slots[output], signal)
  } else if (args[0] === 'crafter') {
    current(bot, ['minecraft:crafter_3x3'])
    const index = integer(args[1], 'crafter slot', 0, 8)
    if (!['enabled', 'disabled'].includes(args[2])) throw new Error('Expected enabled or disabled')
    if (w.slots[index] || w.selectedItem) throw new Error('Crafter state changes require an empty slot and cursor')
    const enabled = args[2] === 'enabled'
    await confirmedPacket(bot, w, 'set_slot_state', { slot_id: index, window_id: w.id, state: enabled },
      p => p[index] === (enabled ? 0 : 1), signal)
  } else if (args[0] === 'beacon') {
    current(bot, ['minecraft:beacon'])
    const normalize = value => String(value || '').replace(/^minecraft:/, '').replace(/_/g, '').toLowerCase()
    const primary = normalize(args[1]), secondary = normalize(args[2] || 'none')
    const tiers = { speed: 1, haste: 1, resistance: 2, jumpboost: 2, strength: 3 }, levels = properties(bot, w)[0]
    if (!Object.hasOwn(tiers, primary) || !(levels >= tiers[primary])) throw new Error('Primary effect unavailable for the known beacon level')
    if (secondary !== 'none' && (levels < 4 || ![primary, 'regeneration'].includes(secondary))) throw new Error('Secondary effect requires level 4 and must be regeneration or the primary effect')
    const effectId = name => {
      const effect = Object.values(bot.registry.effects).find(effect => normalize(effect.name) === name)
      if (!effect) throw new Error(`Effect not found in registry: ${name}`)
      return effect.id
    }
    const primaryId = effectId(primary), secondaryId = secondary === 'none' ? null : effectId(secondary)
    if (!['iron_ingot', 'gold_ingot', 'diamond', 'emerald', 'netherite_ingot'].includes(w.slots[0]?.name)) throw new Error('Put a payment item in beacon slot 0')
    await confirmedPacket(bot, w, 'set_beacon_effect', { primary_effect: primaryId, secondary_effect: secondaryId },
      p => p[1] === primaryId + 1 && p[2] === (secondaryId === null ? 0 : secondaryId + 1), signal)
  } else throw new Error('Usage: station take <slot>|select <index>|crafter <slot> <enabled|disabled>|beacon <primary> [secondary|none]')
  const verify=()=>args[0]==='crafter'?properties(bot,w)[Number(args[1])] === (args[2]==='enabled'?0:1):args[0]==='select'?properties(bot,w)[0]===Number(args[1]):args[0]==='take'?!w.slots[Number(args[1])]:true
  return {...success(bot, `Station operation ${args.join(' ')} completed`),verify,allowUnchanged:['crafter','select'].includes(args[0])}
}
module.exports = { attach, integer, current, slot, describe, guarded, success, station }
