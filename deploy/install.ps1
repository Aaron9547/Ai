# Full deploy: infra + build images + app stack
$ErrorActionPreference = 'Stop'
$deployDir = $PSScriptRoot
$repoRoot = Split-Path -Parent $deployDir

Write-Host '=== Infrastructure (deploy/infra) ===' -ForegroundColor Cyan
Set-Location (Join-Path $deployDir 'infra')
docker compose up -d

Write-Host '=== Build application images ===' -ForegroundColor Cyan
& (Join-Path $repoRoot 'scripts\docker-build-images.ps1')

Write-Host '=== Application stack (deploy) ===' -ForegroundColor Cyan
Set-Location $deployDir
docker compose up -d

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host '  User:  http://<host>:5173/default/chat'
Write-Host '  Admin: http://<host>:5174/'
docker compose ps
