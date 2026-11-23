function error(code,message){throw Object.assign(new Error(message),{code})}
const grid=(name,n)=>Array.from({length:n},(_,i)=>name+':'+(i+1))
const named=(name,keys)=>keys.split(' ').map(key=>name+':'+key)
const playerRefs=['crafting:result',...grid('crafting',4),...named('equipment','head torso legs feet'),...grid('inventory',27),...grid('hotbar',9),'equipment:offhand']
const playerSlotLayout={backpack:'inventory:1..27',hotbar:'hotbar:1..9',equipment:'equipment:<part>',crafting:'crafting:1..4 or crafting:result'}
const menus={
 crafting:['workbench:result',...grid('workbench',9)],
 furnace:named('furnace','input fuel output'),
 enchantment:named('enchanting','item lapis'),
 anvil:named('anvil','left right result'),
 stonecutter:named('stonecutter','input result'),
 grindstone:named('grindstone','top bottom result'),
 smithing:named('smithing','template base addition result'),
 cartography:named('cartography','map material result'),
 loom:named('loom','banner dye pattern result'),
 brewing_stand:named('brewing','bottle1 bottle2 bottle3 ingredient fuel'),
 beacon:['beacon:payment'],crafter_3x3:grid('crafter',9),
 merchant:named('merchant','input1 input2 result'),lectern:['lectern:book']
}
function menuRefs(w) {
  const type=w.type?.replace(/^minecraft:/,'')
  const alias={blast_furnace:'furnace',smoker:'furnace',enchanting_table:'enchantment',crafting_table:'crafting',villager:'merchant'}
  if(Object.hasOwn(menus,alias[type]||type))return menus[alias[type]||type]
  const storage=/^generic_(9x[1-6]|3x3)$/.exec(type)
  const region=storage?(w.storageRegion||'storage_'+storage[1]):({hopper:'hopper',shulker_box:'shulker',dispenser:'dispenser',dropper:'dropper'})[type]
  return region&&Number.isInteger(w.inventoryStart)&&w.inventoryStart<=54?grid(region,w.inventoryStart):[]
}
function readRef(bot,value) {
  const i=playerRefs.indexOf(value)
  if(i>=0)return {ref:value,slotSpace:'inventory',slot:i}
  const w=bot.currentWindow
  if(!isSlotRef(value))error('INVALID_SLOT_REFERENCE','Use an explicit typed slot reference')
  if(!w)error('WINDOW_REQUIRED','Open the corresponding container or workstation first')
  if(value==='crafter:preview'&&w.type==='minecraft:crafter_3x3')return {ref:value,slotSpace:'window',slot:45}
  const slot=menuRefs(w).indexOf(value)
  if(slot<0||slot>=w.inventoryStart)error('INVALID_SLOT_REFERENCE','Slot reference does not match the current interface')
  return {ref:value,slotSpace:'window',slot}
}
const isSlotRef=value=>typeof value==='string'&&/^[a-z][a-z0-9_]*:[a-z0-9]+$/.test(value)&&!value.startsWith('window:')
function playerIndex(ref){return playerRefs.indexOf(ref)}
function storageIndex(ref){const i=playerIndex(ref);return i>=9&&i<=44?i:-1}
function writeRef(bot,value){
  if(value==='crafter:preview')error('RESULT_SLOT_READ_ONLY','Crafter preview is not interactive')
  const r=readRef(bot,value)
  return nativeSlot(bot,r.slotSpace,r.slot)
}
function resultSlot(w) {
  return Number.isInteger(w.craftingResultSlot)&&w.craftingResultSlot>=0?w.craftingResultSlot:
    w.type==='minecraft:inventory'?0:null
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
    if(!Number.isInteger(w.inventoryStart)||slot>=w.inventoryStart)error('INVALID_SLOT','Container and workstation slots belong to the current window; player items use inventory: or hotbar: references')
    return slot
  }
  if(slot>45)error('INVALID_SLOT','Fixed player inventory range is 0..45')
  if(w===bot.inventory)return slot
  if(slot<9||slot>44)error('SLOT_UNAVAILABLE_IN_WINDOW','Crafting/equipment/off-hand clicks require closing the container')
  if(w.inventoryEnd-w.inventoryStart!==36)error('INVALID_WINDOW_RANGE','Player region must contain 36 slots')
  return w.inventoryStart+slot-9
}
function logicalSlot(w,index,isPlayerWindow=false) {
  const ref=isPlayerWindow?playerRefs[index]:w.type==='minecraft:crafter_3x3'&&index===45?'crafter:preview':
    index>=w.inventoryStart&&index<w.inventoryEnd?playerRefs[9+index-w.inventoryStart]:menuRefs(w)[index]
  return {ref:ref??null}
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
module.exports={resolveScope,playerSlots,querySlot,nativeSlot,logicalSlot,playerSlotLayout,resultSlot,readRef,writeRef,isSlotRef,playerIndex,storageIndex,playerRefs}
