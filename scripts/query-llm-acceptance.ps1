param([switch]$Configure, [ValidateSet('query', 'craft', 'craft-stress', 'bugs')][string]$Suite = 'query', [string]$Case, [int]$MaxModelCalls = 0, [switch]$Standalone)
$ErrorActionPreference = 'Stop'
# A desktop launched from PowerShell 7 can pass its module paths to Windows
# PowerShell 5.1. Load this host's Security module before asking for the key.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Security/Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
$root = Split-Path -Parent $PSScriptRoot
$secretPath = Join-Path $root 'runs\secrets\deepseek-key.dpapi'
if ($Configure) {
    $secret = Read-Host 'DeepSeek API key (hidden)' -AsSecureString
    try {
        if ($secret.Length -eq 0) { throw 'Empty API key' }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $secretPath) | Out-Null
        $secret | ConvertFrom-SecureString | Set-Content -LiteralPath $secretPath -Encoding ASCII
    } finally { $secret.Dispose() }
    Write-Host 'Key saved with Windows DPAPI. No model calls made.'
    exit 0
}
if (!(Test-Path -LiteralPath $secretPath)) { throw 'Run this script with -Configure first.' }
$previousKey = $env:DEEPSEEK_API_KEY
$ptr = [IntPtr]::Zero
$secret = $null
try {
    $secret = (Get-Content -LiteralPath $secretPath -Raw).Trim() | ConvertTo-SecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:DEEPSEEK_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $scriptName = if ($Suite -eq 'bugs') { 'api-bug-acceptance.js' } elseif ($Suite -eq 'query') { 'query-llm-acceptance.js' } else { 'craft-llm-acceptance.js' }
    $runArgs = @('--live')
    if ($Standalone) {
        if ($Suite -in @('query', 'bugs')) { throw '-Standalone is only supported with crafting suites' }
        $runArgs += '--standalone'
    }
    if ($Suite -eq 'craft-stress') { $runArgs += '--stress' }
    if ($MaxModelCalls -lt 0) { throw 'MaxModelCalls cannot be negative' }
    if ($MaxModelCalls -gt 0) {
        if ($Suite -in @('query', 'bugs')) { throw '-MaxModelCalls is only supported with crafting suites' }
        $runArgs += @('--max-model-calls', [string]$MaxModelCalls)
    }
    if ($Case) {
        if ($Suite -in @('query', 'bugs')) { throw '-Case is only supported with crafting suites' }
        $runArgs += @('--case', $Case)
    }
    & node (Join-Path $PSScriptRoot $scriptName) @runArgs
    if ($LASTEXITCODE -ne 0) { throw 'Acceptance run failed; inspect the recorded log.' }
} finally {
    $env:DEEPSEEK_API_KEY = $previousKey
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    if ($null -ne $secret) { $secret.Dispose() }
}
