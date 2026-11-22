const test = require('node:test')
const assert = require('node:assert/strict')
const { contracts, validateArguments, normalizeFailure, withContract } = require('../src/commands/command_contract')
const { categories } = require('../src/commands/catalog')
const { loadCommands } = require('../src/commands')
const { SteveXAgent } = require('../src/agent/agent')
const { sleep } = require('../src/agent/action_executor')

const defaults = {nearby:[],findplayer:['SteveX-Test'],findblock:['stone'],recipes:['oak_planks'],look:['angle','0','0'],goto:['0','0','0'],dig:['0','0','0'],move:['forward'],right:['click'],swing:[],follow:['SteveX-Test'],wait:['1'],craft:['oak_planks'],equip:['stone'],hotbar:['1'],drop:['stone'],interactentity:['cow']}
function valid(name){return ({attack:['cow'],slotinfo:['inventory:1'],slotclick:['inventory:1'],moveslot:['inventory:1','inventory:2'],putaway:['inventory:1'],transfer:['deposit','inventory:1'],sneak:['off']})[name]||defaults[name]||[]}
test('contracts cover core commands, slot operations and recovery with shared catalog usage', () => {
  const expected = categories.filter(c=>['query','motion','interaction','inventory','survival'].includes(c.id)).flatMap(c=>c.names.split(' ')).concat(['slotclick','moveslot','putaway','transfer']).sort(), {list}=loadCommands()
  assert.equal(expected.length,41);assert.deepEqual(Object.keys(contracts).sort(),expected)
  for(const name of expected){assert.equal(validateArguments(name,valid(name)),null);assert.equal(list.find(c=>c.name===name).usage,contracts[name].usage)}
})

for(const name of Object.keys(contracts)) {
  test(`${name}: rejects excess/missing/non-token arguments before accessing game state`, async () => {
    const dir=categories[0].names.split(' ').includes(name)?'queries':'actions'
    const handler=require(`../src/commands/${dir}/${name}`).handler
    const bot=new Proxy({}, {get(){throw Error('must not access game state')}})
    const spec=contracts[name], cases=[[...Array(spec.fields.length+1)].map(()=> 'extra'),null,[null],['']]
    if(spec.min)cases.push([])
    for(const args of cases){const r=await handler(bot,args,{});assert.equal(r.errorCode,'INVALID_ARGUMENTS',name);assert.equal(r.ok,false);assert.ok(r.usage);assert.equal(typeof r.output,'string');assert.match(r.output,/^\[INVALID_ARGUMENTS\]/)}
    const loaded=loadCommands().commands[name]
    assert.equal((await loaded(bot,['extra','extra','extra','extra','extra'],{})).errorCode,'INVALID_ARGUMENTS')
  })
}

test('numeric syntax, ranges, enums, namespaces and exact counts are strict', () => {
  const cases = [
    ['look',['0x10','0']],['look',['Infinity','0']],['look',['0','1.6']],['look',['0','NaN','0']],['goto',['1e100','0','0']],
    ['dig',['1.5','0','0']],['dig',['9007199254740992','0','0']],['nearby',['all','2abc']],['nearby',['all','129']],
    ['findblock',['stone','0']],['findblock',['other:stone']],['recipes',['other:oak_log']],['move',['up']],['move',['forward','1e3']],
    ['move',['forward','30001']],['swing',['lef']],['wait',['1.5']],['wait',['9007199254740992']],['craft',['oak_planks','65']],
    ['craft',['oak_planks','1','nearby']],['craft',['oak_planks','1','auto','01']],['craft',['oak_planks','1','auto','0']]
  ]
  for(const[name,args]of cases)assert.equal(validateArguments(name,args).errorCode,'INVALID_ARGUMENTS',`${name} ${args}`)
  for(const[name,args]of [['look',['angle','-1e2','0']],['goto',['-.5','+1.0','2e2']],['dig',['-1','0','1.0']],['move',['FORWARD','30000']],['swing',['OFF-HAND']],['craft',['MINECRAFT:OAK_PLANKS','64','HAND','1']]])assert.equal(validateArguments(name,args),null)
})

test('enum normalization is shared, missing optional arguments remain omitted', async () => {
  for(const[name,args,expected]of [['swing',['OFF-HAND'],['off-hand']],['move',['FORWARD'],['forward']],['craft',['oak_planks','1','TABLE'],['oak_planks','1','table']]]){
    const h=withContract(name,async(b,args)=>({ok:true,args}));assert.deepEqual((await h({},args)).args,expected)
  }
})

test('failure normalization is idempotent and preserves partial results and recipe alternatives', () => {
  const original={ok:false,errorCode:'RECIPE_SELECTION_REQUIRED',error:'Choose an available recipe',output:'available recipes',availableRecipes:[{index:2}],changes:[{item:'oak_log',delta:-1}],completedExecutions:1,producedCount:4,effect:'observed'}
  const r=normalizeFailure('craft',original)
  assert.equal(r.errorCode,original.errorCode);assert.equal(r.availableRecipes,original.availableRecipes);assert.equal(r.changes,original.changes)
  assert.equal(r.completedExecutions,1);assert.equal(r.producedCount,4);assert.equal(r.effect,'observed');assert.match(r.output,/available recipes/)
  assert.deepEqual(normalizeFailure('craft',r),r)
  assert.equal(normalizeFailure('craft',{ok:false,errorCode:'CANCELLED',error:'[CANCELLED] Stop requested',output:'[CANCELLED] Partial craft; Stop requested'}).output,'[CANCELLED] Partial craft; Stop requested')
  const empty={ok:true,found:false,output:'No matching block'};assert.equal(normalizeFailure('findblock',empty),empty)
  assert.equal(normalizeFailure('say',original),original)
})

test('skill registry and module exports apply the same query validation', async () => {
  loadCommands()
  const registry=require('../src/runtime/skill_registry.ts')
  for(const name of ['inventory','nearby','findblock'])assert.equal((await registry.get(name).handler({},['extra','extra','extra'],{})).errorCode,'INVALID_ARGUMENTS')
})

test('bad actions including stop/dig stop never stop Brain, clear controls, enqueue or cancel', async () => {
  const agent=new SteveXAgent({},'contract-test',loadCommands().commands)
  agent.bot={};agent.connected=true
  let effects=0
  agent.brainLoop={stop(){effects++}}
  agent.stopPhysicalActions=()=>effects++
  agent.executor.run=()=>{effects++;throw Error('must not enqueue')}
  agent.executor.cancelAll=()=>effects++
  for(const command of ['stop extra','dig stop extra','dig typo','move stop extra','jump extra','look angle 0 0 extra','goto 1 2','swing typo','dig 1.5 2 3','craft oak_planks 65'])assert.equal((await agent.executeCommand(command)).errorCode,'INVALID_ARGUMENTS')
  assert.equal(effects,0)
})

test('dispatch reports offline, cancellation and deadlines without losing partial craft effects', async () => {
  const agent=new SteveXAgent({},'contract-test',{craft:async(b,args,owner,{signal})=>{
    try { await sleep(100,signal) } catch (_) {}
    return {ok:false,errorCode:'CANCELLED',output:'[CANCELLED] partial craft: four planks',changes:[{item:'oak_planks',delta:4}],producedCount:4,completedExecutions:1,effect:'observed'}
  }})
  assert.equal((await agent.executeCommand('craft oak_planks')).errorCode,'NOT_CONNECTED')
  agent.bot={};agent.connected=true
  const r=await agent.executeCommand('craft oak_planks',{timeoutMs:1})
  assert.equal(r.errorCode,'ACTION_TIMEOUT');assert.equal(r.status,'cancelled');assert.equal(r.producedCount,4);assert.equal(r.changes[0].delta,4);assert.match(r.output,/partial craft/)
  assert.match(r.output,/^\[ACTION_TIMEOUT\]/);assert.doesNotMatch(r.output,/\[CANCELLED\]/)
})

test('window absence and target/path state failures have explicit machine codes', async () => {
  const {commands}=loadCommands()
  assert.equal((await commands.window({},[],{})).errorCode,'NO_WINDOW')
  assert.equal((await commands.dig({blockAtCursor:()=>null},[],{})).errorCode,'NO_TARGET')
  assert.equal((await commands.dig({blockAt:()=>({diggable:true,boundingBox:'block'}),canDigBlock:()=>false},['0','0','0'],{})).errorCode,'PATHFINDER_NOT_READY')
  assert.equal((await commands.collect({},[],{movements:{}})).errorCode,'ACTION_FAILED')
  assert.equal((await withContract('pos',async()=>{throw Error('unavailable')})({},[])).errorCode,'QUERY_FAILED')
})

test('HTTP rejects non-string and empty commands before dispatch', async t => {
  let dispatches=0
  const {createApp}=require('../src/web/server/app')
  const server=createApp({agents:new Map(),sendCommand(){dispatches++;throw Error('must not dispatch')}}).listen(0,'127.0.0.1')
  await new Promise(resolve=>server.once('listening',resolve))
  t.after(async()=>{server.closeAllConnections();await new Promise(resolve=>server.close(resolve))})
  const url=`http://127.0.0.1:${server.address().port}/api/agents/test/command`
  for(const command of [undefined,null,12,[],{},'','   ']){
    const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({command})})
    assert.equal(response.status,400);const r=await response.json();assert.equal(r.errorCode,'INVALID_ARGUMENTS');assert.equal(r.error,r.output)
  }
  assert.equal(dispatches,0)
})
