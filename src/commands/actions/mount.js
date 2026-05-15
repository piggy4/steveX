/**
 * mount — mount the nearest rideable entity
 */
module.exports = {
  name: 'mount',
  description: 'Mount the nearest rideable entity',
  usage: 'mount',
  async handler(bot) {
    const vehicle = Object.values(bot.entities).find(e => e.type === 'mob' || e.type === 'object')
    if (!vehicle) return { ok: false, error: 'No rideable entity nearby' }
    await bot.mount(vehicle)
    return { ok: true, output: `Mounted ${vehicle.name || vehicle.type}` }
  }
}
