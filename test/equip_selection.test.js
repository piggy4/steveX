const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { handler } = require('../src/commands/actions/equip')
const { validateArguments } = require('../src/commands/command_contract')

function fixture() {
  const slots = Array(46).fill(null)
  const bot = new EventEmitter()
  bot.registry = { itemsByName: { diamond_pickaxe: { id: 1 } } }
  bot.inventory = { slots, selectedItem: null }
  bot._syncWindow = async () => {}
  bot.getEquipmentDestSlot = destination => destination === 'off-hand' ? 45 : 36
  Object.defineProperty(bot, 'heldItem', { get: () => slots[36] })
  let calls = 0
  bot.equip = async (item, destination) => {
    calls++
    const index = bot.getEquipmentDestSlot(destination)
    const from = slots.indexOf(item)
    if (from !== index) [slots[index], slots[from]] = [slots[from], slots[index]]
  }
  const put = (slot, extra = {}) => slots[slot] = { name:'diamond_pickaxe', type:1, metadata:0, slot, count:1, nbt:null, components:[], removedComponents:[], ...extra }
  return { bot, slots, put, calls: () => calls }
}

for (const extra of [
  { components:[{type:'enchantments',data:{levels:{efficiency:5}}}] },
  { components:[{type:'damage',data:10}] },
  { nbt:{type:'compound',value:{Damage:{type:'int',value:3}}} },
  { components:[{type:'custom_data',data:{owner:'test'}}] },
  { removedComponents:['custom_name'] },
  { durabilityUsed:7 },
  { metadata:1 }
]) test(`equip returns candidates without choosing distinct variant ${JSON.stringify(extra)}`, async () => {
  const f = fixture()
  f.put(9); f.put(10, extra)
  const result = await handler(f.bot, ['item','diamond_pickaxe','off-hand'])
  assert.equal(result.errorCode,'EQUIP_SELECTION_REQUIRED')
  assert.equal(result.confirmed,false)
  assert.equal(f.calls(),0)
  assert.deepEqual(result.changes,[])
  assert.deepEqual(result.candidates.map(item=>item.ref),['inventory:1','inventory:2'])
  assert.equal(result.destination,'off-hand')
  assert.equal(result.slotSpace,'player-inventory')
  assert.match(result.output,/Nothing equipped.*equip slot/)
  assert.ok(result.output.includes(JSON.stringify(result.candidates)))
  const selected = f.slots[10]
  assert.equal((await handler(f.bot,['slot','inventory:2','OFF-HAND'])).ok,true)
  assert.equal(f.slots[45],selected)
})

test('equip identical variants ignores stack size and chooses the first slot for bare/explicit names', async () => {
  for (const args of [['diamond_pickaxe'],['ITEM','MINECRAFT:DIAMOND_PICKAXE']]) {
    const f=fixture(), first=f.put(9,{count:2})
    f.put(10,{count:1})
    const result=await handler(f.bot,args)
    assert.equal(result.ok,true)
    assert.equal(result.confirmed,true)
    assert.equal(result.candidates,undefined)
    assert.equal(f.bot.heldItem,first)
    assert.equal(f.calls(),1)
  }
})

test('equip reads an explicit slot at execution, rejects empty/unknown names, and never falls back', async () => {
  const f=fixture()
  assert.equal((await handler(f.bot,['slot','inventory:1'])).errorCode,'EMPTY_SLOT')
  assert.equal((await handler(f.bot,['item','missing'])).errorCode,'UNKNOWN_ITEM')
  assert.equal((await handler(f.bot,['item','diamond_pickaxe'])).errorCode,'ITEM_NOT_FOUND')
  f.put(10)
  assert.equal((await handler(f.bot,['slot','inventory:1'])).errorCode,'EMPTY_SLOT')
  assert.equal(f.calls(),0)
  assert.equal((await handler(f.bot,['slot','inventory:2'])).ok,true)
})

test('equip validates selection and destination before reading game state', async () => {
  const bot=new Proxy({}, {get(){throw Error('must not read bot')}})
  for (const args of [[],['slot'],['slot','8'],['slot','45'],['slot','-1'],['slot','9.5'],['slot','9abc'],['slot','9007199254740992'],['item'],['item','diamond_pickaxe','left'],['slot','inventory:1','hand','extra'],['diamond_pickaxe','hand','extra'],[null]]) {
    assert.equal(validateArguments('equip',args).errorCode,'INVALID_ARGUMENTS')
    assert.equal((await handler(bot,args)).errorCode,'INVALID_ARGUMENTS')
  }
})

test('equip keeps window and cursor guards and does not claim an ignored equipment change succeeded', async () => {
  const f=fixture(); f.put(9)
  f.bot.currentWindow={}
  assert.equal((await handler(f.bot,['slot','inventory:1'])).errorCode,'WINDOW_OPEN')
  f.bot.currentWindow=null; f.bot.inventory.selectedItem={name:'stone'}
  assert.equal((await handler(f.bot,['slot','inventory:1'])).errorCode,'CURSOR_NOT_EMPTY')
  f.bot.inventory.selectedItem=null; f.bot.equip=async()=>{}
  assert.equal((await handler(f.bot,['slot','inventory:1'])).errorCode,'INVENTORY_STATE_NOT_CONFIRMED')
})
