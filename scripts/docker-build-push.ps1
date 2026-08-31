# 后端 Docker 镜像：Maven package + 本机 docker build/push（避免 fabric8 上传大 JAR 时 chunk 错误）
# 用法：
#   .\scripts\docker-build-push.ps1
#   .\scripts\docker-build-push.ps1 -PushImage
param(
    [switch]$PushImage,
    [switch]$SkipTests = $true
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$jdk25 = 'C:\Program Files\Java\jdk-25.0.2'
if (Test-Path $jdk25) {
    $env:JAVA_HOME = $jdk25
} elseif ($env:JAVA_HOME -and $env:JAVA_HOME -notmatch '25') {
    Write-Warning "JAVA_HOME 非 JDK 25（当前: $($env:JAVA_HOME)）。请安装 JDK 25 或设置 JAVA_HOME。"
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error @"
Docker 引擎不可用。请先启动 Docker Desktop，待托盘图标就绪后再试。
"@
    exit 1
}

$mvnArgs = @(
    'clean', 'package', '-Dmaven.test.skip=true', '-Ddocker.exec.skip=false'
)
$prefix = 'ai-platform'
if (Test-Path 'deploy\compose.env') {
    $line = Select-String -Path 'deploy\compose.env' -Pattern '^IMAGE_PREFIX=' | Select-Object -First 1
    if ($line) { $prefix = ($line.Line -replace '^IMAGE_PREFIX=', '').Trim() }
}
$mvnArgs += "-Ddocker.image.registry=$prefix"
$mvnArgs += 'exec:exec@docker-build'

if ($PushImage) {
    $mvnArgs += 'exec:exec@docker-push'
    $prefix = 'ai-platform'
    if (Test-Path 'deploy\compose.env') {
        $line = Select-String -Path 'deploy\compose.env' -Pattern '^IMAGE_PREFIX=' | Select-Object -First 1
        if ($line) { $prefix = ($line.Line -replace '^IMAGE_PREFIX=', '').Trim() }
    }
    Write-Host "Will push to ${prefix}/ai-backend (docker login required)." -ForegroundColor Cyan
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
& .\mvnw.cmd @mvnArgs
exit $LASTEXITCODE
