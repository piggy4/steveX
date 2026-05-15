/**
 * wake — get out of bed
 */
module.exports = {
  name: 'wake',
  description: 'Get out of bed',
  usage: 'wake',
  async handler(bot) {
    if (!bot.isSleeping) return { ok: false, error: 'Not sleeping' }
    await bot.wake()
    return { ok: true, output: 'Woke up' }
  }
}
