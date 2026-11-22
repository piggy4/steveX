const test = require('node:test')
const assert = require('node:assert/strict')
const { inventorySnapshot } = require('../src/web/server/inventory')
const { createApp } = require('../src/web/server/app')

function agent(name) {
  const slots = Array(46).fill(null)
  slots[9] = { name: name === 'a' ? 'apple' : 'diamond', count: 3 }
  slots[5] = { name: 'iron_helmet', count: 1, durabilityUsed: 2, maxDurability: 165 }
  slots[45] = { name: 'shield', count: 1 }
  return { name, sessionId: name + '-session', isOnline: () => true, bot: {
    version: '1.21.11', quickBarSlot: 1, inventory: { slots },
    getEquipmentDestSlot: name => ({ head: 5, torso: 6, legs: 7, feet: 8, 'off-hand': 45 })[name]
  } }
}

test('preview preserves empty slots, equipment, offhand, selected hotbar and cursor without mutation', () => {
  const a = agent('a')
  const before = JSON.stringify(a.bot.inventory)
  const view = inventorySnapshot(a)
  assert.equal(view.storage.length, 36)
  assert.equal(view.storage[0].ref, 'inventory:1')
  assert.equal(view.storage[1].item, null)
  assert.equal(view.selectedRef, 'hotbar:2')
  assert.equal(view.equipmentFreshness, 'player-inventory-cache')
  assert.equal(view.equipment[0].item.durability.remaining, 163)
  assert.equal(view.equipment[4].item.name, 'shield')
  assert.equal(view.container, null)
  assert.equal(JSON.stringify(a.bot.inventory), before)
})

test('current window supplies authoritative storage without mixing container and player slot numbers', () => {
  const a = agent('a')
  const slots = Array(63).fill(null)
  slots[0] = { name: 'stone', count: 64 }
  slots[27] = { name: 'bread', count: 2 }
  a.bot.currentWindow = { id: 4, type: 'minecraft:generic_9x3', inventoryStart: 27, inventoryEnd: 63, slots, selectedItem: { name: 'coal', count: 1 } }
  const view = inventorySnapshot(a)
  assert.equal(view.equipmentFreshness, 'last-known')
  assert.equal(view.container.slots.length, 27)
  assert.equal(view.container.slots[0].ref, 'storage_9x3:1')
  assert.equal(view.storage[0].item.name, 'bread')
  assert.equal(view.storage[0].ref, 'inventory:1')
  assert.equal(view.cursor.name, 'coal')
  a.bot.currentWindow = null
  assert.equal(inventorySnapshot(a).container, null)
  assert.equal(inventorySnapshot(a).storage[0].item.name, 'apple')
  a.isOnline = () => false
  assert.deepEqual(inventorySnapshot(a), { agentName: 'a', sessionId: 'a-session', online: false })
})

test('read-only HTTP preview isolates agents and rejects missing agents, writes and unsafe texture paths', async t => {
  const agents = new Map(['a', 'b', '__proto__'].map(name => [name, agent(name)]))
  const server = createApp({ agents }).listen(0, '127.0.0.1')
  await new Promise(resolve => server.once('listening', resolve))
  t.after(() => { server.closeAllConnections(); server.close() })
  const base = `http://127.0.0.1:${server.address().port}`
  for (const name of agents.keys()) {
    const response = await fetch(`${base}/api/agents/${encodeURIComponent(name)}/inventory-preview`)
    const view = await response.json()
    assert.equal(response.headers.get('cache-control'), 'no-store')
    assert.equal(view.agentName, name)
    assert.equal(view.storage[0].item.name, name === 'a' ? 'apple' : 'diamond')
  }
  assert.equal((await fetch(base + '/api/agents/missing/inventory-preview')).status, 404)
  assert.equal((await fetch(base + '/api/agents/a/inventory-preview', { method: 'POST' })).status, 404)
  assert.equal((await fetch(base + '/api/item-textures/1.21.11/invalid%2Fname')).status, 400)
  assert.equal((await fetch(base + '/api/item-textures/1.21.11/no_such_item')).status, 404)
})
