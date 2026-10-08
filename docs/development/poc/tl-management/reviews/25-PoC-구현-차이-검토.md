# PoC 신뢰체계 관리 시스템 — 기존 구현과 최신 설계 비교

- 검토일: 2026-10-07
- 코드·설계 기준: `59b6cd9960bea05d1aa60e79fcbe10033466c79a` (`main`)
- 작업 브랜치: `feat/codex-poc-trust-gap-review`
- 성격: 개발 착수용 정적 검토 및 사용자 PoC 범위 결정 반영. 아래 검증 조건은 기술 검증 제안이며, 최종 PoC 시나리오와 완료 기준은 향후 결정한다.

## 1. 검토 결과

현재 관리 시스템은 운영 화면과 인메모리 API를 제공한다. 실제 접수·심사·확정·영속 저장·TL 서명·배포를 잇는 관리 파이프라인은 확인되지 않았다. `POST /api/admin/publish`는 번호와 시각만 바꾸고 `서명 유효`를 반환한다. 화면의 성공 문구를 실제 발행·서명·배포 성공의 증거로 사용할 수 없다.

사용자 결정에 따라 이번 PoC는 **신뢰체계 개념 증명**을 최우선으로 한다. 인증·감사는 구현하지 않고, 접수·심사·보완·반려는 메뉴만 제공한다. TL은 키 파일로 실제 서명하고 Spring Boot WAS에서 제공한다. 따라서 위 관리 절차의 미구현 전체가 이번 개발의 결함이나 필수 작업을 뜻하지 않는다.

재사용 가능한 자산은 관리 화면 틀, TL 기본 모델·XML 변환기, 이용기관에 있는 XAdES 서명기와 PDF 검증 경로다. 이를 최신 설계의 관리·발행·배포·수신 책임에 맞게 연결해야 한다. 시점별 인정 상태 판정은 현재 상태 비교를 확장하는 작업이 필요하다.

이번 검토에서는 제품 코드를 수정하지 않았다. Google Docs는 다시 조회하지 않았으며, 저장소에 보관된 최신 설계를 기준으로 삼았다. 원격 문서와의 최신성 대조는 별도 확인사항이다.

## 2. 기준 문서와 적용 순서

1. [최신 인계](../handoff.md)의 PoC 구현 비교 지시
2. [최신 1장](../../../../../deliverables/reports/trust-framework/1장-시스템구성-본문.md)과 [통합 설계서](../../../../../deliverables/reports/trust-framework/KR-TL-시스템-및-신뢰체계-설계서.md): 관리 절차, 구성요소, IF-01~08
3. [판정표 18](../../../../design/common/kr-tl/18-신뢰경로-판정표.md), [발행·수신 흐름 19](../../../../design/poc/tl-management/publication/19-변경-발행-수신-흐름.md), [시험 설계 20](../../../../design/poc/tl-management/scenarios/20-시험데이터-비교화면.md): 판정·시간·버전의 구체적 기대 동작
4. [작업계획 22](../planning/22-구현-작업-계획.md), [베이스라인 24](../../../../design/poc/tl-management/background/24-설계-베이스라인.md): 기존 작업 분해 및 범위 결정 이력

이번 PoC의 구현 범위는 다음 절의 사용자 결정(2026-10-07)을 우선 적용한다. 전체 시스템 설계는 목표 구조로 보존하며, 이전 문서의 시험 데이터·R1~R6은 시나리오 논의 자료로만 사용한다.

## 3. 확정된 PoC 범위

| ID | 사용자 결정 | 구현 범위 | 적용 사항 |
|---|---|---|---|
| D-01 | 신뢰체계 개념 증명이 최우선. 인증·감사 기능은 하지 않음 | 인증·역할·권한·감사 기능 제외 | 기존 제출 mTLS·권한 검사도 이번 필수 개발에 포함하지 않음. TL 전자서명 검증은 신뢰체계 핵심 기능으로 유지 |
| D-02 | HSM을 키 파일 서명으로 대체 | 파일에서 읽은 개인키로 실제 TL 서명 | HSM 장비·모의 HSM 연계는 개발 대상에서 제외 |
| D-03 | 기능 메뉴만 두고 구현하지 않음 | 접수·심사·보완·반려 메뉴만 제공 | 해당 업무 API·신청 상태 모델·처리 이력은 구현하지 않음. TL 데이터 관리·인정 상태·발행 기능과 구별 |
| D-04 | IF-01~08이 최신 | 최신 번호·명칭을 추적 기준으로 사용 | 이전 IF-09 기반 신규 기능은 추가하지 않음. 번호 유지가 모든 업무 기능 구현을 뜻하지 않음 |
| D-05 | Spring Boot WAS만 구현 | WAS에서 서명 TL 보관·제공 | 별도 WEB 서버와 WAS→WEB 전송·동기화 구현 제외. 최신 IF 표기는 유지하고 해당 구간은 PoC에서 생략 |
| D-06 | 시험은 PoC 시나리오이며 향후 결정 | 시나리오·표본·시계·결과 수집 방식 결정 유보 | 기존 R1~R6·고정 표본·비교 화면을 확정 요구나 착수 선행조건으로 삼지 않음 |

범위 결정은 완료되었으며, D-06의 시나리오 상세만 향후 결정한다. 제외·유보 항목은 필수 개발량에 합산하지 않는다.

## 4. 기능별 차이와 수정 우선순위

**추가 결정 — 오류 발행 지원:** PoC 관리 화면에서 순번(하위·동일 번호 포함), 발행·만료·상태 시각을 조정해 잘못된 TL도 생성·제공한다. 아래 정상 발행 규칙은 화면의 차단 조건이 아니며 검증 모듈에서 강제한다. 상세 모델과 저장 식별자는 [26](../../../../design/common/kr-tl/26-EU-기준-이력-버전-모델.md)을 따른다. 정상 키로 서명된 의미 오류도 거부해야 한다.

우선순위: P0는 핵심 신뢰 판정·발행의 선행조건, P1은 관리부터 이용환경까지의 연결, P2는 후속 시연·운영 범위다. ‘미확인’은 이번 검토 범위에서 구현 근거를 찾지 못했다는 뜻이다.

아래 표의 표본명·TL #1~#5·R1~R6·구체적 경계 사례는 기존 설계의 참고 항목이다. 이를 포함한 검증 제안은 D-06에 따라 PoC 시나리오 결정 시 조정한다.

| ID | 설계 항목·기준 | 확인한 구현 | 차이·필요 작업 | 우선순위·검증 조건 |
|---|---|---|---|---|
| G-01 | 사업자·서비스 관리, 최신 1장 1.2 | `KrTlAdminController.dashboard()`의 고정 사업자 목록. 화면 등록 버튼은 예시 안내 | 식별자 기반 등록·변경·조회, 서비스별 인증서·공개키 관리 필요 | P1. 등록→조회→재기동 후 보존 |
| G-02 | 접수·심사·보완·반려, IF-01~03 | 관리 컨트롤러에서 dashboard/publish 두 API 확인 | D-03에 따라 메뉴만 제공하고 미구현임을 표시 | 메뉴만 대상. 업무 처리 API·상태·이력 구현 제외 |
| G-03 | 상태·인증서 이력, 12·18 | `KrTrustList.TrustService`에 현재 상태·시작시각·단일 인증서만 존재 | 상태 구간, 이전 디지털 ID, 사업자·서비스 식별자, 확장정보 모델 필요 | P0/W-02. 복원 후에도 과거 정지 구간 보존, SB 경계는 새 상태 |
| G-04 | TL 버전·구조, 10·13 | `SchemeInformation.version` 하나. XML 변환 시 형식 버전과 순번에 같은 값 사용 | 형식 버전·발행 순번 분리, 제도 필드·서비스 이력·KR 확장 정합성 검사 | P0/W-02·03. TL #1~#5와 XSD·의미 규칙 검증 |
| G-05 | 확정본 발행, IF-04·19 | `publish()`가 즉시 AtomicInteger 증가, 메모리 시각 갱신, NextUpdate +24시간 | 확정 데이터 스냅숏, 영속 발행본, 서명 성공 후 번호 확정, 정책 기반 NextUpdate 필요 | P0/W-03·06. 서명 실패 시 번호 미사용, 반출 실패 시 같은 발행본 재사용 |
| G-06 | XML + XAdES 서명, IF-05·22 | builder는 JWS 서명 자산. XML XAdES 서명기는 이용기관 모듈에 존재하며 PrivateKey 사용 | XAdES 서명 책임을 공용 builder로 정리하고 키 파일로 관리 발행 경로에 연결. TL용 앵커와 문서용 신뢰점 분리 | P0/W-03. 실제 XML 서명 생성·검증, 변조 거부. HSM 연계 제외 |
| G-07 | WAS의 TL 제공, D-05 | `poc-krtl-dist` 디렉터리·Gradle 모듈 없음. 관리 API의 발행 결과가 배포 완료처럼 표시됨 | Spring Boot WAS에서 서명본 보관·제공. 별도 WEB 및 WAS→WEB 전송 제외 | P1/W-07. 발행본과 제공 바이트 일치 확인. 별도 모듈 신설 여부는 구현 구성 시 결정 |
| G-08 | 수신·캐시·적용, IF-07·19 | `kr-tl-client`에서 `current()/refresh()` 인터페이스만 확인 | 풀 조회, 서명·순번·NextUpdate 확인, 거부 시 보유본 유지, 만료 후 사용 중단, 적용 색인 필요 | P0/W-04. 위조·역행 거부와 유효 캐시 유지, 만료 시 NO_VALID_TL |
| G-09 | 서명 시각별 판정, 18 | `PdfBaselineVerifier.evaluateTrustList()`는 DN 기반 후보 탐색 후 현재 GRANTED 여부 비교 | 인증서·키 기반 신뢰점 연결을 검토하고, 서명 시각의 이력 구간 판정·구체적 사유 코드·사용 TL 번호 추가 | P0/W-05·09. 18의 S0~S4·SB × TL #1~#5 공통 벡터 재현 |
| G-10 | 관리 화면과 사실에 근거한 결과 표시, 최신 1장·19 | `index.html`은 dashboard/publish 연동, 장애 시 DEMO 데이터, 등록·로그 내보내기는 예시 | 실제 처리 API와 연결, 발행·게시·수신·적용 구별. 서명·게시 증거 없이 성공 표시하지 않음 | P1/W-06. 발행 실패·게시 실패·오프라인 상태를 다른 결과로 표시 |
| G-11 | 변경 전후 비교 화면, 18 §6.1 | dashboard에 고정 통계·활동 목록. 사용 TL·표본·경로·사유 수집 경로 미확인 | 결과 수집·비교 화면의 상세 요구는 PoC 시나리오에 따라 결정 | 유보/D-06. 수집 계약·실행 ID를 지금 확정하지 않음 |
| G-12 | 브라우저 이용환경, 22 W-10 | 계획상 `poc/krtl-chrome-ext` 디렉터리 없음 | 크롬 확장 범위 확인 후 TL 검증·수신·표본 정보 기반 판정 구현 | P2/W-10. Java와 공통 벡터 일치. 전체 PDF 서명 검증 완료로 표시하지 않음 |
| G-13 | 구현 검증과 PoC 시나리오, 20·21 | `poc-kisa-tl` 및 `kr-tl` 하위 3개 모듈에서 시험 소스 미확인, build의 해당 test는 NO-SOURCE | 구현 변경에 필요한 기술 검증은 수행. PoC 전체 시나리오·완료 기준은 향후 결정 | 핵심 기술 검증 대상. R1~R6 통합 실행 확정은 유보/D-06 |
| G-14 | 사업자 구성·제출, IF-01·22 W-08 | `TspCaService`는 단일 CA와 메모리 발급 대장 사용. 기본 설정은 joint-ca 키스토어 하나. CA·OCSP 자산 존재 | 신청·접수 업무와 mTLS 인증 구현 제외. 사업자·인증서 구성은 시나리오 결정에 맞춰 보완 | 업무 구현 제외/D-01·03, 표본 구성 유보/D-06. OCSP 상태와 TL 인정 상태의 독립성 유지 |
| G-15 | 인증체인·서명 표본, 20·22 W-01 | `CertCommand`는 gen/chain/p12 제공. EE EKU는 clientAuth, 발급 기준은 Instant.now(). `gen-certs.ps1`은 기존 단일 Root 아래 4개 CA를 재생성 | 생성 자산은 재사용. 체인·표본 수·기준 시각·메타데이터 형식은 시나리오 결정 후 구체화 | 유보/D-06. 기존 고정 표본 요구를 필수 선행 작업으로 삼지 않음 |
| G-16 | 초기화·체크포인트, 21·22 W-11 | `poc-up.ps1`은 full/mode1 지원. KR-TL 전용 초기화·체크포인트 도구 미확인 | WAS 기동 구성은 구현에 맞춰 보완. 시연 초기화·시험 시계·체크포인트는 시나리오 결정 후 구체화 | 시나리오 도구 유보/D-06 |

## 5. 구현 근거

경로는 저장소 루트 기준이며 행 번호는 기준 커밋의 위치다.

| 근거 | 위치·확인 내용 |
|---|---|
| 관리 API | `src/poc/poc-kisa-tl/src/main/java/com/electcerti/krdss/poc/kisa/KrTlAdminController.java:20` — 인메모리 상태, dashboard, publish |
| 관리 화면 | `src/poc/poc-kisa-tl/src/main/resources/static/index.html:691` — API 호출, DEMO 대체, 발행 성공 토스트·예시 버튼 |
| TL 모델 | `src/common/kr-tl/kr-tl-model/src/main/java/com/electcerti/krdss/tl/model/KrTrustList.java:12` — scheme/TSP/service record |
| XML 변환 | `src/common/kr-tl/kr-tl-builder/src/main/java/com/electcerti/krdss/tl/builder/KrTrustListXml.java:80` — 버전·순번 혼용. 알 수 없는 상태를 WITHDRAWN으로 처리하는 매핑도 후속 확인 대상 |
| JWS 자산 | `src/common/kr-tl/kr-tl-builder/src/main/java/com/electcerti/krdss/tl/builder/KrTrustListBuilder.java`, `SignedKrTrustList.java` — 기존 JSON/JWS 경로. 신규 XML 단일 경로로 전환할 때 기존 호출부 영향 조사 필요 |
| XML 서명 | `src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/pdf/KrTrustListXmlSigner.java:30` — DSS XAdES enveloped, 전달받은 PrivateKey로 서명 |
| 수신 계약 | `src/common/kr-tl/kr-tl-client/src/main/java/com/electcerti/krdss/tl/client/KrTrustListClient.java:11` — 두 메서드의 인터페이스 |
| 이용기관 판정 | `src/poc/poc-relying-party/src/main/java/com/electcerti/krdss/poc/rp/pdf/PdfBaselineVerifier.java:213` — TL 서명 검증 결과와 현재 서비스 상태 결합 |
| 모듈 구성 | `settings.gradle.kts`, `poc/` — 기존 KISA·사업자·이용기관 모듈, 배포 전용 모듈 미등록 |
| 사업자 CA·OCSP | `src/poc/poc-tsp-sim/src/main/java/com/electcerti/krdss/poc/tsp/pki/TspCaService.java:40` — 단일 CA, 메모리 대장, 미추적 인증서는 OCSP unknown. `src/main/resources/application.yml`은 joint-ca 하나 구성 |
| 사업자 기존 시험 | `src/poc/poc-tsp-sim/src/test/java/com/electcerti/krdss/poc/tsp/TspCaOcspIntegrationTest.java` — RA 등록·CA 발급·OCSP 상태 전이 시험 자산. TL 신청·인정 상태 시험과 별개 |
| 인증서 생성 | `src/tools/krdss-cli/src/main/java/com/electcerti/krdss/cli/CertCommand.java:456` — 현재 시각 기준 발급, EE clientAuth EKU. `scripts/common/gen-certs.ps1` — 기존 체인 생성·키스토어 편성 |
| 기동·재현 도구 | `scripts/poc/poc-up.ps1` — full/mode1 모드. scripts 목록·설정과 생성 도구를 정적으로 확인했으며 기동·키 재생성·초기화는 실행하지 않음 |

코드 탐색에는 최신 main 기준으로 다시 색인한 codebase-memory의 search_graph/get_code_snippet을 사용했다. 그래프에서 확인되지 않은 시험 파일과 제외된 정적 화면·설정은 파일 검색으로 보완했다. 별도 장치 신뢰/Attestation 시험이 있다는 사실을 KR-TL 관리 시나리오의 시험 충족으로 계산하지 않았다.

## 6. 개발 순서 제안

1. 본문 D-01~06을 구현 범위 기준으로 사용한다. IF-01~08을 따르되 제외된 업무·인증·감사·HSM·WEB 기능은 개발 목록에서 뺀다.
2. W-02·03·05: 이력·버전 모델, XML 편성·키 파일 서명, 시점 판정을 연결하고 변경에 필요한 단위 검증을 수행한다.
3. W-06: TL 데이터 관리·저장·발행을 연결한다. 접수·심사·보완·반려는 메뉴만 제공한다.
4. W-07·04·09: Spring Boot WAS의 TL 제공과 이용기관의 수신·검증·적용을 연결한다. 별도 WEB 배포 단계는 두지 않는다.
5. PoC 시나리오 결정 후 표본, 결과 비교, 브라우저 시연, 초기화·재실행 범위를 구체화한다. 기존 R1~R6을 자동으로 확정하지 않는다.

최초 구현 단위는 **이력·버전 모델과 키 파일 기반 TL 발행**을 권장한다. 이 결과가 관리 화면, 발행 스냅숏, 수신 색인의 공통 계약이 된다. 상세 PoC 시나리오 결정은 이 작업의 착수 조건이 아니다.

사업자 연계 시 고정 표본 인증서가 발급 대장에 없으면 기존 `lookup()`은 OCSP unknown을 반환한다. W-01 산출물을 W-08에 적재할 때 정상 상태 조회까지 연결해야 TL 상태 변화만으로 판정이 달라지는 시연을 재현할 수 있다. 인증서 폐지·정지 API를 사업자의 TL 인정 상태 변경 API로 재사용하지 않는다.

## 7. 검증 기록과 한계

- `gradlew.bat build`: **BUILD SUCCESSFUL**, 163 tasks 중 1 executed, 162 up-to-date (2026-10-07).
- 이는 현재 빌드·기존 시험 태스크의 성공이며, 새 설계 시나리오를 실행했다는 의미가 아니다. 관리·TL 모듈의 NO-SOURCE와 구별한다.
- 브라우저 화면 실행, 원격 Google Docs 재조회, 사업자 제출·HSM 실제 연계, R1~R6 전체 실행은 이번 검토에서 수행하지 않았다.
- 이전 `yongsungkim-electcerti/kr-tl`은 고유 커밋 0개·미커밋 변경 0개였고 main에 포함되어 종료했다. 최신 설계·인계는 이미 `59b6cd9`에 반영되어 있어 중복 설계 커밋을 만들지 않았다.
