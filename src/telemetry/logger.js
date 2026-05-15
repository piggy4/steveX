function logInfo(message, meta) {
  if (meta) {
    console.log(`[info] ${message}`, meta)
    return
  }
  console.log(`[info] ${message}`)
}

function logError(message, error) {
  if (error) {
    console.error(`[error] ${message}`, error)
    return
  }
  console.error(`[error] ${message}`)
}

module.exports = {
  logInfo,
  logError
}
