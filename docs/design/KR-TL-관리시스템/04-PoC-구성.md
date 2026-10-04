# 04. PoC 구성

> [01 시스템 구성도](01-시스템-구성도.md)의 구성요소·인터페이스를 **그대로 유지**하고, 인프라(방화벽·VPN·HSM·DMZ)만 소프트웨어로 대신한다.
> 목표는 [03 시나리오](03-이용-시나리오.md)의 ★ 항목을 실제로 돌려 보는 것이다.

## 1. 구성요소 ↔ PoC 모듈

| 구성도 요소 | PoC 모듈 | 대체한 것 |
|---|---|---|
| 관리 시스템 전체 | `poc-kisa-tl` (:8081) | 폐쇄망 → 단일 프로세스. 내부 구성요소는 패키지로 나눔. 배포 쪽 설정에 관리 시스템 주소를 두지 않아 단방향 유지 |
| 관리 콘솔 | `poc-kisa-tl` 정적 화면 | MFA·접근통제 → 역할 선택 로그인 |
| 사업자 연계 서버 | `poc-kisa-tl` `provider` 패키지 | VPN → 생략, 클라이언트 인증서로 사업자 식별 |
| 신뢰정보 관리 서버 · TL 발행 서버 | `poc-kisa-tl` `registry`, `publish` 패키지 + `kr-tl-builder` | — |
| KISA Root CA · KISA CA | `krdss-cli cert`로 생성한 Root·하위 CA 키스토어 | 오프라인 Root → 파일 키스토어. 기존 New-KISA RootCA 키스토어 재사용 검토 |
| TL 서명 인증서 | KISA CA가 발급 (`krdss-cli cert`) | — |
| HSM | PKCS#12 소프트 키스토어 (옵션: `poc-hsm` :8092) | 서명 경로는 인터페이스 하나로 감춰 교체 가능 |
| 배포 시스템 (배포 WEB · 배포 WAS) | `poc-krtl-dist` (:8083, 신규). 수신 현황 화면 포함 | DMZ WEB 생략. 연계 API는 별도 포트(:9443)로 분리 |

> 서버 내부 데이터 저장은 각 모듈의 H2(파일 모드)를 쓴다. 구성도에는 그리지 않는다.
| CA · OCSP · TSA | `poc-tsp-sim` (:8082) | CA·OCSP 있음. TSA·제출 기능 추가 |
| 가입자 | `krdss-cli` 또는 `poc-relying-party` 서명 화면 | 타임스탬프 포함/미포함 서명 생성 |
| 이용자 시스템 (KR-DSS SDK) | `poc-relying-party` (:8080) + `kr-tl-client` | 검증 결과 화면에 **사용한 TL 버전** 표시 |
| 웨일 브라우저 | 웨일 검증 모듈 (또는 브라우저 JS 검증 페이지) | TL 수신·서명된 결과 검증. 접속 시 `X-KRTL-Client` 헤더로 유형 구분 |

## 2. PoC 구성도

```mermaid
flowchart LR
    subgraph EXT["외부 역할"]
        TSP["poc-tsp-sim :8082<br/>인증사업자"]
        USR["krdss-cli<br/>가입자 서명"]
        WH["웨일 브라우저"]
        RP["poc-relying-party :8080<br/>이용자 시스템 (kr-tl-client)"]
    end
    subgraph M["poc-kisa-tl :8081 — ① 관리 시스템"]
        CON["관리 콘솔 M-01~09"]
        PRV["provider"]
        REG["registry"]
        PUB["publish + kr-tl-builder"]
    end
    subgraph D["poc-krtl-dist :8083/:9443 — ② 배포 시스템"]
        DL["배포 :8083"]
        RCV["연계 :9443"]
    end
    TSP -- "IF-01·02" --> PRV --> REG --> PUB
    CON --> REG & PUB
    PUB -- "IF-07 패키지 반출" --> RCV
    RP -- "IF-08 TL 수신" --> DL
    WH -- "IF-08 TL 수신" --> DL
    TSP -. "인증서 발급 · 타임스탬프" .-> USR
    USR -. "전자서명 제출" .-> RP
    RP -- "IF-09 서명 결과 제공" --> WH
    USR -- "IF-10 서명 결과 검증" --> WH
    RP -. "OCSP" .-> TSP
    WH -. "OCSP" .-> TSP
```

## 3. 시나리오 실행 순서 (1차)

| 순서 | 시나리오 | 준비물 |
|---|---|---|
| 1 | SC-01 신규 등재 | tsp-sim 사업자 2개, 가입자 서명 1건 |
| 2 | SC-06 동기화·미수신 | 이용자 2개(동기화 주기 다르게), 웨일 브라우저 |
| 3 | SC-03 → SC-04 정지·해제 | 타임스탬프 있는 서명(정지 전/중/후), 없는 서명 |
| 4 | SC-05 철회 | 철회 전후 서명 |
| 5 | SC-08 변조 | 배포 WAS 보관 파일(`output/krtl-dist/`) 직접 수정, 예전 버전 재게시 |
| 6 | SC-11 내부 통제 | 계정 2개, H2 직접 수정 |

각 시나리오 결과는 "관리 화면 캡처 + 이용자 검증 결과(사용 TL 버전 포함) + 감사 기록"으로 남긴다.
