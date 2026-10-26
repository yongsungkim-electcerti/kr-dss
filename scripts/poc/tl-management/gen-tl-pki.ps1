<#
.SYNOPSIS
  main 자료 저장소에 PoC 키 세트를 생성한다. 기존 키는 재생성하지 않는다.
.PARAMETER Target
  자료 저장소 루트. 생략하면 config/local-data.properties 또는 KRTL_DATA_ROOT.
#>
param([string]$Target)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
. (Join-Path $PSScriptRoot 'local-data.ps1')
if ($Target) {
    if (-not [System.IO.Path]::IsPathRooted($Target)) { throw 'Target은 절대 경로여야 합니다.' }
    $tlStorageRoot = Join-Path $Target 'runtime/tl-management'
}
$destination = Join-Path $tlStorageRoot 'pki'
if (Test-Path -LiteralPath $destination) {
    Write-Host "기존 키 세트 사용: $destination"
    exit 0
}
$profile = Join-Path $root 'scripts/poc/tl-management/pki-profile.json'
$cliArgs = 'cert tree -f "{0}" -o "{1}"' -f $profile, $destination
& (Join-Path $root 'gradlew.bat') --project-dir $root ':tools:krdss-cli:run' '--console=plain' -q "--args=$cliArgs"
if ($LASTEXITCODE -ne 0) { throw "키 세트 생성 실패 (exit $LASTEXITCODE)" }
Write-Host "main 자료 저장소에 생성 완료: $destination"
