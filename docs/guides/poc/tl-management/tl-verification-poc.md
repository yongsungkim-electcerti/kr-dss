# TL 적용·실제 PDF 검증·결과 비교

## 실행

관리 시스템에서 생성한 PoC v1 XML/XAdES TL과 별도 TL 신뢰앵커(PEM 또는 DER), 단일 detached 서명 PDF를 준비한다.

```powershell
./scripts/poc/Start-TlPoc.ps1 -Anchors 'F:/kr-dss-works/kr-dss/runtime/시험자료/tl-root.pem' -Port 8082
```

`http://127.0.0.1:8082/tl-poc.html`에서 사용한다. 위 앵커 경로는 예시이며 실제 파일을 지정해야 한다. 개인키는 입력하지 않는다. 서버 설정의 앵커 파일은 API로 변경할 수 없다. 시작 스크립트는 loopback에만 바인딩한다. 인증·사용자 분리는 없는 단일 로컬 PoC다.

1. 관리 시스템에서 TL을 발행·내려받는다. 기존 대시보드와 사업자 화면은 그대로 사용한다.
2. **TL 적용**: XML을 입력하고 검사 후 적용한다. 번호·해시·만료·채택시각을 확인한다. 잘못된 후보는 기존본을 교체하지 않는다.
3. **서명 검증**: PDF와 필요 시 중간 인증서 묶음을 넣는다. 평가시각을 UTC ISO 형식으로 입력한다. 시각은 시험 지정값이며 과거 서명 시각의 증거가 아니다.
4. TL 상태를 바꾸어 새로 발행·적용하고 같은 PDF·인증서·평가시각으로 재검증한다. 중간 정지 구간을 남기려면 각 상태 변경 후 발행한다.
5. **결과 비교**: 두 실행을 선택한다. 조건이 다르면 나란히 조회만 가능하며 TL 단독 효과 판정은 보류한다.

## 저장 위치 및 제약

- `config/local-data.properties`의 mainRoot 또는 `KRTL_DATA_ROOT` 아래 `runtime/tl-verification/`에 저장한다.
- `tl/active.xml`: 재시작 시 다시 검사하는 적용 TL. 만료본은 보관하되 사용하지 않는다.
- `runs/<UUID>/`: 실제 입력 PDF·추가 인증서·TL 원문·result.json·result.sha256. API로 기존 실행을 수정하지 않는다. 로컬 파일 권한을 가진 사람에 대한 위변조 방지 서명은 아니다.
- TL 5 MiB, PDF 25 MiB, 추가 인증서 1 MiB/20장. 검증 작업 1개, 대기열 없음, 응답 제한 30초. 취소를 무시하는 라이브러리 작업은 종료까지 작업 슬롯을 점유하며 새 실행은 BUSY로 기록한다. 강제 프로세스 격리는 이번 범위 밖이다.
- 지원: 단일 `adbe.pkcs7.detached` / `ETSI.CAdES.detached` PDF 서명, 전체 파일 ByteRange, PoC v1 CA 서비스.
- 미검증: CRL/OCSP, 신뢰 시각 증거, PDF 다중 서명·증분 개정. 결과를 전체 전자서명 유효로 해석하지 않는다.
- 기존 `/local-sign.html`의 범용 시연 경로는 유지한다. 이번 TL 기반 실증은 `/tl-poc.html`에서 실행한다.

## 개발 상태

[차수별 계획·체크표](../../../development/poc/tl-management/implementation-stages.md)를 참조한다.

## TL 읽기 화면 v0.4

서버를 재시작하고 tl-poc.html을 강력 새로고침한다. 파일 선택 후 **내용 확인·검사**로 구조·변경을 살핀 다음 **확인한 TL 적용**을 누른다. 검사만으로 적용본은 바뀌지 않는다. 해시는 기술 상세를 펼쳐 확인하며, 상태·효력시각·인증서의 XML 버튼으로 해당 근거를 읽는다. 원문 다운로드는 서명된 바이트를 보존한다. 결과 비교에서는 두 실행에 실제 저장된 TL 내용도 비교한다.
