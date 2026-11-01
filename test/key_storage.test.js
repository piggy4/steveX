const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path')
const { execFileSync } = require('node:child_process')

test('Windows key setup uses the host Security module despite inherited PowerShell 7 module paths', { skip: process.platform !== 'win32' }, () => {
  const runs = path.resolve(__dirname, '../runs')
  fs.mkdirSync(runs, { recursive: true })
  const temp = fs.mkdtempSync(path.join(runs, 'key-storage-test-'))
  try {
    fs.mkdirSync(path.join(temp, 'scripts'))
    fs.copyFileSync(path.resolve(__dirname, '../scripts/query-llm-acceptance.ps1'), path.join(temp, 'scripts/setup.ps1'))
    // Only a fixed non-secret fixture is supplied. The user's key is never read.
    fs.writeFileSync(path.join(temp, 'configure.ps1'), `
function Read-Host {
  param($Prompt, [switch]$AsSecureString)
  $fixture = New-Object System.Security.SecureString
  foreach ($c in 'fixture-not-a-real-key'.ToCharArray()) { $fixture.AppendChar($c) }
  return $fixture
}
& (Join-Path $PSScriptRoot 'scripts/setup.ps1') -Configure
`)
    const exe = path.join(process.env.SystemRoot, 'System32/WindowsPowerShell/v1.0/powershell.exe')
    const options = { windowsHide: true, encoding: 'utf8', timeout: 15000 }
    const output = execFileSync(exe, ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', path.join(temp, 'configure.ps1')], options)
    assert.match(output, /Key saved with Windows DPAPI/)
    const stored = fs.readFileSync(path.join(temp, 'runs/secrets/deepseek-key.dpapi'), 'utf8')
    assert.equal(stored.includes('fixture-not-a-real-key'), false)
    fs.writeFileSync(path.join(temp, 'verify.ps1'), `
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Security/Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
$secret = (Get-Content (Join-Path $PSScriptRoot 'runs/secrets/deepseek-key.dpapi') -Raw).Trim() | ConvertTo-SecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
  if ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) -ne 'fixture-not-a-real-key') { throw 'Round trip failed' }
  Write-Output 'ROUNDTRIP_OK'
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
  $secret.Dispose()
}
`)
    assert.match(execFileSync(exe, ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', path.join(temp, 'verify.ps1')], options), /ROUNDTRIP_OK/)
  } finally { fs.rmSync(temp, { recursive: true, force: true }) }
})
