const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

function moduleContext() {
  const source = fs.readFileSync(path.join(__dirname, '../src/web/public/lib/inventory-preview.js'), 'utf8')
    .replace(/^import .*$/gm, '').replace(/^export /gm, '')
  const context = vm.createContext({ escapeHtml: value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;') })
  vm.runInContext(source, context)
  return context
}

test('inventory tooltips explicitly list direct and stored enchantments, levels and durability', () => {
  const context = moduleContext()
  const text = context.itemTooltipText({ slot: 10, item: { name: 'diamond_sword', count: 1,
    enchantments: [{ name: 'minecraft:sharpness', level: 3 }], storedEnchantments: [{ name: 'mending', level: 1 }],
    durability: { used: 37, remaining: 1524, max: 1561 } } })
  assert.match(text, /Enchantments:\n  sharpness 3/)
  assert.match(text, /Stored enchantments:\n  mending 1/)
  assert.match(text, /Durability: 1524 \/ 1561/)
  assert.equal(context.itemTooltipText({ ref: 'inventory:1', item: null }), 'Slot inventory:1: Empty')
})

test('tooltip data is escaped text and does not depend on native title timing', () => {
  const context = moduleContext()
  context.entry = { slot: 9, item: { name: 'apple', displayName: '<img onerror="alert(1)">', count: 1 } }
  const html = vm.runInContext('slotHtml(entry, "1.21.11")', context)
  assert.match(html, /data-tooltip="&lt;img/)
  assert.doesNotMatch(html, / title=/)
  assert.doesNotMatch(html, /<img onerror/)
})
