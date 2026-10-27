# 표준·규격·결정 근거 목록

작성일: 2026-10-26. 요구사항의 근거는 원문 출처·판본·조항·적용 해석을 함께 관리한다. 원문 확보와 규격 적합성은 서로 다른 상태다. 기존 설계에서 인용한 판본을 기준으로 삼고 최신판으로 임의 변경하지 않았다. RFC 후속 업데이트·errata의 전체 적용성 검토는 완료하지 않았다.

## 원문 보관

- 자료 루트: `F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/`
- 다운로드 기록: 위 폴더의 `manifest.json`. 공식 URL·최종 URL·취득 UTC 시각·크기·SHA-256을 기록했다.
- Git에는 이 목록과 [source-register.json](source-register.json)만 남긴다. PDF·HTML·TXT·ZIP 및 작업용 추출문은 main 로컬에 둔다.
- 각 HTML은 원 서버 응답의 스냅숏이다. 외부 CSS·이미지·동적 조문까지 모두 포함한 오프라인 사이트로 간주하지 않는다.
- 구분: **기존 채택**은 설계가 명시한 근거, **보완**은 이번에 추가 확보한 해석 자료, **부분 확보**는 본문 확보 완료가 아니다.

## 공식 문서

| 근거키 | 문서·판본 | 관계·확인 수준 | 관련 조항 | 적용 범위·한계 | 공식 원문 | 로컬 원문 |
|---|---|---|---|---|---|---|
| STD-01 | ETSI TS 119 612 V2.4.1 (2025-08) | 기존 채택 기준 | §5.1.4, §5.3.1–3·12·14·15, §5.5·5.6·5.7, §6, Annex B·C·D·G | 형식·이력·서명·제공 규칙. 국내 법적 인정과 EU 적격 지위를 동일시하지 않음 | [공식 원문](https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/02.04.01_60/ts_119612v020401p.pdf) | [ETSI_TS_119_612_V2.4.1.pdf](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/ETSI_TS_119_612_V2.4.1.pdf) |
| STD-02 | ETSI TS 119 615 V1.4.1 (2026-05) | 보완 참고 | EU 국가 TL 사용·해석 절차; 상세 조항 채택은 후속 | 기존 자료실 판본과 해시 일치. 외부 소비자 참고이며 KR 수용 정책의 자동 근거 아님 | [공식 원문](https://www.etsi.org/deliver/etsi_ts/119600_119699/119615/01.04.01_60/ts_119615v010401p.pdf) | [ETSI_TS_119_615_V1.4.1.pdf](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/ETSI_TS_119_615_V1.4.1.pdf) |
| STD-03 | ETSI EN 319 132-1 V1.3.1 (2024-07) | 서명 보완 근거 | XAdES 구성·baseline; TS 119 612 B.1과 함께 적용 | 이번에 고정한 보완 조사 판본. 기존 구현의 완전 일치나 인증을 주장하지 않음 | [공식 원문](https://www.etsi.org/deliver/etsi_en/319100_319199/31913201/01.03.01_60/en_31913201v010301p.pdf) | [ETSI_EN_319_132-1_V1.3.1.pdf](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/ETSI_EN_319_132-1_V1.3.1.pdf) |
| STD-04 | W3C XML Signature 1.1 (2013-04-11) | 서명 기본 근거 | §3 처리, §4.3 SignedInfo·Reference | 참조 다이제스트와 서명값 검증. 적용 알고리즘은 별도 프로파일 | [공식 원문](https://www.w3.org/TR/2013/REC-xmldsig-core1-20130411/) | [W3C_XMLDSIG_1.1_20130411.html](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/W3C_XMLDSIG_1.1_20130411.html) |
| STD-05 | W3C Exclusive XML Canonicalization 1.0 (2002-07-18) | 정규화 근거 | §3 Specification | TS 119 612 B.1.0에서 사용하는 exclusive C14N의 정의 | [공식 원문](https://www.w3.org/TR/2002/REC-xml-exc-c14n-20020718/) | [W3C_Exclusive_C14N_20020718.html](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/W3C_Exclusive_C14N_20020718.html) |
| STD-06 | RFC 5280 (2008-05) | 인증서 보완 근거 | §4.1, §4.2.1.2·3·9·12, §6 | 인증서·확장·경로 검증 근거. TL 인정 상태나 국내 신뢰점 레벨을 정의하지 않음 | [공식 원문](https://www.rfc-editor.org/rfc/rfc5280.txt) | [RFC5280.txt](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/RFC5280.txt) |
| STD-07 | RFC 9110 (2022-06) | 기존 조회 계약 근거 | §8.8.3, §9.2.1, §13.1.2, §15.4.5 | ETag·조건부 GET·304. SHA-256 기반 태그 문자열은 프로젝트 선택 | [공식 원문](https://www.rfc-editor.org/rfc/rfc9110.txt) | [RFC9110.txt](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/RFC9110.txt) |
| STD-08 | RFC 9111 (2022-06) | 캐시 보완 근거 | §5.2.2.4 no-cache, §5.2.2.5 no-store, §5.2.2.6 no-transform | HTTP 캐시 정책. TL의 NextUpdate 및 신뢰 수용을 대체하지 않음 | [공식 원문](https://www.rfc-editor.org/rfc/rfc9111.txt) | [RFC9111.txt](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/RFC9111.txt) |
| CTX-01 | KISA 전자서명인증업무 운영기준 준수사실 인정제도 안내 | 제도 배경 | 제도 개요·평가·인정 절차 | HTML 본문 확인. 법령·고시 원문을 대신하지 않음 | [공식 원문](https://www.kisa.or.kr/1060203) | [KISA_accreditation.html](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/KISA_accreditation.html) |
| CTX-02 | 전자서명법 고정 이력 페이지: lsiSeq=236201 | 부분 확보 | 안내 URL의 2022-10-20 시행본; 현행 법령판본 확정 전 | HTML UI 저장, 조문 본문 완전성 미확인. 법적 의무 확정 근거로 사용 금지 | [공식 원문](https://www.law.go.kr/LSW/lsInfoP.do?efYd=20221020&lsiSeq=236201) | [electronic_signature_act_236201.html](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/electronic_signature_act_236201.html) |
| STD-09 | TS 119 612 V2.4.1 공식 스키마 첨부 ZIP | 규범 첨부 확보 | STD-01 Annex C | 원본 ZIP 유지. 전체 dependency resolution과 실제 XML 적합성 시험은 후속 | [공식 원문](https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/02.04.01_60/ts_119612v020401p0.zip) | [ETSI_TS_119_612_V2.4.1_schemas.zip](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/ETSI_TS_119_612_V2.4.1_schemas.zip) |
| CTX-03 | 전자서명인증업무 운영기준 공식 페이지 | 부분 확보 | 공식 표시: 2022-04-20 시행·고시 2022-5호 | 페이지·판본 표시는 확인, 첨부 본문 완전성 미확인. OQ-008에서 원문·시행본 확정 | [공식 원문](https://www.law.go.kr/LSW/admRulLsInfoP.do?admRulId=33463&efYd=0) | [electronic_signature_operation_rules.html](F:/kr-dss-works/kr-dss/runtime/reference-library/tl-management/2026-10-26/electronic_signature_operation_rules.html) |

## 사업·정책·설계 출처

| 근거키 | 출처 | 성격과 사용 원칙 |
|---|---|---|
| BIZ-01 | [제안요청서 본문](../../../project/제안요청서_RFP_본문.md) 과업 2·3 및 산출물 | 사업 요구. 자동 수집·주기 동기화와 현 PoC 범위의 차이는 OQ-002 |
| POL-01 | [사용자 PoC 결정 기록](../../../development/poc/tl-management/reviews/25-PoC-구현-차이-검토.md) §3 D-01~06 및 오류 발행 추가 결정 | 기존 사용자 결정. 새 요구사항 표의 검토 승인을 대신하지 않음 |
| POL-02 | 이 대화 2026-10-26: 비관리 자료 main 보관, 요구사항 재구성, 기존 설계 백업 | 현재 정리 지시. 원문과 개인키를 Git에 추가하지 않음 |
| LEG-개념 | main 기존 백업 `2026-10-26-tl-management/archive/docs/design/poc/tl-management/sources/2026-10-08-concept-v02/concept-design.md` | 목표 운영 설계. §2.2·2.3·4.2·4.4·5.3; 공식 표준 아님 |
| LEG-구조 | 신규 백업 originals 아래 `docs/design/common/kr-tl/10-TL-구조.md` | 국내 적용 구조·언어·TSLType의 과거 선택 |
| LEG-모델 | 신규 백업 originals 아래 `docs/design/common/kr-tl/26-EU-기준-이력-버전-모델.md` | 모델·이력·시점 조회·정상/시험 차이 |
| LEG-판정 | 신규 백업 originals 아래 `docs/design/common/kr-tl/18-신뢰경로-판정표.md` | 외부 판정 정책. PDF 전체 검증과 구분 |
| LEG-프로파일 | 신규 백업 originals 아래 `docs/design/poc/tl-management/profile-poc-v1.md` | 임시 URI·확장·서비스 상태 전이 |
| LEG-복구 | 신규 백업 originals 아래 `docs/design/poc/tl-management/issuance-recovery.md` | 저장·동시성·요청 중복·확정·복구 계약 |
| LEG-조회 | 신규 백업 originals 아래 `docs/design/poc/tl-management/interfaces/28-IF07-TL-조회-API.md` | HTTP 계약·오류 처리·소비자 책임 |
| LEG-적용 | 신규 백업 originals 아래 `docs/design/poc/tl-management/concept-v02-application.md` | 운영 목표를 PoC로 한정한 선택 |
| LEG-키 | 신규 백업 originals 아래 `docs/design/poc/tl-management/pki/27-PoC-키파일-디렉터리-설계.md` | TL·사업자 인증체계·파일 키 구성. 오래된 복사 절차는 POL-02가 우선 |
| LEG-화면 | 신규 백업 originals 아래 `docs/design/poc/tl-management/screens/02-관리-화면.md`; 개발 기록 37 | 관리 기능 식별의 자료. 기능 존재를 규범 요구나 검증 통과로 승계하지 않음 |
| LEG-요구 | 신규 백업 originals 아래 `docs/design/poc/tl-management/requirements/23-요구사항-추적-용어집.md` | 과거 RFP 해석. 운영·PoC 혼재 및 중복 RQ-산출 ID를 신규 ID로 분리 |

신규 백업의 절대 경로와 전체 파일 목록은 [archive.md](archive.md)에 있다. LEG는 결정 경위를 확인하는 보관 자료이며, 표준 문구의 진위는 STD 원문으로 확인한다.

## 표준과 프로젝트 선택의 구분

| 주제 | 원문에서 확인한 규칙 | 프로젝트 적용·예외 | 관련 요구사항 |
|---|---|---|---|
| 목록 유형 | STD-01 §5.3.3·D.6은 비EU CClist 또는 별도 정의 URI를 허용 | KRlist는 CC=KR의 적용값. 전체 URI·territory·스킴 의미를 함께 정의 | FR-022 |
| 형식·순번 | STD-01 §5.3.1·2는 형식 버전과 발행 순번을 구분 | 형식 6, 정상 기준 증가와 TRIAL 역행 시험은 분리 | FR-018, FR-019, FR-033 |
| 상태·신뢰점 | 표준 서비스·상태 모델과 디지털 ID 구조 | 국내 인정/정지/철회 전이, CA 0/1은 국내 PoC 선택. 법령의 직접 명령으로 표시 금지 | FR-006, FR-008, FR-023 |
| 서비스 갱신 | STD-01 §5.5.3·G.4의 키/Subject 기준 | 관리 serviceId는 내부 식별자이며 XML 표준 필드라고 주장하지 않음 | FR-012, FR-013 |
| 서명 | STD-01 B.1.0의 루트 참조·변환·정규화 제한 | 파일 키와 단일 WAS는 구현 선택. 실제 HSM 의무는 이 조항으로 도출하지 않음 | SR-001, FR-053 |
| 해시 조회 | STD-01 §6.1은 SHA-256 보조 해시·주소와 인증 대체 금지를 정의 | raw 32바이트 응답과 SHA-256 ETag 문자열은 기존 프로젝트 계약 | FR-039, FR-040 |
| 언어·스키마 | STD-01 §5.1.4·Annex C | 한글 전용 표시나 일부 필드 변환만으로 전체 적합성을 선언할 수 없음 | FR-025, FR-026 |
| 복구·원자성 | 표준이 프로젝트 state.json 배치를 정하지 않음 | 요청 ID·확정 지점·프로세스 잠금은 기존 파일 저장 설계 선택 | FR-027, FR-030, FR-032, SR-008 |

## 원문 조사에서 남은 사항

전자서명법·운영기준은 공식 페이지를 확보했지만 조문·별표 원본 전체와 채택 시행본을 아직 확정하지 않았다. 국내 인정 상태 코드와 철회/정지 전이를 법령 규칙으로 단정하지 않는다. 상호인정·EU 법적 효력·운영 적합성 인증은 이번 TL 관리 PoC의 자동 요구로 추가하지 않는다. OQ-005·OQ-008에서 적용 범위를 확정한다.
