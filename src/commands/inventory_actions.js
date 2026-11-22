const { isDeepStrictEqual } = require('node:util')
const { withContract, exceptionFailure } = require('./command_contract')
const { result, syncInventory, inventoryChanges } = require('./operation_result')
const { compactItemView } = require('../agent/interaction_memory')

const failure = (errorCode,error) => ({ok:false,errorCode,error})
const sameItem = (a,b) => !!a && !!b && a.type === b.type && a.metadata === b.metadata && isDeepStrictEqual(a.nbt,b.nbt) && isDeepStrictEqual(a.components || [],b.components || []) && isDeepStrictEqual(a.removedComponents || [],b.removedComponents || [])
const carried = bot => [...bot.inventory.slots.slice(1).filter(Boolean),...(bot.inventory.selectedItem ? [bot.inventory.selectedItem] : [])]
function totals(bot) { const counts=new Map();for(const i of carried(bot))counts.set(i.name,(counts.get(i.name)||0)+i.count);return counts }
const variantCount = (bot,item) => carried(bot).filter(i=>sameItem(i,item)).reduce((n,i)=>n+i.count,0)
function findItem(bot,token) {
  const name=token.toLowerCase().replace(/^minecraft:/,'')
  if(!Object.hasOwn(bot.registry.itemsByName,name))return failure('UNKNOWN_ITEM',`Unknown registry item: ${name}`)
  const item=bot.inventory.items().find(i=>i.name===name)
  return item || failure('ITEM_NOT_FOUND',`No ${name} in backpack or quickbar`)
}
function inventoryAction(name,execute) {
  return withContract(name,async(bot,args,agent,{signal}={})=>{
    const reject=(code,message)=>result(name,{...failure(code,message),changes:[]})
    if(bot.currentWindow)return reject('WINDOW_OPEN','Close the current window before changing inventory')
    if(bot.inventory?.selectedItem)return reject('CURSOR_NOT_EMPTY','Empty the cursor before changing inventory')
    try {await syncInventory(bot,{signal})}catch(e){return result(name,{...exceptionFailure(name,e,signal),changes:[],phase:'preflight',output:`No inventory action started: ${e.message}`})}
    const before=totals(bot)
    let outcome,cause,changes=null,snapshotError
    try {signal?.throwIfAborted();outcome=await execute(bot,args,signal)}catch(e){cause=e}
    try {await syncInventory(bot);changes=inventoryChanges(before,totals(bot))}catch(e){snapshotError=e}
    const {verify,...data}=outcome || {}
    const feedback={...data,changes,changesScope:'carried-items-by-name',evidence:snapshotError?[]:['server-inventory-snapshot']}
    if(snapshotError)return result(name,{...feedback,...failure('INVENTORY_CONFIRMATION_UNAVAILABLE',`Final inventory unknown: ${snapshotError.message}; inspect before retrying`),output:'Inventory action not confirmed; final inventory unknown',cancelled:!!signal?.aborted,changes:null})
    if(cause)return result(name,{...feedback,...exceptionFailure(name,cause,signal),output:`Inventory action incomplete; observed changes: ${JSON.stringify(changes)}; ${cause.message}`})
    if(outcome?.ok===false)return result(name,feedback)
    if(!verify?.())return result(name,{...feedback,...failure('INVENTORY_STATE_NOT_CONFIRMED','Requested inventory state not confirmed; inspect slots and cursor before retrying'),output:`Inventory action not confirmed; observed changes: ${JSON.stringify(changes)}`})
    return result(name,{...feedback,ok:true,confirmed:true})
  })
}
function dropAction(name) {
  return inventoryAction(name,async(bot,args,signal)=>{
    const bySlot=args[0].toLowerCase()==='slot',slot=bySlot?require('./slot_scope').storageIndex(args[1]):null
    const item=bySlot?bot.inventory.slots[slot]:findItem(bot,args[0])
    if(!item)return failure('EMPTY_SLOT',`Slot ${args[1]} is empty`)
    if(item.ok===false)return item
    const selected=compactItemView(item),matching=bySlot?[item]:bot.inventory.items().filter(i=>sameItem(i,item))
    const rawCount=args[bySlot?2:1],requested=rawCount===undefined?item.count:Number(rawCount),available=matching.reduce((n,i)=>n+i.count,0)
    if(requested>available)return failure('INSUFFICIENT_ITEMS',`Requested ${requested} ${item.name}, available ${available} with the selected components`)
    const before=variantCount(bot,item)
    let remaining=requested
    for(const source of matching){
      if(!remaining)break
      signal?.throwIfAborted()
      const current=bot.inventory.slots[source.slot]
      if(!sameItem(current,item))throw Object.assign(Error('Source item changed; no automatic retry'),{name:'InventoryChanged'})
      const count=Math.min(remaining,current.count)
      // A one-slot range preserves the chosen components; toss(type,...) can
      // select a different same-type item elsewhere in the inventory.
      await bot.transfer({window:bot.inventory,itemType:current.type,metadata:current.metadata,count,sourceStart:source.slot,sourceEnd:source.slot+1,destStart:-999})
      remaining-=count
    }
    return {target:{item:selected,requestedCount:requested,...(bySlot?{source:{ref:args[1]}}:{})},output:`Dropped ${requested} ${item.name}; inventory reduction confirmed, world pickup is not guaranteed`,verify:()=>!bot.inventory.selectedItem && before-variantCount(bot,item)===requested}
  })
}
module.exports={failure,sameItem,variantCount,findItem,inventoryAction,dropAction,compactItemView}
