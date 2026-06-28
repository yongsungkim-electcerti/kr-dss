<#
.SYNOPSIS
  KR-DSS PoC 서비스 로그 실시간 확인(tail -f).

.DESCRIPTION
  logs\<service>.log 를 실시간으로 따라간다. -Grep 으로 특정 문자열만 필터링한다.
  특허-A Mode 1 검증 흐름만 보려면: -Service relying-party -Grep Mode1

.EXAMPLE
  pwsh scripts\poc-logs.ps1 -Service relying-party
  pwsh scripts\poc-logs.ps1 -Service relying-party -Grep Mode1
  pwsh scripts\poc-logs.ps1 -Service sam
#>
param(
    [ValidateSet('relying-party', 'rssp', 'sam', 'hsm')][string]$Service = 'relying-party',
    [string]$Grep = '',
    [int]$Tail = 40
)

$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root "logs\$Service.log"
if (-not (Test-Path $file)) {
    Write-Warning "로그 파일 없음: $file  (먼저 poc-up.ps1 로 기동하세요)"
    return
}

Write-Host "tail -f $file" -ForegroundColor Cyan
if ([string]::IsNullOrWhiteSpace($Grep)) {
    Get-Content -Path $file -Wait -Tail $Tail
}
else {
    Write-Host "(필터: '$Grep')" -ForegroundColor DarkGray
    Get-Content -Path $file -Wait -Tail $Tail | Select-String -SimpleMatch $Grep
}
