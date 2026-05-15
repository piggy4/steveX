/**
 * fish — cast and reel fishing rod
 */
module.exports = {
  name: 'fish',
  description: 'Go fishing with a fishing rod',
  usage: 'fish',
  async handler(bot) {
    await bot.fish()
    return { ok: true, output: 'Finished fishing' }
  }
}
