/**
 * release — deactivate held item (release bow, stop eating, etc.)
 */
module.exports = {
  name: 'release',
  description: 'Deactivate the held item (release bow, stop eating, etc.)',
  usage: 'release',
  async handler(bot) {
    bot.deactivateItem()
    return { ok: true, output: 'Deactivated held item' }
  }
}
