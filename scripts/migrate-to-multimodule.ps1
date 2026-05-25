# 将单模块 src 树迁移到 Maven 多模块目录（输出至 modules/）
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modules = Join-Path $root "modules"

$javaBase = "$root\src\main\java\com\aaron\cloud"
if (-not (Test-Path $javaBase)) {
    Write-Host "Source tree already migrated."
    exit 0
}

$testBase = "$root\src\test\java\com\aaron\cloud"

function Move-Package($module, $packageDir) {
    $src = Join-Path $javaBase $packageDir
    $dst = Join-Path $modules "$module\src\main\java\com\aaron\cloud\$packageDir"
    if (Test-Path $src) {
        New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
        Move-Item -Force $src $dst
        Write-Host "Moved $packageDir -> modules/$module"
    }
}

function Move-TestTree($module, $packageDir) {
    $src = Join-Path $testBase $packageDir
    $dst = Join-Path $modules "$module\src\test\java\com\aaron\cloud\$packageDir"
    if (Test-Path $src) {
        New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
        Move-Item -Force $src $dst
        Write-Host "Moved test $packageDir -> modules/$module"
    }
}

@(
    @{ m = "ai-common"; p = "common" },
    @{ m = "ai-remoting"; p = "remoting" },
    @{ m = "ai-gateway"; p = "gateway" },
    @{ m = "ai-identity"; p = "identity" },
    @{ m = "ai-model"; p = "model" },
    @{ m = "ai-file"; p = "file" },
    @{ m = "ai-rag"; p = "rag" },
    @{ m = "ai-job"; p = "job" },
    @{ m = "ai-job"; p = "scheduled" },
    @{ m = "ai-chat"; p = "chat" },
    @{ m = "ai-mcp"; p = "mcp" },
    @{ m = "ai-notification"; p = "notification" },
    @{ m = "ai-eval"; p = "eval" }
) | ForEach-Object { Move-Package $_.m $_.p }

$appSrc = Join-Path $javaBase "AiApplication.java"
$appDst = Join-Path $modules "ai-bootstrap\src\main\java\com\aaron\cloud\AiApplication.java"
if (Test-Path $appSrc) {
    New-Item -ItemType Directory -Force -Path (Split-Path $appDst) | Out-Null
    Move-Item -Force $appSrc $appDst
}
Move-Package "ai-bootstrap" "bootstrap"

$resSrc = "$root\src\main\resources"
$resDst = Join-Path $modules "ai-bootstrap\src\main\resources"
if (Test-Path $resSrc) {
    New-Item -ItemType Directory -Force -Path $resDst | Out-Null
    Get-ChildItem $resSrc | ForEach-Object { Move-Item -Force $_.FullName $resDst }
}

Move-TestTree "ai-common" "common"
Move-TestTree "ai-chat" "chat"
Move-TestTree "ai-identity" "identity"
Move-TestTree "ai-model" "model"
Move-TestTree "ai-rag" "rag"

$bootstrapTestDir = Join-Path $modules "ai-bootstrap\src\test\java\com\aaron\cloud"
New-Item -ItemType Directory -Force -Path $bootstrapTestDir | Out-Null
foreach ($f in @("AiApplicationIntegrationTest.java", "BuildSanityTest.java")) {
    $src = Join-Path $testBase $f
    if (Test-Path $src) {
        Move-Item -Force $src (Join-Path $bootstrapTestDir $f)
    }
}

$trSrc = "$root\src\test\resources"
$trDst = Join-Path $modules "ai-bootstrap\src\test\resources"
if (Test-Path $trSrc) {
    New-Item -ItemType Directory -Force -Path $trDst | Out-Null
    Get-ChildItem $trSrc | ForEach-Object { Move-Item -Force $_.FullName $trDst }
}

# 清理空 src 目录
if (Test-Path "$root\src\main\java") { Remove-Item -Recurse -Force "$root\src\main" -ErrorAction SilentlyContinue }
if (Test-Path "$root\src\test") { Remove-Item -Recurse -Force "$root\src\test" -ErrorAction SilentlyContinue }
if (Test-Path "$root\src") {
    $left = Get-ChildItem "$root\src" -Recurse -ErrorAction SilentlyContinue
    if (-not $left) { Remove-Item -Recurse -Force "$root\src" -ErrorAction SilentlyContinue }
}

Write-Host "Migration complete."
