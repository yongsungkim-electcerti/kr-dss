# TL 관리 데이터 저장

상태: 후속 구현 기준. 현재 저장 API가 구현된 것은 아니다.

| 데이터 | 예정 저장 위치 | 원칙 |
|---|---|---|
| RegistryDraft | `runtime/tl-management/registry/` | 편집·저장·복원 가능 |
| TrustListSnapshot | `runtime/tl-management/issued/<issuanceId>/` | 발행 시 고정. 이후 초안과 분리 |
| IssuedTrustList | `runtime/tl-management/issued/<issuanceId>/` | 서명 XML·해시·입력값 불변 보관 |
| 현재 제공본 | `runtime/tl-management/publication/` | 선택한 issuanceId를 가리키는 포인터를 원자적으로 전환 |
| PoC TL 서명키 | `runtime/tl-management/pki/` | 후속 생성. Git 비관리 |

정상 발행의 논리 식별자는 목록 ID·순번이고, 실제 보관 키는 issuanceId다. 동일 순번 다른 내용과 하위 순번 발행을 보관할 수 있어야 한다. 서비스 이력과 발행본 이력은 별개다.

상세 필드·상태 구간·인증서 갱신 규칙은 [공통 모델 26](../../common/kr-tl/26-EU-기준-이력-버전-모델.md)을 따른다. 오류 발행은 후보 복사본을 사용하여 정상 초안·이력을 훼손하지 않는다.

기존 서명 시연용 인증서 세트는 `runtime/pki/legacy/`로 이동했다. 이 세트는 새 PoC의 분리된 TL/사업자 인증체계를 구현한 결과가 아니다.
