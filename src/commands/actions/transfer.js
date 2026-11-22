const api=require('../window_actions')
const storage=new Set(['minecraft:generic_9x1','minecraft:generic_9x2','minecraft:generic_9x3','minecraft:generic_9x4','minecraft:generic_9x5','minecraft:generic_9x6','minecraft:generic_3x3','minecraft:hopper','minecraft:shulker_box'])
module.exports={name:'transfer',usage:'transfer <deposit|withdraw> <sourceSlot> [count]',
  description:'Deposit uses inventory: or hotbar:; withdraw uses the current storage reference. Whole stack default; exact components preserved. Only storage windows; no name matching/planning. Reports scoped changes',
  handler:api.action('transfer',async(bot,w,args,signal)=>{
    if(!bot.currentWindow)api.fail('WINDOW_REQUIRED','Open a storage window first')
    if(!storage.has(w.type))api.fail('WINDOW_TYPE_MISMATCH','Use workstation operations or slotclick/moveslot for this window')
    const [start,end]=api.inventoryRange(w),deposit=args[0]==='deposit'
    const source=require('../slot_scope').writeRef(bot,args[1])
    api.itemAt(w,source)
    if(deposit ? source<start||source>=end : source>=start)api.fail('INVALID_SOURCE_REGION',`Source must be in ${deposit?'player inventory':'storage'} region`)
    const {sourceSlot,...result}=await api.intoRange(bot,w,source,deposit?0:start,deposit?start:end,args[2]===undefined?undefined:Number(args[2]),signal)
    return {...result,direction:args[0],source:{ref:args[1]}}
  })}
