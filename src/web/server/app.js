const express = require('express')
const path = require('path')
const { registerRoutes } = require('./routes')

/**
 * Create and configure the Express application.
 * @param {import('../../multiagent/agent_manager').AgentManager} manager
 * @param {Function} loadConfig
 * @returns {import('express').Express}
 */
function createApp(manager, loadConfig) {
  const app = express()

  // JSON body parser
  app.use(express.json())

  // Static files — serve index.html, style.css, app.js from public/
  app.use(express.static(path.join(__dirname, '..', 'public'), {
    maxAge: '1h'
  }))

  // API routes
  registerRoutes(app, manager, loadConfig)

  return app
}

module.exports = { createApp }
