# 로컬 실행 자료 관리

로그·디버그·실행 데이터·키·결과물은 main 체크아웃에서 관리한다.
`config/local-data.properties`의 mainRoot가 기준 위치이며 다른 머신에서는
`KRTL_DATA_ROOT` 환경변수로 재정의한다. 경로 설정만 Git으로 관리한다.

| 자료 | main 아래 위치 |
|---|---|
| TL 초안·발행본·키 | runtime/tl-management |
| 로그 | logs |
| 실행 결과 | results |
| 정리 전 자료 | _backup/2026-10-26-tl-management |
| 워크트리 캐시·보조 파일 | _backup/2026-10-26-tl-management/worktree-local |

`tl-admin-up.ps1`, `seed-demo-data.ps1`, `gen-tl-pki.ps1`은 같은 설정을 읽는다.
`:poc:poc-kisa-tl:bootRun`도 이 설정으로 storage-root를 지정한다.
JAR를 직접 실행할 때는 `--krtl.admin.storage-root=F:/kr-dss-works/kr-dss/runtime/tl-management`를 전달한다.
동일 데이터를 사용하는 관리 서버는 한 번에 하나만 실행한다.

이동 중 main에 같은 경로의 다른 파일이 있으면 기존 main 파일을
`_backup/2026-10-26-tl-management/main-before-local-move/`에 보존한다.
파일별 이동·해시는 백업의 local-files.csv에 기록한다.
공유 데이터는 워크트리에 복사하거나 링크하지 않는다. Gradle 캐시와 build 출력은
공유하지 않으며 빌드하면 각 워크트리에서 재생성된다. 에이전트 스킬·설정은 개발 도구 설정으로 유지한다.
