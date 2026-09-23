const TYPESAFE_URL = 'https://api.typesafe.ai/v1/systemone'
const CLOUDFLARE_MODEL = 'typesafe/jev'
const OPENROUTER_URL = 'https://openrouter.ai/api/alpha/decisions'
const OPENROUTER_MODEL = 'typesafe/jev-1.13'

function requireObject(value, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError(`${name} must be an object`)
  }
}

function envValue(name, fallback = '') {
  return process.env[name] || fallback
}

function resolveJevOptions(config = {}) {
  const provider = envValue('JEV_PROVIDER', config.provider || 'typesafe').toLowerCase()
  const accountId = envValue('CLOUDFLARE_ACCOUNT_ID', config.accountId)
  const defaultUrl = provider === 'cloudflare'
    ? (accountId ? `https://api.cloudflare.com/client/v4/accounts/${accountId}/ai/run` : TYPESAFE_URL)
    : (provider === 'openrouter' ? OPENROUTER_URL : TYPESAFE_URL)
  const defaultModel = provider === 'cloudflare'
    ? CLOUDFLARE_MODEL
    : (provider === 'openrouter' ? OPENROUTER_MODEL : 'jev-1.13.0')
  const keyName = provider === 'cloudflare'
    ? 'CLOUDFLARE_API_TOKEN'
    : (provider === 'openrouter' ? 'OPENROUTER_API_KEY' : 'JEV_API_KEY')

  return {
    provider,
    apiKey: envValue(keyName, config.apiKey),
    baseUrl: envValue('JEV_BASE_URL', config.baseUrl || defaultUrl),
    model: envValue('JEV_MODEL', config.model || defaultModel),
    timeoutMs: Number(envValue('JEV_TIMEOUT_MS', config.timeoutMs || 10000))
  }
}

class JevClient {
  constructor(options = {}, fetchImpl = globalThis.fetch) {
    const resolved = resolveJevOptions(options)
    if (!['typesafe', 'cloudflare', 'openrouter'].includes(resolved.provider)) {
      throw new Error(`Unsupported Jev provider: ${resolved.provider}`)
    }
    if (typeof fetchImpl !== 'function') throw new TypeError('fetch implementation is required')

    Object.assign(this, resolved)
    this.fetch = fetchImpl
  }

  async evaluate(state, questions) {
    if (!this.apiKey) throw new Error('Jev disabled: missing API key')
    requireObject(questions, 'questions')
    if (Object.keys(questions).length === 0) throw new Error('questions must not be empty')

    const input = { state, questions }
    const body = this.provider === 'cloudflare'
      ? { model: this.model, input }
      : { model: this.model, ...input }
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs)

    try {
      const response = await this.fetch(this.baseUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.apiKey}`
        },
        body: JSON.stringify(body),
        signal: controller.signal
      })
      const responseText = await response.text()
      let payload
      try {
        payload = JSON.parse(responseText)
      } catch {
        payload = { error: responseText }
      }
      if (!response.ok) {
        const detail = payload.errors || payload.error || payload.message || response.statusText
        throw new Error(`Jev error ${response.status}: ${JSON.stringify(detail)}`)
      }

      const result = this.provider === 'cloudflare' ? payload.result : payload
      if (!result || typeof result !== 'object' || !result.answers) {
        throw new Error('Jev returned an invalid response: missing answers')
      }
      return result
    } finally {
      clearTimeout(timeout)
    }
  }
}

module.exports = {
  CLOUDFLARE_MODEL,
  OPENROUTER_MODEL,
  OPENROUTER_URL,
  TYPESAFE_URL,
  JevClient,
  resolveJevOptions
}
