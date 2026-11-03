const api=require('../window_actions')
module.exports={name:'moveslot',usage:'moveslot <sourceRef> <destRef> [count]',
  description:'Move to a specified destination slot with explicit source/destination scopes, including cross-region moves. inventory indices fixed; window only container/workstation. Output slots cannot receive items; take results with slotclick, not moveslot. Count requires empty/identical-component target; omitted count moves whole stack or swaps. No overflow/dropping; scoped feedback',
  handler:api.action('moveslot',async(bot,w,args,signal)=>{
    const {parseSlotRef,nativeSlot}=require('../slot_scope'),from=parseSlotRef(args[0]),to=parseSlotRef(args[1])
    const source=nativeSlot(bot,from.slotSpace,from.slot),dest=nativeSlot(bot,to.slotSpace,to.slot)
    const {sourceSlot,destSlot,...result}=await api.move(bot,w,source,dest,args[2]===undefined?undefined:Number(args[2]),signal)
    return {...result,source:from,destination:to}
  })}
