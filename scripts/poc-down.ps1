<#
.SYNOPSIS
  KR-DSS PoC 서비스 일괄 종료.

.DESCRIPTION
  PoC 포트(8080/8090/8091/8092)에서 수신 중인 프로세스를 종료하고, Gradle 데몬도 정지한다.

.EXAMPLE
  pwsh scripts\poc-down.ps1
#>
param([switch]$KeepGradleDaemon)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$ports = 8080, 8090, 8091, 8092

Write-Host "KR-DSS PoC 종료" -ForegroundColor Cyan
foreach ($port in $ports) {
    $procId = (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess
    if ($procId) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "• 포트 $port 종료 (PID $procId) ✓" -ForegroundColor Green
        }
        catch {
            Write-Warning "• 포트 $port (PID $procId) 종료 실패: $($_.Exception.Message)"
        }
    }
    else {
        Write-Host "• 포트 $port : 수신 프로세스 없음"
    }
}

if (-not $KeepGradleDaemon) {
    Write-Host "• Gradle 데몬 정지…"
    & (Join-Path $root 'gradlew.bat') --stop *> $null
}
Write-Host "완료." -ForegroundColor Cyan
