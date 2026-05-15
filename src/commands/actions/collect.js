/**
 * collect — pick up nearest dropped item
 */
const Vec3 = require('vec3').Vec3
const { goals: pathfinderGoals } = require('mineflayer-pathfinder')

module.exports = {
  name: 'collect',
  description: 'Walk to the nearest dropped item and pick it up',
  usage: 'collect',
  async handler(bot, args, agent) {
    if (!agent.movements) return { ok: false, error: 'Pathfinder not ready' }

    const drop = Object.values(bot.entities).find(e => e.type === 'object' && e.name === 'item')
    if (!drop) return { ok: false, error: 'No dropped items nearby' }

    await bot.pathfinder.goto(new pathfinderGoals.GoalNear(drop.position.x, drop.position.y, drop.position.z, 1))
    return { ok: true, output: `Moved to item drop` }
  }
}
