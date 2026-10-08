# TL 관리 PoC 설계

기준일: 2026-10-08. 이번 PoC의 담당 영역은 TL 관리시스템이다. 사업자·서비스 정보 관리, 이력 보존, TL 생성·키 파일 서명·보관·제공을 구현한다.

## 작업 진입점

- [확정 범위](scope.md)
- [시스템 구성과 책임](architecture.md)
- [초안·스냅숏·발행본 저장](data-storage.md)
- [발행·제공 절차](publication.md)
- [공통 TL 모델](../../common/kr-tl/README.md)
- [개발 절차와 진행상태](../../../development/poc/tl-management/README.md)
- [관리시스템 소스](../../../../src/poc/poc-kisa-tl)

## 기준 적용 순서

1. 사용자가 확정한 [PoC 범위](scope.md).
2. [구현 차이 검토 25](../../../development/poc/tl-management/reviews/25-PoC-구현-차이-검토.md)의 D-01~06.
3. [이력·버전 모델 26](../../common/kr-tl/26-EU-기준-이력-버전-모델.md), [키 구성 27](pki/27-PoC-키파일-디렉터리-설계.md), [IF-07 계약 28](interfaces/28-IF07-TL-조회-API.md).
4. 이전 화면·시나리오·인프라 자료는 위 기준과 충돌하지 않는 범위에서 참조한다. HSM·별도 WEB·심사 업무·R1~R6을 자동으로 필수 개발에 포함하지 않는다.

`background/`는 목표 시스템의 기존 설계다. `screens/`, `scenarios/`, `data/`의 번호 문서도 작성 당시의 상세안이며 현재 PoC 범위와 구별한다. 기존 번호는 문서 간 추적을 위해 유지했다.

통합 보고서와 장별 보관본은 [보고 산출물](../../../../deliverables/reports/trust-framework/README.md)에 있다. 로컬 보관본과 원격 Google Docs의 최신 편집 상태는 같다고 가정하지 않는다.
