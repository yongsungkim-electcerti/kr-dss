param(
    [string]$BaseUrl='http://localhost:18081',
    [string]$OutputDirectory='./api-test'
)
# 공개 API 전송 계약 시험. XML 서명·인증 경로 검증을 대신하지 않습니다.
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$root=(Resolve-Path -LiteralPath $OutputDirectory).Path
$xml=Join-Path $root 'received-tl.xml'
$sha=Join-Path $root 'received-tl.sha2'
$headers=@{Accept='application/vnd.etsi.tsl+xml'}
$response=Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/tl/kr-tl.xml" -Headers $headers -OutFile $xml -PassThru
Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/tl/kr-tl.sha2" -OutFile $sha | Out-Null
$bytes=[IO.File]::ReadAllBytes($sha)
if($bytes.Length -ne 32){throw 'Expected raw 32-byte SHA-256.'}
$expected=[BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
$hasher=[Security.Cryptography.SHA256]::Create()
try {$actual=[BitConverter]::ToString($hasher.ComputeHash([IO.File]::ReadAllBytes($xml))).Replace('-','').ToLowerInvariant()}
finally {$hasher.Dispose()}
if($expected -ne $actual){throw 'XML/hash mismatch. Publication may have changed; fetch both files again.'}
$etag=[string]$response.Headers['ETag']
if(-not $etag){throw 'ETag missing.'}
$conditionalStatus=0
try {
    $conditional=Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/tl/kr-tl.xml" -Headers @{Accept='application/vnd.etsi.tsl+xml';'If-None-Match'=$etag}
    $conditionalStatus=[int]$conditional.StatusCode
} catch {
    if($null -eq $_.Exception.Response){throw}
    $conditionalStatus=[int]$_.Exception.Response.StatusCode
    if($conditionalStatus -ne 304){throw}
}
if($conditionalStatus -ne 304){throw 'Expected 304 for unchanged publication. Check whether publication changed.'}
[pscustomobject]@{xml=$xml;httpStatus=[int]$response.StatusCode;sha256=$actual;hashBytes=$bytes.Length;etag=$etag;conditionalStatus=$conditionalStatus;tlTrustValidated=$false}
