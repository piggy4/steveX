/**
 * denypack — deny server resource pack
 */
module.exports = {
  name: 'denypack',
  description: 'Deny server resource pack',
  usage: 'denypack',
  async handler(bot) {
    bot.denyResourcePack()
    return { ok: true, output: 'Denied resource pack' }
  }
}
