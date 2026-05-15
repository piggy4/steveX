const mineflayer = require('mineflayer')

function createBot(minecraftConfig) {
  return mineflayer.createBot({
    host: minecraftConfig.host,
    port: minecraftConfig.port,
    username: minecraftConfig.username,
    auth: minecraftConfig.auth,
    version: minecraftConfig.version
  })
}

module.exports = {
  createBot
}
