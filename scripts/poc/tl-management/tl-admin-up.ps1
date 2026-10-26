<#
.SYNOPSIS
  TL 관리 PoC(poc-kisa-tl, :8081)만 기동한다.

.DESCRIPTION
  저장소 루트에서 :poc:poc-kisa-tl:bootRun 을 실행한다. 저장 루트는 config/local-data.properties에서 지정하며
  로그와 키도 main 자료 저장소에서 읽고 쓴다.
  - 기본: 백그라운드 기동 후 포트가 열릴 때까지 대기. 로그는 logs\kisa-tl.log
  - -Foreground: 현재 콘솔에서 실행(Ctrl+C 로 종료)
  TL 서명 키(runtime/tl-management/pki/tl-signer/T-02-signer)가 없으면 경고한다.
  키가 없어도 입력·초안 저장은 되지만 발행(서명)은 실패한다.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/tl-admin-up.ps1
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/tl-admin-up.ps1 -Open
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/tl-admin-up.ps1 -Foreground
#>
param(
    [switch]$Foreground,
    [switch]$Open,
    [int]$TimeoutSeconds = 180
)
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $PSScriptRoot 'local-data.ps1')
$env:KRTL_DATA_ROOT = $dataRoot
$gradlew = Join-Path $root 'gradlew.bat'
$task = ':poc:poc-kisa-tl:bootRun'
$port = 8081
$url = "http://localhost:$port"

Write-Host "TL 관리 PoC 기동 ($root)" -ForegroundColor Cyan

$signer = Join-Path $tlStorageRoot 'pki\tl-signer\T-02-signer'
if (-not ((Test-Path (Join-Path $signer 'key.pem')) -and (Test-Path (Join-Path $signer 'chain.pem')))) {
    Write-Warning "TL 서명 키가 없습니다: $signer`n  발행(서명)이 실패합니다. 키 세트 생성: scripts/poc/tl-management/gen-tl-pki.ps1"
}

if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
    $procId = (Get-NetTCPConnection -LocalPort $port -State Listen).OwningProcess | Select-Object -First 1
    Write-Host "• 포트 $port 이미 사용 중 (PID $procId) — 기동하지 않음. 종료: tl-admin-down.ps1" -ForegroundColor Yellow
    exit 1
}

if ($Foreground) {
    Write-Host "• 포그라운드 실행 → $url (Ctrl+C 로 종료)"
    Push-Location $root
    try { & $gradlew $task --console=plain } finally { Pop-Location }
    exit $LASTEXITCODE
}

$logs = $tlLogRoot
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$out = Join-Path $logs 'kisa-tl.log'
$err = Join-Path $logs 'kisa-tl.err.log'
Write-Host "• 백그라운드 기동 → $out"
$process = Start-Process -FilePath $gradlew `
    -ArgumentList @($task, '--console=plain') `
    -WorkingDirectory $root `
    -RedirectStandardOutput $out -RedirectStandardError $err `
    -WindowStyle Hidden -PassThru

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "  └ up ✓" -ForegroundColor Green
        Write-Host ""
        Write-Host "관리 화면: $url" -ForegroundColor Cyan
        Write-Host "IF-07   : $url/tl/kr-tl.xml , $url/tl/kr-tl.sha2 (제공본 선택 후)"
        Write-Host "로그    : Get-Content -Wait $out"
        Write-Host "종료    : powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/tl-admin-down.ps1"
        if ($Open) { Start-Process $url }
        exit 0
    }
    if ($process.HasExited) {
        throw "프로세스가 포트 $port 를 열기 전에 종료됨 (exit=$($process.ExitCode)). $out , $err 확인"
    }
    Start-Sleep -Seconds 2
}
try { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } catch {}
throw "제한 시간($TimeoutSeconds 초) 내 포트 $port 를 열지 못함. $out , $err 확인"
