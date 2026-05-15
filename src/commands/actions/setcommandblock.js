/**
 * setcommandblock <x> <y> <z> <command> — set a command block's properties
 */
module.exports = {
  name: 'setcommandblock',
  description: 'Set a command block at coordinates',
  usage: 'setcommandblock <x> <y> <z> <command>',
  async handler(bot, args) {
    if (args.length < 4) return { ok: false, error: 'Usage: setcommandblock <x> <y> <z> <command>' }
    const [x, y, z] = args.slice(0, 3).map(Number)
    if (isNaN(x) || isNaN(y) || isNaN(z)) return { ok: false, error: 'Invalid coordinates' }
    const command = args.slice(3).join(' ')

    const Vec3 = require('vec3').Vec3
    await bot.setCommandBlock(new Vec3(x, y, z), command)
    return { ok: true, output: `Set command block at ${x} ${y} ${z}: ${command}` }
  }
}
