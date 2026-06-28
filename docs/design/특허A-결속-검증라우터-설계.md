# 특허-A 구현 설계서 — 서명 결속부(Signature Binding) + 정책 기반 검증 라우터

> 대상 특허: **특허-A v5.0** "WebAuthn 인증기 기반 전자서명 생성 및 검증 시스템 및 방법"
> 범위: PoC(WEB/AOS). iOS 제외. 특허-B/특허-C 연계 지점은 인터페이스만 정의하고 본 설계의 구현 범위에서는 stub.
> 브랜치: `feat/claude-webauthn`
> 상태: 설계(Design) — 구현 착수 전 합의용 문서

---

## 1. 목적과 청구항 매핑

특허-A의 독립항/종속항을 PoC 컴포넌트로 매핑한다.

| 청구항 | 핵심 사상 | 본 설계의 산출물 |
|---|---|---|
| 청구항 1/2 (독립) | 서명 결속부 + 전자서명 객체 생성부 + 정책 기반 검증 라우터 | `SignatureBindingService`, `WebAuthnCmsAssembler`, `VerificationRouter` |
| 청구항 4/8 (L2) | 인코딩값 해시 = Challenge + Crypto Agility | `SignedAttrsBuilder`, `HashSuite`(정책 교체) |
| 청구항 5/10/11 (L2/L3) | unsignedAttrs 컨테이너 + 순환참조 방지 + CMS SignedData | `WebAuthnAssertionAttr`(ASN.1), CMS 패키징 |
| 청구항 6/7 (L2) | certificatePolicies OID 기반 3분기 + Policy Engine | `CertPolicyResolver`, `VerificationRouter` |
| 청구항 9 (L3) | `Challenge = BASE64URL(SHA-256(DER(SignedAttrs)))` | `SignatureBindingService.deriveChallenge()` |
| 청구항 12/15 (검증) | WebAuthn 검증 경로 상세 | `WebAuthnVerificationPath` |
| 청구항 13 (검증) | HSM 원격 서명 검증 경로 | `HsmVerificationPath` (기존 흐름 재사용) |
| 청구항 14 (검증) | TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED | `VerificationStatus` + `VerificationResult` |

---

## 2. 현재 PoC와의 핵심 차이 (가장 중요)

### 2.1 현재 동작 (as-is)
`poc/poc-relying-party/RpWebController.signBegin/signFinish` → `poc-rssp` → `poc-sam` → `poc-hsm`:

1. RP가 문서 다이제스트 계산 (`RemoteSignCoordinator.prepare`).
2. SAM `WebAuthnService.begin()` 이 **32바이트 난수 challenge** 발급(문서와 결속 X). 문서 해시는 `hashes`로 별도 보관되어 SAD에 바인딩.
3. 브라우저 `navigator.credentials.get(challenge)`.
4. SAM `verify()` — type/origin/UP만 확인, **COSE 서명 검증 생략**.
5. 검증 통과 시 SAD 발급 → **HSM(soft)이 문서 다이제스트에 RSA 서명** → JSON 컨테이너로 패키징.

→ **WebAuthn 어서션은 "사람이 승인했다"는 2FA 신호일 뿐, 전자서명값이 아니다.** 서명값은 HSM RSA 키 소산.

### 2.2 특허-A가 요구하는 동작 (to-be, Mode 1)
1. 서버가 `SignedAttrs = {messageDigest=H(문서), signingTime, signingCertificateV2=H(서명자 인증서)}` 구성.
2. `Challenge = BASE64URL(SHA-256(DER(SignedAttrs)))` — **문서·시각·인증서 3요소 결속**.
3. 브라우저/AOS `navigator.credentials.get(challenge)`.
4. 검증부: challenge 재계산 일치 + type/origin/rpIdHash/UP/UV + **COSE 공개키로 어서션 서명 실검증**.
5. **어서션 자체가 전자서명** → CMS SignedData(`signedAttrs` + `unsignedAttrs`에 `WebAuthnAssertionAttr`)로 패키징. **HSM 미사용.**

### 2.3 결론 — 두 서명 모드 공존 + 정책 라우팅
PoC는 두 경로를 **모두** 보유하고, 정책 라우터가 서명자 인증서의 `certificatePolicies` OID로 분기한다.

| 모드 | 서명 주체 | 키 | challenge 의미 | 특허 근거 |
|---|---|---|---|---|
| **Mode 1: WebAuthn 로컬 서명** (신규) | 인증기(패스키) | Credential 키쌍 | **결속 challenge = H(DER(SignedAttrs))** = 전자서명 입력 | 청1·12 |
| **Mode 2: HSM 원격 서명** (기존 개조) | HSM | 서버 키 | 결속 challenge = 서명자 인증(2FA) 게이트, 실제 서명은 HSM이 SignedAttrs 다이제스트에 수행 | 청13 |

> Mode 2도 challenge를 난수에서 **결속값으로 교체**한다(2FA가 "바로 그 문서"에 대한 승인임을 결착). Mode 1·2 공통으로 "결속 challenge"를 사용하는 것이 본 설계의 토대.

### 2.4 Mode 1 ↔ Mode 2 의 물리적 분리 (T3 재검토, 2026-06-28 확정)
사용자 합의: **Mode 1(WebAuthn 로컬 서명)은 SAM/HSM 원격서명 경로와 분리**된다.

| 구분 | Mode 1: WebAuthn 로컬 서명 | Mode 2: HSM 원격 서명 |
|---|---|---|
| 검증용 공개키 출처 | **CA가 발급한 X.509 인증서**(Credential 공개키 = SubjectPublicKeyInfo) | HSM 서명 인증서 |
| 공개키 저장소 | **WebAuthn 자격증명 레지스트리**(`WebAuthnCredentialStore`) — 인증서 보관 | RSSP 자격증명(`credentialInfo`) |
| 검증 수행 위치 | **kr-dss-core 검증 라우터의 WebAuthn 경로**(SAM 미경유) | RemoteSignVerifier/HSM 경로 |
| SAM/HSM | **미사용** | 사용(2FA 게이트 + 서명연산) |

→ 따라서 SAM `WebAuthnService`(난수→결속 challenge 2FA)는 **Mode 2 전용으로 유지**하고, **Mode 1 검증은 신규 경로**로 구현한다. 공개키는 등록 시 CA가 Credential 공개키로 발급한 인증서를 레지스트리에 저장해 두고, 검증 시 그 인증서의 공개키로 어서션 서명을 확인한다. (CA 발급 = 특허-B Registration Binding의 최소 브리지)

---

## 3. Signature Binding 상세

### 3.1 SignedAttrs 구조 (CMS SignedAttributes, ETSI EN 319 122 정합)
```
SignedAttrs (SET OF Attribute):
  - contentType        (1.2.840.113549.1.9.3)
  - messageDigest      (1.2.840.113549.1.9.4)   = H(문서)
  - signingTime        (1.2.840.113549.1.9.5)   = 서명 시각 (replay 방지)
  - signingCertificateV2 (1.2.840.113549.1.9.16.2.47) = H(서명자 인증서) (서명자 결속)
```

### 3.2 Challenge 파생식 (청구항 9)
```
Challenge = BASE64URL( HashFunc( DER(SignedAttrs) ) )
일실시예: Challenge = BASE64URL( SHA-256( DER(SignedAttrs) ) )
```
- 인코딩은 DER 고정(CMS 호환). `IMPLICIT [0]` SignedAttrs 태깅 주의 — 서명 입력용 DER은 `SET OF`로 재인코딩(CMS 규칙)하여 검증 측과 동일 바이트를 보장.
- WebAuthn `clientDataJSON.challenge` 필드에 이 값이 그대로 운반된다.

### 3.3 Crypto Agility (청구항 4/8)
- `HashSuite` 설정값: `SHA-256`(기본) | `SHA-384` | `SHA-512` | `SHA3-256` | `SM3`.
- 사용 알고리즘 식별자는 CMS `SignerInfo.signatureAlgorithm` 및 `digestAlgorithm`에 기재.
- PoC 1차 구현: SHA-256/SHA-384/SHA-512(JDK 표준) + SHA3-256(BouncyCastle). SM3는 enum/식별자만 예약(미구현 표시).

---

## 4. Container Binding 상세

### 4.1 WebAuthnAssertionAttr (ASN.1, 청구항 10)
```asn1
WebAuthnAssertionAttr ::= SEQUENCE {
    version            INTEGER (1),
    authenticatorData  OCTET STRING,
    clientDataJSON     OCTET STRING,   -- 원문 바이트 그대로 보관
    coseAlg            INTEGER,        -- 예: -7(ES256), -257(RS256)
    credentialId       OCTET STRING OPTIONAL,
    aaguid             OCTET STRING OPTIONAL
}
```
- 비서명 속성 OID(사설): `1.3.6.1.4.1.<PEN>.1.1` 형태로 예약(임시 `1.3.6.1.4.1.99999.1.1`, 추후 정식 PEN 배정).

### 4.2 순환참조 방지 (청구항 5)
- Challenge가 `SignedAttrs`의 인코딩값으로부터 파생되므로, 어서션 데이터를 `SignedAttrs`에 넣으면 순환참조.
- → 어서션 데이터는 반드시 **`unsignedAttrs`** 에 저장. WebAuthn 서명이 authenticatorData/clientDataJSON 무결성을 보호하므로 객체 전체 무결성 유지.

### 4.3 CMS SignedData 매핑 (청구항 11) — 설계 결정
- `SignedData.signerInfos[0].signedAttrs` = §3.1 SignedAttrs.
- `signerInfos[0].signatureAlgorithm` = WebAuthn 식별자(라우팅 힌트) — 표준 RSA/ECDSA OID 대신 KR-DSS 사설 OID `webAuthnAssertion` 사용.
- `signerInfos[0].signature` = WebAuthn 어서션 서명 바이트(`assertion.response.signature`). **표준 CMS 검증으로는 검증 불가** — 검증은 라우터의 WebAuthn 경로가 수행.
- `signerInfos[0].unsignedAttrs` = `WebAuthnAssertionAttr`.
- 인증서는 `SignedData.certificates`에 포함.

> 데모 1차에서는 BouncyCastle `CMSSignedData`를 직접 조립(저수준 `SignerInfo` 빌더)하거나, 복잡도를 낮추기 위해 **CMS 구조를 모사한 DER 컨테이너**로 시작하고 후속 태스크에서 정식 CMS로 승격하는 2단계 전략을 허용한다(아래 태스크 T4 참조).

---

## 5. Policy-Based Verification Router 상세

### 5.1 입력 / 출력 (청구항 6/7)
```
입력:
  - 전자서명 객체 (CMS / 데모 컨테이너)
  - 서명자 인증서의 certificatePolicies OID 집합
  - (선택) 인증서 내 장치 등급 속성
  - 검증 정책 설정 (KR-TL 연계 — 특허-C, 본 범위에선 stub)
출력:
  - 선택된 검증 경로 (WEBAUTHN | HSM | STANDARD)
  - VerificationStatus (TOTAL_PASSED | INDETERMINATE | TOTAL_FAILED) + subIndication
  - Check 목록(단계별 근거)
```

### 5.2 라우팅 규칙
1. 서명자 인증서 `certificatePolicies`에서 OID 추출.
2. OID → 경로 매핑 테이블(설정):
   - `policy.webauthn.*` → WEBAUTHN
   - `policy.hsm.*` → HSM
   - 그 외/없음 → STANDARD
3. **부트스트랩(특허-B 미구현 기간)**: OID 매핑이 비어있으면 컨테이너 구조로 추정 — `unsignedAttrs`에 `WebAuthnAssertionAttr` 존재 → WEBAUTHN, 그 외 → STANDARD/HSM. (정식 OID 라우팅은 특허-B 발급기 완성 후 활성화)

### 5.3 WebAuthn 검증 경로 (청구항 12/15)
```
1. unsignedAttrs에서 WebAuthnAssertionAttr 추출 (authenticatorData, clientDataJSON, sig, coseAlg, aaguid)
2. SignedAttrs DER 재인코딩 → HashFunc 재계산 → BASE64URL → clientDataJSON.challenge 와 비교
   (불일치: TOTAL_FAILED / BINDING_FAILURE)
3. clientDataJSON.type == "webauthn.get" 확인
4. clientDataJSON.origin 확인 (WEB: https origin 허용목록 / AOS: android:apk-key-hash 허용목록)
5. authenticatorData.rpIdHash == SHA-256(rpId) 확인
6. authenticatorData flags: UP(0x01) 필수, UV(0x04) 정책에 따라
7. Verify(credentialPubKey, authenticatorData ∥ SHA-256(clientDataJSON), signature)  ← COSE 실검증
   (실패: TOTAL_FAILED / SIG_CRYPTO_FAILURE)
8. SignedAttrs.messageDigest == H(문서) 비교 (원문 제공 시)
9. signingCertificateV2 결속 확인 (서명자 인증서 해시 일치)
10. 인증서 경로·신뢰목록·폐지·서명시점 검증 (특허-C 연계 — 본 범위 stub: INDETERMINATE 사유로 분류)
```

### 5.4 HSM 원격 서명 검증 경로 (청구항 13)
```
1. SignedAttrs DER → HashFunc 재계산
2. 재계산값(또는 messageDigest)에 대한 표준 비대칭 서명 검증 (기존 RemoteSignVerifier 로직 재사용)
3. messageDigest == H(문서) 비교
4. signingCertificateV2 결속 확인
5. 인증서 경로·신뢰목록·폐지·시점 (특허-C stub)
6. (선택) HSM Attestation 확인 (특허-C stub)
```

### 5.5 결과 분류 (청구항 14)
- `TOTAL_PASSED`: 서명검증 + 3요소 결속 + 문서해시 + (경로·TL·폐지·시점) 모두 성공.
- `TOTAL_FAILED`: 서명검증 실패 | 결속 불일치 | 해시 불일치 | 경로 검증 실패 중 하나.
- `INDETERMINATE`: 암호학적 서명은 성공했으나 TL·폐지·시점·장치신뢰 일부 불충분(특허-C 미구현 구간 포함).

---

## 6. 모듈 / 파일 변경 맵

### 6.1 신규 클래스
| 모듈 | 패키지/클래스 | 책임 |
|---|---|---|
| `kr-ades/kr-ades-cades` | `…ades.cades.bind.SignedAttrsBuilder` | SignedAttrs(messageDigest/signingTime/signingCertificateV2) 구성·DER 인코딩 |
| `kr-ades/kr-ades-cades` | `…ades.cades.bind.SignatureBindingService` | `deriveChallenge(SignedAttrs)` = BASE64URL(Hash(DER)) |
| `kr-ades/kr-ades-cades` | `…ades.cades.bind.HashSuite` | Crypto Agility (알고리즘 enum + 식별자) |
| `kr-ades/kr-ades-cades` | `…ades.cades.container.WebAuthnAssertionAttr` | ASN.1 구조 인코딩/디코딩 |
| `kr-ades/kr-ades-cades` | `…ades.cades.container.WebAuthnCmsAssembler` | CMS SignedData(signedAttrs+unsignedAttrs) 패키징 |
| `kr-ades/kr-ades-cades` | `…ades.cades.KrAdesOids` | 사설 OID 상수(webAuthnAssertion, policy.*) |
| `kr-dss-sdk/kr-dss-core` | `…dss.core.verify.VerificationRouter` | 경로 분기 + 결과 종합 |
| `kr-dss-sdk/kr-dss-core` | `…dss.core.verify.CertPolicyResolver` | certificatePolicies OID 추출/매핑 |
| `kr-dss-sdk/kr-dss-core` | `…dss.core.verify.WebAuthnVerificationPath` | §5.3 (webauthn4j 사용) |
| `kr-dss-sdk/kr-dss-core` | `…dss.core.verify.HsmVerificationPath` | §5.4 (기존 RemoteSignVerifier 흡수) |
| `kr-dss-sdk/kr-dss-core` | `…dss.core.verify.VerificationResult` | indication/subIndication/checks DTO |

### 6.2 수정 파일
| 파일 | 변경 |
|---|---|
| `gradle/libs.versions.toml` | `webauthn4j-core` 추가, bc는 이미 존재(cades 모듈에 의존 추가) |
| `poc-sam/WebAuthnService.java` | `begin()`이 **외부 결속 challenge를 등록**(난수 생성 제거). `register()`가 **credential 공개키(COSE/SPKI) 저장**. `verify()`가 **COSE 서명 실검증**(webauthn4j) |
| `poc-sam/SamController.java` | `AuthorizeBeginRequest`에 challenge/SignedAttrs 전달 반영, passkey 등록 시 공개키 수신 |
| `kr-dss-remote/CscModels.java` | `AuthorizeBeginRequest`에 `challenge` 또는 `signedAttrsB64` 필드 추가; `PasskeyRegisterRequest`에 `publicKeyB64`(SPKI) 추가 |
| `poc-relying-party/RpWebController.java` | `signBegin`: SignedAttrs 구성→결속 challenge 파생(Mode 1). `signFinish`: WebAuthnCmsAssembler로 CMS 패키징, 라우터 검증 |
| `poc-relying-party/SignerInteractionComponent.java` | Mode 1 경로 추가(HSM signHash 생략) |
| `kr-dss-core/RemoteSignCoordinator.java` | `assemble`가 포맷에 따라 WebAuthnCmsAssembler로 위임 가능하도록 확장 |
| `kr-dss-core/RemoteSignVerifier.java` | `VerificationRouter`로 흡수/위임(하위호환 유지) |
| `poc-relying-party/static/index.html` | 등록 시 `getPublicKey()` 전송; 검증 결과에 결속/경로 표시 |

### 6.3 의존성 방향 점검 (AGENTS.md §3 준수)
- `kr-dss-core` → `kr-ades-cades` (오케스트레이션 → 어댑터): **허용**.
- `kr-ades-cades` → `dss-cades`, `bc-pkix`: 어댑터 → 라이브러리: **허용**.
- 역방향 의존 없음.

---

## 7. API / 데이터 모델 변경

### 7.1 패스키 등록 (공개키 캡처 — Mode 1 검증의 전제)
- **현재**: `{credentialId}` 만 전송 → 어서션 서명 검증 불가.
- **변경**: 브라우저 `cred.response.getPublicKey()`(SPKI DER) 전송. AOS는 attestationObject에서 COSE 공개키 추출.
```
PasskeyRegisterRequest { signerId, credentialId, publicKeyB64 /* SPKI */, aaguid?, attestationObject? }
```

### 7.2 서명 begin (결속 challenge)
```
SignBeginResponse {
  ticket, digestB64,
  challenge,          // = BASE64URL(SHA-256(DER(SignedAttrs)))  ← 결속값
  rpId, allowCredentials, timeoutMs,
  signedAttrsB64      // (디버그/검증용, 서버 보관이 원칙)
}
```

### 7.3 서명 finish (CMS 패키징)
- 입력은 기존과 동일(assertion 4종).
- 출력 `signedDocument`는 **CMS(Base64) 또는 데모 컨테이너(JSON)** — `format`/`signatureMode=WEBAUTHN_LOCAL` 표기.

### 7.4 검증 응답
- 기존 `RemoteSignVerifier.Report` 확장: `signaturePath`(WEBAUTHN/HSM/STANDARD), `bindingChecks`(challenge 재계산·messageDigest·signingCertificateV2) 추가.

---

## 8. WEB / AOS 영향

| 항목 | WEB | AOS |
|---|---|---|
| 등록 공개키 | `cred.response.getPublicKey()` (SPKI) | attestationObject → COSE 공개키 파싱 |
| 결속 challenge 주입 | `navigator.credentials.get({challenge})` | Credential Manager `GetPublicKeyCredentialOption(requestJson)` 의 challenge |
| origin 검증 | `https://…` 허용목록 | `android:apk-key-hash:…` 허용목록 (라우터 §5.3-4 분기) |
| COSE 서명 검증 | 서버 webauthn4j(공통) | 동일(서버 공통) |
| UV | Windows Hello 등 | 지문/얼굴 |

> 검증·결속·CMS 패키징은 **전부 서버 공통**. AOS는 클라이언트만 추가(별도 작업, 본 (a) 범위 외). 본 설계의 서버 변경은 WEB·AOS 양쪽을 동시에 충족한다.

---

## 9. 태스크 분해 (구현 순서)

각 태스크는 독립 커밋 + `./gradlew build` 통과 + 단위테스트 동반.

- **T1 — 의존성/OID 기반 구성**
  `libs.versions.toml`에 webauthn4j 추가, `kr-ades-cades`에 bc/dss-cades 의존 추가, `KrAdesOids`·`HashSuite` 작성.
  산출물: 빌드 통과, OID/알고리즘 상수.

- **T2 — Signature Binding 코어**
  `SignedAttrsBuilder`(messageDigest/signingTime/signingCertificateV2) + `SignatureBindingService.deriveChallenge()`.
  검증: 동일 입력→동일 challenge, DER 재인코딩 안정성 단위테스트.

- **T3 — WebAuthn 검증 경로(COSE 실검증)** *(재검토 반영: SAM/HSM과 분리)*
  `WebAuthnCredentialStore`(credentialId→CA발급 인증서·coseAlg·aaguid·signCount) + `WebAuthnVerificationPath`(webauthn4j로 어서션 서명/flags/rpIdHash 검증, 공개키=저장 인증서의 SPKI). **SAM은 미경유**(Mode 2 전용 유지).
  검증: 합성 어서션(EC P-256 자격증명+인증서)으로 서명검증 PASS, 변조/잘못된 challenge FAIL.

- **T4 — Container Binding (CMS 패키징)**
  `WebAuthnAssertionAttr` + `WebAuthnCmsAssembler`. (1단계: 모사 DER/JSON 컨테이너 → 2단계: 정식 CMS SignedData)
  검증: 패키징→파싱 라운드트립, unsignedAttrs 위치 확인.

- **T5 — Policy-Based Verification Router**
  `VerificationRouter` + `CertPolicyResolver` + `HsmVerificationPath`(기존 흡수) + 결과 3분류.
  검증: WEBAUTHN/HSM/STANDARD 분기 + TOTAL_PASSED/INDETERMINATE/TOTAL_FAILED 케이스.

- **T6 — RP/SAM 흐름 통합 (Mode 1 end-to-end)**
  `RpWebController.signBegin/finish`에 Mode 1 결속 흐름 연결, SAM begin을 결속 challenge 등록형으로 개조.
  검증: 브라우저에서 서명→검증 PASS 데모.

- **T7 — 프론트엔드/데모**
  `index.html` 등록 공개키 전송 + 검증 결과에 결속/경로/3분류 시각화.
  검증: 수동 E2E.

- **T8 — Crypto Agility 데모**
  HashSuite 설정 교체(SHA-256↔SHA-384) 시 challenge/검증 일관성.

> T1→T2→T3는 순차, T4는 T2 이후 병렬 가능, T5는 T3/T4 이후, T6은 T5 이후, T7/T8은 T6 이후.

---

## 10. 테스트 계획
- 단위: SignedAttrs DER 안정성, challenge 파생 결정성, WebAuthnAssertionAttr 라운드트립, COSE 서명 검증(테스트벡터), 라우터 분기·분류.
- 통합: Mode 1(WebAuthn 로컬) / Mode 2(HSM 원격) 각각 서명→검증 TOTAL_PASSED, 변조 문서 TOTAL_FAILED, challenge 재사용 TOTAL_FAILED.
- 수동 E2E: 브라우저(Windows Hello/보안키)로 등록→서명→검증.

---

## 11. 주요 결정사항 / 리스크
> **확정 (2026-06-28):** (1) 컨테이너 = **2단계 전략** 채택(1차 모사 → 2차 정식 CMS). (2) COSE 검증 = **webauthn4j 도입**.

1. **CMS 정식 vs 모사 컨테이너** — *2단계 전략 확정*: 1차는 검증 가능한 DER/JSON 모사로 속도 확보, T4 2단계에서 BouncyCastle 정식 CMS로 승격. (리스크: 정식 CMS의 SignerInfo.signature 비표준 사용 — 표준 CMS 검증기와 호환 불가, KR-DSS 라우터 전용임을 명시)
2. **공개키 캡처 의존성**: Mode 1 검증은 등록 시 credential 공개키 저장이 전제. 특허-B(Registration Binding)와 직접 연결되나, 본 범위에선 "등록 시 SPKI 저장"만 선행.
3. **COSE 검증 라이브러리** — *webauthn4j 확정*: `webauthn4j-core`로 어서션 서명·flags·rpIdHash 실검증. (단, KR-DSS는 challenge를 SignedAttrs 결속값으로 쓰므로 webauthn4j의 challenge 검증은 우회/커스텀하고 서명·구조 검증만 활용)
3. **정책 OID 라우팅**: 특허-B 발급기 미완성 동안 컨테이너 구조 추정으로 부트스트랩. 정식 OID 매핑은 특허-B 완료 후 활성화.
4. **TL/폐지/시점/장치신뢰**: 특허-C 범위 → 본 설계에서는 INDETERMINATE 사유로 분류하는 hook만 마련.
5. **SM3**: 식별자만 예약, 미구현(국내 정책 확정 시 추가).

---

## 12. 변경 이력
| 버전 | 날짜 | 내용 |
|---|---|---|
| v0.1 | 2026-06-28 | 최초 설계 — 두 서명 모드 정의, 결속/컨테이너/라우터 상세, 모듈 변경 맵, 태스크 분해 |
