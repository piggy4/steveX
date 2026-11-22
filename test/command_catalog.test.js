const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { loadCommands } = require('../src/commands')
const { categories, categoryFor, groupCommands, formatCommandCatalog } = require('../src/commands/catalog')
const { Brain } = require('../src/agent/brain')
const { generate } = require('../scripts/generate-command-catalog')

test('every command has exactly one category, with no stale category entries', () => {
  const { list } = loadCommands()
  const names = categories.flatMap(c => c.names.split(' '))
  assert.equal(new Set(names).size, names.length)
  assert.deepEqual([...names].sort(), list.map(c => c.name).sort())
  assert.equal(groupCommands(list).flatMap(c => c.commands).length, 60)
  assert.equal(groupCommands(list)[0].commands.length, 15)
  assert.throws(() => categoryFor('unregistered'), /Missing/)
})
test('Brain groups only allowed commands and never accepts category headings as commands', () => {
  const agent = { commands: { pos() {}, goto() {} } }
  const brain = new Brain(agent, {}, {}, { allowedCommands: ['pos'] })
  brain.buildSystem()
  assert.match(brain.table, /State and environment/)
  assert.doesNotMatch(brain.table, /Motion and control/)
  assert.equal(brain.pickCommand('pos'), 'pos')
  for (const value of ['Read-only;', '[状态与环境查询', 'goto 0 0 0']) assert.equal(brain.pickCommand(value), null)
  const normal = new Brain({}, {}, {})
  assert.equal(normal.pickCommand('setinventory 1 stone 1'), null)
})
test('generated command documentation stays synchronized', () => {
  assert.equal(fs.readFileSync(path.join(__dirname, '../docs/command-catalog.md'), 'utf8').replace(/\r\n/g, '\n'), generate())
})

test('all generated catalog text is English, including privileged and window commands', () => {
  const text = formatCommandCatalog(loadCommands().list)
  assert.doesNotMatch(text, /[\u3400-\u9fff]/)
  for (const category of categories) assert.ok(text.includes(`[${category.title}]`))
  assert.match(text, /\[State and environment\]/)
  assert.match(text, /\[Workstations and trading\]/)
  assert.match(text, /\[Client management\]/)
  assert.doesNotMatch(generate(), /[\u3400-\u9fff]/)
})

test('move stop replaces stand without duration or immediate global cancellation', async () => {
  const { commands, list } = loadCommands()
  assert.equal(commands.stand, undefined)
  assert.equal(new Brain({}, {}, {}).pickCommand('stand'), null)
  assert.equal(list.length, 60)
  let clears = 0
  const bot = { clearControlStates() { clears++ } }
  assert.equal((await commands.move(bot, ['stop'])).ok, true)
  assert.equal(clears, 1)
  assert.equal((await commands.move(bot, ['stop', '100'])).errorCode, 'INVALID_ARGUMENTS')
  assert.equal(clears, 1)
})

test('semantic categories match the same groups in command metadata and Brain prompt', () => {
  const expected = {
    motion: ['look', 'move', 'sneak', 'jump', 'stop', 'goto', 'follow', 'wait', 'collect'],
    interaction: ['dig', 'right', 'attack', 'interactentity', 'swing', 'fish'],
    inventory: ['equip', 'hotbar', 'unequip', 'drop', 'craft'],
    survival: ['wake', 'respawn']
  }
  const { list } = loadCommands()
  const groups = groupCommands(list)
  const brain = new Brain({bot:{health:0,isSleeping:true}}, {}, {})
  assert.equal(groups.some(group => group.id === 'gather'), false)
  assert.doesNotMatch(brain.table, /Gathering and building|Inventory and items|Survival and entities/)
  for (const [id, names] of Object.entries(expected)) {
    const group = groups.find(group => group.id === id)
    assert.deepEqual(group.commands.map(command => command.name), names)
    const promptSection = brain.table.split(`[${group.title}]\n`)[1]?.split('\n\n')[0]
    assert.ok(promptSection, id)
    for (const name of names) {
      assert.equal(categoryFor(name), id)
      const command = list.find(command => command.name === name)
      assert.equal(command.category, id)
      assert.ok(promptSection.includes(`${command.usage} -- ${command.description}`), name)
    }
  }
  const interaction = groups.find(group => group.id === 'interaction')
  assert.match(interaction.note, /swing is animation only, never interaction/)
})

test('default Brain hides all management and privileged entries without removing manual APIs', () => {
  const { list, commands } = loadCommands()
  const hidden = ['settings', 'quit', 'acceptpack', 'denypack', ...list.filter(c => c.category === 'creative' || c.kind === 'creative').map(c => c.name)]
  const brain = new Brain({}, {}, {})
  for (const name of hidden) {
    assert.equal(typeof commands[name], 'function', `${name} must remain manually callable`)
    assert.equal(list.find(c => c.name === name).llmDefaultVisible, false, name)
    assert.equal(brain.commandNames.has(name), false, name)
    assert.equal(brain.pickCommand(name), null, name)
    assert.ok(!brain.table.split('\n').some(line => line.startsWith(name + ' ') || line.startsWith(name + ' --')), name)
  }
  for (const name of ['say', 'stop', 'inventory', 'right']) {
    assert.equal(list.find(c => c.name === name).llmDefaultVisible, true, name)
    assert.equal(brain.commandNames.has(name), true, name)
  }
  assert.equal(brain.commandNames.size, require("../src/commands/window_catalog").windowCommands(list.filter(c=>c.llmDefaultVisible),null).length)
  assert.doesNotMatch(brain.table, /Creative and privileged tools/)
  for (const command of list) assert.equal(typeof command.llmDefaultVisible, 'boolean')
})

test('acceptance checklist sections follow the shared catalog without duplicate command rows', () => {
  const doc = fs.readFileSync(path.join(__dirname, '../docs/api-acceptance-checklist.md'), 'utf8')
  const groups = groupCommands(loadCommands().list)
  const allNames = []
  for (const [index, group] of groups.entries()) {
    const kind = group.id === 'query' ? 'query' : group.id === 'creative' ? '入口' : 'action'
    // The retained Chinese acceptance checklist is independent of English catalog titles.
    const heading = doc.split('\n').map(line => line.trim()).find(line => line.startsWith(`## ${index + 3}. `))
    assert.ok(heading)
    assert.ok(heading.endsWith(`（${group.commands.length} 个${kind === '入口' ? '' : ' '}${kind}）`))
    const section = doc.split(heading)[1]?.split('\n## ')[0]
    assert.ok(section, heading)
    const names = [...section.matchAll(/^\| `([a-z]+)(?:[ `])/gm)].map(match => match[1])
    assert.deepEqual(names, group.commands.map(command => command.name))
    allNames.push(...names)
  }
  assert.equal(new Set(allNames).size, loadCommands().list.length)
})

test('explicit Brain allowlist replaces defaults but cannot expose missing agent commands', () => {
  const brain = new Brain({}, {}, {}, { allowedCommands: ['settings', 'setcommandblock', 'setinventory', 'unregistered'] })
  assert.deepEqual([...brain.commandNames].sort(), ['setcommandblock', 'setinventory', 'settings'])
  assert.equal(brain.pickCommand('settings viewDistance 8'), 'settings viewDistance 8')
  assert.equal(brain.pickCommand('setcommandblock 1 2 3 say test'), 'setcommandblock 1 2 3 say test')
  assert.equal(brain.pickCommand('setinventory 36 stone 1'), 'setinventory 36 stone 1')
  assert.equal(brain.pickCommand('pos'), null)
  const limited = new Brain({ commands: { pos() {} } }, {}, {}, { allowedCommands: ['pos', 'settings', 'setinventory'] })
  assert.deepEqual([...limited.commandNames], ['pos'])
  const empty = new Brain({}, {}, {}, { allowedCommands: [] })
  assert.equal(empty.table, '')
  assert.equal(empty.pickCommand('pos'), null)
})

test('shared descriptions distinguish targeting, window movement and safety stop semantics', () => {
  const byName = Object.fromEntries(loadCommands().list.map(c => [c.name, c]))
  assert.match(byName.stop.description, /Immediate global.*cancel active\/queued/)
  assert.match(byName.move.description, /Queued.*preserves.*held-item/)
  assert.match(byName.transfer.description, /deposit.*inventory:.*withdraw.*storage reference/i)
  assert.match(byName.transfer.description,/hotbar:/)
  assert.match(byName.putaway.description, /destination chosen automatically/)
  assert.match(byName.moveslot.description, /specified destination slot/)
  assert.match(byName.setcommandblock.description, /existing command block.*does not place/)
  const brain = new Brain({}, {}, {})
  assert.doesNotMatch(brain.buildSystem(), /Never repeat a command/)
  assert.match(brain.buildSystem(), /retry only after correcting arguments or checking changed conditions/)
  for (const name of ['slotclick', 'moveslot', 'putaway']) {
    assert.ok(brain.table.includes(byName[name].description), name)
  }
})
