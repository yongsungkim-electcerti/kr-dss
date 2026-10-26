# Dot-source하여 main의 데이터 경로를 읽는다. 파일·디렉터리를 생성하지 않는다.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$dataConfig = Get-Content -Raw (Join-Path $repoRoot 'config/local-data.properties') | ConvertFrom-StringData
$dataRoot = if ($env:KRTL_DATA_ROOT) { $env:KRTL_DATA_ROOT } else { $dataConfig.mainRoot }
if (-not [System.IO.Path]::IsPathRooted($dataRoot)) { throw 'KRTL_DATA_ROOT/mainRoot는 절대 경로여야 합니다.' }
if (-not (Test-Path -LiteralPath $dataRoot -PathType Container)) { throw "자료 저장소가 없습니다: $dataRoot" }
$tlStorageRoot = Join-Path (Join-Path $dataRoot $dataConfig.runtime) 'tl-management'
$tlLogRoot = Join-Path $dataRoot $dataConfig.logs
$tlResultsRoot = Join-Path $dataRoot $dataConfig.results
