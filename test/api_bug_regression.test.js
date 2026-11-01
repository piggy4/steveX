const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { Vec3 } = require('vec3')
const hp = require('../src/commands/queries/hp').handler
const inventory = require('../src/commands/queries/inventory').handler
const findplayer = require('../src/commands/queries/findplayer').handler
const say = require('../src/commands/actions/say').handler
const { hudEffectsPlugin } = require('../src/agent/hud_effects')
const { syncPlayerLabel, adapt } = require('../scripts/install-map-player-models')

function protocolBot() {
  const bot = Object.assign(new EventEmitter(), { version: '1.21.11',
    registry: require('prismarine-registry')('1.21.11'), _client: new EventEmitter(),
    supportFeature: () => false, health: 20, game: { dimension: 'overworld' } })
  require('mineflayer/lib/plugins/entities')(bot)
  bot._client.emit('login', { entityId: 1 })
  hudEffectsPlugin(bot)
  return bot
}

test('1.21.11 entity packets carry health boost, all five HUD effects and remaining yellow hearts', async () => {
  const bot = protocolBot()
  bot._client.emit('entity_update_attributes', { entityId: 1, properties: [
    { key: 'generic.max_health', value: 20, modifiers: [{ uuid: 'minecraft:effect.health_boost', amount: 8, operation: 0 }] }
  ] })
  for (const name of ['Regeneration', 'Poison', 'Wither', 'HealthBoost', 'Absorption']) {
    bot._client.emit('entity_effect', { entityId: 1, effectId: bot.registry.effectsByName[name].id, amplifier: 1, duration: 200 })
  }
  const key = bot.registry.entitiesByName.player.metadataKeys.indexOf('player_absorption')
  bot.entity.metadata[key] = 5.5
  let result = await hp(bot, [])
  assert.equal(result.maxHealth, 28)
  const { AgentManager } = require('../src/agent/agent_manager')
  const manager = new AgentManager(() => ({ agents: [{ name: 'test' }] }))
  manager.agents.set('test', { bot, isOnline: () => true, getUsername: () => 'Test', executor: { getStatus: () => null } })
  assert.equal(manager.getStatus({ name: 'test' })[0].maxHealth, 28, 'Agent debug receives the same maximum as hp')
  assert.equal(result.absorption, 5.5)
  assert.equal(result.effects.length, 5)
  assert.ok(result.effects.every(e => e.level === 2 && e.durationSeconds === 10))
  for (let i = 0; i < 20; i++) bot.emit('physicsTick')
  assert.ok((await hp(bot, [])).effects.every(e => e.durationSeconds === 9))
  bot.entity.metadata[key] = 0
  result = await hp(bot, [])
  assert.equal(Object.hasOwn(result, 'absorption'), false)
  assert.equal(result.effects.some(e => e.name === 'absorption'), false)
  assert.doesNotMatch(result.output, /absorption/i)
  // The potion still exists after all yellow hearts are consumed.
  assert.ok(bot.entity.effects[bot.registry.effectsByName.Absorption.id])
  bot._client.emit('entity_update_attributes', { entityId: 1, properties: [{ key: 'generic.max_health', value: 20, modifiers: [] }] })
  assert.equal((await hp(bot, [])).maxHealth, 20)
  assert.equal(manager.getStatus({ name: 'test' })[0].maxHealth, 20)
  bot.emit('end')
})

test('slots omit every ordinary empty slot but retain the selected empty slot across window mapping', async () => {
  const bot = { quickBarSlot: 3, inventory: { slots: Array(46).fill(null) } }
  assert.deepEqual((await inventory(bot, ['slots'])).slots, [{ slot: 39, selected: true }])
  bot.inventory.slots[10] = { name: 'stone', count: 2 }
  let result = await inventory(bot, ['slots'])
  assert.deepEqual(result.slots, [{ name: 'stone', count: 2, slot: 10 }, { slot: 39, selected: true }])
  bot.currentWindow = { id: 1, type: 'minecraft:generic_9x3', inventoryStart: 27, inventoryEnd: 63, slots: Array(63).fill(null) }
  result = await inventory(bot, ['slots'])
  assert.deepEqual(result.slots, [{ slot: 39, selected: true }])
  assert.equal(result.emptySlotCount, 36)
})

test('say sends the original text once, omits its echo and preserves unconfirmed status', async () => {
  const sent = [], message = '/tell Alpha ' + 'long message '.repeat(40)
  const result = await say({ chat: text => sent.push(text) }, message.split(' '))
  assert.deepEqual(sent, [message])
  assert.equal(result.confirmed, false)
  assert.doesNotMatch(result.output, /long message|Alpha/)
  await assert.rejects(say({ chat: () => { throw Error('send failed') } }, ['hello']), /send failed/)
})

test('findplayer never returns stale coordinates across dimension/entity replacement', async () => {
  const bot = protocolBot(), entity = { id: 2, username: 'Alpha', position: new Vec3(10, 64, 10), isValid: true }
  bot.players.Alpha = { username: 'Alpha', entity }
  bot.entities[2] = entity
  assert.equal((await findplayer(bot, ['Alpha'])).players[0].dimension, 'overworld')
  bot._client.emit('entity_destroy', { entityIds: [2] })
  bot.game.dimension = 'the_nether'
  let result = (await findplayer(bot, ['Alpha'])).players[0]
  assert.equal(result.loaded, false)
  assert.equal(Object.hasOwn(result, 'position'), false)
  // Simulate an old tab-list reference surviving a respawn or reused entity ID.
  bot.players.Alpha.entity = entity
  bot.entities[2] = { ...entity, isValid: true }
  result = (await findplayer(bot, ['Alpha'])).players[0]
  assert.equal(result.loaded, false)
  assert.equal(Object.hasOwn(result, 'dimension'), false)
  bot.emit('end')
})

test('name tag updates the matrix used by CSS2D at the moving and final model position', () => {
  const marker = { visible: true, data: {}, position: new Vec3(0, 0, 0),
    updateMatrixWorld(force) { assert.equal(force, true); this.projectedPosition = this.position.clone() } }
  const app = { playerMarkerManager: { getPlayerMarker: () => marker } }
  const instance = { data: { uuid: 'player' }, currentPos: new Vec3(4, 64, 9) }
  for (const x of [4, 8, 12, 12]) {
    instance.currentPos.x = x
    syncPlayerLabel(app, instance)
    assert.deepEqual(marker.projectedPosition, new Vec3(x, 65.8, 9))
  }
  marker.data.foreign = true
  instance.currentPos.x = 100
  syncPlayerLabel(app, instance)
  assert.equal(marker.projectedPosition.x, 12)
  const source = require('node:fs').readFileSync(require.resolve('../vendor/bluemap-player-models/player-models.js'), 'utf8')
  assert.ok(adapt(source).includes(syncPlayerLabel.toString()))
})
