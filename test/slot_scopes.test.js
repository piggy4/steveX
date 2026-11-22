const test=require('node:test'),assert=require('node:assert/strict')
const {commands}=require('../src/commands').loadCommands(),{fixture}=require('./helpers/window_fixture.cjs')
const {nativeSlot}=require('../src/commands/slot_scope'),{Brain}=require('../src/agent/brain')
for(const [start,type]of [[5,'minecraft:hopper'],[27,'minecraft:generic_9x3'],[54,'minecraft:generic_9x6']])test(`fixed player 9/36/44 map correctly across ${type}`,async()=>{
 const f=fixture(start,type)
 for(const slot of [9,36,44])assert.equal(nativeSlot(f.bot,'inventory',slot),start+slot-9)
 f.put(start,'stone',5);f.put(0,'dirt',1)
 const r=await commands.moveslot(f.bot,['inventory:1',(type==='minecraft:hopper'?'hopper':type==='minecraft:generic_9x6'?'storage_9x6':'storage_9x3')+':2','2'])
 assert.equal(r.confirmed,true);assert.deepEqual(r.source,{ref:'inventory:1'});assert.deepEqual(r.destination,{ref:r.destination.ref})
 assert.ok(r.changes.some(c=>c.ref==='inventory:1'));assert.ok(r.changes.some(c=>c.ref===r.destination.ref))
 assert.equal(f.w.slots[start].count,3);assert.equal(f.w.slots[1].count,2)
})
test('same number has explicit different meanings; neither scope is guessed',async()=>{
 const f=fixture();f.put(9,'dirt',1);f.put(27,'stone',3)
 assert.equal((await commands.slotinfo(f.bot,['inventory:1'])).item.name,'stone')
 assert.equal((await commands.slotinfo(f.bot,['storage_9x3:10'])).item.name,'dirt')
 assert.equal((await commands.slotclick(f.bot,['9'])).errorCode,'INVALID_ARGUMENTS');assert.equal(f.calls.length,0)
})
test('window scope refuses player-region aliases and closed windows; inventory special slots cannot be clicked through containers',async()=>{
 const f=fixture()
 assert.equal((await commands.slotclick(f.bot,['storage_9x3:28'])).errorCode,'INVALID_SLOT_REFERENCE')
 for(const slot of ['crafting:result','equipment:head','equipment:offhand'])assert.equal((await commands.slotclick(f.bot,[slot])).errorCode,'SLOT_UNAVAILABLE_IN_WINDOW')
 f.bot.currentWindow=null
 assert.equal((await commands.slotclick(f.bot,['storage_9x3:10'])).errorCode,'WINDOW_REQUIRED')
 assert.equal((await commands.slotinfo(f.bot,['storage_9x3:10'])).errorCode,'WINDOW_REQUIRED');assert.equal(f.calls.length,0)
})
test('queries remain read-only and expose slotinfo even without open window',async()=>{
 const f=fixture(),brain=new Brain({bot:f.bot},{},{})
 assert.equal(brain.commandNames.has('slotinfo'),true);assert.match(brain.buildSystem(),/unchanged when windows open/)
 f.put(27,'stone',1)
 await commands.inventory(f.bot,[]);await commands.inventory(f.bot,['slots']);await commands.slotinfo(f.bot,['inventory:1'])
 assert.equal(f.calls.length,0)
})

test('legacy scopes and bare creative slots are rejected before actions',async()=>{
 const f=fixture()
 for(const ref of ['window:0','36','inventory:36','hotbar:0']){
  assert.equal((await commands.slotclick(f.bot,[ref])).ok,false)
 }
 assert.equal((await commands.clearslot(f.bot,['36'])).errorCode,'INVALID_SLOT_REFERENCE')
 assert.equal((await commands.setinventory(f.bot,['36','stone'])).errorCode,'INVALID_SLOT_REFERENCE')
 assert.equal(f.calls.length,0)
})
