#Requires -Version 5.1
<#
.SYNOPSIS
  将仓库 UTF-8 配置同步到本地 IDE（IntelliJ / VS Code / Cursor）。

.EXAMPLE
  .\scripts\setup-ide-encoding.ps1
#>
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repoRoot

$ideaSrc = Join-Path $repoRoot 'config\idea\encodings.xml'
$ideaDir = Join-Path $repoRoot '.idea'
$ideaDst = Join-Path $ideaDir 'encodings.xml'
if (Test-Path $ideaSrc) {
    if (-not (Test-Path $ideaDir)) {
        New-Item -ItemType Directory -Path $ideaDir | Out-Null
    }
    Copy-Item -Path $ideaSrc -Destination $ideaDst -Force
    Write-Host "OK: IntelliJ/Cursor Java IDE -> $ideaDst (Project Encoding = UTF-8)"
}

$vscodeSrc = Join-Path $repoRoot 'config\vscode\settings.json'
$vscodeDir = Join-Path $repoRoot '.vscode'
$vscodeDst = Join-Path $vscodeDir 'settings.json'
if (Test-Path $vscodeSrc) {
    if (-not (Test-Path $vscodeDir)) {
        New-Item -ItemType Directory -Path $vscodeDir | Out-Null
    }
    if (Test-Path $vscodeDst) {
        Write-Host "SKIP: $vscodeDst already exists (merge config/vscode/settings.json manually if needed)"
    }
    else {
        Copy-Item -Path $vscodeSrc -Destination $vscodeDst -Force
        Write-Host "OK: VS Code / Cursor -> $vscodeDst (files.encoding=utf8, autoGuessEncoding=false)"
    }
}

Write-Host ''
Write-Host 'Also verify: Editor -> File Encodings -> Global + Project = UTF-8; do NOT use GBK/GB2312/GB18030 to open sources.'
