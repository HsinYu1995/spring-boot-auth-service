# Auto-generated Layer 1 integration test runner
# Project type: Maven Spring Boot
# Generated on: 2026-05-20

$ErrorActionPreference = 'Stop'

function Write-ErrorAndExit($message) {
    Write-Host "ERROR: $message"
    exit 1
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-ErrorAndExit 'Docker is not installed or not on PATH. Please install Docker and try again.'
}

try {
    docker info > $null 2>&1
} catch {
    Write-ErrorAndExit 'Docker is not running. Please start Docker and try again.'
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Join-Path $scriptDir '..\..')

$mvnArgs = 'verify', '-DincludeTags=Layer1'
$mvn = 'mvn'
$process = Start-Process -FilePath $mvn -ArgumentList $mvnArgs -NoNewWindow -Wait -PassThru
$exitCode = $process.ExitCode

Write-Host ''
Write-Host '========================================'
if ($exitCode -eq 0) {
    Write-Host '✅ Layer 1 integration tests PASSED'
} else {
    Write-Host '❌ Layer 1 integration tests FAILED'
}
Write-Host '========================================'
exit $exitCode
