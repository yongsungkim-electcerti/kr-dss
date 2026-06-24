# 원격전자서명 PoC (Remote Electronic Signature)

> eIDAS 원격서명 / CSC(Cloud Signature Consortium) v2 / EN 419 241(서버서명) 모델에 정합하는
> 원격전자서명 데모 구조. 이용사(SIC)가 문서 전체가 아닌 **다이제스트만** 전송하여
> 원격 QSCD 에서 서명값을 받아오는 2단계 흐름을 실증한다.

## 1. 구성요소

| 모듈 | 역할 | 표준 매핑 | 포트 |
| --- | --- | --- | --- |
| `kr-dss-sdk:kr-dss-remote` | 원격서명 클라이언트·CSC 모델·SAD·JWS/TOTP 유틸 | CSC v2 | — |
| `poc:poc-rssp` | **RSSP/SSA** — CSC v2 API 서버, OAuth2 서비스 인증, 오케스트레이션 | 서버서명 애플리케이션 | 8090 |
| `poc:poc-sam` | **SAM** — 서명자 2FA 인증·SAD 발급/검증, 단독 통제권 보장 | EN 419 241-1/-2 SAM | 8091 |
| `poc:poc-hsm` | **HSM** — 소프트HSM, 서명키 보관·서명연산 | QSCD | 8092 |
| `poc:poc-relying-party` | **SIC** — 이용사 단말 + **웹 데모 화면**, 인증·WebAuthn·서명객체 결합 | 서명요소 | 8080 |

### 인증 (CSC 2계층)

- **서비스 인증** — OAuth2 client_credentials. SIC 가 `/oauth2/token` 으로 Bearer 토큰을 받아
  모든 `/csc/v2/**` 호출에 첨부. RSSP 의 `BearerAuthInterceptor` 가 검증(없으면 401).
- **서명자 인증(2FA)** — `/csc/v2/credentials/authorize` 로 PIN(아는 것)+TOTP(가진 것)를
  서명 대상 해시에 묶어 제출. SAM 이 검증 후 **HMAC 서명된 SAD 토큰**(JWS HS256)을 발급한다.
  SAD 는 SAM 만 발급/검증하므로 SIC 가 자가발급할 수 없고 위변조도 탐지된다.

이용사 서명요소(**SIC**)는 `poc-relying-party` 의 `SignerInteractionComponent` 로 구현되며,
SDK의 `RemoteSignClient`(CSC v2) 로 RSSP 를 호출한다.

### 코어 연동 (kr-dss-core)

원격서명의 **포맷 독립적 전·후처리**는 코어가 담당한다(`RemoteSignCoordinator`).

```
문서 → [코어 prepare]  다이제스트 계산 + KR-AdES-Core 공통 모델 구성
     → [SIC 콜백]      SAD 구성(해시 바인딩) → RSSP→SAM→HSM → 서명값
     → [코어 assemble] 서명값·인증서를 KR-AdES 서명객체로 패키징
```

- 코어는 CSC/RSSP 전송을 알지 않고 `RemoteSigner` 콜백으로 위임 → 모듈 경계(상위→하위) 준수
- 콜백이 다이제스트를 받는 지점에서 SIC 가 SAD 해시 바인딩을 구성 → 서명 대상과 활성화 승인이 동일 해시로 결착
- 산출물: KR-AdES 서명객체(데모는 KR-JAdES 형식 JSON 컨테이너 — `profileId·documentInfo·signatureValue·signingCertificate`)

## 2. 신뢰·통제 흐름

```
[이용사 SIC]
   │  ⓪ 서비스 인증: POST /oauth2/token (client_credentials) → Bearer 액세스 토큰
   │  ① 문서 → 다이제스트 계산(로컬)
   │  ② 서명자 인증: POST /csc/v2/credentials/authorize { hashes, signerId, PIN, TOTP }
   ▼
[RSSP :8090]  CSC v2 진입점 · 오케스트레이션  (Bearer 토큰 검증)
   │  ③ POST /sam/authorize
   ▼
[SAM :8091]   서명자 2요소 인증 검증(PIN 해시 + TOTP) → SAD 토큰 발급(JWS, 해시 바인딩)
   │
   ▲  ④ SAD 토큰 반환
[이용사 SIC]
   │  ⑤ POST /csc/v2/signatures/signHash { hashes, sad(토큰) }
   ▼
[RSSP :8090] → [SAM :8091]   SAD 토큰 서명·바인딩·만료·nonce 검증 (sole control)
   │  ⑥ 검증 통과 시에만 활성화 토큰 제시
   ▼
[HSM :8092]   서명키 활성화 · 서명연산 (키는 경계 밖으로 노출 안 됨)
   │  ⑦ PKCS#1 v1.5 서명값 반환
   ▼
[이용사 SIC]  ⑧ 서명값을 문서에 결합 → KR-AdES 서명객체 완성
```

**핵심 통제점**
- **서비스 인증**: CSC API 는 OAuth2 Bearer 토큰 없이는 호출 불가(401)
- **서명자 인증(2FA)**: 아는 것(PIN) + 가진 것(TOTP) 검증을 통과해야 SAD 발급
- **SAD 무결성**: SAD 는 SAM 만의 비밀키로 HMAC 서명된 토큰 → 위변조 탐지, SIC 자가발급 불가
- **해시 바인딩**: SAD 가 승인한 다이제스트로만 서명 가능 → 다른 문서로의 전용(轉用) 방지
- **단독 통제권**: SAM 의 SAD 검증 없이는 HSM 서명키가 활성화되지 않음
- **키 비노출**: 개인키는 HSM 경계를 벗어나지 않고, 외부에는 서명값만 반환

## 3. 실행

3개 서비스를 각각 기동한다(별도 터미널 권장). 의존 순서상 HSM → SAM → RSSP.

```bash
./gradlew :poc:poc-hsm:bootRun     # :8092
./gradlew :poc:poc-sam:bootRun     # :8091
./gradlew :poc:poc-rssp:bootRun    # :8090
```

## 4. 웹 데모 화면 (SIC) — 권장 진입점

4개 서비스를 기동하면 `poc-relying-party`(SIC)가 **브라우저 데모 화면**을 제공한다.
<http://localhost:8080> 접속.

```bash
./gradlew :poc:poc-hsm:bootRun :poc:poc-sam:bootRun \
          :poc:poc-rssp:bootRun :poc:poc-relying-party:bootRun   # 각각 별도 터미널
# 브라우저: http://localhost:8080
```

화면 구성(3탭):

1. **① 인증서 발급** — 원격 QSCD(HSM) 서명자 인증서 발급/조회(PEM), **WebAuthn 패스키 등록**
2. **② 전자서명** — 원문 + 규격(포맷·레벨·패키징) 선택 → 서명 요청. 요청 시 코어가
   다이제스트를 계산하고 **SAD 생성과 함께 WebAuthn 사용자 인증**(생체/패스키)을 수행한 뒤
   SIC→RSSP→SAM→HSM 경로로 서명값을 받아 KR-AdES 서명객체를 만든다.
3. **③ 서명 검증** — KR-AdES 서명객체를 인증서 공개키로 실제 검증(원문 제공 시 무결성까지).

브라우저 서명 흐름(WebAuthn 2단계):

```
[브라우저] 원문+규격 → POST /api/sign/begin
[SIC] 코어 다이제스트 계산 → RSSP /credentials/authorize/begin → SAM 이 해시 바인딩 challenge 발급
[브라우저] navigator.credentials.get() → 생체/패스키 인증
[SIC] POST /api/sign/finish (어서션+PIN) → RSSP /credentials/authorize/finish
[SAM] PIN + WebAuthn 어서션 검증 → SAD 발급 → signHash → HSM 서명 → KR-AdES 서명객체
```

> 참조: 2025 전자서명 규격 데모 화면(<https://github.com/sol7442/KR-DSS-WEB>)의 생성/검증
> 2-뷰 구성을 따르되, 본 PoC 백엔드(원격서명·WebAuthn·SAD)에 맞춰 정적 화면으로 재구성했다.
> WebAuthn 은 localhost(보안 컨텍스트)에서 동작하며, 미지원 환경은 PIN+TOTP 대체 경로를 제공한다.

> **참고**: Gradle 데몬은 JDK 21 로 실행해야 한다(`build-logic` 플러그인이 21 타깃).
> `JAVA_HOME` 이 하위 버전이면 `JAVA_HOME=<jdk21> ./gradlew ...` 로 지정한다.

## 5. 단일 호출 API — 이용사(SIC) 엔드포인트

`poc-relying-party` 는 SIC·코어를 묶은 헤드리스 엔드포인트도 제공한다(WebAuthn 없이
PIN+TOTP). 문서 원본은 RSSP 로 전송되지 않고 **다이제스트만** 전송된다.

```bash
./gradlew :poc:poc-hsm:bootRun :poc:poc-sam:bootRun \
          :poc:poc-rssp:bootRun :poc:poc-relying-party:bootRun   # 각각 별도 터미널

curl -s -X POST http://localhost:8080/rp/remote-sign \
  -H 'Content-Type: application/json' \
  -d '{"text":"KR-DSS remote signature demo document.","format":"MADES","level":"KR_B"}'
```

응답: KR-AdES 서명객체(JSON). `signatureValue`(원격 HSM 서명값) + `signingCertificate` 포함.
요청 `format` 은 6종(XADES/CADES/PADES/JADES/HADES/MADES), `level` 은 KR_B/KR_T/KR_LT/KR_LTA.

> 잘못된 OTP·해시 바인딩 불일치·만료된 SAD 는 SAM 이 거부하여 서명값이 발급되지 않는다.

## 6. End-to-End 호출 예시 (curl, 저수준)

```bash
# (0) 서비스 인증 — OAuth2 액세스 토큰 발급
TOKEN=$(curl -s -X POST http://localhost:8090/oauth2/token -H 'Content-Type: application/json' \
  -d '{"grant_type":"client_credentials","client_id":"krdss-rp-client","client_secret":"rp-client-secret"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

# (1) 자격증명 목록 (이하 모든 CSC 호출에 Bearer 첨부)
curl -s -X POST http://localhost:8090/csc/v2/credentials/list \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
# → {"credentialIDs":["krdss-remote-signer"]}

# (2) 서명자 인증(2FA) → SAD 토큰 발급
#  - hashes 는 서명 대상 SHA-256 다이제스트(Base64), SAD 에 바인딩됨
#  - otp 는 TOTP 코드(인증앱). 데모 비밀 JBSWY3DPEHPK3PXP 로 생성 가능
SAD=$(curl -s -X POST http://localhost:8090/csc/v2/credentials/authorize \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"credentialID":"krdss-remote-signer","hashes":["<BASE64_SHA256_DIGEST>"],
       "signerId":"signer-001","pin":"123456","otp":"<TOTP_CODE>"}' \
  | sed -E 's/.*"sad":"([^"]+)".*/\1/')

# (3) SAD 로 서명 요청
curl -s -X POST http://localhost:8090/csc/v2/signatures/signHash \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"credentialID\":\"krdss-remote-signer\",
       \"hashes\":[\"<BASE64_SHA256_DIGEST>\"],
       \"hashAlgoOid\":\"2.16.840.1.101.3.4.2.1\",
       \"signAlgoOid\":\"1.2.840.113549.1.1.11\",
       \"sad\":\"$SAD\"}"
# → {"signatures":["<BASE64_SIGNATURE>"]}
```

> Bearer 없는 CSC 호출은 `401`. 잘못된 PIN/TOTP 는 authorize 가 `401`(서명자 인증 실패).
> 해시 바인딩 불일치·만료·nonce 재사용·SAD 위변조는 signHash 단계에서 SAM 이 거부한다.

## 7. 설정 (application.yml)

| 키 | 모듈 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `krdss.hsm.activation-token` | hsm | `SAM-INTERNAL-TOKEN` | SAM 만 제시하는 활성화 토큰(데모용 공유 비밀) |
| `krdss.sam.sad-secret` | sam | `sam-sad-...` | SAD 토큰 HMAC 서명 비밀키(SAM 전용) |
| `krdss.sam.sad-ttl-seconds` | sam | `300` | 발급된 SAD 유효시간 |
| `krdss.sam.signer.{id,pin,totp-secret}` | sam | signer-001 / 123456 / JBSWY3DPEHPK3PXP | 데모 서명자 2FA 자격 |
| `krdss.sam.hsm-*` | sam | localhost:8092 | 하위 HSM 연동 |
| `krdss.rssp.sam-/hsm-base-url` | rssp | localhost:8091/8092 | 하위 구성요소 연동 |
| `krdss.rssp.oauth.{client-id,client-secret,token-secret}` | rssp | krdss-rp-client / rp-client-secret | OAuth2 서비스 인증 |
| `krdss.rp.rssp-base-url` | relying-party | localhost:8090 | SIC → RSSP 진입점 |
| `krdss.rp.credential-id` | relying-party | `krdss-remote-signer` | 데모 자격증명 |
| `krdss.rp.oauth.{client-id,client-secret}` | relying-party | krdss-rp-client / rp-client-secret | OAuth2 클라이언트(RSSP 와 일치) |
| `krdss.rp.signer.{id,pin,totp-secret}` | relying-party | signer-001 / 123456 / JBSWY3DPEHPK3PXP | 서명자 2FA 자격(SAM 과 일치) |

## 8. 데모 한계 · 확장 방향

본 PoC 는 구조를 드러내기 위한 스캐폴딩이다. 운영화 시 다음을 보강한다.

- **인증**: HMAC 자체구현 JWS 대신 검증된 JWT 라이브러리·키 회전, OAuth2 인증서버 분리,
  서명자 인증을 실제 수단(간편인증·생체·FIDO·PKI)과 연동, PIN 해시를 PBKDF2/Argon2 로 강화
- **WebAuthn**: 현재는 challenge·origin·type·UP 플래그(데모 등급)만 검증한다. 운영화 시
  등록 단계에서 COSE 공개키를 보관하고 어서션 *서명*까지 검증하는 WebAuthn 라이브러리(webauthn4j 등)로 대체,
  attestation 검증·signCount 재생방지 추가
- SAD 검증을 HSM 경계 내부에서 수행, SAM↔HSM 내부 채널 봉인(현재는 공유 비밀 토큰으로 모사)
- KR-AdES 어댑터(`kr-dss-core`)와 연결해 서명값을 KR-PAdES/KR-XAdES 등으로 패키징
- KR-TL 연동으로 원격서명 인증서의 신뢰검증 수행
```
