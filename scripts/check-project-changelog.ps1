#Requires -Version 5.1
<#
.SYNOPSIS
  变更记录门禁：动代码须同集改 PROJECT.md；顶节 ### 补丁位须 >= pom.xml（允许文档领先构件）。

.EXAMPLE
  .\scripts\check-project-changelog.ps1
  .\scripts\check-project-changelog.ps1 -IncludeUntracked
#>
param(
    [switch]$IncludeUntracked
)

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repoRoot

$prefixes = @(
    'src/main/java/',
    'src/test/java/',
    'src/main/resources/',
    'web/user-web/',
    'web/admin-web/',
    'db/mysql/',
    'pom.xml'
)

function Invoke-GitNames {
    param([string[]]$GitArgs)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try {
        $out = & git @GitArgs 2>$null
        if ($null -eq $out) { return @() }
        if ($out -is [string]) { return @($out) }
        return @($out)
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

function Test-ChangelogPath {
    param([string]$RelativePath)
    $p = $RelativePath -replace '\\', '/'
    foreach ($prefix in $prefixes) {
        if ($p -eq $prefix.TrimEnd('/') -or $p.StartsWith($prefix)) {
            return $true
        }
    }
    return $false
}

function Get-PomVersion {
    $line = Get-Content -Path 'pom.xml' -Encoding UTF8 | Where-Object { $_ -match '^\s*<version>0\.1\.\d+-SNAPSHOT</version>\s*$' } | Select-Object -First 1
    if (-not $line) {
        return $null
    }
    if ($line -match '<version>(0\.1\.\d+-SNAPSHOT)</version>') {
        return $Matches[1]
    }
    return $null
}

function Get-ProjectChangelogTopVersion {
    $lines = Get-Content -Path 'PROJECT.md' -Encoding UTF8
    $inChangelog = $false
    foreach ($line in $lines) {
        if ($line -match '^##\s+变更记录\s*$') {
            $inChangelog = $true
            continue
        }
        if ($inChangelog -and $line -match '^###\s+(0\.1\.\d+-SNAPSHOT)\s*$') {
            return $Matches[1]
        }
        if ($inChangelog -and $line -match '^##\s+' -and $line -notmatch '^###') {
            break
        }
    }
    return $null
}

$files = @(Invoke-GitNames @('diff', '--cached', '--name-only'))
if ($IncludeUntracked) {
    $wt = @(Invoke-GitNames @('diff', '--name-only', 'HEAD'))
    $untracked = @(Invoke-GitNames @('ls-files', '--others', '--exclude-standard'))
    $files = @($files + $wt + $untracked) | Select-Object -Unique
}

if ($files.Count -eq 0) {
    Write-Host 'OK: no changes to check.'
    exit 0
}

$codeTouched = $false
$projectMdTouched = $false
foreach ($f in $files) {
    if ([string]::IsNullOrWhiteSpace($f)) { continue }
    if ($f -eq 'PROJECT.md') { $projectMdTouched = $true }
    if (Test-ChangelogPath $f) { $codeTouched = $true }
}

if (-not $codeTouched) {
    Write-Host 'OK: no changes under changelog-required paths.'
    exit 0
}

if (-not $projectMdTouched) {
    Write-Host ''
    Write-Host 'FAIL: Code changed but PROJECT.md was not updated.' -ForegroundColor Red
    Write-Host 'Add entries under PROJECT.md "## 变更记录" (see AGENTS.md).' -ForegroundColor Yellow
    Write-Host ''
    $files | Where-Object { Test-ChangelogPath $_ } | Select-Object -First 12 | ForEach-Object { Write-Host "  $_" }
    exit 1
}

function Get-SnapshotPatch {
    param([string]$Version)
    if ($Version -match '^0\.1\.(\d+)-SNAPSHOT$') {
        return [int]$Matches[1]
    }
    return $null
}

$pomVer = Get-PomVersion
$docVer = Get-ProjectChangelogTopVersion
$pomPatch = Get-SnapshotPatch $pomVer
$docPatch = Get-SnapshotPatch $docVer

if ($pomVer -and $docVer -and $null -ne $pomPatch -and $null -ne $docPatch) {
    if ($docPatch -lt $pomPatch) {
        Write-Host ''
        Write-Host "FAIL: PROJECT.md changelog top ($docVer) is behind pom.xml ($pomVer)." -ForegroundColor Red
        Write-Host 'Add a newer ### section at the top of "## 变更记录".' -ForegroundColor Yellow
        exit 1
    }
    if ($docPatch -eq $pomPatch) {
        Write-Host "OK: PROJECT.md updated; changelog top matches pom.xml ($pomVer)."
    }
    else {
        Write-Host "OK: PROJECT.md updated; changelog top $docVer (artifact pom $pomVer)."
    }
}
elseif ($pomVer -and $docVer) {
    Write-Host "OK: PROJECT.md updated (pom=$pomVer, doc=$docVer)."
}
else {
    Write-Host 'OK: PROJECT.md updated.'
}

$encScript = Join-Path $repoRoot 'scripts\check-encoding.ps1'
if (Test-Path $encScript) {
    & $encScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'FAIL: UTF-8 encoding check failed (see tools/encoding-report.txt).' -ForegroundColor Red
        exit 1
    }
}

exit 0
