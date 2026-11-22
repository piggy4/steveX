// Dedicated player and reserved fixture region; no model calls.
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), { execFileSync } = require('node:child_process')
const { Vec3 } = require('vec3')
const { commands } = require('../src/commands').loadCommands()
const dir = path.resolve(__dirname, '../runs/api-acceptance-server'), name = 'Item-API-Test'
const rcon = c => execFileSync(process.execPath, [path.join(dir, 'rcon.cjs'), c], { encoding: 'utf8', timeout: 12000 }).trim()
const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
const report = { startedAt: new Date().toISOString(), cases: [] }
let bot, started = false
const call = (command, args = [], signal = AbortSignal.timeout(15000)) => commands[command](bot, args, { bot }, { signal })
async function check(name, run) { const result = await run(); report.cases.push({ name, result }); console.log('PASS', name) }
async function give(slot, item) { rcon(`item replace entity ${name} ${slot} with ${item}`); await wait(250) }
async function run() {
  rcon('list')
  bot = require('mineflayer').createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' })
  await new Promise((resolve, reject) => { bot.once('spawn', resolve); bot.once('error', reject) })
  require('../src/commands/inventory_sync').installInventorySync(bot)
  bot.physicsEnabled = false
  rcon('forceload add 7600 7600 7606 7606'); started = true
  rcon('fill 7600 99 7600 7606 99 7606 stone')
  rcon(`tp ${name} 7600.5 100 7602.5`); rcon(`gamemode survival ${name}`); rcon(`clear ${name}`)
  for (let i = 0; i < 150 && (!bot.blockAt(new Vec3(7602, 99, 7602)) || bot.entity.position.distanceTo(new Vec3(7600.5, 100, 7602.5)) > 3); i++) await wait(100)
  assert.ok(bot.blockAt(new Vec3(7602, 99, 7602)))
  await check('equip slot, hotbar and unequip preserve item components and server equipment', async () => {
    await give('inventory.0', 'diamond_helmet[enchantments={"minecraft:protection":2}]')
    const equip = await call('equip', ['slot', 'inventory:1', 'head']); assert.equal(equip.confirmed, true, JSON.stringify(equip))
    assert.match(rcon(`data get entity ${name} equipment.head`), /diamond_helmet/)
    const hotbar = await call('hotbar', ['9']); assert.equal(hotbar.ok, true); assert.equal(bot.quickBarSlot, 8)
    const unequip = await call('unequip', ['head']); assert.equal(unequip.confirmed, true, JSON.stringify(unequip))
    assert.equal(bot.inventory.slots[5], null)
    assert.match(rcon(`data get entity ${name} Inventory`), /minecraft:protection/)
    return { equip, hotbar, unequip }
  })
  await check('right down off-hand shield and up report actual held use', async () => {
    await give('weapon.offhand', 'shield')
    const down = await call('right', ['down', 'off-hand']); assert.equal(down.ok, true, JSON.stringify(down)); await wait(600)
    const up = await call('right', ['up']); assert.equal(up.hadActiveUse, true, JSON.stringify(up)); assert.equal(up.hand, 'off-hand')
    const idle = await call('right', ['up']); assert.equal(idle.hadActiveUse, false, JSON.stringify(idle))
    return { down, up, idle, scope: 'held-use lifecycle, not damage reduction' }
  })
  await check('swing both hands never consumes items or changes aimed block', async () => {
    rcon('setblock 7602 100 7602 stone'); await wait(200)
    await bot.lookAt(new Vec3(7602.5, 100.5, 7602.5), true)
    const before = rcon(`data get entity ${name} Inventory`)
    const hand = await call('swing', ['hand']), off = await call('swing', ['off-hand'])
    assert.equal(hand.ok, true); assert.equal(off.ok, true)
    assert.equal(rcon(`data get entity ${name} Inventory`), before)
    assert.match(rcon('execute if block 7602 100 7602 stone run time query gametime'), /The time is/)
    return { hand, off }
  })
  await check('dig crosshair destroys actual block; dig stop remains callable', async () => {
    await give('weapon.mainhand', 'diamond_pickaxe')
    await bot.lookAt(new Vec3(7602.5, 100.5, 7602.5), true)
    const dig = await call('dig'); assert.equal(dig.confirmed, true, JSON.stringify(dig))
    assert.match(rcon('execute if block 7602 100 7602 air run time query gametime'), /The time is/)
    const stop = await call('dig', ['stop']); assert.equal(stop.ok, true)
    return { dig, stop }
  })
  await check('right click block placement consumes one item and server block matches', async () => {
    await give('weapon.mainhand', 'oak_planks 2')
    await bot.lookAt(new Vec3(7602.5, 99.99, 7602.5), true)
    const result = await call('right', ['click']); assert.equal(result.confirmed, true, JSON.stringify(result))
    assert.match(rcon('execute if block 7602 100 7602 oak_planks run time query gametime'), /The time is/)
    // Block updates may arrive before the corresponding carried-item packet.
    for (let i = 0; i < 40 && bot.heldItem?.count !== 1; i++) await wait(50)
    assert.equal(bot.heldItem.count, 1)
    assert.match(rcon(`clear ${name} oak_planks 0`), /Found 1 matching item/)
    return result
  })
  await check('fish cancellation cleans own bobber without pretending catch success', async () => {
    rcon('fill 7601 100 7601 7605 100 7605 water'); await give('weapon.mainhand', 'fishing_rod')
    await bot.lookAt(new Vec3(7603, 100.1, 7602.5), true)
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(new Error('Fixture cancellation')), 1800)
    let result
    try { result = await call('fish', [], controller.signal) } finally { clearTimeout(timer) }
    assert.notEqual(result.confirmed, true, JSON.stringify(result))
    assert.equal(result.castSent, true)
    assert.equal(typeof result.bobberId, 'number')
    assert.equal(result.cleanup.bobberRemovalConfirmed, true)
    await wait(1200)
    assert.equal(Object.values(bot.entities).some(e => /fishing_bobber/.test(e.name || '')), false)
    return { result, scope: 'cancellation only; natural bite and harvest still untested this round' }
  })
}
run().catch(error => { report.error = error.stack; process.exitCode = 1 }).finally(() => {
  if (bot) try { bot.quit() } catch {}
  if (started) try {
    rcon('kill @e[type=item,x=7600,y=99,z=7600,dx=6,dy=5,dz=6]')
    rcon('fill 7600 99 7600 7606 103 7606 air'); rcon('forceload remove 7600 7600 7606 7606')
  } catch (e) { report.cleanupError = e.message; process.exitCode = 1 }
  report.finishedAt = new Date().toISOString()
  const file = path.join(dir, `item-interaction-${Date.now()}.json`)
  fs.writeFileSync(file, JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ passed: report.cases.length, error: report.error || null, file }))
})
