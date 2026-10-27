# 1·2·3차 실행 증거

대상 버전은 커밋과 작업트리 파일 해시를 함께 고정했다. main 비관리 로그 경로는 사용자 자료 관리 방침을 따른다.

| ID | 관련 시험 | 대상 버전 | 환경 | 명령·절차 | 입력 조건 | 기대 결과 | 실제 결과 | 실행 시각 | 종료 상태 | 로그 위치 | 재사용 근거 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| EVD-101 | TC-101, TC-102, TC-103, TC-104, TC-105, TC-106 | 919ddea685bdb7cbe5d68540f906152cd78ee943+worktree-6c3d5946f261 | Windows, Java 21, Gradle 8.10.2 | gradlew.bat build | tlpoc 시험의 임시 PKI·실제 PDF·TL 표본 | 정상·실패·대조 단언 충족 | 신규 6개 자동 시험 통과, 전체 빌드 성공 | 2026-10-27 | 0 | F:/kr-dss-works/kr-dss/results/tl-verification/2026-10-27/gradle-build.log | source-manifest.json 및 개별 JUnit XML로 고정 |
| EVD-102 | TC-107 | 919ddea685bdb7cbe5d68540f906152cd78ee943+worktree-6c3d5946f261 | Windows, Edge headless, 1440x1000 및 390x844 | tl-live-check.mjs, 실제 loopback API | 공개 시험 TL #1/#2 및 동일 PDF | 적용·검증·비교·거부를 실제 실행 | 전 단계 단언 통과, pageErrors 0 | 2026-10-27 | 0 | F:/kr-dss-works/kr-dss/results/tl-verification/2026-10-27/browser-check.json | 브라우저 확인 후 개인키 입력 차단 및 오류 배너 초기화만 추가. 핵심 검증·비교 결과 영향 없음; 추가 차단은 TC-105로 검증 |

| EVD-103 | TC-101, TC-102, TC-103, TC-104, TC-105, TC-106, TC-108, TC-109 | 919ddea685bdb7cbe5d68540f906152cd78ee943+worktree-2abd33e7acdd | Windows, Java 21, Gradle | gradlew.bat build | 임시 PKI와 실제 서명 PDF/TL | 전체 빌드 및 관련 시험 성공 | 전체 빌드 성공, tlpoc 9개 시험 실패 0 | 2026-10-28 | 0 | F:/kr-dss-works/kr-dss/results/tl-explorer/2026-10-28/test-summary.json | JUnit XML과 source-manifest.json 보존 |
| EVD-104 | TC-110 | 919ddea685bdb7cbe5d68540f906152cd78ee943+worktree-2abd33e7acdd | Windows, Edge headless | tl-explorer-check.mjs 및 explorer-final-check.mjs | 인정·정지 TL 및 동일 PDF·평가시각, HTML 문자열을 담은 비신뢰 후보 | 구조·변경·원문·검증 연계 | 10개 흐름 단언 통과, pageErrors 0. 최종 후보 패널·파싱 오류·문자 escaping 추가 확인 통과 | 2026-10-28 | 0 | F:/kr-dss-works/kr-dss/results/tl-explorer/2026-10-28/browser-check.json | 최종 UI 파일을 브라우저에 제공하여 추가 확인. 같은 폴더 final-ui-check.json 참조; 서버·검증 로직 불변 |
