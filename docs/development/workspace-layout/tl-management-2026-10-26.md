# TL 관리시스템 자료 정리 (2026-10-26)

백업 위치: `F:/kr-dss-works/kr-dss/_backup/2026-10-26-tl-management/`. Git 비관리이며 clone에 포함되지 않는다.

`snapshot/`은 정리 전 추적 파일 868개의 문서·소스·참고자료, `archive/`는 현행 트리에서 제외한 이전 자료다. `manifest.csv`에 원래 경로·기준 커밋·SHA-256을 기록했다. 복원 시 해시를 확인하고 원래 상대 경로로 복사한다.

현재 기준은 TL 관리 설계의 scope, concept-v02-application, profile-poc-v1, issuance-recovery와 공통 모델·IF-07 계약이다. 공통 문서에서 사용하는 배경 도식, 결정 근거 검토 기록, 실행 소스·테스트는 유지한다. 다른 PoC와 공통 참고자료는 백업 사본만 만들고 원래 위치를 유지한다.

## 보관 자료

아래 항목은 과거 자료이며 현재 구현 기준이 아니다. 각 경로는 백업의 archive/ 아래에서 찾을 수 있다.

<a id="archive-1"></a>
- `docs/design/poc/tl-management/background/01-시스템-구성도.md`

<a id="archive-2"></a>
- `docs/design/poc/tl-management/background/04-PoC-구성.md`

<a id="archive-3"></a>
- `docs/design/poc/tl-management/background/24-설계-베이스라인.md`

<a id="archive-4"></a>
- `docs/design/poc/tl-management/background/infrastructure/A-아키텍처-결정기록.md`

<a id="archive-5"></a>
- `docs/design/poc/tl-management/background/infrastructure/B-인프라-구성-상세.md`

<a id="archive-6"></a>
- `docs/design/poc/tl-management/background/infrastructure/C-망구성-상세.md`

<a id="archive-7"></a>
- `docs/design/poc/tl-management/background/infrastructure/krtl-network.drawio`

<a id="archive-8"></a>
- `docs/design/poc/tl-management/background/infrastructure/krtl-network.drawio.png`

<a id="archive-9"></a>
- `docs/design/poc/tl-management/interfaces/background/06-IF-명세서.md`

<a id="archive-10"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/concept-design.md`

<a id="archive-11"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/document.json`

<a id="archive-12"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/export-manifest.json`

<a id="archive-13"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/KRTL_v02.html`

<a id="archive-14"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image1.png`

<a id="archive-15"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image10.png`

<a id="archive-16"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image11.png`

<a id="archive-17"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image12.png`

<a id="archive-18"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image13.png`

<a id="archive-19"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image2.png`

<a id="archive-20"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image3.png`

<a id="archive-21"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image4.png`

<a id="archive-22"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image5.png`

<a id="archive-23"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image6.png`

<a id="archive-24"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image7.png`

<a id="archive-25"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image8.png`

<a id="archive-26"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/html/images/image9.png`

<a id="archive-27"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/image-map.json`

<a id="archive-28"></a>
- `docs/design/poc/tl-management/sources/2026-10-08-concept-v02/verification.json`

<a id="archive-29"></a>
- `docs/development/poc/tl-management/planning/00-설계-진행계획.md`

<a id="archive-30"></a>
- `docs/development/poc/tl-management/planning/22-구현-작업-계획.md`

## 검증 결과

- 정리 전 추적 파일 868개 및 백업 전용 30개 SHA-256 일치.
- Git 비관리 로컬 자료 2,693개를 main으로 이동하고 워크트리에서 제거. 기존 main과 다른 파일 12개는 main-before-local-move에 보존.
- 워크트리의 에이전트 스킬·설정은 유지. 런타임·로그·결과·빌드 캐시는 제거.
- 전체 Gradle build 성공: 169개 작업, 1개 실행·168개 최신 상태(기존 테스트 결과 재사용).
- PowerShell 구문 검사·main 경로 로딩 정상, 변경 Markdown 로컬 링크 누락 0건, git diff --check 통과.
- 파일별 로컬 자료 이동 내역은 백업의 local-files.csv에서 확인한다.
