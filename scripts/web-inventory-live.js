// Two real Minecraft clients + the production Web app. No LLM calls or keys.
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const { Vec3 } = require('vec3')
const { AgentManager } = require('../src/agent/agent_manager')
const { startWebServer } = require('../src/web/server')

const root = path.resolve(__dirname, '..')
const output = path.join(root, 'runs/web-inventory-live')
const rcon = command => execFileSync(process.execPath, [path.join(root, 'runs/api-acceptance-server/rcon.cjs'), command], { encoding: 'utf8', timeout: 12000 }).trim()
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const names = ['Web-A', 'Web-B']
const usernames = ['SteveX-WebA', 'SteveX-WebB']
const report = { startedAt: new Date().toISOString(), minecraft: '1.21.11', cases: [] }
let manager, server, browser, keeping = false

async function until(check, label, timeout = 15000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) { if (await check()) return; await sleep(100) }
  throw new Error('Timed out: ' + label)
}
async function check(name, run) {
  const evidence = await run()
  report.cases.push({ name, passed: true, evidence })
  console.log('PASS ' + name)
}
async function shutdown() {
  await browser?.close()
  browser = null
  await manager?.close()
  if (server) {
    server.closeResources()
    server.closeAllConnections()
    await new Promise(resolve => server.close(resolve))
    server = null
  }
}

async function main() {
  await fs.mkdir(output, { recursive: true })
  rcon('list')
  const config = { web: { host: '127.0.0.1', port: 8091 }, renderer: { enabled: false },
    agents: names.map((name, i) => ({ name, minecraft: { host: '127.0.0.1', port: 25565, version: '1.21.11', username: usernames[i], auth: 'offline' }, brain: { enabled: false } })) }
  manager = new AgentManager(() => config)
  server = startWebServer(manager)
  await new Promise((resolve, reject) => { server.once('listening', resolve); server.once('error', reject) })
  for (const name of names) manager.connectAgent(name)
  await until(() => names.every(name => manager.agents.get(name)?.isOnline()), 'both clients spawned', 45000)
  const bot = index => manager.agents.get(names[index]).bot
  const preview = async index => {
    const response = await fetch(`http://127.0.0.1:8091/api/agents/${names[index]}/inventory-preview`)
    const data = await response.json()
    assert.equal(response.status, 200, JSON.stringify(data))
    assert.equal(data.agentName, names[index])
    return data
  }
  const command = async (index, text) => {
    const result = await manager.sendCommand(names[index], text)
    assert.equal(result.ok, true, text + ': ' + JSON.stringify(result))
    return result
  }
  rcon('forceload add 8120 8120 8134 8126')
  rcon('fill 8120 99 8120 8134 99 8126 stone')
  rcon('fill 8120 100 8120 8134 104 8126 air')
  for (let i = 0; i < 2; i++) {
    rcon(`gamemode survival ${usernames[i]}`)
    rcon(`effect give ${usernames[i]} minecraft:resistance infinite 4 true`)
    rcon(`tp ${usernames[i]} ${8120.5 + 8 * i} 100 8122.5`)
    rcon(`clear ${usernames[i]}`)
    await until(() => bot(i).entity.position.distanceTo(new Vec3(8120.5 + 8 * i, 100, 8122.5)) < 1 && bot(i).blockAt(new Vec3(8122 + 8 * i, 99, 8122)), 'fixture chunks loaded')
  }
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(root, 'runs/web-inventory-tools/node_modules/playwright'))
  browser = await chromium.launch({ channel: 'msedge', headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } })
  const pageErrors = [], browserWrites = []
  page.on('pageerror', error => pageErrors.push(error.message))
  page.on('request', request => { if (request.method() !== 'GET') browserWrites.push(request.method() + ' ' + new URL(request.url()).pathname) })
  await page.goto('http://127.0.0.1:8091')
  for (const name of names) await page.locator(`[data-agent-name="${name}"] .inventory-preview summary`).click()
  const assertPageItem = async (index, region, slot, name, count) => {
    await page.waitForFunction(({ agent, region, slot, name, count }) => {
      const selector = `[data-agent-name="${agent}"] .inventory-preview ${region} .inventory-slot`
      return [...document.querySelectorAll(selector)].some(el => el.querySelector('.item-slot-number')?.textContent === String(slot) &&
        el.getAttribute('aria-label')?.startsWith(`${name} x${count}\n`))
    }, { agent: names[index], region, slot, name, count })
  }
  await check('different inventory stacks, empty slots and selected hotbar match server', async () => {
    rcon(`item replace entity ${usernames[0]} inventory.0 with apple 17`)
    rcon(`item replace entity ${usernames[0]} hotbar.2 with oak_log 7`)
    rcon(`item replace entity ${usernames[1]} inventory.0 with diamond 23`)
    rcon(`item replace entity ${usernames[1]} hotbar.2 with bread 5`)
    bot(0).setQuickBarSlot(2)
    bot(1).setQuickBarSlot(2)
    await until(async () => (await preview(0)).storage[0].item?.count === 17 && (await preview(1)).storage[0].item?.count === 23, 'inventory packets')
    const truth = usernames.map(username => rcon(`data get entity ${username} Inventory`))
    assert.match(truth[0], /minecraft:apple/)
    assert.match(truth[1], /minecraft:diamond/)
    assert.match(rcon(`data get entity ${usernames[0]} Inventory[{Slot:9b}].count`), /: 17$/)
    assert.match(rcon(`data get entity ${usernames[1]} Inventory[{Slot:9b}].count`), /: 23$/)
    for (let i = 0; i < 2; i++) {
      const state = await preview(i)
      assert.equal(state.storage.length, 36)
      assert.equal(state.storage.filter(entry => !entry.item).length, 34)
      assert.equal(state.selectedRef, 'hotbar:3')
      await assertPageItem(i, '.inventory-grid', 9, i ? 'Diamond' : 'Apple', i ? 23 : 17)
    }
    return { server: truth, snapshots: await Promise.all([preview(0), preview(1)]) }
  })
  await check('armor, damaged durability and offhand remain isolated', async () => {
    for (let i = 0; i < 2; i++) {
      rcon(`item replace entity ${usernames[i]} armor.head with ${i ? 'golden_helmet' : 'diamond_helmet'}[damage=${i ? 4 : 17}]`)
      rcon(`item replace entity ${usernames[i]} armor.chest with ${i ? 'golden_chestplate' : 'diamond_chestplate'}`)
      rcon(`item replace entity ${usernames[i]} armor.legs with ${i ? 'golden_leggings' : 'diamond_leggings'}`)
      rcon(`item replace entity ${usernames[i]} armor.feet with ${i ? 'golden_boots' : 'diamond_boots'}`)
      rcon(`item replace entity ${usernames[i]} weapon.offhand with ${i ? 'totem_of_undying' : 'shield'}`)
    }
    await until(async () => (await preview(0)).equipment[4].item?.name === 'shield' && (await preview(1)).equipment[4].item?.name === 'totem_of_undying', 'equipment packets')
    const states = await Promise.all([preview(0), preview(1)])
    assert.equal(states[0].equipment[0].item.durability.used, 17)
    assert.equal(states[1].equipment[0].item.durability.used, 4)
    assert.equal(states[0].equipment[1].item.durability, undefined)
    await assertPageItem(0, '.inventory-equipment', 45, 'Shield', 1)
    await assertPageItem(1, '.inventory-equipment', 45, 'Totem of Undying', 1)
    const truth = usernames.map(username => rcon(`data get entity ${username} equipment`))
    assert.match(truth[0], /minecraft:diamond_helmet/)
    assert.match(truth[0], /"minecraft:damage": 17/)
    assert.match(truth[0], /minecraft:shield/)
    assert.match(truth[1], /minecraft:golden_helmet/)
    assert.match(truth[1], /"minecraft:damage": 4/)
    assert.match(truth[1], /minecraft:totem_of_undying/)
    return { server: truth, snapshots: states }
  })
  const open = async (index, blockName) => {
    const b = bot(index)
    if (b.currentWindow) await command(index, 'close')
    const x = 8122 + 8 * index
    await until(() => b.blockAt(new Vec3(x, 100, 8122))?.name === blockName, blockName + ' block update')
    await b.lookAt(new Vec3(x + 0.5, 100.5, 8122.5), true)
    await command(index, 'right click')
    await until(() => !!b.currentWindow, 'window opened')
  }
  await check('two simultaneous chest windows reflect separate block inventories', async () => {
    for (let i = 0; i < 2; i++) {
      const x = 8122 + 8 * i
      rcon(`setblock ${x} 100 8122 chest`)
      rcon(`item replace block ${x} 100 8122 container.0 with ${i ? 'emerald' : 'cobblestone'} ${i ? 13 : 31}`)
      await open(i, 'chest')
    }
    await until(async () => (await preview(0)).container?.slots[0].item?.count === 31 && (await preview(1)).container?.slots[0].item?.count === 13, 'chest contents')
    await assertPageItem(0, '.inventory-sections section:last-child .inventory-grid', 0, 'Cobblestone', 31)
    await assertPageItem(1, '.inventory-sections section:last-child .inventory-grid', 0, 'Emerald', 13)
    const states = await Promise.all([preview(0), preview(1)])
    assert.equal(states[0].container.slots.length, 27)
    assert.equal(states[1].container.slots.length, 27)
    // Window IDs are client-local and may be identical: isolation must use agent identity.
    assert.equal(states[0].container.id, states[1].container.id)
    await page.screenshot({ path: path.join(output, 'two-chests.png'), fullPage: true })
    return { server: [8122, 8130].map(x => rcon(`data get block ${x} 100 8122 Items`)), snapshots: states }
  })
  await check('server slot changes and inventory changes while chest stays open update only the correct panel', async () => {
    rcon('item replace block 8122 100 8122 container.0 with stone 9')
    rcon(`item replace entity ${usernames[0]} inventory.0 with apple 6`)
    await assertPageItem(0, '.inventory-sections section:last-child .inventory-grid', 0, 'Stone', 9)
    await assertPageItem(0, '.inventory-grid', 9, 'Apple', 6)
    await assertPageItem(1, '.inventory-sections section:last-child .inventory-grid', 0, 'Emerald', 13)
    return { serverA: rcon('data get block 8122 100 8122 Items'), serverInventory: rcon(`data get entity ${usernames[0]} Inventory`) }
  })
  await check('equipment without server updates is labeled last-known until the container closes', async () => {
    const received = [[], []]
    const listeners = [0, 1].map(i => (packet, meta) => {
      if (['set_player_inventory', 'set_slot', 'window_items'].includes(meta.name)) received[i].push({ packet: meta.name, slot: packet.slot ?? packet.slotId, windowId: packet.windowId })
    })
    for (let i = 0; i < 2; i++) bot(i)._client.on('packet', listeners[i])
    rcon(`item replace entity ${usernames[0]} armor.head with diamond_helmet[damage=40]`)
    rcon(`item replace entity ${usernames[0]} weapon.offhand with torch 8`)
    rcon(`item replace entity ${usernames[1]} weapon.offhand with air`)
    await sleep(800)
    for (let i = 0; i < 2; i++) bot(i)._client.removeListener('packet', listeners[i])
    const cached = await Promise.all([preview(0), preview(1)])
    assert.equal(cached[0].equipmentFreshness, 'last-known')
    assert.equal(cached[1].equipmentFreshness, 'last-known')
    await page.waitForFunction(() => document.querySelectorAll('.equipment-freshness').length === 2)
    const duringOpen = { received, previews: cached, server: usernames.map(username => rcon(`data get entity ${username} equipment`)) }
    await command(0, 'close')
    await command(1, 'close')
    await until(async () => (await preview(0)).equipment[0].item?.durability?.used === 40 &&
      (await preview(0)).equipment[4].item?.name === 'torch' && !(await preview(1)).equipment[4].item, 'equipment refresh after close')
    await assertPageItem(0, '.inventory-equipment', 45, 'Torch', 8)
    const empty = page.locator('[data-agent-name="Web-B"] .inventory-equipment .inventory-slot').last()
    await until(async () => (await empty.getAttribute('aria-label')).includes('Empty'), 'empty offhand rendered')
    const truth = usernames.map(username => rcon(`data get entity ${username} equipment`))
    assert.match(truth[0], /minecraft:torch/)
    assert.doesNotMatch(truth[1], /totem_of_undying/)
    rcon(`item replace entity ${usernames[0]} weapon.offhand with shield`)
    rcon(`item replace entity ${usernames[1]} weapon.offhand with totem_of_undying`)
    await assertPageItem(0, '.inventory-equipment', 45, 'Shield', 1)
    await assertPageItem(1, '.inventory-equipment', 45, 'Totem of Undying', 1)
    await open(0, 'chest')
    await open(1, 'chest')
    return { duringOpen, serverAfterClose: truth, refreshedAfterClose: true }
  })
  await check('clicking and hovering preview slots does not change server inventories', async () => {
    const before = usernames.map(username => rcon(`data get entity ${username} Inventory`))
    const blockBefore = [8122, 8130].map(x => rcon(`data get block ${x} 100 8122 Items`))
    for (const name of names) {
      const slot = page.locator(`[data-agent-name="${name}"] .inventory-grid .inventory-slot`).first()
      await slot.click()
      await slot.hover()
    }
    assert.deepEqual(usernames.map(username => rcon(`data get entity ${username} Inventory`)), before)
    assert.deepEqual([8122, 8130].map(x => rcon(`data get block ${x} 100 8122 Items`)), blockBefore)
    assert.deepEqual(browserWrites, [])
    return { unchangedPlayerInventories: true, unchangedContainers: true, browserWrites }
  })
  await check('real cursor movement is displayed without any browser write', async () => {
    await bot(0).clickWindow(0, 0, 0)
    await until(async () => (await preview(0)).cursor?.name === 'stone', 'cursor held')
    const state = await preview(0)
    assert.equal(state.cursor.count, 9)
    assert.equal(state.container.slots[0].item, null)
    await page.waitForFunction(() => document.querySelector('[data-agent-name="Web-A"] .inventory-sections section:last-child')?.textContent.includes('Cursor'))
    await bot(0).clickWindow(1, 0, 0)
    await until(async () => !(await preview(0)).cursor && (await preview(0)).container.slots[1].item?.count === 9, 'cursor placed')
    assert.match(rcon('data get block 8122 100 8122 Items[{Slot:1b}].id'), /minecraft:stone/)
    return { server: rcon('data get block 8122 100 8122 Items'), browserWrites }
  })
  await check('furnace three slots and separate chest update independently', async () => {
    await command(0, 'close')
    rcon('setblock 8122 100 8122 furnace')
    rcon('item replace block 8122 100 8122 container.0 with raw_iron 3')
    rcon('item replace block 8122 100 8122 container.2 with iron_ingot 2')
    await open(0, 'furnace')
    await assertPageItem(0, '.inventory-sections section:last-child .inventory-grid', 0, 'Raw Iron', 3)
    await assertPageItem(0, '.inventory-sections section:last-child .inventory-grid', 2, 'Iron Ingot', 2)
    const state = await preview(0)
    assert.equal(state.container.slots.length, 3)
    await assertPageItem(1, '.inventory-sections section:last-child .inventory-grid', 0, 'Emerald', 13)
    await page.screenshot({ path: path.join(output, 'furnace-and-chest.png'), fullPage: true })
    rcon('item replace block 8122 100 8122 container.1 with coal 2')
    await until(async () => (await preview(0)).container.slots[1].item?.count === 1, 'furnace fuel consumed')
    await assertPageItem(0, '.inventory-sections section:last-child .inventory-grid', 1, 'Coal', 1)
    assert.match(rcon('data get block 8122 100 8122 Items[{Slot:1b}].count'), /: 1$/)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: path.join(output, 'mobile-live.png'), fullPage: true })
    await page.setViewportSize({ width: 1440, height: 1050 })
    return { server: rcon('data get block 8122 100 8122 Items'), snapshot: await preview(0) }
  })
  await check('closing one window, disconnect and new-session reconnect do not leak stale inventory', async () => {
    await command(0, 'close')
    await until(async () => !(await preview(0)).container, 'window closed')
    await page.waitForFunction(() => document.querySelector('[data-agent-name="Web-A"] .inventory-preview-content')?.textContent.includes('No container open'))
    const oldSession = manager.agents.get(names[0]).sessionId
    manager.disconnectAgent(names[0])
    await page.waitForFunction(() => document.querySelector('[data-agent-name="Web-A"] .inventory-preview-content')?.textContent === 'Agent not started')
    await assertPageItem(1, '.inventory-sections section:last-child .inventory-grid', 0, 'Emerald', 13)
    manager.connectAgent(names[0])
    await until(() => manager.agents.get(names[0])?.isOnline(), 'reconnected', 30000)
    const state = await preview(0)
    assert.notEqual(state.sessionId, oldSession)
    assert.equal(state.container, null)
    await assertPageItem(0, '.inventory-grid', 9, 'Apple', 6)
    assert.deepEqual(pageErrors, [])
    assert.deepEqual(browserWrites, [])
    return { newSession: true, browserWrites, pageErrors }
  })
  await open(0, 'furnace')
  if (process.argv.includes('--keep-running')) {
    // Leave a visual sample tray after automated acceptance, not part of score assertions.
    const samples = [
      'diamond_sword[enchantments={"minecraft:sharpness":3,"minecraft:unbreaking":2},damage=37]',
      'enchanted_book[stored_enchantments={"minecraft:mending":1}]',
      'potion[potion_contents={potion:"minecraft:healing"}]',
      'written_book[written_book_content={title:"Web Preview",author:"SteveX",pages:["Preview sample"]}]',
      'red_bundle[bundle_contents=[{id:"minecraft:diamond",count:3},{id:"minecraft:apple",count:4}]]',
      'red_shulker_box[container=[{slot:0,item:{id:"minecraft:diamond",count:12}},{slot:13,item:{id:"minecraft:apple",count:5}}]]',
      'mace', 'copper_sword', 'copper_chest', 'iron_spear', 'resin_brick', 'pale_oak_sapling'
    ]
    for (const [index, item] of samples.entries()) {
      const result = rcon(`item replace entity ${usernames[0]} inventory.${index + 1} with ${item}`)
      if (!/Replaced/.test(result)) report.sampleWarnings = [...(report.sampleWarnings || []), result]
    }
    rcon(`item replace entity ${usernames[1]} inventory.1 with netherite_sword[enchantments={"minecraft:smite":4}]`)
    rcon(`item replace entity ${usernames[1]} inventory.2 with totem_of_undying`)
    await sleep(1200)
  }
  await page.screenshot({ path: path.join(output, 'final-live.png'), fullPage: true })
  report.completedAt = new Date().toISOString()
  report.passed = true
  keeping = process.argv.includes('--keep-running')
  console.log('LIVE COMPLETE ' + JSON.stringify({ passed: report.cases.length, web: 'http://127.0.0.1:8091', minecraft: '127.0.0.1:25565', keeping }))
}

main().catch(error => { report.passed = false; report.error = error.stack; console.error(error); process.exitCode = 1 }).finally(async () => {
  await fs.mkdir(output, { recursive: true })
  await fs.writeFile(path.join(output, 'results.json'), JSON.stringify(report, null, 2))
  await browser?.close()
  browser = null
  if (!keeping || !report.passed) await shutdown()
})
for (const event of ['SIGINT', 'SIGTERM']) process.on(event, async () => { keeping = false; await shutdown() })
