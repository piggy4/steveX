/**
 * goto <x> <y> <z> — pathfind to coordinates
 */
const { goals: pathfinderGoals } = require('mineflayer-pathfinder')

module.exports = {
  name: 'goto',
  description: 'Pathfind to coordinates',
  usage: 'goto <x> <y> <z>',
  async handler(bot, args, agent) {
    if (args.length < 3) return { ok: false, error: 'Usage: goto <x> <y> <z>' }
    const [x, y, z] = args.map(Number)
    if (isNaN(x) || isNaN(y) || isNaN(z)) return { ok: false, error: 'Invalid coordinates' }
    if (!agent.movements) return { ok: false, error: 'Pathfinder not ready' }
    await bot.pathfinder.goto(new pathfinderGoals.GoalBlock(x, y, z))
    return { ok: true, output: `Arrived at ${x} ${y} ${z}` }
  }
}
