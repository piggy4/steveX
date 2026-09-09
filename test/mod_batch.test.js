const test = require('node:test')
const assert = require('node:assert/strict')
const { setImmediate: nextTurn } = require('node:timers/promises')
const { startBatch, getBatch, stopBatch, serializeRun } = require('../src/web/server/mod_batch')

function managerFor(call) {
  return { getModClient: () => ({ isConnected: () => true, call }) }
}

async function finished(id) {
  for (let i = 0; i < 100; i++) {
    if (getBatch(id).finishedAt !== null) return serializeRun(getBatch(id))
    await nextTurn()
  }
  assert.fail('batch did not settle')
}

test('cleanup holds the execution slot and hides terminal status until release settles', async () => {
  let release
  const manager = managerFor(async (method, params) => params.pressed === false
    ? new Promise(resolve => { release = resolve })
    : { ok: true })
  const first = startBatch(manager, { steps: [{ method: 'key/up', params: { pressed: true } }] })
  try {
    await nextTurn()
    const during = serializeRun(getBatch(first.batchId))
    assert.equal(during.status, 'running')
    assert.equal(during.finishedAt, null)
    const second = startBatch(manager, { steps: [{ method: 'player' }] })
    assert.equal(second.http, 409)
    assert.equal(second.runningId, first.batchId)
  } finally {
    release({ ok: true })
    await finished(first.batchId)
  }
  assert.deepEqual(getBatch(first.batchId).releasedKeys, ['key/up'])
  const next = startBatch(manager, { steps: [{ method: 'player' }] })
  assert.equal(next.ok, true)
  assert.equal((await finished(next.batchId)).status, 'done')
})

test('a failed explicit release is retried during cleanup', async () => {
  let releases = 0
  const manager = managerFor(async (method, params) => {
    if (params.pressed === false && ++releases === 1) return { ok: false, error: 'timeout' }
    return { ok: true }
  })
  const { batchId } = startBatch(manager, { steps: [
    { method: 'key/up', params: { pressed: true } },
    { method: 'key/up', params: { pressed: false } }
  ] })
  const result = await finished(batchId)
  assert.equal(releases, 2)
  assert.deepEqual(result.releasedKeys, ['key/up'])
  assert.deepEqual(result.releaseErrors, [])
})

test('successful explicit release is not repeated', async () => {
  let releases = 0
  const manager = managerFor(async (method, params) => {
    if (params.pressed === false) releases++
    return { ok: true }
  })
  const { batchId } = startBatch(manager, { steps: [
    { method: 'key/up', params: { pressed: true } },
    { method: 'key/up', params: { pressed: false } }
  ] })
  assert.equal((await finished(batchId)).status, 'done')
  assert.equal(releases, 1)
})

test('stop cancels a pending wait, skips subsequent actions and releases held keys', async () => {
  const calls = []
  const manager = managerFor(async (method, params) => {
    calls.push([method, params])
    return { ok: true }
  })
  const { batchId } = startBatch(manager, { steps: [
    { method: 'key/up', params: { pressed: true } },
    { waitMs: 60000 },
    { method: 'player' }
  ] })
  await nextTurn()
  stopBatch(batchId)
  const result = await finished(batchId)
  assert.equal(result.status, 'stopped')
  assert.equal(result.steps[1].state, 'skipped')
  assert.equal(result.steps[2].state, 'skipped')
  assert.deepEqual(calls, [['key/up', { pressed: true }], ['key/up', { pressed: false }]])
})

for (const throws of [false, true]) {
  test('cleanup reports release failure, including thrown errors: ' + throws, async () => {
    const manager = managerFor(async (method, params) => {
      if (params.pressed === false) {
        if (throws) throw new Error('release unavailable')
        return { ok: false, error: 'release unavailable' }
      }
      return { ok: true }
    })
    const { batchId } = startBatch(manager, {
      steps: [{ method: 'key/up', params: { pressed: true } }]
    })
    const result = await finished(batchId)
    assert.equal(result.status, 'failed')
    assert.deepEqual(result.releaseErrors, [{ method: 'key/up', error: 'release unavailable' }])
    assert.deepEqual(result.releasedKeys, [])
  })
}
