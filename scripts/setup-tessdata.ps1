# Download Tesseract language packs into repo tessdata/ for local image OCR.
# Requires PowerShell 5+ and GitHub access.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root "tessdata"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$base = "https://github.com/tesseract-ocr/tessdata/raw/main"
$files = @("eng.traineddata", "chi_sim.traineddata")

foreach ($f in $files) {
    $out = Join-Path $dest $f
    if (Test-Path $out) {
        Write-Host "skip (exists): $f"
        continue
    }
    Write-Host "download: $f"
    Invoke-WebRequest -Uri "$base/$f" -OutFile $out -UseBasicParsing
}

$env:TESSDATA_PREFIX = $dest
Write-Host "done. tessdata: $dest"
Write-Host "TESSDATA_PREFIX set for this shell: $dest"
Write-Host "Set TESSDATA_PREFIX permanently or re-run before starting the backend."
