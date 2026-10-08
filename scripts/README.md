# 실행 도구

저장소 루트에서 실행한다. PowerShell 스크립트는 자신의 위치를 기준으로 저장소 루트를 찾는다.

| 용도 | 명령 |
|---|---|
| 기존 시연 인증서 생성 | `pwsh scripts/common/gen-certs.ps1` |
| 데모 HTTPS 인증서 생성 | `pwsh scripts/common/gen-demo-tls.ps1 -Hostname sol-pc` |
| 기존 전체 PoC 기동 | `pwsh scripts/poc/poc-up.ps1 -Mode full` |
| WebAuthn 시연 기동 | `pwsh scripts/poc/poc-up.ps1 -Mode mode1` |
| 서비스 종료 | `pwsh scripts/poc/poc-down.ps1` |
| 로그 확인 | `pwsh scripts/poc/poc-logs.ps1 -Service relying-party` |
| PDF PoC 패키징·기동 | `pwsh scripts/poc/whale-startup.ps1` |

TL 관리시스템 단독 기동은 `./gradlew :poc:poc-kisa-tl:bootRun`이다. 기존 기동 도구는 다른 PoC도 지원하며 이번 관리 기능의 구현 완료를 뜻하지 않는다.
