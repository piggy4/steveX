/**
 * stand — stop all movement controls
 */
module.exports = {
  name: 'stand',
  description: 'Stop all movement controls',
  usage: 'stand',
  async handler(bot) {
    bot.clearControlStates()
    return { ok: true, output: 'All movement stopped' }
  }
}
