# 28. IF-07 신뢰목록 조회 API — Request / Response

작성일: 2026-10-07. 설계 문서만 작성하며 서버·뷰어 구현과 실제 통신 시험은 후속 개발에서 수행한다.

## 1. EU 참조와 설계 방향

EU TL은 고정 URL의 서명된 XML을 HTTP로 내려받는 방식이다. ETSI는 직접 접근 가능한 `.xml` 또는 `.xtsl` 주소, `application/vnd.etsi.tsl+xml` 매체 유형, 같은 경로의 `.sha2` 해시 제공을 규정한다. 해시는 변경 감지 보조 정보이며 TL 인증을 대신하지 않는다. [ETSI TS 119 612 V2.4.1 §6.1·6.2](https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/02.04.01_60/ts_119612v020401p.pdf)

따라서 IF-07의 **업무 조회는 GET**으로 설계한다. 별도 검색·POST·로그인·세션·PDF 업로드는 필요 없다. HEAD나 브라우저 CORS용 OPTIONS까지 EU 전체에서 금지한다는 뜻은 아니다. ETag/304와 아래 오류·캐시 계약은 KR-TL PoC의 HTTP 설계다.

### PDF 뷰어에서 참조할 EU URL

| 용도 | URL | 의미 |
|---|---|---|
| EU LOTL | `https://ec.europa.eu/tools/lotl/eu-lotl.xml` | 회원국 TL의 위치와 서명 인증 정보를 모은 목록 |
| 국가 TL 예: 독일 | `https://tl.bundesnetzagentur.de/TL-DE.xml` | 해당 국가의 서비스·신뢰정보 목록 |
| 사람이 보는 목록 | `https://eidas.ec.europa.eu/efda/trust-services/browse/eidas/tls` | 조회 웹 화면. 뷰어의 XML 입력 URL과 구별 |

EU LOTL 주소는 [유럽연합 공식 공고](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A52026XC01944)에서 확인했다. 독일 TL 주소는 [EU 공식 TL Browser의 독일 항목](https://eidas.ec.europa.eu/efda/trust-services/browse/eidas/tls/tl/DE/tsp/remove.5501b2683d9e2fb0.svg)에서 확인했다. 이번 조사 도구에서는 두 XML 원문을 직접 열지 못했으므로 실제 서버의 응답 헤더·ETag·CORS 지원은 확인하지 않았다.

EU 모드는 검증된 LOTL에서 국가 TL 주소와 서명 정보를 얻는 구조다. URL을 입력하는 것만으로 신뢰가 성립하지 않는다. [EU LOTL의 신뢰 갱신 설명](https://ec.europa.eu/tools/lotl/pivot-lotl-explanation.html)을 참조한다. KR PoC는 단일 KR-TL을 조회하고 [27](../pki/27-PoC-키파일-디렉터리-설계.md)의 KISA TL Root를 사전 신뢰 기준으로 쓴다. EU와 KR의 앵커를 혼용하지 않는다.

특정 상용 PDF 뷰어가 임의 EU/KR TL URL을 바로 등록할 수 있다고 가정하지 않는다. 이번 설계의 소비자는 프로젝트 PDF 뷰어 내부 검증 모듈과 이용기관 KR-DSS SDK다. 상용 뷰어 연동은 제품·버전 확인 후 별도 판단한다.

## 2. 책임과 URL

| 항목 | 계약 |
|---|---|
| 제공자 | Spring Boot WAS의 TL 제공 기능 |
| 소비자 | PDF 뷰어 문서검증 모듈, 이용기관 KR-DSS SDK |
| TL 내용 책임 | TL 발행기. WAS는 선택한 발행본의 바이트를 그대로 제공 |
| 수용 책임 | 소비자의 TL 검증 모듈. HTTP 성공과 신뢰 수용을 구별 |
| 인증 | 없음. 공개 조회이며 API 키·쿠키·mTLS를 요구하지 않음 |
| 전송 | 배포 주소는 HTTPS를 기본으로 제안. 로컬 PoC는 HTTP 가능 |
| 기준 주소 | `{baseUrl}/tl/kr-tl.xml` |
| 보조 주소 | `{baseUrl}/tl/kr-tl.sha2` |

`baseUrl`의 호스트·포트는 배포 구성에서 결정한다. 예: `http://localhost:<WAS포트>/tl/kr-tl.xml`. 경로에 `/v1`을 붙이거나 순번 쿼리를 요구하지 않는다. 형식 버전과 순번은 XML 내부 값이다. URL은 서버 파일 시스템 경로를 노출하지 않는다.

‘현재 제공본’은 관리 화면이 선택한 issuanceId의 파일이다. **최대 순번을 자동 선택하거나 정상 TL로 대체하지 않는다.** PoC에서는 하위 순번·오류 시각의 발행본도 같은 URL로 제공해야 한다. 게시 대상을 바꾸는 관리 동작은 IF-07의 GET과 분리한다.

## 3. IF-07-01 — TL XML 조회

### Request

```http
GET /tl/kr-tl.xml HTTP/1.1
Host: <WAS호스트>
Accept: application/vnd.etsi.tsl+xml
```

| 입력 | 필수 | 의미 |
|---|---|---|
| Path | 예 | `/tl/kr-tl.xml` 고정 |
| Query / Body | 없음 | 순번·사업자 ID·PDF 파일을 보내지 않음 |
| Accept | 아니오 | 권장값은 위 매체 유형. 생략·`*/*`도 수용 |
| If-None-Match | 아니오 | 보유 응답의 ETag. 최초 조회에는 보내지 않음 |

GET은 조회만 수행한다. 발행·재서명·파일 교체·캐시 초기화의 부수 효과를 만들지 않는다.

### Response — 200 OK

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.etsi.tsl+xml
ETag: "sha256-<서명된 XML 바이트의 SHA-256 소문자 hex 64자리>"
Cache-Control: no-cache, no-transform

<저장된 서명 XML 원문 바이트>
```

위 꺾쇠 표기는 설명용 자리표시자이며 실제 응답 문자열이 아니다. 본문은 `TrustServiceStatusList`를 루트로 하는 완전한 서명 XML이다. JSON envelope, Base64 문자열, HTML 다운로드 화면으로 감싸지 않는다. UTF-8 XML 선언과 일치하는 저장 바이트를 그대로 보낸다. 조회 시 재직렬화·들여쓰기·재서명하지 않는다.

ETag는 **순번이나 issuanceId가 아닌 실제 XML 해시**로 만든다. 동일 번호라도 내용이 다르면 바뀐다. 같은 바이트를 재제공하면 같다. PoC 기본 제공에는 본문 압축·변환을 적용하지 않아 해시 기준을 단순하게 유지한다.

`no-cache`는 저장 금지가 아니라 재사용 전 재확인 정책이다. 브라우저 HTTP 캐시와 검증 모듈의 ‘수용한 TL’ 저장소는 별개로 관리한다. 이 헤더로 TL의 NextUpdate를 연장할 수 없다. 이 설계는 Last-Modified를 제공하지 않으며 시간 조건 대신 ETag를 사용한다. 의미 오류 시연에 쓰는 XML 발행 시각을 HTTP 수정 시각으로 재사용하지 않는다.

### Request / Response — 변경 없음

```http
GET /tl/kr-tl.xml HTTP/1.1
Host: <WAS호스트>
Accept: application/vnd.etsi.tsl+xml
If-None-Match: "sha256-<보유 응답 해시>"
```

```http
HTTP/1.1 304 Not Modified
ETag: "sha256-<현재 응답 해시>"
Cache-Control: no-cache, no-transform
```

304에는 본문이 없다. 조건 비교는 HTTP의 If-None-Match 규칙을 따른다. [RFC 9110 §13.1.2·15.4.5](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.2)

304는 ‘서버 파일 불변’만 의미한다. 캐시의 서명 검증 상태·NextUpdate·현재 검증 시각을 다시 확인한다. 캐시가 없거나 만료되었으면 조건 없는 GET으로 전체 파일을 요청한다. 다시 받은 파일도 만료라면 거부한다. 브라우저가 304를 캐시와 결합해 200으로 전달할 수도 있으므로 모듈은 제공된 바이트에 동일 검증 절차를 적용한다.

## 4. IF-07-02 — 변경 감지용 해시 조회

소비자는 이 API를 생략하고 XML을 직접 GET해도 된다. 별도 JSON 메타 조회는 필수 경로에 추가하지 않는다.

```http
GET /tl/kr-tl.sha2 HTTP/1.1
Host: <WAS호스트>
Accept: application/octet-stream
```

```http
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Content-Length: 32
Cache-Control: no-cache, no-transform

<현재 제공 XML 원문 바이트의 SHA-256, raw 32바이트>
```

**raw 32바이트**는 이번 KR API의 표현 선택이다. hex 문자열 64글자나 Base64는 반환하지 않는다. EU 개별 서버의 해시 표현이 모두 같다고 가정하지 않으며 EU 연계 클라이언트는 실제 응답 형식을 별도 확인한다. 해시 보조 API에는 이번 기본 계약에서 조건부 조회를 두지 않는다.

XML과 해시는 같은 불변 발행본에서 계산해 함께 게시 대상으로 전환한다. 두 번의 GET 사이에 게시 전환이 일어나 해시가 맞지 않으면 재조회한다. 불일치만으로 서명 위조를 확정하지 않는다. 해시 조회 실패 시 XML을 직접 받아 검증한다. 해시가 같아도 TL 만료 확인을 생략하지 않는다.

## 5. HTTP 오류와 TL 검증 오류

| HTTP 응답 | 의미 | 소비자 처리 |
|---|---|---|
| 200 | 파일 전송 성공. TL의 의미적 유효성은 미확정 | 반드시 검증 후 적용 |
| 304 | XML 조건부 조회에서 파일 불변 | 캐시 상태·만료 확인 |
| 404 | 제공본 미선택·최초 미발행, 또는 경로 없음 | 신규 적용 없음. 기존 유효본만 사용 |
| 406 | 명시한 Accept에 응답 매체 유형이 허용되지 않음 | 클라이언트 설정 확인 |
| 405 | 지원하지 않는 변경 메서드 | 요청 방식 확인 |
| 503 | 선택한 파일 읽기 실패·일시 제공 불가 | Retry-After가 있으면 따르고 제한된 재시도 |
| 500 | 예상하지 못한 제공 오류 | 기존 유효본 유지, 무한 재시도 금지 |

오류 응답은 `Cache-Control: no-store`, `Content-Type: text/plain; charset=utf-8`로 제공한다. 본문은 `TL_NOT_PUBLISHED`, `TL_UNAVAILABLE`, `NOT_ACCEPTABLE`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR`처럼 짧은 진단 문자열을 사용한다. 클라이언트는 HTTP 상태를 우선 처리하고 오류 본문을 TL XML로 파싱하지 않는다. 503의 Retry-After 값은 서버 설정이며 특정 대기시간을 시나리오로 고정하지 않는다.

하위 순번·동일 번호 다른 내용·미래 발행·만료·상태 소급 등 **파일 내용 오류는 4xx로 바꾸지 않는다.** WAS는 선택된 파일을 200으로 제공하고 검증 모듈이 [26의 사유 코드](../../../common/kr-tl/26-EU-기준-이력-버전-모델.md)로 거부한다. GET 응답에 `trusted: true` 같은 판정값은 넣지 않는다. 현재 소비자 보유본에 따라 판정이 달라질 수 있기 때문이다.

## 6. 뷰어·이용기관의 적용 흐름

```text
설정된 TL URL
  → GET /tl/kr-tl.xml
  → XML 형식·서명·사전 신뢰앵커 확인
  → 순번·발행/만료 시각·서비스 이력 검사
  → 통과한 TL과 색인을 함께 교체
  → PDF 서명자의 경로·서비스 상태 판정에 활용
```

수신 후보와 마지막 수용본을 분리한다. 거부한 응답의 ETag를 받았다는 이유로 수용본 번호를 올리지 않는다. 조건부 요청은 원칙적으로 보유한 수용본의 ETag로 보낸다. 거부본은 진단 자료로만 둘 수 있다. 오류본이 계속 제공되더라도 기존 유효본은 유지하고, 그 유효기간이 끝나면 유효 TL 없음으로 전환한다.

뷰어 설정에는 TL URL과 신뢰 설정을 분리한다. KR-TL은 KISA TL Root를 사전 설정하고, 다운로드한 TL의 자기 주장만으로 새 앵커를 등록하지 않는다. EU LOTL을 직접 KR 서비스 TL로 파싱하거나 KISA 키로 검증하지 않는다.

TL 조회는 PDF 파일·서명값을 서버에 보내지 않는다. PDF 서명 무결성, 인증서 경로, OCSP, 서비스 상태 판정은 IF-08의 검증 모듈 책임이다. IF-07만 연결했다고 PDF 검증이 완성되는 것은 아니다.

조회 주기·자동 새로고침·시연 시각은 향후 PoC 시나리오에서 정한다. ‘지금 조회’는 동일 GET을 호출하며, 서버에 검증 결과를 보고하는 POST는 이번 IF-07 범위에 포함하지 않는다.

## 7. 브라우저와 Spring Boot 구현 시 확인

- 웹 기반 PDF 뷰어가 다른 origin에서 호출하면 공개 TL 경로에만 CORS를 설정한다. 자격 증명은 사용하지 않는다. `ETag`를 읽을 수 있도록 노출하고 조건부 헤더의 preflight를 지원한다. OPTIONS는 브라우저 프로토콜 보조이며 업무 API 추가가 아니다.
- HEAD는 GET과 같은 헤더를 제공하고 본문을 생략하는 보조 동작으로 허용한다. POST·PUT·PATCH·DELETE는 조회 경로에서 지원하지 않는다. 405에는 실제 지원 메서드를 Allow 헤더로 제공한다.
- Spring Boot 응답 계층이 TL 매체 유형을 HTML/XML 기본값으로 바꾸거나 서명 XML을 재직렬화하지 않게 한다. 리다이렉트·로그인 페이지 없이 고정 주소에서 직접 응답한다.
- 제공본 선택 포인터는 원자적으로 바꾸고 한 요청 안에서는 하나의 issuanceId를 고정해 헤더·본문이 섞이지 않게 한다.
- EU URL의 CORS·상용 뷰어 지원 여부는 아직 미검증이다. 네이티브 뷰어·SDK와 웹 뷰어의 통신 제약은 구분해 확인한다.

## 8. 호환성과 후속 검증

기존 관리용 `POST /api/admin/publish`를 IF-07로 대체하지 않는다. 신규 소비자는 본문의 XML 필드를 권위 있는 값으로 사용하고 HTTP ETag는 전송 최적화에만 사용한다. 내부 issuanceId는 수신 신뢰 판단에 필요하지 않으며 조회 URL의 필수 입력으로 노출하지 않는다. 순번별 과거본 조회·페이지 조회는 이번 최소 계약에 포함하지 않는다.

후속 개발에서 확인할 항목: 최초 GET의 바이트 일치, ETag/304, 같은 순번 다른 내용의 200 응답과 검증 거부, 하위 순번 제공과 기존본 유지, 만료 후 304 처리, 해시 조회 실패의 XML 직접 조회, 게시 전환 중 XML/해시 불일치 재조회, 브라우저 CORS다. 현재 문서의 Request/Response는 계약 예시이며 EU 서버나 로컬 WAS에서 실행한 시험 결과가 아니다.
