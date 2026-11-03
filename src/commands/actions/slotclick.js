const api=require('../window_actions')
module.exports={name:'slotclick',usage:'slotclick <inventory:slot|window:slot> [left|right|shift-left]',
  description:'Explicit scope: inventory indices stay fixed even with containers open; window only container/workstation slots. left default, right split/place one, shift-left quick move. Output slots are take-only; incompatible cursor placement is rejected. Equipment/off-hand/crafting clicks require closing containers. Reports scoped changes/cursor, not task completion',
  handler:api.action('slotclick',async(bot,w,args,signal)=>{
    const {parseSlotRef,nativeSlot}=require('../slot_scope'),ref=parseSlotRef(args[0]),slot=nativeSlot(bot,ref.slotSpace,ref.slot),button=args[1]||'left'
    api.itemAt(w,slot);api.ensureCurrent(bot,w,signal)
    if(slot===require('../slot_scope').resultSlot(w)&&w.selectedItem){
      const output=w.slots[slot],cursor=w.selectedItem
      if(!api.same(output,cursor)||output.count+cursor.count>(cursor.stackSize||64))api.fail('RESULT_SLOT_READ_ONLY','Cannot place a carried cursor stack into an output slot; inventory hotbar is 36..44')
    }
    if(button==='shift-left')api.emptyCursor(w)
    await bot.clickWindow(slot,button==='right'?1:0,button==='shift-left'?1:0)
    return {...ref,button}
  })}
