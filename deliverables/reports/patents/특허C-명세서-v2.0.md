# 특허 명세서 v2.0-C
# (특허-A v5.0 · 특허-B v3.0 와 동일일 동시 출원)

---

## 발명의 명칭

전자서명 통합 신뢰목록 및 HSM Attestation 시스템 및 방법

---

# 관련 출원 참조

본 특허 출원은 동일일 출원된 다음 두 출원과 관련된다.
- 특허-A v5.0 : "WebAuthn 인증기 기반 전자서명 생성 및 검증 시스템 및 방법"
- 특허-B v3.0 : "WebAuthn 기반 전자서명 신원확인 및 인증서 발급 시스템 및 방법"

전자서명 생성·검증 메커니즘(정책 기반 검증 라우터)은 특허-A를, 인증서 발급 인프라(Registration Binding, Multi-RA)는 특허-B를 참조한다. 본 명세서는 전자서명 신뢰체계 구조 자체를 정의한다.

---

# 기술분야

본 발명은 전자서명 신뢰체계 기술에 관한 것으로, 보다 상세하게는 다음 두 가지 독립적 발명으로 구성된다.

발명 C-1 : PKI 신뢰서비스 목록(EU-TL 방식)과 서명 생성 장치 신뢰목록(FIDO Attestation 기반 WebAuthn 인증기 + HSM Attestation 기반 HSM)을 통합하고, 신뢰목록 갱신 시 검증 정책을 자동 변경하는 통합 전자서명 신뢰목록(KR-TL) 시스템.

발명 C-2 : HSM이 FIDO Attestation에 상응하는 암호학적 장치 신뢰 증명(HSM Attestation Statement)을 생성하고, 검증기가 HSM Attestation Root CA를 신뢰 앵커로 이를 검증하는 HSM Attestation 시스템.

두 발명은 공통 기술사상인 "Attestation 기반 장치 신뢰 검증"으로 연결되며, KR-TL이 HSM Attestation 기반 장치를 포함함으로써 결합된다.

---

# 배경기술

## 전자서명 신뢰 검증의 이원화 문제

전자서명의 신뢰성은 두 차원에서 검증되어야 한다.

첫째는 신뢰서비스(Trust Service) 차원이다. 인증서를 발급한 인증기관(CA), 타임스탬프 기관(TSA), 인증서 상태 검증 서비스(OCSP) 등이 국가 공인 목록에 등재된 기관인지 확인하는 것이다. 유럽의 경우 eIDAS 규정에 따른 EU 신뢰목록(EU-TL)이 운영된다.

둘째는 서명 생성 장치 신뢰(Signing Device Trust) 차원이다. 전자서명이 생성된 장치(WebAuthn 인증기 또는 HSM)가 신뢰할 수 있는 기기인지 확인하는 것이다.

기존 검증 시스템은 신뢰서비스 차원만을 검증하며, 서명 생성 장치의 신뢰성은 검증하지 못한다. 또한 신뢰목록 갱신 시 검증 정책을 수동으로 변경해야 하는 운영 부담이 존재한다.

## FIDO Attestation과 HSM 신뢰 모델의 비대칭

FIDO Attestation은 인증기 제조사가 인증기의 진위 및 보안 수준을 암호학적으로 증명하는 메커니즘이다. 반면 HSM 신뢰 증명은 CC EAL / FIPS 인증서 제출이라는 정적·문서 기반 방식에 의존하며, 특정 HSM 인스턴스가 실제로 해당 인증을 받은 기기인지, 서명키가 실제로 그 HSM 내에서 생성·보호되는지를 암호학적으로 증명할 수 없다.

---

# 해결하고자 하는 과제

① PKI 신뢰서비스 목록과 서명 생성 장치 신뢰목록(WebAuthn 인증기 + HSM 통합)을 하나의 통합 신뢰목록(KR-TL)으로 구성하여, 신뢰서비스 차원과 서명 생성 장치 차원의 이중 보증을 단일 검증 절차 내에서 수행한다.

② 신뢰목록 갱신 시 검증 정책을 자동 변경하는 정책 자동 갱신(Policy Auto-Update) 메커니즘을 제공하여, 수동 정책 변경 없이 신뢰 기준 변화를 검증 시스템에 반영한다.

③ HSM이 FIDO Attestation에 상응하는 암호학적 신뢰 증명(HSM Attestation Statement)을 생성하는 구조를 정의하여, WebAuthn 인증기와 HSM을 동일한 신뢰 프레임워크 내에서 통합 검증한다.

④ HSM Attestation Root CA를 신뢰 앵커로 사용하여, 특정 HSM 장치 인스턴스의 진위와 서명키의 비추출성(non-extractability)을 암호학적으로 검증한다.

---

# 발명의 구성

## 발명 C-1 : KR-TL — 전자서명 통합 신뢰목록

### 이중 계층 구조

```
KR-TL (통합 전자서명 신뢰목록)
│
├── Layer 1 : 신뢰서비스 목록 (Trust Service List)
│   방식    : EU-TL (EU Trusted List) 준용
│   대상    : CA, TSA, OCSP 등 PKI 신뢰서비스 제공자
│   역할    : 인증서 발급 기관 수준 국가 공인 확인
│
└── Layer 2 : 서명 생성 장치 신뢰목록 (Signing Device Trust List)
    대상    : WebAuthn 인증기 (AAGUID 기반, FIDO Attestation)
              + 원격 서명 HSM (hsmDeviceId 기반, HSM Attestation)
    역할    : 서명 생성 장치 수준 신뢰성·보안 등급 확인
```

### Layer 1 — 신뢰서비스 목록

국가기관 또는 정부 위탁기관이 관리하며 PKI 신뢰서비스 제공자를 등록한다.

등록 항목: 서비스 제공자 명칭·식별자, 서비스 유형(CA/TSA/OCSP), 인증서 정책, 서비스 상태(활성/정지/폐지), 공개키 정보, 서비스 시작·종료 일시.

일실시예에서, 신뢰서비스 목록 구조는 eIDAS 규정에 따른 EU 신뢰목록(EU-TL) 체계를 준용한다.

### Layer 2 — 서명 생성 장치 신뢰목록

**WebAuthn 인증기 서브목록 (FIDO Attestation 기반)**

등록 항목: AAGUID, 인증기 제조사·모델명, 지원 알고리즘(COSE), 하드웨어 보안 수준(TEE/SE/Secure Enclave), FIDO 인증 등급(L1/L2/L3+), Attestation 유형, Attestation Root Certificate, 서비스 상태.

일실시예에서, 메타데이터 형식과 AAGUID 등록 방식은 FIDO Alliance FIDO MDS 구조를 준용한다.

**HSM 장치 서브목록 (HSM Attestation 기반)**

등록 항목: hsmDeviceId, HSM 제조사·모델명, 지원 알고리즘, 하드웨어 보안 수준(CC EAL 등급, FIPS 등급), HSM Attestation Root Certificate, 서비스 상태.

### 정책 자동 갱신(Policy Auto-Update) 메커니즘

KR-TL이 갱신될 때, 검증 정책이 자동으로 변경되는 메커니즘이다. 기존에는 신뢰목록 갱신 후 검증 시스템의 정책을 수동으로 변경해야 했다.

```
KR-TL 갱신 이벤트 유형:
  A. 신규 장치 등재   → 해당 장치 유형 검증 허용 정책 자동 적용
  B. 장치 보안 등급 변경 → 해당 AAGUID/hsmDeviceId 의 허용 등급 자동 갱신
  C. 장치 폐지       → 해당 장치로 생성된 전자서명 자동 TOTAL_FAILED 처리
  D. 서비스 제공자 폐지 → 해당 CA 발급 인증서 자동 TOTAL_FAILED 처리

정책 자동 갱신 흐름:
1. KR-TL 갱신 배포 수신
2. 갱신 내용 분석: 신규 등재 / 등급 변경 / 폐지 분류
3. 해당 AAGUID / hsmDeviceId 에 대한 검증 정책 자동 업데이트
4. 정책 변경 이력 저장 및 감사 로그 생성
5. 갱신된 정책 즉시 적용
```

### 통합 검증 절차

```
[ Layer 1 : 신뢰서비스 검증 ]
1. 서명자 인증서의 발급 기관 추출
2. 신뢰서비스 목록에서 발급 기관 조회
3. 서비스 상태 확인 → 미등재·폐지: TOTAL_FAILED

[ Layer 2 : 서명 생성 장치 검증 ]
4. 전자서명 객체의 비서명 속성에서 장치 식별자 추출
   (WebAuthn → AAGUID, HSM → hsmDeviceId)
5. 인증서 정책 OID로 장치 유형 확인
6. 해당 서브목록에서 장치 식별자 조회
7. 보안 등급 및 서비스 상태 확인
8. → 미등재: 정책에 따라 INDETERMINATE 또는 TOTAL_FAILED
   → 등급 미충족: INDETERMINATE

[ 통합 결과 산출 ]
9. Layer 1 + Layer 2 결과 종합 → 최종 검증 결과 반환
```

## 발명 C-2 : HSM Attestation Statement — HSM 장치 신뢰 증명

### 설계 원칙: FIDO Attestation의 HSM 적용

FIDO Attestation은 등록 시점에 인증기가 제조사 Attestation 키로 서명한 Attestation Statement를 서버에 제출하는 구조다. 본 발명은 동일한 원칙을 HSM에 적용한다.

```
FIDO Attestation (WebAuthn)     HSM Attestation
───────────────────────────     ───────────────────────────
등록 시 Attestation Statement   키 생성 시 Attestation Statement
AAGUID (장치 모델 식별자)       hsmDeviceId (HSM 모델 식별자)
FIDO Alliance Root CA           HSM Attestation Root CA
FIDO MDS 메타데이터 조회        KR-TL HSM 서브목록 조회
FIDO Alliance 등재 관리         국가기관/위탁기관 등재 관리
UP/UV 플래그 (사용자 검증)      nonExtractable (키 추출불가)
```

### HSM Attestation Object 구조

```asn1
HSMAttestationObject ::= SEQUENCE {
    version          INTEGER (1),
    hsmDeviceId      OCTET STRING,        -- HSM 장치 모델 식별자
    hsmInstanceId    OCTET STRING OPTIONAL, -- HSM 인스턴스 고유 식별자
    hsmPublicKey     SubjectPublicKeyInfo, -- HSM Attestation 공개키
    keyGenStatement  KeyGenStatement,     -- 서명키 생성 정책 진술
    securityLevel    SecurityLevel,       -- 보안 수준
    timestamp        GeneralizedTime,     -- 키 생성 시각
    attestationSig   OCTET STRING         -- HSM Attestation 키 서명
}

KeyGenStatement ::= SEQUENCE {
    algorithm        AlgorithmIdentifier, -- 키 알고리즘
    keySize          INTEGER,             -- 키 크기 (비트)
    nonExtractable   BOOLEAN,             -- 키 추출 불가 여부
    keyUsage         KeyUsage             -- 키 용도
}

SecurityLevel ::= SEQUENCE {
    ccLevel          UTF8String OPTIONAL, -- CC EAL 등급
    fipsLevel        UTF8String OPTIONAL, -- FIPS 등급
    vendorLevel      UTF8String OPTIONAL  -- 제조사 자체 등급
}
```

### HSM Attestation 신뢰 체계

```
HSM Attestation Root CA (신뢰 앵커, KR-TL 등재)
    │
    └── HSM Attestation 중간 CA (제조사별)
            │
            └── HSM Attestation Certificate (장치별)
                    │
                    └── HSM Attestation Statement (요청별)
```

### HSM Attestation 검증 절차

```
1. HSM Attestation Object 수신
2. hsmDeviceId → KR-TL HSM 서브목록 조회
3. Attestation Root Certificate 취득
4. Root CA → 중간 CA → 장치 인증서 체인 검증
5. 장치 인증서 공개키로 attestationSig 검증
6. keyGenStatement.nonExtractable = TRUE 확인
7. 알고리즘·키 크기 정책 적합성 확인
8. securityLevel 정책 요구 등급 충족 확인
9. timestamp 유효성 확인
10. 검증 결과: TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED
```

---

# 발명의 효과

① **이중 신뢰 보증**: 신뢰서비스 차원(CA 수준)과 서명 생성 장치 차원(인증기/HSM 수준)의 이중 검증을 단일 절차에서 수행한다. 기존 단일 차원(CA 수준) 검증 대비 신뢰 보증 레이어를 2배로 확장한다.

② **정책 자동 갱신(Policy Auto-Update)**: KR-TL 갱신 시 검증 정책이 자동으로 업데이트되므로, 수동 정책 변경 운영 부담이 제거된다. 특히 장치 폐지 이벤트 발생 시 해당 장치로 생성된 전자서명을 즉시 자동으로 TOTAL_FAILED로 처리하여 보안 사고 대응 시간을 단축한다.

③ **HSM 서명키 비추출성 암호학적 증명**: keyGenStatement.nonExtractable = TRUE 검증으로, 서명키가 HSM 외부로 추출될 수 없음을 암호학적으로 확인한다. 기존 CC/FIPS 문서 기반 방식 대비 실시간 암호학적 검증이 가능하다.

④ **로컬·원격 서명의 통합 신뢰체계**: WebAuthn 기반 로컬 서명과 HSM 기반 원격 서명을 동일한 KR-TL 구조 내에서 통합 관리하므로, 서명 유형에 관계없이 일관된 신뢰 검증 절차를 제공한다.

⑤ **국가 차원의 서명 생성 장치 통제**: 국가기관 또는 정부 위탁기관이 KR-TL을 운영하므로, 전자서명 서비스에서 허용되는 서명 생성 장치의 보안 수준을 국가 정책 차원에서 관리·통제한다.

⑥ **자동 등급 기반 검증 정책 분기**: 장치 식별자(AAGUID/hsmDeviceId)로 보안 등급을 조회하고, 등급에 따라 검증 정책을 자동으로 다르게 적용한다. 예를 들어, L1 등급 인증기 서명은 INDETERMINATE, L2 이상만 TOTAL_PASSED로 처리하는 등의 정책을 신뢰목록 수준에서 정의할 수 있다.

---

# 도면의 간단한 설명

도 1 : KR-TL 이중 계층 구조 — Layer 1(신뢰서비스 목록) + Layer 2(서명 생성 장치 신뢰목록)
도 2 : Layer 2 서브목록 — WebAuthn 인증기 서브목록 + HSM 장치 서브목록 (상세 구조도)
도 3 : 정책 자동 갱신(Policy Auto-Update) 메커니즘 — KR-TL 갱신 이벤트 → 정책 자동 변경 (flowchart)
도 4 : HSM Attestation Object 구조 및 HSM 신뢰 체계 (논리 구조도 + ASN.1 다이어그램)
도 5 : FIDO Attestation vs. HSM Attestation 대응 관계 (비교 다이어그램)

---

# 청구항

## ════════════════════════════════════════
## LEVEL 1 : FRAMEWORK — 독립항 (4개)
## ════════════════════════════════════════

## 청구항 1 (독립항 — 통합 신뢰목록 시스템 KR-TL)

전자서명 검증을 위한 통합 신뢰목록 시스템으로서,

PKI 신뢰서비스 제공자를 등록·관리하는 신뢰서비스 목록; 및

Attestation 메커니즘을 이용하여 검증된 서명 생성 장치를 장치 식별자 단위로 등록·관리하되, WebAuthn 인증기와 HSM을 통합하여 등록하는 서명 생성 장치 신뢰목록;

을 포함하며,

검증기가 상기 신뢰서비스 목록을 참조하여 전자서명의 인증서 발급 기관의 공인 여부를 확인하고, 상기 서명 생성 장치 신뢰목록을 참조하여 전자서명이 생성된 장치의 공인 여부 및 보안 등급을 확인하는 이중 신뢰 검증을 단일 검증 절차 내에서 수행하도록 하며,

상기 신뢰목록이 갱신될 때 검증 정책을 자동으로 변경하는 정책 자동 갱신(Policy Auto-Update) 메커니즘을 포함하는

통합 전자서명 신뢰목록 시스템.

---

## 청구항 2 (독립항 — HSM Attestation 시스템)

HSM 기반 원격 서명의 장치 신뢰 증명 시스템으로서,

HSM이 서명키 생성 시 또는 장치 신뢰 증명 요청 시, HSM 내부의 Attestation 키로 서명한 HSM Attestation Statement를 생성하는 Attestation 생성부; 및

상기 HSM Attestation Statement를 수신하여 HSM Attestation Root CA를 신뢰 앵커로 검증하고, 서명키의 비추출성(non-extractability) 및 보안 등급을 확인하는 Attestation 검증부;

를 포함하는 HSM Attestation 시스템.

---

## 청구항 3 (독립항 — 방법)

전자서명 통합 신뢰목록 참조 이중 검증 방법으로서,

신뢰서비스 목록을 참조하여 전자서명의 인증서 발급 기관의 공인 여부를 확인하는 단계;

서명 생성 장치 신뢰목록을 참조하여 전자서명이 생성된 장치의 장치 식별자를 대조하고, 상기 장치의 공인 여부 및 보안 등급을 확인하는 단계;

상기 두 단계의 검증 결과를 종합하여 최종 검증 결과를 산출하는 단계; 및

상기 신뢰목록 갱신 시 검증 정책을 자동으로 변경하여 적용하는 단계;

를 포함하는 이중 검증 방법.

---

## 청구항 4 (독립항 — 컴퓨터 판독 가능한 기록매체)

청구항 3에 따른 방법을 컴퓨터에서 실행하기 위한 프로그램이 기록된 컴퓨터 판독 가능한 기록매체.

---

## ════════════════════════════════════════
## LEVEL 2 : BINDING MECHANISM — 중위 종속항
## ════════════════════════════════════════

## 청구항 5 (종속항 — 신뢰서비스 목록 구조)

청구항 1에 있어서,

상기 신뢰서비스 목록은 등록된 각 PKI 신뢰서비스 제공자에 대하여 인증기관 명칭, 서비스 유형, 인증서 발급 정책, 서비스 상태 및 공개키 정보를 포함하며, 검증기가 서명자 인증서의 발급 기관이 상기 신뢰서비스 목록에 등재된 공인 기관인지 확인하는 데 사용되는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 6 (종속항 — WebAuthn 인증기 서브목록)

청구항 1에 있어서,

상기 서명 생성 장치 신뢰목록은 FIDO Attestation을 이용하여 검증된 WebAuthn 인증기를 AAGUID 단위로 등록하되, 각 AAGUID에 대하여 인증기 제조사, 하드웨어 보안 수준, FIDO 인증 등급, Attestation 유형 및 Attestation Root Certificate를 포함하는 WebAuthn 인증기 서브목록을 포함하는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 7 (종속항 — HSM 장치 서브목록)

청구항 1에 있어서,

상기 서명 생성 장치 신뢰목록은 HSM Attestation을 이용하여 검증된 HSM을 장치 식별자(hsmDeviceId) 단위로 등록하되, 각 hsmDeviceId에 대하여 HSM 제조사, 하드웨어 보안 수준(CC EAL 등급, FIPS 등급), HSM Attestation Root Certificate 및 서비스 상태를 포함하는 HSM 장치 서브목록을 포함하는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 8 (종속항 — Policy Auto-Update 메커니즘)

청구항 1에 있어서,

상기 정책 자동 갱신 메커니즘은, 신뢰목록 갱신 이벤트를 신규 장치 등재, 장치 보안 등급 변경 및 장치 폐지로 분류하고, 각 이벤트 유형에 따라 해당 장치 식별자에 대한 검증 허용 여부 및 검증 결과 분류 기준을 자동으로 변경하여 적용하는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 9 (종속항 — HSM Attestation Statement 구조)

청구항 2에 있어서,

상기 HSM Attestation Statement는 HSM 장치 모델 식별자(hsmDeviceId), HSM Attestation 공개키, 서명키 생성 정책(keyGenStatement), 보안 수준(securityLevel), 키 생성 시각(timestamp) 및 HSM Attestation 키에 의한 서명값(attestationSig)을 포함하며, 상기 keyGenStatement는 서명키 알고리즘, 키 크기, 키 추출 불가 여부(nonExtractable) 및 키 용도를 포함하는 것을 특징으로 하는 HSM Attestation 시스템.

---

## ════════════════════════════════════════
## LEVEL 3 : IMPLEMENTATION — 하위 종속항
## ════════════════════════════════════════

## 청구항 10 (종속항 — EU-TL 방식 준용)

청구항 5에 있어서,

상기 신뢰서비스 목록의 구조와 운영 방식은 eIDAS 규정에 따른 EU 신뢰목록(EU Trusted List, EU-TL) 체계를 준용하는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 11 (종속항 — FIDO MDS 방식 준용)

청구항 6에 있어서,

상기 WebAuthn 인증기 서브목록의 메타데이터 형식과 AAGUID 등록 방식은 FIDO Alliance FIDO Metadata Service(FIDO MDS)의 구조를 준용하는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 12 (종속항 — 미등재 장치 정책 처리)

청구항 1에 있어서,

상기 검증기는 전자서명 객체에서 추출된 장치 식별자가 상기 서명 생성 장치 신뢰목록에 등재되지 않은 경우 정책에 따라 INDETERMINATE 또는 TOTAL_FAILED로 처리하고, 등재되어 있으나 보안 등급이 정책 요구 수준에 미달하는 경우 INDETERMINATE로 처리하는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 13 (종속항 — HSM Attestation 검증 절차)

청구항 2에 있어서,

상기 Attestation 검증부는 hsmDeviceId로 서명 생성 장치 신뢰목록에서 HSM Attestation Root Certificate를 조회하고, 상기 Root Certificate를 신뢰 앵커로 HSM Attestation Statement의 서명값을 검증하며, keyGenStatement의 nonExtractable 값이 TRUE인지 확인하고, securityLevel이 정책 요구 등급 이상인지 확인하는 것을 특징으로 하는 HSM Attestation 시스템.

---

## 청구항 14 (종속항 — 신뢰목록 운영 주체)

청구항 1에 있어서,

상기 신뢰서비스 목록 및 상기 서명 생성 장치 신뢰목록은 국가기관 또는 정부 위탁기관에 의해 등록·관리·배포되는 것을 특징으로 하는 통합 전자서명 신뢰목록 시스템.

---

## 청구항 15 (종속항 — HSM Attestation Root CA 신뢰 체계)

청구항 2에 있어서,

상기 HSM Attestation 시스템은 HSM 제조사별 Attestation Root Certificate를 상기 서명 생성 장치 신뢰목록에 등재하며, 상기 Attestation Root Certificate는 HSM 제조사의 Attestation 인증 체계(Root CA → 중간 CA → 장치 인증서)의 최상위 신뢰 앵커로 사용되는 것을 특징으로 하는 HSM Attestation 시스템.

---

# 청구항 계층도

```
독립항 1 (통합 신뢰목록 시스템 KR-TL)
├── [L2] 청구항  5  신뢰서비스 목록 구조
│         └── [L3] 청구항 10  EU-TL 방식 준용
├── [L2] 청구항  6  WebAuthn 인증기 서브목록 (FIDO Attestation)
│         └── [L3] 청구항 11  FIDO MDS 방식 준용
├── [L2] 청구항  7  HSM 장치 서브목록 (HSM Attestation)
├── [L2] 청구항  8  Policy Auto-Update 메커니즘
│         (갱신 이벤트 → 정책 자동 변경)
├──       청구항 12  미등재/등급 미달 장치 정책 처리
└──       청구항 14  신뢰목록 운영 주체 (국가기관/위탁기관)

독립항 2 (HSM Attestation 시스템)
├── [L2] 청구항  9  HSM Attestation Statement 구조
│         (hsmDeviceId, keyGenStatement, nonExtractable, attestationSig)
├── [L2] 청구항 13  HSM Attestation 검증 절차
└──      청구항 15  HSM Attestation Root CA 신뢰 체계

독립항 3 (방법) ← 1+2 통합 + Policy Auto-Update
독립항 4 (기록매체) ← 청구항 3 기반
```

---

# 변경 이력

| 버전    | 날짜       | 변경 내용 |
|---------|------------|-----------|
| v1.0-C  | 2026-06-28 | 신규 작성. KR-TL + HSM Attestation. 독립항 4개. |
| v2.0-C  | 2026-06-28 | 독립항 강화: KR-TL 독립항(청구항 1)에 정책 자동 갱신(Policy Auto-Update) 추가. HSM Attestation 독립항(청구항 2) 명확화: nonExtractable 검증 핵심화. Policy Auto-Update 종속항(청구항 8) 신규 추가. 기술효과 정량화(이중 보증 레이어 확장, 보안 사고 자동 대응 등). |
