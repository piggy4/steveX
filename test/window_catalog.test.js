const test=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events')
const {Brain}=require('../src/agent/brain'),{loadCommands}=require('../src/commands')
const {openWindow}=require('../src/commands/window_open')
test('Brain refreshes window-specific operations and preserves backpack controls and manual catalog',()=>{
 const agent={bot:{}},brain=new Brain(agent,{},{}),all=loadCommands()
 for(const name of ['slotclick','moveslot','putaway'])assert.equal(brain.commandNames.has(name),true)
 for(const name of ['furnace','anvil','enchant','station','trade','close','transfer','window'])assert.equal(brain.commandNames.has(name),false)
 assert.match(brain.buildSystem(),/right click to open/)
 for(const [type,expected]of [['minecraft:furnace','furnace'],['minecraft:anvil','anvil'],['minecraft:enchantment','enchant'],['minecraft:merchant','trade'],['minecraft:loom','station'],['minecraft:generic_9x3',null]]){
  agent.bot.currentWindow={type};brain.buildSystem()
  for(const name of ['furnace','anvil','enchant','station','trade'])assert.equal(brain.commandNames.has(name),name===expected,`${type}: ${name}`)
  for(const name of ['close','window'])assert.equal(brain.commandNames.has(name),true)
  assert.equal(brain.commandNames.has('transfer'),type==='minecraft:generic_9x3')
 }
 agent.bot.currentWindow={type:'minecraft:loom'};brain.buildSystem();assert.match(brain.table,/station select/);assert.doesNotMatch(brain.table,/station beacon/)
 agent.bot.currentWindow={type:'minecraft:beacon'};brain.buildSystem();assert.match(brain.table,/station beacon/);assert.doesNotMatch(brain.table,/station select/)
 agent.bot.currentWindow=null;brain.buildSystem();assert.equal(brain.pickCommand('trade 0'),null)
 for(const name of ['furnace','anvil','enchant','station','trade'])assert.equal(typeof all.commands[name],'function')
 for(const name of ['open','openentity','dispenser'])assert.equal(all.commands[name],undefined)
})
test('window-aware exposure is per-agent and does not bypass the allowlist',()=>{
 const one=new Brain({bot:{currentWindow:{type:'minecraft:merchant'}}},{},{}),two=new Brain({bot:{}},{},{})
 assert.equal(one.commandNames.has('trade'),true);assert.equal(two.commandNames.has('trade'),false)
 const limited=new Brain({bot:{currentWindow:{type:'minecraft:merchant'}}},{},{},{allowedCommands:['pos']})
 assert.deepEqual([...limited.commandNames],['pos'])
})
function fixture(){return Object.assign(new EventEmitter(),{_client:new EventEmitter()})}
for(const [name,method]of [['chest','openBlock'],['furnace','openFurnace'],['smoker','openFurnace'],['enchanting_table','openEnchantmentTable'],['anvil','openAnvil'],['loom','openBlock'],['crafter','openBlock']])test(`right-click window initialization: ${name}`,async()=>{
 const bot=fixture();let calls=0
 bot[method]=async target=>{assert.equal(target.name,name);calls++;bot.currentWindow={type:'fixture',slots:[]};bot.emit('windowOpen',bot.currentWindow);return bot.currentWindow}
 const result=await openWindow(bot,{name})
 assert.equal(result.confirmed,true);assert.equal(calls,1);assert.equal(bot.listenerCount('windowOpen'),1)
 bot.emit('end');assert.equal(bot.listenerCount('windowOpen'),0)
})
test('aborted native open closes its late window and does not claim successful opening',async()=>{
 const bot=fixture(),controller=new AbortController();let resolve,closed=0
 bot.openBlock=()=>new Promise(r=>{resolve=r});bot.closeWindow=()=>{closed++;bot.currentWindow=null}
 const action=openWindow(bot,{name:'chest'},{signal:controller.signal});await Promise.resolve();controller.abort()
 let settled=false;action.finally(()=>{settled=true}).catch(()=>{})
 await Promise.resolve();assert.equal(settled,false,'Cancellation must not release the native settlement barrier')
 const late={type:'minecraft:generic_9x3'};bot.currentWindow=late;resolve(late)
 await assert.rejects(action)
 assert.equal(closed,1);bot.emit('end')
})
test('known block source follows the window instance and not the reused ID or changed aim',async()=>{
 const bot=fixture(),{commands}=loadCommands(),position={x:2,y:65,z:0}
 bot.game={dimension:'overworld'}
 bot.openBlock=async()=>bot.currentWindow={id:4,type:'minecraft:generic_9x3',slots:[]}
 const result=await openWindow(bot,{name:'chest',position})
 const expected={kind:'block',name:'chest',position:{x:2,y:65,z:0},dimension:'overworld'}
 position.z=2
 assert.deepEqual(result.window.openedFrom,expected)
 assert.deepEqual((await commands.window(bot)).window.openedFrom,expected)
 bot.currentWindow={...bot.currentWindow}
 assert.equal((await commands.window(bot)).window.openedFrom,null)
 await openWindow(bot,{name:'chest',position})
 assert.equal((await commands.window(bot)).window.openedFrom.position.z,2)
 bot.emit('end')
})
test('wrong or missing windows reject operations without opening or moving items',async()=>{
 const {commands}=loadCommands(),bot={}
 assert.equal((await commands.furnace(bot,['input','1'])).errorCode,'WINDOW_REQUIRED')
 bot.currentWindow={type:'minecraft:loom'}
 assert.equal((await commands.furnace(bot,['input','1'])).errorCode,'WINDOW_TYPE_MISMATCH')
 assert.equal((await commands.trade(bot,['0'])).errorCode,'WINDOW_TYPE_MISMATCH')
 for(const name of ['furnace','anvil','enchant','station'])assert.equal((await commands[name](bot,['open'])).errorCode,'INVALID_ARGUMENTS')
})
