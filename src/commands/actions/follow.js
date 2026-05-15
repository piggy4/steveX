/**
 * follow <playerName> — follow a player continuously
 */
const { goals: pathfinderGoals } = require('mineflayer-pathfinder')

module.exports = {
  name: 'follow',
  description: 'Follow a player continuously',
  usage: 'follow <playerName>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: follow <playerName>' }
    const targetName = args.join(' ').toLowerCase()
    const target = Object.values(bot.players).find(p => p.username.toLowerCase() === targetName)
    if (!target || !target.entity) return { ok: false, error: `Player "${targetName}" not found or too far` }

    const followTask = () => {
      if (!target.entity) return
      const pos = target.entity.position
      bot.pathfinder.setGoal(new pathfinderGoals.GoalNear(pos.x, pos.y, pos.z, 2))
    }

    // Follow every 1s
    const interval = setInterval(followTask, 1000)
    followTask()

    // Store interval on entity for cleanup
    target.entity._followInterval = interval

    bot.once('entityGone', (gone) => {
      if (gone === target.entity) {
        clearInterval(interval)
        bot.pathfinder.stop()
      }
    })

    return { ok: true, output: `Now following ${target.username}` }
  }
}
