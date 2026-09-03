<#
.SYNOPSIS
  output/certs 테스트 인증체인 재생성 — KISA RootCA → 4개 중간 CA → 최종개체.

.DESCRIPTION
  저장소의 인증서 발급 도구(`krdss cert`, tools/krdss-cli)로 체인 전체를 다시 만든다.
  기존 output/certs 는 output/certs.bak-<타임스탬프> 로 통째로 옮긴 뒤 새로 생성한다.

  체인 구조(자세한 설명은 output/certs/README.md):

    KISA RootCA (자가서명, RSA 4096)
     ├─ KISA CA            → KISA Trusted List Signer
     ├─ KISA Joint CA      → Joint Test User
     ├─ KISA Financial CA  → Financial Test User
     └─ KISA Test CA       → Test User

  ⚠ 전부 테스트·실증 전용이다. 운영 용도로 쓰지 않는다.

.PARAMETER Password
  생성되는 모든 PKCS#12 키스토어의 비밀번호. 기본 111111 (테스트 전용).

.PARAMETER SkipBuild
  krdss-cli 재빌드를 건너뛰고 이미 설치된 배포본으로 발급한다.

.EXAMPLE
  pwsh scripts\gen-certs.ps1
  pwsh scripts\gen-certs.ps1 -Password 111111
#>
param(
    [string]$Password = '111111',
    [switch]$SkipBuild,
    [string]$JavaHome = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$certsDir = Join-Path $root 'output\certs'

# --- Java 21 실행기 결정 (whale-startup.ps1 과 같은 규칙) --------------------
function Get-JavaMajor([string]$exe) {
    $jdkHome = Split-Path -Parent (Split-Path -Parent $exe)
    $release = Join-Path $jdkHome 'release'
    if (Test-Path $release) {
        $hit = Select-String -Path $release -Pattern '^JAVA_VERSION="?(\d+)' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($hit) { return [int]$hit.Matches[0].Groups[1].Value }
    }
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

function Resolve-JavaHome {
    $candidates = @()
    if ($JavaHome) { $candidates += (Join-Path $JavaHome 'bin\java.exe') }
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $onPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($onPath) { $candidates += $onPath }
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

$java = Resolve-JavaHome
$keytool = Join-Path (Split-Path -Parent $java) 'keytool.exe'

# --- 1. 기존 자료 백업 -----------------------------------------------------
Write-Host "테스트 인증체인 재생성" -ForegroundColor Cyan
$backup = $null
if (Test-Path $certsDir) {
    $backup = Join-Path $root ('output\certs.bak-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Move-Item -Path $certsDir -Destination $backup
    Write-Host "• 기존 자료 백업 → $(Split-Path -Leaf $backup)" -ForegroundColor Yellow
}
New-Item -ItemType Directory -Force -Path $certsDir | Out-Null

# README.md 는 발급 산출물이 아니라 구조 설명 문서다. 백업으로 딸려 가면 안 되므로 되돌린다.
if ($backup) {
    $backupReadme = Join-Path $backup 'README.md'
    if (Test-Path $backupReadme) {
        Copy-Item -Path $backupReadme -Destination (Join-Path $certsDir 'README.md')
        Write-Host "• README.md 유지" -ForegroundColor DarkGray
    }
}

# --- 2. 발급 도구 준비 -----------------------------------------------------
$installDir = Join-Path $root 'tools\krdss-cli\build\install\krdss-cli'
if (-not $SkipBuild) {
    Write-Host "• krdss-cli 빌드"
    & (Join-Path $root 'gradlew.bat') '--project-dir' $root ':tools:krdss-cli:installDist' '--console=plain' -q
    if ($LASTEXITCODE -ne 0) { throw "krdss-cli 빌드 실패 (exit=$LASTEXITCODE)" }
}
if (-not (Test-Path $installDir)) {
    throw "krdss-cli 배포본이 없습니다: $installDir  (-SkipBuild 없이 실행하세요)"
}
$cliClasspath = Join-Path $installDir 'lib\*'

# CLI 를 한 번 호출한다. 실패하면 즉시 멈춘다(부분 생성된 체인이 남지 않도록).
function Invoke-Cli {
    param([string[]]$CliArgs, [string]$What)
    $output = & $java '-cp' $cliClasspath 'com.electcerti.krdss.cli.KrDssCli' @CliArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        $output | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        throw "$What 실패 (exit=$LASTEXITCODE)"
    }
}

function New-Ca {
    param([string]$Name, [string]$Subject, [string]$Type, [int]$Days, [int]$KeySize,
          [string]$IssuerName)
    $out = Join-Path $certsDir $Name
    $cliArgs = @('cert', 'gen', '--type', $Type, '--subject', $Subject, '--out', $out,
                 '--key-alg', 'RSA', '--key-size', "$KeySize", '--days', "$Days")
    if ($IssuerName) {
        $cliArgs += @('--issuer-cert', (Join-Path $certsDir "$IssuerName.crt"),
                      '--issuer-key', (Join-Path $certsDir "$IssuerName.key"))
    }
    Invoke-Cli -CliArgs $cliArgs -What "$Name 발급"
    Write-Host "  ✓ $Name  ($Type, RSA $KeySize, ${Days}일)" -ForegroundColor Green
}

# --- 3. 체인 발급 ----------------------------------------------------------
Write-Host "• 루트 CA"
New-Ca -Name 'kisa-rootca' -Type 'ROOT' -Days 7300 -KeySize 4096 `
    -Subject 'CN=KISA RootCA,OU=Korea Internet & Security Agency,O=KISA,C=KR'

Write-Host "• 중간 CA 4종"
$subCas = @(
    @{ Name = 'kisa-ca';           Subject = 'CN=KISA CA,OU=Korea Internet & Security Agency,O=KISA,C=KR' }
    @{ Name = 'kisa-joint-ca';     Subject = 'CN=KISA_Joint_CA,OU=Joint,O=KISA,C=KR' }
    @{ Name = 'kisa-financial-ca'; Subject = 'CN=KISA_Financial_CA,OU=Financial,O=KISA,C=KR' }
    @{ Name = 'kisa-test-ca';      Subject = 'CN=KISA_Test_CA,OU=Test,O=KISA,C=KR' }
)
foreach ($ca in $subCas) {
    New-Ca -Name $ca.Name -Type 'SUB' -Days 3650 -KeySize 4096 -Subject $ca.Subject -IssuerName 'kisa-rootca'
}

Write-Host "• 최종개체 4종"
# 최종개체는 모두 중간 CA 가 발급한다. 루트가 최종개체를 직접 발급하지 않는 것이
# 실제 PKI 운영 형태이고, 신뢰목록(KR-TL)이 중간 CA 단위로 등재되기 때문이다.
$endEntities = @(
    @{ Name = 'kisa-tl-signer';       Ca = 'kisa-ca';           Alias = 'tl-signer'
       Subject = 'CN=KISA Trusted List Signer,OU=Korea Internet & Security Agency,O=KISA,C=KR'; Days = 1095 }
    @{ Name = 'kisa-joint-user';      Ca = 'kisa-joint-ca';     Alias = 'joint-user'
       Subject = 'CN=Joint_Test_User,OU=Joint,O=KISA,C=KR'; Days = 825 }
    @{ Name = 'kisa-financial-user';  Ca = 'kisa-financial-ca'; Alias = 'financial-user'
       Subject = 'CN=Financial_Test_User,OU=Financial,O=KISA,C=KR'; Days = 825 }
    @{ Name = 'kisa-test-cert';       Ca = 'kisa-test-ca';      Alias = 'test-user'
       Subject = 'CN=Test_User,OU=Test,O=KISA,C=KR'; Days = 825 }
)
foreach ($ee in $endEntities) {
    New-Ca -Name $ee.Name -Type 'EE' -Days $ee.Days -KeySize 2048 -Subject $ee.Subject -IssuerName $ee.Ca
}

# --- 4. 번들 구성 ----------------------------------------------------------
Write-Host "• 번들 구성"

# ca-chain.pem — 루트 + 중간 CA 전부. 검증 화면의 트러스트스토어 입력으로 그대로 쓴다.
$caChain = Join-Path $certsDir 'ca-chain.pem'
$caOrder = @('kisa-rootca') + ($subCas | ForEach-Object { $_.Name })
Set-Content -Path $caChain -Value ($caOrder | ForEach-Object {
    Get-Content (Join-Path $certsDir "$_.crt") -Raw }) -NoNewline -Encoding ascii
Write-Host "  ✓ ca-chain.pem (루트 + 중간 CA $($subCas.Count)장)" -ForegroundColor Green

# 최종개체별 풀체인 PEM — EE + 발급 CA + 루트
foreach ($ee in $endEntities) {
    $full = Join-Path $certsDir "$($ee.Name)-fullchain.pem"
    Set-Content -Path $full -Value (@($ee.Name, $ee.Ca, 'kisa-rootca') | ForEach-Object {
        Get-Content (Join-Path $certsDir "$_.crt") -Raw }) -NoNewline -Encoding ascii
}
Write-Host "  ✓ *-fullchain.pem ($($endEntities.Count)개)" -ForegroundColor Green

# 최종개체별 PKCS#12 — 개인키 + 발급 CA + 루트. 서명 화면의 인증서 입력.
$allP12 = Join-Path $certsDir 'kisa-all.p12'
foreach ($ee in $endEntities) {
    # 개별 키스토어가 담는 체인은 [EE, 발급 CA, 루트] 다. 서명 시 이 체인이 PDF 에 실린다.
    $chainPem = Join-Path $certsDir "$($ee.Ca)-and-root.pem"
    Set-Content -Path $chainPem -Value (@($ee.Ca, 'kisa-rootca') | ForEach-Object {
        Get-Content (Join-Path $certsDir "$_.crt") -Raw }) -NoNewline -Encoding ascii

    $p12 = Join-Path $certsDir "$($ee.Name).p12"
    Invoke-Cli -What "$($ee.Name).p12 생성" -CliArgs @(
        'cert', 'p12',
        '--cert', (Join-Path $certsDir "$($ee.Name).crt"),
        '--key', (Join-Path $certsDir "$($ee.Name).key"),
        '--chain', $chainPem, '--out', $p12,
        '--alias', $ee.Alias, '--password', $Password)

    # 통합 키스토어(kisa-all.p12)에도 같은 엔트리를 더한다.
    Invoke-Cli -What "kisa-all.p12 에 $($ee.Alias) 추가" -CliArgs @(
        'cert', 'p12',
        '--cert', (Join-Path $certsDir "$($ee.Name).crt"),
        '--key', (Join-Path $certsDir "$($ee.Name).key"),
        '--chain', $chainPem, '--out', $allP12,
        '--alias', $ee.Alias, '--password', $Password, '--append')

    Remove-Item $chainPem -Force
}
Write-Host "  ✓ 최종개체 키스토어 $($endEntities.Count)개 + kisa-all.p12 (비밀번호 $Password)" -ForegroundColor Green

# truststore.p12 — 신뢰 앵커(루트 + 중간 CA)만 담은 인증서 전용 키스토어.
#
# cert p12 는 개인키를 요구해 인증서 전용 스토어를 만들지 못하므로 keytool 로 만든다.
# keytool 은 키스토어 비밀번호를 6자 이상으로 강제하니 짧은 비밀번호로는 실패한다.
# (.NET 의 인증서 컬렉션 내보내기는 Java PKCS12 KeyStore 가 엔트리 0개로 읽어 쓸 수 없다.)
$trustStore = Join-Path $certsDir 'truststore.p12'
if ($Password.Length -lt 6) {
    Write-Warning "비밀번호가 6자 미만이라 truststore.p12 를 만들 수 없다(keytool 제약). ca-chain.pem 을 트러스트스토어로 쓰면 된다."
}
else {
    foreach ($name in $caOrder) {
        $importLog = & $keytool -importcert -noprompt -alias $name `
            -file (Join-Path $certsDir "$name.crt") `
            -keystore $trustStore -storetype PKCS12 -storepass $Password 2>&1
        if ($LASTEXITCODE -ne 0) {
            $importLog | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
            throw "truststore.p12 에 $name 추가 실패"
        }
    }
    Write-Host "  ✓ truststore.p12 (신뢰 앵커 $($caOrder.Count)장, 개인키 없음)" -ForegroundColor Green
}

# --- 5. 요약 ---------------------------------------------------------------
Write-Host ""
Write-Host "완료 — $certsDir" -ForegroundColor Cyan
Write-Host "  인증서/키 : $((Get-ChildItem $certsDir -Filter *.crt).Count) crt, $((Get-ChildItem $certsDir -Filter *.key).Count) key"
Write-Host "  키스토어   : $((Get-ChildItem $certsDir -Filter *.p12).Count) p12  (비밀번호 $Password)
  트러스트스토어 : truststore.p12 / ca-chain.pem"
Write-Host "  구조 설명  : output\certs\README.md"
