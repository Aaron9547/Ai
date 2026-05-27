#Requires -Version 5.1
<#
.SYNOPSIS
  UTF-8 / mojibake gate for Java, TS, and Vue sources.

.EXAMPLE
  .\scripts\check-encoding.ps1
#>
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $repoRoot

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) {
    Write-Host 'FAIL: python not found on PATH.' -ForegroundColor Red
    exit 1
}

& python (Join-Path $repoRoot 'tools\check_text_encoding.py') --write-report
exit $LASTEXITCODE
