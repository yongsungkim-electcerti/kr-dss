param(
    [Parameter(Mandatory = $true)][string]$Anchors,
    [int]$Port = 8082
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$anchorFile = (Resolve-Path -LiteralPath $Anchors).Path
$dataRoot = $env:KRTL_DATA_ROOT
if (-not $dataRoot) {
    $line = Get-Content (Join-Path $repoRoot 'config/local-data.properties') | Where-Object { $_ -match '^mainRoot=' } | Select-Object -First 1
    $dataRoot = $line.Substring('mainRoot='.Length)
}
if (-not (Test-Path -LiteralPath $dataRoot -PathType Container)) { throw 'main 자료 경로를 확인하십시오.' }
$storage = Join-Path $dataRoot 'runtime/tl-verification'
Push-Location $repoRoot
try {
    & ./gradlew.bat :poc:poc-relying-party:bootRun "--args=--server.address=127.0.0.1 --server.port=$Port --krtl.poc.enabled=true --krtl.poc.storage-root=`"$storage`" --krtl.poc.anchors=`"$anchorFile`""
} finally { Pop-Location }
