# 현재 인계

## Claude 관리 화면·초안 저장 (2026-10-09)

- 브랜치 `feat/claude-tl-admin` (main ← PR #29 병합 후 분기). 결과: [33](reviews/33-관리화면-초안저장-구현결과.md).
- 사용자 결정: REST 계약 문서·외부 사업자/서비스 관리 인터페이스는 만들지 않는다. 관리자가 PoC 관리 화면에서 사업자를 직접 등록하고 인증서를 입력한다. 관리 서비스 개발에 집중한다.
- 실행: `gradlew.bat :poc:poc-kisa-tl:bootRun` → http://localhost:8081 . 저장 루트 `runtime/tl-management` (Git 비관리).
- PoC 키 체계 생성 완료(2026-10-09): `runtime/tl-management/pki` 세트 krtl-poc-pki-v1, 22개 노드, UN 미등재 대조군 포함. 설계 27 상단 참조. 다른 체크아웃에서는 같은 명령으로 새로 생성해야 한다(Git 비관리).
- 다음 작업: XML 생성·T-02 키 파일 서명 → issuanceId 불변 보관·BASELINE/TRIAL → 제공본 선택 → IF-07 → 화면 발행 메뉴 연결.

---

## Codex → Claude 개발 인계

인계일: 2026-10-09. 사용자가 Claude로 개발을 이관하며 Codex는 문서 정리와 커밋 후 작업을 종료한다. Claude 세션 실행은 사용자가 진행한다.

### 작업 위치와 시작 방법

- 저장소: `C:/Users/sol74/orca/workspaces/kr-dss/kr-tl`
- 인계 브랜치: `feat/codex-tl-model-xml`. 이번 인계 커밋은 이 브랜치의 최신 커밋이다.
- 개념 설계 원문 보관·검토의 선행 커밋: `2398a2c`.
- main 병합·원격 push는 이번 인계 범위에 포함하지 않았다. 새 작업을 main에서 시작하면 이번 구현이 없을 수 있으므로 인계 브랜치를 기준으로 분기한다.
- Codex 종료 후 같은 체크아웃을 이어받는 경우: `git switch -c feat/claude-tl-registry feat/codex-tl-model-xml`. 별도 Orca worktree를 사용할 경우에도 동일한 인계 브랜치/커밋을 기준으로 만든다.
- 기존 Codex 브랜치에 직접 후속 커밋하지 말고 Claude 작업 브랜치를 사용한다. 루트 AGENTS.md와 CLAUDE.md의 프로젝트 지침을 먼저 읽는다.

### (완료·대체됨) 다음 작업: 관리 API·초안 영속 저장 — 위 Claude 항목 참조

1. [확정 범위](../../../design/poc/tl-management/scope.md), [PoC 적용 기준](../../../design/poc/tl-management/concept-v02-application.md), [프로파일](../../../design/poc/tl-management/profile-poc-v1.md), [발행·복구 계약](../../../design/poc/tl-management/issuance-recovery.md)을 읽는다.
2. `src/poc/poc-kisa-tl`에서 사업자·서비스 입력/수정/조회와 초안 저장을 구현한다. 현재 KrTlAdminController의 고정 숫자·발행 성공 문구는 실제 구현 증거가 아니다.
3. `runtime/tl-management/registry/revisions/`의 불변 revision과 `publication/state.json`의 활성 초안 포인터를 구현한다. 단일 작성자 잠금, 기대 draftRevision 충돌 검사, 원자 교체와 재기동 복원을 연결한다.
4. 인증서 입력에서 DER·공개키·Subject·SKI를 추출·검증한다. 내부 providerId/serviceId를 유지하고 동일 키 갱신과 새 키 등록을 구별한다. 공통 변경 연산에 필요한 SKI를 누락하지 않는다.
5. API는 기존 상세 설계의 책임을 따르되 구체적인 관리 REST 경로·요청/응답은 구현 전 짧게 문서화한다. IF-07 공개 조회 경로와 섞지 않는다.
6. 생성·수정·저장·재기동 복원, 오래된 revision 거부, 저장 실패 시 기존 상태 유지, 인증서 갱신/키 교체를 테스트하고 전체 빌드를 확인한다.

이 단계의 완료 기준은 관리 입력이 저장되고 재기동 후 동일하게 복원되며, 동시 편집과 저장 실패로 정상 초안이 훼손되지 않는 것이다. 이후 실제 XML 서명·불변 발행본 보관·제공본 선택·IF-07·관리 화면 연결 순으로 진행한다.

### 구현상 주의와 검증 근거

- [구현 결과 32](reviews/32-공통모델-XML-구현결과.md)에 파일별 변경·호환성·한계를 기록했다. 공통 모델은 현재/이력과 다중 인증서, BigInteger 순번을 지원한다.
- XML 변환은 지원 필드의 변환기이며 전체 XSD 검증기가 아니다. 모델 밖 필드를 XML 재생성으로 보존한다고 가정하지 않는다. 서명 XML은 원문 바이트를 별도 보관한다.
- TrustServiceTimeline의 FOUND는 구간 검색 성공이다. 인정·서명·체인·정책 검증 통과가 아니다. 미지원 URI와 critical 확장을 자동 승인하지 않는다.
- 구형 JSON은 명시적 변환기로 읽는다. 기존 이용기관은 fromLegacyXml로 구형 XML을 읽는다. 이용기관 리포트의 version은 정수에서 십진 문자열로 변경됐다.
- 정상 BASELINE과 오류 시험 TRIAL을 분리한다. TRIAL의 큰 순번이나 과거본 제공으로 정상 기준 이력·권장 순번을 바꾸지 않는다.
- `gradlew.bat build` 최종 통과: 167개 작업(39 실행·128 최신 상태). JUnit 29개 suite, 126개 test, 실패·오류 0건. 추가 회귀 테스트 22건. 일부 기존 결과는 재사용했다.
- Javadoc 주석 누락·구형 호환 API 사용 경고는 남아 있다. 새 TL 키·인증체인 생성과 관리 발행 기능은 아직 하지 않았다.
- 원문 보관 자료의 SHA-256 14개와 문서 링크를 확인했다. `runtime/`의 로그·검증 보조 스크립트는 Git 비관리이며 다음 세션의 필수 입력이 아니다.

### 사용자 작업 방식

사용자는 요청 범위의 문서 읽기·파일 수정·명령 실행·작업 트리 확인·검증을 매번 질문하지 말고 자동 진행하도록 승인했다. 이 선호를 유지한다. 실행 환경의 필수 권한 통제는 따른다. 외부 배포·범위 확대·임의 삭제까지 승인한 것으로 확대 해석하지 않는다.

불필요·중복 자료의 백업은 main 체크아웃 `F:/kr-dss-works/kr-dss/_backup/`에만 두며 Git에 넣지 않는다. 기존 백업 `2026-10-08-layout-133204`와 `runtime/pki/legacy`의 시연 인증서를 새 TL 인증체계로 오인하지 않는다.

---

## 기존 결정과 참조

2026-10-09: 상세 설계 보완 후 `feat/codex-tl-model-xml`에서 공통 모델·XML 변환을 구현했다. 전체 빌드 통과, JUnit 126건 실패·오류 0건. 다음 작업은 관리 API·초안 저장이다. [구현 결과와 한계](reviews/32-공통모델-XML-구현결과.md)를 먼저 확인한다.

먼저 [설계 상태 점검](reviews/31-설계-상태-점검.md)과 [개정 설계 영향 검토](reviews/30-수정-개념설계-v02-영향검토.md)를 읽고 [개발 순서](README.md)를 따른다.

- PoC 담당 범위는 TL 관리시스템. 다른 PoC는 보존된 연계 자산이다.
- 실제 발행·영속 저장·조회 제공은 후속 구현 대상이다. 기존 관리 API의 성공 문구를 실제 발행 증거로 쓰지 않는다.
- 인증·감사·HSM·별도 WEB은 제외, 접수·심사·보완·반려는 메뉴만 제공한다.
- 오류 TL의 순번·시각을 조정하여 생성·제공할 수 있어야 한다.
- 새 TL 키·인증체인은 아직 생성하지 않았다. 기존 runtime/pki/legacy와 혼동하지 않는다.
- 폴더 정리 이전 세션 인계는 main 로컬 백업에 보관했다. [정리 기록](../../workspace-layout/README.md)에서 위치를 확인한다.
- 수정된 Google Docs v02는 전체 탭·표·그림을 [별도 보관](../../../design/poc/tl-management/sources/2026-10-08-concept-v02/README.md)했다. 원격 문서는 수정하지 않았고 이전 보고 보관본도 덮어쓰지 않았다.
- 개정안의 운영 게시 거부 정책을 PoC 오류 TL 제공 경로에 그대로 적용하지 않는다. 기존 사용자 확정 범위가 우선이다.
- [PoC 적용표](../../../design/poc/tl-management/concept-v02-application.md), [임시 프로파일](../../../design/poc/tl-management/profile-poc-v1.md), [발행·복구 계약](../../../design/poc/tl-management/issuance-recovery.md)을 다음 구현 기준으로 읽는다. CA에만 level을 두고, 시험 발행은 정상 이력·권장 순번을 바꾸지 않는다.
- 국가 공표 URI·자동 재발행·상세 시나리오는 확정하지 않았다. PoC 전용 URI를 공식 값으로 오인하지 않는다. 전체 XSD 적합성, 소비자의 프로파일 지원, 파일 원자 교체와 중단 복구는 구현 단계의 검증 항목이다.
