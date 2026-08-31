# Build images + start backend + frontends (infra must be running)
$ErrorActionPreference = 'Stop'
$deployDir = $PSScriptRoot
$repoRoot = Split-Path -Parent $deployDir

Write-Host '=== Build images ===' -ForegroundColor Cyan
& (Join-Path $repoRoot 'scripts\docker-build-images.ps1')

Write-Host '=== Start backend + frontends ===' -ForegroundColor Cyan
Set-Location $deployDir
docker compose up -d

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host '  User:  http://<host>:5173/default/chat'
Write-Host '  Admin: http://<host>:5174/'
docker compose ps
