TrustList PoC — Windows 로컬 배포본

요구 환경: Windows 10/11, Windows PowerShell 5.1 이상, Java 21.
Java는 포함하지 않습니다. JAVA_HOME 또는 config.json의 javaHome을 지정하거나 java.exe를 PATH에 둡니다.
소스·Gradle·Maven·사업자 서버는 필요하지 않습니다. 관리/검증 JAR의 의존성은 각 JAR 내부에 포함됩니다.

현재 상태: 배포 구성 완료 / 배포본 직접 실행·검증은 사용자 수행 대기.
상세 관리 매뉴얼·API 연계 매뉴얼은 사용자 실행 확인 후 별도 작성 예정입니다.

1. ZIP을 쓰기 가능한 폴더에 전체 해제합니다. 경로에 큰따옴표(")를 사용하지 않습니다.
2. Start-Poc.cmd를 실행합니다. 또는 PowerShell에서:
   powershell -NoProfile -ExecutionPolicy Bypass -File .\Start-Poc.ps1
3. 관리 화면: http://localhost:18081/
   TL/PDF 검증 작업대: http://localhost:18082/tl-poc.html
4. 상태 확인: Status-Poc.cmd / 종료: Stop-Poc.cmd
   종료는 이 패키지가 시작하고 식별한 Java 프로세스만 대상으로 합니다.
   실행 중에는 패키지 폴더를 이동하지 마십시오. 이동 전 Stop-Poc.cmd를 실행합니다.

포트가 사용 중이면 다른 서버를 종료하지 않고 시작을 중지합니다. config.json의 두 포트를 서로 다르게 변경합니다.
서버는 127.0.0.1에만 바인딩합니다. 외부 접속·인증/권한·운영 배포 설정은 제공하지 않습니다.
시작 중 오류가 발생하면 이번 시작에서 실행한 서비스만 종료합니다. logs/의 최근 파일을 확인합니다.

modules/: 실행 JAR 2개. 기본 PoC 의존성과 데모 리소스 포함.
samples/: 이번 패키지 전용으로 새로 생성한 시험 자료.
  tl-1.xml: 인정, tl-2.xml: 정지. signed.pdf: 동일 PDF 비교 표본.
  평가시각은 sample-info.json의 evaluationTime을 그대로 사용합니다.
  TL은 생성 후 약 180일간 유효합니다. 만료 후에는 새 자료가 필요합니다.
  tl-root.pem: TL 검증 앵커. service-ca.pem: 관리 화면에 등록 가능한 시험 CA 인증서.
  signer/key.pem, signer/chain.pem: 관리 프로그램의 TL 발행용 시험 전용 키·체인.
  이 키는 전달받는 누구나 사용할 수 있는 공개 시연용 비밀키입니다. 운영·실제 업무에 사용하지 마십시오.
  PDF 서명 개인키와 루트 개인키는 내보내지 않습니다. 기존 사용자 키·기존 실행 로그는 포함하지 않습니다.
data/: 사업자 4개·서비스 10개(CA 4, OCSP 3, TSA 3)를 담은 관리 초기 초안이 포함됩니다.
  main 개발 초안 r15의 사업자·서비스·공개 인증서를 초기 revision 1로 구성했습니다.
  과거 저장 이력·확정 발행본·제공본·개인키·검증 실행 결과는 가져오지 않습니다.
logs/: 날짜별 로그. run/: 이 패키지의 프로세스 식별 정보.

관리 프로그램에서 발행한 TL과 검증 작업대는 자동 연동하지 않습니다.
관리 화면에서 서명 XML을 내려받아 검증 작업대의 내용 확인·검사 → 확인한 TL 적용 순서로 사용합니다.
제공한 tl-1.xml/tl-2.xml은 관리 초안/발행 이력에 자동 등록되지 않습니다.
관리 초기 사업자 자료와 samples/signed.pdf는 서로 다른 시험 데이터입니다.
해당 PDF를 관리 프로그램에서 발행한 TL로 검증하려면 samples/service-ca.pem을 서비스 신뢰점으로 별도 등록해야 합니다.
같은 저장소에서 TL #2 적용 후 #1은 역행으로 거부됩니다. 재시연은 기존 data/verification을 보존한 채
패키지 ZIP을 다른 폴더에 새로 해제하여 시작할 수 있습니다(기존 인스턴스는 먼저 종료).

백업: Stop-Poc.cmd로 종료 후 data/, config.json, samples/를 함께 보존합니다.
교체·복구: 이전 ZIP과 해당 시점 데이터 백업을 별도 폴더에서 사용합니다. 자동 삭제/초기화 기능은 없습니다.
파일 무결성: SHA256SUMS.txt 및 package-manifest.json 참조. 재압축 파일의 SHA-256은 ZIP 옆 .sha256 파일에 있습니다.
