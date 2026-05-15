/**
 * dismount — dismount from current vehicle
 */
module.exports = {
  name: 'dismount',
  description: 'Dismount from current vehicle',
  usage: 'dismount',
  async handler(bot) {
    if (!bot.vehicle) return { ok: false, error: 'Not riding any vehicle' }
    await bot.dismount()
    return { ok: true, output: 'Dismounted' }
  }
}
