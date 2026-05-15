const fs = require('fs')
const path = require('path')

function loadJson(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8')
  return JSON.parse(raw)
}

function deepMerge(target, source) {
  const result = { ...target }
  for (const key of Object.keys(source)) {
    if (
      source[key] && typeof source[key] === 'object' && !Array.isArray(source[key]) &&
      target[key] && typeof target[key] === 'object' && !Array.isArray(target[key])
    ) {
      result[key] = deepMerge(target[key], source[key])
    } else {
      result[key] = source[key]
    }
  }
  return result
}

function loadConfig() {
  const root = path.resolve(__dirname, '..', '..')
  const defaultPath = path.join(root, 'configs', 'defaults', 'app.json')
  const envPath = path.join(root, 'configs', 'environments', 'app.json')

  let config = loadJson(defaultPath)

  // If environment-specific config exists, merge it with the default config
  if (fs.existsSync(envPath)) {
    const envConfig = loadJson(envPath)
    config = deepMerge(config, envConfig)
  }

  return config
}

module.exports = {
  loadConfig
}
