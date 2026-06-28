# 특허-A T4 — Cowork / Code 작업 분담

> 특허확보 → 설계검토 → **T4(Container Binding)** 경로를 **Cowork(문서·설계)** 와
> **Code(구현·빌드)** 로 분리해 관리한다. 최종 갱신: 2026-06-28 · 브랜치 `feat/claude-webauthn`

---

## 0. 분담 원칙

프로젝트 규약(`F:\webAsign` 지침) + 본 저장소 환경 제약을 결합한 역할 분리.

| 영역 | 역할 | 근거 |
|---|---|---|
| **Chat** | 기술 조사 (특허 초안·검토) | 규약: "Chat을 통해 기술을 조사" |
| **Cowork** | 기술문서·설계·스펙·계획 | 규약: "Cowork을 통해 기술문서를 제작" |
| **Code** | 데모 프로그램(소스·빌드·테스트·커밋) | 규약: "Code를 통해 데모 프로그램" |

**핵심 제약 (이번 확인):** Cowork 리눅스 샌드박스는 이 Gradle/JVM 프로젝트를 **빌드할 수 없다.**
JDK 21 부재(JDK 11만 존재) · Maven 의존성/JDK21 toolchain 다운로드 네트워크 차단.
→ **`.java` 작성·`./gradlew build`·테스트·커밋은 전부 Code(사용자 Windows)에서 수행.**
→ Cowork는 **빌드가 필요 없는 산출물**(설계·스펙·문서·계획)만 담당.

---

## 1. 전체 경로 R&R (특허확보 → T4)

| 단계 | 산출물 | 담당 | 상태 |
|---|---|---|---|
| 특허 본문 확보 | 특허 A/B/C 명세서 `docs/design/특허[ABC]-명세서-*.md` | **Cowork** | ✅ 완료 |
| 설계 검토 | DER 인코딩 · IMPLICIT 태그 결정 | **Cowork** | ✅ 완료 |
| 설계서 §4.1 갱신 | IMPLICIT 태그 ASN.1 확정문 (본 문서 §5) | **Cowork**(작성) / **Code**(적용·커밋) | ✅ `aaf8405` |
| T4 구현 스펙 | Code-ready 명세 (본 문서 §4) | **Cowork** | ✅ 본 문서 |
| T4 소스 | `WebAuthnAssertionAttr.java`, `WebAuthnCmsAssembler.java` | **Code** | ✅ `aaf8405` |
| T4 테스트 | `WebAuthnAssertionAttrTest`, `WebAuthnCmsAssemblerTest` | **Code** | ✅ 10건 통과 |
| 빌드·테스트 | `./gradlew build` 그린 | **Code** | ✅ |
| 커밋 | `feat(cades): T4 … [claude]` | **Code** | ✅ `aaf8405` |
| HANDOFF 갱신 | T4 완료 기록 | **Cowork**(작성) / **Code**(커밋) | ✅ |

---

## 2. 확정 설계 결정 (T4)

1. **1차 모사 컨테이너 인코딩 = DER(ASN.1)** — BouncyCastle. 2차 정식 CMS와 구조 동일·바이트 안정, challenge 재파생 byte-identical 보장.
2. **`WebAuthnAssertionAttr` OPTIONAL = IMPLICIT `[0]`/`[1]` 태그** — 동일 UNIVERSAL 타입(OCTET STRING) OPTIONAL 두 개 연속의 디코딩 모호성 제거. 설계서 §4.1 원문 갱신 동반.
3. **2단계 전략 유지** — T4는 **1차(모사 DER 컨테이너)** 까지. 2차 정식 `CMSSignedData` 승격은 후속 태스크.
4. (변경 금지 확정사항은 HANDOFF §2를 따른다 — 어서션은 반드시 `unsignedAttrs`, 순환참조 방지.)

---

## 3. Cowork 산출물

**완료**
- 특허 A v5.0 / B v3.0 / C v2.0 명세서 저장 (이스케이프 정리).
- 설계 검토 및 결정 2건 (§2).
- 본 분담·관리 문서.

**잔여 (빌드 불요, Cowork 가능)**
- 설계서 §4.1 ASN.1 IMPLICIT 태그 확정문 → **본 문서 §5 제공**(Code가 같은 커밋에 적용).
- T4 완료 후 HANDOFF §3 T4 항목 체크 및 §1 표에 커밋 해시 기록(텍스트 편집).

---

## 4. Code 작업 패키지 (구현 스펙 — 그대로 구현)

> 위치: `kr-ades/kr-ades-cades/src/main/java/com/electcerti/krdss/ades/cades/container/`
> 의존: BouncyCastle(`bcpkix`/`bcprov`, 기존) · `KrAdesOids`(기존). 의존 방향 cades(하위) 유지.

### 4.1 `WebAuthnAssertionAttr.java`

```asn1
WebAuthnAssertionAttr ::= SEQUENCE {
    version            INTEGER (1),
    authenticatorData  OCTET STRING,
    clientDataJSON     OCTET STRING,        -- 원문 바이트 그대로 보관
    coseAlg            INTEGER,             -- 예: -7(ES256), -257(RS256)
    credentialId   [0] IMPLICIT OCTET STRING OPTIONAL,
    aaguid         [1] IMPLICIT OCTET STRING OPTIONAL
}
```

- 값 보관 = Java `record`(byte[] 방어복사). `coseAlg`는 음수 가능 → `ASN1Integer(long)`.
- `byte[] toDer()` : `DLSequence`/`ASN1EncodableVector` 구성, OPTIONAL은 `DERTaggedObject(false, n, new DEROctetString(...))`(false=IMPLICIT).
- `static WebAuthnAssertionAttr fromDer(byte[])` : `ASN1Sequence` 파싱, 인덱스 0–3 고정, 이후 `ASN1TaggedObject`의 `getTagNo()`로 `[0]`/`[1]` 분기(IMPLICIT → `ASN1OctetString.getInstance(tagged, false)`).
- 검증: `version==1`, 필수 4필드 존재, coseAlg 범위.

### 4.2 `WebAuthnCmsAssembler.java`

최상위 모사 컨테이너 (CMS SignerInfo 필드와 1:1 대응 → 2차 승격 용이):

```asn1
KrWebAuthnSignature ::= SEQUENCE {
    version            INTEGER (1),
    signedAttrs        OCTET STRING,            -- T2 DER(SET OF Attribute) 원본 바이트 그대로
    signatureAlgorithm OBJECT IDENTIFIER,       -- KrAdesOids.WEBAUTHN_ASSERTION_SIG_ALG
    signature          OCTET STRING,            -- assertion.response.signature
    certificates       SEQUENCE OF Certificate, -- 서명자 인증서(들)
    unsignedAttrs  [1] IMPLICIT SET OF Attribute -- WebAuthnAssertionAttr 수납
}
```

- `unsignedAttrs`의 원소 = CMS `Attribute ::= SEQUENCE { attrType OID, attrValues SET OF ANY }`,
  `attrType = KrAdesOids.WEBAUTHN_ASSERTION_ATTR`, `attrValues = { WebAuthnAssertionAttr.toDer() }`.
- **`signedAttrs`는 T2 `SignedAttrsBuilder.SignedAttrs.der()` 원본 바이트를 OCTET STRING으로 그대로 저장** — 검증 측이 동일 바이트로 challenge 재파생(설계 §3.2). 재인코딩 금지.
- API:
  - `byte[] assemble(byte[] signedAttrsDer, byte[] assertionSignature, List<X509Certificate> certs, WebAuthnAssertionAttr attr)`
  - `Parsed parse(byte[] container)` → `record Parsed(byte[] signedAttrsDer, String sigAlgOid, byte[] signature, List<X509Certificate> certs, WebAuthnAssertionAttr attr)`
- 어서션 데이터는 **오직 unsignedAttrs**에만. `signedAttrs`(OCTET STRING) 안에는 절대 넣지 않음(순환참조 방지, HANDOFF §2).

### 4.3 테스트

`WebAuthnAssertionAttrTest`
- 라운드트립: OPTIONAL 4조합(둘다有 / cred만 / aaguid만 / 둘다無) 각각 `toDer→fromDer` 일치.
- `[0]`/`[1]` 단독 존재 시 정확히 해당 필드로 복원(모호성 회귀 방지).
- version≠1, 필수필드 누락 시 예외.

`WebAuthnCmsAssemblerTest`
- `assemble→parse` 전 필드 일치.
- **signedAttrs 원본 바이트 보존**: 컨테이너에서 꺼낸 signedAttrsDer로 `SignatureBindingService.deriveChallenge` 재계산 → assemble 입력과 동일 challenge.
- **위치 검증**: 파싱된 signedAttrs(OCTET STRING) 안에 WebAuthnAssertionAttr OID가 **없음**, unsignedAttrs에 **있음**.

### 4.4 완료 기준
- 라운드트립·위치·challenge 재파생 테스트 통과.
- `./gradlew :kr-ades:kr-ades-cades:test` + 전체 `./gradlew build` 그린.

---

## 5. 설계서 §4.1 확정 교체문 (Code가 같은 커밋에 적용)

`docs/design/특허A-결속-검증라우터-설계.md` §4.1의 ASN.1 블록(현재 plain OPTIONAL)을 아래로 교체하고, 바로 밑에 결정 주석 추가:

```asn1
WebAuthnAssertionAttr ::= SEQUENCE {
    version            INTEGER (1),
    authenticatorData  OCTET STRING,
    clientDataJSON     OCTET STRING,   -- 원문 바이트 그대로 보관
    coseAlg            INTEGER,        -- 예: -7(ES256), -257(RS256)
    credentialId   [0] IMPLICIT OCTET STRING OPTIONAL,
    aaguid         [1] IMPLICIT OCTET STRING OPTIONAL
}
```
> 결정(2026-06-28): 동일 UNIVERSAL 타입 OPTIONAL 연속의 DER 디코딩 모호성 제거를 위해 IMPLICIT 컨텍스트 태그 적용.

---

## 6. Code 실행 순서 (사용자 Windows / Claude Code)

```bash
git checkout feat/claude-webauthn
./gradlew build                                   # 시작 그린 확인 (T1~T3)
# §4 스펙대로 container/WebAuthnAssertionAttr.java, container/WebAuthnCmsAssembler.java 작성
# §4.3 테스트 작성
# §5대로 설계서 §4.1 갱신
./gradlew :kr-ades:kr-ades-cades:test             # T4 단위테스트
./gradlew build                                   # 전체 그린
git add -A && git commit -m "feat(cades): T4 Container Binding — WebAuthnAssertionAttr + 모사 CMS 컨테이너 [claude]"
```

### Claude Code용 프롬프트 (복사용)
> `feat/claude-webauthn` 브랜치에서 특허-A **T4(Container Binding)** 1차를 구현한다.
> 스펙: `docs/design/특허A-T4-Cowork-Code-분담.md` §4·§5 를 그대로 따른다.
> 인코딩 DER(BouncyCastle), OPTIONAL은 IMPLICIT `[0]`/`[1]`. 어서션은 unsignedAttrs에만(순환참조 방지).
> `container/WebAuthnAssertionAttr.java`, `container/WebAuthnCmsAssembler.java` + 단위테스트 작성,
> 설계서 §4.1을 §5 확정문으로 갱신, `./gradlew build` 그린 후 `[claude]` 태그로 태스크 단위 커밋.
> 보안/암호 코드이므로 두 번 검토 후 작성한다.

---

## 7. 경계 요약

| | Cowork | Code |
|---|---|---|
| 특허 명세서·설계·스펙·계획 | ✅ | |
| `.java` 소스·테스트 | | ✅ |
| `./gradlew build`·테스트 실행 | (불가) | ✅ |
| git 커밋 | | ✅ |
| 문서(설계서/HANDOFF) 본문 작성 | ✅ | |
| 문서 변경의 커밋 | | ✅ |
