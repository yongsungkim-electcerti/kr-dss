# TL 관리시스템 구성

상태: 개발 기준 요약. 기능 구현 완료를 뜻하지 않는다.

| 위치 | 책임 |
|---|---|
| `src/common/kr-tl/kr-tl-model` | TL 데이터 구조·버전·서비스 현재 정보와 이력 |
| `src/common/kr-tl/kr-tl-builder` | XML 편성·서명에 재사용하는 기능 |
| `src/common/kr-tl/kr-tl-client` | 수신 계약·공통 수신 기능. 소비자 측 구현은 이번 관리시스템과 별도 |
| `src/poc/poc-kisa-tl` | 관리 화면·API, 초안 저장, 발행 조작, 발행본 보관·선택, 공개 제공 |

관리 화면 → 관리 API → 초안 저장 → 스냅숏 → XML 편성·키 파일 서명 → 발행본 보관 → 제공본 선택 → IF-07 GET 순서로 연결한다.

현재 관리 API는 인메모리 시연 구현이다. 기존 XML 서명기는 이용기관 모듈에 있으며, 공통 builder로 옮기는 작업은 후속 기능 개발이다. 이번 폴더 정리는 그 기능 이전을 수행하지 않는다.

기존 전체 시스템 구성은 [1장 보관본](../../../../deliverables/reports/trust-framework/1장-시스템구성-본문.md), 현재 인터페이스 명칭은 [IF 정정 명세](interfaces/IF-정정-명세.md)를 참조한다.
