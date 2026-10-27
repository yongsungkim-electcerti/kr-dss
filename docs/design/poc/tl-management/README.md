# TrustList 개념증명 — 설계·개발 산출물

기준일: 2026-10-28. 현재 검토 대상은 **TL을 실제 검증에 사용하는 개념증명 요구사항 10개와 산출물 4종**이다. 사업자 연계는 제외하고 인증서 파일·최소 신뢰정보 입력으로 실증한다. 엑셀·화면 시안은 [산출물 목록](artifacts/README.md)에서 설계 문서와 함께 관리한다.

## 검토 순서

1. [PoC 요구사항·범위](poc-requirements.md): 현재 검토할 초안.
2. [현재 엑셀 검토본 v0.4](artifacts/tl-poc-20261028/TrustList_PoC_요구사항_기능목록_v0.4.xlsx): 요구사항·기능목록·화면 변경. 이전 버전은 산출물 목록에 구분한다.
3. [공식 근거와 적용 구분](sources.md), [Dev-Pulse 지침 보완안](dev-pulse-guidance-changes.md).
4. [기능목록·화면설계](poc-ui-design/README.md): v0.4 변경을 반영한 기능 27개·화면 4개 분류. D1의 설계 부속이다.
5. [개발 상태·시험](../../../development/poc/tl-management/README.md), [실행 안내](../../../guides/poc/tl-management/tl-verification-poc.md).

사용자가 제공한 시나리오 2개는 비공식 예상 사례이며 정식 시나리오·인수 기준으로 승계하지 않는다. 핵심 TL 검증·비교는 구현·시험했으며, 전체 요구의 검증 완료와는 구분한다.

## 이전 상세안 — 현재 PoC의 의무 목록이 아님

엑셀 검토본: [요구사항 70개·도출 산출물 24종](artifacts/tl-requirements-20261026/신뢰체계_요구사항_및_산출물정의_v0.2.xlsx). 산출물별 필수 내용·검수 기준·작성 상태를 포함한다. 엑셀 수정 의견은 검토 후 아래 원본에 반영한다.

1. [요구사항 정의서](requirements.md): 범위·개별 요구·인수 기준. 수정 요청은 ID로 지정한다.
2. [공식 근거와 적용 구분](sources.md): 원문·판본·조항·국내 적용·PoC 예외.
3. [미결정 사항](open-questions.md): 요구사항 확정 전에 필요한 결정.
4. [시험 명세](test-spec.md): 요구별 검증 입력·기대 결과. 모든 시험은 미실행.
5. [추적표](traceability.md): Dev-Pulse가 생성하는 요구→설계→시험 연결.

## 관리 자료

- [진행 상태](state.md), [설계 연결 후보](detailed-design.md), [검사 결과](validation.md)
- [표준 취득 메타데이터](source-register.json), [기존 설계 백업](archive.md)
- [Dev-Pulse 지침 보완안](dev-pulse-guidance-changes.md)

기존 설계·도식 72개는 main 백업에 보관한다. 현재 폴더에는 신규 요구사항 묶음만 둔다. 다운로드한 원문은 main의 `runtime/reference-library/tl-management/2026-10-26/`에 보관하고 Git에는 경로·해시·목록을 남긴다.

범위 보류 항목은 이번 PoC의 구현 의무에 추가하지 않는다. 이전 구현·시험 기록은 참고 자료이며 신규 TC의 통과 증거로 자동 승계하지 않는다. 현재 구현·시험 범위는 개발 상태표와 증거 문서를 따른다.
