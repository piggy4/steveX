const fs = require('fs/promises')
const path = require('path')
const { METHODS } = require('../../mod/methods')

// 与 src/utils/config.js 一致：以仓库根为基准（__dirname 锚定），不依赖启动 cwd。
// 否则从非仓库根目录启动时，面板读写会指向错误路径，而启动加载仍读正确路径。
const REPO_ROOT = path.resolve(__dirname, '..', '..', '..')
const ENV_CONFIG_RELATIVE_PATH = path.join('configs', 'environments', 'app.json')
const ENV_CONFIG_PATH = path.join(REPO_ROOT, ENV_CONFIG_RELATIVE_PATH)

/**
 * Register all API routes on the Express app.
 */
function registerRoutes(app, manager) {

  // ---- Status ----
  app.get('/api/status', (req, res) => {
    res.json({
      agents: manager.getStatus(),
      uptimeSec: Math.floor(process.uptime())
    })
  })

  // ---- Read environment config ----
  app.get('/api/config/environment', async (req, res) => {
    try {
      let config

      try {
        const raw = await fs.readFile(ENV_CONFIG_PATH, 'utf8')
        config = JSON.parse(raw)
      } catch (err) {
        // env 文件是 gitignored 的可选覆盖层（含密钥）：首次克隆时不存在。
        // 与 config.js 启动加载语义一致 —— 视作"尚无覆盖"返回空 {}，由保存时创建。
        if (err.code === 'ENOENT') {
          config = {}
        } else {
          throw err
        }
      }

      res.json({
        ok: true,
        path: ENV_CONFIG_RELATIVE_PATH,
        config
      })
    } catch (err) {
      console.error('[web] Failed to read environment config:', err)

      res.status(500).json({
        ok: false,
        error: err.message || 'Failed to read environment config'
      })
    }
  })

  // ---- Save environment config ----
  app.post('/api/config/environment', async (req, res) => {
    try {
      const config = req.body && req.body.config

      if (!config || typeof config !== 'object' || Array.isArray(config)) {
        return res.status(400).json({
          ok: false,
          error: 'Request body must contain a config object'
        })
      }

      const nextRaw = `${JSON.stringify(config, null, 2)}\n`

      // Guard against oversized config blobs
      const MAX_SIZE = 512 * 1024 // 512 KB
      if (Buffer.byteLength(nextRaw, 'utf8') > MAX_SIZE) {
        return res.status(413).json({
          ok: false,
          error: `Config too large (max ${MAX_SIZE / 1024} KB)`
        })
      }

      // 首次保存时 configs/environments/ 目录可能不存在（gitignored，全新克隆无此目录）
      await fs.mkdir(path.dirname(ENV_CONFIG_PATH), { recursive: true })
      await fs.writeFile(ENV_CONFIG_PATH, nextRaw, 'utf8')

      res.json({
        ok: true,
        path: ENV_CONFIG_RELATIVE_PATH
      })
    } catch (err) {
      console.error('[web] Failed to save environment config:', err)

      res.status(500).json({
        ok: false,
        error: err.message || 'Failed to save environment config'
      })
    }
  })

  // ---- Reload config ----
  app.post('/api/reload', (req, res) => {
    manager.reload()
    res.json({ ok: true })
  })

  // ---- Connect agent ----
  app.post('/api/agents/:name/connect', (req, res) => {
    const name = decodeURIComponent(req.params.name)
    const ok = manager.connectAgent(name)
    res.status(ok ? 200 : 404).json({ ok })
  })

  // ---- Disconnect agent ----
  app.post('/api/agents/:name/disconnect', (req, res) => {
    const name = decodeURIComponent(req.params.name)
    const ok = manager.disconnectAgent(name)
    res.status(ok ? 200 : 404).json({ ok })
  })

  // ---- Skills: 语义改为 mod 48 方法清单（原 77 命令执行层已退役）----
  // 前端可复用该接口渲染"可调用方法"下拉，等价于 GET /api/mod/methods。
  // args 由 paramDefs 派生为 name→type 紧凑对象（形状与旧 m.params 一致）。
  app.get('/api/skills', (req, res) => {
    const skills = METHODS.map(m => ({
      name: m.method,
      description: m.description,
      args: Object.fromEntries(m.paramDefs.map(p => [p.name, p.type])),
      zh: m.zh
    }))

    res.json({ ok: true, skills })
  });

  // ---- 404 catch-all ----
  app.use((req, res) => {
    res.status(404).json({
      ok: false,
      error: 'Not found'
    })
  })
}

module.exports = { registerRoutes }