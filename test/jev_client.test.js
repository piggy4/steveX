const assert = require('node:assert/strict')
const test = require('node:test')
const { JevClient, TYPESAFE_URL, resolveJevOptions } = require('../src/decision/jev_client')

test('builds the native TypeSafe request shape', async () => {
  let request
  const client = new JevClient({ apiKey: 'test-key' }, async (url, options) => {
    request = { url, options }
    return new Response(JSON.stringify({ model: 'jev-1.13.0', answers: { safe: { type: 'noul', noul: 0.9 } } }))
  })

  const result = await client.evaluate('state', { safe: { type: 'noul', instructions: 'Is it safe?' } })
  assert.equal(request.url, TYPESAFE_URL)
  assert.equal(request.options.headers.Authorization, 'Bearer test-key')
  assert.deepEqual(JSON.parse(request.options.body), {
    model: 'jev-1.13.0',
    state: 'state',
    questions: { safe: { type: 'noul', instructions: 'Is it safe?' } }
  })
  assert.equal(result.answers.safe.noul, 0.9)
})

test('unwraps the Cloudflare Workers AI response', async () => {
  let body
  const client = new JevClient({
    provider: 'cloudflare',
    accountId: 'account-id',
    apiKey: 'test-token'
  }, async (_url, options) => {
    body = JSON.parse(options.body)
    return new Response(JSON.stringify({
      success: true,
      result: { model: 'jev-1.13.0', answers: { route: { type: 'choice', choice: 'retreat' } } }
    }))
  })

  const result = await client.evaluate({}, { route: { type: 'choice', criteria: { retreat: 'Retreat' } } })
  assert.equal(body.model, 'typesafe/jev')
  assert.ok(body.input.questions.route)
  assert.equal(result.answers.route.choice, 'retreat')
})

test('environment variables override non-secret config', () => {
  const previous = process.env.JEV_MODEL
  process.env.JEV_MODEL = 'jev-test'
  try {
    assert.equal(resolveJevOptions({ model: 'jev-config' }).model, 'jev-test')
  } finally {
    if (previous === undefined) delete process.env.JEV_MODEL
    else process.env.JEV_MODEL = previous
  }
})

test('rejects calls without credentials before network access', async () => {
  const client = new JevClient({ apiKey: '' }, async () => assert.fail('fetch must not run'))
  await assert.rejects(client.evaluate({}, { decision: { type: 'noul' } }), /missing API key/)
})
