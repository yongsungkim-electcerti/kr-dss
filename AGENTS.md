# AGENTS.md — AI 에이전트 공통 작업 지침

> 이 파일은 **Codex · Antigravity · Claude Code** 등 모든 AI 코딩 에이전트가 본 저장소에서
> 작업할 때 따르는 **단일 표준 지침(Single Source of Truth)** 이다.
> 도구별 설정 파일(CLAUDE.md / GEMINI.md 등)은 이 파일을 가리키도록 구성한다.

---

## 0. 도구별 진입점 (모두 이 파일을 읽는다)

| 도구 | 자동으로 읽는 파일 | 구성 |
| --- | --- | --- |
| **OpenAI Codex** | `AGENTS.md` | 이 파일을 직접 사용 |
| **Google Antigravity** | `AGENTS.md` (워크스페이스 규칙) | 이 파일을 직접 사용 |
| **Claude Code** | `CLAUDE.md` | `CLAUDE.md` 가 이 파일을 import |

> 새 에이전트/IDE를 추가할 때도 **반드시 이 파일을 참조**하도록 설정한다. 규칙을 중복 작성하지 말 것.

---

## 1. 프로젝트 개요

- **사업**: 국내 전자서명 규격 및 신뢰검증 기술 실증 연구 (발주: KISA)
- **산출물**: KR-AdES(서명규격) · KR-TL(신뢰목록) · KR-DSS(통합 SDK) — 자세한 내용은 [README.md](README.md)
- **스택**: Java 21 + Gradle 8.10.2 (멀티모듈), EU DSS 6.1 기반, Spring Boot 3(PoC)

### 모듈 지도 (작업 분담 단위)
```
kr-ades/    [과업1] core + 6종 포맷 어댑터(xades/cades/pades/jades/hades/mades)
kr-tl/      [과업2] model / builder / client
kr-dss-sdk/ [과업3] api / crypto / core / report
poc/        가상 인정사업자 / KISA-TL / 이용사 서비스 (Spring Boot)
tools/      krdss-cli
build-logic/ 공통 빌드 컨벤션 플러그인
gradle/libs.versions.toml  의존성 버전 단일 관리
```

---

## 2. 빌드 · 실행 명령 (모든 에이전트 공통)

```bash
./gradlew build                              # 전체 빌드 + 테스트 (커밋 전 필수 통과)
./gradlew classes                            # 컴파일만
./gradlew :poc:poc-relying-party:bootRun     # 이용사 PoC (:8080)
./gradlew :tools:krdss-cli:run --args="..."  # CLI 실행
./gradlew printModules                        # 모듈 목록
```
- Windows: `gradlew.bat`, Unix/CI: `./gradlew`
- 로컬 JDK가 21 미만이어도 Gradle toolchain이 JDK 21을 자동 프로비저닝한다.

---

## 3. 코드 컨벤션

- **언어/패키지**: Java 21, base package `com.electcerti.krdss.*`
- **들여쓰기**: 스페이스 4칸, 인코딩 UTF-8, 줄바꿈은 `.gitattributes` 규칙 준수(`gradlew`는 LF 고정)
- **의존성 추가**: 반드시 `gradle/libs.versions.toml` 버전 카탈로그를 통해서만 추가 (모듈 build 파일에 버전 하드코딩 금지)
- **모듈 경계 준수**: 의존 방향은 항상 상위(오케스트레이션) → 하위(api/core). 역방향 의존 금지.
- 주석/문서는 한국어 허용, 식별자는 영어.

---

## 4. ⚠️ 동시 작업 규칙 (멀티 에이전트 핵심)

여러 에이전트(Codex·Antigravity·Claude)가 **동시에 같은 저장소**에 접속해 작업할 때 충돌을
막기 위한 규칙이다. **반드시 준수한다.**

### 4.1 작업 디렉터리 분리 — git worktree 사용
동일 머신에서 두 에이전트가 동시에 작업하면 작업 트리와 빌드 산출물(`build/`)이 서로를 덮어쓴다.
**에이전트마다 별도 worktree** 를 사용해 물리적으로 분리한다.

```bash
# 메인 체크아웃(F:\devwork\kr-dss)은 통합/리뷰용으로 두고, 에이전트별 작업 공간을 만든다
git worktree add ../kr-dss-codex        feat/codex-<topic>
git worktree add ../kr-dss-antigravity  feat/antigravity-<topic>
# 작업 종료 후
git worktree remove ../kr-dss-codex
```

### 4.2 브랜치 전략 — 1 에이전트 = 1 브랜치
- `main` 직접 커밋·push **금지**. 모든 변경은 작업 브랜치 → PR 로 병합.
- 브랜치 명명: `feat/<agent>-<topic>` · `fix/<agent>-<topic>`
  - 예: `feat/codex-pades-sign`, `feat/antigravity-krtl-client`
- `<agent>` = `codex` | `antigravity` | `claude`

### 4.3 작업 분담 — 모듈 단위로 점유
- **하나의 모듈/디렉터리를 두 에이전트가 동시에 수정하지 않는다.** (위 모듈 지도 기준 분담)
- 공용 파일(`settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/`)을 바꿀 때는
  **작은 단위로 변경 → 즉시 커밋 → 즉시 push** 하여 점유 시간을 최소화한다.
- 작업 시작 시 브랜치를 만들고 바로 push 하면, 다른 에이전트가 원격에서 "누가 무엇을 하는지" 확인할 수 있다.

### 4.4 동기화 규칙
1. **작업 시작 전**: `git fetch origin && git rebase origin/main`
2. **작게 자주 커밋**, 자주 push (장시간 미push 금지)
3. **커밋 전 빌드 통과 필수**: `./gradlew build`
4. **커밋 메시지에 에이전트 태그**:
   ```
   feat(kr-tl): KR-TL 클라이언트 동기화 구현 [antigravity]
   ```
   형식: `<type>(<scope>): <요약> [<agent>]` — type: feat|fix|chore|docs|refactor|test
5. 충돌 발생 시 `git rebase` 로 해결하고, 판단이 어려우면 **사람에게 확인**한다.

### 4.5 금지 사항
- `build/`, `.gradle/`, IDE 산출물 커밋 금지 (`.gitignore` 준수)
- 다른 에이전트의 작업 브랜치에 직접 commit/force-push 금지
- `main` 에 `--force` push 금지
- 사용자 동의 없이 외부로 코드 전송·배포·릴리스 금지

---

## 5. PR / 병합

- 작업 완료 시 작업 브랜치 → `main` 으로 PR 생성, 가능하면 사람 또는 다른 에이전트가 리뷰.
- PR 본문에 변경 모듈·테스트 결과 명시.
- 병합은 `main` 빌드가 통과하는 상태에서만.

---

## 6. 충돌·불확실 시

확신이 없거나, 광범위한 리팩터링·삭제·외부 영향이 있는 작업은 **진행 전 사용자에게 확인**한다.
다른 에이전트의 변경과 겹칠 가능성이 보이면 멈추고 상태를 보고한다.
