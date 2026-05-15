/**
 * mine <x> <y> <z> — walk to and dig a block at coordinates
 */
const Vec3 = require('vec3').Vec3
const { goals: pathfinderGoals } = require('mineflayer-pathfinder')

module.exports = {
  name: 'mine',
  description: 'Walk to and dig a block at coordinates',
  usage: 'mine <x> <y> <z>',
  async handler(bot, args, agent) {
    if (args.length < 3) return { ok: false, error: 'Usage: mine <x> <y> <z>' }
    const [x, y, z] = args.map(Number)
    if (isNaN(x) || isNaN(y) || isNaN(z)) return { ok: false, error: 'Invalid coordinates' }
    if (!agent.movements) return { ok: false, error: 'Pathfinder not ready' }

    const pos = new Vec3(x, y, z)
    const block = bot.blockAt(pos)
    if (!block) return { ok: false, error: 'Block not loaded' }

    await bot.pathfinder.goto(new pathfinderGoals.GoalBlock(x, y + 1, z))
    await bot.dig(block)
    return { ok: true, output: `Mined ${block.name} at ${x} ${y} ${z}` }
  }
}
