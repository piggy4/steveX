const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const api = require('../src/commands/workstations')
const native = require('../src/commands/workstation_native')
const trade = require('../src/commands/actions/trade')
const {openWindow}=require('../src/commands/window_open')
const { loadCommands } = require('../src/commands')

function fixture(type = 'minecraft:loom', inventoryStart = 4) {
  const bot = new EventEmitter()
  bot.version = '1.21.11'
  bot._client = new EventEmitter()
  bot.currentWindow = Object.assign(new EventEmitter(), { id: 3, type, inventoryStart,
    inventoryEnd: inventoryStart + 36, slots: Array(inventoryStart + 36).fill(null), selectedItem: null })
  bot._client.write = () => {}
  api.attach(bot)
  return bot
}
function property(bot, index, value, windowId = bot.currentWindow.id) {
  bot._client.emit('craft_progress_bar', { windowId, property: index, value })
}
function item(bot, index, name = 'stone', count = 4) {
  return bot.currentWindow.slots[index] = { slot: index, name, type: 1, metadata: 0, count }
}

test('compact window partitions all slots into occupied and empty while retaining special data', () => {
  const bot = fixture('minecraft:generic_9x3', 27)
  item(bot, 0, 'stone', 64)
  item(bot, 26, 'pickaxe', 1).components = [{ type: 'damage', data: 0 }]
  bot.currentWindow.slots[27] = { name: 'book', count: 1, slot: 27, components: [{ type: 'writable_book_content', data: { pages: ['text'] } }] }
  const data = api.describe(bot, { compact: true })
  assert.equal(data.slotCount, 63)
  assert.deepEqual(data.slots.map(i => i.ref), ['storage_9x3:1','storage_9x3:27','inventory:1'])
  assert.deepEqual(data.slots[1], { name: 'pickaxe', count: 1, ref:'storage_9x3:27' })
  const references=[...data.slots,...data.emptySlots].map(i=>i.ref)
  assert.equal(new Set(references).size,63)
  for (const key of ['id', 'properties', 'source', 'cursor']) assert.equal(Object.hasOwn(data, key), false)
  bot.currentWindow.selectedItem = { name: 'diamond', count: 2 }
  assert.equal(api.describe(bot, { compact: true }).cursor.count, 2)
  assert.equal(api.describe(bot).slots[1].item, null)
  bot.emit('end')
})

test('compact furnace returns visible bar percentages rather than raw property ticks', () => {
  const bot = fixture('minecraft:furnace', 3)
  assert.deepEqual(api.describe(bot, { compact: true }).furnace, {})
  for (const [index, value] of [800, 1600, 50, 200].entries()) property(bot, index, value)
  assert.deepEqual(api.describe(bot, { compact: true }).furnace, { fuelPercent: 50, progressPercent: 25 })
  property(bot, 0, 0); property(bot, 1, 0)
  assert.equal(api.describe(bot, { compact: true }).furnace.fuelPercent, 0)
  assert.equal(api.describe(bot).furnace.totalProgressTicks, 200)
  bot.emit('end')
})

test('workstation properties are unknown until observed, scoped to window objects and cleaned on disconnect', () => {
  const bot = fixture('minecraft:brewing_stand', 5)
  assert.equal(api.describe(bot).brewing.fuelUses, null)
  property(bot, 0, 350); property(bot, 1, 19)
  assert.equal(api.describe(bot).brewing.fuelUses, 19)
  assert.equal(api.describe(bot).brewing.remainingTicks, 350)
  bot.currentWindow = { ...bot.currentWindow, slots: [...bot.currentWindow.slots] }
  assert.equal(api.describe(bot).brewing.fuelUses, null)
  property(bot, 1, 4, 99)
  assert.equal(api.describe(bot).brewing.fuelUses, null)
  bot.emit('end')
  assert.equal(bot._client.listenerCount('craft_progress_bar'), 0)
})

test('villagers use native initialization and invalid trade counts never reach the protocol', async () => {
  const villager = { name: 'villager' }
  let initialized = 0, traded = 0
  const bot = Object.assign(new EventEmitter(),{_client:new EventEmitter(),openVillager: async entity => { assert.equal(entity, villager); initialized++;bot.currentWindow={type:'minecraft:merchant'};return bot.currentWindow }})
  assert.equal((await openWindow(bot, villager,{entity:true})).ok, true)
  assert.equal(initialized, 1)
  const w=Object.assign(new EventEmitter(),{id:3,type:'minecraft:merchant',inventoryStart:3,inventoryEnd:39,slots:Array(39).fill(null),trades:[{nbTradeUses:1,maximumNbTradeUses:3,outputItem:{name:'emerald',type:2,metadata:0,count:1}}]})
  bot.currentWindow=w;bot._syncWindow=async()=>{}
  bot.trade = async () => { traded++;w.slots[3]={...w.trades[0].outputItem,count:2} }
  assert.equal((await trade.handler(bot, ['0', '0'])).ok, false)
  assert.equal((await trade.handler(bot, ['0x', '1'])).ok, false)
  assert.equal((await trade.handler(bot, ['0', '3'])).ok, false)
  assert.equal((await trade.handler(bot, ['9', '1'])).ok, false)
  assert.equal((await trade.handler(bot, ['0', '2'])).ok, true)
  assert.equal(traded, 1)
})

test('enchant operations delegate to upstream methods using the current window inventory', async () => {
  const bot = fixture('minecraft:enchantment', 2), w = bot.currentWindow
  const calls = []
  item(bot, 2, 'diamond_sword'); item(bot, 3, 'lapis_lazuli')
  w.putTargetItem = async value => calls.push(['put', value.slot])
  w.putLapis = async value => calls.push(['lapis', value.slot])
  w.enchantments = [{ level: 1 }]
  w.enchant = async value => calls.push(['enchant', value])
  w.takeTargetItem = async () => calls.push(['take'])
  await native.enchant(bot, ['put', 'inventory:1']); await native.enchant(bot, ['lapis', 'inventory:2'])
  await native.enchant(bot, ['choose', '0'])
  item(bot, 0, 'diamond_sword'); await native.enchant(bot, ['take'])
  assert.deepEqual(calls, [['put', 2], ['lapis', 3], ['enchant', 0], ['take']])
  await assert.rejects(native.enchant(bot, ['lapis', 'inventory:1']), /lapis/)
  await assert.rejects(native.enchant(bot, ['choose', '3']), /choice/)
})

test('anvil and furnace delegate complete operations and reject invalid slots/counts', async () => {
  const a = fixture('minecraft:anvil', 3)
  const first = item(a, 3, 'iron_sword'), second = item(a, 4, 'iron_sword')
  let name, pair
  a.currentWindow.rename = async (value, text) => { assert.equal(value, first); name = text }
  a.currentWindow.combine = async (...values) => { pair = values }
  await native.anvil(a, ['rename', 'inventory:1', 'Test', 'Sword'])
  await native.anvil(a, ['combine', 'inventory:1', 'inventory:2'])
  assert.equal(name, 'Test Sword'); assert.deepEqual(pair.slice(0, 2), [first, second])
  await assert.rejects(native.anvil(a, ['combine', 'inventory:1', 'inventory:1']), /different/)
  const f = fixture('minecraft:furnace', 3)
  item(f, 3, 'raw_iron'); item(f, 2, 'iron_ingot', 1)
  let took = false
  f.clickWindow=async(slot,button)=>{const w=f.currentWindow;if(!w.selectedItem){w.selectedItem=w.slots[slot];w.slots[slot]=null}else if(button===1){w.slots[slot]={...w.selectedItem,count:(w.slots[slot]?.count||0)+1};if(--w.selectedItem.count===0)w.selectedItem=null}else{w.slots[slot]=w.selectedItem;w.selectedItem=null}}
  f.currentWindow.takeOutput = async () => { took = true }
  await native.furnace(f, ['input', 'inventory:1', '2']); await native.furnace(f, ['take','furnace:output'])
  assert.equal(f.currentWindow.slots[0].count, 2); assert.equal(took, true)
  await assert.rejects(native.furnace(f, ['input', 'inventory:1', '5']), /count/)
})

test('selection waits for the correct server property and does not accept unrelated window updates', async () => {
  const bot = fixture()
  let packet, done = false
  bot._client.write = (name, body) => { packet = [name, body] }
  const pending = api.station(bot, ['select', '2']).then(() => { done = true })
  property(bot, 0, 2, 99)
  await Promise.resolve(); assert.equal(done, false)
  item(bot, 3, 'white_banner')
  property(bot, 0, 2)
  await pending
  assert.deepEqual(packet, ['enchant_item', { windowId: 3, enchantment: 2 }])
  assert.equal(bot.listenerCount('workstation:update'), 0)
})

test('1.21.11 smithing has four workstation slots and selection needs an output, not just a cached index', async () => {
  const bot = fixture('minecraft:smithing', 3)
  assert.equal(bot.currentWindow.inventoryStart, 4)
  assert.equal(bot.currentWindow.craftingResultSlot, 3)
  bot.currentWindow.type = 'minecraft:stonecutter'
  property(bot, 0, 0)
  let sent = false
  bot._client.write = () => { sent = true; queueMicrotask(() => { item(bot, 1, 'stone_slab'); bot.currentWindow.emit('updateSlot', 1) }) }
  await api.station(bot, ['select', '0'])
  assert.equal(sent, true)
})

test('crafter enabled boolean is inverse of disabled property and updates require an empty slot', async () => {
  const bot = fixture('minecraft:crafter_3x3', 10)
  let packet
  bot._client.write = (name, body) => { packet = [name, body]; queueMicrotask(() => property(bot, body.slot_id, body.state ? 0 : 1)) }
  await api.station(bot, ['crafter', 'crafter:1', 'disabled'])
  assert.deepEqual(packet, ['set_slot_state', { slot_id: 0, window_id: 3, state: false }])
  assert.equal(api.describe(bot).disabledSlots[0], true)
  await api.station(bot, ['crafter', 'crafter:1', 'enabled'])
  assert.equal(packet[1].state, true)
  item(bot, 0)
  await assert.rejects(api.station(bot, ['crafter', 'crafter:1', 'disabled']), /empty/)
})

test('beacon validates tiers/payment and preserves zero-based effect IDs in packets', async () => {
  const bot = fixture('minecraft:beacon', 1)
  bot.registry = { effects: [{ id: 0, name: 'Speed' }, { id: 9, name: 'Regeneration' }] }
  item(bot, 0, 'diamond', 1)
  property(bot, 0, 1)
  let packet
  bot._client.write = (name, body) => { packet = [name, body]; queueMicrotask(() => { property(bot, 1, 1); property(bot, 2, 0) }) }
  await api.station(bot, ['beacon', 'speed'])
  assert.deepEqual(packet, ['set_beacon_effect', { primary_effect: 0, secondary_effect: null }])
  await assert.rejects(api.station(bot, ['beacon', 'speed', 'regeneration']), /level 4/)
})

test('raw operation abort and window close reject and remove all temporary listeners', async () => {
  for (const mode of ['abort', 'close']) {
    const bot = fixture('minecraft:stonecutter')
    const controller = new AbortController()
    const pending = api.station(bot, ['select', '1'], controller.signal)
    const rejected = assert.rejects(pending)
    if (mode === 'abort') controller.abort(); else bot.emit('windowClose', bot.currentWindow)
    await rejected
    assert.equal(bot.listenerCount('workstation:update'), 0)
    assert.equal(bot.listenerCount('windowClose'), 0)
  }
  const bot = fixture('minecraft:stonecutter'); bot.version = '1.20.4'
  await assert.rejects(api.station(bot, ['select', '0']), /1.21.11/)
})

test('window is a discoverable read-only query and station is an action', () => {
  const loaded = loadCommands()
  assert.equal(loaded.queryNames.has('window'), true)
  assert.equal(loaded.queryNames.has('station'), false)
  assert.ok(loaded.list.find(command => command.name === 'station').usage.includes('crafter'))
})
