const { JevClient } = require('../src/decision/jev_client')

async function main() {
  const client = new JevClient()
  const result = await client.evaluate(
    {
      health: 6,
      hostileDistance: 4,
      hasShield: true
    },
    {
      action: {
        type: 'choice',
        instructions: 'Choose the safest immediate Minecraft action.',
        criteria: {
          retreat: 'Create distance from the hostile entity',
          defend: 'Raise the shield and hold position',
          continue: 'Continue the current task'
        }
      }
    }
  )

  console.log(JSON.stringify(result, null, 2))
}

main().catch(error => {
  console.error(error.message)
  process.exitCode = 1
})
