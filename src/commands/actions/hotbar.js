const { inventoryAction } = require('../inventory_actions')
module.exports = {
  name: 'hotbar',
  description: 'Select quick-bar index 0..8 (keyboard selection only), corresponding to fixed inventory slots 36..44. Does not move items; index 0 is not inventory:0',
  usage: 'hotbar <slot>',
  handler: inventoryAction('hotbar',async(bot,args)=>{
    const slot=Number(args[0])
    bot.setQuickBarSlot(slot)
    return {target:{quickBarSlot:slot},output:`Selected quick bar slot ${slot}`,verify:()=>bot.quickBarSlot===slot}
  })
}
