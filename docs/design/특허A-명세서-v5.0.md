# 특허 명세서 v5.0-A
# (특허-B v3.0 · 특허-C v2.0 와 동일일 동시 출원)

---

## 발명의 명칭

WebAuthn 인증기 기반 전자서명 생성 및 검증 시스템 및 방법

---

# 관련 출원 참조

본 특허 출원은 동일일 출원된 다음 두 출원과 관련된다.
- 특허-B : "WebAuthn 기반 전자서명 신원확인 및 인증서 발급 시스템 및 방법"
- 특허-C : "전자서명 통합 신뢰목록 및 HSM Attestation 시스템 및 방법"

인증서 발급 인프라(Registration Binding, Multi-RA)의 상세 사항은 특허-B를, 신뢰목록 구조(KR-TL, HSM Attestation)의 상세 사항은 특허-C를 참조한다.

---

# 기술분야

본 발명은 전자서명 기술에 관한 것으로, 보다 상세하게는 전자문서, 서명 시각 및 서명자 인증서를 단일 WebAuthn Challenge에 암호학적으로 결속(Signature Binding)하여 전자서명을 생성하고, 인증서 정책 식별자에 따라 검증 알고리즘을 런타임에 동적으로 선택하는 정책 기반 검증 라우터(Policy-Based Verification Router)를 이용하여 전자서명을 검증하는 시스템 및 방법에 관한 것이다.

---

# 배경기술

기존 전자서명 기술은 PKI 기반 인증서와 개인키를 이용하여 CMS(CAdES), PDF(PAdES), XML(XAdES) 등의 전자서명을 생성한다. 이러한 방식은 사용자 단말에 별도의 보안 프로그램 설치를 요구하며, 기존 검증 시스템은 단일한 검증 경로만 지원하는 구조로 설계되어 있다.

최근 금융당국은 설치형 보안 소프트웨어의 단계적 철폐를 추진하고 있으나, 웹 표준 API인 WebAuthn은 사용자 인증을 위한 규격으로 설계되어 있어 PKI 기반 전자서명 체계와 직접 통합하기 위한 기술 방법론이 부재하다.

WebAuthn 인증기에서 생성되는 Assertion의 서명 입력은 다음과 같이 고정된다.

```
SigningInput = authenticatorData ∥ H(clientDataJSON)
```

이는 기존 CMS 계열 전자서명이 요구하는 H(Encode(signedAttrs))와 구조적으로 다르므로, WebAuthn Assertion을 기존 전자서명 검증 경로에 그대로 적용할 수 없다. 특히 다음 문제가 존재한다.

첫째, WebAuthn Challenge는 임의의 바이트열이므로, 전자문서·서명 시각·서명자 인증서와의 암호학적 결속 메커니즘이 없으면 Assertion의 재사용(replay attack) 및 문서 대체(document substitution) 공격에 취약하다.

둘째, 기존 검증 시스템은 단일 검증 경로만 지원하므로, WebAuthn 기반 전자서명, HSM 기반 원격 서명, 표준 전자서명을 단일 시스템에서 처리하기 위한 검증 경로 동적 선택 메커니즘이 부재하다.

셋째, 다양한 해시 알고리즘(SHA-256, SHA-3, SM3 등) 및 미래 표준 변화에 대응하는 암호 민첩성(Crypto Agility) 구조가 정의된 바 없다.

---

# 해결하고자 하는 과제

① 전자문서, 서명 시각, 서명자 인증서를 단일 WebAuthn Challenge에 암호학적으로 결속하는 Signature Binding 메커니즘을 제공하여, Assertion의 재사용 공격 및 문서 대체 공격을 원천 차단한다.

② 인증서 정책 식별자에 기반하여 검증 알고리즘을 런타임에 동적으로 선택하는 정책 기반 검증 라우터(Policy-Based Verification Router)를 통해, 단일 검증 시스템에서 다중 전자서명 유형을 처리한다.

③ 해시 알고리즘 및 컨테이너 포맷에 독립적인 암호 민첩성(Crypto Agility) 구조를 정의하여, 미래 표준 변화에 대응한다.

④ 설치형 보안 소프트웨어 없이 브라우저 WebAuthn API만으로 전자서명을 수행·검증한다.

---

# 발명의 구성

## 1. Signature Binding — 3요소 암호학적 결속

본 발명의 핵심은 전자문서, 서명 시각, 서명자 인증서라는 세 가지 요소를 단일 WebAuthn Challenge에 암호학적으로 결속하는 것이다. 이를 위해 서버는 다음 속성을 포함하는 서명 대상 속성(SignedAttrs)을 구성한다.

- messageDigest      : H(전자문서) — 문서 결속
- signingTime        : 서명 시각   — 시각 결속 (replay 방지)
- signingCertificateV2 : 서명자 인증서 해시 — 서명자 결속

SignedAttrs를 소정의 인코딩 방식으로 변환한 후, 상기 인코딩값에 대한 해시 연산으로 WebAuthn Challenge를 생성한다.

```
Challenge = HashFunc(Encode(SignedAttrs))
```

일실시예에서:
```
Challenge = BASE64URL(SHA-256(DER(SignedAttrs)))
```

WebAuthn 인증기는 다음 서명 입력으로 서명값(Assertion)을 생성한다.
```
SigningInput = authenticatorData ∥ H(clientDataJSON)
```
여기서 clientDataJSON.challenge 필드는 상기 Challenge 값을 포함한다.

이 구조의 보안 특성:
- **문서 전용성**: Challenge가 H(전자문서)를 포함하는 SignedAttrs의 해시이므로, 다른 문서에 대해 동일 Challenge가 생성되지 않는다.
- **시간 결속**: signingTime을 포함하므로, 동일 문서라도 서명 시각이 다르면 Challenge가 달라진다.
- **서명자 결속**: signingCertificateV2를 포함하므로, 다른 서명자의 인증서로 Assertion을 재사용할 수 없다.

### 암호 민첩성(Crypto Agility)

상기 HashFunc와 Encode는 사전 협의된 정책에 따라 SHA-256, SHA-3, SM3 등 다양한 해시 알고리즘과 DER, BER, JSON 등 다양한 인코딩 방식으로 대체 가능하다. 사용된 알고리즘 식별자는 전자서명 객체의 서명 알고리즘 속성(signatureAlgorithm)에 기재된다.

## 2. Container Binding — Assertion-전자서명 객체 결속

WebAuthn Assertion 과정에서 생성된 Assertion 데이터(authenticatorData, clientDataJSON, 서명값, Credential ID, COSE 알고리즘 식별자, AAGUID)는 전자서명 객체의 비서명 속성(unsignedAttrs) 영역에 저장한다.

### 순환참조 방지 설계

Challenge가 SignedAttrs의 인코딩값으로부터 생성되므로, Assertion 데이터를 SignedAttrs에 포함하면 순환참조가 발생한다. 이를 방지하기 위해 Assertion 데이터는 unsignedAttrs에 저장한다. WebAuthn 서명이 authenticatorData 및 clientDataJSON의 무결성을 보호하므로 전자서명 객체 전체의 무결성이 유지된다.

```asn1
WebAuthnAssertionAttr ::= SEQUENCE {
    version           INTEGER (1),
    authenticatorData OCTET STRING,
    clientDataJSON    OCTET STRING,   -- 원문 바이트 그대로 보관
    coseAlg           INTEGER,
    credentialId      OCTET STRING OPTIONAL,
    aaguid            OCTET STRING OPTIONAL
}
```

일실시예에서, 전자서명 객체는 CMS SignedData 기반 컨테이너를 사용한다. 동일 구조는 JAdES, COSE, CBOR 등 다른 컨테이너 형식에도 적용 가능하다.

## 3. Policy-Based Verification Router — 정책 기반 검증 라우터

본 발명의 검증부는 단순한 OID 확인이 아니라, 인증서 정책 식별자를 기반으로 검증 알고리즘 전체를 런타임에 동적으로 선택·제어하는 정책 기반 검증 라우터(Policy-Based Verification Router)로 동작한다.

기존 검증 시스템은 하나의 고정된 검증 알고리즘만 실행할 수 있다. 본 발명의 검증 라우터는 서명자 인증서의 정책 식별자, 장치 등급, 인증서 속성을 조합하여 최적 검증 알고리즘을 선택한다.

```
검증 라우터 입력:
  - 서명자 인증서의 certificatePolicies OID
  - 인증서 내 장치 등급 속성 (선택)
  - 검증 정책 설정

검증 라우터 출력:
  - 선택된 검증 알고리즘 (WebAuthn / HSM / 표준)
  - 적용할 신뢰목록 (특허-C 참조)
  - 검증 결과 분류 기준
```

### WebAuthn 기반 검증 경로

```
1.  SignedAttrs 인코딩값의 HashFunc 재계산
2.  clientDataJSON.challenge 와 재계산값 비교 → 불일치: TOTAL_FAILED
3.  clientDataJSON.type = "webauthn.get" 확인
4.  clientDataJSON.origin 확인
5.  authenticatorData.rpIdHash 확인
6.  authenticatorData.UP / UV 플래그 확인
7.  Verify(pubKey, authenticatorData ∥ H(clientDataJSON), signature)
8.  messageDigest 와 H(전자문서) 비교 → 불일치: TOTAL_FAILED
9.  signingCertificateV2 결속 확인
10. 인증서 경로, 신뢰목록, 폐지, 서명시점 검증 (특허-C 참조)
```

### HSM 기반 원격 서명 검증 경로

```
1.  SignedAttrs 인코딩값의 HashFunc 재계산
2.  재계산값에 대한 표준 비대칭 서명 검증
3.  messageDigest 와 H(전자문서) 비교
4.  signingCertificateV2 결속 확인
5.  인증서 경로, 신뢰목록, 폐지, 서명시점 검증 (특허-C 참조)
6.  HSM Attestation 데이터 확인 (선택, 특허-C 참조)
```

### 검증 결과 분류

```
TOTAL_PASSED    : 서명 검증, 3요소 결속, 문서 해시, 인증서 경로·신뢰목록·폐지·시점 모두 성공
INDETERMINATE   : 암호학적 서명 검증 성공, 신뢰목록·폐지·시점·장치 신뢰 일부 불충분
TOTAL_FAILED    : 서명 검증 실패, 결속 불일치, 해시 불일치, 인증서 경로 실패 중 하나
```

---

# 발명의 효과

① **설치형 소프트웨어 완전 제거**: 브라우저 표준 API(WebAuthn)만으로 전자서명을 수행하므로 클라이언트 측 추가 실행 코드가 0%이다. 기존 설치형 보안 소프트웨어 방식 대비 클라이언트 배포·유지보수 비용을 제거한다.

② **Replay Attack 원천 차단**: Challenge가 H(전자문서) + signingTime + 서명자 인증서 해시를 포함하는 SignedAttrs의 해시로 생성되므로, 동일 Assertion의 다른 문서·시각·서명자에 대한 재사용이 암호학적으로 불가능하다.

③ **단일 검증 시스템의 다중 유형 처리**: 정책 기반 검증 라우터를 통해 WebAuthn 기반 로컬 서명, HSM 기반 원격 서명, 표준 전자서명을 단일 검증 인프라에서 처리한다. 기존 방식에서는 유형별로 별도 검증 시스템 구축이 필요하였다.

④ **암호 민첩성(Crypto Agility)**: 해시 알고리즘(SHA-256, SHA-3, SM3 등) 및 인코딩 방식을 정책에 따라 교체 가능하므로, 특정 알고리즘 취약점 발견 시 서비스 중단 없이 알고리즘을 전환할 수 있다.

⑤ **컨테이너 포맷 독립성**: 동일한 결속 구조를 CMS, JAdES, COSE 등 다양한 컨테이너 포맷에 적용할 수 있어, 미래 표준 변화에도 권리범위가 유지된다.

---

# 도면의 간단한 설명

도 1 : Signature Binding — 3요소(문서·시각·인증서) → SignedAttrs → Challenge → Assertion 결속 흐름
도 2 : Container Binding — WebAuthnAssertionAttr 구조 및 unsignedAttrs 저장 (순환참조 방지)
도 3 : Policy-Based Verification Router — 정책 식별자 기반 검증 알고리즘 동적 선택 구조
도 4 : WebAuthn 기반 검증 경로 상세 절차 (flowchart)
도 5 : HSM 기반 원격 서명 검증 경로 상세 절차 (flowchart)

---

# 청구항

## ════════════════════════════════════════
## LEVEL 1 : FRAMEWORK — 독립항 (3개)
## ════════════════════════════════════════

## 청구항 1 (독립항 — 전자서명 시스템)

WebAuthn 인증기 기반 전자서명 시스템으로서,

전자문서의 해시값, 서명 시각 및 서명자 인증서 정보를 포함하는 서명 대상 속성(SignedAttrs)을 구성하고, 상기 서명 대상 속성의 인코딩값에 대한 해시 연산으로 WebAuthn Challenge를 생성함으로써, 전자문서·서명 시각·서명자 인증서를 단일 WebAuthn Challenge에 암호학적으로 결속하는 서명 결속부(Signature Binding Module);

상기 Challenge에 대응하는 WebAuthn Assertion을 수신하고, 상기 서명 대상 속성과 상기 Assertion을 포함하는 전자서명 객체를 생성하는 전자서명 객체 생성부; 및

상기 전자서명 객체에서 추출된 서명자 인증서의 정책 식별자에 기반하여, 검증 알고리즘을 런타임에 동적으로 선택하고 검증 절차를 제어하는 정책 기반 검증 라우터(Policy-Based Verification Router);

를 포함하는 전자서명 시스템.

---

## 청구항 2 (독립항 — 전자서명 방법)

WebAuthn 인증기 기반 전자서명 방법으로서,

전자문서의 해시값, 서명 시각 및 서명자 인증서 정보를 포함하는 서명 대상 속성(SignedAttrs)을 구성하고, 상기 서명 대상 속성의 인코딩값에 대한 해시 연산으로 WebAuthn Challenge를 생성함으로써, 전자문서·서명 시각·서명자 인증서를 단일 WebAuthn Challenge에 암호학적으로 결속하는 단계;

상기 Challenge에 대응하는 WebAuthn Assertion을 수신하고, 상기 서명 대상 속성과 상기 Assertion을 포함하는 전자서명 객체를 생성하는 단계; 및

상기 전자서명 객체에서 추출된 서명자 인증서의 정책 식별자에 기반하여 검증 알고리즘을 런타임에 동적으로 선택하고 검증을 수행하는 단계;

를 포함하는 전자서명 방법.

---

## 청구항 3 (독립항 — 컴퓨터 판독 가능한 기록매체)

청구항 2에 따른 전자서명 방법을 컴퓨터에서 실행하기 위한 프로그램이 기록된 컴퓨터 판독 가능한 기록매체.

---

## ════════════════════════════════════════
## LEVEL 2 : BINDING MECHANISM — 중위 종속항
## ════════════════════════════════════════

## 청구항 4 (종속항 — Signature Binding: 인코딩값 해시)

청구항 1에 있어서,

상기 서명 결속부는 상기 서명 대상 속성의 인코딩값의 해시값을 WebAuthn Challenge로 생성하되, 사용되는 해시 알고리즘은 SHA-256, SHA-3, SM3 중 적어도 하나를 포함하는 암호 민첩성(Crypto Agility) 구조를 지원하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 5 (종속항 — Container Binding: unsignedAttrs)

청구항 1에 있어서,

상기 전자서명 객체 생성부는 상기 WebAuthn Assertion 데이터를 전자서명 객체의 비서명 속성(unsignedAttrs) 영역에 저장하되, 상기 비서명 속성은 상기 서명 대상 속성에 의해 직접 서명되지 않으며, 상기 WebAuthn Assertion의 서명값이 authenticatorData 및 clientDataJSON의 무결성을 보호하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 6 (종속항 — Policy Routing Mechanism: certificatePolicies)

청구항 1에 있어서,

상기 정책 기반 검증 라우터는 서명자 인증서의 인증서 정책 확장(certificatePolicies)에 포함된 정책 식별자를 확인하여 WebAuthn 기반 검증 알고리즘, HSM 기반 원격 서명 검증 알고리즘 및 표준 전자서명 검증 알고리즘 중 하나를 선택하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 7 (종속항 — Policy Engine: 다중 속성 조합)

청구항 6에 있어서,

상기 정책 기반 검증 라우터는 정책 식별자 외에 인증서 내 장치 등급 속성 및 검증 정책 설정을 추가로 참조하여 검증 알고리즘을 선택하는 정책 엔진(Policy Engine)을 포함하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 8 (종속항 — Signature Binding Mechanism, 방법)

청구항 2에 있어서,

상기 결속하는 단계는, 상기 서명 대상 속성의 인코딩값의 해시값을 WebAuthn Challenge로 생성하되, 사용되는 해시 알고리즘은 사전에 협의된 정책에 따라 교체 가능한 것을 특징으로 하는 전자서명 방법.

---

## ════════════════════════════════════════
## LEVEL 3 : IMPLEMENTATION — 하위 종속항
## ════════════════════════════════════════

## 청구항 9 (종속항 — SHA-256(DER(SignedAttrs)))

청구항 4에 있어서,

상기 Challenge는 다음 식에 따라 생성되는 것을 특징으로 하는 전자서명 시스템.
```
Challenge = BASE64URL(SHA-256(DER(SignedAttrs)))
```

---

## 청구항 10 (종속항 — WebAuthnAssertionAttr ASN.1)

청구항 5에 있어서,

상기 비서명 속성은 authenticatorData, clientDataJSON, WebAuthn 서명값, Credential ID, COSE 알고리즘 식별자 및 AAGUID를 포함하는 WebAuthnAssertionAttr 구조로 저장되는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 11 (종속항 — CMS SignedData 컨테이너)

청구항 5에 있어서,

상기 전자서명 객체는 CMS SignedData 기반 컨테이너를 사용하여 상기 서명 대상 속성(signedAttrs) 및 상기 비서명 속성(unsignedAttrs)을 저장하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 12 (종속항 — WebAuthn 기반 검증 경로 상세)

청구항 1에 있어서,

상기 정책 기반 검증 라우터가 WebAuthn 기반 검증 알고리즘을 선택한 경우, 상기 서명 대상 속성의 인코딩값의 해시값을 재계산하고 clientDataJSON의 challenge 필드와 비교하는 단계; clientDataJSON의 type 및 origin을 검증하는 단계; authenticatorData의 rpIdHash, UP 플래그 및 UV 플래그를 검증하는 단계; authenticatorData와 H(clientDataJSON)의 연접에 대한 WebAuthn 서명값을 인증서 공개키로 검증하는 단계; 및 서명 대상 속성의 messageDigest와 전자문서의 해시값을 비교하는 단계를 수행하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 13 (종속항 — HSM 원격 서명 검증 경로)

청구항 1에 있어서,

상기 정책 기반 검증 라우터가 HSM 기반 원격 서명 검증 알고리즘을 선택한 경우, 상기 서명 대상 속성의 인코딩값의 해시값을 재계산하는 단계; 재계산값에 대한 서명값을 인증서 공개키로 표준 비대칭 서명 알고리즘에 따라 검증하는 단계; 및 서명 대상 속성의 messageDigest와 전자문서의 해시값을 비교하는 단계를 수행하는 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 14 (종속항 — 검증 결과 분류)

청구항 1에 있어서,

상기 정책 기반 검증 라우터는 검증 결과를 TOTAL_PASSED, INDETERMINATE 및 TOTAL_FAILED로 분류하여 반환하되, TOTAL_PASSED는 서명 검증, 3요소 결속 확인, 문서 해시 일치, 인증서 경로·신뢰목록·폐지·시점 검증이 모두 성공한 경우이고, TOTAL_FAILED는 서명 검증 실패, 결속 불일치, 문서 해시 불일치 또는 인증서 경로 검증 실패 중 하나에 해당하는 경우이며, INDETERMINATE는 그 외 불충분한 경우인 것을 특징으로 하는 전자서명 시스템.

---

## 청구항 15 (종속항 — 방법: WebAuthn 검증 경로 상세)

청구항 2에 있어서,

상기 검증을 수행하는 단계에서 WebAuthn 기반 검증 알고리즘이 선택된 경우, 상기 서명 대상 속성의 인코딩값의 해시값을 재계산하고 clientDataJSON의 challenge 필드와 비교하는 단계; clientDataJSON의 type 및 origin을 검증하는 단계; authenticatorData의 rpIdHash, UP 플래그 및 UV 플래그를 검증하는 단계; authenticatorData와 H(clientDataJSON)의 연접에 대한 WebAuthn 서명값을 인증서 공개키로 검증하는 단계; 및 서명 대상 속성의 messageDigest와 전자문서의 해시값을 비교하는 단계를 포함하는 것을 특징으로 하는 전자서명 방법.

---

# 청구항 계층도

```
독립항 1 (시스템)
├── [L2] 청구항  4  Signature Binding : 인코딩값 해시 + Crypto Agility
│         └── [L3] 청구항  9  SHA-256(DER(SignedAttrs))
├── [L2] 청구항  5  Container Binding : unsignedAttrs + 순환참조 방지
│         ├── [L3] 청구항 10  WebAuthnAssertionAttr ASN.1
│         └── [L3] 청구항 11  CMS SignedData 컨테이너
├── [L2] 청구항  6  Policy Routing : certificatePolicies 기반 3방향 분기
│         └── [L2] 청구항  7  Policy Engine : OID + 장치 등급 + 검증 정책 조합
├──       청구항 12  WebAuthn 검증 경로 상세
├──       청구항 13  HSM 원격 서명 검증 경로
└──       청구항 14  TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED

독립항 2 (방법)
├── [L2] 청구항  8  Signature Binding + Crypto Agility (방법)
└──      청구항 15  WebAuthn 검증 경로 상세 (방법)

독립항 3 (기록매체) ← 청구항 2 기반
```

---

# 변경 이력

| 버전   | 날짜       | 변경 내용 |
|--------|------------|-----------|
| v4.0-A | 2026-06-28 | 3특허 재배치. Registration Binding → B 이관. HSM 검증 경로 추가. |
| v5.0-A | 2026-06-28 | 독립항 강화: "서명 결속부(Signature Binding Module)" + "정책 기반 검증 라우터(Policy-Based Verification Router)" 명명. Crypto Agility 종속항(청구항 4·8) 추가. Policy Engine 종속항(청구항 7) 추가. 기술효과 정량화. 3요소 결속 보안 특성 명시. |
