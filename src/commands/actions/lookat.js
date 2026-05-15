/**
 * lookat <x> <y> <z> — look at block/position
 */
const Vec3 = require('vec3').Vec3

module.exports = {
  name: 'lookat',
  description: 'Look at a position',
  usage: 'lookat <x> <y> <z>',
  async handler(bot, args) {
    if (args.length < 3) return { ok: false, error: 'Usage: lookat <x> <y> <z>' }
    const [lx, ly, lz] = args.map(Number)
    if (isNaN(lx) || isNaN(ly) || isNaN(lz)) return { ok: false, error: 'Invalid coordinates' }
    await bot.lookAt(new Vec3(lx, ly, lz))
    return { ok: true, output: `Looking at ${lx} ${ly} ${lz}` }
  }
}
