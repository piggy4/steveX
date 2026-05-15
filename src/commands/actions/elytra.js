/**
 * elytra — start elytra flight (requires elytra chestplate)
 */
module.exports = {
  name: 'elytra',
  description: 'Start elytra flight',
  usage: 'elytra',
  async handler(bot) {
    await bot.elytraFly()
    return { ok: true, output: 'Elytra flight activated' }
  }
}
