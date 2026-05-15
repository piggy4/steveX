/**
 * equipslot <hand|head|torso|legs|feet|off-hand> — get the inventory slot number for an equipment slot
 */
module.exports = {
  name: 'equipslot',
  description: 'Get inventory slot number for an equipment destination',
  usage: 'equipslot <hand|head|torso|legs|feet|off-hand>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: equipslot <hand|head|torso|legs|feet|off-hand>' }
    const dest = args[0].toLowerCase()
    const slot = bot.getEquipmentDestSlot(dest)
    if (slot === undefined || slot === null) return { ok: false, error: `Unknown destination: ${dest}` }
    return { ok: true, output: `Slot number for "${dest}": ${slot}` }
  }
}
