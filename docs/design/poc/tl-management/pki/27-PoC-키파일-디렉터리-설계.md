# 27. PoC 키 파일·인증서 디렉터리 설계

작성일: 2026-10-07. 경로 정리: 2026-10-08. 미등재 대조군 UN 추가: 2026-10-09. 새 PoC 생성 루트는 `runtime/tl-management/pki/`이며 실제 생성은 후속 개발이다.

**2026-10-09 생성 완료.** 세트 `krtl-poc-pki-v1`(EC P-256, 22개 노드)을 `krdss-cli cert tree`와 프로파일 `scripts/poc/tl-management/pki-profile.json`으로 생성했다. 노드별 키쌍 일치·발급자 서명·Root까지 PKIX 경로·역할별 KU/EKU 검사를 통과했고 openssl로 22개 체인을 별도 확인했다. 가입자 인증서는 생성하지 않았다. 생성물은 Git 비관리다. 기존 `runtime/pki/legacy`는 그대로 두었다.

**배포 방식: 수동 복사.** 키 세트는 한 번만 생성하고, 다른 체크아웃(main `F:/kr-dss-works/kr-dss` 등)에는 `runtime/tl-management/pki/` 폴더 전체를 수동으로 복사한다. 다른 체크아웃에서 다시 생성하지 않는다. 다시 생성하면 지문이 다른 별개 세트가 되어 TL 등재 인증서·이용기관 신뢰앵커와 맞지 않는다.

1. 원본: 생성한 체크아웃의 `runtime/tl-management/pki/` (세트 ID·지문은 `manifest.json`)
2. 대상 체크아웃에 `runtime/tl-management/pki/`가 없는지 확인한다. 있으면 덮어쓰지 말고 `manifest.json`의 setId·generatedAt을 비교해 보고한다.
3. 폴더 전체를 복사한다(부분 복사 금지).
4. 원본·대상의 전체 파일 SHA-256 목록이 같은지 확인한다.

2026-10-09 기록: worktree `kr-tl`에서 생성(generatedAt 2026-10-09T07:57:35Z)한 세트를 main 체크아웃에 복사했다. 파일 130개 해시 일치, manifest.json SHA-256 `d3e609f606e70c3bd6261d53286a7adc8cce68631c08fdbafb530ed0b5643e0b`.

최초 생성 명령(저장소 루트, 출력 폴더가 있으면 거부, 새 세트가 필요할 때만): `gradlew.bat :tools:krdss-cli:run --args="cert tree -f scripts/poc/tl-management/pki-profile.json -o runtime/tl-management/pki"`

## 1. 인증체계 구성

[07 인증체계 설계](../../../common/trust-framework/07-인증체계-설계.md)의 가상 사업자 4곳과 인증서 ID를 유지한다. KISA의 두 Root는 별도 키쌍으로 생성한다.

```text
KISA TL RootCA (T-00)                 TL 검증용 사전 신뢰앵커
└── KISA TL CA (T-01)
    └── KISA TL Signer (T-02)         이 개인키로 TL XML 서명

KISA 공동인증 RootCA (K-00)
├── 가상공동인증A CA (JA-01)
│   ├── OCSP (JA-02)
│   ├── TSA (JA-03)
│   └── 가입자 인증서
└── 가상공동인증B CA (JB-01)
    ├── OCSP (JB-02)
    ├── TSA (JB-03)
    └── 가입자 인증서

가상간편인증A RootCA (SA-00)
└── 가상간편인증A CA (SA-01)
    ├── OCSP (SA-02)
    ├── TSA (SA-03)
    └── 가입자 인증서

가상간편인증B RootCA (SB-00)
└── 가상간편인증B CA (SB-01)
    ├── OCSP (SB-02)
    ├── TSA (SB-03)
    └── 가입자 인증서

가상미등재사업자 RootCA (UN-00)       TL 미등재 체계 — 어떤 노드도 등재하지 않음
└── 가상미등재사업자 CA (UN-01)
    ├── OCSP (UN-02)
    ├── TSA (UN-03)
    └── 가입자 인증서
```

각 Root 인증서는 자기 키로 서명하고, 하위 인증서는 바로 위 발급자의 개인키로 서명한다. 각 노드는 서로 다른 키쌍을 갖는다. `chain.pem`은 그 발급 관계의 공개 인증서 묶음이며 개인키를 이어 붙인 파일이 아니다.

UN(가상미등재사업자)은 TL에 등재되지 않은 서비스를 위한 대조군이다. 정상적인 X.509 체인(키쌍·발급자 서명·용도 확장)을 갖추되 KR-TL에는 RootCA·CA·OCSP·TSA 어느 것도 등재하지 않는다. 이 체인의 가입자 서명·OCSP 응답·타임스탬프는 체인이 형식상 유효해도 TL 기준 신뢰 판정에서 실패해야 한다. 관리 화면에 UN 인증서를 등록하는 것은 별도 시험 조작이며 정상 등재 목록(TSP 4·서비스 10)에 포함하지 않는다. 다른 Root와 교차 인증하지 않는다.

TL 서명 체계와 사업자 인증체계 사이에는 교차 인증을 만들지 않는다. KISA TL Root 인증서는 TL 서명을 확인하는 사전 신뢰앵커다. 사업자 인증서는 TL의 서비스 등재 정보를 통해 신뢰 여부를 판정한다. 체인에 존재한다는 이유만으로 모든 인증서를 TL에 등재하지 않는다. 신뢰점 레벨과 등재 선택은 기존 서비스 설계에 따른다.

## 2. 생성할 디렉터리

경로는 저장소 루트 기준이다. 각 인증서 노드에는 §3의 공통 파일을 생성한다.

```text
runtime/tl-management/pki/
├── README.md                         전체 체인·사용 방법·파일 위치 설명
├── manifest.json                     생성 세트 ID, 인증서 목록·지문·발급 관계
├── tl-signer/
│   ├── README.md                     KISA TL 서명 체인 설명
│   ├── T-00-root/                    KISA TL RootCA
│   ├── T-01-ca/                      KISA TL CA
│   └── T-02-signer/                  실제 TL 서명에 사용할 키
└── providers/
    ├── README.md                     사업자별 체계·TL 등재 구분
    ├── KISA-J/
    │   ├── README.md                 공동인증 Root와 JA·JB의 발급 관계
    │   └── K-00-root/
    ├── JA/
    │   ├── README.md
    │   ├── provider.json
    │   ├── JA-01-ca/
    │   ├── JA-02-ocsp/
    │   ├── JA-03-tsa/
    │   └── subscribers/              가입자 발급 시 생성
    │       └── <certificate-id>/
    ├── JB/
    │   ├── README.md
    │   ├── provider.json
    │   ├── JB-01-ca/
    │   ├── JB-02-ocsp/
    │   ├── JB-03-tsa/
    │   └── subscribers/
    ├── SA/
    │   ├── README.md
    │   ├── provider.json
    │   ├── SA-00-root/
    │   ├── SA-01-ca/
    │   ├── SA-02-ocsp/
    │   ├── SA-03-tsa/
    │   └── subscribers/
    ├── SB/
    │   ├── README.md
    │   ├── provider.json
    │   ├── SB-00-root/
    │   ├── SB-01-ca/
    │   ├── SB-02-ocsp/
    │   ├── SB-03-tsa/
    │   └── subscribers/
    └── UN/                           TL 미등재 대조군
        ├── README.md                 미등재 목적·기대 판정(신뢰 실패) 명시
        ├── provider.json             tlListed=false 고정
        ├── UN-00-root/
        ├── UN-01-ca/
        ├── UN-02-ocsp/
        ├── UN-03-tsa/
        └── subscribers/
```

Root・CA・서비스용 기본 노드는 총 22개다(TL 체계 3, 공동인증 Root 1, JA/JB 각 3, SA/SB/UN 각 4). Root는 5개(T-00, K-00, SA-00, SB-00, UN-00)다. 가입자는 별도 발급하며 수량·표본 시각은 향후 PoC 시나리오에서 정한다. TSA 노드의 키·인증서 준비와 TSA 서버 구현·시연은 구별한다. TSA 실행 기능은 이번 디렉터리 설계로 추가하지 않는다.

## 3. 인증서 노드의 공통 파일

```text
<certificate-node>/
├── key.pem             이 노드의 개인키(PKCS#8 PEM)
├── cert.pem            이 노드의 X.509 인증서 1장(PEM)
├── chain.pem           cert.pem부터 발급자 방향으로 Root까지(PEM)
├── meta.json           ID·발급자·용도·파일 경로·지문
└── README.md           이 키의 용도와 인증서 체인 설명
```

| 파일 | 작성 규칙 |
|---|---|
| key.pem | 해당 노드 전용 개인키. 평문/암호화 여부와 로딩 설정은 생성 도구 구현 시 정하고 README에 명시 |
| cert.pem | 주체 공개키 포함. key.pem과의 키쌍 일치 검사 수행 |
| chain.pem | 현재 인증서 → 중간 CA → Root 순서. Root 노드는 자기 인증서만 포함 |
| meta.json | certificateId, issuerCertificateId, providerId(해당 시), role, 파일 상대경로, subject, issuer, serial, 유효기간, SHA-256 지문, 공개키 식별 정보 |
| README.md | 실제 생성된 인증서 정보를 읽어 작성. 개인키 내용은 기록하지 않음 |

공개키는 `cert.pem`에서 읽으므로 별도 `public-key.pem`은 필수 파일로 두지 않는다. `chain.pem`은 로컬 체인 구성·확인용이며, 그 전체를 TL XML의 KeyInfo나 서비스 이력에 자동 삽입하는 규칙이 아니다. XML 수록 항목은 별도 TL 직렬화 규칙에 따른다.

메타데이터의 `issuerCertificateId`는 실제 서명 검증 결과와 일치해야 한다. manifest의 경로는 pki 루트 기준 상대경로로 기록한다. Root 개인키를 JA·JB 디렉터리에 복제하지 않는다. 필요한 상위 공개 인증서만 chain.pem에 포함한다.

## 4. TL 발행·이용 모듈에서 참조하는 위치

| 사용처 | 참조 파일 | 용도 |
|---|---|---|
| TL 발행기 | `runtime/tl-management/pki/tl-signer/T-02-signer/key.pem` | TL 서명 |
| TL 발행기 | `runtime/tl-management/pki/tl-signer/T-02-signer/cert.pem` | TL 서명자 인증서 |
| 로컬 체인 구성 | `runtime/tl-management/pki/tl-signer/T-02-signer/chain.pem` | T-02 → T-01 → T-00 관계 확인 |
| 사업자 CA | 각 사업자 `*-01-ca/key.pem` | 가입자·서비스 인증서 발급 |
| 사업자 OCSP | 각 사업자 `*-02-ocsp/key.pem` | OCSP 응답 서명 |
| TL 데이터 편성 | 선택한 사업자 노드의 `cert.pem` | 서비스 디지털 ID. 개인키 불필요 |
| 이용기관 | 아래 trust-anchor·intermediates의 공개 인증서 | TL 서명 체인 구성·신뢰 검증 |

```text
krtl-client/
├── trust-anchor/
│   └── T-00-root.pem                 T-00 cert.pem의 공개 인증서 복사본
├── intermediates/
│   └── T-01-ca.pem                   체인 구성 보조. 신뢰앵커 아님
└── cache/                            검증 후 수용한 TL

krtl/
├── registry/                         등재 원천. 공개 인증서·서비스 데이터만
├── issued/
│   └── <issuanceId>/                 26의 내부 발행 ID
│       ├── kr-tl.xml                 서명된 TL
│       └── issuance.json             입력 순번·시각·해시·생성 결과
└── served/                           WAS가 제공하는 발행본 선택 정보
```

TL 발행 시 Root나 TL CA의 개인키를 읽을 필요는 없다. 그 키는 하위 인증서를 생성할 때 사용한다. 배포 WAS의 공개 제공 대상은 TL 등 공개 산출물이며 pki 디렉터리를 정적 웹 경로로 노출하지 않는다. 관리용 인증·감사 기능은 추가하지 않는다.

`issued/<issuanceId>`를 사용하므로 같은 순번의 다른 내용이나 하위 순번도 덮어쓰기 없이 보관할 수 있다. 오류 TL 재발행은 기존 키로 수행하며, 시각·순번 오류 시연을 위해 Root·사업자 키까지 재생성하지 않는다. 세부 발행 동작은 [26](../../../common/kr-tl/26-EU-기준-이력-버전-모델.md)을 따른다.

## 5. 향후 생성할 체인 설명 문서

개발 단계에서 키·인증서 생성과 함께 §2·3의 README들을 작성한다. 문서는 예정값이 아니라 실제 산출물에서 추출한 정보로 작성한다.

| 문서 | 필수 내용 |
|---|---|
| runtime/tl-management/pki/README.md | 전체 체인 그림, KISA 두 Root의 차이, 가상 사업자 목록, 생성·재사용 방법 |
| tl-signer/README.md | T-00/T-01/T-02 발급 관계, 실제 TL 서명키 경로, 이용기관 앵커·중간 인증서 배치 |
| providers/README.md | 공동인증과 독립 간편인증 체계, 인증서 발급 관계와 TL 등재 관계의 차이 |
| 사업자별 README.md | 해당 사업자의 Root까지 체인, CA·OCSP·TSA·가입자 용도, 상위 인증서 출처. UN은 미등재 대조군임과 기대 판정 |
| 노드별 README.md | 인증서 ID·발급자 ID, key/cert/chain 경로, Subject·Issuer·serial·유효기간·지문, KU/EKU, 체인 순서, 키쌍·체인 검증 결과 |

README에는 생성 명령·도구 버전·생성 세트 ID를 기록하되 비밀번호나 개인키 원문을 넣지 않는다. 같은 폴더에 여러 키를 무명으로 쌓지 않는다. 키 교체는 새 ID·새 폴더로 생성하고 기존 인증서와 체인 자료를 유지한다.

## 6. 후속 개발 순서와 완료 조건

1. 생성 프로파일을 정하고 기존 `krdss-cli cert`를 확장한다. 인증서 유효기간·알고리즘은 프로파일에 모으고, 역할별 KU/EKU와 SKI/AKI를 명시한다.
2. 5개 Root(T-00, K-00, SA-00, SB-00, UN-00), 하위 CA, 서비스 인증서를 발급 관계 순서대로 생성한다.
3. chain.pem·meta.json·manifest.json과 각 README를 실제 인증서에서 생성한다.
4. 키쌍 일치·발급자 서명·Root까지의 경로·용도 확장을 검사하고 결과를 README에 기록한다.
5. T-02 키 파일을 TL 발행기에 연결하고, 이용기관에는 T-00·T-01 공개 인증서만 배치한다. 정상 TL 서명·검증 연결을 확인한다.

기존 `runtime/pki/legacy`, 기존 모듈 리소스의 키스토어는 자동 덮어쓰기하거나 삭제하지 않는다. 새 pki 파일과 모듈 연결이 확인된 후 별도 전환한다. 생성 전 `.gitignore`를 확인해 개인키와 실행 산출물이 소스 커밋에 섞이지 않게 하고, 재현 가능한 생성 설정·템플릿은 소스로 관리한다. 파일 생성 단계에서 Git 추적 대상과 로컬 산출물을 구분한다.

이 문서의 완료는 디렉터리 설계 기록까지다. 실제 파일 생성·체인 검증·README 생성 완료로 보고하지 않는다.
