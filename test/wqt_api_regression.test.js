const test=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events')
const {commands}=require('../src/commands').loadCommands()
const {fixture}=require('./helpers/window_fixture.cjs')

test('result destination rejects before pickup in player, crafting and furnace windows',async()=>{
  for(const [start,type,result]of [[9,'minecraft:inventory',0],[10,'minecraft:crafting',0],[3,'minecraft:furnace',2]]){
    const f=fixture(start,type);f.w.craftingResultSlot=result;f.put(start,'stone',2)
    if(type==='minecraft:inventory'){f.bot.inventory=f.w;f.bot.currentWindow=null}
    const dest=type==='minecraft:inventory'?'inventory:0':`window:${result}`
    const r=await commands.moveslot(f.bot,['inventory:9',dest])
    assert.equal(r.errorCode,'RESULT_SLOT_READ_ONLY');assert.equal(f.calls.length,0);assert.equal(f.w.selectedItem,null)
    assert.equal(f.w.slots[start].count,2)
    f.w.selectedItem={name:'stone',count:1}
    assert.equal((await commands.slotclick(f.bot,[dest])).errorCode,'RESULT_SLOT_READ_ONLY');assert.equal(f.calls.length,0)
  }
})

test('result extraction remains slotclick; moveslot cannot repeat a recipe',async()=>{
  const f=fixture(10,'minecraft:crafting');f.w.craftingResultSlot=0;f.put(0,'stone',1)
  assert.equal((await commands.moveslot(f.bot,['window:0','inventory:9'])).errorCode,'RESULT_SLOT_REQUIRES_CLICK')
  assert.equal(f.calls.length,0)
  assert.equal((await commands.slotclick(f.bot,['window:0'])).ok,true)
})

test('slotclick still takes a result onto a compatible cursor stack',async()=>{
  const f=fixture(10,'minecraft:crafting');f.w.craftingResultSlot=0;f.put(0,'stone',1)
  f.w.selectedItem={...f.w.slots[0],count:2}
  // A real result slot allows taking, never placing. Model that server response.
  f.bot.clickWindow=async()=>{f.calls.push('take');f.w.selectedItem.count++;f.w.slots[0]=null}
  assert.equal((await commands.slotclick(f.bot,['window:0'])).ok,true)
  assert.equal(f.w.selectedItem.count,3);assert.deepEqual(f.calls,['take'])
})

test('inventory exposes cursor separately and stable layout across open/closed windows',async()=>{
  const f=fixture();f.bot.quickBarSlot=0
  f.bot.inventory.selectedItem={name:'stone',count:2};f.w.selectedItem={name:'apple',count:3}
  f.put(54,'torch',4)
  const a=await commands.inventory(f.bot,['slots']);assert.deepEqual(a.cursor,{name:'apple',count:3})
  assert.deepEqual(a.slotLayout.hotbar,[36,44]);assert.equal(a.slotLayout.result,0)
  assert.equal(a.slots.find(i=>i.name==='torch').slot,36)
  f.bot.currentWindow=null
  assert.deepEqual((await commands.inventory(f.bot,[])).cursor,{name:'stone',count:2})
  f.bot.inventory.selectedItem=null
  assert.equal(Object.hasOwn(await commands.inventory(f.bot,[]),'cursor'),false)
})

test('look block reports air explicitly without turning or clicking',async()=>{
  let turned=false
  const r=await commands.look({blockAt:()=>({name:'air'}),lookAt:()=>{turned=true}},['block','1','2','3'])
  assert.equal(r.errorCode,'TARGET_IS_AIR');assert.match(r.error,/support block/);assert.equal(turned,false)
})

test('dig compatibility normalizes component enchantments for tools and helmets, preserves upstream ordinary path',()=>{
  const registry=require('prismarine-registry')('1.21.11'),Item=require('prismarine-item')(registry),Block=require('prismarine-block')(registry)
  const bot=new EventEmitter();bot.registry=registry;bot.inventory={slots:Array(46).fill(null)};bot.getEquipmentDestSlot=()=>5
  bot.game={gameMode:'survival'};bot.entity={onGround:true,effects:{}};bot._getBlockAtEyeLevel=()=>({name:'air'})
  const original=()=>123;bot.digTime=original
  require('../src/agent/digging_compat').diggingCompatibility(bot)
  const block=Block.fromStateId(registry.blocksByName.stone.defaultState,0)
  assert.equal(bot.digTime(block),123)
  const enchanted=(name,enchant)=>{const item=new Item(registry.itemsByName[name].id,1)
    const component={type:'enchantments',data:{enchantments:[{id:registry.enchantmentsByName[enchant].id,level:3}]}}
    item.components=[component];item.componentMap.set('enchantments',component)
    return item}
  bot.heldItem=enchanted('diamond_pickaxe','efficiency')
  const expected=block.digTime(bot.heldItem.type,false,false,false,[{name:'efficiency',lvl:3}],{})
  assert.equal(bot.digTime(block),expected)
  bot.inventory.slots[5]=enchanted('diamond_helmet','aqua_affinity');bot._getBlockAtEyeLevel=()=>({name:'water'})
  assert.equal(bot.digTime(block),block.digTime(bot.heldItem.type,false,true,false,[{name:'efficiency',lvl:3},{name:'aqua_affinity',lvl:3}],{}))
  bot.entity.onGround=false;bot.game.gameMode='creative'
  assert.equal(bot.digTime(block),0)
  bot.emit('end');assert.equal(bot.digTime,original)
})
