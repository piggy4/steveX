// Local isolated acceptance fixture; no model calls or credentials in evidence.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process'),{Vec3}=require('vec3')
const {commands}=require('../src/commands').loadCommands(),{syncWindow}=require('../src/commands/inventory_sync'),{formatCommandResult}=require('../src/agent/command_feedback')
const dir=path.resolve(__dirname,'../runs/api-acceptance-server'),name='Window-Feedback',tag='window_feedback_fixture'
const rcon=command=>execFileSync(process.execPath,[path.join(dir,'rcon.cjs'),command],{encoding:'utf8',timeout:12000}).trim()
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms)),log={startedAt:new Date().toISOString(),cases:[]}
let bot,started=false
const call=(name,args=[])=>commands[name](bot,args,{bot},{signal:AbortSignal.timeout(12000)})
async function check(name,run){const result=await run();log.cases.push({name,result});console.log('PASS '+name)}
async function open(block){if(bot.currentWindow)await call('close');await bot.lookAt(new Vec3(7502.5,100.5,7502.5),true);assert.equal(bot.blockAt(new Vec3(7502,100,7502)).name,block);const r=await call('right',['click']);assert.equal(r.ok,true,JSON.stringify(r));await sleep(200)}
async function replace(block){if(bot.currentWindow)await call('close');rcon(`setblock 7502 100 7502 ${block}`);await sleep(200);await open(block)}
async function run(){
 rcon('list');bot=require('mineflayer').createBot({host:'127.0.0.1',port:25565,username:name,version:'1.21.11',auth:'offline'})
 await new Promise((resolve,reject)=>{bot.once('spawn',resolve);bot.once('error',reject)});bot.physicsEnabled=false
 require('../src/commands/inventory_sync').installInventorySync(bot)
 rcon('forceload add 7500 7500 7506 7506');started=true;rcon('fill 7500 99 7500 7506 99 7506 stone');rcon(`tp ${name} 7500.5 100 7502.5`)
 for(let i=0;i<100&&(!bot.blockAt(new Vec3(7502,99,7502))||bot.entity.position.distanceTo(new Vec3(7500.5,100,7502.5))>5);i++)await sleep(100)
 rcon(`gamemode survival ${name}`);rcon(`clear ${name}`)
 rcon(`item replace entity ${name} inventory.0 with stone 4`);rcon(`item replace entity ${name} inventory.1 with stone[custom_data={variant:"B"}] 5`);await sleep(200)
 await check('drop slot only consumes selected component stack; over-count refuses without spill',async()=>{
  const refused=await call('drop',['slot','inventory:2','6']);assert.equal(refused.errorCode,'INSUFFICIENT_ITEMS')
  const partial=await call('drop',['slot','inventory:2','2']);assert.equal(partial.confirmed,true,JSON.stringify(partial));assert.equal(bot.inventory.slots[9].count,4);assert.equal(bot.inventory.slots[10].count,3)
  const rest=await call('drop',['slot','inventory:2']);assert.equal(rest.confirmed,true);assert.equal(bot.inventory.slots[10],null)
  const server=rcon(`data get entity ${name} Inventory`);assert.match(server,/Count|count/);return {refused,partial,rest,server}
 })
 await check('furnace fixed source input and fuel report server-confirmed effects',async()=>{
  rcon(`item replace entity ${name} inventory.0 with raw_iron 3`);rcon(`item replace entity ${name} inventory.1 with coal 2`);await replace('furnace')
  const input=await call('furnace',['input','inventory:1','2']);assert.equal(input.confirmed,true,JSON.stringify(input))
  const fuel=await call('furnace',['fuel','inventory:2','1']);assert.equal(fuel.confirmed,true,JSON.stringify(fuel));assert.equal(input.window,undefined)
  const server=rcon('data get block 7502 100 7502 Items');assert.match(server,/raw_iron/);return {input,fuel,server}
 })
 await check('furnace output take observes result and inventory; empty take refuses',async()=>{
  rcon('item replace block 7502 100 7502 container.2 with iron_ingot 2');await sleep(200)
  const take=await call('furnace',['take','furnace:output']);assert.equal(take.confirmed,true,JSON.stringify(take))
  const empty=await call('furnace',['take','furnace:output']);assert.equal(empty.ok,false);assert.equal(empty.confirmed,false)
  return {take,empty,server:rcon('data get block 7502 100 7502 Items')}
 })
 await check('removed station move is rejected and compact moveslot works in crafter',async()=>{
  await replace('crafter');const removed=await call('station',['move','inventory:1','crafter:1']);assert.equal(removed.errorCode,'INVALID_ARGUMENTS')
  const moved=await call('moveslot',['inventory:1','crafter:1','1']);assert.equal(moved.confirmed,true,JSON.stringify(moved));return {removed,moved}
 })
 await check('crafter slot property change and idempotent request have consistent feedback',async()=>{
  const changed=await call('station',['crafter','crafter:2','disabled']);assert.equal(changed.confirmed,true,JSON.stringify(changed))
  const unchanged=await call('station',['crafter','crafter:2','disabled']);assert.equal(unchanged.confirmed,true,JSON.stringify(unchanged));assert.deepEqual(unchanged.changes,[])
  const enabled=await call('station',['crafter','crafter:2','enabled']);assert.equal(enabled.confirmed,true,JSON.stringify(enabled));return {changed,unchanged,enabled,server:rcon('data get block 7502 100 7502 disabled_slots')}
 })
 await check('anvil rename confirms player inventory change and server custom name',async()=>{
  await call('close');rcon(`clear ${name}`);rcon(`item replace entity ${name} inventory.0 with iron_sword`);rcon(`experience set ${name} 30 levels`);await replace('anvil')
  const renamed=await call('anvil',['rename','inventory:1','FeedbackSword']);assert.equal(renamed.confirmed,true,JSON.stringify(renamed));const server=rcon(`data get entity ${name} Inventory`);assert.match(server,/FeedbackSword/);return {renamed,server}
 })
 await check('enchant put, lapis, choose and take confirm slot/component changes',async()=>{
  await call('close');rcon(`clear ${name}`);rcon(`item replace entity ${name} inventory.0 with diamond_sword`);rcon(`item replace entity ${name} inventory.1 with lapis_lazuli 5`);rcon(`experience set ${name} 30 levels`);await replace('enchanting_table')
  const put=await call('enchant',['put','inventory:1']);assert.equal(put.confirmed,true,JSON.stringify(put))
  const lapis=await call('enchant',['lapis','inventory:2']);assert.equal(lapis.confirmed,true,JSON.stringify(lapis));await sleep(300)
  const choose=await call('enchant',['choose','0']);assert.equal(choose.confirmed,true,JSON.stringify(choose))
  const take=await call('enchant',['take']);assert.equal(take.confirmed,true,JSON.stringify(take));const server=rcon(`data get entity ${name} Inventory`);assert.match(server,/enchantments/);return {put,lapis,choose,take,server}
 })
 await check('merchant output gain confirms requested trade; unavailable offer refuses',async()=>{
  await call('close');rcon('setblock 7502 100 7502 air');rcon(`clear ${name}`);rcon(`item replace entity ${name} inventory.0 with emerald 3`)
  rcon(`summon villager 7502.5 100 7502.5 {NoAI:1b,PersistenceRequired:1b,Tags:["${tag}"],VillagerData:{type:"minecraft:plains",profession:"minecraft:farmer",level:1},Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:bread",count:3},maxUses:10,uses:0,priceMultiplier:0.0f}]}}`);await sleep(400)
  const villager=Object.values(bot.entities).find(e=>e.name==='villager'&&e.position.distanceTo(bot.entity.position)<4);assert.ok(villager);await bot.openVillager(villager)
  const traded=await call('trade',['0','2']);assert.equal(traded.confirmed,true,JSON.stringify(traded));const invalid=await call('trade',['9']);assert.equal(invalid.errorCode,'TRADE_UNAVAILABLE');const server=rcon(`data get entity ${name} Inventory`);assert.match(server,/bread/);return {traded,invalid,server}
 })
 await check('inventory sync timeout removes own listener and subsequent snapshot succeeds',async()=>{
  await call('close');await syncWindow(bot,bot.inventory)
  const baseline=bot.listenerCount('setWindowItems:0'),write=bot._client.write.bind(bot._client)
  bot._client.write=(name,data)=>{if(name==='window_click'&&data.stateId===-1)return;return write(name,data)}
  try{await assert.rejects(syncWindow(bot,bot.inventory,{timeoutMs:100}),{code:'INVENTORY_SYNC_TIMEOUT'})}finally{bot._client.write=write}
  assert.equal(bot.listenerCount('setWindowItems:0'),baseline);await syncWindow(bot,bot.inventory);return {timeout:'INVENTORY_SYNC_TIMEOUT',listenersRestored:true,recovered:true}
 })
 await check('LLM formatted results preserve confirmed status and errors without entire window',async()=>{
  const r=await call('drop',['slot','hotbar:9']);const json=JSON.parse(formatCommandResult(r));assert.equal(json.ok,false);assert.equal(json.errorCode,'EMPTY_SLOT');assert.equal(json.confirmed,false);return json
 })
}
run().catch(error=>{log.error=error.stack;process.exitCode=1}).finally(async()=>{
 if(bot)try{if(bot.currentWindow)bot.closeWindow(bot.currentWindow);bot.quit()}catch{}
 if(started)try{rcon(`kill @e[tag=${tag}]`);rcon('kill @e[type=item,x=7500,y=99,z=7500,dx=6,dy=4,dz=6]');rcon('fill 7500 99 7500 7506 103 7506 air');rcon('forceload remove 7500 7500 7506 7506')}catch(e){log.cleanupError=e.message;process.exitCode=1}
 const file=path.join(dir,`window-feedback-${Date.now()}.json`);fs.writeFileSync(file,JSON.stringify(log,null,2));console.log(JSON.stringify({passed:log.cases.length,error:log.error||null,cleanupError:log.cleanupError||null,file}))
})
