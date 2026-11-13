# TrustList API 연동 가이드

네이버 웨일 개발자용

신뢰목록 수신 · 버전 확인 · 등록

v1.0 · 2026.10.28

## 목차

| 구성 | 내용 |
| --- | --- |
| 1. API 구성 | 접속 주소, XML·해시 조회 API |
| 2. 신뢰목록 수용 | 수동 등록과 주기적 원격 등록 |
| 3. TL 조회·갱신 | 요청 헤더, ETag, 200·304 처리 |
| 4. TL 데이터 | 발행정보, 사업자·서비스, 인증서·상태 이력 |
| 5. 등록 조건 | 서명, 신뢰앵커, 버전, 갱신 기한 |
| 6. 연동 예제 | 파일 다운로드와 주기적 갱신 구현 |
| 7. 응답·오류 코드 | HTTP 상태와 오류 본문 |

## API 구성

웨일은 공개 TL API에서 신뢰목록을 수신합니다.

| 항목 | 내용 |
| --- | --- |
| 기본 주소 | http://localhost:18081 |
| TL XML | GET /tl/kr-tl.xml<br>서명된 신뢰목록 XML 원문을 반환합니다. |
| TL 해시 | GET /tl/kr-tl.sha2<br>XML 원문의 SHA-256을 바이너리 32바이트로 반환합니다. |
| 헤더 조회 | 두 경로 모두 HEAD를 지원합니다. |
| 인증 | 별도 인증 헤더 없이 호출합니다. |

## 신뢰목록 수용

ETag로 변경 여부를 확인하고, XML의 TSLSequenceNumber로 배포 버전을 비교합니다.

- **수동 등록**: TL XML을 다운로드해 웨일에 파일로 등록합니다.

- **원격 등록**: 웨일이 API를 주기적으로 호출해 새 TL을 수신하고 등록합니다.

- **버전 확인**: 등록 시 TSLSequenceNumber를 현재 버전과 비교합니다. 등록 조건을 통과한 최신 버전을 적용합니다.

## TL 조회·갱신

첫 요청은 XML과 ETag를 받고, 이후 요청은 저장한 ETag로 변경 여부를 확인합니다.

| 구분 | 계약 |
| --- | --- |
| Accept | application/vnd.etsi.tsl+xml 또는 */* |
| 200 OK | Content-Type: application/vnd.etsi.tsl+xml<br>본문: 서명된 XML 원문 |
| ETag | "sha256-<64자리 16진수>" |
| 조건부 요청 | If-None-Match에 저장한 ETag를 따옴표까지 전달합니다. |
| 304 Not Modified | 본문 없이 응답합니다. 현재 TL을 유지하고 NextUpdate를 확인합니다. |
| Cache-Control | no-cache, no-transform |

## TL 데이터

웨일은 TL에 등재된 인증서와 상태 이력을 PDF 서명 검증에 사용합니다.

| 구조 | 필드 | 사용 |
| --- | --- | --- |
| SchemeInformation | TSLSequenceNumber<br>ListIssueDateTime / NextUpdate | 배포 버전, 발행 시각, 갱신 기한을 확인합니다. |
| TrustServiceProviderList | 사업자 및 서비스 목록 | 검증 대상 인증서에 해당하는 서비스를 찾습니다. |
| ServiceInformation | 서비스 유형·인증서·상태·효력 시각 | 인증 경로를 연결하고 평가시각의 상태를 판단합니다. |
| ServiceHistory | 이전 상태와 효력 시각 | 평가시각에 유효한 상태 구간을 선택합니다. |
| ds:Signature | XML 서명·서명 인증서 | 신뢰앵커를 기준으로 TL 서명을 검증합니다. |

## PoC 데이터 값

이 배포본에 사용하는 서비스 유형·상태·확장 값입니다.

| 항목 | 값 |
| --- | --- |
| URI 접두사 | urn:example:kr-tl:poc:v1: |
| 서비스 유형 | 접두사 + service:ca / service:ocsp / service:tsa |
| 서비스 상태 | 접두사 + status:accredited / status:suspended / status:withdrawn |
| 확장 네임스페이스 | urn:example:kr-tl:poc:extensions:v1 |
| TrustDomain | joint (공동인증) / simple (간편인증) |
| TrustPointLevel | 0 (ROOT) / 1 (CA) |

## 등록 조건

검사를 통과하면 XML 원문과 ETag를 함께 저장하고 검증에 적용합니다.

| 검사 | 처리 |
| --- | --- |
| 서명·인증 경로 | samples/tl-root.pem을 신뢰앵커로 TL 서명과 인증 경로를 검증합니다. |
| 배포 버전 | 현재보다 높은 TSLSequenceNumber와 늦은 발행 시각을 확인합니다. |
| 동일 버전 | 동일 원문이면 유지합니다. 원문이 다르면 충돌로 처리합니다. |
| 이전 버전 | 현재보다 낮은 버전은 등록을 거부합니다. |
| 갱신 기한 | NextUpdate가 현재 시각 이후인지 확인합니다. |
| 확장·상태 이력 | critical 확장과 상태 이력을 해석합니다. 미지원 critical 확장은 거부합니다. |

## 파일 다운로드

XML, 응답 헤더, 해시 파일을 현재 폴더에 저장합니다.

- **PowerShell**: 줄 끝의 백틱(`)을 포함해 실행합니다.

- **해시 비교**: 출력한 두 해시를 비교합니다. 다운로드 중 TL이 변경돼 불일치하면 다시 수신합니다.

```powershell
$base = 'http://localhost:18081'
curl.exe --fail-with-body -D tl-headers.txt `
  -H 'Accept: application/vnd.etsi.tsl+xml' `
  -o received-tl.xml "$base/tl/kr-tl.xml"

curl.exe --fail-with-body `
  -o received-tl.sha2 "$base/tl/kr-tl.sha2"

$bytes = [IO.File]::ReadAllBytes(
  (Resolve-Path ./received-tl.sha2).Path)
[BitConverter]::ToString($bytes).Replace('-','')
(Get-FileHash ./received-tl.xml -Algorithm SHA256).Hash
```

## 주기적 갱신

acceptTl·saveTl은 웨일의 검증·저장 함수로 구현합니다. 호출 시 현재 TL의 NextUpdate를 확인합니다.

- **호출**: 웨일의 스케줄러에서 주기적으로 호출합니다. 첫 호출의 current는 null입니다.

- **검증·저장**: acceptTl은 등록 조건을 검사하고 실패 시 예외를 던집니다. saveTl은 검사 결과와 원문을 저장합니다.

```javascript
async function refreshTl(url, current, acceptTl, saveTl) {
  const headers = { Accept: 'application/vnd.etsi.tsl+xml' };
  if (current?.etag) headers['If-None-Match'] = current.etag;
  const r = await fetch(url, {
    headers, credentials: 'omit', cache: 'no-cache'
  });
  if (r.status === 304) return current;
  if (!r.ok) throw new Error('TL HTTP ' + r.status);
  const xml = new Uint8Array(await r.arrayBuffer());
  const accepted = await acceptTl(xml, current);
  const next = { ...accepted, xml, etag: r.headers.get('ETag') };
  await saveTl(next);
  return next;
}
```

## 브라우저 호출

웹 페이지에서 호출할 때는 실행 환경의 혼합 콘텐츠 정책을 확인합니다.

| 항목 | 설정 |
| --- | --- |
| CORS | Access-Control-Allow-Origin: * |
| 자격 증명 | credentials: omit |
| 메서드 | GET, HEAD |
| 조건부 요청 헤더 | If-None-Match |
| 노출 응답 헤더 | ETag |
| 웨일 확장 기능 | 호출 주소에 대한 확장 권한을 설정합니다. |

## 응답·오류 코드

오류 본문은 평문입니다.

| HTTP | 본문 | 의미 |
| --- | --- | --- |
| 200 | XML / 바이너리 해시 | 요청한 TL 또는 해시를 반환합니다. |
| 304 | 없음 | ETag가 일치합니다. |
| 404 | TL_NOT_PUBLISHED | 관리 시스템에서 제공본을 선택해야 합니다. |
| 406 | NOT_ACCEPTABLE | Accept를 TSL 미디어 유형 또는 */*로 지정합니다. |
| 405 | METHOD_NOT_ALLOWED | GET 또는 HEAD로 호출합니다. |
| 503 | TL_UNAVAILABLE | 저장된 발행본을 제공할 수 없습니다. |
| 500 | INTERNAL_ERROR | 서버 내부 오류입니다. |
