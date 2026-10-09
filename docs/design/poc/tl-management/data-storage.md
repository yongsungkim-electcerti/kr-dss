# TL 관리 데이터 저장

상태: 후속 구현 기준. 현재 저장 API가 구현된 것은 아니다.

2026-10-09: [발행·저장 트랜잭션과 복구](issuance-recovery.md)에서 실제 파일 배치·권위·동시성·중단 복구를 구체화했다.

| 데이터 | 예정 저장 위치 | 원칙 |
|---|---|---|
| RegistryDraft | `runtime/tl-management/registry/revisions/` | 저장마다 불변 revision 생성, 활성 revision은 state.json |
| TrustListSnapshot | `runtime/tl-management/issued/<issuanceId>/` | 발행 시 고정. 이후 초안과 분리 |
| IssuedTrustList | `runtime/tl-management/issued/<issuanceId>/` | 서명 XML·해시·입력값 불변 보관 |
| 확정 상태·현재 제공본 | `runtime/tl-management/publication/state.json` | 초안·정상 기준·요청 매핑·발행 확정 목록·제공 포인터를 하나의 상태 파일로 관리 |
| 발행 작업 중 | `runtime/tl-management/staging/` | 외부 제공 금지, 중단 시 미확정으로 보존 |
| 요청 진행·실패 | `runtime/tl-management/requests/` | 진단 자료. 성공의 최종 권위는 state.json |
| PoC TL 서명키 | `runtime/tl-management/pki/` | 후속 생성. Git 비관리 |

정상 발행의 논리 식별자는 목록 ID·순번이고, 실제 보관 키는 issuanceId다. 동일 순번 다른 내용과 하위 순번 발행을 보관할 수 있어야 한다. 서비스 이력과 발행본 이력은 별개다.

issued 폴더의 존재만으로 발행 확정을 판단하지 않는다. state.json의 committed 목록과 파일 무결성을 함께 확인한다. 단일 작성자 잠금 아래 불변 파일을 먼저 저장하고 state를 원자 교체한다. BASELINE 성공 시에만 정상 이력과 활성 초안을 함께 전진시키고, TRIAL은 이를 변경하지 않는다. 서버 재시작은 진행 중 요청을 복구·종결한 뒤 관리 쓰기를 허용한다.

상세 필드·상태 구간·인증서 갱신 규칙은 [공통 모델 26](../../common/kr-tl/26-EU-기준-이력-버전-모델.md)을 따른다. 오류 발행은 후보 복사본을 사용하여 정상 초안·이력을 훼손하지 않는다.

기존 서명 시연용 인증서 세트는 `runtime/pki/legacy/`로 이동했다. 이 세트는 새 PoC의 분리된 TL/사업자 인증체계를 구현한 결과가 아니다.
