#Requires -Version 5.1
<#
.SYNOPSIS
  安装 git pre-commit：提交前校验 PROJECT.md 变更记录（含暂存区未跟踪需先 git add）。
#>
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$hookPath = Join-Path $repoRoot '.git\hooks\pre-commit'
$hookContent = @'
#!/bin/sh
powershell -NoProfile -ExecutionPolicy Bypass -File "$(git rev-parse --show-toplevel)/scripts/check-project-changelog.ps1"
exit $?
'@
Set-Content -Path $hookPath -Value $hookContent -Encoding ASCII -NoNewline
Write-Host "Installed: $hookPath"
Write-Host 'Runs check-project-changelog.ps1 on staged files before each commit.'
