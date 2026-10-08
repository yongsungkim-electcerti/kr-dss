# 특허 명세서 v3.0-B
# (특허-A v5.0 · 특허-C v2.0 와 동일일 동시 출원)

---

## 발명의 명칭

WebAuthn 기반 전자서명 신원확인 및 인증서 발급 시스템 및 방법

---

# 관련 출원 참조

본 특허 출원은 동일일 출원된 다음 두 출원과 관련된다.
- 특허-A v5.0 : "WebAuthn 인증기 기반 전자서명 생성 및 검증 시스템 및 방법"
- 특허-C v2.0 : "전자서명 통합 신뢰목록 및 HSM Attestation 시스템 및 방법"

전자서명 생성·검증 메커니즘은 특허-A를, 신뢰목록 구조는 특허-C를 참조한다.

---

# 기술분야

본 발명은 전자서명 인증서 발급 인프라 기술에 관한 것으로, WebAuthn 등록 결과(Registration Result)를 PKI X.509 인증서와 결속하는 Registration Binding, 사용자 서비스 플랫폼을 보유하지 않는 인증기관(CA)이 복수의 등록 기관(RA)을 통해 단일 WebAuthn 등록 결과에 대한 복수의 인증서를 발급하는 Multi-RA 인프라, 단일 WebAuthn 등록에 기반한 인증서 생명주기(Lifecycle) 관리, 및 HSM 기반 원격 서명 인증서 발급 시 HSM Attestation Object를 발급 절차에 결합하는 방법에 관한 것이다.

---

# 배경기술

## Registration Binding의 부재

WebAuthn 등록 과정에서 인증기는 Credential Key Pair를 생성하고 공개키를 서버에 전달하나, WebAuthn 표준은 이 등록 결과를 PKI X.509 인증서와 결속하는 방법을 정의하지 않는다. 특히 Attestation Statement에 담긴 인증기 보안 등급 정보를 인증서 발급 정책에 체계적으로 반영하는 메커니즘이 부재하다.

또한 기존 방식에서는 동일 사용자가 복수의 RA 서비스를 이용하려면 RA마다 별도의 키 쌍 생성과 WebAuthn 등록이 필요하므로, 인증기 저장 공간 낭비와 사용자 부담이 발생한다.

## Multi-RA 환경의 rpId 바인딩 충돌

WebAuthn Credential은 등록 시 지정된 rpId에 고정된다. 사용자 플랫폼을 보유하지 않는 CA가 복수의 RA를 통해 서비스를 제공하면, 각 RA의 rpId에서만 Credential을 사용할 수 있어 단일 등록으로 복수 RA 서비스를 이용하는 것이 불가능하다.

## 인증서 생명주기와 Binding 관계 유지의 문제

인증서 갱신, 재발급, 폐지 시 기존 WebAuthn Credential과의 Binding 관계를 어떻게 유지·갱신·해제할 것인지에 대한 표준 방법론이 부재하다.

---

# 해결하고자 하는 과제

① WebAuthn 등록 결과(Registration Result)를 PKI X.509 인증서와 결속하되, Attestation 검증 결과를 인증서 발급 정책에 반영하는 Registration Binding 메커니즘을 제공한다.

② CA가 단일 WebAuthn RP로 동작하고 복수의 RA가 신원확인 및 서명 절차 위임을 담당하는 Multi-RA 인프라를 통해, 단일 WebAuthn 등록으로 복수의 RA에서 전자서명 서비스를 이용할 수 있도록 한다.

③ 인증서 갱신, 재발급, 정지, 폐지 등 인증서 생명주기 전반에서 WebAuthn Credential과의 Binding 관계를 유지·갱신·해제하는 Lifecycle 관리 메커니즘을 제공한다.

④ HSM 기반 원격 서명 인증서 발급 시 CSR과 HSM Attestation Object를 결합하여 서명키 생성 환경의 신뢰성을 암호학적으로 확인하는 발급 메커니즘을 제공한다.

---

# 발명의 구성

## 발명 B-1 : Registration Binding

### WebAuthn 등록 결과와 PKI 인증서의 결속

본 발명에서 Registration Binding의 대상은 단순히 Credential Public Key가 아니라, WebAuthn 등록 과정 전체의 결과(Registration Result)이다. Registration Result는 다음을 포함한다.

- Credential Public Key : 인증기가 생성한 공개키
- AAGUID : 인증기 모델 식별자
- Attestation Statement : 인증기 제조사의 암호학적 신뢰 증명
- Credential ID : 해당 인증기에서 Credential을 식별하는 식별자

인증기관(CA)은 Registration Result를 수신하여 Attestation Statement를 검증하고, 인증기 보안 등급을 판정한 후, Credential Public Key를 SubjectPublicKeyInfo로 사용하는 X.509 인증서를 발급한다. 발급된 인증서에는 검증 경로를 식별하는 정책 식별자와 인증기 보안 등급 정보를 포함한다.

```
Registration Result 처리 흐름:
1. WebAuthn 등록 → Registration Result 생성
2. Attestation Statement 암호학적 검증
3. AAGUID → 인증기 메타데이터 조회 → 보안 등급 판정
4. Credential Public Key → SubjectPublicKeyInfo
5. X.509 인증서 발급
   - certificatePolicies: WebAuthn 정책 OID (등급별 차등)
   - SubjectKeyIdentifier: Credential ID 기반 키 식별자
6. credentialId, publicKey, 인증서, AAGUID, Attestation 결과 저장
```

이 구조에서 Registration Binding은 다음 관계를 성립시킨다.

```
WebAuthn Credential ←→ X.509 인증서
(Credential Public Key = SubjectPublicKeyInfo)
(AAGUID → 인증기 등급 → Policy OID)
(Credential ID → SubjectKeyIdentifier)
```

종속항에서, 상기 Credential Public Key는 상기 X.509 인증서의 SubjectPublicKeyInfo로 사용되며, 상기 AAGUID는 인증기 메타데이터 레지스트리 조회를 통해 보안 등급으로 변환된다. 인증기 메타데이터 레지스트리의 구조는 특허-C를 참조한다.

## 발명 B-2 : Multi-RA 인프라

### CA 단일 WebAuthn RP 구조

CA는 모든 WebAuthn 등록 및 Assertion 생성을 CA 자신의 rpId 기준으로 수행하는 단일 WebAuthn RP로 동작한다. 각 RA는 신원확인부(Identity Verification Module)와 서명 위임부(Signature Delegation Module)로 구성된다. WebAuthn 등록 및 Assertion 생성 시 rpId는 항상 CA의 도메인으로 고정된다.

RA 유형 분류:
- 신원확인형 RA (Identity RA): 신원확인 기능 + 서비스 플랫폼 보유
- 플랫폼형 RA (Platform RA): 자체 인증 플랫폼의 인증 결과를 신원확인 근거로 활용

### 단일 등록 → 복수 인증서 구조

```
WebAuthn Credential Key Pair (1개)
  Credential Public Key (동일)
      │
      ├── 인증서-A : SubjectPublicKeyInfo = Credential PK
      │              certificatePolicies  = RA-A OID
      │              SubjectKeyIdentifier = KID (동일)
      │
      ├── 인증서-B : SubjectPublicKeyInfo = Credential PK
      │              certificatePolicies  = RA-B OID
      │              SubjectKeyIdentifier = KID (동일)
      │
      └── 인증서-N : ...
```

동일 Credential Key Pair에서 발급된 복수의 인증서는 SubjectKeyIdentifier 확장에 동일한 키 식별자 값을 포함하여 연관성을 명시한다.

## 발명 B-3 : 인증서 생명주기(Lifecycle) 관리

### 단일 WebAuthn Credential과 복수 인증서 간 Binding 관계 유지

WebAuthn Credential과 X.509 인증서 간 Binding 관계는 인증서 생명주기 전반에서 유지·갱신·해제된다.

**인증서 갱신(Renewal)**
기존 Credential이 유효한 경우, 새로운 WebAuthn 등록 없이 동일 Credential Public Key에 대한 새 인증서를 발급한다. 갱신 시 AAGUID 및 Attestation 재검증을 수행하여 인증기 보안 등급의 변동 여부를 확인한다.

```
갱신 흐름:
1. 갱신 요청 및 신원 재확인
2. credentialId로 기존 Credential 조회
3. AAGUID 및 Attestation 재검증
4. 보안 등급 변동 여부 확인 → Policy OID 재결정
5. 동일 SubjectPublicKeyInfo로 새 인증서 발급
6. 기존 인증서 폐지 (선택)
```

**인증서 재발급(Reissuance)**
인증기 분실, 손상 등으로 Credential이 유효하지 않은 경우, 신규 WebAuthn 등록 후 새 Credential Public Key로 인증서를 발급한다. 이 경우 기존 인증서는 폐지된다.

**인증서 정지(Suspension) 및 폐지(Revocation)**
특정 RA의 서비스 해지 또는 인증기 분실 시, 해당 RA의 인증서만 선택적으로 정지·폐지할 수 있다. 다른 RA의 인증서는 동일 Credential이 유효한 경우 계속 사용 가능하다.

**인증서 추가 발급(Add-on Issuance)**
기존 사용자가 추가 RA와 서비스 관계를 맺는 경우, 기존 Credential에 대한 새 인증서를 추가 발급한다. 신규 신원확인 절차 없이 기존 신원확인 결과를 재사용할 수 있다.

## 발명 B-4 : HSM 원격 서명 인증서 발급 연계

HSM 기반 원격 서명 인증서 발급 시, CSR(PKCS#10)과 함께 HSM Attestation Object를 제출한다. 인증기관은 HSM Attestation Object를 검증하여 서명키가 HSM 내에서 생성·보호됨을 암호학적으로 확인한 후 인증서를 발급한다. HSM Attestation Object의 구조 및 검증 방법은 특허-C를 참조한다.

HSM Attestation 검증 결과(보안 등급, 키 추출불가 여부)는 인증서의 정책 식별자에 반영된다.

---

# 발명의 효과

① **N개 RA 단일 등록 처리**: N개 RA 서비스에 대해 WebAuthn 등록 1회로 처리한다. 기존 RA별 개별 등록 방식(N회 등록) 대비 사용자 등록 부담을 1/N 수준으로 감소시킨다.

② **인증기 저장 공간 최소화**: 단일 Credential Key Pair로 복수의 RA 서비스를 지원하므로, 인증기 내 저장되는 Credential 수를 최소화한다. 특히 저장 공간이 제한된 하드웨어 보안 키(FIDO2 Security Key)에서 효과가 크다.

③ **SubjectKeyIdentifier 기반 O(1) 연관 조회**: SubjectKeyIdentifier에 동일 값을 포함하여, 단일 Credential에서 발급된 N개 인증서 간 연관성을 O(1)으로 조회한다.

④ **선택적 Lifecycle 제어**: RA별 인증서를 독립적으로 정지·폐지할 수 있어, 특정 RA 서비스 해지 시 다른 RA의 서비스에 영향이 없다.

⑤ **HSM 서명키 생성 환경 암호학적 증명**: HSM Attestation Object를 통해 서명키가 HSM 내에서 생성·보호됨을 암호학적으로 증명한다. 기존 CC/FIPS 문서 기반 방식 대비 실시간 검증이 가능하다.

⑥ **인증기 보안 등급 자동 반영**: AAGUID 조회를 통해 인증기 보안 등급을 자동으로 판정하고 Policy OID를 차등 부여하므로, 수동 등급 심사 없이 정책 적용이 가능하다.

---

# 도면의 간단한 설명

도 1 : Registration Binding — WebAuthn 등록 결과(Registration Result) 처리 및 X.509 인증서 발급 흐름
도 2 : Multi-RA 구조 — CA 단일 WebAuthn RP, RA 신원확인·위임, 복수 인증서 발급
도 3 : 복수 인증서 구조 — 1 Registration → N 인증서, SubjectKeyIdentifier 연관 식별자
도 4 : 인증서 생명주기 관리 — 갱신·재발급·정지·폐지·추가 발급 (state diagram)
도 5 : HSM 원격 서명 인증서 발급 — CSR + HSM Attestation Object 결합 흐름

---

# 청구항

## ════════════════════════════════════════
## LEVEL 1 : FRAMEWORK — 독립항 (4개)
## ════════════════════════════════════════

## 청구항 1 (독립항 — Registration Binding 시스템)

WebAuthn 기반 전자서명 인증서 발급 시스템으로서,

WebAuthn 등록 결과(Registration Result)를 PKI X.509 인증서와 결속하되, 상기 인증서에 전자서명 검증 경로를 식별하는 정책 식별자를 포함시키는 인증서 결속부; 및

WebAuthn 등록 과정에서 취득한 장치 식별 정보 및 Attestation 검증 결과를 기반으로 서명 생성 장치의 보안 등급을 판정하고, 상기 보안 등급을 인증서 발급 정책에 반영하는 장치 등급 판정부;

를 포함하는 WebAuthn 기반 전자서명 인증서 발급 시스템.

---

## 청구항 2 (독립항 — Multi-RA 인프라 시스템)

WebAuthn 기반 다중 등록 기관 전자서명 인프라 시스템으로서,

사용자 신원확인을 수행하고 신원확인 결과를 인증기관(CA)에 전달하는 복수의 등록 기관(RA);

단일 WebAuthn 신뢰 당사자(rpId)로 동작하며, 상기 복수의 RA로부터 신원확인 결과를 수신하여 단일 WebAuthn 등록 결과에 대한 복수의 X.509 인증서를 발급하는 인증기관(CA); 및

상기 복수의 X.509 인증서 각각이 동일한 SubjectPublicKeyInfo와 해당 RA를 식별하는 서로 다른 인증서 정책 식별자를 포함하도록 제어하는 인증서 발급 제어부;

를 포함하는 다중 등록 기관 전자서명 인프라 시스템.

---

## 청구항 3 (독립항 — 방법)

WebAuthn 기반 전자서명 인증서 발급 방법으로서,

WebAuthn 등록 결과(Registration Result)를 PKI X.509 인증서와 결속하되, 장치 식별 정보 및 Attestation 검증 결과를 기반으로 서명 생성 장치의 보안 등급을 판정하여 인증서 발급 정책에 반영하는 단계;

단일 WebAuthn 신뢰 당사자(rpId)로 동작하는 인증기관(CA)이 복수의 등록 기관(RA)으로부터 신원확인 결과를 수신하고, 단일 WebAuthn 등록 결과에 대하여 각 RA에 대응하는 복수의 X.509 인증서를 발급하되, 각 인증서가 동일한 SubjectPublicKeyInfo와 해당 RA를 식별하는 서로 다른 인증서 정책 식별자를 포함하도록 발급하는 단계; 및

상기 단일 WebAuthn 등록 결과와 결속된 복수의 X.509 인증서에 대한 생명주기(Lifecycle)를 관리하는 단계;

를 포함하는 전자서명 인증서 발급 방법.

---

## 청구항 4 (독립항 — 컴퓨터 판독 가능한 기록매체)

청구항 3에 따른 방법을 컴퓨터에서 실행하기 위한 프로그램이 기록된 컴퓨터 판독 가능한 기록매체.

---

## ════════════════════════════════════════
## LEVEL 2 : BINDING MECHANISM — 중위 종속항
## ════════════════════════════════════════

## 청구항 5 (종속항 — Registration Result 구성요소)

청구항 1에 있어서,

상기 WebAuthn 등록 결과는 인증기가 생성한 Credential Public Key, AAGUID, Attestation Statement 및 Credential ID를 포함하며, 상기 인증서 결속부는 상기 Credential Public Key를 X.509 인증서의 SubjectPublicKeyInfo로 사용하는 것을 특징으로 하는 전자서명 인증서 발급 시스템.

---

## 청구항 6 (종속항 — CA 단일 rpId + RA 위임)

청구항 2에 있어서,

상기 복수의 RA 각각은 사용자 신원확인을 수행하는 신원확인부 및 WebAuthn 등록과 전자서명 생성 절차를 상기 CA에 위임하는 서명 위임부를 포함하며, WebAuthn 등록 및 Assertion 생성 시 rpId는 상기 CA의 도메인으로 고정되는 것을 특징으로 하는 다중 등록 기관 전자서명 인프라 시스템.

---

## 청구항 7 (종속항 — RA 유형 분류)

청구항 2에 있어서,

상기 복수의 RA는 사용자 신원확인 기능과 서비스 플랫폼을 보유하며 신원확인 결과를 상기 CA에 전달하는 신원확인형 RA(Identity RA), 및 자체 사용자 인증 플랫폼의 인증 결과를 신원확인 근거로 활용하는 플랫폼형 RA(Platform RA) 중 적어도 하나를 포함하는 것을 특징으로 하는 다중 등록 기관 전자서명 인프라 시스템.

---

## 청구항 8 (종속항 — Lifecycle: 갱신 시 Attestation 재검증)

청구항 1에 있어서,

상기 인증서 결속부는 인증서 갱신 요청 시 AAGUID 및 Attestation을 재검증하여 서명 생성 장치의 보안 등급 변동 여부를 확인하고, 변동된 보안 등급에 따라 갱신 인증서의 정책 식별자를 재결정하는 것을 특징으로 하는 전자서명 인증서 발급 시스템.

---

## 청구항 9 (종속항 — Lifecycle: 선택적 폐지)

청구항 2에 있어서,

상기 인증서 발급 제어부는 복수의 RA 중 특정 RA에 대응하는 인증서를 선택적으로 정지 또는 폐지할 수 있으며, 해당 RA의 인증서 정지·폐지는 동일 Credential Key Pair에 기반한 다른 RA의 인증서에 영향을 주지 않는 것을 특징으로 하는 다중 등록 기관 전자서명 인프라 시스템.

---

## 청구항 10 (종속항 — HSM 원격 서명 인증서 발급)

청구항 1에 있어서,

상기 인증서 결속부는 HSM 기반 원격 서명 인증서 발급 시 CSR(Certificate Signing Request)과 함께 HSM Attestation Object를 수신하고, 상기 HSM Attestation Object를 검증하여 서명키가 HSM 내에서 생성·보호됨을 확인한 후 인증서를 발급하는 것을 특징으로 하는 전자서명 인증서 발급 시스템.

---

## ════════════════════════════════════════
## LEVEL 3 : IMPLEMENTATION — 하위 종속항
## ════════════════════════════════════════

## 청구항 11 (종속항 — Attestation 등급 → Policy OID 차등)

청구항 1에 있어서,

상기 장치 등급 판정부는 AAGUID 조회를 통해 확인된 서명 생성 장치의 보안 등급에 따라 서로 다른 인증서 정책 식별자(OID)를 인증서에 차등 부여하는 것을 특징으로 하는 전자서명 인증서 발급 시스템.

---

## 청구항 12 (종속항 — FIDO MDS 조회, 일실시예)

청구항 1에 있어서,

상기 장치 등급 판정부는 AAGUID를 인증기 메타데이터 레지스트리에서 조회하여 인증기의 보안 등급, 인증 방식 및 하드웨어 보안 수준을 확인하되, 일실시예에서 상기 인증기 메타데이터 레지스트리는 FIDO Alliance가 운영하는 FIDO Metadata Service(MDS)인 것을 특징으로 하는 전자서명 인증서 발급 시스템.

---

## 청구항 13 (종속항 — SubjectKeyIdentifier 연관 식별자)

청구항 2에 있어서,

상기 복수의 X.509 인증서는 SubjectKeyIdentifier 확장에 동일한 키 식별자 값을 포함하여 단일 WebAuthn 등록 결과에서 발급된 인증서임을 식별할 수 있도록 하는 것을 특징으로 하는 다중 등록 기관 전자서명 인프라 시스템.

---

## 청구항 14 (종속항 — Lifecycle: 추가 RA 인증서 발급)

청구항 2에 있어서,

상기 인증서 발급 제어부는 이미 등록된 사용자가 추가 RA와 서비스 관계를 맺는 경우, 추가 신원확인 절차 없이 기존 WebAuthn 등록 결과에 대하여 해당 추가 RA에 대응하는 인증서를 추가 발급하되, 상기 추가 인증서는 기존 발급된 인증서와 동일한 SubjectPublicKeyInfo 및 SubjectKeyIdentifier를 포함하는 것을 특징으로 하는 다중 등록 기관 전자서명 인프라 시스템.

---

## 청구항 15 (종속항 — HSM Attestation 결과 Policy OID 반영)

청구항 10에 있어서,

상기 인증서 결속부는 HSM Attestation Object 검증 결과에서 확인된 HSM 보안 등급에 따라 서로 다른 인증서 정책 식별자(OID)를 인증서에 차등 부여하되, HSM Attestation Object의 구조 및 검증 방법은 동일일 출원된 특허-C를 참조하는 것을 특징으로 하는 전자서명 인증서 발급 시스템.

---

# 청구항 계층도

```
독립항 1 (Registration Binding 시스템)
├── [L2] 청구항  5  Registration Result 구성: Credential PK + AAGUID + Attestation
├── [L2] 청구항  8  Lifecycle: 갱신 시 Attestation 재검증 + Policy OID 재결정
├── [L2] 청구항 10  HSM 원격 서명: CSR + HSM Attestation Object
├── [L3] 청구항 11  Attestation 등급 → Policy OID 차등
├── [L3] 청구항 12  FIDO MDS 조회 (일실시예)
└── [L3] 청구항 15  HSM Attestation 결과 → Policy OID

독립항 2 (Multi-RA 인프라 시스템)
├── [L2] 청구항  6  CA 단일 rpId + RA 신원확인·위임
├── [L2] 청구항  7  RA 유형: 신원확인형 / 플랫폼형
├── [L2] 청구항  9  Lifecycle: 선택적 폐지 (타 RA 인증서 무영향)
└── [L3] 청구항 13  SubjectKeyIdentifier 연관 식별자
│   청구항 14  Lifecycle: 추가 RA 인증서 추가 발급

독립항 3 (방법) ← 1+2 통합 + Lifecycle
독립항 4 (기록매체) ← 청구항 3 기반
```

---

# 변경 이력

| 버전    | 날짜       | 변경 내용 |
|---------|------------|-----------|
| v2.0-B  | 2026-06-28 | 3특허 재배치. Registration Binding(A에서 이관), Multi-RA, HSM 인증서 발급 연계. |
| v3.0-B  | 2026-06-28 | 독립항 강화: "WebAuthn 등록 결과(Registration Result)"로 추상화. Lifecycle 관리 전면 추가(청구항 3·8·9·14): 갱신·재발급·정지·폐지·추가 발급. 기술효과 정량화. RA별 선택적 폐지 명시. |
