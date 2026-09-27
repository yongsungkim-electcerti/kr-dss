# 디지털 신뢰체계 상호운용성 산출물 초안 v0.1

작성일: 2026-09-27. 상태: 기관 협의용, 미승인·미실행.

## 문서 5종

- D01 [서비스별_상호운용성_검토서](01_서비스별_상호운용성_검토서.md) · [Google Docs](https://docs.google.com/document/d/1RQfGhqPi48hudhf8PuMjbrflH2xCdzmWS5oEAaMQJR8/edit)
- D02 [기관간_책임_데이터_협약](02_기관간_책임_데이터_협약.md) · [Google Docs](https://docs.google.com/document/d/1sgot2mWpkSL5GXLsVvLeSFyWmW-cztIlFh1sR51gnig/edit)
- D03 [공통용어_상태코드](03_공통용어_상태코드.md) · [Google Docs](https://docs.google.com/document/d/1kjd4g3nXT9Qn3zRO6v07WFUC_22lFhDiJyLU4aJeWYw/edit)
- D04 [재사용_신뢰검증_구성요소](04_재사용_신뢰검증_구성요소.md) · [Google Docs](https://docs.google.com/document/d/1YWwZCeO351BthBa8MXkW7bQbhQrvnIGEAzCVg1e5KPk/edit)
- D05 [공동_적합성_시험세트](05_공동_적합성_시험세트.md) · [Google Docs](https://docs.google.com/document/d/1hVx42KYrrWsBphsS9lXQqF31T9MBSc0-jSJchBd8fcM/edit)

## 함께 사용하는 방법

D01에서 서비스 목적·기관·프로파일과 R01~R12를 정하고 D02에서 역할·데이터·운영 조건을 합의한다. D03의 용어·코드를 기준으로 D04의 구성요소를 구현한 뒤 D05의 기대 결과로 공동 시험한다. D03은 의미·집계의 기준 문서이며, 개별 서비스의 승인 정책을 대체하지 않는다.

기준 사례는 SVC-01 기관 간 서명문서, SVC-02 공공 속성의 민간 제출, SVC-03 민간 재직증명의 행정 제출이다. 실제 참여기관이나 기존 SDK의 기능 지원을 확인한 결과는 아니다.

## 부속 자료

- [상태코드.json](상태코드.json): 연구용 코드 23개.
- [시험사례.json](시험사례.json): 기술 32개·운영/문서 6개, 모두 NOT_RUN.
- [시험사례.csv](시험사례.csv): 기술 사례와 기대 결과를 표로 검토하기 위한 사본.
- [초안 정합성 검사](초안_정합성검사.json): 코드 참조·기술 결과 집계·R01~R12 연결 확인. 구현 적합성 시험 아님.
- [Google Docs 내용 대조](GoogleDocs_검증.json): 본문·표 누락 여부와 문서 구조 확인.

## 기관 협의가 필요한 항목

O01 실제 기관·업무 근거 — 서비스 소유기관. O02 형식·프로토콜·알고리즘 버전 — 기술협의체. O03 갱신·캐시·시계 오차 — 운영·수신기관. O04 보존기간·처리 근거·제공/위탁·국외 처리 — 데이터 책임자·법무. O05 SLA·연락망·비용·책임·종료 — 기관 대표. O06 구현 조합·성능·시험 환경 — 공동 시험 담당.

협약의 기관명·효력일·서명권자는 의도적으로 미기입했다. 위 항목을 해소하지 않은 채 운영 협약으로 사용하지 않는다. 시험용 300초·120초·30초는 운영 SLA가 아니다. 실행 가능한 암호 fixture·테스트 러너·배포 코드와 국제 인증 성적서는 이번 초안에 포함되지 않는다.

## 근거와 변경 관리

앞서 수집한 EIF, ETSI, EUDI ARF, OpenID, 일본 VC·DIW, NIST 등에서 검토 구조를 가져오고 국내 연구용 요구·코드·시험 ID를 새로 제안했다. 문서별 공식 자료 링크와 법령 검토 위치를 기재했다. AI 보조로 작성한 초안이며 기관 합의·법무 검토·독립 시험을 거친 최종본이 아니다.

Google Docs와 Markdown은 작성 시점에 내용 대조를 완료한 별도 사본이다. 이후 어느 쪽을 수정하든 버전·수정자·변경 이유를 기록하고 다른 사본에도 반영한다. 저장소 반영 이력은 Git 커밋과 PR에서 확인한다.
