# 인증서 발급 도구 (`krdss cert`)

KR-TL 신뢰목록·전자서명 검증 실증에 사용할 **테스트 PKI 체인**(ROOT CA → SUB CA → 최종개체)을
명령 한 줄로 발급하는 CLI 도구다. EU DSS / BouncyCastle 기반이며 `tools:krdss-cli` 모듈에 포함된다.

> ⚠️ **테스트·실증 전용.** 여기서 발급하는 인증서는 KISA 공인 체계와 무관한 자체 테스트 인증서다.
> 운영(실인증) 용도로 사용하지 않는다.

---

## 1. 빠른 시작

```bash
# 1) 샘플 템플릿 생성
./gradlew :tools:krdss-cli:run --args="cert chain --init build/pki/chain-template.json"

# 2) 템플릿으로 ROOT/SUB/EE 한 번에 발급
./gradlew :tools:krdss-cli:run --args="cert chain --template build/pki/chain-template.json"
```

발급 후 `build/pki/` 에 다음이 생성된다.

| 파일 | 설명 |
| --- | --- |
| `root.crt` / `root.key` | 루트 CA 인증서 / 개인키 (PEM) |
| `sub.crt` / `sub.key` | 중간 CA 인증서 / 개인키 (PEM) |
| `ee.crt` / `ee.key` | 최종개체 인증서 / 개인키 (PEM) |
| `ca-chain.pem` | 중간 CA + 루트 CA 묶음 (검증 시 `-untrusted` 용) |
| `ee-fullchain.pem` | EE + 중간 CA + 루트 CA 풀체인 |

> 개인키는 PKCS#8 PEM(평문)으로 저장된다. 키 파일은 외부 공유·커밋 금지(`build/` 는 `.gitignore` 대상).

---

## 2. 명령 구조

```
krdss cert
 ├─ gen      단일 인증서 한 장 발급
 └─ chain    템플릿(JSON) 하나로 ROOT→SUB→EE 전체 체인 일괄 발급
```

---

## 3. `cert chain` — 템플릿 기반 체인 일괄 발급

템플릿 JSON 하나로 루트·중간·최종개체를 순서대로 발급하고, 발급자 관계(ROOT→SUB→EE)를 자동으로 연결한다.

### 3.1 옵션

| 옵션 | 필수 | 설명 |
| --- | --- | --- |
| `-f`, `--template <경로>` | △ | 체인 템플릿 JSON 경로 |
| `--init <경로>` | △ | 샘플 템플릿 JSON을 해당 경로에 생성하고 종료 |

> `--template` 또는 `--init` 중 하나는 반드시 지정한다.

### 3.2 템플릿 JSON 스펙

```json
{
  "outDir": "build/pki",
  "keyAlg": "EC",
  "keySize": 256,
  "root": {
    "subject": "CN=KR Test Root CA,O=ELECTCERTI,C=KR",
    "days": 7300
  },
  "sub": {
    "subject": "CN=KR Sub CA,O=ELECTCERTI,C=KR",
    "days": 3650
  },
  "ee": {
    "subject": "CN=hong gildong,O=ELECTCERTI,C=KR",
    "days": 825,
    "san": ["test.example.kr"],
    "keyAlg": "RSA",
    "keySize": 2048
  }
}
```

#### 최상위 필드

| 필드 | 기본값 | 설명 |
| --- | --- | --- |
| `outDir` | `pki` | 산출물 출력 디렉터리 (없으면 자동 생성) |
| `keyAlg` | `EC` | 기본 키 알고리즘. 노드별로 재정의 가능 |
| `keySize` | `256` | 기본 키 크기. 노드별로 재정의 가능 |
| `root` / `sub` / `ee` | (필수) | 각 계층 노드 정의. 세 노드 모두 있어야 한다 |

#### 노드(`root`/`sub`/`ee`) 필드

| 필드 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `subject` | ✅ | — | 주체 DN. 예: `CN=...,O=...,C=KR` |
| `days` | | ROOT 7300 / SUB 3650 / EE 825 | 유효기간(일) |
| `san` | | — | **EE 전용** SAN(DNS) 목록 |
| `keyAlg` | | 최상위 값 | 키 알고리즘 재정의(`RSA`\|`EC`) |
| `keySize` | | 최상위 값 | 키 크기 재정의 |
| `out` | | `root`/`sub`/`ee` | 출력 파일명 접두사 재정의(`outDir` 기준 상대) |

> 알 수 없는 필드는 무시되므로 주석성 키를 추가해도 무방하다.

### 3.3 발급 결과 (예)

```
[cert] 체인 일괄 발급 완료
  [ROOT]
    subject : CN=KR Test Root CA,O=ELECTCERTI,C=KR
    issuer  : CN=KR Test Root CA,O=ELECTCERTI,C=KR
    ...
  [SUB]
    subject : CN=KR Sub CA,O=ELECTCERTI,C=KR
    issuer  : CN=KR Test Root CA,O=ELECTCERTI,C=KR
    ...
  [EE]
    subject : CN=hong gildong,O=ELECTCERTI,C=KR
    issuer  : CN=KR Sub CA,O=ELECTCERTI,C=KR
    ...
  ca-chain     -> .../build/pki/ca-chain.pem
  ee-fullchain -> .../build/pki/ee-fullchain.pem
```

---

## 4. `cert gen` — 단일 인증서 발급

체인 중 특정 한 장만, 또는 외부에서 받은 발급자 키로 직접 발급할 때 사용한다.

### 4.1 옵션

| 옵션 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `-t`, `--type` | | `EE` | `ROOT`(루트CA) \| `SUB`(중간CA) \| `EE`(최종개체) |
| `-s`, `--subject` | ✅ | — | 주체 DN |
| `-o`, `--out` | ✅ | — | 출력 접두사 → `<prefix>.crt`, `<prefix>.key` |
| `--issuer-cert` | △ | — | 발급자 인증서 PEM (SUB/EE 필수) |
| `--issuer-key` | △ | — | 발급자 개인키 PEM (SUB/EE 필수) |
| `--key-alg` | | `RSA` | `RSA` \| `EC` |
| `--key-size` | | `2048` | RSA 비트수(2048/3072/4096) 또는 EC 곡선(256/384/521) |
| `--days` | | `3650` | 유효기간(일) |
| `--san` | | — | SAN(DNS) 항목. 여러 번 지정 가능 |

### 4.2 예시 — 수동으로 체인 발급

```bash
# 루트 CA (자가서명)
./gradlew :tools:krdss-cli:run --args="cert gen --type ROOT \
  --subject CN=KR_Test_Root_CA,O=ELECTCERTI,C=KR \
  --out build/pki/root --key-alg EC --key-size 256 --days 7300"

# 중간 CA (루트가 발급)
./gradlew :tools:krdss-cli:run --args="cert gen --type SUB \
  --subject CN=KR_Sub_CA,O=ELECTCERTI,C=KR \
  --issuer-cert build/pki/root.crt --issuer-key build/pki/root.key \
  --out build/pki/sub --key-alg EC --key-size 256"

# 최종개체 (중간 CA가 발급)
./gradlew :tools:krdss-cli:run --args="cert gen --type EE \
  --subject CN=hong_gildong,O=ELECTCERTI,C=KR \
  --issuer-cert build/pki/sub.crt --issuer-key build/pki/sub.key \
  --out build/pki/ee --key-alg RSA --key-size 2048 --san test.example.kr"
```

---

## 4-A. `cert p12` — PKCS#12 키스토어 생성

인증서 + 개인키(+상위 CA 체인)를 `.p12` 키스토어 한 파일로 묶는다. 서명 PoC·키스토어 로딩 테스트에 사용한다.

### 옵션

| 옵션 | 필수 | 설명 |
| --- | --- | --- |
| `-c`, `--cert <경로>` | ✅ | 대상 인증서 PEM |
| `-k`, `--key <경로>` | ✅ | 대상 개인키 PEM |
| `--chain <경로>` | | 상위 CA 체인 PEM(중간/루트, 여러 장 가능) — 키스토어에 함께 저장 |
| `-o`, `--out <경로>` | ✅ | 출력 `.p12` 경로 |
| `-a`, `--alias <별칭>` | | 키 엔트리 별칭(미지정 시 인증서 CN) |
| `-p`, `--password <PW>` | ✅ | 키스토어 및 개인키 보호 비밀번호 |

### 예시

```bash
# 사용자 인증서 + (중간CA+루트) 체인을 비밀번호 1234 키스토어로
./gradlew :tools:krdss-cli:run --args="cert p12 \
  --cert build/pki/ee.crt --key build/pki/ee.key --chain build/pki/ca-chain.pem \
  --out build/pki/ee.p12 --alias user --password 1234"

# 검증
keytool -list -keystore build/pki/ee.p12 -storetype PKCS12 -storepass 1234
```

저장 키스토어에는 `[leaf, 중간CA…, 루트CA]` 순서로 인증서 체인이 함께 들어간다.

---

## 5. 인증서 프로파일 (확장필드)

유형별로 다음 X.509 v3 확장이 자동 설정된다.

| 확장 | ROOT / SUB (CA) | EE (최종개체) |
| --- | --- | --- |
| Basic Constraints | `CA:TRUE` (critical) | `CA:FALSE` (critical) |
| Key Usage | `keyCertSign`, `cRLSign` (critical) | `digitalSignature`, `nonRepudiation` (critical) |
| Extended Key Usage | — | `id-kp-clientAuth` |
| Subject Alternative Name | — | `--san`/`san` 지정 시 DNS 항목 |
| Subject Key Identifier | 자동 | 자동 |

- 서명 알고리즘: 발급자 키가 EC면 `SHA256withECDSA`, RSA면 `SHA256withRSA`
- 일련번호: 96비트 난수(`SecureRandom`)

---

## 6. 검증 방법 (openssl)

```bash
# 확장필드 확인
openssl x509 -in build/pki/ee.crt -noout -text

# 체인 검증 (EE ← SUB ← ROOT)
openssl verify -CAfile build/pki/root.crt -untrusted build/pki/sub.crt build/pki/ee.crt
# => build/pki/ee.crt: OK
```

---

## 7. 참고

- 구현: [`CertCommand.java`](../src/main/java/com/electcerti/krdss/cli/CertCommand.java)
- 의존성: BouncyCastle(`bcprov`,`bcpkix`), Jackson(template), picocli — 모두 `gradle/libs.versions.toml` 카탈로그 관리
- 작업 디렉터리: `./gradlew run` 의 기준 경로는 모듈 폴더(`tools/krdss-cli`)다. 상대 경로(`build/pki`)는 그 기준으로 해석되며, 절대 경로를 쓰면 임의 위치에 출력할 수 있다.
