/**
 * stop — stop pathfinding
 */
module.exports = {
  name: 'stop',
  description: 'Stop pathfinding',
  usage: 'stop',
  async handler(bot) {
    bot.pathfinder.stop()
    return { ok: true, output: 'Pathfinding stopped' }
  }
}
