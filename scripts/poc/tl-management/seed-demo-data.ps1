<#
.SYNOPSIS
  TL 관리 PoC(:8081)에 시연 데이터(사업자·서비스·인증서)를 관리 API로 입력한다.

.DESCRIPTION
  PoC 키 세트(runtime/tl-management/pki, krtl-poc-pki-v1)의 TL 등재(★) 노드를 읽어 등록한다.
  - 사업자: providers/<ID>/provider.json 중 tlListed=true (JA·JB·SA·SB). UN(미등재 대조군)은 넣지 않는다.
  - 서비스: 각 사업자의 tlListed 노드. ROOT·CA → CA(레벨: manifest trustPointLevel 0=ROOT, 1=CA), OCSP, TSA.
    서비스 이름은 인증서 Subject CN, 인증서는 cert.pem 원문(개인키 불필요).
  - 같은 이름의 사업자·서비스가 이미 있으면 건너뛴다(재실행 안전). 기존 항목을 수정·삭제하지 않는다.
  - 입력 1건마다 새 초안 revision 이 생긴다. 관리 화면과 같은 API(expectedRevision 충돌 검사)를 쓴다.
  -Publish 를 주면 이어서 정상(BASELINE) 서명·발행 후 그 발행본을 IF-07 제공본으로 선택한다.
  발행본은 불변 보관되며 정상 기준 순번이 전진하므로, 화면에서 직접 발행해 보려면 생략한다.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/seed-demo-data.ps1
  powershell -ExecutionPolicy Bypass -File scripts/poc/tl-management/seed-demo-data.ps1 -Publish
#>
param(
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$PkiDir,
    # 인정 시작 시각. 생략하면 현재 시각
    [datetime]$StartingTime,
    [switch]$Publish
)
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if (-not $PkiDir) {
    . (Join-Path $PSScriptRoot 'local-data.ps1')
    $PkiDir = Join-Path $tlStorageRoot 'pki'
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
$api = "$BaseUrl/api/admin"

function Read-Json([string]$path) {
    return [IO.File]::ReadAllText($path, $utf8) | ConvertFrom-Json
}

# Windows PowerShell 5 는 charset 없는 JSON 응답과 문자열 본문의 한글을 깨뜨리므로 UTF-8 바이트로 직접 주고받는다.
function Invoke-Api([string]$method, [string]$path, $body) {
    $params = @{ Method = $method; Uri = "$api$path"; UseBasicParsing = $true }
    if ($null -ne $body) {
        $params.Body = $utf8.GetBytes(($body | ConvertTo-Json -Depth 10 -Compress))
        $params.ContentType = 'application/json; charset=utf-8'
    }
    try {
        $response = Invoke-WebRequest @params
    }
    catch {
        $detail = $_.ErrorDetails.Message
        if (-not $detail -and $_.Exception.Response) {
            $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream(), $utf8)
            $detail = $reader.ReadToEnd()
        }
        throw "$method $path 실패: $($_.Exception.Message) $detail"
    }
    $stream = $response.RawContentStream
    $stream.Position = 0
    $text = (New-Object IO.StreamReader($stream, $utf8)).ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

function Show-Warnings($result) {
    foreach ($w in @($result.warnings)) { if ($w) { Write-Host "    ⚠ $w" -ForegroundColor Yellow } }
}

function Get-Cn([string]$subject) {
    if ($subject -match '(?:^|,)\s*CN=([^,]+)') { return $Matches[1].Trim() }
    return $subject
}

# ---------------------------------------------------------------- 사전 확인
$manifestPath = Join-Path $PkiDir 'manifest.json'
if (-not (Test-Path $manifestPath)) {
    throw "PoC 키 세트가 없습니다: $PkiDir`n먼저 실행: scripts/poc/tl-management/gen-tl-pki.ps1"
}
$manifest = Read-Json $manifestPath
$nodes = @{}
foreach ($n in $manifest.nodes) { $nodes[$n.certificateId] = $n }

try {
    $state = Invoke-Api GET '/state' $null
}
catch {
    throw "관리 PoC($BaseUrl)에 연결할 수 없습니다. 먼저 실행: scripts/poc/tl-management/tl-admin-up.ps1`n$_"
}
if (-not $state.health.available) { throw "저장소를 쓸 수 없습니다: $($state.health.reason)" }
$draft = $state.draft

if (-not $StartingTime) { $StartingTime = Get-Date }
$start = $StartingTime.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

Write-Host "시연 데이터 입력 → $BaseUrl (키 세트 $($manifest.setId), 초안 r$($draft.revision), 인정 시작 $start)" -ForegroundColor Cyan

# ---------------------------------------------------------------- 사업자·서비스 등록
$added = @{ providers = 0; services = 0; skipped = 0 }
$providerFiles = Get-ChildItem (Join-Path $PkiDir 'providers') -Directory |
    ForEach-Object { Join-Path $_.FullName 'provider.json' } | Where-Object { Test-Path $_ } | Sort-Object

foreach ($file in $providerFiles) {
    $p = Read-Json $file
    if (-not $p.tlListed) {
        Write-Host "• $($p.name) ($($p.providerId)) : TL 미등재 대조군 — 넣지 않음" -ForegroundColor DarkGray
        continue
    }

    $provider = @($draft.providers) | Where-Object { $_.name -eq $p.name } | Select-Object -First 1
    if ($provider) {
        Write-Host "• 사업자 $($p.name) : 이미 있음 ($($provider.providerId))"
        $added.skipped++
    }
    else {
        $result = Invoke-Api POST "/providers?expectedRevision=$($draft.revision)" @{ name = $p.name }
        $draft = $result.draft
        $provider = @($draft.providers) | Where-Object { $_.name -eq $p.name } | Select-Object -First 1
        Write-Host "• 사업자 $($p.name) 등록 ($($provider.providerId)) → r$($draft.revision)" -ForegroundColor Green
        Show-Warnings $result
        $added.providers++
    }

    foreach ($ref in @($p.nodes) | Where-Object { $_.tlListed }) {
        $node = $nodes[$ref.certificateId]
        $name = Get-Cn $node.subject
        if (@($provider.services) | Where-Object { $_.name -eq $name }) {
            Write-Host "    - $name : 이미 있음"
            $added.skipped++
            continue
        }
        $type = if ($node.role -in 'ROOT', 'CA') { 'CA' } else { $node.role }
        $level = $null
        if ($type -eq 'CA') { $level = if ($node.trustPointLevel -eq 0) { 'ROOT' } else { 'CA' } }
        $pem = [IO.File]::ReadAllBytes((Join-Path $PkiDir (Join-Path $node.dir 'cert.pem')))
        $body = @{
            type               = $type
            name               = $name
            domain             = $p.domain
            level              = $level
            statusStartingTime = $start
            certificates       = @([Convert]::ToBase64String($pem))
        }
        $result = Invoke-Api POST "/providers/$($provider.providerId)/services?expectedRevision=$($draft.revision)" $body
        $draft = $result.draft
        $provider = @($draft.providers) | Where-Object { $_.providerId -eq $provider.providerId } | Select-Object -First 1
        $levelText = if ($level) { " · 레벨 $level" } else { '' }
        Write-Host "    - $name ($type$levelText, $($ref.certificateId)) 등록 → r$($draft.revision)" -ForegroundColor Green
        Show-Warnings $result
        $added.services++
    }
}

Write-Host ""
Write-Host "등록: 사업자 $($added.providers) · 서비스 $($added.services) · 건너뜀 $($added.skipped) · 현재 초안 r$($draft.revision)" -ForegroundColor Cyan

# ---------------------------------------------------------------- 발행·제공 (선택)
if ($Publish) {
    $plan = Invoke-Api GET '/issuance/plan?purpose=BASELINE' $null
    if (-not $plan.signerReady) { throw "서명 키가 준비되지 않았습니다: $($plan.signerDetail)" }
    foreach ($d in @($plan.diagnostics)) {
        if ($d) { Write-Host "  ⚠ 사전 검사: $($d | ConvertTo-Json -Compress)" -ForegroundColor Yellow }
    }
    $request = @{
        requestId             = [guid]::NewGuid().ToString()
        expectedDraftRevision = $draft.revision
        purpose               = 'BASELINE'
        sequenceNumber        = $plan.recommendedSequence
    }
    $issued = Invoke-Api POST '/issuance/issuances' $request
    $m = $issued.manifest
    Write-Host "• 서명·발행: $($m.issuanceId) · 목적 $($m.purpose) · 순번 $($m.sequenceNumber)" -ForegroundColor Green

    $publication = Invoke-Api GET '/issuance/publication' $null
    $selected = Invoke-Api POST '/issuance/publication' @{
        expectedPublicationRevision = $publication.publicationRevision
        issuanceId                  = $m.issuanceId
    }
    Write-Host "• 제공본 선택: $($selected.servedIssuanceId) (제공 revision $($selected.publicationRevision))" -ForegroundColor Green
    Write-Host "  IF-07: $BaseUrl/tl/kr-tl.xml , $BaseUrl/tl/kr-tl.sha2"
}

Write-Host "관리 화면: $BaseUrl/#providers"
