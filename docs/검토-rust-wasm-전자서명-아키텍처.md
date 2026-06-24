# KR-DSS 전자서명 아키텍처 검토 — Rust · WASM · WebAuthn · TEE · EUDI

> 작성 맥락: "Rust로 KR-DSS-API(sign/issue/verify)를 만들고 KR-DSS-WEB을 WebAssembly로 만들어
> WebAuthn으로 인증서발행·서명·검증이 가능한가, 서명은 AdES 규격"이라는 검토 요청에서 출발한
> 일련의 설계 논의를 정리한 문서. AdES = ETSI EN 319 1xx / eIDAS 정합 전자문서 서명.

---

## 0. TL;DR (핵심 결론)

1. **Rust로 AdES 구현**: CAdES/JAdES/PAdES는 현실적, **XAdES·HAdES·KR-TL은 비권장**(XML C14N 성숙 라이브러리 부재). 기존 Java/EU DSS 6.1을 코어로 유지하는 편이 안전.
2. **WASM 빌드**: 가능. 단 WASM은 키 저장소가 없어, 키는 WebAuthn·HSM·TEE·토큰 중 하나에 있어야 함.
3. **WebAuthn으로 직접 AdES 서명**: ❌ 구조적으로 불가(임의 데이터 서명 불가). WebAuthn은 항상
   `Sign(authData ‖ SHA256(clientDataJSON))`만 서명 → 표준 CMS/AdES 검증과 충돌.
4. **"해시만 서명 + 서버 AdES 조립"** 패턴 자체는 정석(EU DSS 3-step / CSC API). 단 그 raw 서명을
   **WebAuthn 키가 아니라** 임의서명 가능한 키(HSM·TEE·PRF로 푼 소프트키)가 해야 표준 정합.
5. **브라우저 WASM 단독으로 OS 보안 API 전자서명은 불가**. OS TEE로 표준 서명하려면 **네이티브
   컴포넌트가 키를 다루고 WASM은 키 외 연산만** 맡는 분담이 유일. = EUDI 네이티브 Local WSCD 모델.
6. **유럽(eIDAS/EUDI) 결론과 수렴**: FIDO는 "서명키"가 아니라 "원격 QSCD 활성화 인증"으로 사용.
   키는 서버 HSM 또는 단말 SE/TEE에, **서버가 표준 AdES 조립**. WSCD/WSCA 추상화로 원격·로컬 모두 허용.
7. **최종 권고**: **KR-WSCD 추상화 + 2-트랙(A-2 원격서명 + C 로컬 TEE)**. 비표준 경로(WebAuthn-extension /
   경로 B)는 폐기. 한국 논문(extension 방식)도 배제 결정.

---

## 1. Rust로 AdES API 구현 가능성

기존: **Java 21 + EU DSS 6.1**(ETSI 전 규격 커버하는 사실상 유일 성숙 레퍼런스). Rust엔 대응 통합
라이브러리 없음 → RustCrypto 부품 조립 방식.

| 영역 | Rust 가용성 | 난이도 | 비고 |
|---|---|---|---|
| 해시/RSA/ECDSA/EdDSA | `sha2`,`rsa`,`p256`,`ecdsa`,`ed25519` | 낮음 | 성숙 |
| X.509/CSR/경로검증 | `x509-cert`,`spki`,`der`,`rasn` | 중 | RFC5280 정책검증 직접 부담 |
| **CAdES**(CMS) | `cms`,`rasn` | 중 | AdES 속성 수작업 |
| **JAdES**(JWS) | `jose`/직접 | 중 | 수월 |
| **PAdES**(PDF) | `lopdf`+CMS | 중상 | 증분서명·ByteRange 직접 |
| **XAdES**(XMLDSig) | ⚠ 취약 | 높음 | **C14N 성숙 구현체 부재 = 최대 약점** |
| **HAdES**(HWP) | 없음 | 높음 | HWP 파싱 + XAdES류 |
| RFC3161 TSP | `rasn`/`cms` | 중 | T/LT/LTA 필수 |
| **KR-TL**(TS 119 612) | XML 기반 | 높음 | C14N 문제 재발 |

→ **CAdES/JAdES/PAdES는 Rust 가능, XAdES/HAdES/KR-TL은 Java/DSS 유지 권장.**

---

## 2. WASM 빌드

Rust→WASM(`wasm-bindgen`/`wasm-pack`)은 강점 영역. RustCrypto 순수 Rust라 WASM 동작. 단 **WASM 내
키 취급은 메모리·XSS 위험** → 키는 외부(WebAuthn/HSM/TEE/토큰)에 둬야 함.

---

## 3. WebAuthn의 구조적 한계

- **임의 데이터 서명 불가**: 서명 입력이 항상 `authenticatorData ‖ SHA256(clientDataJSON)`로 고정.
  문서 해시는 `challenge`에만 넣을 수 있음.
- **X.509 인증서 미발행**: 자격증명은 COSE 키쌍일 뿐. attestation은 "인증기 모델" 증명이지 사용자
  신원 인증서가 아님 → 인증서 발행은 **별도 CA/RA** 필요.
- **키 비추출 + 알고리즘 제한**(ES256/RS256/EdDSA; KCDSA/SEED 불가).

### 3.1 "해시만 서명 + 서버 AdES 조립" 패턴 (정석이나 WebAuthn과 충돌)

- 이 분리는 EU DSS 3-step(`getDataToSign→signDigest→signDocument`) = CSC API 모델. **정답**.
- 정정: 서명 대상은 "문서 해시"가 아니라 **`SignedAttributes`의 해시**.
  `signedAttrs{messageDigest=H(문서), signingTime, signing-certificate-v2, ...}` → `DTBS=DER(signedAttrs)`
  → `서명값=Sign(sk, H(DTBS))`. 서명 인증서가 **서명 전에 확정**돼야 함(2-라운드트립).
- **충돌**: WebAuthn은 `H(DTBS)`가 아니라 `authData‖H(clientData)`를 서명 → 표준 검증기(EU DSS)는
  `verify(pub, sig, H(signedAttrs))`로 검사 → **항상 실패**. CMS엔 서명 입력을 재정의할 자리가 없음.

### 3.2 결론: 어떤 키로 raw 서명하느냐가 갈림길

| 서명 디바이스 | 해시만 서명 + 서버 조립 | 표준 검증 |
|---|---|---|
| PKCS#11 토큰/스마트카드 | ✅ | ✅ |
| 원격 HSM/QSCD(CSC) | ✅ | ✅ |
| WebAuthn PRF로 푼 소프트키 | ✅(WASM raw 서명) | ✅ |
| **WebAuthn 자격증명 키 자체** | ⚠ | ❌ |

---

## 4. authenticatorData vs FIDO MDS (역할 구분)

- **authenticatorData**: 인증기가 **매 ceremony마다 생성하는 런타임 바이트**
  (`rpIdHash·flags·signCount·[attestedCredentialData]·[extensions]`). signCount/flags 가변 →
  **재구성·예측 불가**, 서명 시점에 **캡처해 동봉**해야 함. MDS에서 가져올 수 없음.
- **FIDO MDS**: 인증기 **모델 카탈로그**(AAGUID 조회). attestation 루트, 모델 속성·인증등급·폐기상태.
  → **등록(create) 시 attestation 검증**에만 쓰임(issue 신뢰근거). 개별 서명값·사용자 공개키는 안 줌.

| 필요 항목 | 출처 |
|---|---|
| authenticatorData / clientDataJSON | 서명·등록 응답 캡처 (MDS ❌) |
| 사용자 공개키 | 등록 응답 attestedCredentialData (MDS ❌) |
| attestation 루트·모델속성·폐기상태 | **MDS ✅** |

---

## 5. (검토 후 폐기) 한국 논문 — Cho & Lee 2019

**"FIDO2 CTAP을 활용한 전자서명 방법"** (고려대, 정보보호학회논문지 29(5))

- 아이디어: **authData extension을 운반채널**로 사용. 등록 시 사용자 X.509를, 서명 시 사용자
  개인키 서명값을 extension에 실어 전송.
- **결정적 한계**: 논문 스스로 "실제 인증서 전자서명을 연산하지 않고 **임의의 정적 데이터**로 진행".
  → 증명한 것은 "브라우저가 extension을 깨지 않고 전달한다"는 **전송 가능성뿐**.
- 현실 장벽: **커스텀 인증기/CTAP 확장 필요**(상용 패스키 불가), 표준 브라우저가 커스텀 확장 입력을
  전달 안 할 수 있음.
- **결정: 이 extension 방식은 향후 전면 배제.**

---

## 6. (채택) KR-AdES-WA 프로파일 — 검증 시 authenticatorData 전송

WebAuthn 키를 굳이 서명키로 쓰는 경로(경로 B)를 자체 생태계 한정으로 구현하는 규격.

### 6.1 구조
- 컨테이너: **CAdES-shape(CMS SignedData)** — PAdES PDF 임베딩 포섭.
- ceremony 데이터: **`unsignedAttrs`에 authData·clientDataJSON 동봉**
  (signedAttrs에 넣으면 순환참조; unsignedAttrs라도 WebAuthn 서명이 두 값을 무결성 보호).
- 결속: **`challenge = H(DTBS)`, `DTBS = DER(signedAttrs)`** — 이 결속이 안전성의 전부.

### 6.2 OID 체계 (검증기 자동 분기용)
```
id-krdss        ::= { 1 3 6 1 4 1 <PEN> }      -- IANA PEN 또는 KISA 국가 arc
id-krdss-cp-webauthn      ::= { id-krdss 1 1 }  -- (A) 인증서 certificatePolicies에 삽입
id-krdss-attr-waAssertion ::= { id-krdss 2 1 }  -- (B) unsignedAttr: authData/clientData 운반
id-krdss-sp-webauthn      ::= { id-krdss 3 1 }  -- (C) signedAttr: 서명 자기기술(보조)
id-krdss-attr-waAttestation ::= { id-krdss 2 2 } -- (선택) attestation 동봉
```
발급 시 인증서에 `certificatePolicies=id-krdss-cp-webauthn` + `keyUsage=nonRepudiation` 삽입,
`subjectPublicKey = WebAuthn 자격증명 공개키`(등록 attestation을 MDS로 검증 후 결속).

### 6.3 unsignedAttr 데이터 모델
```asn1
KrWebAuthnAssertion ::= SEQUENCE {
    version INTEGER(1),
    authenticatorData OCTET STRING,    -- raw
    clientDataJSON    OCTET STRING,    -- raw, 재직렬화 금지
    coseAlg           INTEGER,         -- -7=ES256, -257=RS256, -8=EdDSA
    aaguid            OCTET STRING OPTIONAL,
    credentialId      OCTET STRING OPTIONAL }
```

### 6.4 검증기 분기 + 7단계
```
[Step0 라우팅] cert.certificatePolicies ∋ id-krdss-cp-webauthn → 전용경로, 아니면 표준 ETSI
[A] 1.cert추출 2.signingInput=authData‖SHA256(clientDataJSON)
    3.Verify(cert.pub, signature, signingInput)   (ES256: r‖s→DER 변환)
[B] 4.DTBS=DER(signedAttrs) 5.cd.type=="webauthn.get"
    6.base64url(cd.challenge)==H(DTBS)            ← 문서 결속
[C] 7.rpIdHash==SHA256(rpId), flags.UP==1(UV==1 요구시)  (signCount는 선택)
[D] messageDigest==H(문서), signing-certificate-v2↔cert, KR-TL 경로검증, T/LT/LTA TS
    → TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED
```
**상호운용 비용**: 서명이 `authData‖H(clientData)` 위에 있어 **표준 ETSI 검증기는 무효 판정**.
KR-DSS 전용 검증기에서만 유효 → 국가 프로파일 확장으로 명시 + 표준 트랙(A-2)과 2-트랙.

### 6.5 attestation 매 서명 동봉 — 장단점
- **정정**: WebAuthn은 서명(get) 때 attestation을 새로 안 만듦 → "매번 전송"=등록 attestation 재첨부.
- 장점: 문서 단독 출처검증, 인증등급 증거화, 취약 발급절차 이중방어, AAGUID 가시성.
- 단점: **신선도 착시(등록시점 정적)**, 용량과다(수 KB), 프라이버시·추적성, 인증서와 중복,
  **상용/동기화 패스키는 attestation=none 흔함**, LTA 시 MDS 스냅샷 보존 부담.
- **권고**: 기본 비채택. 발급 시 1회 검증+인증서 결속 유지. 필요한 고보증 트랙만 **정책 OID로 게이트**,
  가능하면 **압축참조**(AAGUID·등급·mdsSnapshotId·H(attObj)·등록 TS)만 동봉.

---

## 7. 유럽 사례 (eIDAS / EUDI) — 우리 결론과 수렴

### 7.1 원격서명 모델 + FIDO의 역할
- 적격전자서명(QES)은 **원격서명**이 주류: 적격 인증서·개인키는 **서버 QSCD(HSM)** 에 보관,
  AdES도 서버 조립. 단말은 인증만.
- **SCAL2**(Sole Control Assurance Level 2) + **SAM**(Signature Activation Module) + **SAD**(Activation Data).
- **FIDO = 원격 QSCD 활성화 인증 수단**(SAD 생성 기여). **FIDO 키로 문서를 서명하지 않음.**
- 표준 스택: **CEN EN 419 241-1/-2**(원격 QSCD/SAM), **EN 419 221-5**(HSM), **ETSI TS 119 431-1/-2**,
  **ETSI TS 119 432**(원격서명 프로토콜), **CSC API v2.x**, **EN 319 142**(PAdES).

### 7.2 EUDI Wallet 구축·배포 모델
- EU는 "앱 하나"를 만들지 않음 → **공통규칙(Toolbox) + 오픈소스 레퍼런스 + 테스트베드**.
  - eIDAS 2.0(Reg. (EU) 2024/1183): 각 회원국 **2026년 말까지 ≥1 지갑** 제공.
  - **ARF(v2.8) + 31개 implementing acts + ETSI/CEN 표준 + 가이드라인**, EU Digital Identity
    Cooperation Group 조율.
  - 오픈소스 레퍼런스 구현(GitHub `eu-digital-identity-wallet`), 대규모 파일럿(LSP), 인증체계 정비 중.
  - **회원국/사업자가 각자 지갑 구현 → 앱스토어 배포**(네이티브 앱).

### 7.3 WSCD/WSCA — 키 아키텍처 4유형 (우리 경로와 1:1)
**WSCD**(Wallet Secure Cryptographic Device)=탬퍼/복제방지 보안HW, **WSCA**(Wallet Secure
Cryptographic Application)=WSCD 위에서 critical asset 관리하는 앱.

| EUDI WSCD 유형 | WSCA 구현 | 호출 인터페이스 | 우리 경로 |
|---|---|---|---|
| **Remote WSCD** | HSM 펌웨어 모듈 | KMIP, PKCS#11, CSC/TS119432 | **A-2 원격** |
| **Local WSCD** (eSE/eSIM) | JavaCard 애플릿 | Open Mobile API, APDU | C(SE형, 고보증) |
| **Local External WSCD** | 외부 스마트카드 애플릿 | APDU/PKCS#11 | 보안토큰 |
| **Local Native WSCD** | OS 통합(별도 WSCA 불필요) | 플랫폼 네이티브 API | **C(TEE)** |

ARF: "지갑은 로컬 또는 원격 서명 앱으로 서명값 생성 가능해야 함" → **로컬·원격 둘 다 공식 인정**
= 우리의 A-2 + C 2-트랙이 곧 EU 표준 구조. 보증등급: **QES=SE/HSM, AdES=TEE**(순수 TEE는 인증 천장).

### 7.4 WSCA 구조·기능·인터페이스(SCI)
- **기능**: 키쌍 생성, proof-of-possession, 키/민감데이터 안전삭제, cryptographic binding(WUA·PID 결속),
  사용자 인증 확인 후 암호연산, 지갑단위 키 격리, 디지털 서명 실행.
- **SCI(Secure Cryptographic Interface)**: Wallet Instance ↔ WSCA 통신 인터페이스. ARF는 "WSCA가
  Wallet Instance와 직접 인터페이스"라고만 하고 **프로토콜을 단일 표준으로 못박지 않음**(Topic P =
  표준화 필요성 논의 단계). WSCD 유형별로 PKCS#11/KMIP/JavaCard/Open Mobile API/네이티브 API.
- Android=Keystore/StrongBox-Keymaster, iOS=Keychain/CryptoKit/Secure Enclave.

### 7.5 브라우저 → WSCA 직접 호출 가능성
- **Wallet Instance는 네이티브 앱**이며 WSCA를 SCI(네이티브/APDU/원격 프로토콜)로 호출.
  **브라우저/웹앱이 WSCA를 직접 호출하는 표준은 ARF에 없음**(웹 접근성은 Topic P 범위 밖).
- 즉 웹은 **네이티브 Wallet Instance를 경유**(딥링크/Native Messaging/앱 핸드오프)해야 WSCA에 도달.
  → "브라우저 WASM이 OS 보안 API/WSCA를 직접 못 부른다"는 우리 결론과 일치.

---

## 8. 브라우저 WASM과 OS 보안 API — 무엇이 되고 안 되나

| 브라우저 WASM이 부를 수 있는 것 | OS TEE 접근 | 임의서명 | 결과 |
|---|---|---|---|
| Web Crypto(`crypto.subtle`) | ❌ 소프트키 | ✅ | 표준 AdES 가능, **HW보호·attestation 없음** |
| WebAuthn(`navigator.credentials`) | ✅ 유일한 표준 OS-TEE 경로 | ❌ | 비표준(경로 B) |
| OS keystore 직접 | — | — | **그런 Web API 없음** |

→ **표준 AdES + OS TEE를 동시에 = 네이티브 컴포넌트 필수.** WASM은 키 외 연산만.
WASI(브라우저 밖) 런타임이면 호스트가 TEE host-function 노출 가능하나 결국 네이티브 호스트 전제.

---

## 9. 경로 비교 (종합)

| 경로 | 키 위치 | 서명 표준성 | 키보호 | 설치부담 | QES |
|---|---|---|---|---|---|
| **A-1** PRF 소프트키 | 브라우저 메모리 | ✅ 표준 | ⚠ 약 | 없음 | ❌ |
| **A-2** 원격 QSCD | 서버 HSM | ✅ 표준 | ✅ 강 | 없음 | **✅** |
| **B** WebAuthn 키 서명 | 인증기 | ❌ KR전용 | ✅ | 없음 | ❌ |
| **C** TEE 전용 API | 단말 TEE/SE | ✅ 표준 | ✅ 강 | ⚠ 큼 | △(인증SE면) |

---

## 10. 최종 권고 아키텍처

**KR-WSCD 추상화 + 2-트랙(A-2 원격 + C 로컬), 서버가 표준 AdES 조립.** 비표준 경로(B, 한국 논문 extension) 폐기.

```
                      ┌───────────────── KR-DSS 공통 코어 (Rust) ─────────────────┐
[브라우저/WASM]        │  KR-WSCD 추상화 (provider 인터페이스)                      │
  문서·H(DTBS) 준비    │   ├ Remote backend  → HSM/QSCD (CSC API / ETSI TS 119 432)│  ← 트랙 A-2
  UI / 검증            │   ├ Local Native    → OS TEE (Keystore/SE/CNG-TPM, FFI)   │  ← 트랙 C
        │             │   ├ Local External  → 스마트카드/PKCS#11                   │
        ▼             │   └ (WebAuthn)      → SAD 활성화 인증 전용                 │
[네이티브 에이전트]    │  AdES 조립(CAdES/PAdES/…) · 검증기 · KR-TL                │
  TEE Sign(H(DTBS))   └───────────────────────────────────────────────────────────┘
        │                                   ▲
        └────── raw sig ────────────────────┘  서버가 표준 AdES 마감 (EU DSS 검증 통과)
```

- **거버넌스**: `kr-ades`/`kr-tl`(규격=ARF 상당) + `kr-dss-sdk`(레퍼런스 SDK) + `poc`(테스트베드=LSP 상당).
- **키 추상화**: `kr-dss-crypto`에 **KR-WSCD provider 계층**(원격HSM·eSE/eSIM·외부토큰·OS TEE).
- **WASM 역할**: 문서 파싱·해시·AdES 조립 준비·UI·검증. **키 연산은 네이티브/서버**.
- **단일 Rust 코어 → 듀얼 타깃**: WASM(웹) + 네이티브(에이전트, FFI로 OS keystore).
- **보증등급 트랙**: QES=SE/원격HSM, AdES(고급)=TEE. Protection Profile 인증 로드맵 별도.

### 모듈 매핑(제안)
| 기능 | 모듈 | 비고 |
|---|---|---|
| AdES 조립/검증 코어 | `kr-dss-sdk/kr-dss-core` | CAdES/PAdES 우선 |
| 암호·KR-WSCD provider | `kr-dss-sdk/kr-dss-crypto` | 4 백엔드 추상화 |
| 공개 API(sign/issue/verify) | `kr-dss-sdk/kr-dss-api` | CSC/TS119432 표면 |
| 검증보고서 | `kr-dss-sdk/kr-dss-report` | ETSI Validation Report |
| 신뢰목록 | `kr-tl` | XAdES 검증 → Java/DSS 고려 |

---

## 11. 미해결 / 다음 단계 후보
1. **2-트랙 통합 설계서**: KR-WSCD 인터페이스 정의 + 발급/서명/검증 시퀀스.
2. **경로 C 상세**: Rust 네이티브 에이전트 + 플랫폼 TEE FFI(Android Keystore/iOS SE/Win CNG-TPM) +
   Native Messaging + Key Attestation→CA 발급 검증.
3. **경로 A-2 상세**: CSC API/ETSI TS 119 432 엔드포인트 + WebAuthn=SAD 활성화 시퀀스.
4. **KR-AdES-WA 프로파일 규격 초안**(자체 생태계 한정 필요 시): OID 등록표·ASN.1·검증 7+E단계.
5. **Rust vs Java/DSS 코어 결정**: XAdES/HAdES/KR-TL C14N 리스크 평가 후 확정.
6. **OID arc 확보**: IANA PEN 신청 또는 KISA 국가 arc 협의.

---

## 12. 참고 자료
- FIDO Alliance — Using FIDO with eIDAS Services / EUDI Wallet White Papers
- Cloud Signature Consortium — CSC API v2.2 (2025-11) 및 EU 디지털신원 백서(2024)
- ETSI TS 119 431-1/-2, TS 119 432, EN 319 142 / CEN EN 419 241-1/-2, EN 419 221-5
- EUDI Wallet — Architecture and Reference Framework (ARF v2.x, GitHub `eu-digital-identity-wallet`)
- EUDI Dev Hub — Topic P: Secure Cryptographic Interface (Wallet Instance ↔ WSCA)
- Methics — WSCD/WSCA vs SSCD / GlobalPlatform — Securing EUDI Wallets with Secure Elements (2025)
- Cho, Han-koo & Lee, Kyung-ho, "A Method of Digital Signature Using FIDO2 CTAP", 정보보호학회논문지 29(5), 2019
- ETSI TS 119 612(Trusted List), EN 319 102-1 / TS 119 102-2(검증)
