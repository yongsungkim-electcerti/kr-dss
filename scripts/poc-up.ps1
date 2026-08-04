<#
.SYNOPSIS
  KR-DSS PoC 서비스 일괄 기동 (수동 브라우저 E2E 용).

.DESCRIPTION
  서비스를 백그라운드로 띄우고 각 포트가 열릴 때까지 대기한다. 로그는 repo 루트의
  logs\<service>.log 로 기록된다.

  -Mode full  : PoC 6개 전체 — kisa-tl(8081), tsp-sim(8082),
                hsm(8092)→sam(8091)→rssp(8090)→relying-party(8080)
  -Mode mode1 : 특허-A Mode 1(WebAuthn 로컬 서명)만 — relying-party(8080) 단독(SAM/HSM 불필요)
  -Demo       : 태블릿·QR 시연 프로파일 — relying-party 를 HTTPS(https://sol-pc:8080) 로 기동.
                사전에 `pwsh scripts\gen-demo-tls.ps1` 로 인증서를 생성해야 한다.

.EXAMPLE
  pwsh scripts\poc-up.ps1 -Mode mode1
  pwsh scripts\poc-up.ps1 -Mode mode1 -Demo   # 태블릿 시연(HTTPS)
  pwsh scripts\poc-up.ps1                     # 기본 full
#>
param(
    [ValidateSet('full', 'mode1')][string]$Mode = 'full',
    [switch]$Demo,
    [string]$DemoHostname = 'sol-pc'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $root 'gradlew.bat'
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Start-Svc([string]$name, [string]$task, [int]$port, [string[]]$extraArgs = @()) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "• $name : 포트 $port 이미 사용 중 — 건너뜀" -ForegroundColor Yellow
        return
    }
    $out = Join-Path $logs "$name.log"
    $err = Join-Path $logs "$name.err.log"
    Write-Host "• $name 기동 (port $port) → logs\$name.log"
    $process = Start-Process -FilePath $gradlew `
        -ArgumentList (@($task, '--console=plain') + $extraArgs) `
        -WorkingDirectory $root `
        -RedirectStandardOutput $out -RedirectStandardError $err `
        -WindowStyle Hidden -PassThru

    for ($i = 0; $i -lt 90; $i++) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            Write-Host "  └ up ✓" -ForegroundColor Green
            return
        }
        if ($process.HasExited) {
            throw "$name 프로세스가 포트 $port 를 열기 전에 종료됨 (exit=$($process.ExitCode)). logs\$name.log 및 logs\$name.err.log 확인"
        }
        Start-Sleep -Seconds 2
    }
    try { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } catch {}
    throw "$name 이 제한 시간 내 포트 $port 를 열지 못함. logs\$name.log 및 logs\$name.err.log 확인"
}

Write-Host "KR-DSS PoC 기동 (Mode=$Mode)" -ForegroundColor Cyan
if ($Mode -eq 'full') {
    Start-Svc 'kisa-tl' ':poc:poc-kisa-tl:bootRun' 8081
    Start-Svc 'tsp-sim' ':poc:poc-tsp-sim:bootRun' 8082
    Start-Svc 'hsm'  ':poc:poc-hsm:bootRun'  8092
    Start-Svc 'sam'  ':poc:poc-sam:bootRun'  8091
    Start-Svc 'rssp' ':poc:poc-rssp:bootRun' 8090
}
$rpArgs = @()
if ($Demo) {
    $keystore = Join-Path $root 'certs\demo-tls.p12'
    if (-not (Test-Path $keystore)) {
        throw "데모 TLS 키스토어가 없습니다: $keystore`n먼저 실행: pwsh scripts\gen-demo-tls.ps1 -Hostname $DemoHostname"
    }
    $rpArgs = @('--args=--spring.profiles.active=demo')
}
Start-Svc 'relying-party' ':poc:poc-relying-party:bootRun' 8080 $rpArgs

$baseUrl = if ($Demo) { "https://${DemoHostname}:8080" } else { 'http://localhost:8080' }
Write-Host ""
Write-Host "열기: $baseUrl" -ForegroundColor Cyan
if ($Demo) {
    Write-Host "  ⚠ IP 주소로 접속하면 WebAuthn 이 동작하지 않는다 (rpId 에 IP 사용 불가)." -ForegroundColor Yellow
    Write-Host "  ⚠ 태블릿에 certs\krdss-demo-root-ca.crt 가 설치되어 있어야 한다." -ForegroundColor Yellow
}
if ($Mode -eq 'full') {
    Write-Host "서비스: KISA-TL(:8081), TSP-SIM(:8082), RSSP(:8090), SAM(:8091), HSM(:8092)" -ForegroundColor DarkGray
}
if ($Mode -eq 'mode1') {
    Write-Host "  ① 탭에서 '특허-A Mode 1' 체크 → 'WebAuthn 패스키 등록'"
    Write-Host "  ② 원문 입력 → '전자서명 요청'(생체/PIN)  ③ '검증 수행' → TOTAL_PASSED 확인"
}
Write-Host "로그: pwsh scripts\poc-logs.ps1 -Service relying-party"
Write-Host "종료: pwsh scripts\poc-down.ps1"
