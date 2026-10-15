param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$dependencyRoot = Join-Path $projectRoot 'deps'
$manifestPath = Join-Path $dependencyRoot 'download-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
foreach ($artifact in $manifest) {
    $artifactPath = Join-Path $dependencyRoot $artifact.path
    $matches = (Test-Path -LiteralPath $artifactPath) -and ((Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash -eq $artifact.sha256)
    if ($matches) { continue }
    if ($Offline) { throw "Missing or invalid dependency: $($artifact.path)" }
    if (-not $artifact.url.StartsWith('https://')) { throw "Non-HTTPS dependency URL: $($artifact.path)" }
    New-Item -ItemType Directory -Path (Split-Path -Parent $artifactPath) -Force | Out-Null
    $temporaryPath = "$artifactPath.download"
    Invoke-WebRequest -Uri $artifact.url -OutFile $temporaryPath
    if ((Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash -ne $artifact.sha256) {
        Remove-Item -LiteralPath $temporaryPath -Force
        throw "SHA-256 mismatch: $($artifact.path)"
    }
    Move-Item -LiteralPath $temporaryPath -Destination $artifactPath -Force
}
Write-Host "Verified $($manifest.Count) pinned dependency files."
