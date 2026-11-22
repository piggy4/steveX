const { dropAction } = require('../inventory_actions')
module.exports = {
  name: 'drop',
  description: 'Drop exact-name items or drop slot <inventoryRef|hotbarRef> [count] from one fixed player slot. Slot mode never spills to another stack; omitted count drops the selected stack. Name mode spans identical-component stacks only. Confirms carried loss, not world pickup; close windows first',
  usage: 'drop <itemName> [count] | drop slot <slot> [count]',
  handler: dropAction('drop')
}
