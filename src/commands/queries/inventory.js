const {withContract}=require('../command_contract')
const {playerSlots}=require('../slot_scope')
const {brief}=require('../item_details')
const groups={equipment:[5,6,7,8,45],hotbar:Array.from({length:9},(_,i)=>36+i),backpack:Array.from({length:27},(_,i)=>9+i)}
const equipmentNames={5:'head',6:'torso',7:'legs',8:'feet',45:'off-hand'}
const command={
  name:'inventory',usage:'inventory [slots]',
  description:'Default: compact equipment/off-hand, hotbar and backpack groups, current selected item and backpack empty count. Nonempty cursor returned separately, not lost items or free-space contents. slots: fixed PLAYER indices and slotLayout, unchanged across windows: result 0 (take only), crafting 1..4, armor 5..8, backpack 9..35, hotbar 36..44, off-hand 45. Include selected empty hotbar slot; use slotinfo inventory:9/window:0 for details; identical-name variants stay separate',
  handler:withContract('inventory',async(bot,args)=>{
    const slots=playerSlots(bot),showSlots=args[0]==='slots'
    const make=slot=>({...brief(slots[slot]),...(showSlots?{slot}:{}),...(slot===36+bot.quickBarSlot?{selected:true}:{}),...(equipmentNames[slot]?{destination:equipmentNames[slot]}:{})})
    const data={slotSpace:'inventory',mode:showSlots?'slots':'summary',source:bot.currentWindow?'mapped-window-player-region-and-player-cache':'player-inventory-cache',
      emptySlotCount:slots.slice(9,45).filter(i=>!i).length,capacity:36}
    const cursor=(bot.currentWindow||bot.inventory).selectedItem
    if(cursor)data.cursor=brief(cursor)
    if(showSlots)data.slotLayout=require('../slot_scope').playerSlotLayout
    if(!showSlots)for(const [group,indices]of Object.entries(groups))data[group]=indices.filter(i=>slots[i]).map(make)
    if(showSlots)data.slots=Array.from({length:46},(_,slot)=>slot).filter(slot=>slots[slot] || slot===36+bot.quickBarSlot).map(make)
    return {ok:true,...data,output:JSON.stringify(data)}
  })
}
require('../../runtime/skill_registry.ts').register({...command,args:{mode:{required:false,desc:'slots for fixed player slot numbers'}}})
module.exports=command
