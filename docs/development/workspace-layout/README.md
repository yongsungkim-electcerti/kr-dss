# 2026-10-08 작업 공간 정리

[검증 기록](verification.md)

## 분류 기준

설계는 `docs/design/common`과 `docs/design/poc`, 소스는 `src/common`과 `src/poc`로 분리했다. 개발 계획·검토·인계는 `docs/development`, 실행 절차는 `docs/guides`, 외부 원문은 `references`, 제출·발표 자료는 `deliverables`에 둔다.

[파일별 대응표](file-map.csv)는 정리 전 1,115개 추적 파일의 원래 경로·처리·목적지·사유·원본 SHA-256을 기록한다. 원본 312개는 백업 전용, 784개는 이동, 19개는 위치 유지로 분류했다. 위치 유지 파일 중 README·빌드 설정은 갱신 대상이다.

## main 로컬 백업

백업: `F:/kr-dss-works/kr-dss/_backup/2026-10-08-layout-133204/`

- `originals/`: 원래 경로를 유지한 정리 전 파일 1,115개. LFS 포인터가 아닌 실제 파일을 확인하고 SHA-256으로 복사 검증.
- `manifest.csv`: 원본·백업 위치·처리 사유·목적지·해시.
- `plan.json`: 파일별 정리 계획.

main의 소스와 커밋은 변경하지 않았다. Git 로컬 `info/exclude`에 `/_backup/`을 추가하여 즉시 추적에서 제외했다. 작업 브랜치의 `.gitignore`에도 같은 규칙을 반영한다. 백업 자료는 저장소 clone으로 전달되지 않는다.

## 보관 판단

- 발표자료 v01~v06, 초기 중복 발표 소스, ZIP은 백업. 편집 가능한 v07과 v08 배포 자료는 유지.
- 통합 원문 확보 후 남은 이전 수집 중간자료와 기존 원문 제외 자료는 백업. 원문·출처 목록·요약·고유 EIF 분석 보고서는 유지.
- 도식은 SHA-256이 같은 파일만 중복 제거. 내용이 다른 도식과 상세 설계는 참조 위치에 보존.
- 폴더 정리 전 인계·혼합 목차는 새 README·handoff로 대체.
- 외부 도구 설치용 `scripts/install.ps1`은 제품 빌드와 무관하여 백업.

## 실행 경로

Gradle 논리 모듈 경로는 그대로다. `settings.gradle.kts`의 projectDir만 실제 `src/` 위치에 대응한다. 빌드 설정·래퍼·build-logic은 루트에 유지한다.

기존 생성 인증서 31개는 `runtime/pki/legacy/`로 이동하여 추적에서 제외한다. 새 checkout은 [인증서 생성 절차](../../guides/pki/legacy-certificates.md)를 사용한다. 기존 PoC의 classpath 테스트 키스토어는 재현용 리소스로 유지하며 새 PoC 운영 키로 사용하지 않는다.

실행 패키지는 `results/packages/whale/`, 로그는 `logs/`, 향후 TL 관리 데이터는 `runtime/tl-management/`에 둔다. `runtime/`, `results/`, `_backup/`는 Git 비관리다.

참조 원문의 바이트와 원래 출처 메타데이터는 유지한다. 과거 목록의 원래 경로는 provenance이며 현재 위치는 file-map.csv와 references/catalog/current-paths.json으로 연결한다.
