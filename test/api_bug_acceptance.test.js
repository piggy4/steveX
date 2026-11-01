const test = require('node:test')
const assert = require('node:assert/strict')
const { cases, localBase } = require('../scripts/api-bug-acceptance')
test('bug acceptance selects only the three intended read-only queries and validates local origin', () => {
  assert.deepEqual(cases('Alpha').map(c => c.command), ['hp', 'inventory slots', 'findplayer Alpha'])
  for (const name of ['x\nsay hello', 'x y', '', 'x'.repeat(17)]) assert.throws(() => cases(name))
  assert.equal(localBase('http://localhost:8090/'), 'http://localhost:8090')
  for (const url of ['https://localhost', 'http://example.com', 'http://localhost@evil.example', 'http://user:pass@localhost', 'http://localhost/api', 'http://localhost/?key=x']) assert.throws(() => localBase(url))
})

test('live runner records three local queries and three provider explanations without leaking the key', async t => {
  const http = require('node:http'), fs = require('node:fs'), path = require('node:path')
  const { once } = require('node:events'), { execFile } = require('node:child_process')
  const calls = [], modelCalls = [], secret = 'fixture-api-secret'
  const server = http.createServer(async (req, res) => {
    let text = ''; for await (const part of req) text += part
    const body = JSON.parse(text)
    res.setHeader('Content-Type', 'application/json')
    if (req.url === '/provider') {
      assert.equal(req.headers.authorization, `Bearer ${secret}`)
      modelCalls.push(body)
      res.end(JSON.stringify({ choices: [{ message: { content: 'Recorded fixture explanation' } }] }))
    } else {
      assert.equal(req.url, '/api/agents/Test%20Agent/command')
      calls.push(body.command)
      res.end(JSON.stringify({ ok: true, output: 'fixture', health: 20, maxHealth: 28 }))
    }
  }).listen(0, '127.0.0.1')
  await once(server, 'listening')
  t.after(() => { server.closeAllConnections(); server.close() })
  const base = `http://127.0.0.1:${server.address().port}`
  const { stdout, stderr } = await require('node:util').promisify(execFile)(process.execPath,
    [require.resolve('../scripts/api-bug-acceptance'), '--live'], { timeout: 10000,
      env: { ...process.env, DEEPSEEK_API_KEY: secret, DEEPSEEK_BASE_URL: base + '/provider',
        STEVEX_ACCEPTANCE_URL: base, STEVEX_ACCEPTANCE_AGENT: 'Test Agent', STEVEX_ACCEPTANCE_PLAYER: 'Alpha' } })
  assert.deepEqual(calls, ['hp', 'inventory slots', 'findplayer Alpha'])
  assert.equal(modelCalls.length, 3)
  assert.doesNotMatch(stdout + stderr, /fixture-api-secret/)
  const reportPath = stdout.match(/Report: (.+)/)[1].trim()
  const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'))
  assert.equal(report.results.length, 3)
  assert.equal(report.status, 'manual-review-required')
  assert.ok(report.results.every(r => r.interpretation && r.review === null))
  assert.equal(JSON.stringify(report).includes(secret), false)
  assert.equal(path.dirname(reportPath), path.resolve(__dirname, '../runs/api-bug-acceptance'))
})
