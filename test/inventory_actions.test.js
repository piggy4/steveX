const test=require('node:test')
const assert=require('node:assert/strict')
const {EventEmitter}=require('node:events')
const {SteveXAgent}=require('../src/agent/agent')
const {loadCommands}=require('../src/commands')
const {sleep}=require('../src/agent/action_executor')
const {sameItem}=require('../src/commands/inventory_actions')
const equip=require('../src/commands/actions/equip').handler,unequip=require('../src/commands/actions/unequip').handler,hotbar=require('../src/commands/actions/hotbar').handler
const use=require('../src/commands/mouse/rightdown').handler,rightup=require('../src/commands/mouse/rightup').handler,drop=require('../src/commands/actions/drop').handler
function fixture(){
  const bot=new EventEmitter(),slots=Array(46).fill(null)
  bot.registry={itemsByName:{stone:{id:1},arrow:{id:2},spectral_arrow:{id:3},iron_helmet:{id:4},bread:{id:5},shield:{id:6},crossbow:{id:7},potion:{id:8},glass_bottle:{id:9},milk_bucket:{id:10},bucket:{id:11}}}
  bot.registry.foodsByName={bread:{id:5}}
  bot.inventory=Object.assign(new EventEmitter(),{slots,items:()=>slots.slice(9,45).filter(Boolean),selectedItem:null})
  bot.quickBarSlot=0;Object.defineProperty(bot,'heldItem',{get:()=>slots[36+bot.quickBarSlot]})
  bot._syncWindow=async()=>{}
  bot.getEquipmentDestSlot=d=>({head:5,torso:6,legs:7,feet:8,'off-hand':45,hand:36+bot.quickBarSlot})[d]
  bot.setQuickBarSlot=n=>{bot.quickBarSlot=n}
  bot.equip=async(item,destination)=>{const dest=destination==='hand'?36:bot.getEquipmentDestSlot(destination);const old=slots[dest];slots[dest]=item;slots[item.slot]=old;if(destination==='hand')bot.quickBarSlot=0}
  bot.unequip=async destination=>{
    if(destination==='hand'){const empty=slots.findIndex((i,n)=>n>=36&&n<45&&!i);if(empty>=0){bot.quickBarSlot=empty-36;return}}
    const source=bot.getEquipmentDestSlot(destination),empty=slots.findIndex((i,n)=>n>=9&&n<45&&!i)
    slots[empty]=slots[source];slots[source]=null
  }
  bot.transfer=async options=>{
    assert.equal(options.sourceEnd,options.sourceStart+1);assert.equal(options.destStart,-999)
    const source=slots[options.sourceStart];source.count-=options.count;if(!source.count)slots[options.sourceStart]=null
  }
  bot.activateItem=()=>{bot.usingHeldItem=true};bot.deactivateItem=()=>{bot.usingHeldItem=false}
  const put=(slot,name,count=1,components=[])=>slots[slot]={slot,name,type:bot.registry.itemsByName[name].id,count,metadata:0,nbt:null,components,removedComponents:[],stackSize:64}
  return {bot,slots,put}
}
test('equip chooses an exact item and confirms destination and conservation',async()=>{
  const f=fixture();f.put(9,'spectral_arrow',1);f.put(10,'arrow',2)
  const r=await equip(f.bot,['MINECRAFT:ARROW'])
  assert.equal(r.ok,true);assert.equal(f.bot.heldItem.name,'arrow');assert.deepEqual(r.changes,[])
  assert.equal((await equip(f.bot,['bread'])).errorCode,'ITEM_NOT_FOUND')
  assert.equal((await equip(f.bot,['planks'])).errorCode,'UNKNOWN_ITEM')
})
test('equip supports armor and does not upgrade a rejected move to success',async()=>{
  const f=fixture();f.put(9,'iron_helmet')
  assert.equal((await equip(f.bot,['iron_helmet','HEAD'])).ok,true);assert.equal(f.slots[5].name,'iron_helmet')
  f.put(10,'stone');f.bot.equip=async()=>{}
  const r=await equip(f.bot,['stone','head']);assert.equal(r.errorCode,'INVENTORY_STATE_NOT_CONFIRMED');assert.match(r.output,/not confirmed/)
})
test('hotbar validates complete integer tokens and confirms selected index',async()=>{
  const f=fixture()
  for(const args of [['2abc'],['1.5'],['-1'],['10'],['0','extra']])assert.equal((await hotbar(f.bot,args)).errorCode,'INVALID_ARGUMENTS')
  assert.equal((await hotbar(f.bot,['9'])).ok,true);assert.equal(f.bot.quickBarSlot,8)
})
test('unequip refuses a full inventory before the upstream auto-drop fallback',async()=>{
  const f=fixture();for(let i=9;i<45;i++)f.put(i,'stone',64)
  f.bot.unequip=()=>{throw Error('must not auto-drop')}
  const r=await unequip(f.bot,[]);assert.equal(r.errorCode,'INVENTORY_FULL');assert.deepEqual(r.changes,[])
})
test('unequip is harmless when empty and preserves items when selecting an empty hand',async()=>{
  const f=fixture();assert.equal((await unequip(f.bot,[])).ok,true)
  f.put(36,'stone',3);assert.equal((await unequip(f.bot,[])).ok,true);assert.equal(f.slots[36].count,3);assert.equal(f.bot.heldItem,null)
})
test('unequip checks capacity for the full offhand stack, not just one item',async()=>{
  const f=fixture();for(let i=9;i<45;i++)f.put(i,'arrow',64)
  f.slots[9].count=63;f.put(45,'arrow',64)
  assert.equal((await unequip(f.bot,['off-hand'])).errorCode,'INVENTORY_FULL')
})
for(const[name,handler]of [['drop',drop]]){
  test(`${name} preserves component identity and scopes native transfers to one source slot`,async()=>{
    const f=fixture();f.put(9,'stone',5,[{type:'custom_data',data:{variant:'A'}}]);f.put(10,'stone',5,[{type:'custom_data',data:{variant:'B'}}])
    assert.equal((await handler(f.bot,['stone','2'])).ok,true);assert.equal(f.slots[9].count,3);assert.equal(f.slots[10].count,5)
    assert.equal((await handler(f.bot,['stone','4'])).errorCode,'INSUFFICIENT_ITEMS');assert.equal(f.slots[9].count,3)
  })
}
test('omitted drop quantity means first stack, explicit count can span identical stacks',async()=>{
  const f=fixture();f.put(9,'stone',64);f.put(10,'stone',6)
  assert.equal((await drop(f.bot,['stone','65'])).ok,true);assert.equal(f.slots[9],null);assert.equal(f.slots[10].count,5)
  assert.equal((await drop(f.bot,['stone'])).ok,true);assert.equal(f.slots[10],null)
})
test('drop slot selects exact variant, defaults to that stack and never spills to others',async()=>{
 const f=fixture();f.put(9,'stone',4,[{type:'custom_data',data:{variant:'A'}}]);f.put(10,'stone',5,[{type:'custom_data',data:{variant:'B'}}])
 assert.equal((await drop(f.bot,['slot','inventory:2','6'])).errorCode,'INSUFFICIENT_ITEMS');assert.equal(f.slots[10].count,5)
 const r=await drop(f.bot,['slot','inventory:2','2']);assert.equal(r.confirmed,true);assert.deepEqual(r.target.source,{ref:'inventory:2'});assert.equal(f.slots[9].count,4);assert.equal(f.slots[10].count,3)
 assert.equal((await drop(f.bot,['slot','inventory:2'])).confirmed,true);assert.equal(f.slots[10],null)
 assert.equal((await drop(f.bot,['slot','inventory:2'])).errorCode,'EMPTY_SLOT')
 for(const args of [['slot','45'],['slot','5'],['slot','9','0'],['slot','9','1x']])assert.equal((await drop(f.bot,args)).errorCode,'INVALID_ARGUMENTS')
})
test('component comparison handles nested data and removed components',()=>{
  const f=fixture(),a=f.put(9,'stone',1,[{type:'custom_data',data:{x:1n}}]),b=f.put(10,'stone',2,[{type:'custom_data',data:{x:1n}}])
  assert.equal(sameItem(a,b),true);b.removedComponents=['custom_name'];assert.equal(sameItem(a,b),false)
})
test('cancelled multi-stack drop retains already observed item loss without retry',async()=>{
  const f=fixture();f.put(9,'stone',64);f.put(10,'stone',6);const controller=new AbortController(),native=f.bot.transfer
  f.bot.transfer=async options=>{await native(options);controller.abort()}
  const r=await drop(f.bot,['stone','65'],{}, {signal:controller.signal})
  assert.equal(r.status,'cancelled');assert.equal(r.changes[0].delta,-64);assert.equal(f.slots[10].count,6)
})
test('open windows and cursor items reject before clicks; final snapshot failure stays unknown',async()=>{
  const f=fixture();f.bot.currentWindow={};assert.equal((await drop(f.bot,['stone'])).errorCode,'WINDOW_OPEN')
  f.bot.currentWindow=null;f.bot.inventory.selectedItem={};assert.equal((await equip(f.bot,['stone'])).errorCode,'CURSOR_NOT_EMPTY')
  f.bot.inventory.selectedItem=null;f.put(9,'stone');let syncs=0;f.bot._syncWindow=async()=>{if(++syncs===2)throw Error('offline')}
  const r=await drop(f.bot,['stone']);assert.equal(r.errorCode,'INVENTORY_CONFIRMATION_UNAVAILABLE');assert.equal(r.changes,null);assert.match(r.output,/unknown/)
})
test('use/rightup acknowledge requests, reject repeated activation and handle empty hand',async()=>{
  const f=fixture();assert.equal((await use(f.bot,[])).errorCode,'NO_HELD_ITEM');f.put(36,'bread',2)
  const r=await use(f.bot,[]);assert.equal(r.confirmation,'request-sent-only');assert.equal(r.requested,true)
  assert.equal((await use(f.bot,[])).errorCode,'ITEM_ALREADY_IN_USE');assert.equal((await rightup(f.bot,[])).requested,true);assert.equal((await rightup(f.bot,[])).requested,false)
})
test('Agent keeps use active across queries and wait, and releases exactly on explicit rightup',async()=>{
  const f=fixture();f.put(36,'bread',2);let releases=0
  f.bot.deactivateItem=()=>{f.bot.usingHeldItem=false;releases++};f.bot.clearControlStates=()=>{};f.bot.stopDigging=()=>{}
  const loaded=loadCommands(),agent=new SteveXAgent({},'inventory-test',loaded.commands,{queryNames:loaded.queryNames});agent.bot=f.bot;agent.connected=true
  await agent.executeCommand('right down');assert.equal(releases,0)
  await agent.executeCommand('hp');assert.equal(releases,0)
  const waiting=agent.executeCommand('wait 2');await sleep(1);f.bot.emit('physicsTick');f.bot.emit('physicsTick');await waiting
  assert.equal(releases,0);assert.equal(f.bot.usingHeldItem,true)
  f.bot.usingHeldItem=false
  assert.equal((await use(f.bot,[])).errorCode,'ITEM_ALREADY_IN_USE')
  await agent.executeCommand('right up');assert.equal(releases,1);await agent.executeCommand('right up');assert.equal(releases,1)
})

test('held-use intent ignores unrelated status but clears on own completion and changed stack',async()=>{
  const f=fixture();f.put(36,'bread',2);f.bot.entity={id:7};f.bot._client=new (require('node:events').EventEmitter)()
  await use(f.bot,[]);f.bot.usingHeldItem=false
  f.bot._client.emit('entity_status',{entityId:8,entityStatus:9})
  f.bot.emit('heldItemChanged',{type:f.bot.heldItem.type,count:2})
  assert.equal((await use(f.bot,[])).errorCode,'ITEM_ALREADY_IN_USE')
  f.bot._client.emit('entity_status',{entityId:7,entityStatus:9})
  assert.equal((await rightup(f.bot,[])).requested,false)
  await use(f.bot,[]);f.bot.usingHeldItem=false
  f.bot.emit('heldItemChanged',{type:f.bot.heldItem.type,count:1})
  assert.equal((await rightup(f.bot,[])).requested,false)
  assert.equal(f.bot._client.listenerCount('entity_status'),0)
})

test('use selects the requested hand, defaults to main hand, and rejects invalid hands',async()=>{
  const f=fixture();let activated;f.bot.activateItem=off=>{activated=off;f.bot.usingHeldItem=true}
  f.put(45,'shield');assert.equal((await use(f.bot,[])).errorCode,'NO_HELD_ITEM')
  const r=await use(f.bot,['OFF-HAND']);assert.equal(r.hand,'off-hand');assert.equal(r.item,'shield');assert.equal(activated,true)
  assert.equal((await use(f.bot,['hand'])).errorCode,'NO_HELD_ITEM')
  assert.equal((await rightup(f.bot,[])).requested,true)
  f.put(36,'bread');await use(f.bot,[]);assert.equal(activated,false);await rightup(f.bot,[])
  assert.equal((await use(f.bot,['left'])).errorCode,'INVALID_ARGUMENTS')
  assert.equal((await use(f.bot,['hand','extra'])).errorCode,'INVALID_ARGUMENTS')
})

test('offhand use survives main-hand notifications and shield damage, but clears when offhand empties',async()=>{
  const f=fixture();f.put(45,'shield');f.put(36,'stone');await use(f.bot,['off-hand']);f.bot.usingHeldItem=false
  f.bot.emit('heldItemChanged',null);f.bot.inventory.emit('updateSlot',36)
  f.slots[45].components=[{type:'damage',data:10}];f.bot.inventory.emit('updateSlot',45)
  assert.equal((await use(f.bot,['off-hand'])).errorCode,'ITEM_ALREADY_IN_USE')
  f.slots[45]=null;f.bot.inventory.emit('updateSlot',45)
  assert.equal((await rightup(f.bot,[])).requested,false)
  assert.equal(f.bot.inventory.listenerCount('updateSlot'),0)
})

test('Agent movement and turning preserve held use until an inventory action or rightup',async()=>{
  const f=fixture();f.put(45,'shield');let releases=0
  f.bot.deactivateItem=()=>{f.bot.usingHeldItem=false;releases++};f.bot.clearControlStates=()=>{};f.bot.stopDigging=()=>{}
  const loaded=loadCommands(),commands={...loaded.commands}
  const motions=['move forward 1','jump','look angle 122.7042204869 0','look at 1 2 3','move stop','goto 1 2 3','follow Other','wait 1']
  for(const command of motions)commands[command.split(' ')[0]]=async()=>({ok:true})
  const agent=new SteveXAgent({},'shield-motion',commands,{queryNames:loaded.queryNames});agent.bot=f.bot;agent.connected=true
  await agent.executeCommand('right down off-hand')
  for(const command of motions){assert.equal((await agent.executeCommand(command)).ok,true);assert.equal(releases,0,command)}
  assert.equal((await agent.executeCommand('right down off-hand')).errorCode,'ITEM_ALREADY_IN_USE')
  await agent.executeCommand('hotbar 1');assert.equal(releases,1)
  assert.equal((await agent.executeCommand('right up')).released,false)
  await agent.executeCommand('right down off-hand');await agent.executeCommand('right up');assert.equal(releases,2)
})

test('stop and aborted motion remain full safety stops while ordinary move preserves use',async()=>{
  const f=fixture();f.put(45,'shield');let releases=0;const controls={}
  f.bot.deactivateItem=()=>{f.bot.usingHeldItem=false;releases++};f.bot.clearControlStates=()=>{for(const k of Object.keys(controls))controls[k]=false};f.bot.stopDigging=()=>{}
  f.bot.setControlState=(k,v)=>{controls[k]=v}
  const loaded=loadCommands(),agent=new SteveXAgent({},'shield-stop',loaded.commands,{queryNames:loaded.queryNames});agent.bot=f.bot;agent.connected=true
  await agent.executeCommand('right down off-hand');await agent.executeCommand('move forward 1');assert.equal(releases,0)
  await agent.executeCommand('stop');assert.ok(releases>0);assert.equal((await agent.executeCommand('right up')).released,false)
  await agent.executeCommand('right down off-hand');const moving=agent.executeCommand('move forward 30000',{timeoutMs:10})
  const result=await moving;assert.equal(result.ok,false);assert.equal(controls.forward,false)
  assert.equal((await agent.executeCommand('right up')).released,false)
})

test('observation capture does not silently rightup held use',async()=>{
  const f=fixture();f.put(45,'shield');let releases=0
  f.bot.deactivateItem=()=>{f.bot.usingHeldItem=false;releases++};f.bot.clearControlStates=()=>{};f.bot.stopDigging=()=>{}
  const loaded=loadCommands(),agent=new SteveXAgent({},'shield-capture',loaded.commands,{queryNames:loaded.queryNames,
    renderService:{capture:async()=>({timestamp:Date.now(),agentId:'shield-capture'})}})
  agent.bot=f.bot;agent.connected=true
  await agent.executeCommand('right down off-hand');assert.equal((await agent.captureObservation()).ok,true);assert.equal(releases,0)
  assert.equal((await agent.executeCommand('right up')).released,true);assert.equal(releases,1)
})

for(const hand of ['hand','off-hand'])test(`${hand} crossbow loading and firing complete use without type/count changes`,async()=>{
  const f=fixture(),slot=hand==='hand'?36:45;f.put(slot,'crossbow');let releases=0
  f.bot.deactivateItem=()=>{releases++;f.bot.usingHeldItem=false}
  const update=components=>{f.slots[slot].components=components;if(hand==='hand')f.bot.emit('heldItemChanged',f.slots[slot]);else f.bot.inventory.emit('updateSlot',slot)}
  await use(f.bot,[hand]);assert.equal(f.bot.usingHeldItem,true)
  update([{type:'charged_projectiles',data:{projectiles:[{itemId:2,itemCount:1}]}}])
  assert.equal(releases,1)
  assert.equal(f.bot.usingHeldItem,false);assert.equal((await rightup(f.bot,[])).requested,false)
  const fired=await use(f.bot,[hand]);assert.equal(fired.ok,true);assert.equal(fired.holdTracked,false)
  update([{type:'charged_projectiles',data:{projectiles:[]}}])
  // Firing a loaded crossbow is instantaneous, not another held-use lifecycle.
  assert.equal(releases,1)
  assert.equal(f.bot.usingHeldItem,false);assert.equal((await rightup(f.bot,[])).requested,false)
  assert.equal((await use(f.bot,[hand])).ok,true);await rightup(f.bot,[])
  assert.equal(f.bot.inventory.listenerCount('updateSlot'),0)
})

for(const hand of ['hand','off-hand'])for(const[source,container]of [['potion','glass_bottle'],['milk_bucket','bucket']]){
  test(`${hand} ${source} completion clears use on returned container or remaining stack`,async()=>{
    for(const stacked of [false,true]){
      const f=fixture(),slot=hand==='hand'?36:45;let activations=0,releases=0
      for(let i=9;i<45;i++)f.put(i,'stone',64)
      f.put(slot,source,stacked?2:1)
      f.bot.activateItem=()=>{activations++;f.bot.usingHeldItem=true}
      f.bot.deactivateItem=()=>{releases++;f.bot.usingHeldItem=false}
      await use(f.bot,[hand])
      // Feed the observed server result; this tests lifecycle, not Minecraft's
      // bottle/bucket placement rules (those are verified by the live suite).
      f.put(slot,stacked?source:container,1)
      if(hand==='hand')f.bot.emit('heldItemChanged',f.slots[slot]);else f.bot.inventory.emit('updateSlot',slot)
      assert.equal(f.bot.usingHeldItem,false);assert.equal((await rightup(f.bot,[])).requested,false)
      assert.equal(activations,1);assert.equal(releases,0);assert.equal(f.bot.inventory.listenerCount('updateSlot'),0)
      if(stacked){assert.equal((await use(f.bot,[hand])).ok,true);assert.equal(activations,2);await rightup(f.bot,[])}
    }
  })
}
