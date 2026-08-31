# Push images built with IMAGE_PREFIX / IMAGE_TAG (from deploy/.env or params)
param(
    [string]$ImagePrefix = '',
    [string]$ImageTag = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (Test-Path 'deploy\.env') {
    Get-Content 'deploy\.env' | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            $k = $Matches[1].Trim()
            $v = $Matches[2].Trim()
            Set-Item -Path "Env:$k" -Value $v
        }
    }
}

if ($ImagePrefix) { $env:IMAGE_PREFIX = $ImagePrefix }
if ($ImageTag) { $env:IMAGE_TAG = $ImageTag }

$prefix = if ($env:IMAGE_PREFIX) { $env:IMAGE_PREFIX } else { 'ai-platform' }
$tag = if ($env:IMAGE_TAG) { $env:IMAGE_TAG } else { '0.1.258-SNAPSHOT' }

foreach ($name in @('ai-backend', 'ai-user-web', 'ai-admin-web')) {
    $ref = "${prefix}/${name}:${tag}"
    Write-Host "Pushing $ref ..." -ForegroundColor Cyan
    docker push $ref
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Push failed for $ref. Ensure docker login and repository exists."
    }
}
Write-Host 'All images pushed.' -ForegroundColor Green
