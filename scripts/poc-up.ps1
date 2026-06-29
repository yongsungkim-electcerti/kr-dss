<#
.SYNOPSIS
  KR-DSS PoC 서비스 일괄 기동 (수동 브라우저 E2E 용).

.DESCRIPTION
  서비스를 백그라운드로 띄우고 각 포트가 열릴 때까지 대기한다. 로그는 repo 루트의
  logs\<service>.log 로 기록된다.

  -Mode full  : 원격서명(Mode 2) 전체 — hsm(8092)→sam(8091)→rssp(8090)→relying-party(8080)
  -Mode mode1 : 특허-A Mode 1(WebAuthn 로컬 서명)만 — relying-party(8080) 단독(SAM/HSM 불필요)

.EXAMPLE
  pwsh scripts\poc-up.ps1 -Mode mode1
  pwsh scripts\poc-up.ps1            # 기본 full
#>
param([ValidateSet('full', 'mode1')][string]$Mode = 'full')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $root 'gradlew.bat'
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Start-Svc([string]$name, [string]$task, [int]$port) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "• $name : 포트 $port 이미 사용 중 — 건너뜀" -ForegroundColor Yellow
        return
    }
    $out = Join-Path $logs "$name.log"
    $err = Join-Path $logs "$name.err.log"
    Write-Host "• $name 기동 (port $port) → logs\$name.log"
    Start-Process -FilePath $gradlew `
        -ArgumentList $task, '--console=plain' `
        -WorkingDirectory $root `
        -RedirectStandardOutput $out -RedirectStandardError $err `
        -WindowStyle Hidden | Out-Null

    for ($i = 0; $i -lt 90; $i++) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            Write-Host "  └ up ✓" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 2
    }
    Write-Warning "  └ $name 이 시간 내 포트 $port 를 열지 못함 — logs\$name.log 확인"
}

Write-Host "KR-DSS PoC 기동 (Mode=$Mode)" -ForegroundColor Cyan
if ($Mode -eq 'full') {
    Start-Svc 'hsm'  ':poc:poc-hsm:bootRun'  8092
    Start-Svc 'sam'  ':poc:poc-sam:bootRun'  8091
    Start-Svc 'rssp' ':poc:poc-rssp:bootRun' 8090
}
Start-Svc 'relying-party' ':poc:poc-relying-party:bootRun' 8080

Write-Host ""
Write-Host "열기: http://localhost:8080" -ForegroundColor Cyan
if ($Mode -eq 'mode1') {
    Write-Host "  ① 탭에서 '특허-A Mode 1' 체크 → 'WebAuthn 패스키 등록'"
    Write-Host "  ② 원문 입력 → '전자서명 요청'(생체/PIN)  ③ '검증 수행' → TOTAL_PASSED 확인"
}
Write-Host "로그: pwsh scripts\poc-logs.ps1 -Service relying-party"
Write-Host "종료: pwsh scripts\poc-down.ps1"
