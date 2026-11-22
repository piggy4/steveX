const {isDeepStrictEqual}=require('node:util')
const bound=new Set(['slotclick','moveslot','putaway','transfer','station','furnace','anvil','enchant','trade','close'])
function capture(bot){
  const window=bot?.currentWindow??null
  const count=window?.type==='minecraft:loom'?3:window?.type==='minecraft:stonecutter'?1:0
  const inputs=count?window.slots?.slice(0,count).map(item=>item?structuredClone({
    type:item.type,name:item.name,count:item.count,components:item.components,removedComponents:item.removedComponents,nbt:item.nbt
  }):null):null
  return {bot,window,inputs}
}
function guard(bot,command,requested,observed){
  const [name,operation]=command.trim().toLowerCase().split(/\s+/)
  if(!bound.has(name))return null
  const current=capture(bot)
  for(const context of [requested,observed]){
    if(!context)continue
    if(context.bot!==bot||context.window!==current.window)
      return {ok:false,errorCode:'WINDOW_CONTEXT_STALE',error:'The active interface changed. Inspect window or inventory again before acting.',confirmed:false,changes:[]}
    if(name==='station'&&operation==='select'&&!isDeepStrictEqual(context.inputs,current.inputs))
      return {ok:false,errorCode:'WINDOW_OPTIONS_STALE',error:'Workstation inputs changed. Inspect window again before choosing an index.',confirmed:false,changes:[]}
  }
  return null
}
module.exports={capture,guard}
