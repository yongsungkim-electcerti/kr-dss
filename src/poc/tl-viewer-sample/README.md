# KR-TL 수신 검증 샘플 (PDF 뷰어·이용기관용)

PDF 뷰어·이용기관 검증 모듈이 IF-07([설계 28](../../../docs/design/poc/tl-management/interfaces/28-IF07-TL-조회-API.md))로
TL을 받아 수행하는 **TL 관련 검증 단계만** 보여주는 정적 샘플이다. PDF 서명 무결성·가입자 인증서 경로·OCSP 검증은 하지 않는다.
빌드가 없고 외부 라이브러리를 쓰지 않는다(브라우저 DOMParser·WebCrypto만 사용, 폐쇄망 가능).

| 파일 | 내용 |
|---|---|
| `krtl-verifier.js` | 재사용 가능한 검증 모듈(ES module). `fetchTrustList`, `verifyTrustList`, `lookupService` |
| `index.html` | 설정·조회·단계별 결과·보유(수용) TL·서비스 조회 화면 |

## 실행

1. TL 관리 PoC를 띄우고 발행본을 제공본으로 선택한다: `gradlew.bat :poc:poc-kisa-tl:bootRun` → http://localhost:8081
2. 샘플을 다른 origin에서 제공한다(CORS 경로 확인). JDK 18 이상의 `jwebserver` 예:
   ```
   jwebserver -p 8095 -b 127.0.0.1 -d <저장소>/src/poc/tl-viewer-sample
   ```
   http://127.0.0.1:8095/index.html 을 연다. (8090은 RSSP PoC 포트이므로 피한다.)
3. 신뢰앵커에 `runtime/tl-management/pki/tl-signer/T-00-root/cert.pem`(KISA TL RootCA)을 넣고 ‘지금 조회’.

신뢰앵커는 서버에서 받지 않는다. 다운로드한 TL·KeyInfo의 자기 주장만으로 새 앵커를 등록하지 않는다.
설정과 수용본은 이 브라우저의 localStorage에만 저장된다(‘보유본 지우기’로 최초 수신 상태로 돌아간다).

## 조회 흐름

```text
보유(수용)본 ETag로 조건부 GET /tl/kr-tl.xml
  ├ 304 → 보유본을 현재 시각으로 다시 검증 → 유효하면 유지, 만료면 조건 없는 GET 1회
  ├ 200 → 받은 바이트 전체 검증 → 통과: 보유본 교체 / 실패: 거부, 기존 보유본 유지
  └ 404·503·네트워크 오류 → 기존 보유본 유지(만료면 유효 TL 없음)
200이면 /tl/kr-tl.sha2 를 받아 대조(불일치는 재조회 권고, 검증을 대신하지 않음)
```

거부한 응답의 ETag는 보관하지 않는다. 조건부 요청은 항상 수용본의 ETag로 보낸다.

## 검증 단계와 사유 코드

| 단계 | 확인 | 실패 사유 코드 |
|---|---|---|
| XML 형식 | TrustServiceStatusList 루트·Id | XML_INVALID |
| 서명 구조 | TS 119 612 Annex B.1.0: enveloped, Reference URI=#루트Id, Transform enveloped-signature·exc-c14n, CanonicalizationMethod exc-c14n | SIGNATURE_INVALID |
| 참조 다이제스트 | TL 본문·XAdES SignedProperties (exc-c14n, SHA-256) | SIGNATURE_INVALID |
| 서명값 | SignedInfo ECDSA/RSA-SHA256 | SIGNATURE_INVALID |
| 서명 인증서 결속 | xades:SigningCertificateV2 CertDigest = KeyInfo 서명 인증서 | SIGNATURE_INVALID |
| 서명자 체인 | KeyInfo(T-02·T-01) → 사전 설정 T-00, 유효기간, CA·keyCertSign, 서명자 EKU id-tsl-kp-tslSigning | SIGNER_UNTRUSTED |
| 목록 값 | 발행 시각 미래(허용 오차), NextUpdate 순서·6개월 상한, 만료 | ISSUE_TIME_IN_FUTURE, INVALID_UPDATE_INTERVAL, TL_EXPIRED |
| 보유본 대비 | 순번 역행, 같은 순번 다른 내용, 발행 시각 역행. 최초 수신이면 ‘비교 불가’ | SEQUENCE_ROLLBACK, SEQUENCE_CONFLICT, ISSUE_TIME_REGRESSION |
| 서비스 정보 | 상태 URI(프로파일 v1)·critical 확장 지원, 이력 순서, 보유본 대비 소급 변경·이력 삭제 | UNSUPPORTED_STATUS, UNSUPPORTED_CRITICAL_EXTENSION, INVALID_HISTORY, RETROACTIVE_STATUS_CHANGE |

사유 코드는 [설계 26](../../../docs/design/common/kr-tl/26-EU-기준-이력-버전-모델.md)의 제안 코드를 따르고, 서명·지원 범위 코드를 추가했다.

‘TL 서비스 조회’는 PDF 서명자 판정 중 TL 단계만 시연한다. 입력 인증서가 등재 서비스 자체(공개키·SKI 일치)이거나,
AKI로 찾은 직접 발급 CA가 등재된 경우 지정 시각의 상태(현재·이력 반열린 구간)를 보여준다.

## 한계

- 서명자 체인은 데모용 최소 경로 검사다. CRL/OCSP 폐기 확인, 이름 제약·정책 처리, 다중 경로 탐색을 하지 않는다.
- XSD 전체 검증을 하지 않는다. 지원 Transform은 enveloped-signature·exc-c14n뿐이다(InclusiveNamespaces 미지원).
- 서비스 조회는 레벨 0 Root까지의 경로 구성·가입자 인증서 서명 검증을 하지 않는다.
- 검증 기준 시각 입력은 확인용이며 시나리오 시계가 아니다.
