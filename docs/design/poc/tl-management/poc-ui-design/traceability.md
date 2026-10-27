<!-- devpulse:generated -->

# 추적표

- 생성: devpulse.py 0.2.0 trace, 2026-10-28 (양식 2)
- 검증 대상 버전: 919ddea685bdb7cbe5d68540f906152cd78ee943+worktree-2abd33e7acdd
- 판정 대상: PoC
- 이 파일은 자동 생성된다. 직접 고치지 말고 원본 산출물을 고친 뒤 다시 생성한다.

## 요구사항 → 근거 → 설계 → 구현 → 시험 → 증거

| 요구사항 | 우선순위 | 적용성 | 상태 | 근거 | 설계(상태) | 구현 위치 | 통제 | 시험(상태) | 증거 |
|---|---|---|---|---|---|---|---|---|---|
| FR-101 POC-01 인증서 파일 기반 최소 신뢰정보 준비 | 필수 | 적용 | 초안 | SRC-REQ | DES-001(초안), DES-002(초안), DES-003(초안), DES-005(초안), DES-006(초안) | — | CTRL-001, CTRL-003 | TC-001(미실행), TC-002(미실행), TC-003(미실행), TC-005(미실행), TC-006(미실행), TC-025(미실행) | — |
| FR-102 POC-02 시험용 TrustList 생성·서명·제공 | 필수 | 적용 | 초안 | SRC-REQ | DES-007(초안), DES-008(초안) | — | — | TC-007(미실행), TC-008(미실행) | — |
| FR-103 POC-03 이용 검증기에서 TL 신뢰성 확인 | 필수 | 적용 | 초안 | SRC-REQ | DES-005(초안), DES-009(구현됨), DES-010(구현됨), DES-011(구현됨), DES-025(구현됨) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlAcceptance.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlReview.java | CTRL-002 | TC-005(미실행), TC-009(미실행), TC-010(미실행), TC-011(미실행), TC-101(통과), TC-106(통과), TC-107(통과) | EVD-101, EVD-102 |
| FR-104 POC-04 TL 등재 인증서로 가입자 인증 체인 검증 | 필수 | 적용 | 초안 | SRC-REQ | DES-015(구현됨), DES-016(구현됨) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java | CTRL-002 | TC-015(미실행), TC-016(미실행), TC-102(통과), TC-103(통과) | EVD-101 |
| FR-105 POC-05 전자서명 검증 흐름에서 TL 실제 이용 | 필수 | 적용 | 초안 | SRC-REQ | DES-013(초안), DES-014(초안), DES-015(구현됨), DES-017(구현됨), DES-020(구현됨) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java | CTRL-001, CTRL-004 | TC-013(미실행), TC-014(미실행), TC-015(미실행), TC-017(미실행), TC-020(미실행), TC-025(미실행), TC-102(통과), TC-106(통과), TC-107(통과) | EVD-101, EVD-102 |
| FR-106 POC-06 서비스 상태와 이력에 따른 판정 비교 | 필수 | 적용 | 초안 | SRC-REQ | DES-004(초안), DES-014(초안), DES-017(구현됨), DES-021(구현됨) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java | — | TC-004(미실행), TC-014(미실행), TC-017(미실행), TC-021(미실행), TC-102(통과), TC-104(통과) | EVD-101 |
| FR-107 POC-07 TL 갱신 전후 실제 적용 결과 비교 | 필수 | 적용 | 초안 | SRC-REQ | DES-011(구현됨), DES-019(구현됨), DES-020(구현됨), DES-021(구현됨), DES-026(구현됨) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlAcceptance.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlReview.java | CTRL-004 | TC-011(미실행), TC-019(미실행), TC-020(미실행), TC-021(미실행), TC-105(통과), TC-107(통과) | EVD-101, EVD-102 |
| FR-108 POC-08 사용할 수 없는 TL의 채택 차단 | 필수 | 적용 | 초안 | SRC-REQ | DES-009(구현됨), DES-010(구현됨), DES-012(초안), DES-022(초안) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlAcceptance.java | CTRL-002 | TC-009(미실행), TC-010(미실행), TC-012(미실행), TC-022(미실행), TC-101(통과) | EVD-101 |
| FR-109 POC-09 미등재·다른 공개키·잘못된 체인 구분 | 필수 | 적용 | 초안 | SRC-REQ | DES-001(초안), DES-002(초안), DES-013(초안), DES-016(구현됨), DES-022(초안) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java | CTRL-002 | TC-001(미실행), TC-002(미실행), TC-013(미실행), TC-016(미실행), TC-022(미실행), TC-103(통과) | EVD-101 |
| FR-110 POC-10 검증 근거 표시와 실증 재현 | 필수 | 적용 | 초안 | SRC-REQ | DES-006(초안), DES-008(초안), DES-017(구현됨), DES-018(구현됨), DES-019(구현됨), DES-023(초안), DES-024(초안), DES-025(구현됨), DES-026(구현됨), DES-027(구현됨) | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java, src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlReview.java | CTRL-003, CTRL-004 | TC-006(미실행), TC-008(미실행), TC-017(미실행), TC-018(미실행), TC-019(미실행), TC-023(미실행), TC-024(미실행), TC-105(통과), TC-106(통과), TC-107(통과) | EVD-101, EVD-102 |

## 자산·경계 → 위협 → 통제 → 시험

| 위협 | 위험도 | 처리 | 자산 | 경계 | 통제 | 시험(상태) |
|---|---|---|---|---|---|---|
| TH-001 악성 XML·경로·큰 입력으로 파일 노출 또는 처리 자원 고갈 | 높음 | 완화 | AST-001, AST-003 | TB-001 | CTRL-001 | TC-025(미실행) |
| TH-002 변조·비신뢰 TL 또는 이름만 같은 키를 신뢰정보로 채택 | 높음 | 완화 | AST-002 | TB-002 | CTRL-002 | TC-010(미실행), TC-012(미실행), TC-016(미실행) |
| TH-003 키 본문이 화면·로그·내보내기로 노출 | 높음 | 완화 | AST-001 | TB-003 | CTRL-003 | TC-005(미실행), TC-023(미실행) |
| TH-004 예상값·다른 조건·실행중 교체로 잘못된 증거와 비교 결론 생성 | 보통 | 완화 | AST-004 | TB-004 | CTRL-004 | TC-015(미실행), TC-018(미실행), TC-020(미실행), TC-024(미실행) |

## 적용 범위와 분모

| 구분 | 요구사항 수 |
|---|---|
| 적용성 적용 | 10 |
| 판정 대상 밖 | 0 |
| **필수 분모** (필수·적용·판정 대상 안·삭제 아님) | 10 |

## 연결률과 진척

연결률은 식별자가 이어졌는지만 센다. 내용이 충분한지는 확정 설계·실행·충족 행과 사람 검토로 본다.

| 지표 | 값 |
|---|---|
| 필수 → 설계 연결 | 10/10 (100%) |
| 필수 → 확정 설계 (DES 확정·구현됨) | 8/10 (80%) |
| 필수 → 시험 연결 | 10/10 (100%) |
| 필수 → 연결 시험 모두 실행 | 0/10 (0%) |
| 필수 → 충족 (유효 증거 있는 통과) | 0/10 (0%) |
| 보안 요구사항 → 통제 | 0/0 |
| 위협(완화) → 통제 | 4/4 (100%) |
| 통제 → 시험 | 4/4 (100%) |
