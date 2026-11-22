const api = require('../workstations')
const native = require('../workstation_native')
module.exports = {
  name: 'anvil', description: 'Rename/combine in open anvil; source slots are inventory: or hotbar: references. Open via right click',
  usage: 'anvil rename <playerRef> <name> | anvil combine <playerRef1> <playerRef2> [name]',
  handler: require('../workstation_feedback').workstationAction('anvil',native.anvil)
}
