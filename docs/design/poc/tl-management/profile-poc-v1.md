# TL 관리 PoC 프로파일 v1

작성일: 2026-10-09. 내부 구현을 위한 임시 프로파일 설계다. 국가 공표 규격이나 ETSI 인증 결과가 아니다. 기본 이력 모델은 [26](../../common/kr-tl/26-EU-기준-이력-버전-모델.md)을 유지한다.

## 식별과 호환성

profileId는 `kr-tl-poc`, profileVersion은 `1`이다. 발행 시 사용한 매핑 설정 전체와 해시를 스냅숏에 보존한다. 현재 설정 변경으로 과거 XML·해석을 재작성하지 않는다.

PoC 전용 URI 접두사는 `urn:example:kr-tl:poc:v1:`로 선택한다. 발행기·시험 소비자에 같은 프로파일 설정을 명시적으로 제공한다. 공식 URI가 결정되면 새 프로파일 버전으로 전환하며 기존 발행본은 그대로 보존한다. 임시 값을 외부 공식 TL과 자동 호환되는 것으로 취급하지 않는다.

## 서비스 유형·분야·레벨

| 내부 값 | PoC 표현 | 규칙 |
|---|---|---|
| CA | 접두사 + `service:ca` | RootCA와 하위 CA를 같은 서비스 유형 내에서 구별 |
| OCSP | 접두사 + `service:ocsp` | 인증서 상태 서비스 |
| TSA | 접두사 + `service:tsa` | 시점확인 서비스 |
| JOINT | trustDomain=`joint` | 공동인증 분야 |
| SIMPLE | trustDomain=`simple` | 간편인증 분야 |
| ROOT | trustPointLevel=`0` | CA 서비스 중 RootCA |
| CA | trustPointLevel=`1` | CA 서비스 중 개별 CA |

유형 URI는 ServiceTypeIdentifier에 연결한다. 이 임시 유형을 해석하지 못하는 소비자는 기존 EU 유형으로 추정하지 않는다. 기존 데이터는 명시적인 import 매핑을 거쳐 새 초안으로 가져온다.

trustPointLevel은 CA에만 필수이며 OCSP·TSA에는 넣지 않는다. 그림의 ‘CA·OCSP·TSA(레벨 1)’은 서비스군을 개별 등재한다는 설명으로 해석하고, OCSP·TSA의 수준 숫자로 저장하지 않는다. 분야는 provider가 아니라 서비스 정보의 필드이며 현재·이력마다 보존한다.

OCSP·TSA를 직접 등재하면 각각 자체 디지털 ID·상태를 갖는 서비스가 된다. 레벨 0 연결을 시험할 경우 등재 RootCA까지의 실제 경로와 해당 RootCA의 상태가 판단 근거다. 관리 시스템이 등재되지 않은 OCSP·TSA의 개별 인정 상태를 만들어 넣지 않는다. 외부 검증기는 응답·타임스탬프 서명과 인증서의 용도 등을 별도로 검증한다. 정지된 직접 등재 서비스를 상위 RootCA로 우회하여 통과시키지 않는 기존 정책도 유지한다.

## 인정 상태

| 업무 상태 | PoC statusUri | 입력·변경 규칙 |
|---|---|---|
| ACCREDITED | 접두사 + `status:accredited` | 최초 인정 또는 정지 후 복원 |
| SUSPENDED | 접두사 + `status:suspended` | 정지 효력 시각부터 적용 |
| WITHDRAWN | 접두사 + `status:withdrawn` | 철회 효력 시각부터 적용 |

statusUri는 ServiceStatus에 연결한다. ‘복원’은 별도 상태가 아니라 ACCREDITED로의 새 변경이다. 접수·심사·보완·반려·미등재는 상태 URI에 포함하지 않는다.

모델 26의 SET_BY_NATIONAL_LAW는 향후 표현 가능성을 보존하는 개념이다. 이번 세 가지 상태용 발행 화면에서는 지원하지 않는다. 미지원 URI를 입력하면 명시적인 오류를 반환하고, 외부 XML을 읽을 때는 원 URI를 보존한 채 해석 불가로 반환한다. WITHDRAWN이나 ACCREDITED로 자동 치환하지 않는다.

최초 발행의 정상 상태는 ACCREDITED다. 정상 전이는 인정→정지/철회, 정지→인정/철회다. 철회 후 동일 서비스 재인정은 이 버전에서 정의하지 않는다. 같은 상태·동일 자료 저장은 새 이력을 만들지 않는다. 상태 전이 정책을 시험하기 위한 임의 상태 추가는 순번·시각 오류 생성 범위와 구별한다.

기존 GRANTED enum·기존 granted URI는 이전 자료를 읽는 명시적 변환표에서만 국내 ACCREDITED로 대응시킨다. 모든 외부 granted를 무조건 국내 인정으로 바꾸는 전역 규칙은 만들지 않는다.

## KR 확장과 XML 연결

PoC XML 확장 namespace는 `urn:example:kr-tl:poc:extensions:v1`로 둔다. 다음 표는 논리 필드와 확장 payload 설계이며, 실제 ETSI XSD의 확장 컨테이너 배치는 구현 시 사용할 스키마와 함께 검사한다.

| 확장 payload | 값 | critical | 위치 |
|---|---|---|---|
| TrustDomain | joint 또는 simple | true | 현재 ServiceInformation 및 대응 ServiceHistoryInstance의 확장 |
| TrustPointLevel | 0 또는 1 | true | CA 서비스의 현재 및 대응 이력 확장 |

확장 종류·namespace·값·critical 여부를 모두 보존한다. 이름만 같고 namespace가 다르면 같은 확장으로 취급하지 않는다. 인지하지 못한 critical 확장을 무시하고 신뢰 통과시키지 않는다. 비critical 미지원 확장도 원문 값을 보존한다.

정지 세부분류는 현재 개념 설계에 값 목록과 판정 규칙이 없으므로 새 enum·URI를 만들지 않는다. 변경 사유는 관리 메타데이터로 보관하고, XML 공개 필드로 자동 전환하지 않는다. 새로운 판정 확장은 후속 프로파일 개정으로 추가한다.

## 발행 필드와 관리 필드

| 관리 필드 | XML 연결 또는 내부 용도 |
|---|---|
| formatVersion | TSLVersionIdentifier. 모델 26의 값 유지 |
| sequenceNumber | TSLSequenceNumber. 별도 정수 필드, JSON 전달 시 십진 문자열로 손실 방지 |
| issuedAt / nextUpdate | ListIssueDateTime / NextUpdate의 dateTime |
| historicalInformationPeriod | 모델 26의 값 유지 |
| operatorNames / schemeNames / territory / informationUris | 대응 SchemeInformation 항목 |
| providerNames / tradeNames / addresses / informationUris | 대응 TSPInformation 항목 |
| serviceTypeUri / names / statusUri / statusStartingTime | 현재·이력의 대응 서비스 정보 |
| current certificates | 현재 ServiceDigitalIdentity의 다중 인증서 |
| history key identifiers | 이력 디지털 ID. 인증서 DER를 이력 XML에 복사하지 않음 |
| providerId / serviceId / draftRevision | 관리 저장소 전용 식별자 |
| issuanceId / requestId / purpose / diagnostics | 발행·실패 복구 메타데이터. XML의 신뢰 주장으로 넣지 않음 |
| profileId / profileVersion / configHash | 발행 시 적용한 프로파일 추적용 내부 값 |

목록 운영자 이름이 바뀌었다고 listId를 바꾸지 않는다. 미정인 다른 TL 포인터는 임의 URL로 채우지 않는다. 스키마가 요구하는 추가 SchemeInformation 항목은 기존 모델 26과 실제 XSD를 기준으로 채운다. 이 표만으로 완전한 XSD 매핑이 끝났다고 판단하지 않는다.

## 구현 확인 기준

유형·상태·critical 확장과 현재·이력의 왕복 변환을 확인한다. 같은 키·Subject의 인증서 추가는 서비스 ID·상태 시작을 유지하고, 새 키는 새 서비스가 된다. 유형·분야·레벨 등 판정 정보 변경 시 과거 구간의 값은 보존한다.

미지원 상태·critical 확장, OCSP/TSA에 잘못 들어간 level, 구형 자료 import를 각각 확인한다. 외부 소비자 프로파일 지원과 전체 XSD·서명 검사는 구현 단계의 통과 조건으로 남는다. 아직 실행한 시험은 없다.
