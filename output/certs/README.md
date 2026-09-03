# 테스트 인증체인 (`output/certs`)

Whale PoC(PDF Baseline)와 KR-TL 실증에 쓰는 **테스트 PKI 체인**이다.
저장소의 인증서 발급 도구(`krdss cert`)로 생성했고, `scripts/gen-certs.ps1` 한 번으로 전부 재생성된다.

> ⚠️ **테스트·실증 전용이다.** KISA 공인 체계와 무관한 자체 발급 인증서이며,
> 개인키(`*.key`)와 키스토어(`*.p12`)가 저장소에 그대로 들어 있다.
> 비밀번호도 `111111` 로 고정되어 있다. **운영 용도로 절대 쓰지 않는다.**

---

## 1. 체인 구조

루트 하나 아래에 용도별 중간 CA 넷을 두고, 최종개체는 모두 중간 CA가 발급한다.
루트가 최종개체를 직접 발급하지 않는 것이 실제 PKI 운영 형태이고, 신뢰목록(KR-TL)이
중간 CA 단위로 등재되기 때문이다.

```
KISA RootCA                                                    [자가서명, RSA 4096, 20년]
│   CN=KISA RootCA, OU=Korea Internet & Security Agency, O=KISA, C=KR
│
├── KISA CA                          (신뢰목록 운영)           [RSA 4096, 10년]
│   │   CN=KISA CA, OU=Korea Internet & Security Agency, O=KISA, C=KR
│   └── KISA Trusted List Signer     KR-TL 서명자              [RSA 2048, 3년]
│           CN=KISA Trusted List Signer, OU=Korea Internet & Security Agency, O=KISA, C=KR
│
├── KISA_Joint_CA                    (공동인증)                [RSA 4096, 10년]
│   │   CN=KISA_Joint_CA, OU=Joint, O=KISA, C=KR
│   └── Joint_Test_User              공동인증 사용자           [RSA 2048, 825일]
│           CN=Joint_Test_User, OU=Joint, O=KISA, C=KR
│
├── KISA_Financial_CA                (금융인증)                [RSA 4096, 10년]
│   │   CN=KISA_Financial_CA, OU=Financial, O=KISA, C=KR
│   └── Financial_Test_User          금융인증 사용자           [RSA 2048, 825일]
│           CN=Financial_Test_User, OU=Financial, O=KISA, C=KR
│
└── KISA_Test_CA                     (시험)                    [RSA 4096, 10년]
    │   CN=KISA_Test_CA, OU=Test, O=KISA, C=KR
    └── Test_User                    범용 시험 사용자          [RSA 2048, 825일]
            CN=Test_User, OU=Test, O=KISA, C=KR
```

### 계층별 성격

| 계층 | 키 | BasicConstraints | 용도 |
| --- | --- | --- | --- |
| 루트 CA | RSA 4096 | `CA:true` | 유일한 **신뢰 앵커**. 중간 CA만 발급한다 |
| 중간 CA | RSA 4096 | `CA:true` | 용도별 발급기관. **KR-TL 등재 단위** |
| 최종개체 | RSA 2048 | `CA:false` | 서명 주체. 개인키를 `.p12` 로 배포 |

---

## 2. 파일 목록

### 2.1 인증서 · 개인키 (PEM)

| 파일 | 내용 |
| --- | --- |
| `kisa-rootca.crt` / `.key` | 루트 CA — **트러스트 앵커** |
| `kisa-ca.crt` / `.key` | 중간 CA (신뢰목록 운영) |
| `kisa-joint-ca.crt` / `.key` | 중간 CA (공동인증) |
| `kisa-financial-ca.crt` / `.key` | 중간 CA (금융인증) |
| `kisa-test-ca.crt` / `.key` | 중간 CA (시험) |
| `kisa-tl-signer.crt` / `.key` | 최종개체 — KR-TL 서명자 |
| `kisa-joint-user.crt` / `.key` | 최종개체 — 공동인증 사용자 |
| `kisa-financial-user.crt` / `.key` | 최종개체 — 금융인증 사용자 |
| `kisa-test-cert.crt` / `.key` | 최종개체 — 시험 사용자 |

> 개인키는 PKCS#8 평문 PEM이다.

### 2.2 번들 (PEM)

| 파일 | 내용 |
| --- | --- |
| `ca-chain.pem` | 루트 + 중간 CA 4장 — 트러스트스토어(PEM 형태) |
| `kisa-tl-signer-fullchain.pem` | TL 서명자 + KISA CA + 루트 |
| `kisa-joint-user-fullchain.pem` | 공동인증 사용자 + Joint CA + 루트 |
| `kisa-financial-user-fullchain.pem` | 금융인증 사용자 + Financial CA + 루트 |
| `kisa-test-cert-fullchain.pem` | 시험 사용자 + Test CA + 루트 |

### 2.3 신뢰목록 (KR-TL)

중간 CA 4장 + 루트를 `GRANTED` 로 등재하고 `kisa-tl-signer` 로 전자서명한 신뢰목록이다.
같은 내용을 두 형식으로 제공한다.

| 파일 | 형식 | 서명 |
| --- | --- | --- |
| `kr-tl-signed.xml` | **TS 119 612 XML** (표준) | XAdES-BASELINE-B enveloped (`ds:Signature`) |
| `kr-tl-signed.json` | KR-TL JSON (경량) | JWS(RFC 7515) 평탄화 직렬화 |

**XML** 은 EU TL 과 같은 `TrustServiceStatusList` 골격을 쓴다. 서명이 문서 안에 들어가므로 파일 하나로
목록과 서명이 함께 다니고, 검증도 EU DSS 의 일반 문서 검증 경로를 그대로 탄다 — PDF 검증과 **같은
트러스트 앵커·같은 검증정책**을 쓰므로 문서와 신뢰목록이 서로 다른 기준으로 통과하는 일이 없다.

서비스 상태는 ETSI URI 로 표현한다. `GRANTED` → `.../Svcstatus/granted`, `WITHDRAWN` → `.../withdrawn`.
TS 119 612 에 "정지" 상태 URI 가 없어 `SUSPENDED` 만 KR 확장 URI(`urn:kr:krdss:TrustedList:Svcstatus:suspended`)를 쓴다.

**JSON(JWS)** 은 `payload`(KR-TL 본문) · `protected`(alg·x5c 서명자 체인) · `signature` 세 필드다.
검증 측이 JSON 을 다시 직렬화하지 않고 파일에 실린 바이트 그대로 서명을 확인하므로,
필드 순서·공백 차이로 서명이 깨지지 않는다.

> ⚠️ **인증체인을 재발급하면 두 파일 모두 무효가 된다.** CA 도 서명자도 바뀌기 때문이다.
> `scripts/gen-certs.ps1` 실행 뒤에는 검증 화면의 **"서명된 KR-TL 내려받기"** 버튼으로 새로 받는다
> (`GET /api/pdf/krtl/sample?format=xml|jws`, 기본 `xml`). 그 편이 목록 내용과 실제 인증체인이 어긋나지 않는다.

### 2.4 키스토어 (PKCS#12, 비밀번호 **`111111`**)

| 파일 | 별칭 | 담긴 내용 |
| --- | --- | --- |
| `kisa-tl-signer.p12` | `tl-signer` | TL 서명자 + KISA CA + 루트 |
| `kisa-joint-user.p12` | `joint-user` | 공동인증 사용자 + Joint CA + 루트 |
| `kisa-financial-user.p12` | `financial-user` | 금융인증 사용자 + Financial CA + 루트 |
| `kisa-test-cert.p12` | `test-user` | 시험 사용자 + Test CA + 루트 |
| `kisa-all.p12` | 위 4개 전부 | 최종개체 4종 통합 |
| `truststore.p12` | CA별 별칭 5개 | **신뢰 앵커 전용** — 루트 + 중간 CA 4장, 개인키 없음 |

최종개체 키스토어는 `[최종개체, 발급 중간 CA, 루트]` 순으로 체인을 담는다.
PAdES 서명 시 이 체인이 그대로 PDF 서명에 실리므로, 검증 측은 루트만 신뢰하면 경로가 완성된다.

`kisa-all.p12` 는 네 개를 한 파일에 모은 것이라, 서명 화면처럼 "첫 개인키 항목"을 쓰는
경우에는 어느 서명자가 선택될지 정해지지 않는다. 서명자를 지정하려면 개별 `.p12` 를 쓴다.

> 트러스트스토어는 `truststore.p12`(PKCS#12) 와 `ca-chain.pem`(PEM) 두 형태로 있다.
> 검증 화면은 둘 다 받는다. `truststore.p12` 는 `krdss cert p12` 가 개인키를 요구해 만들 수 없어
> `keytool -importcert` 로 만드는데, keytool 이 키스토어 비밀번호를 6자 이상으로 강제한다.
> 따라서 **비밀번호를 6자 미만으로 바꾸면 이 파일은 생성되지 않는다**(그때는 `ca-chain.pem` 사용).

---

## 3. 비밀번호

저장소의 모든 테스트 키스토어 비밀번호는 **`111111`** 로 통일되어 있다.

| 키스토어 | 위치 | 쓰는 곳 |
| --- | --- | --- |
| 최종개체 `.p12` · `truststore.p12` | `output/certs/` | 로컬 PDF 서명·검증 화면 |
| `joint-ca.p12` | `poc/poc-relying-party/src/main/resources/pki/`<br>`poc/poc-tsp-sim/src/main/resources/pki/` | 특허-B CA 서버 발급 키 (`krdss.ca.keystore-password`) |
| `demo-tls.p12` | `certs/` (`scripts/gen-demo-tls.ps1` 로 생성) | 태블릿 시연용 HTTPS (`server.ssl.key-store-password`) |

---

## 4. 사용법 — 로컬 PDF 서명·검증 화면

`http://localhost:8080/local-sign.html` (기동: `pwsh scripts\whale-startup.ps1`)

### 서명

| 입력란 | 넣는 파일 |
| --- | --- |
| PDF 파일 | 서명할 PDF |
| 인증서 파일 (PKCS#12) | `kisa-joint-user.p12` (또는 다른 최종개체 `.p12`) |
| 인증서 비밀번호 | `111111` |

### 검증

| 입력란 | 넣는 파일 |
| --- | --- |
| 서명된 PDF | 위에서 만든 서명본 |
| 트러스트스토어 | `truststore.p12` (비밀번호 `111111`) 또는 `ca-chain.pem` (비밀번호 불필요) |
| KR-TL | `kr-tl-signed.xml` (또는 `.json`) — 검증 화면의 "서명된 KR-TL 내려받기" 버튼으로도 받는다 |

KR-TL 은 이 디렉터리의 CA 인증서로 만들어지므로 중간 CA 넷이 모두 `GRANTED` 로 등재된다.

검증은 두 단계로 이뤄진다.

1. **KR-TL 자체의 전자서명** — 서명값이 본문과 맞는지, 서명자(`kisa-tl-signer`)가 트러스트스토어
   앵커까지 이어지는지. 하나라도 어긋나면 그 목록을 판정 근거로 쓰지 않는다.
   XML 은 DSS 로 XAdES 서명을 검증해 `TOTAL_PASSED` / `HASH_FAILURE` / `NO_CERTIFICATE_CHAIN_FOUND`
   같은 판정을 그대로 리포트에 싣는다.
2. **등재 여부** — 문서 서명자의 발급기관이 목록에 `GRANTED` 로 실려 있는지.

둘 다 통과해야 `TOTAL_PASSED` 이고, 어느 한쪽이 어긋나면 `INDETERMINATE` 다.
KR-TL 을 아예 내지 않으면 평가를 생략하므로 판정을 낮추지 않는다.

---

## 5. 재생성

```powershell
pwsh scripts\gen-certs.ps1                  # 비밀번호 111111 (기본)
pwsh scripts\gen-certs.ps1 -Password 111111
pwsh scripts\gen-certs.ps1 -SkipBuild       # krdss-cli 재빌드 없이
```

실행하면 기존 `output/certs` 를 `output/certs.bak-<타임스탬프>` 로 옮긴 뒤 새로 만든다.
이 문서(`README.md`)는 새 디렉터리로 그대로 옮겨 온다.
**`kr-tl-signed.xml` · `kr-tl-signed.json` 은 재생성되지 않는다** — 서명이 필요하므로
검증 화면의 내려받기 버튼으로 새로 받는다.
백업 디렉터리는 `.gitignore` 대상이다 — 이전 자료는 git 이력에도 남아 있다.

발급 자체는 저장소의 `krdss cert` 도구가 수행한다. 도구 사용법은
[tools/krdss-cli/docs/cert-tool.md](../../tools/krdss-cli/docs/cert-tool.md) 참고.

---

## 6. 확인 명령

```bash
# 발급 관계 보기
keytool -printcert -file output/certs/kisa-joint-user.crt

# 체인 검증 (EE → 중간 CA → 루트)
openssl verify -CAfile output/certs/kisa-rootca.crt \
  -untrusted output/certs/ca-chain.pem output/certs/kisa-joint-user.crt

# 키스토어 내용
keytool -list -v -keystore output/certs/kisa-joint-user.p12 -storetype PKCS12 -storepass 111111
keytool -list -keystore output/certs/truststore.p12 -storetype PKCS12 -storepass 111111
```

---

## 7. 이전 체인과 달라진 점

| 항목 | 이전 | 현재 |
| --- | --- | --- |
| `Test_User` 발급자 | **루트 CA가 직접 발급** | `KISA_Test_CA` 신설 후 그 아래로 이동 |
| 중간 CA | 3개 (`kisa-ca`, joint, financial) | 4개 (`kisa-test-ca` 추가) |
| `ca-chain.pem` | 루트 + `kisa-ca` 2장 — joint/financial CA 누락 | 루트 + 중간 CA 4장 전부 |
| 풀체인 PEM | `ee-fullchain.pem` 1개 (어느 EE인지 불명확) | 최종개체별 `*-fullchain.pem` 4개 |
| 트러스트스토어 | 없음 | `truststore.p12` + `ca-chain.pem` |
| KR-TL | 없음 (`kisa-tl-template.json` 은 생성 설정) | `kr-tl-signed.xml`(TS 119 612/XAdES) · `kr-tl-signed.json`(JWS) |
| 키스토어 비밀번호 | `1234` (그리고 `joint-ca.p12` 는 `changeit`) | `111111` — 저장소 전체 공통 |
| `kisa-tl-template.json` | 있음 (다른 PC 절대경로 `F:/devwork/...` 가 박힌 생성 설정) | 제거 — 재생성은 `scripts/gen-certs.ps1` 이 담당 |
| 재생성 수단 | 없음 (수작업 흔적만 남음) | `scripts/gen-certs.ps1` |

`ca-chain.pem` 이 joint/financial CA를 빠뜨리고 있던 것이 실질적인 결함이었다.
루트가 앵커라 검증 자체는 통과했지만, 중간 CA를 담지 않은 번들이라 이름과 내용이 어긋나 있었다.

이전 자료는 `output/certs.bak-<타임스탬프>/` 에 그대로 보관되어 있다.
