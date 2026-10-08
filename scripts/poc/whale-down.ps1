<#
.SYNOPSIS
  Whale PoC(PDF Baseline) 종료 — 이용기관(rp) + 서버서명(rssp).

.DESCRIPTION
  Whale PoC 가 사용하는 포트(8080 rp / 8090 rssp)에서 수신 중인 프로세스를 종료한다.
  Gradle 데몬도 함께 정지한다(-KeepGradleDaemon 으로 유지 가능).

.EXAMPLE
  pwsh scripts\poc\whale-down.ps1
  pwsh scripts\poc\whale-down.ps1 -KeepGradleDaemon
#>
param([switch]$KeepGradleDaemon)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# rp(:8080) 를 먼저 내려 진행 중인 서명 요청이 rssp 로 새로 들어가지 않게 한다.
$services = [ordered]@{ 'rp' = 8080; 'rssp' = 8090 }

Write-Host "Whale PoC 종료" -ForegroundColor Cyan
foreach ($entry in $services.GetEnumerator()) {
    $name = $entry.Key
    $port = $entry.Value
    $procId = (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess
    if ($procId) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "• $name (포트 $port) 종료 (PID $procId) ✓" -ForegroundColor Green
        }
        catch {
            Write-Warning "• $name (포트 $port, PID $procId) 종료 실패: $($_.Exception.Message)"
        }
    }
    else {
        Write-Host "• $name (포트 $port) : 수신 프로세스 없음"
    }
}

if (-not $KeepGradleDaemon) {
    Write-Host "• Gradle 데몬 정지…"
    # --project-dir 명시: scripts\ 안에서 실행해도 프로젝트 디렉터리를 잘못 잡지 않는다.
    & (Join-Path $root 'gradlew.bat') '--project-dir' $root --stop *> $null
}
Write-Host "완료." -ForegroundColor Cyan
