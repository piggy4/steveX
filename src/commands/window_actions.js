const {isDeepStrictEqual} = require('node:util')
const {withContract,exceptionFailure} = require('./command_contract')
const {compactItemView} = require('../agent/interaction_memory')
const {resolveScope,logicalSlot,parseSlotRef}=require('./slot_scope')
const {syncWindow}=require('./inventory_sync')
const fail = (code,message) => { const e=new Error(message);e.code=code;throw e }
const same = (a,b) => !!a && !!b && a.type===b.type && a.metadata===b.metadata &&
  isDeepStrictEqual(a.nbt,b.nbt) && isDeepStrictEqual(a.components||[],b.components||[]) &&
  isDeepStrictEqual(a.removedComponents||[],b.removedComponents||[])
function windowOf(bot) {
  if(require('./book_ui').bookContext(bot))fail('BOOK_UI_OPEN','Close the book before manipulating slots')
  const window=bot.currentWindow||bot.inventory
  if(!Array.isArray(window?.slots))fail('WINDOW_UNAVAILABLE','Inventory/window slots unavailable')
  return window
}
function itemAt(w,slot) {
  if(!Number.isInteger(slot)||slot<0||slot>=w.slots.length)fail('INVALID_SLOT','Slot outside current window')
  return w.slots[slot]
}
const snapshot = w => {const view=i=>{if(!i)return null;const {slot,...data}=compactItemView(i);return structuredClone(data)};return {slots:w.slots.map(view),cursor:view(w.selectedItem)}}
function changes(before,after,w,isPlayerWindow) {
  const result=[]
  for(let i=0;i<Math.max(before.slots.length,after.slots.length);i++)if(!isDeepStrictEqual(before.slots[i],after.slots[i]))
    result.push({...logicalSlot(w,i,isPlayerWindow),before:before.slots[i]??null,after:after.slots[i]??null})
  if(!isDeepStrictEqual(before.cursor,after.cursor))result.push({kind:'cursor',before:before.cursor,after:after.cursor})
  return result
}
function action(name,execute,{resolve,state}={}) {
  return withContract(name,async(bot,args=[],agent,{signal}={})=>{
    let w,before,data,error,delta=null,verified=false,beforeState,stateChanges=[]
    try {
      w=resolve?resolve(bot,args):resolveScope(bot,name==='transfer'?(args[0]==='deposit'?'inventory':'window'):parseSlotRef(args[0]).slotSpace,{write:true})
      await syncWindow(bot,w,{signal})
      if(windowOf(bot)!==w)fail('WINDOW_CHANGED','Window changed before operation')
      before=snapshot(w)
      beforeState=state?.(bot,w)
      signal?.throwIfAborted()
      data=await execute(bot,w,args,signal)
      signal?.throwIfAborted()
    } catch(e) {error=e}
    if(before)try {
      if(windowOf(bot)!==w)fail('WINDOW_CHANGED','Window changed during operation')
      await syncWindow(bot,w)
      if(windowOf(bot)!==w)fail('WINDOW_CHANGED','Window changed during confirmation')
      delta=changes(before,snapshot(w),w,w===bot.inventory);verified=true
      if(state){const after=state(bot,w);for(const key of new Set([...Object.keys(beforeState||{}),...Object.keys(after||{})]))if(!isDeepStrictEqual(beforeState?.[key],after?.[key]))stateChanges.push({property:key,before:beforeState?.[key]??null,after:after?.[key]??null})}
    }catch(e){error=error||e}
    const evidence=verified?['server-inventory-snapshot']:[]
    if(error) {
      const failure=exceptionFailure(name,error,signal)
      return {...failure,error:error.message,...(!signal?.aborted&&typeof error.code==='string'?{errorCode:error.code}:{}),action:name,status:signal?.aborted?'cancelled':'failed',confirmed:false,changes:delta,evidence,
        stateChanges,output:'Please inspect observed changes and cursor before retrying; an interrupted operation may have partially applied'}
    }
    const {verify,allowUnchanged,output,window,ok,...details}=data||{}
    const confirmed=!!(verified&&(delta.length>0||stateChanges.length>0||allowUnchanged)&&(!verify||verify()))
    return {ok:confirmed,action:name,status:confirmed?'completed':'failed',confirmed,...details,changes:delta,evidence,
      ...(!confirmed?{errorCode:'SLOT_CHANGE_NOT_CONFIRMED',error:'No server-confirmed slot change'}:{}),
      stateChanges,output:`${name}: ${confirmed?'server-confirmed effect':'no effect confirmed'}`}
  })
}
function emptyCursor(w) {if(w.selectedItem)fail('CURSOR_NOT_EMPTY','Empty the cursor first')}
function ensureCurrent(bot,w,signal) {signal?.throwIfAborted();if(windowOf(bot)!==w)fail('WINDOW_CHANGED','Window changed; no further clicks sent')}
async function move(bot,w,source,dest,count,signal) {
  const result=require('./slot_scope').resultSlot(w)
  if(dest===result)fail('RESULT_SLOT_READ_ONLY','Cannot place into an output slot; inventory hotbar is 36..44, not 0..8')
  if(source===result)fail('RESULT_SLOT_REQUIRES_CLICK','Use slotclick to take a result; moveslot does not choose or repeat recipes')
  emptyCursor(w)
  const item=itemAt(w,source),target=itemAt(w,dest)
  if(source===dest)fail('SAME_SLOT','Source and destination must differ')
  if(!item)fail('SOURCE_EMPTY',`Source slot ${source} is empty`)
  if(count!==undefined && count>item.count)fail('INSUFFICIENT_ITEMS',`Source has ${item.count} items`)
  const amount=count??item.count,compatible=!target||same(item,target),sourceCount=item.count,targetCount=target?.count||0
  if(count!==undefined&&!compatible)fail('DESTINATION_MISMATCH','Partial moves require empty or identical-component destination; omit count for explicit stack swap')
  if(compatible && (target?.count||0)+amount>(target?.stackSize||item.stackSize||64))fail('DESTINATION_FULL','Destination cannot hold requested amount')
  const click=async(slot,button)=>{ensureCurrent(bot,w,signal);await bot.clickWindow(slot,button,0)}
  await click(source,0)
  if(count===undefined || amount===item.count)await click(dest,0)
  else for(let i=0;i<amount;i++)await click(dest,1)
  if(w.selectedItem)await click(source,0)
  return {sourceSlot:source,destSlot:dest,requestedCount:amount,mode:compatible?'move':'swap',verify:()=>{
    if(w.selectedItem)return false
    const from=w.slots[source],to=w.slots[dest]
    if(!compatible)return same(from,target)&&from.count===targetCount&&same(to,item)&&to.count===sourceCount
    return (!from?sourceCount===amount:same(from,item)&&from.count===sourceCount-amount)&&same(to,item)&&to.count===targetCount+amount
  }}
}
async function intoRange(bot,w,source,start,end,count,signal) {
  emptyCursor(w)
  const item=itemAt(w,source)
  if(!item)fail('SOURCE_EMPTY',`Source slot ${source} is empty`)
  const amount=count??item.count
  if(amount>item.count)fail('INSUFFICIENT_ITEMS',`Source has ${item.count} items`)
  const destinations=[]
  for(let i=start;i<end;i++)if(i!==source){const dest=w.slots[i];if(!dest||same(item,dest))destinations.push({slot:i,room:(dest?.stackSize||item.stackSize||64)-(dest?.count||0)})}
  if(destinations.reduce((n,d)=>n+d.room,0)<amount)fail('DESTINATION_FULL','Not enough compatible destination space; no items moved')
  let remaining=amount
  const sourceCount=item.count,destCount=()=>w.slots.slice(start,end).filter((i,index)=>start+index!==source&&same(i,item)).reduce((n,i)=>n+i.count,0),beforeDest=destCount()
  for(const dest of destinations){if(!remaining)break;const n=Math.min(remaining,dest.room);if(n>0){await move(bot,w,source,dest.slot,n,signal);remaining-=n}}
  return {sourceSlot:source,requestedCount:amount,verify:()=>!w.selectedItem&&
    (!w.slots[source]?sourceCount===amount:same(w.slots[source],item)&&w.slots[source].count===sourceCount-amount)&&destCount()===beforeDest+amount}
}
function inventoryRange(w) {
  if(!Number.isInteger(w.inventoryStart)||!Number.isInteger(w.inventoryEnd)||w.inventoryStart<1||w.inventoryEnd<=w.inventoryStart||w.inventoryEnd>w.slots.length)
    fail('INVALID_WINDOW_RANGE','Invalid player inventory range')
  return [w.inventoryStart,w.inventoryEnd]
}
module.exports={action,fail,windowOf,itemAt,move,intoRange,inventoryRange,emptyCursor,ensureCurrent,same}
