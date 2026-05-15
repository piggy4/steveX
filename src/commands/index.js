const fs = require('fs')
const path = require('path')

/**
 * Load all command definitions from the actions/ and queries/ subdirectories.
 * Each file must export: { name, description, usage, handler(bot, args, agent) }
 *
 * Returns an object: { commands: { name -> handler }, list: [ {name, description, usage} ] }
 */
function loadCommands() {
  const commands = {}
  const list = []

  const dirs = ['actions', 'queries', 'creative']

  for (const dir of dirs) {
    const dirPath = path.join(__dirname, dir)
    if (!fs.existsSync(dirPath)) continue

    const files = fs.readdirSync(dirPath).filter(f => f.endsWith('.js'))

    for (const file of files) {
      const filePath = path.join(dirPath, file)
      const mod = require(filePath)

      if (typeof mod.handler !== 'function') {
        console.warn(`[commands] Skipping ${filePath}: missing handler function`)
        continue
      }

      const name = mod.name || path.basename(file, '.js')
      commands[name] = mod.handler
      list.push({
        name,
        description: mod.description || '',
        usage: mod.usage || name
      })
    }
  }

  return { commands, list }
}

module.exports = { loadCommands }
