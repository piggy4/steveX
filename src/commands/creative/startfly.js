/**
 * startfly — enable flying (zero gravity) in creative mode
 */
module.exports = {
  name: 'startfly',
  description: '[Creative] Start flying (zero gravity)',
  usage: 'startfly',
  async handler(bot) {
    bot.creative.startFlying()
    return { ok: true, output: 'Flying enabled (zero gravity)' }
  }
}
