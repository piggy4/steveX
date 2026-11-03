function error(code,message){throw Object.assign(new Error(message),{code})}
const playerSlotLayout={result:0,crafting:[1,4],head:5,torso:6,legs:7,feet:8,backpack:[9,35],hotbar:[36,44],offHand:45}
function resultSlot(w) {
  return Number.isInteger(w.craftingResultSlot)&&w.craftingResultSlot>=0?w.craftingResultSlot:
    w.type==='minecraft:inventory'?0:null
}
function parseSlotRef(value) {
  const match=typeof value==='string'&&/^(inventory|window):(\d+)$/i.exec(value)
  if(!match||!Number.isSafeInteger(Number(match[2])))error('INVALID_SLOT_REFERENCE','Use inventory:<slot> or window:<slot>; bare numbers are not inferred')
  return {slotSpace:match[1].toLowerCase(),slot:Number(match[2])}
}
function resolveScope(bot,scope,{write=false}={}) {
  if(!['inventory','window'].includes(scope))error('INVALID_SLOT_SCOPE','Specify inventory or window explicitly')
  if(write&&require('./book_ui').bookContext(bot))error('BOOK_UI_OPEN','Close the book before manipulating slots')
  if(scope==='window') {
    if(!bot.currentWindow)error('WINDOW_REQUIRED','No container/workstation window; no fallback to inventory')
    return bot.currentWindow
  }
  if(!Array.isArray(bot.inventory?.slots))error('INVENTORY_UNAVAILABLE','Player inventory unavailable')
  return write&&bot.currentWindow?bot.currentWindow:bot.inventory
}
function nativeSlot(bot,scope,slot) {
  const w=resolveScope(bot,scope,{write:true})
  if(!Number.isSafeInteger(slot)||slot<0)error('INVALID_SLOT','Slot must be a nonnegative safe integer')
  if(scope==='window') {
    if(!Number.isInteger(w.inventoryStart)||slot>=w.inventoryStart)error('INVALID_SLOT','Window scope only addresses container/workstation slots; player items use inventory 9..44')
    return slot
  }
  if(slot>45)error('INVALID_SLOT','Fixed player inventory range is 0..45')
  if(w===bot.inventory)return slot
  if(slot<9||slot>44)error('SLOT_UNAVAILABLE_IN_WINDOW','Crafting/equipment/off-hand clicks require closing the container')
  if(w.inventoryEnd-w.inventoryStart!==36)error('INVALID_WINDOW_RANGE','Player region must contain 36 slots')
  return w.inventoryStart+slot-9
}
function logicalSlot(w,index,isPlayerWindow=false) {
  return isPlayerWindow?{slotSpace:'inventory',slot:index}:index>=w.inventoryStart&&index<w.inventoryEnd?
    {slotSpace:'inventory',slot:9+index-w.inventoryStart}:{slotSpace:'window',slot:index}
}
function playerSlots(bot) {
  const slots=Array.from(resolveScope(bot,'inventory').slots)
  if(slots.length<46)error('INVENTORY_UNAVAILABLE','Complete player inventory cache unavailable')
  const w=bot.currentWindow
  if(w) {
    if(!Array.isArray(w.slots)||!Number.isInteger(w.inventoryStart)||w.inventoryStart<1||w.inventoryEnd-w.inventoryStart!==36||w.inventoryEnd>w.slots.length)
      error('INVALID_WINDOW_RANGE','Cannot map the open window player region to fixed inventory slots')
    for(let i=0;i<36;i++)slots[9+i]=w.slots[w.inventoryStart+i]??null
  }
  return slots
}
function querySlot(bot,scope,slot) {
  const w=resolveScope(bot,scope),slots=scope==='inventory'?playerSlots(bot):w.slots
  const limit=scope==='inventory'?46:w.inventoryStart
  if(!Number.isSafeInteger(slot)||slot<0||slot>=limit)error('INVALID_SLOT',`Slot outside ${scope} range 0..${limit-1}`)
  if(scope==='inventory'&&slots.length<46)error('INVENTORY_UNAVAILABLE','Complete player inventory cache unavailable')
  return {item:slots[slot]??null,slotSpace:scope,slot,...(scope==='window'?{windowId:w.id,windowType:w.type}:{}),
    source:scope==='window'||(bot.currentWindow&&slot>=9&&slot<=44)?'current-window-cache':'player-inventory-cache'}
}
module.exports={resolveScope,playerSlots,querySlot,nativeSlot,logicalSlot,parseSlotRef,playerSlotLayout,resultSlot}
