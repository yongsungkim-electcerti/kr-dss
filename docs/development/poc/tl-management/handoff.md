# 현재 인계

## Claude 관리 화면·초안 저장 (2026-10-09)

- 브랜치 `feat/claude-tl-admin` (main ← PR #29 병합 후 분기). 결과: [33](reviews/33-관리화면-초안저장-구현결과.md).
- 사용자 결정: REST 계약 문서·외부 사업자/서비스 관리 인터페이스는 만들지 않는다. 관리자가 PoC 관리 화면에서 사업자를 직접 등록하고 인증서를 입력한다. 관리 서비스 개발에 집중한다.
- 실행: `gradlew.bat :poc:poc-kisa-tl:bootRun` → http://localhost:8081 . 저장 루트는 main의 `runtime/tl-management` (Git 비관리). [경로 설정](../../../guides/poc/tl-management/local-data.md)을 따른다.
- 전용 스크립트(2026-10-12): `scripts/poc/tl-management/tl-admin-up.ps1`(백그라운드 기동·포트 대기, `-Foreground`·`-Open`, 서명 키 없으면 경고, 로그 `logs/kisa-tl.log`)과 `tl-admin-down.ps1`(8081만 종료, Gradle 데몬은 `-StopGradleDaemon` 지정 시에만 정지).
- PoC 키 체계 생성 완료(2026-10-09): `runtime/tl-management/pki` 세트 krtl-poc-pki-v1, 22개 노드, UN 미등재 대조군 포함. 설계 27 상단 참조. Git 비관리. `scripts/poc/tl-management/gen-tl-pki.ps1`은 main 자료 저장소에 직접 생성하며 기존 키는 재생성하지 않는다. 2026-10-26 이후 워크트리에 키 사본을 두지 않는다.
- XML 서명·발행본 보관 완료(2026-10-09): [34](reviews/34-XML서명-발행본보관-구현결과.md). state.json v2, issued/<issuanceId>, BASELINE/TRIAL, requestId 중복 처리.
- 제공본 선택·IF-07 완료(2026-10-09): [35](reviews/35-제공본선택-IF07-구현결과.md). 공개 주소 `http://localhost:8081/tl/kr-tl.xml`, `/tl/kr-tl.sha2`.
- TL 수신 검증 샘플 완료(2026-10-09): [36](reviews/36-수신검증샘플-구현결과.md), `src/poc/tl-viewer-sample`. 발행 서명을 TS 119 612 Annex B.1.0(URI=#kr-tl, enveloped→exc-c14n)로 수정했다.
- 관리 기능 목록·시연 데이터(2026-10-12): [37](reviews/37-관리기능-목록-구현상태.md). `scripts/poc/tl-management/seed-demo-data.ps1`이 PoC 키 세트의 TL 등재 노드(사업자 4, 서비스 10)를 관리 API로 입력한다(재실행 시 건너뜀, `-Publish`는 정상 발행·제공본 선택까지).
- 다음 작업 후보: 상태 시작 시각 오류 TL 시험 조작, KR-DSS SDK(Java) 수신 모듈 이식, 단계 7 기술 검증·인계.

## 현재 기준과 후속 검증

공통 모델은 현재·이력과 다중 인증서를 지원한다. XML 재생성은 모델 밖 필드를 보존하지 않으므로 서명 원문 바이트를 별도 보관한다. FOUND는 시간 구간 검색 결과이며 인정·서명·체인·정책 검증 통과를 뜻하지 않는다. BASELINE과 TRIAL은 분리하며 시험 발행은 정상 이력·권장 순번을 바꾸지 않는다.

전체 XSD 적합성, 소비자 프로파일 지원, 파일 원자 교체·중단 복구는 후속 검증 대상이다. 과거의 완료 전 인계는 main 로컬 백업 snapshot에 보존했다. [정리 기록](../../workspace-layout/tl-management-2026-10-26.md)을 참조한다.
