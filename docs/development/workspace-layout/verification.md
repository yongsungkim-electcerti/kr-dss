# 폴더 정리 검증 기록

검증일: 2026-10-08. 대상: `feat/codex-workspace-layout`의 재배치 결과.

| 항목 | 결과 |
|---|---|
| 정리 전 백업 | 1,115개 실제 파일 복사 및 SHA-256 재검증 통과 |
| 외부 원문·실행 인증서 보존 | 189개 파일의 원본 바이트 일치 |
| 문서 링크 | Markdown 로컬 링크 1,048개 점검, 끊어진 링크 0개. 백업 링크 4개는 main 로컬 경로 |
| 전체 빌드 | `gradlew.bat build --console=plain` 성공, 163 tasks: 97 executed, 57 from cache, 9 up-to-date |
| 기존 테스트 결과 | JUnit XML 25개, tests 104, failures 0, errors 0. 일부 태스크는 캐시 사용 |
| 패키징·CLI | `gradlew.bat whalePackage :tools:krdss-cli:installDist --console=plain` 성공 |
| PowerShell | 이동한 전체 PS1 구문 분석 통과 |
| 관리 서버 | 격리 포트 18081에서 `/api/admin/dashboard` HTTP 200, JSON 구조 확인 |
| PDF 서버 | 패키지 작업 디렉터리에서 포트 18080 기동, `/api/pdf/krtl/sample` HTTP 200, TL XML·Signature 요소 확인 |
| IF-08 참조 샘플 | Java 21 실행, 예상한 `INDETERMINATE / CRYPTO_NOT_CONFIGURED` 결과 확인 |
| Git | staged diff 공백 검사 통과. runtime/results/main _backup 제외 확인. main 소스 작업 트리 변경 없음 |

격리 기동한 프로세스는 모두 종료했다. 실행 로그·샘플 TL·smoke.json은 `results/workspace-layout/`에 로컬 보관한다. 기존 테스트 성공은 새 TL 관리 기능·시나리오의 구현 완료를 뜻하지 않는다. HTTP 샘플 검사는 파일 경로 연결 확인이며 암호 서명의 별도 독립 검증이나 브라우저 UI 시험은 아니다.

전체 빌드의 기존 Javadoc 주석 경고는 남아 있다. 새 PoC 키 생성, 인증서 재발급, 원격 문서 수정, 외부 배포는 수행하지 않았다.
