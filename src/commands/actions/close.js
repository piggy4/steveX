/**
 * close — close the currently open window/container
 */
module.exports = {
  name: 'close',
  description: 'Close the currently open container/window',
  usage: 'close',
  async handler(bot) {
    const w = bot.currentWindow || bot.inventory
    await bot.closeWindow(w)
    return { ok: true, output: 'Closed container' }
  }
}
