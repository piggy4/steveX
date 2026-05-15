/**
 * acceptpack — accept server resource pack
 */
module.exports = {
  name: 'acceptpack',
  description: 'Accept server resource pack',
  usage: 'acceptpack',
  async handler(bot) {
    bot.acceptResourcePack()
    return { ok: true, output: 'Accepted resource pack' }
  }
}
