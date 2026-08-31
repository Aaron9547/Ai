# Server one-shot: build images + docker compose up (Windows)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created deploy/.env — edit cluster endpoints, then run again.' -ForegroundColor Yellow
    exit 1
}

Write-Host '=== Build images (local) ===' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot '..\scripts\docker-build-images.ps1')

Write-Host '=== Start stack ===' -ForegroundColor Cyan
docker compose up -d

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host '  User:  http://<host>:5173/default/chat'
Write-Host '  Admin: http://<host>:5174/'
docker compose ps
