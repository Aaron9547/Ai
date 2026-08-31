# Build and optionally push Docker images
# Usage:
#   .\scripts\docker-build-push-all.ps1
#   .\scripts\docker-build-push-all.ps1 -PushImage
#   .\scripts\docker-build-push-all.ps1 -ImagePrefix my-registry.com/team -PushImage
param(
    [switch]$PushImage,
    [switch]$SkipTests = $true,
    [switch]$SkipBackend,
    [switch]$SkipUserWeb,
    [switch]$SkipAdminWeb,
    [string]$ImagePrefix = '',
    [string]$ImageTag = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (Test-Path 'deploy\compose.env') {
    Get-Content 'deploy\compose.env' | ForEach-Object {
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

docker info *> $null
if ($LASTEXITCODE -ne 0) { Write-Error 'Docker is not available.' }

function Build-Backend {
    $mvnArgs = @('clean', 'package', '-Ddocker.exec.skip=false', "-Ddocker.image.registry=$prefix")
    if ($SkipTests) { $mvnArgs += '-Dmaven.test.skip=true' }
    $mvnArgs += 'exec:exec@docker-build'
    if ($PushImage) { $mvnArgs += 'exec:exec@docker-push' }
    & .\mvnw.cmd @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
}

function Build-UserWeb {
    docker build -f web/user-web/Dockerfile -t "${prefix}/ai-user-web:${tag}" ./web
    if ($LASTEXITCODE -ne 0) { throw 'user-web build failed' }
    if ($PushImage) {
        docker push "${prefix}/ai-user-web:${tag}"
        if ($LASTEXITCODE -ne 0) { throw 'user-web push failed' }
    }
}

function Build-AdminWeb {
    docker build -f web/admin-web/Dockerfile -t "${prefix}/ai-admin-web:${tag}" ./web
    if ($LASTEXITCODE -ne 0) { throw 'admin-web build failed' }
    if ($PushImage) {
        docker push "${prefix}/ai-admin-web:${tag}"
        if ($LASTEXITCODE -ne 0) { throw 'admin-web push failed' }
    }
}

Write-Host "Image prefix: $prefix"
Write-Host "Image tag:    $tag"
if ($PushImage) { Write-Host 'Will push after build (docker login required).' -ForegroundColor Yellow }

if (-not $SkipBackend) { Write-Host '>>> ai-backend' -ForegroundColor Green; Build-Backend }
if (-not $SkipUserWeb) { Write-Host '>>> ai-user-web' -ForegroundColor Green; Build-UserWeb }
if (-not $SkipAdminWeb) { Write-Host '>>> ai-admin-web' -ForegroundColor Green; Build-AdminWeb }

Write-Host 'Done.' -ForegroundColor Green
Write-Host "  ${prefix}/ai-backend:${tag}"
Write-Host "  ${prefix}/ai-user-web:${tag}"
Write-Host "  ${prefix}/ai-admin-web:${tag}"
