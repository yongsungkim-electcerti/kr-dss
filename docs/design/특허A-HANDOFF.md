# 특허-A 구현 인수인계 (HANDOFF)

> **이 문서 하나로 이어서 작업한다.** 특허-A "서명 결속부 + 정책 기반 검증 라우터"의
> PoC(WEB/AOS, iOS 제외) 구현 진행 현황·결정·다음 작업을 담는다.
> 최종 갱신: 2026-06-28 · 브랜치: `feat/claude-webauthn`

관련 문서:
- 상세 설계서: [특허A-결속-검증라우터-설계.md](특허A-결속-검증라우터-설계.md)
- 3특허→PoC 분석(상위): 본 세션 분석 요약(특허 A/B/C 매핑)은 설계서 §1·§2 참조

---

## 0. 한눈 요약 (지금 어디까지 왔나)

특허-A의 핵심 = **(1) 문서·서명시각·서명자 인증서 3요소를 단일 WebAuthn Challenge에 결속**하고,
**(2) 인증서 정책으로 검증 경로를 동적 분기**한다.

- ✅ **T1~T6 완료, 커밋·빌드 그린.** 결속 challenge + COSE 실검증 + 모사 CMS 컨테이너 + 정책 라우터 + **Mode 1 E2E 통합(RP `/api/local/*`, 브라우저 경로 포함)**까지 동작.
- ⬜ **T7(프론트 시각화 보강) → T8(Crypto Agility 데모)** 남음.
- 다음 시작점: **T7 — 프론트엔드 시각화 보강** (결속 3요소·경로·3분류 시각화. Mode 1 기본 흐름은 index.html에 이미 추가됨).

핵심 설계 통찰(반드시 숙지):
> 현재 PoC의 WebAuthn은 **2FA 게이트**일 뿐 실제 서명은 HSM이 한다. 특허-A는 **WebAuthn 어서션 자체가 전자서명**(Mode 1)이다. 그래서 두 서명 모드를 공존시키고 정책 라우터가 분기한다.

| 모드 | 서명 주체 | 검증 공개키 출처 | SAM/HSM | 특허 |
|---|---|---|---|---|
| **Mode 1** WebAuthn 로컬 서명 (신규) | 패스키 | **CA 발급 인증서 SPKI** | 미사용 | 청1·12 |
| **Mode 2** HSM 원격 서명 (기존 개조) | HSM | HSM 서명 인증서 | 사용 | 청13 |

---

## 1. 완료 작업 (T1~T3)

| # | 내용 | 커밋 |
|---|---|---|
| 설계 | 설계서 + 태스크 분해 + 결정사항 | `f3b7597` |
| T1 | webauthn4j 0.28.5 의존성, `KrAdesOids`, `HashSuite`(Crypto Agility) | `f3b7597` |
| T2 | `SignedAttrsBuilder`(3요소→SignedAttrs), `SignatureBindingService`(challenge 파생) | `f3b7597` |
| T3 | `WebAuthnCredentialStore`, `WebAuthnVerificationPath`(webauthn4j COSE 실검증) | `163d495` |
| T4 | `container/WebAuthnAssertionAttr`(IMPLICIT 태그), `container/WebAuthnCmsAssembler`(모사 컨테이너, signedAttrs 원본 보존, 어서션=unsignedAttrs) | `aaf8405` |
| T5 | `verify/VerificationRouter`(컨테이너 기반 경로 분기+3분류), `CertPolicyResolver`(정책 OID), `HsmVerificationPath`(RemoteSignVerifier 흡수), `VerificationResult`, `bind/SignedAttrsParser`(결속 검증) | `45130e1` |
| T6 | `poc-rp/local/{WebAuthnDemoCa,Mode1LocalSignService,Mode1WebAuthnController}`(`/api/local/*`, SAM/HSM 미경유), index.html Mode 1 토글·흐름, E2E 서비스테스트 2건 | (이 커밋) |

### 산출 파일
```
kr-ades/kr-ades-cades/src/main/java/com/electcerti/krdss/ades/cades/
  KrAdesOids.java                         # 표준 CMS + KR-DSS 사설 OID(WebAuthn/정책)
  bind/HashSuite.java                     # SHA-256/384/512/SHA3-256(+SM3 예약)
  bind/SignedAttrsBuilder.java            # messageDigest+signingTime+signingCertificateV2 → DER
  bind/SignatureBindingService.java       # Challenge = BASE64URL(Hash(DER(SignedAttrs)))
kr-ades/kr-ades-cades/src/test/.../bind/SignatureBindingTest.java   # 5건

kr-dss-sdk/kr-dss-core/src/main/java/com/electcerti/krdss/dss/core/verify/
  WebAuthnCredentialStore.java            # credentialId → CA발급 인증서·coseAlg·aaguid·signCount
  WebAuthnVerificationPath.java           # 어서션 서명/flags/rpIdHash/challenge/origin 검증
kr-dss-sdk/kr-dss-core/src/test/.../verify/WebAuthnVerificationPathTest.java  # 5건
```

### 검증된 사실(테스트 근거)
- 문서/서명시각/서명자 인증서 중 하나만 바뀌어도 challenge가 달라짐 → **replay·문서대체 차단**.
- 결속 challenge로 생성한 EC P-256 어서션이 **webauthn4j로 실제 서명 검증 통과**, 변조/잘못된 challenge/비허용 origin/미등록 자격증명은 모두 실패.
- Crypto Agility: SHA-256↔SHA-384 교체 시 challenge 일관 변경, SM3는 예약(미지원).

---

## 2. 확정된 설계 결정 (변경 금지, 변경 시 합의 필요)

1. **두 서명 모드 공존 + 정책 라우팅** (위 표). Mode 1은 SAM/HSM과 **물리적 분리**.
2. **결속식**: `Challenge = BASE64URL(SHA-256(DER(SignedAttrs)))`. SignedAttrs = contentType + messageDigest + signingTime + signingCertificateV2.
3. **컨테이너 = 2단계 전략**: 1차는 검증 가능한 모사 DER/JSON → 2차 BouncyCastle 정식 CMS SignedData. 어서션은 **unsignedAttrs**(순환참조 방지).
4. **COSE 검증 = webauthn4j**. 단, challenge는 결속값을 사용(webauthn4j ServerProperty에 결속 challenge 바이트를 주입해 대조).
5. **Mode 1 공개키 출처 = CA 발급 인증서**(등록 시 Credential 공개키로 발급·저장). 특허-B Registration Binding의 최소 브리지.
6. **정책 OID 라우팅**: 특허-B 발급기 완성 전까지는 컨테이너 구조 추정으로 부트스트랩(`unsignedAttrs`에 WebAuthnAssertionAttr 있으면 WEBAUTHN).
7. **TL/폐지/시점/장치신뢰**(특허-C 범위)는 본 구현에서 INDETERMINATE 사유 hook만 마련.

사설 OID(임시 아크 `1.3.6.1.4.1.99999.*`)는 정식 PEN 배정 후 교체. (`KrAdesOids` 참조)

---

## 3. 다음 작업 (T4~T8) — 시작점·구현 힌트·완료 기준

### T4 — Container Binding ✅ 완료 (`aaf8405`)
> 산출: `container/WebAuthnAssertionAttr.java`(IMPLICIT [0]/[1]), `container/WebAuthnCmsAssembler.java`(모사 `KrWebAuthnSignature`). 테스트 10건. 상세 스펙은 [특허A-T4-Cowork-Code-분담.md](특허A-T4-Cowork-Code-분담.md) §4·§5.
> 후속(2차): 정식 `CMSSignedData` 승격.
- 신규: `kr-ades-cades` 에 `container/WebAuthnAssertionAttr.java`(ASN.1), `container/WebAuthnCmsAssembler.java`.
- ASN.1 (설계서 §4.1):
  ```asn1
  WebAuthnAssertionAttr ::= SEQUENCE {
    version INTEGER(1), authenticatorData OCTET STRING, clientDataJSON OCTET STRING,
    coseAlg INTEGER, credentialId OCTET STRING OPTIONAL, aaguid OCTET STRING OPTIONAL }
  ```
- 1차 구현: BouncyCastle `ASN1Sequence`로 `WebAuthnAssertionAttr` 인코딩/디코딩 + 이를 담는 모사 컨테이너(JSON/DER) 패키징·파싱. 어서션은 unsignedAttrs 영역에 배치(OID `KrAdesOids.WEBAUTHN_ASSERTION_ATTR`).
- 완료 기준: 패키징→파싱 라운드트립 단위테스트, unsignedAttrs 위치 검증.
- 2차(후속): BouncyCastle `CMSSignedData` 정식 조립(`signedAttrs`=SignedAttrs, `signature`=어서션 서명, `unsignedAttrs`=WebAuthnAssertionAttr, `certificates`=서명자 인증서). 비표준 SignerInfo.signature임을 주석 명시.

### T5 — 정책 기반 검증 라우터 ✅ 완료
> 산출: `verify/{VerificationRouter,CertPolicyResolver,HsmVerificationPath,VerificationResult}.java`, `bind/SignedAttrsParser.java`. 테스트 4건(WEBAUTHN TOTAL_PASSED / 문서변조 TOTAL_FAILED / 미등록 INDETERMINATE / JSON→HSM 위임). 경로는 컨테이너 구조로 분기(정책 OID는 CertPolicyResolver로 기록), 미등록 자격증명/TL 미평가는 INDETERMINATE.
- 신규: `kr-dss-core/verify` 에 `VerificationRouter`, `CertPolicyResolver`, `HsmVerificationPath`, `VerificationResult`.
- `CertPolicyResolver`: 서명자 인증서 `certificatePolicies` OID 추출 → WEBAUTHN/HSM/STANDARD 매핑(설계서 §5.2). 부트스트랩 규칙 포함.
- `HsmVerificationPath`: 기존 [RemoteSignVerifier.java](../../kr-dss-sdk/kr-dss-core/src/main/java/com/electcerti/krdss/dss/core/remote/RemoteSignVerifier.java) 로직 흡수(하위호환 유지).
- `VerificationRouter`: 경로 선택 → 해당 경로 실행 → §5.5 3분류(`VerificationStatus`)로 종합. WebAuthn 경로는 T3 `WebAuthnVerificationPath` + messageDigest/문서 비교 + signingCertificateV2 결속 확인 추가.
- 완료 기준: WEBAUTHN/HSM/STANDARD 분기 + TOTAL_PASSED/INDETERMINATE/TOTAL_FAILED 케이스 테스트.

### T6 — RP/SAM 흐름 통합 (Mode 1 E2E) ✅ 완료
> 산출: `poc-relying-party/.../local/{WebAuthnDemoCa,Mode1LocalSignService,Mode1WebAuthnController}`. 엔드포인트 `/api/local/{register,sign/begin,sign/finish,verify}`. index.html에 "Mode 1" 토글 + create()/get() 흐름 추가(기존 Mode 2 보존). `Mode1LocalSignServiceTest` 2건(TOTAL_PASSED / 문서변조 FAILED) + 라이브 부트 스모크 확인. 등록 시 `WebAuthnDemoCa`가 Credential 공개키로 인증서 발급(certificatePolicies=POLICY_WEBAUTHN)→`WebAuthnCredentialStore` 저장.
> 후속: 등록/서명을 RSSP/SAM 경유로 옮기려면 별도 설계(현재 Mode 1은 RP 내 완결, SAM/HSM 미경유 — 설계 의도와 일치).
- 등록(`/api/passkey/register` 계열): 브라우저 `cred.response.getPublicKey()`(SPKI) 수신 → **CA 인증서 발급**(BouncyCastle, `tools/krdss-cli`의 `CertCommand` 로직 재사용) → `WebAuthnCredentialStore`에 저장.
  - `CscModels.PasskeyRegisterRequest`에 `publicKeyB64` 추가, `AuthorizeBeginRequest`에 결속 challenge 전달.
- 서명 begin([RpWebController.java](../../poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/RpWebController.java) `signBegin`): `SignedAttrsBuilder`로 SignedAttrs 구성 → `SignatureBindingService`로 challenge 파생 → 브라우저 전달. (난수 challenge 제거; SAM `WebAuthnService.begin`은 외부 결속 challenge 등록형으로 개조 또는 Mode 1은 SAM 미경유)
- 서명 finish: 어서션 → `VerificationRouter`(또는 곧장 `WebAuthnVerificationPath`)로 검증 → `WebAuthnCmsAssembler`로 패키징.
- 완료 기준: 브라우저에서 등록→서명→검증 TOTAL_PASSED 데모. `./gradlew :poc:poc-relying-party:bootRun` 후 수동 E2E.

### T7 — 프론트엔드 데모 (다음 시작점)
- [index.html](../../poc/poc-relying-party/src/main/resources/static/index.html): 등록 시 `getPublicKey()` 전송, 검증 결과에 결속/경로/3분류 시각화.

### T8 — Crypto Agility 데모
- HashSuite 설정 교체(SHA-256↔SHA-384) 시 challenge/검증 일관성 시연.

---

## 4. 빌드 · 테스트 · 실행

```bash
./gradlew build                              # 전체 빌드+테스트 (커밋 전 필수)
./gradlew :kr-ades:kr-ades-cades:test        # T2 결속 테스트
./gradlew :kr-dss-sdk:kr-dss-core:test       # T3 검증 테스트
./gradlew :poc:poc-relying-party:bootRun     # SIC 데모(:8080) — Mode 2(기존) 동작
```
- JDK 21 toolchain 자동 프로비저닝. Windows는 `gradlew.bat`.
- PoC 전체 기동 순서(Mode 2): hsm(8092) → sam(8091) → rssp(8090) → relying-party(8080).

---

## 5. 작업 규칙 (AGENTS.md 준수)
- 브랜치: `feat/claude-webauthn` (이미 분리됨). main 직접 커밋 금지.
- 작게 자주 커밋, 커밋 메시지 형식 `<type>(<scope>): <요약> [claude]`, 빌드 통과 후 커밋.
- 모듈 경계: 의존 방향 상위(core)→하위(cades). 역방향 금지.
- 의존성은 `gradle/libs.versions.toml` 카탈로그로만 추가.

---

## 6. AOS(안드로이드) 메모 (본 범위 외, 후속)
- 서버 검증·결속·패키징은 WEB/AOS 공통. AOS는 클라이언트만 추가(별도 모듈, 별도 Gradle 빌드 권장 — build-logic은 JVM 전용).
- AOS는 실제 기기 attestation/AAGUID 제공 → 특허-B(등급 판정)·특허-C(장치 신뢰목록) 데모에 유리. origin은 `android:apk-key-hash:…` (검증 라우터 §5.3-4에서 분기, webauthn4j Origin이 지원).
- iOS는 PoC 제외.

---

## 7. 이어서 시작하는 법 (Cowork/신규 세션)
1. 이 문서와 [설계서](특허A-결속-검증라우터-설계.md) §2·§5를 먼저 읽는다.
2. `git checkout feat/claude-webauthn && ./gradlew build` 로 그린 확인.
3. **T4**부터 §3의 시작점대로 진행 → 각 태스크 단위 커밋.
4. 막히면 §2 확정 결정과 충돌하는지 확인하고, 충돌 시 사용자에게 합의 요청.
