# PoC 실행 · 수동 검증 가이드

> KR-DSS PoC 서비스 일괄 기동/종료와, 검증 수행 중 로그 확인 방법. 특허-A Mode 1(WebAuthn
> 로컬 서명) 수동 브라우저 E2E 절차 포함. 최종 갱신: 2026-07-31

---

## 0. 서비스·포트 지도

| 서비스 | 포트 | 역할 | Mode 1 | Mode 2(원격) |
|---|---|---|:---:|:---:|
| poc-relying-party | 8080 | 이용사(SIC) + 데모 웹 UI + **Mode 1 CA·검증** | ✅ | ✅ |
| poc-rssp | 8090 | 원격서명 게이트웨이(CSC v2) | — | ✅ |
| poc-sam | 8091 | 서명활성화(2FA·SAD) | — | ✅ |
| poc-hsm | 8092 | Soft HSM(서명연산) | — | ✅ |

- **특허-A Mode 1** 은 relying-party(8080) **단독**으로 동작한다(SAM/HSM 불필요).
- **Mode 2(원격서명)** 데모는 4개 서비스를 모두 띄워야 한다.

---

## 1. 일괄 기동 / 종료 (PowerShell)

> 스크립트는 `scripts/` 에 있고, 로그는 repo 루트 `logs/<service>.log` 로 기록된다(`.gitignore` 처리됨).

```powershell
# 특허-A Mode 1 만 (8080 단독) — 브라우저 E2E 권장
pwsh scripts\poc-up.ps1 -Mode mode1

# 전체(원격서명 Mode 2 포함) — hsm→sam→rssp→relying-party 순서로 기동·대기
pwsh scripts\poc-up.ps1 -Mode full
pwsh scripts\poc-up.ps1            # 기본값 full

# 종료(8080/8090/8091/8092 정리 + Gradle 데몬 정지)
pwsh scripts\poc-down.ps1
```

- 각 서비스는 백그라운드로 뜨며, 스크립트가 **포트가 열릴 때까지 대기**한 뒤 `up ✓` 를 표시한다.
- 이미 사용 중인 포트는 건너뛴다(중복 기동 방지).

---

## 2. 수동 브라우저 E2E (특허-A Mode 1)

1. `pwsh scripts\poc-up.ps1 -Mode mode1` 실행 → `http://localhost:8080` 접속.
2. **① 인증서 발급 탭**: `특허-A Mode 1` 체크 → **WebAuthn 패스키 등록**
   - 인증기(Windows Hello/보안키)로 패스키 생성 → 서버가 그 공개키로 **CA 인증서 발급·저장**.
   - 발급된 서명자 인증서(PEM)가 화면에 표시되면 성공.
3. **② 전자서명 탭**: 결속 흐름 다이어그램(문서·시각·인증서 → SignedAttrs → Challenge)이 보임.
   - 원문 입력 → **전자서명 요청** → 생체/PIN 인증.
   - 결과에 **TOTAL_PASSED · 경로 WEBAUTHN** 와 단계별 check 가 표시됨.
4. **③ 서명 검증 탭**: ②에서 자동 전달된 결속 컨테이너로 **검증 수행** → TOTAL_PASSED 재확인.
   - 원문을 바꿔 검증하면 **TOTAL_FAILED · HASH_FAILURE**(문서 무결성 위반)로 떨어지는지 확인.

> 브라우저 없이 빠르게 확인하려면 HTTP 하니스 사용:
> ```bash
> python scripts/mode1-e2e.py            # requests, cryptography 필요
> # → register: ES256 / begin … / finish: TOTAL_PASSED path= WEBAUTHN
> ```

---

## 3. 검증 수행 중 로그 확인

### 3.1 실시간 로그(tail -f)
```powershell
# relying-party 로그 실시간
pwsh scripts\poc-logs.ps1 -Service relying-party

# 특허-A Mode 1 검증 흐름만 필터
pwsh scripts\poc-logs.ps1 -Service relying-party -Grep Mode1

# 다른 서비스
pwsh scripts\poc-logs.ps1 -Service sam
```

### 3.2 무엇이 보이나 (INFO 기본)
Mode 1 검증 시 다음 흐름이 한 줄씩 찍힌다:
```
[Mode1] 등록 cred=cHktY3JlZC0x alg=ES256 → CA 인증서 발급·저장(subject=CN=KR-DSS WebAuthn Signer)
[Mode1] begin cred=cHktY3JlZC0x docBytes=35 hash=SHA_256 → 결속 challenge=o6zMW5MWy_jq…(SignedAttrs 165B)
[Mode1] finish cHktY3JlZC0x → TOTAL_PASSED path=WEBAUTHN
```

### 3.3 단계별 check 까지 보기 (DEBUG)
검증 라우터의 단계별 PASS/FAIL(어서션 서명·문서 무결성·서명자 결속·정책 라우팅 등)을 보려면
`poc-relying-party/src/main/resources/application.yml` 에서:
```yaml
logging:
  level:
    com.electcerti.krdss: DEBUG   # INFO → DEBUG
```
로 바꾸고 재기동하면 다음과 같이 출력된다:
```
[Mode1]   PASS 정책 식별자 라우팅 — certificatePolicies → WEBAUTHN
[Mode1]   PASS WebAuthn 어서션 서명 — COSE 서명·flags·rpIdHash·challenge·origin 검증 성공
[Mode1]   PASS 문서 무결성(messageDigest) — 원문 다이제스트 일치
[Mode1]   PASS 서명자 결속(signingCertificateV2) — 서명자 인증서 해시 일치
```

### 3.4 브라우저 측 확인(보조)
- 검증 결과 JSON(`indication`/`signaturePath`/`checks`)은 화면과 **DevTools → Network** 의
  `/api/local/sign/finish`·`/api/local/verify` 응답에서도 그대로 확인할 수 있다.

---

## 4. 태블릿·QR 시연 (HTTPS / demo 프로파일)

WebAuthn 은 **보안 컨텍스트(HTTPS 또는 localhost)** 에서만 동작하고, **rpId 로 IP 주소를 쓸 수 없다.**
따라서 태블릿에서 시연하려면 PC 를 **호스트명 기준 HTTPS** 로 서비스해야 한다.

```powershell
# 1) 데모 Root CA + 서버 인증서 생성 (최초 1회)
pwsh scripts\gen-demo-tls.ps1 -Hostname sol-pc

# 2) PC 신뢰 저장소에 Root CA 설치 (관리자 PowerShell)
Import-Certificate -FilePath .\certs\krdss-demo-root-ca.crt -CertStoreLocation Cert:\LocalMachine\Root

# 3) 태블릿에 certs\krdss-demo-root-ca.crt 전송 → "CA 인증서" 로 설치
#    (Android: 설정 → 보안 → 암호화 및 자격증명 → CA 인증서 설치)

# 4) demo 프로파일로 기동
pwsh scripts\poc-up.ps1 -Mode mode1 -Demo

# 5) PC·태블릿 모두 https://sol-pc:8080 으로 접속
```

**반드시 지킬 것**

| 항목 | 규칙 |
|---|---|
| 접속 주소 | 항상 `https://sol-pc:8080`. **IP 주소로 접속하면 WebAuthn 이 동작하지 않는다** |
| 태블릿의 이름 해석 | 태블릿이 `sol-pc` 를 못 찾으면 공유기 DNS 에 호스트명을 등록하거나 hosts 항목을 추가 |
| rpId ↔ origin ↔ 접속 주소 | `krdss.rp.mode1.rp-id`(=`sol-pc`) · `allowed-origins`(=`https://sol-pc:8080`) · 실제 접속 주소 **3개가 한 세트**. 하나만 바꾸면 서명이 실패한다 |
| 프로파일 전환 시 | rpId 가 바뀌면 기존 패스키는 사용할 수 없다 → **패스키 재등록 필요** |

기본(프로파일 없음)은 `http://localhost:8080` · `rp-id=localhost` 로 동작하므로,
로컬 개발은 종전대로 `pwsh scripts\poc-up.ps1 -Mode mode1` 만 하면 된다.

> rpId 는 서버 설정 **단일 출처**다. 화면은 `/api/local/webauthn-config` 로 rpId 를 받아
> 등록(`create()`)·서명(`get()`) 모두에 같은 값을 쓰고, 접속 주소와 어긋나면 상단에 경고 배너를 띄운다.

---

## 5. 트러블슈팅

| 증상 | 조치 |
|---|---|
| `포트 8080 이미 사용 중` | 기존 인스턴스 종료: `pwsh scripts\poc-down.ps1` |
| `up` 안 뜨고 대기 만료 | `logs\<service>.log`/`<service>.err.log` 확인(포트 충돌·컴파일 오류 등) |
| **`The relying party ID is not a registrable domain suffix of, nor equal to the current domain`** | 접속 주소와 `krdss.rp.mode1.rp-id` 불일치. `localhost` 로 접속했다면 프로파일 없이 기동하고, `sol-pc` 로 시연하려면 위 4장(`-Demo`)을 따른다. **rpId 를 바꾼 뒤에는 패스키 재등록 필요** |
| 서명 finish 에서 origin 검증 실패 | `krdss.rp.mode1.allowed-origins` 의 scheme·host·port 가 실제 접속 주소와 완전히 같아야 한다 |
| 패스키 등록 실패(브라우저) | HTTPS 아닌 `localhost` 는 WebAuthn 허용됨. 다른 호스트면 https 필요(4장) |
| 태블릿에서 인증서 경고 | `krdss-demo-root-ca.crt` 가 태블릿에 CA 인증서로 설치되어 있는지 확인 |
| 검증 INDETERMINATE(CREDENTIAL_NOT_REGISTERED) | 같은 세션에서 ① 등록 후 ②/③ 진행(레지스트리는 인메모리) |
| 해시 알고리즘 변경 시연 | `application.yml` `krdss.rp.mode1.hash-suite: SHA_384` 후 재기동 |

---

## 6. 참고
- 구현 현황: [특허A-HANDOFF.md](특허A-HANDOFF.md)
- 설계: [특허A-결속-검증라우터-설계.md](특허A-결속-검증라우터-설계.md)
- Mode 2(원격서명) 아키텍처: [docs/remote-signature.md](../remote-signature.md)
