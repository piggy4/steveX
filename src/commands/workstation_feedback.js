const api=require('./workstations')
const {action}=require('./window_actions')
function state(bot){
  const {slots,emptySlots,cursor,slotCount,windowSlotCount,playerSlotLayout,type,windowId,...visible}=api.describe(bot,{compact:true})
  return visible
}
function workstationAction(name,execute){
  return action(name,async(bot,w,args,signal)=>{
    require('./window_actions').emptyCursor(w)
    const result=await execute(bot,args,signal)
    if(result?.ok===false)throw Object.assign(new Error(result.error),{code:result.errorCode||'ACTION_FAILED'})
    return {operation:args[0],...result}
  },{resolve:(bot,args)=>{
    if(!args.length||args[0]==='open'||(name==='station'&&args[0]==='move'))throw Object.assign(new Error(`Invalid ${name} operation; open windows with right click and move slots with moveslot`),{code:'INVALID_ARGUMENTS'})
    const operations={furnace:{input:[2,3],fuel:[2,3],take:[2,2]},anvil:{rename:[3,Infinity],combine:[3,Infinity]},enchant:{put:[2,2],lapis:[2,2],choose:[2,2],take:[1,1]},station:{take:[2,2],select:[2,2],crafter:[3,3],beacon:[2,3]}}
    const range=name==='trade'?[1,2]:operations[name]?.[args[0]]
    if(!range||args.length<range[0]||args.length>range[1])throw Object.assign(new Error(`Invalid ${name} operation or argument count`),{code:'INVALID_ARGUMENTS'})
    const types={furnace:['minecraft:furnace','minecraft:blast_furnace','minecraft:smoker'],anvil:['minecraft:anvil'],enchant:['minecraft:enchantment','minecraft:enchanting_table'],trade:['minecraft:merchant','minecraft:villager'],station:['minecraft:brewing_stand','minecraft:loom','minecraft:stonecutter','minecraft:smithing','minecraft:grindstone','minecraft:cartography','minecraft:beacon','minecraft:crafter_3x3']}
    return api.current(bot,types[name])
  },state})
}
module.exports={workstationAction}
