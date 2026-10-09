# 34. XML 서명·발행본 보관 구현 결과

작성일: 2026-10-09. 브랜치 `feat/claude-tl-admin`. 개발 단계 3(XML 생성·키 파일 서명)과 4(발행본 보관)의 보관 부분을 구현했다. 제공본 선택·IF-07은 다음 단계다.

## 구현 범위

| 구분 | 내용 | 소스 |
|---|---|---|
| XML 서명 | enveloped XAdES-BASELINE-B, SHA-256, ECDSA(T-02 키). ds:Signature는 TL 루트의 마지막 자식, KeyInfo에 T-02·T-01 | `kr-tl-builder/KrTrustListXmlSigner.sign` |
| 서명 자체 확인 | JDK XMLDSig로 서명값·모든 Reference 다이제스트 검사, KeyInfo 인증서가 T-02와 같은지 확인. 체인·신뢰 판정 아님 | `KrTrustListXmlSigner.check` |
| 서명 키 | `runtime/tl-management/pki/tl-signer/T-02-signer`의 key.pem·chain.pem을 발행 때마다 읽고 키쌍 일치 확인 | `issuance/SignerKeys` |
| 후보 편성 | 정상 기준 스냅숏 대비 최종 변경만 ServiceHistory로 추가. 같은 키 인증서 갱신·단순 재발행은 이력 미추가 | `issuance/CandidateBuilder` |
| 사전 검사 | SEQUENCE_NOT_INCREASING, SEQUENCE_GAP, ISSUE_TIME_NOT_INCREASING, ISSUE_TIME_IN_FUTURE, NEXT_UPDATE_NOT_AFTER_ISSUE, NEXT_UPDATE_TOO_FAR(6개월), NEXT_UPDATE_PASSED, HISTORY_START_NOT_INCREASING, STATUS_START_RETROACTIVE. 경고가 있으면 BASELINE 요청도 TRIAL로 저장 | `CandidateBuilder` |
| 보관 | staging/&lt;requestId&gt;에 signed.xml·signed.sha256·snapshot.json·profile.json·manifest.json 기록 후 issued/&lt;issuanceId&gt;로 원자 이동. 기존 ID 덮어쓰기 금지 | `issuance/IssuanceService` |
| 확정 | state.json v2(committed 목록·baselineIssuanceId·normalSequence) 원자 교체가 확정 지점. BASELINE은 기준을 가리키는 새 초안 revision을 함께 활성화, TRIAL은 기준·정상 순번 불변 | `RegistryStore.commitIssuance` |
| 중복 요청 | 같은 requestId·같은 명시 입력이면 저장 결과 반환(재서명 없음), 다른 입력이면 REQUEST_CONFLICT, 실패·중단된 요청은 종결 결과 반환 | `IssuanceService.issue` |
| 복구 | 기동 시 확정되지 않은 진행 요청을 INTERRUPTED로 종결. state에 없는 issued 폴더는 ‘미확정 잔여’로 표시하고 자동 제공·승격하지 않음. 조회 시 signed.xml 해시 확인, 손상이면 제공 거부 | `IssuanceService` |
| 화면 | TL 발행: 목적·순번(권장값)·발행 시각·NextUpdate 입력, 사전 검사, 서명·발행, 응답 유실 시 같은 requestId 재전송, 발행본 목록(정상/시험·진단·해시·자체 서명 확인·무결성·XML 내려받기) | `static/index.html` |

초안 편집 규칙도 보완했다. 정상 기준 발행 후의 서비스 정보 수정은 현재 시작 이후의 효력 시각을 받아 새 이력 구간이 되게 했다.

## 검증

- `gradlew.bat build` 통과. kr-tl-builder 18건, poc-kisa-tl 27건(발행 8건 신규), kr-tl-model 5건 실패 0.
  - 서명·확인·변조 거부·미서명 거부
  - 최초 BASELINE 확정과 기준 전진, 같은 요청 재전송(재서명 없음), 다른 입력 충돌
  - 큰 순번·과거 시각 TRIAL 저장과 정상 권장 순번 불변
  - 정지→복원 두 번 변경 후 이력 1구간, 같은 키 인증서 갱신 후 이력 미추가
  - 오래된 초안 revision 거부, state 교체 전 중단 시 미확정 잔여·재기동 후 중단 종결·새 requestId 정상 발행
  - 서명 키 누락 시 미확정, 재기동 후 확정본 복원, signed.xml 변조 검출
- 임시 저장 루트와 실제 T-02 키로 기동해 JA(CA·OCSP·TSA)·SA(RootCA 레벨 0) 등록 → BASELINE 1 → 재전송 → 순번 100 TRIAL → 화면에서 BASELINE 2 발행을 확인했다.

## 한계

- 자체 서명 확인은 암호 서명과 참조만 본다. ETSI TS 119 612 전체 XSD 적합성, XAdES 전체 검증, 이용기관의 체인·신뢰 판정은 하지 않았다.
- NextUpdate 기본값 7일은 PoC 입력 편의값이며 운영 수치가 아니다.
- 상태 시작 시각만 바꾼 오류 TL(초안을 거치지 않는 시험 override)은 아직 없다. 현재는 순번·발행 시각·NextUpdate만 발행 요청에서 조정한다.
- 다음: 제공본 선택(publicationRevision) → IF-07 `/tl/kr-tl.xml`·`/tl/kr-tl.sha2`·ETag/304 → 화면 연결.
