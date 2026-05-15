/**
 * stopfly — disable flying and restore gravity
 */
module.exports = {
  name: 'stopfly',
  description: '[Creative] Stop flying and restore gravity',
  usage: 'stopfly',
  async handler(bot) {
    bot.creative.stopFlying()
    return { ok: true, output: 'Flying disabled (gravity restored)' }
  }
}
