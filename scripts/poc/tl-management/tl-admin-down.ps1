<#
.SYNOPSIS
  TL 관리 PoC(poc-kisa-tl, :8081)를 종료한다.

.DESCRIPTION
  포트 8081 에서 수신 중인 프로세스만 종료한다. Gradle 데몬은 다른 worktree·에이전트와
  공유되므로 기본으로 정지하지 않는다. 필요하면 -StopGradleDaemon 을 지정한다.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/tl-admin-down.ps1
#>
param([switch]$StopGradleDaemon)
$ErrorActionPreference = 'Continue'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$port = 8081

Write-Host "TL 관리 PoC 종료" -ForegroundColor Cyan
$procIds = (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess | Select-Object -Unique
if ($procIds) {
    foreach ($procId in $procIds) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "• 포트 $port 종료 (PID $procId) ✓" -ForegroundColor Green
        }
        catch {
            Write-Warning "• 포트 $port (PID $procId) 종료 실패: $($_.Exception.Message)"
        }
    }
}
else {
    Write-Host "• 포트 $port : 수신 프로세스 없음"
}

if ($StopGradleDaemon) {
    Write-Host "• Gradle 데몬 정지…"
    & (Join-Path $root 'gradlew.bat') --stop *> $null
}
Write-Host "완료." -ForegroundColor Cyan
