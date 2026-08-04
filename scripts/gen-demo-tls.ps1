<#
.SYNOPSIS
  태블릿·QR 시연용 HTTPS 인증서를 생성한다 (데모 Root CA → 서버 인증서).

.DESCRIPTION
  WebAuthn 은 보안 컨텍스트(HTTPS)에서만 동작하고, rpId 로 IP 주소를 쓸 수 없다.
  따라서 PC 를 호스트명(기본 sol-pc)으로 HTTPS 서비스해야 태블릿에서 시연할 수 있다.

  생성물(모두 certs/ — .gitignore 대상):
    demo-root-ca.p12          데모 Root CA 키스토어
    krdss-demo-root-ca.crt    ★ PC·태블릿 신뢰 저장소에 설치할 Root CA 인증서
    demo-tls.p12              서버 키스토어 (application-demo.yml 이 참조)

.PARAMETER Hostname
  서버 호스트명. application-demo.yml 의 krdss.rp.mode1.rp-id 와 반드시 같아야 한다.

.EXAMPLE
  pwsh scripts\gen-demo-tls.ps1
  pwsh scripts\gen-demo-tls.ps1 -Hostname sol-pc -Force
#>
[CmdletBinding()]
param(
    [string]$Hostname = 'sol-pc',
    [string]$StorePassword = 'changeit',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$certDir  = Join-Path $repoRoot 'certs'
$rootP12  = Join-Path $certDir 'demo-root-ca.p12'
$rootCrt  = Join-Path $certDir 'krdss-demo-root-ca.crt'
$serverP12 = Join-Path $certDir 'demo-tls.p12'

# --- keytool 확인 (JAVA_HOME 우선, 없으면 PATH) ---
$keytool = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\keytool.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\keytool.exe'
} else {
    (Get-Command keytool -ErrorAction SilentlyContinue)?.Source
}
if (-not $keytool) { throw 'keytool 을 찾을 수 없습니다. JDK 를 설치하거나 JAVA_HOME 을 설정하세요.' }

if (-not (Test-Path $certDir)) { New-Item -ItemType Directory -Path $certDir | Out-Null }

if ((Test-Path $serverP12) -and -not $Force) {
    Write-Host "이미 존재합니다: $serverP12 (다시 만들려면 -Force)" -ForegroundColor Yellow
    exit 0
}
foreach ($f in @($rootP12, $rootCrt, $serverP12)) { if (Test-Path $f) { Remove-Item $f -Force } }

# --- SAN 구성: 호스트명 + mDNS + localhost + 실제 LAN IP ---
$sanEntries = @("dns:$Hostname", "dns:$Hostname.local", 'dns:localhost', 'ip:127.0.0.1')
$lanIps = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } |
    Select-Object -ExpandProperty IPAddress -Unique
foreach ($ip in $lanIps) { $sanEntries += "ip:$ip" }
$san = $sanEntries -join ','
Write-Host "SAN: $san" -ForegroundColor DarkGray

$tmpCsr   = Join-Path $certDir '_server.csr'
$tmpCrt   = Join-Path $certDir '_server.crt'
$tmpChain = Join-Path $certDir '_chain.crt'

try {
    # 1) 데모 Root CA (자가서명, CA:true)
    & $keytool -genkeypair -alias root -keyalg RSA -keysize 3072 -validity 3650 `
        -dname "CN=KR-DSS Demo Root CA,OU=PoC,O=ElectCerti,C=KR" `
        -ext 'bc:c=ca:true,pathlen:0' -ext 'ku:c=keyCertSign,cRLSign' `
        -keystore $rootP12 -storetype PKCS12 -storepass $StorePassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Root CA 생성 실패' }

    & $keytool -exportcert -alias root -rfc -file $rootCrt `
        -keystore $rootP12 -storepass $StorePassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Root CA 인증서 추출 실패' }

    # 2) 서버 키쌍 + CSR
    & $keytool -genkeypair -alias $Hostname -keyalg RSA -keysize 2048 -validity 825 `
        -dname "CN=$Hostname,OU=PoC,O=ElectCerti,C=KR" `
        -keystore $serverP12 -storetype PKCS12 -storepass $StorePassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '서버 키쌍 생성 실패' }

    & $keytool -certreq -alias $Hostname -file $tmpCsr `
        -keystore $serverP12 -storepass $StorePassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'CSR 생성 실패' }

    # 3) Root CA 가 서버 인증서 발급 (SAN·EKU 포함)
    & $keytool -gencert -alias root -infile $tmpCsr -outfile $tmpCrt -rfc -validity 825 `
        -ext "san=$san" -ext 'eku=serverAuth' -ext 'bc=ca:false' `
        -keystore $rootP12 -storepass $StorePassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '서버 인증서 발급 실패' }

    # 4) 체인(서버+Root)을 서버 키스토어에 반입
    Get-Content $tmpCrt, $rootCrt | Set-Content $tmpChain -Encoding ascii
    & $keytool -importcert -alias root -file $rootCrt -noprompt `
        -keystore $serverP12 -storepass $StorePassword | Out-Null
    & $keytool -importcert -alias $Hostname -file $tmpChain -noprompt `
        -keystore $serverP12 -storepass $StorePassword | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '서버 인증서 체인 반입 실패' }
} finally {
    foreach ($f in @($tmpCsr, $tmpCrt, $tmpChain)) { if (Test-Path $f) { Remove-Item $f -Force } }
}

Write-Host ''
Write-Host "생성 완료 (호스트명: $Hostname)" -ForegroundColor Green
Write-Host "  서버 키스토어 : $serverP12"
Write-Host "  Root CA 인증서: $rootCrt"
Write-Host ''
Write-Host '다음 단계' -ForegroundColor Cyan
Write-Host "  1) PC 신뢰 저장소 설치(관리자 PowerShell):"
Write-Host "     Import-Certificate -FilePath '$rootCrt' -CertStoreLocation Cert:\LocalMachine\Root"
Write-Host "  2) 태블릿: $rootCrt 를 전송해 '신뢰할 수 있는 자격증명(CA 인증서)' 으로 설치"
Write-Host "  3) 기동: pwsh scripts\poc-up.ps1 -Mode mode1 -Demo"
Write-Host "  4) 접속: https://$Hostname`:8080   (IP 주소로 접속하면 WebAuthn 이 동작하지 않는다)"
