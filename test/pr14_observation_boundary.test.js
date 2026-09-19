const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const assert = require('node:assert/strict')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

test('vision/entity is gated by this-session capture and current dimension', () => {
  const source = read('vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionEntityStore.java')
  assert.match(source, /!capturedThisSession/)
  assert.match(source, /!expectedDimension\.equals\(currentDimension\)/)
  assert.match(source, /return entry\.contains\(KEY_VIEW\)/)
})

test('the entity query returns a thin projection instead of restoration payloads', () => {
  const source = read('vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/VisionEntityStore.java')
  const start = source.indexOf('private static CompoundTag buildView')
  const end = source.indexOf('\n    /**', start)
  const method = source.slice(start, end === -1 ? source.length : end)
  assert.ok(start >= 0)
  assert.doesNotMatch(method, /e\.payload\(\)/)
  assert.doesNotMatch(method, /e\.living\(\)/)
  assert.match(method, /e\.content\(\)/)
  assert.match(method, /e\.livingView\(\)/)
})

test('unknown potion effects are not interpreted as an authoritative empty list', () => {
  const writer = read('vendor/stevex-template-1.21.11/src/main/java/name/modid/vision/LivingSummary.java')
  const reader = read('vendor/stevex-test-template-1.21.11/src/main/java/com/example/memworld/EntityRestorer.java')
  assert.match(writer, /if \(EffectSampler\.available\(\)\)/)
  assert.match(writer, /out\.putBoolean\(KEY_EFFECTS_KNOWN, true\)/)
  assert.match(reader, /if \(living\.getBooleanOr\(KEY_EFFECTS_KNOWN, false\) \|\| living\.contains\(KEY_EFFECTS\)\)/)
})
