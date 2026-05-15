/**
 * drive <left|right> <forward|back> — control a vehicle
 * left: -1 (right), 1 (left)
 * forward: -1 (back), 1 (forward)
 */
module.exports = {
  name: 'drive',
  description: 'Control a vehicle (mount first)',
  usage: 'drive <left|right> <forward|back>',
  async handler(bot, args) {
    if (args.length < 2) return { ok: false, error: 'Usage: drive <left|right> <forward|back>' }
    if (!bot.vehicle) return { ok: false, error: 'Not riding a vehicle. Use "mount" first.' }

    const left = args[0].toLowerCase() === 'left' ? 1 : -1
    const forward = args[1].toLowerCase() === 'forward' ? 1 : -1

    bot.moveVehicle(left, forward)
    return { ok: true, output: `Driving: ${args[0]}, ${args[1]}` }
  }
}
