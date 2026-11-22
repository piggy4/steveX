const api=require('../window_actions')
module.exports={name:'moveslot',usage:'moveslot <sourceRef> <destRef> [count]',
  description:'Move to a specified destination slot with explicit source/destination scopes, including cross-region moves. inventory indices fixed; window only container/workstation. Output slots cannot receive items; take results with slotclick, not moveslot. Count requires empty/identical-component target; omitted count moves whole stack or swaps. No overflow/dropping; scoped feedback',
  handler:api.action('moveslot',async(bot,w,args,signal)=>{
    const {readRef,writeRef}=require('../slot_scope'),from=readRef(bot,args[0]),to=readRef(bot,args[1])
    const source=writeRef(bot,args[0]),dest=writeRef(bot,args[1])
    const {sourceSlot,destSlot,...result}=await api.move(bot,w,source,dest,args[2]===undefined?undefined:Number(args[2]),signal)
    return {...result,source:{ref:from.ref},destination:{ref:to.ref}}
  })}
