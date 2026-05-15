/**
 * stopdig — stop digging the current block
 */
module.exports = {
  name: 'stopdig',
  description: 'Stop digging the current block',
  usage: 'stopdig',
  async handler(bot) {
    bot.stopDigging()
    return { ok: true, output: 'Digging stopped' }
  }
}
