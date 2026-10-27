# 기능별 설계 계약

| ID | 설계 항목 | 종류 | 관련 요구사항 | 정상 조건 | 실패 조건 | 권한 조건 | 상태 | 구현 위치 |
|---|---|---|---|---|---|---|---|---|
| DES-001 | 인증서 파일 읽기 | 화면 동작 | FR-101, FR-109 | 용도(TL 앵커/서명 체인/서비스 신뢰점/중간 인증서/가입자), PEM·DER 파일 → 공개 인증서를 파싱하고 파일 해시로 식별한다. 같은 이름의 다른 공개키는 다른 자료로 유지한다. → 역할·주체·발급자·SPKI 식별값을 표시 | CERT_PARSE_ERROR: 자료를 등록하지 않고 파일별 오류 표시 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-002 | 인증서 상세 확인 | 화면 동작 | FR-101, FR-109 | 등록된 인증서 ID → Subject·Issuer·유효기간·CA 여부·공개키 지문을 읽기 전용 표시한다. 서비스명과 인증서 식별을 구분한다. → 선택 인증서의 속성·용도 확인 | CERT_NOT_FOUND: 선택 해제 후 목록 새로고침 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-003 | 최소 서비스 정보 설정 | 화면 동작 | FR-101 | 시험 사업자 식별자·명칭, 서비스명, CA/RootCA 인증서 ID → 사업자 업무 메뉴 없이 한 행에서 서비스와 공개 인증서를 연결한다. PoC 기본 유형은 CA이며 신뢰점 종류는 표본 프로파일에 따른다. → 서비스 ID와 연결 인증서 저장 | SERVICE_CERT_REQUIRED: 연결되지 않은 서비스는 발행 대상에서 제외 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-004 | 상태·효력시각 설정 | 화면 동작 | FR-106 | 서비스 ID, 인정/정지/복원/철회, UTC 효력시각 → 시간순 구간을 추가한다. 복원은 새 인정 구간이며 과거 정지 구간을 보존한다. 동일 효력시각 중복은 거부한다. → 미발행 초안의 구간 미리보기 | STATE_TIME_CONFLICT: 저장 차단. 과거 발행본은 수정하지 않음 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-005 | 서명 프로파일·앵커 선택 | 화면 동작 | FR-101, FR-103 | 서명키 별칭, TL 서명 체인 ID, 별도 TL 앵커 ID → 서버에 구성된 키 별칭만 선택한다. 개인키 본문·암호를 브라우저로 보내지 않는다. 앵커는 TL 후보 파일에서 자동 신뢰하지 않는다. → TL 서명 설정과 검증앵커가 별도 표시 | SIGNER_PROFILE_MISSING: 서명 비활성, 앵커 미지정 시 TL 채택 비활성 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-006 | 시험 자료 묶음 저장 | 화면 동작 | FR-101, FR-110 | 인증서·서비스·상태 설정 → datasetId·revision과 입력 파일 해시를 저장한다. 저장 후 변경은 새 revision으로 구분한다. 원본 파일은 main 자료 영역에 둔다. → 다음 화면에서 자료 묶음 선택 가능 | DATASET_INCOMPLETE: 누락 필드를 표시하고 저장 차단 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-007 | TL 생성·서명 | 화면 동작 | FR-102 | datasetId/revision, 순번, 발행 UTC, NextUpdate, 서명 별칭 → 입력을 고정한 뒤 XML 생성·서명한다. 정상 발행은 NextUpdate>발행시각이며 순번은 직전 정상본보다 크다. 잘못된 시험 TL은 별도 파일로 가져온다. → 새 issuanceId·해시·서명본 생성. 검증기 적용은 별도 | SIGNING_FAILED: 발행 성공 표시·자동 적용 금지. 재시도는 새 작업 여부 확인 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-008 | 발행본 조회·내려받기 | 화면 동작 | FR-102, FR-110 | issuanceId → 일련번호·시각·서비스 수·파일 해시로 발행본을 구별한다. 서버 보관 원문 바이트를 내려받는다. → 발행본 파일 확보·재사용 | ISSUANCE_NOT_FOUND: 내려받기 차단, 기존 발행본 보존 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-009 | TL 후보 파일 선택 | 화면 동작 | FR-103, FR-108 | 발행본 ID 또는 TL XML 파일 → 받은 파일을 후보 상태로 저장한다. 정상 발행본·시험 오류 파일을 구분하며 후보 선택만으로 적용 TL을 바꾸지 않는다. → 후보 파일명·해시·번호(파싱 가능 시) 표시 | TL_PARSE_ERROR: 거부 후보로 기록, 적용본 유지 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlAcceptance.java |
| DES-010 | TL 채택 조건 검사 | 화면 동작 | FR-103, FR-108 | 후보 ID, 별도 앵커, TL 수신 검사 시각 → XML 서명·서명 체인·NextUpdate·순번을 검사한다. 같은 번호+같은 해시는 변경 없음, 같은 번호+다른 해시는 충돌, 낮은 번호는 회귀로 거부한다. → 단계별 결과·채택 가능/거부 사유 표시 | TL_SIGNATURE_INVALID / TL_CHAIN_UNTRUSTED / TL_EXPIRED / TL_ROLLBACK / TL_CONFLICT | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlAcceptance.java |
| DES-011 | 검증기 TL 적용 | 화면 동작 | FR-103, FR-107 | 검사 통과 후보 ID → 서명본 해시와 검사 설정이 같은지 재확인한 뒤 서비스 색인과 적용본을 함께 교체한다. 다운로드·검사 완료와 실제 적용 완료를 구별한다. → 적용 TL 번호·해시 갱신. 다음 실행부터 적용 | CANDIDATE_CHANGED / ADOPT_FAILED: 이전 적용본 유지 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlAcceptance.java |
| DES-012 | 거부·만료 상태 처리 | 화면 동작 | FR-108 | 거부 후보·기존 적용본 → 새 후보 거부 시 유효한 기존 적용본을 유지한다. 기존본도 만료면 적용 사용 불가로 표시한다. 검사 시각과 상태 평가 시각을 혼용하지 않는다. → 유효본 유지 또는 NO_VALID_TL 표시 | TL 없음: TL 신뢰 판정 보류. 독립 서명값 검증 결과는 유지 가능 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-013 | 서명 표본 선택 | 화면 동작 | FR-105, FR-109 | 지원 포맷의 서명 문서/데이터, 필요 시 원문·중간 인증서 → 기존 검증 모듈의 단일 포맷을 사용한다. 파일 해시로 표본을 고정하고 중간 인증서를 신뢰앵커로 승격하지 않는다. → 파일명·해시·서명자 정보 표시 | FORMAT_UNSUPPORTED / SIGNATURE_INPUT_MISSING: 실행 차단 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-014 | 정책·평가시각 지정 | 화면 동작 | FR-105, FR-106 | 정책 ID/버전, 평가 UTC, 시각 출처(시험 지정/증거 추출) → 시험 지정 시각은 모의 입력으로 항상 표시한다. TL 수신 검사 시각은 별도 보관하며 과거 평가를 위해 만료 TL을 현재 수용하지 않는다. → 실행 조건이 명시적으로 고정됨 | TIME_REQUIRED / TIME_SOURCE_UNVERIFIED: 상태 판정 보류·근거 표시 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-015 | 실제 검증 실행 | 화면 동작 | FR-104, FR-105 | 표본·정책·시각, 적용 TL 스냅숏 → runId를 만들고 TL 번호·해시·색인을 실행 시작 시 고정한다. 기존 검증 모듈의 서명값 검사와 TL 기반 체인·상태 판단을 실행한다. 실행 중 TL 교체는 현재 run에 영향 없음. → 실제 검사 단계와 runId 반환 | VERIFY_TIMEOUT / VERIFY_ERROR: 실행 오류. 정상·실패 판정으로 위장하지 않음 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java |
| DES-016 | 인증 경로·신뢰점 표시 | 화면 동작 | FR-104, FR-109 | 실행 결과의 인증 체인·TL 서비스 매칭 → 가입자→중간 CA→TL 등재 신뢰점 경로를 표시한다. 이름만 같은 다른 키는 매칭하지 않는다. 단순 미등재와 인증서 서명 관계 오류를 구분한다. → 인증서 지문·선택 서비스·경로 확인 | NOT_IN_TL=근거 부족, CHAIN_INVALID=체인 오류. 정지 CA를 상위 Root로 우회하지 않음 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java |
| DES-017 | 결과 분리 표시 | 화면 동작 | FR-105, FR-106, FR-110 | TL 채택, 암호학적 검사, 상태·시점 판단 결과 → 서명값/문서 무결성, 인증 체인, TL 신뢰 판단을 별도 행으로 표시한다. 폐지·시각 증거 미검증은 미검증으로 보이고 전체 전자서명 유효라는 단일 녹색 배지를 만들지 않는다. → 근거·사유·미검증 항목 확인 | 검사 누락을 통과로 바꾸지 않음. 실행 오류와 조건 불충족 분리 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocPdfVerifier.java |
| DES-018 | 실행 결과 보존 | 화면 동작 | FR-110 | 완료 또는 오류 run → 표본·정책·시각·TL·프로그램 버전과 실제 결과·로그 참조를 고정해 저장한다. 예상 결과는 별도 필드이며 비교표 편집으로 실제 결과를 수정하지 않는다. → 변경 불가 run 기록이 결과 비교 목록에 추가 | RESULT_SAVE_FAILED: 보존 실패 표시·내보내기 불가, 재저장 시 같은 run 사용 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java |
| DES-019 | 비교 실행 선택 | 화면 동작 | FR-107, FR-110 | 기준 runId, 비교 runId → 저장된 두 실행을 선택한다. 파일명 대신 입력 해시·정책 버전·평가시각·프로그램 버전을 요약한다. → 선택한 두 실행의 조건 나란히 표시 | RUN_NOT_FOUND: 비교 불가 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java |
| DES-020 | 비교 조건 확인 | 화면 동작 | FR-105, FR-107 | 두 실행의 조건·비교 목적 → TL 변경 효과 비교는 입력·정책·평가시각·검증 모듈 버전이 같아야 한다. 다르면 차이를 표시하고 TL 단독 효과라는 결론을 막는다. 체인 대조 모드는 다른 표본임을 표시한다. → 비교 가능 또는 조건 불일치 표기 | COMPARISON_MISMATCH: 나란히 조회는 가능, TL 효과 비교 판정은 보류 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java |
| DES-021 | TL 변경 전후 비교 | 화면 동작 | FR-106, FR-107 | 조건이 일치한 두 run → 등재 여부·적용 TL·상태 구간·선택 신뢰점·판정 사유의 차이를 강조한다. 과거 정지 구간을 복원본에서도 유지하는지 확인한다. → 결과가 달라진 근거 또는 동일한 이유 표시 | 차이가 없다는 이유로 실증 실패로 자동 판정하지 않음 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/PocRunStore.java |
| DES-022 | 예외·대조 결과 조회 | 화면 동작 | FR-108, FR-109 | TL 거부 이벤트 또는 대조 run → 변조·비신뢰·구버전·만료 TL의 채택 결과와 정상/미등재/다른키/서명관계오류 체인을 구분해 조회한다. 기존 유효본 사용 여부도 함께 표시한다. → 거부 후보와 실제 적용본, 각 판정 사유를 구분 | 참고 시나리오의 예상값은 실제 결과로 복사하지 않음 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-023 | 증거 묶음 내보내기 | 화면 동작 | FR-110 | 선택 run·비교 목적·검토 메모 → 입력 해시 목록·실제 결과·설정 요약·로그 참조를 내보낸다. 시험키·암호·개인키 내용은 포함하지 않는다. 서명 문서 포함은 별도 선택 사항이다. → D4 실증 결과서에 첨부 가능한 결과 묶음 | EXPORT_FAILED: 산출 완료로 표시하지 않음; SECRET_CONTENT: 포함 차단 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |
| DES-024 | 동일 조건 재실행 | 화면 동작 | FR-110 | 과거 run의 입력·정책·시각·TL 참조 → 필요 파일 존재·해시 일치를 확인하고 새로운 runId로 재실행한다. 저장된 과거 결과를 다시 보여주는 것과 새 실행을 구별한다. → 새 run과 원 run 연결 | REPLAY_INPUT_CHANGED: 변경 항목 표시, 같은 조건 재현이라고 표시하지 않음 | 단일 로컬 실증 사용자. 키·자료 접근은 서버 설정 범위로 제한. 계정·역할 관리 제외 | 초안 | — |

## 내부 처리 경계와 데이터 계약

아래는 새 HTTP API 확정안이 아닌 논리 명령 계약이다. 기존 모듈의 호출 형태는 구현 준비에서 확인한다. UI만으로 신뢰·경로·키 정책을 강제하지 않는다.

| 명령 | 입력 | 출력 | 상태 변화·실패 |
|---|---|---|---|---|
| prepareDataset | 인증서 바이트, 역할, 최소 서비스 정보, 상태 구간 | datasetId, revision, certId, hash | 파싱 성공 항목만 초안 반영; 실패는 항목별 오류 |
| issueTL | datasetId/revision, sequence, issueAt, nextUpdate, signingProfileAlias | issuanceId, tlHash, signedFileRef | 서명 성공 뒤 발행본 보관. 적용본은 변경하지 않음 |
| inspectCandidate | candidateRef, trustedAnchorRef, intakeClock | signature/chain/freshness/sequence 검사와 decision | 읽기·검사만 수행, 자동 채택 없음 |
| adoptTL | candidateId, expectedHash, 검사 설정 버전 | activeTL 번호·hash·indexVersion | 최신성 재확인 후 파일과 색인을 함께 교체; 실패는 이전본 유지 |
| verify | sampleRef/hash, policyId/version, evaluationTime/source, activeTL snapshot | runId, signatureResult, chainResult, tlTrustResult, assumptions | 시작 시 모든 입력 고정. 실패 시 실행 오류 기록 |
| compare | runA, runB, purpose | 조건 차이, TL·상태·결과 차이 | 결과를 변경하지 않음; 조건 불일치는 비교 제한 |
| export / replay | runIds 및 증거 선택 / 원 run 참조 | 증거 묶음 / 새 runId | 비밀정보 제외; 파일 누락·hash 변경이면 재현 차단 |

인증서와 서명 파일은 파일 참조+해시로 연결하며 사용자 입력 경로를 그대로 서버 파일 경로로 사용하지 않는다. 파일·실행 크기와 제한시간은 OQ-002의 구현 전 결정 사항이다. 수치 미확정을 무제한 허용으로 구현하지 않는다.

## 상태와 시각

- 서비스 초안: 미저장 → 저장된 revision → 발행 스냅숏. 발행본은 불변이다.
- TL: 파일 후보 → 검사 중 → 채택 가능/거부 → 적용. 발행본 존재와 검증기 적용을 구별한다.
- TL 검사시각은 수신본의 사용 가능성을 판단한다. 서비스 평가시각은 인정 상태 구간을 선택한다. 시험 지정 시각은 모의 입력으로 표시한다.
- 실행: 미실행 → 실행 중 → 결과 보존/실행 오류. 결과 내의 통과·실패·보류는 실행 상태와 별개다.
- 비교: 두 실행 선택 → 조건 확인 → 비교 가능/조건 불일치. 고정 입력 해시·정책 버전·평가시각·검증 모듈 버전이 같을 때 TL 단독 효과를 설명한다.

## 결과 구조

runId, sampleHash, policyId/version, evaluationTime/source, intakeClock, TL sequence/hash/indexVersion, certificateChain/fingerprints, matchedServiceId, selectedState/interval, signatureResult, chainResult, tlTrustResult/reason, uncheckedItems, engineVersion, actualOutputRef를 보관한다. expectedResult·reviewNote는 별도다.

TL 결과: 신뢰 확인 / 신뢰 조건 불충족 / 판단 보류. 서명값 결과: 유효 / 무효 / 미검증 / 처리 오류. 체인 결과: 연결 확인 / 서명 관계 오류 / 근거 부족 / 미검증. 최종 전체 전자서명 유효성 단정은 이 PoC 설계에 포함하지 않는다.

## 2026-10-27 구현 범위 구체화

사용자 승인에 따라 FUN-011, 015~021을 순차 구현했다. 단일 detached PDF·PoC v1 CA 서비스·시험 지정 UTC를 사용한다. 발행본 선택과 후보 영구 보관은 기존 관리 시스템 책임이며 실증 화면에서는 파일 검사와 채택을 한 요청으로 수행한다. 적용본과 서비스 목록은 하나의 불변 스냅숏으로 전달한다.

UI-01 기존 대시보드·사업자 상세는 유지한다. 예시 화면 제공은 허용되지만 TL 적용·검증·비교 결과는 실제 API 값만 표시한다. FUN-004의 미발행 중간 상태를 모두 저장하는 변경은 하지 않았으며, 시연에서는 상태별 발행으로 이력을 보존한다.

구체적인 제한·경계·차수별 구현 및 시험은 [개발 상태표](../../../../development/poc/tl-management/implementation-stages.md)와 [실행 안내](../../../../guides/poc/tl-management/tl-verification-poc.md)에 기록한다.

## TL 탐색 변경 계약 v0.4

DES-009~011은 [변경 정의](tl-explorer-definition.md)의 후보 메모리·검사/적용 분리·기준 변경 거부 계약을 따른다. 개별 검사 단계 전체 목록 대신 종합 검사 결과와 최초 거부 사유를 표시한다.

| ID | 설계 항목 | 종류 | 관련 요구사항 | 정상 조건 | 실패 조건 | 권한 조건 | 상태 | 구현 위치 |
|---|---|---|---|---|---|---|---|---|
| DES-025 | TL 구조와 실제 이력 | 화면 동작 | FR-103, FR-110 | 파싱한 운영기관·서비스·인증서·실제 이력만 표시 | 파싱 실패는 구조를 표시하지 않음; 이력 없음을 명시 | 단일 loopback 실증 사용자, 서버 경로 선택 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlReview.java |
| DES-026 | TL 의미상 변경 비교 | 화면 동작 | FR-107, FR-110 | 현재/후보 또는 저장한 두 실행 TL을 ID·공개키로 비교 | 중복 식별은 모호함; 저장 TL 해시 불일치 시 비교 거부 | 단일 loopback 실증 사용자, 서버 경로 선택 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlReview.java |
| DES-027 | XML 근거와 원문 | 화면 동작 | FR-110 | 필드별 XML 표시, 원문 바이트 다운로드 | 후보 교체·원문 부재는 내려받기 거부; XML은 텍스트로만 표시 | 단일 loopback 실증 사용자, 서버 경로 선택 | 구현됨 | src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/tlpoc/TlReview.java |
