const api = require('../workstations')
const native = require('../workstation_native')
module.exports = {
  name: 'furnace', description: 'Operate open furnace/smoker/blast furnace. input/fuel take inventory: or hotbar: references. Open via right click',
  usage: 'furnace input <playerRef> [count] | furnace fuel <playerRef> [count] | furnace take <furnace:input|furnace:fuel|furnace:output>',
  handler: require('../workstation_feedback').workstationAction('furnace',native.furnace)
}
