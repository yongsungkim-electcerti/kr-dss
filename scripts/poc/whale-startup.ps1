<#
.SYNOPSIS
  Whale PoC(PDF Baseline) 기동 — 이용기관(rp) + 서버서명(rssp).

.DESCRIPTION
  ./gradlew whalePackage 로 산출물을 만든 뒤 results/packages/whale/poc 의 실행 jar 를 백그라운드로 띄운다.

    results/packages/whale/poc/rssp.jar   서버서명(RSSP/SSA)      :8090
    results/packages/whale/poc/rp.jar     이용기관(Relying Party) :8080

  Whale PoC 범위는 PAdES BASELINE-B 다. 원격전자서명 백엔드(SAM :8091 / HSM :8092),
  TSA·OCSP 시뮬레이터(:8082), KISA-TL 시뮬레이터(:8081)는 기동하지 않는다.

  로그는 저장소 루트의 logs\whale-<service>.log 에 기록된다.
  런타임 설정을 덮어쓰려면 results/packages/whale/poc/config/application.yml 를 두면 된다.

.PARAMETER AdesProfile
  results/packages/whale/libs 로 반출할 KR-AdES 범위.
    pades (기본) PAdES 슬림 — kr-ades-pades + kr-ades-core 만
    full         6종 포맷 어댑터 전부

.PARAMETER Restart
  이미 기동 중인 rp/rssp 를 먼저 종료하고 다시 기동한다.
  실행 중인 java 는 results\packages\whale\poc\*.jar 를 잠그므로, 정리하지 않으면 재패키징이
  "Unable to delete file ...
p.jar" 로 실패한다.

.PARAMETER SkipBuild
  재패키징 없이 이미 만들어진 results/packages/whale/poc 의 jar 로 바로 기동한다.

.PARAMETER Demo
  태블릿·QR 시연 프로파일 — rp 를 HTTPS(https://<DemoHostname>:8080)로 기동한다.
  사전에 `pwsh scripts\common\gen-demo-tls.ps1` 로 인증서를 생성해야 한다.

.EXAMPLE
  pwsh scripts\poc\whale-startup.ps1
  pwsh scripts\poc\whale-startup.ps1 -Restart          # 코드 수정 후 재빌드·재기동
  pwsh scripts\poc\whale-startup.ps1 -AdesProfile full
  pwsh scripts\poc\whale-startup.ps1 -SkipBuild
  pwsh scripts\poc\whale-startup.ps1 -Demo
#>
param(
    [ValidateSet('pades', 'full')][string]$AdesProfile = 'pades',
    [switch]$Restart,
    [switch]$SkipBuild,
    [switch]$Demo,
    [string]$DemoHostname = 'sol-pc',
    [string]$JavaHome = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$outputPoc = Join-Path $root 'results\packages\whale\poc'
$logs = Join-Path $root 'logs'

# --- Java 21 실행기 결정 ---------------------------------------------------
# 빌드는 Gradle toolchain 이 JDK 21 을 자동 프로비저닝하지만, jar 실행은 PATH 의 java 를
# 쓰므로 버전이 낮을 수 있다. JavaHome → JAVA_HOME → PATH → Gradle 프로비저닝 JDK 순으로 찾는다.
function Get-JavaMajor([string]$exe) {
    # 1순위: JDK 홈의 release 파일(JAVA_VERSION="21.0.10"). 프로세스를 띄우지 않아 가장 안전하다.
    $jdkHome = Split-Path -Parent (Split-Path -Parent $exe)
    $release = Join-Path $jdkHome 'release'
    if (Test-Path $release) {
        $hit = Select-String -Path $release -Pattern '^JAVA_VERSION="?(\d+)' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($hit) { return [int]$hit.Matches[0].Groups[1].Value }
    }
    # 2순위: java -version. 출력이 stderr 라 파일로 받는다.
    #   Windows PowerShell 5.1 은 $ErrorActionPreference='Stop' 에서 네이티브 명령의 stderr 를
    #   종료 오류(NativeCommandError)로 승격한다. `2>&1 | ...` 로 파이프라인에 태우면 JDK 가
    #   멀쩡히 있어도 전부 catch 로 빠져 "JDK 21 을 찾지 못했습니다" 가 뜬다. 파일 리다이렉션은
    #   stderr 를 파이프라인에 올리지 않으므로 5.1/7 양쪽에서 동일하게 동작한다.
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        & $exe -version 2> $tmp | Out-Null
        $out = Get-Content -Path $tmp -Raw -ErrorAction SilentlyContinue
        if ($out -and $out -match 'version "(\d+)') { return [int]$Matches[1] }
    }
    catch { return 0 }
    finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
    return 0
}

function Resolve-Java {
    $candidates = @()
    if ($JavaHome) { $candidates += (Join-Path $JavaHome 'bin\java.exe') }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $onPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($onPath) { $candidates += $onPath }
    # Gradle toolchain 이 내려받아 둔 JDK (~\.gradle\jdks\*\bin\java.exe)
    $gradleJdks = Join-Path $env:USERPROFILE '.gradle\jdks'
    if (Test-Path $gradleJdks) {
        $candidates += (Get-ChildItem -Path $gradleJdks -Filter 'java.exe' -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.DirectoryName -like '*\bin' } | ForEach-Object { $_.FullName })
    }
    foreach ($c in ($candidates | Where-Object { $_ -and (Test-Path $_) })) {
        if ((Get-JavaMajor $c) -ge 21) { return $c }
    }
    throw "JDK 21 이상을 찾지 못했습니다. -JavaHome 으로 경로를 지정하거나 JAVA_HOME 을 설정하세요."
}

function Get-PortOwner([int]$port) {
    (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1).OwningProcess
}

function Start-Svc([string]$name, [string]$jar, [int]$port, [string[]]$appArgs = @()) {
    $owner = Get-PortOwner $port
    if ($owner) {
        Write-Host "• $name : 포트 $port 이미 사용 중(PID $owner) — 건너뜀. 방금 빌드한 jar 가 아닐 수 있다." -ForegroundColor Yellow
        return
    }
    if (-not (Test-Path $jar)) {
        throw "$name 실행 jar 없음: $jar`n-SkipBuild 없이 다시 실행하거나 .\gradlew.bat whalePackage 를 먼저 수행하세요."
    }
    $out = Join-Path $logs "whale-$name.log"
    $err = Join-Path $logs "whale-$name.err.log"
    Write-Host "• $name 기동 (port $port) → logs\whale-$name.log"
    $process = Start-Process -FilePath $java `
        -ArgumentList (@('-jar', $jar) + $appArgs) `
        -WorkingDirectory $outputPoc `
        -RedirectStandardOutput $out -RedirectStandardError $err `
        -WindowStyle Hidden -PassThru

    for ($i = 0; $i -lt 60; $i++) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            Write-Host "  └ up ✓ (PID $($process.Id))" -ForegroundColor Green
            return
        }
        if ($process.HasExited) {
            throw "$name 프로세스가 포트 $port 를 열기 전에 종료됨 (exit=$($process.ExitCode)). logs\whale-$name.log 및 logs\whale-$name.err.log 확인"
        }
        Start-Sleep -Seconds 2
    }
    try { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } catch {}
    throw "$name 이 제한 시간 내 포트 $port 를 열지 못함. logs\whale-$name.log 및 logs\whale-$name.err.log 확인"
}

# --- 0. 선점 포트 점검 -----------------------------------------------------
# 이미 기동 중인 rp/rssp 의 java 프로세스는 results\packages\whale\poc\*.jar 를 잠근다. 그 상태로 패키징하면
# :poc:packagePoc 이 "Unable to delete file ... rp.jar" 로 실패하므로,
# 빌드를 건드리기 전에 먼저 정리하거나 명확히 안내하고 멈춘다.
Write-Host "Whale PoC 기동 (PDF Baseline — rp + rssp)" -ForegroundColor Cyan

$busy = @()
foreach ($entry in ([ordered]@{ 'rp' = 8080; 'rssp' = 8090 }).GetEnumerator()) {
    $owner = Get-PortOwner $entry.Value
    if ($owner) {
        $busy += [pscustomobject]@{ Name = $entry.Key; Port = $entry.Value; ProcessId = $owner }
    }
}

if ($busy) {
    # "$(...)" 바로 뒤에 '(' 가 오면 PowerShell 이 메서드 호출로 파싱하므로 -f 를 쓴다.
    $desc = ($busy | ForEach-Object { '{0}(:{1}, PID {2})' -f $_.Name, $_.Port, $_.ProcessId }) -join ', '
    if ($Restart) {
        Write-Host "• 기동 중인 서비스 종료: $desc" -ForegroundColor Yellow
        foreach ($b in $busy) {
            try { Stop-Process -Id $b.ProcessId -Force -ErrorAction Stop }
            catch { throw "포트 $($b.Port) (PID $($b.ProcessId)) 종료 실패: $($_.Exception.Message)" }
        }
        # 프로세스가 완전히 끝나야 jar 파일 핸들이 풀린다.
        foreach ($b in $busy) {
            Wait-Process -Id $b.ProcessId -Timeout 20 -ErrorAction SilentlyContinue
            if (Get-PortOwner $b.Port) {
                throw "포트 $($b.Port) 가 아직 점유 중이다. pwsh scripts\poc\whale-down.ps1 로 정리 후 다시 실행하세요."
            }
        }
    }
    elseif ($SkipBuild) {
        # 패키징을 하지 않으니 jar 잠금 문제는 없다. 다만 기동 중인 건 이전 빌드다.
        Write-Host "• 이미 기동 중: $desc — 재패키징을 하지 않으므로 그대로 둔다." -ForegroundColor Yellow
    }
    else {
        throw ("이미 기동 중인 서비스가 있습니다: $desc`n" +
            "실행 중인 java 가 results\packages\whale\poc\*.jar 를 잠그고 있어 재패키징이 실패합니다. 다음 중 하나를 쓰세요.`n" +
            "  재빌드 후 재기동 : pwsh scripts\poc\whale-startup.ps1 -Restart`n" +
            "  종료 후 재실행   : pwsh scripts\poc\whale-down.ps1`n" +
            "  빌드 없이 기동만 : pwsh scripts\poc\whale-startup.ps1 -SkipBuild")
    }
}

# --- 1. 패키징 -------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $logs | Out-Null

if (-not $SkipBuild) {
    $pkgLog = Join-Path $logs 'whale-package.log'
    Write-Host "• 패키징: .\gradlew.bat whalePackage -Pwhale.ades=$AdesProfile  (로그 logs\whale-package.log)"

    # Gradle 은 실패 사유를 stderr 로도 낸다. Windows PowerShell 5.1 은
    # $ErrorActionPreference='Stop' 에서 네이티브 stderr 를 종료 오류로 승격하므로,
    # 파이프로 넘기는 동안만 잠시 낮춘다. (그러지 않으면 "exit=1" 대신 엉뚱한 오류가 뜬다)
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # --project-dir 를 명시한다. Gradle 은 프로젝트 디렉터리를 gradlew.bat 의 위치가 아니라
        # 현재 작업 디렉터리로 잡으므로, scripts\ 안에서 실행하면
        # "Project directory '...\scripts' is not part of the build" 으로 실패한다.
        & (Join-Path $root 'gradlew.bat') '--project-dir' $root 'whalePackage' `
            "-Pwhale.ades=$AdesProfile" '--console=plain' 2>&1 |
            Tee-Object -FilePath $pkgLog
        $pkgExit = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $prevEap }

    if ($pkgExit -ne 0) {
        # 콘솔에서 스크롤로 밀려나기 쉬운 Gradle 의 "What went wrong" 블록을 오류에 같이 싣는다.
        $detail = ''
        $lines = @(Get-Content $pkgLog -ErrorAction SilentlyContinue)
        $hit = $lines | Select-String -SimpleMatch '* What went wrong:' | Select-Object -First 1
        if ($hit) {
            $from = $hit.LineNumber - 1
            $to = [Math]::Min($from + 8, $lines.Count - 1)
            $detail = "`n" + (($lines[$from..$to]) -join "`n")
        }
        throw ("whalePackage 실패 (exit=$pkgExit)$detail`n" +
            "전체 로그: logs\whale-package.log")
    }
}
else {
    # -SkipBuild 는 패키징을 건너뛰므로 results/packages/whale/libs 는 직전 실행의 프로파일 그대로다.
    Write-Host "• 패키징 생략 — results/packages/whale/libs 는 직전 실행 상태 유지" -ForegroundColor DarkGray
}

New-Item -ItemType Directory -Force -Path (Join-Path $outputPoc 'config') | Out-Null

$java = Resolve-Java
Write-Host "• java: $java (major $(Get-JavaMajor $java))" -ForegroundColor DarkGray

# --- 2. 서비스 기동 --------------------------------------------------------
# 서버서명(rssp) 이 먼저 떠 있어야 이용기관(rp) 이 원격 호출을 붙일 수 있다.
Start-Svc 'rssp' (Join-Path $outputPoc 'rssp.jar') 8090

$rpArgs = @()
if ($Demo) {
    $keystore = Join-Path $root 'runtime\pki\demo-tls\demo-tls.p12'
    if (-not (Test-Path $keystore)) {
        throw "데모 TLS 키스토어가 없습니다: $keystore`n먼저 실행: pwsh scripts\common\gen-demo-tls.ps1 -Hostname $DemoHostname"
    }
    # jar 의 workingDir 은 results/packages/whale/poc 이므로 키스토어는 절대 경로로 넘긴다.
    $rpArgs = @('--spring.profiles.active=demo', "--server.ssl.key-store=file:$keystore")
}
Start-Svc 'rp' (Join-Path $outputPoc 'rp.jar') 8080 $rpArgs

# --- 3. 안내 ---------------------------------------------------------------
$baseUrl = if ($Demo) { "https://${DemoHostname}:8080" } else { 'http://localhost:8080' }
Write-Host ""
Write-Host "열기: $baseUrl" -ForegroundColor Cyan
Write-Host "서비스: RP(:8080), RSSP(:8090)  — TSA·OCSP·SAM·HSM 미사용(PAdES BASELINE-B)" -ForegroundColor DarkGray
if (-not $SkipBuild) {
    Write-Host "반출 : results/packages/whale/libs (프로파일 $AdesProfile), results/packages/whale/poc" -ForegroundColor DarkGray
}
if ($Demo) {
    Write-Host "  ⚠ IP 주소로 접속하면 WebAuthn 이 동작하지 않는다 (rpId 에 IP 사용 불가)." -ForegroundColor Yellow
    Write-Host "  ⚠ 태블릿에 runtime\pki\demo-tls\krdss-demo-root-ca.crt 가 설치되어 있어야 한다." -ForegroundColor Yellow
}
Write-Host "로그: Get-Content logs\whale-rp.log -Wait -Tail 40"
Write-Host "종료: pwsh scripts\poc\whale-down.ps1"
