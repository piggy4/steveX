/**
 * flyto <x> <y> <z> — fly in a straight line to coordinates in creative mode
 */
const Vec3 = require('vec3').Vec3

module.exports = {
  name: 'flyto',
  description: '[Creative] Fly in a straight line to coordinates',
  usage: 'flyto <x> <y> <z>',
  async handler(bot, args) {
    if (args.length < 3) return { ok: false, error: 'Usage: flyto <x> <y> <z>' }
    const [x, y, z] = args.map(Number)
    if (isNaN(x) || isNaN(y) || isNaN(z)) return { ok: false, error: 'Invalid coordinates' }
    await bot.creative.flyTo(new Vec3(x, y, z))
    return { ok: true, output: `Flew to ${x} ${y} ${z}` }
  }
}
