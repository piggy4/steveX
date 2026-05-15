/**
 * settings <key> <value> — change client settings
 * key: chat, colorsEnabled, viewDistance, difficulty, enableTextFiltering, enableServerListing
 */
module.exports = {
  name: 'settings',
  description: 'Change client settings',
  usage: 'settings <key> <value>',
  async handler(bot, args) {
    if (args.length < 2) return { ok: false, error: 'Usage: settings <key> <value>' }
    const key = args[0]
    const value = args.slice(1).join(' ')

    // Parse common value types
    let parsed = value
    if (value === 'true') parsed = true
    else if (value === 'false') parsed = false
    else if (!isNaN(Number(value))) parsed = Number(value)

    bot.setSettings({ [key]: parsed })
    return { ok: true, output: `Set setting "${key}" to ${JSON.stringify(parsed)}` }
  }
}
