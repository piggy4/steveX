const api = require('../workstations')
const native = require('../workstation_native')
module.exports = {
  name: 'enchant', description: 'Operate open enchanting table; put/lapis source slots are inventory: or hotbar: references. Choice index is not a slot. Open via right click',
  usage: 'enchant put <playerRef> | enchant lapis <playerRef> | enchant choose <0..2> | enchant take',
  handler: require('../workstation_feedback').workstationAction('enchant',native.enchant)
}
