const test=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events')
const {fixture}=require('./helpers/window_fixture.cjs'),{commands}=require('../src/commands').loadCommands()
function setup(type='minecraft:furnace',start=3){const f=fixture(start,type);Object.setPrototypeOf(f.bot,EventEmitter.prototype);EventEmitter.call(f.bot);Object.setPrototypeOf(f.w,EventEmitter.prototype);EventEmitter.call(f.w);f.bot._client=new EventEmitter();f.bot.version='1.21.11';f.bot._client.write=()=>{};return f}
test('furnace moves exact component source and returns confirmed scoped changes without full window',async()=>{
 const f=setup();f.put(3,'stone',5,[{type:'custom_data',data:{v:'A'}}]);f.put(4,'stone',5,[{type:'custom_data',data:{v:'B'}}])
 const r=await commands.furnace(f.bot,['input','inventory:2','2']);assert.equal(r.ok,true,JSON.stringify(r));assert.equal(r.status,'completed');assert.equal(r.confirmed,true);assert.equal(f.w.slots[3].count,5);assert.equal(f.w.slots[4].count,3);assert.equal(f.w.slots[0].components[0].data.v,'B');assert.equal(r.window,undefined);assert.ok(r.changes.some(c=>c.ref==='inventory:2'))
})
test('unrelated furnace progress does not confirm a rejected requested move',async()=>{
 const f=setup();f.put(3,'stone',5);let calls=0;require('../src/commands/workstations').attach(f.bot);f.bot._client.emit('craft_progress_bar',{windowId:1,property:3,value:200});f.bot.clickWindow=async()=>{calls++;f.bot._client.emit('craft_progress_bar',{windowId:1,property:2,value:calls})}
 const r=await commands.furnace(f.bot,['input','inventory:1']);assert.equal(r.ok,false);assert.equal(r.confirmed,false);assert.ok(r.stateChanges.length)
})
test('workstation thrown operations retain partial observed effects and errors',async()=>{
 const f=setup();f.put(2,'stone',2);f.w.takeOutput=async()=>{f.w.slots[3]=f.w.slots[2];f.w.slots[2]=null;throw Error('connection failure')}
 const r=await commands.furnace(f.bot,['take','furnace:output']);assert.equal(r.ok,false);assert.equal(r.status,'failed');assert.equal(r.confirmed,false);assert.ok(r.changes.length);assert.equal(r.errorCode,'ACTION_FAILED')
})
test('removed station move never sends any slot clicks',async()=>{
 const f=setup('minecraft:loom',4);const r=await commands.station(f.bot,['move','inventory:1','storage_9x3:1']);assert.equal(r.errorCode,'INVALID_ARGUMENTS');assert.equal(f.calls.length,0)
})
test('crafter already-satisfied state is server-snapshot confirmed without extra packets',async()=>{
 const f=setup('minecraft:crafter_3x3',9);require('../src/commands/workstations').attach(f.bot);f.bot._client.emit('craft_progress_bar',{windowId:1,property:0,value:1})
 const r=await commands.station(f.bot,['crafter','crafter:1','disabled']);assert.equal(r.ok,true,JSON.stringify(r));assert.deepEqual(r.changes,[]);assert.deepEqual(r.stateChanges,[])
})
test('post-operation snapshot failure leaves effects unknown rather than empty',async()=>{
 const f=setup();f.put(3,'stone',5);let n=0;f.bot._syncWindow=async()=>{if(++n===2)throw Object.assign(Error('snapshot lost'),{code:'INVENTORY_SYNC_TIMEOUT'})}
 const r=await commands.furnace(f.bot,['input','inventory:1']);assert.equal(r.ok,false);assert.equal(r.changes,null);assert.equal(r.errorCode,'INVENTORY_SYNC_TIMEOUT')
})
