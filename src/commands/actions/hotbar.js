const { inventoryAction } = require('../inventory_actions')
module.exports = {
  name: 'hotbar',
  description: 'Select hotbar key 1..9 without moving items',
  usage: 'hotbar <index>',
  handler: inventoryAction('hotbar',async(bot,args)=>{
    const slot=Number(args[0])-1
    bot.setQuickBarSlot(slot)
    return {target:{ref:'hotbar:'+(slot+1)},output:`Selected quick bar slot ${slot+1}`,verify:()=>bot.quickBarSlot===slot}
  })
}
