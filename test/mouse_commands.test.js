const test=require('node:test'),assert=require('node:assert/strict'),{Vec3}=require('vec3')
const {loadCommands}=require('../src/commands')
const interact=require('../src/commands/actions/interactentity').handler
function fixture(){
  const entity={id:1,position:new Vec3(0,0,0),height:1.62},a={id:2,name:'cow',height:1.4,position:new Vec3(0,0,2)},b={id:3,name:'cow',height:1.4,position:new Vec3(0,0,3)}
  const calls=[],bot={entity,entities:{1:entity,2:a,3:b},world:{raycast:()=>null},lookAt:async()=>{},activateEntity:async e=>calls.push(['normal',e.id]),activateEntityAt:async(e,p)=>calls.push(['at',e.id,p.y])}
  return {bot,a,b,calls}
}
test('removed commands disappear from dispatch, catalog and skill registry',()=>{
  const {commands,list}=loadCommands(),registry=require('../src/runtime/skill_registry.ts')
  for(const name of ['use','stopdig','eat','sleep','useon','interactat','toss','click','release','mount','dismount']){assert.equal(commands[name],undefined);assert.equal(list.some(c=>c.name===name),false);assert.equal(registry.get(name),undefined)}
  for(const name of ['attack','fish','swing','dig','right','drop','slotclick','interactentity'])assert.equal(typeof commands[name],'function')
})
test('swing only sends arm animation, not attack or interaction',async()=>{
  const calls=[],swing=require('../src/commands/actions/swing').handler
  const bot={swingArm:hand=>calls.push(hand)}
  await swing(bot,[]);await swing(bot,['off-hand']);assert.deepEqual(calls,['right','left'])
  assert.match(loadCommands().list.find(c=>c.name==='swing').description,/never attacks/)
})
test('Agent swing never implicitly releases a drawn bow or stops other physical controls',async()=>{
  const {SteveXAgent}=require('../src/agent/agent'),loaded=loadCommands(),calls=[]
  const agent=new SteveXAgent({},'swing-only',loaded.commands,{queryNames:loaded.queryNames})
  agent.bot={swingArm:hand=>calls.push(hand)};agent.connected=true
  agent.stopPhysicalActions=()=>{throw Error('Animation must not stop digging, movement or held use')}
  assert.equal((await agent.executeCommand('swing')).ok,true);assert.deepEqual(calls,['right'])
})
test('entity interaction chooses nearest name match and supports exact id without pre-aim',async()=>{
  const f=fixture();assert.equal((await interact(f.bot,['cow'])).target.id,2)
  assert.equal((await interact(f.bot,['3'])).target.id,3);assert.deepEqual(f.calls,[['normal',2],['normal',3]])
})
test('entity at mode is a single explicit request at a current hit position',async()=>{
  const f=fixture(),r=await interact(f.bot,['cow','AT'])
  assert.equal(r.status,'requested');assert.equal(r.confirmed,false);assert.deepEqual(f.calls,[['at',2,0.7]])
})
test('entity interaction rejects unknown, distant and obstructed targets without requests',async()=>{
  const f=fixture();assert.equal((await interact(f.bot,['zombie'])).errorCode,'ENTITY_NOT_FOUND')
  f.a.position=new Vec3(0,0,20);f.b.position=new Vec3(0,0,21)
  assert.equal((await interact(f.bot,['cow'])).errorCode,'ENTITY_OUT_OF_REACH')
  f.a.position=new Vec3(0,0,2);f.bot.world.raycast=()=>({})
  assert.equal((await interact(f.bot,['cow'])).errorCode,'ENTITY_OBSTRUCTED');assert.equal(f.calls.length,0)
})
test('entity disappearing during aim is rejected rather than retrying another entity',async()=>{
  const f=fixture();f.bot.lookAt=async()=>{delete f.bot.entities[2]}
  assert.equal((await interact(f.bot,['cow'])).errorCode,'TARGET_CHANGED');assert.equal(f.calls.length,0)
})

test('entity interaction rechecks obstruction and current player position after aiming',async()=>{
  const f=fixture();let blocked=false
  f.bot.world.raycast=()=>blocked?{}:null
  f.bot.lookAt=async()=>{blocked=true}
  assert.equal((await interact(f.bot,['cow'])).errorCode,'ENTITY_OBSTRUCTED');assert.equal(f.calls.length,0)
  blocked=false;f.bot.lookAt=async()=>{f.bot.entity.position=new Vec3(0,0,20)}
  assert.equal((await interact(f.bot,['cow'])).errorCode,'ENTITY_OUT_OF_REACH');assert.equal(f.calls.length,0)
})
test('dig stop works directly and malformed subcommands never stop digging',async()=>{
  let stops=0;const dig=require('../src/commands/actions/dig').handler,bot={stopDigging:()=>stops++}
  assert.equal((await dig(bot,['STOP'])).ok,true);assert.equal(stops,1)
  for(const args of [['start'],['stop','extra']])assert.equal((await dig(bot,args)).errorCode,'INVALID_ARGUMENTS')
  assert.equal(stops,1)
})

test('slotclick keeps window-slot behavior and left/right button mapping',async()=>{
  const handler=require('../src/commands/actions/slotclick').handler,calls=[]
  const bot={inventory:{slots:Array(12).fill(null)},_syncWindow:async()=>{},clickWindow:async(...args)=>calls.push(args)}
  assert.equal((await handler(bot,['inventory:1'])).confirmed,false)
  assert.equal((await handler(bot,['inventory:2','right'])).confirmed,false)
  assert.deepEqual(calls,[[9,0,0],[10,1,0]])
  assert.match((await handler(bot,[])).error,/slotclick/)
})
