# Pull pre-built images and start (skip local build)
param([switch]$SkipPull)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env — edit cluster endpoints, then run again.' -ForegroundColor Yellow
    exit 1
}

if (-not $SkipPull) {
    docker compose pull
}
docker compose up -d

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host '  User:  http://<host>:5173/default/chat'
Write-Host '  Admin: http://<host>:5174/'
docker compose ps
