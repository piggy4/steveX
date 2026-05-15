/**
 * use — activate the held item (eat, bow, throw, etc.)
 */
module.exports = {
  name: 'use',
  description: 'Use/activate the currently held item',
  usage: 'use',
  async handler(bot) {
    if (!bot.heldItem) return { ok: false, error: 'No item in hand to use' }
    bot.activateItem()
    return { ok: true, output: `Used ${bot.heldItem.name}` }
  }
}
