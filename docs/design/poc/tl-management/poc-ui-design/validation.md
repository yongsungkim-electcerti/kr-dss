# 구현·검사 기록

2026-10-27, 사용자 지정 1~3차 범위: FUN-011 → FUN-015~017 → FUN-018~021.

| 검사 | 실제 결과 | 근거 |
|---|---|---|
| 전체 Gradle build | 성공 | main results/tl-verification/2026-10-27/gradle-build.log |
| 신규 JUnit 단위·통합 시험 | 6개 통과, 실패 0 | 같은 폴더의 TEST-*tlpoc*.xml |
| 실제 API 브라우저 실행 | 적용·PDF 검증·비교·불일치·거부 흐름 통과 | browser-check.json 및 화면 PNG |
| Dev-Pulse check | 오류 0, 경고 0, 90행 | devpulse-check.txt |
| Git diff 공백 검사 | 통과 | git diff --check |
| 전체 요구 종합 판정 | 검증 불충분 | verdict.md: 기존 설계 후보 시험 25개 전체는 미실행 상태 유지 |

검증 대상은 evidence.md와 main source-manifest.json에 커밋+작업트리 해시로 고정했다. 이번 핵심 자동 시험을 과거의 포괄적인 시험 후보 전체에 자동 승계하지 않았다. 정식 인수 시나리오 승인·부하/시간 제한 경계·모든 장애 복구 조합은 남아 있다. 1~3차 구현 완료와 전체 PoC 인수 완료를 구분한다.

브라우저 확인 이후 개인키 마커 차단과 화면 전환 시 이전 오류 배너 초기화를 추가했다. 개인키 차단은 최종 JUnit 시험 및 전체 빌드로 재확인했다. 브라우저 핵심 결과의 재사용 근거는 EVD-102에 기록했다.

시험용 서버는 확인 후 종료했다. [실행 안내](../../../../guides/poc/tl-management/tl-verification-poc.md)에 따라 다시 실행할 수 있다.

## v0.4 추가 검증 (2026-10-28)

전체 Gradle 빌드 성공, tlpoc 자동 시험 9개 실패 0. 실제 Edge에서 후보 검사/적용 분리·구조·필드 XML·원문 바이트 동일·저장 실행 TL 비교·모바일 화면 확인. EVD-103/104 참조. 기존 후보 TC-001~025는 미실행 상태 유지. 전체 요구의 판정은 검증 불충분.
