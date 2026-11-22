const test=require('node:test'),assert=require('node:assert/strict')
const {EventEmitter}=require('node:events')
const {Brain}=require('../src/agent/brain')
const {capture,guard}=require('../src/agent/window_context')
const chest=()=>({id:1,type:'minecraft:generic_9x3'})

test('same-type replacement invalidates prior view and in-flight decisions even when ID is reused',async()=>{
  const bot={currentWindow:chest()},calls=[]
  const agent={bot,isOnline:()=>true,executeCommand:async command=>{calls.push(command);return {ok:true}}}
  const brain=new Brain(agent,{apiKey:'fixture'},new EventEmitter())
  let answer='window'
  brain.llm.chat=async()=>answer
  const request=async()=>(await brain.requestDecision()).result
  await brain.submitDecision(await request()).result
  bot.currentWindow=chest()
  answer='moveslot hotbar:1 chest:1 2'
  assert.equal((await brain.submitDecision(await request()).result).errorCode,'WINDOW_CONTEXT_STALE')
  answer='window';await brain.submitDecision(await request()).result
  answer='moveslot hotbar:1 chest:1 2'
  const issued=await request()
  bot.currentWindow=null
  assert.equal((await brain.submitDecision(issued).result).errorCode,'WINDOW_CONTEXT_STALE')
  assert.deepEqual(calls,['window','window'])
})

test('changed workstation inputs invalidate option selection but not ordinary movement',()=>{
  const bot={currentWindow:{type:'minecraft:loom',slots:[{name:'white_banner',count:1},{name:'red_dye',count:1},null]}}
  const old=capture(bot)
  bot.currentWindow.slots[1].name='blue_dye'
  assert.equal(guard(bot,'station select 5',capture(bot),old).errorCode,'WINDOW_OPTIONS_STALE')
  assert.equal(guard(bot,'move forward',capture(bot),old),null)
  assert.equal(guard(bot,'station select 5',capture(bot),capture(bot)),null)
})
