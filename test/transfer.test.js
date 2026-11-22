const test=require('node:test'),assert=require('node:assert/strict')
const {fixture}=require('./helpers/window_fixture.cjs')
const {commands}=require('../src/commands').loadCommands()
for(const [start,type]of [[5,'minecraft:hopper'],[27,'minecraft:generic_9x3'],[54,'minecraft:generic_9x6']])test(`explicit transfer deposits/withdraws last hotbar slot for ${type}`,async()=>{
  const f=fixture(start,type),slot=f.w.inventoryEnd-1
  f.put(slot,'stone',3,[{type:'custom_name',data:'chosen'}])
  const r=await commands.transfer(f.bot,['deposit','hotbar:9','2'])
  assert.equal(r.confirmed,true);assert.equal(f.w.slots[slot].count,1);assert.equal(f.w.slots[0].count,2)
  assert.deepEqual(f.w.slots[0].components,[{type:'custom_name',data:'chosen'}]);assert.equal(r.changes.length,2)
  const back=await commands.transfer(f.bot,['withdraw',type==='minecraft:hopper'?'hopper:1':type==='minecraft:generic_9x6'?'storage_9x6:1':'storage_9x3:1'])
  assert.equal(back.confirmed,true);assert.equal(f.w.slots[0],null)
})
test('transfer never searches names or silently selects another component variant',async()=>{
  const f=fixture();f.put(27,'stone',3);f.put(28,'stone',4,[{type:'custom_name',data:'other'}])
  assert.equal((await commands.transfer(f.bot,['stone'])).errorCode,'INVALID_ARGUMENTS')
  assert.equal((await commands.transfer(f.bot,['deposit','inventory:2','2'])).confirmed,true)
  assert.equal(f.w.slots[27].count,3);assert.equal(f.w.slots[28].count,2)
  assert.deepEqual(f.w.slots[0].components,[{type:'custom_name',data:'other'}])
})
test('transfer checks source region, counts, cursor and storage type without clicks',async()=>{
  const f=fixture();f.put(27,'stone',3)
  for(const args of [['deposit','0'],['withdraw','27'],['deposit','inventory:1','4'],['deposit','inventory:1','1.5']])assert.equal((await commands.transfer(f.bot,args)).ok,false)
  f.w.selectedItem={name:'stone',count:1};assert.equal((await commands.transfer(f.bot,['deposit','inventory:1'])).errorCode,'CURSOR_NOT_EMPTY')
  f.w.selectedItem=null;f.w.type='minecraft:furnace';assert.equal((await commands.transfer(f.bot,['deposit','inventory:1'])).errorCode,'WINDOW_TYPE_MISMATCH')
  f.w.type='minecraft:generic_9x3';f.w.inventoryEnd=1000;assert.equal((await commands.transfer(f.bot,['deposit','inventory:1'])).errorCode,'INVALID_WINDOW_RANGE')
  assert.equal(f.calls.length,0)
})
test('transfer full destination rejects before touching source and cannot drop overflow',async()=>{
  const f=fixture();for(let i=0;i<27;i++)f.put(i,'stone',64);f.put(27,'stone',5)
  const r=await commands.transfer(f.bot,['deposit','inventory:1']);assert.equal(r.errorCode,'DESTINATION_FULL');assert.equal(f.calls.length,0);assert.equal(f.w.slots[27].count,5)
  assert.equal(r.observedEffect,'unchanged');assert.deepEqual(r.changes,[])
})
test('moveslot partial move, exact merge, full swap and rejected partial swap',async()=>{
  const f=fixture();f.put(27,'stone',5)
  let r=await commands.moveslot(f.bot,['inventory:1','inventory:2','2']);assert.equal(r.confirmed,true);assert.equal(f.w.slots[27].count,3);assert.equal(f.w.slots[28].count,2)
  r=await commands.moveslot(f.bot,['inventory:1','inventory:2']);assert.equal(r.confirmed,true);assert.equal(f.w.slots[28].count,5)
  f.put(27,'dirt',1);r=await commands.moveslot(f.bot,['inventory:1','inventory:2','1']);assert.equal(r.errorCode,'DESTINATION_MISMATCH')
  r=await commands.moveslot(f.bot,['inventory:1','inventory:2']);assert.equal(r.confirmed,true);assert.equal(r.mode,'swap');assert.equal(f.w.slots[28].name,'dirt')
})
test('slot clicks expose readable buttons and actual cursor changes; shift-left maps to native mode 1',async()=>{
  const f=fixture();f.put(0,'stone',5)
  const r=await commands.slotclick(f.bot,['storage_9x3:1','right']);assert.equal(r.confirmed,true);assert.equal(f.w.selectedItem.count,3);assert.equal(f.w.slots[0].count,2)
  assert.ok(r.changes.some(c=>c.kind==='cursor'))
  await commands.slotclick(f.bot,['storage_9x3:2','left']);const shifted=await commands.slotclick(f.bot,['storage_9x3:1','shift-left'])
  assert.equal(shifted.confirmed,true);assert.deepEqual(f.calls.at(-1),[0,0,1])
})
test('server rejection is not successful move; partial effects survive thrown native calls',async()=>{
  const f=fixture();f.put(27,'stone',5);f.bot.clickWindow=async()=>{}
  let r=await commands.moveslot(f.bot,['inventory:1','inventory:2','2']);assert.equal(r.confirmed,false);assert.equal(r.errorCode,'SLOT_CHANGE_NOT_CONFIRMED')
  const g=fixture();g.put(27,'stone',5);const native=g.bot.clickWindow;let calls=0
  g.bot.clickWindow=async(...args)=>{await native(...args);if(++calls===2)throw Error('server stopped responding')}
  r=await commands.moveslot(g.bot,['inventory:1','inventory:2','2']);assert.equal(r.ok,false);assert.equal(r.observedEffect,'changed');assert.ok(r.changes.length);assert.ok(r.changes.some(c=>c.kind==='cursor'));assert.match(r.output,/inspect/)
})
test('putaway checks room before moving and does not auto-craft from result slots',async()=>{
  const f=fixture();f.put(0,'stone',3);assert.equal((await commands.putaway(f.bot,['storage_9x3:1'])).confirmed,true)
  f.put(0,'stone',1);f.w.craftingResultSlot=0;assert.equal((await commands.putaway(f.bot,['storage_9x3:1'])).errorCode,'RESULT_SLOT_REQUIRES_CLICK')
})
test('cancellation prevents further clicks and retains observed cursor changes',async()=>{
  const f=fixture(),controller=new AbortController();f.put(27,'stone',5);const click=f.bot.clickWindow
  f.bot.clickWindow=async(...args)=>{await click(...args);controller.abort()}
  const r=await commands.moveslot(f.bot,['inventory:1','inventory:2','2'],null,{signal:controller.signal})
  assert.equal(r.errorCode,'CANCELLED');assert.equal(f.calls.length,1);assert.ok(r.changes.some(c=>c.kind==='cursor'))
})
test('formal Agent cancellation preserves partial slot evidence, not only direct handler feedback',async()=>{
  const {SteveXAgent}=require('../src/agent/agent'),{loadCommands}=require('../src/commands'),loaded=loadCommands()
  const f=fixture(),controller=new AbortController();f.put(27,'stone',5);const native=f.bot.clickWindow
  f.bot.clickWindow=async(...args)=>{await native(...args);controller.abort()}
  const agent=new SteveXAgent({},'slot-cancel',loaded.commands,{queryNames:loaded.queryNames});agent.bot=f.bot;agent.connected=true
  const r=await agent.executeCommand('moveslot inventory:1 inventory:2 2',{signal:controller.signal})
  assert.equal(r.ok,false);assert.equal(r.status,'cancelled');assert.equal(r.confirmed,false);assert.ok(r.changes.some(c=>c.kind==='cursor'));assert.equal(f.calls.length,1)
})
test('window changes during an operation retain uncertainty and prevent more clicks',async()=>{
  const f=fixture();f.put(27,'stone',5);let calls=0
  f.bot.clickWindow=async()=>{calls++;f.bot.currentWindow={...f.w,slots:[...f.w.slots]}}
  const r=await commands.moveslot(f.bot,['inventory:1','inventory:2','2'])
  assert.equal(r.ok,false);assert.equal(r.errorCode,'WINDOW_CHANGED');assert.equal(r.changes,null);assert.equal(r.observedEffect,'unknown');assert.equal(calls,1)
})
