# Pull app images and start (infra must be running)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
docker compose pull
docker compose up -d
docker compose ps
