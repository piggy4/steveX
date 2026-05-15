/**
 * unequip [slot] — unequip an item from a slot
 * slot: hand, head, torso, legs, feet, off-hand (default: hand)
 */
module.exports = {
  name: 'unequip',
  description: 'Unequip an item from a slot',
  usage: 'unequip [slot]',
  async handler(bot, args) {
    const destination = args[0] || 'hand'
    const valid = ['hand', 'head', 'torso', 'legs', 'feet', 'off-hand']
    if (!valid.includes(destination)) return { ok: false, error: `Invalid slot. Valid: ${valid.join(', ')}` }
    await bot.unequip(destination)
    return { ok: true, output: `Unequipped ${destination}` }
  }
}
