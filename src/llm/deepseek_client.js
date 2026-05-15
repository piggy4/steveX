const DEFAULT_BASE_URL = 'https://api.deepseek.com/v1/chat/completions'

class DeepSeekClient {
  constructor(options) {
    this.apiKey = options.apiKey || ''
    this.model = options.model || 'deepseek-v4-flash'
    this.baseUrl = options.baseUrl || DEFAULT_BASE_URL
    this.timeoutMs = options.timeoutMs || 20000
  }

  async chat(messages) {
    if (!this.apiKey) {
      return 'LLM disabled: missing DEEPSEEK_API_KEY'
    }

    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs)

    try {
      const response = await fetch(this.baseUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.apiKey}`
        },
        body: JSON.stringify({
          model: this.model,
          messages
        }),
        signal: controller.signal
      })

      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`DeepSeek error ${response.status}: ${errorText}`)
      }

      const data = await response.json()
      const choice = data.choices && data.choices[0]
      const content = choice && choice.message && choice.message.content
      return content || ''
    } finally {
      clearTimeout(timeout)
    }
  }
}

module.exports = {
  DeepSeekClient
}
