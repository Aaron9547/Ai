# Build local Docker images (Windows). Names: ${IMAGE_PREFIX}/ai-*:${IMAGE_TAG}
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

docker info *> $null
if ($LASTEXITCODE -ne 0) { Write-Error 'Docker is not available.' }

Write-Host "Image prefix: $prefix"
Write-Host "Image tag:    $tag"

Write-Host '>>> ai-backend' -ForegroundColor Green
$mvnArgs = @(
    'clean', 'package', '-Dmaven.test.skip=true',
    '-Ddocker.exec.skip=false',
    "-Ddocker.image.registry=$prefix",
    'exec:exec@docker-build'
)
& .\mvnw.cmd @mvnArgs
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }

Write-Host '>>> ai-user-web' -ForegroundColor Green
docker build -f web/user-web/Dockerfile -t "${prefix}/ai-user-web:${tag}" ./web
if ($LASTEXITCODE -ne 0) { throw 'user-web build failed' }

Write-Host '>>> ai-admin-web' -ForegroundColor Green
docker build -f web/admin-web/Dockerfile -t "${prefix}/ai-admin-web:${tag}" ./web
if ($LASTEXITCODE -ne 0) { throw 'admin-web build failed' }

Write-Host 'Done.' -ForegroundColor Green
Write-Host "  ${prefix}/ai-backend:${tag}"
Write-Host "  ${prefix}/ai-user-web:${tag}"
Write-Host "  ${prefix}/ai-admin-web:${tag}"
