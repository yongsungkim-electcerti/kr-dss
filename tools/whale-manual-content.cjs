module.exports=function({A,U,cards,table,code,shot,flow}){
A.push({kind:'cover',title:'TrustList\nAPI 연동 가이드',sub:'네이버 웨일 개발자용',items:['신뢰목록 수신 · 버전 확인 · 등록','v1.0 · 2026.10.28']});
table(A,'목차',['구성','내용'],[
['1. API 구성','접속 주소, XML·해시 조회 API'],['2. 신뢰목록 수용','수동 등록과 주기적 원격 등록'],['3. TL 조회·갱신','요청 헤더, ETag, 200·304 처리'],['4. TL 데이터','발행정보, 사업자·서비스, 인증서·상태 이력'],['5. 등록 조건','서명, 신뢰앵커, 버전, 갱신 기한'],['6. 연동 예제','파일 다운로드와 주기적 갱신 구현'],['7. 응답·오류 코드','HTTP 상태와 오류 본문']]);
table(A,'API 구성',['항목','내용'],[
['기본 주소','http://localhost:18081'],['TL XML','GET /tl/kr-tl.xml\n서명된 신뢰목록 XML 원문을 반환합니다.'],['TL 해시','GET /tl/kr-tl.sha2\nXML 원문의 SHA-256을 바이너리 32바이트로 반환합니다.'],['헤더 조회','두 경로 모두 HEAD를 지원합니다.'],['인증','별도 인증 헤더 없이 호출합니다.']], '웨일은 공개 TL API에서 신뢰목록을 수신합니다.');
cards(A,'신뢰목록 수용',[
['수동 등록','TL XML을 다운로드해 웨일에 파일로 등록합니다.'],['원격 등록','웨일이 API를 주기적으로 호출해 새 TL을 수신하고 등록합니다.'],['버전 확인','등록 시 TSLSequenceNumber를 현재 버전과 비교합니다. 등록 조건을 통과한 최신 버전을 적용합니다.']], 'ETag로 변경 여부를 확인하고, XML의 TSLSequenceNumber로 배포 버전을 비교합니다.');
table(A,'TL 조회·갱신',['구분','계약'],[
['Accept','application/vnd.etsi.tsl+xml 또는 */*'],['200 OK','Content-Type: application/vnd.etsi.tsl+xml\n본문: 서명된 XML 원문'],['ETag','"sha256-<64자리 16진수>"'],['조건부 요청','If-None-Match에 저장한 ETag를 따옴표까지 전달합니다.'],['304 Not Modified','본문 없이 응답합니다. 현재 TL을 유지하고 NextUpdate를 확인합니다.'],['Cache-Control','no-cache, no-transform']], '첫 요청은 XML과 ETag를 받고, 이후 요청은 저장한 ETag로 변경 여부를 확인합니다.');
table(A,'TL 데이터',['구조','필드','사용'],[
['SchemeInformation','TSLSequenceNumber\nListIssueDateTime / NextUpdate','배포 버전, 발행 시각, 갱신 기한을 확인합니다.'],['TrustServiceProviderList','사업자 및 서비스 목록','검증 대상 인증서에 해당하는 서비스를 찾습니다.'],['ServiceInformation','서비스 유형·인증서·상태·효력 시각','인증 경로를 연결하고 평가시각의 상태를 판단합니다.'],['ServiceHistory','이전 상태와 효력 시각','평가시각에 유효한 상태 구간을 선택합니다.'],['ds:Signature','XML 서명·서명 인증서','신뢰앵커를 기준으로 TL 서명을 검증합니다.']], '웨일은 TL에 등재된 인증서와 상태 이력을 PDF 서명 검증에 사용합니다.','D1 §3·7 / D4 §3');
table(A,'PoC 데이터 값',['항목','값'],[
['URI 접두사','urn:example:kr-tl:poc:v1:'],['서비스 유형','접두사 + service:ca / service:ocsp / service:tsa'],['서비스 상태','접두사 + status:accredited / status:suspended / status:withdrawn'],['확장 네임스페이스','urn:example:kr-tl:poc:extensions:v1'],['TrustDomain','joint (공동인증) / simple (간편인증)'],['TrustPointLevel','0 (ROOT) / 1 (CA)']], '이 배포본에 사용하는 서비스 유형·상태·확장 값입니다.');
table(A,'등록 조건',['검사','처리'],[
['서명·인증 경로','samples/tl-root.pem을 신뢰앵커로 TL 서명과 인증 경로를 검증합니다.'],['배포 버전','현재보다 높은 TSLSequenceNumber와 늦은 발행 시각을 확인합니다.'],['동일 버전','동일 원문이면 유지합니다. 원문이 다르면 충돌로 처리합니다.'],['이전 버전','현재보다 낮은 버전은 등록을 거부합니다.'],['갱신 기한','NextUpdate가 현재 시각 이후인지 확인합니다.'],['확장·상태 이력','critical 확장과 상태 이력을 해석합니다. 미지원 critical 확장은 거부합니다.']], '검사를 통과하면 XML 원문과 ETag를 함께 저장하고 검증에 적용합니다.');
code(A,'파일 다운로드',String.raw`$base = 'http://localhost:18081'
curl.exe --fail-with-body -D tl-headers.txt `+'`'+String.raw`
  -H 'Accept: application/vnd.etsi.tsl+xml' `+'`'+String.raw`
  -o received-tl.xml "$base/tl/kr-tl.xml"

curl.exe --fail-with-body `+'`'+String.raw`
  -o received-tl.sha2 "$base/tl/kr-tl.sha2"

$bytes = [IO.File]::ReadAllBytes(
  (Resolve-Path ./received-tl.sha2).Path)
[BitConverter]::ToString($bytes).Replace('-','')
(Get-FileHash ./received-tl.xml -Algorithm SHA256).Hash`,[
['PowerShell','줄 끝의 백틱(`)을 포함해 실행합니다.'],['해시 비교','출력한 두 해시를 비교합니다. 다운로드 중 TL이 변경돼 불일치하면 다시 수신합니다.']], 'XML, 응답 헤더, 해시 파일을 현재 폴더에 저장합니다.');
A[A.length-1].language='powershell';
code(A,'주기적 갱신',`async function refreshTl(url, current, acceptTl, saveTl) {
  const headers = { Accept: 'application/vnd.etsi.tsl+xml' };
  if (current?.etag) headers['If-None-Match'] = current.etag;
  const r = await fetch(url, {
    headers, credentials: 'omit', cache: 'no-cache'
  });
  if (r.status === 304) return current;
  if (!r.ok) throw new Error('TL HTTP ' + r.status);
  const xml = new Uint8Array(await r.arrayBuffer());
  const accepted = await acceptTl(xml, current);
  const next = { ...accepted, xml, etag: r.headers.get('ETag') };
  await saveTl(next);
  return next;
}`, [['호출','웨일의 스케줄러에서 주기적으로 호출합니다. 첫 호출의 current는 null입니다.'],['검증·저장','acceptTl은 등록 조건을 검사하고 실패 시 예외를 던집니다. saveTl은 검사 결과와 원문을 저장합니다.']], 'acceptTl·saveTl은 웨일의 검증·저장 함수로 구현합니다. 호출 시 현재 TL의 NextUpdate를 확인합니다.');
A[A.length-1].language='javascript';
table(A,'브라우저 호출',['항목','설정'],[
['CORS','Access-Control-Allow-Origin: *'],['자격 증명','credentials: omit'],['메서드','GET, HEAD'],['조건부 요청 헤더','If-None-Match'],['노출 응답 헤더','ETag'],['웨일 확장 기능','호출 주소에 대한 확장 권한을 설정합니다.']], '웹 페이지에서 호출할 때는 실행 환경의 혼합 콘텐츠 정책을 확인합니다.');
table(A,'응답·오류 코드',['HTTP','본문','의미'],[
['200','XML / 바이너리 해시','요청한 TL 또는 해시를 반환합니다.'],['304','없음','ETag가 일치합니다.'],['404','TL_NOT_PUBLISHED','관리 시스템에서 제공본을 선택해야 합니다.'],['406','NOT_ACCEPTABLE','Accept를 TSL 미디어 유형 또는 */*로 지정합니다.'],['405','METHOD_NOT_ALLOWED','GET 또는 HEAD로 호출합니다.'],['503','TL_UNAVAILABLE','저장된 발행본을 제공할 수 없습니다.'],['500','INTERNAL_ERROR','서버 내부 오류입니다.']], '오류 본문은 평문입니다.');

U.push({kind:'cover',title:'TrustList 관리 시스템\nTL 작업대 사용 매뉴얼',sub:'네이버 웨일 개발자용',items:['샘플 TL 비교 · 직접 발행 · API 수신 시험','v1.0 · 2026.10.28']});
table(U,'목차',['구성','내용'],[
['1. 실행 환경','Java 설정, 시작·종료, 접속 주소'],['2. 화면 구성','관리 시스템과 TL 작업대'],['3. 샘플 TL 비교','TL 1 → PDF 검증 → TL 2 → 같은 조건으로 비교'],['4. 관리 TL 발행','CA 등록 → 발행·제공 → 다운로드 → 상태 변경·재검증'],['5. 문제 해결','실행 오류와 TL 등록 오류']]);
table(U,'실행 환경',['항목','설정·명령'],[
['배포 폴더','TrustList-PoC-Windows-20261028'],['Java','Java 21 · config.json의 javaHome에 JDK 루트를 지정합니다.\n예: "javaHome": "C:/Java/jdk-21"'],['시작·상태·종료','Start-Poc.cmd / Status-Poc.cmd / Stop-Poc.cmd'],['관리 시스템','http://localhost:18081/#overview'],['TL 작업대','http://localhost:18082/tl-poc.html']], '배포 폴더에서 Start-Poc.cmd를 실행합니다. 접속 포트는 config.json에서 변경합니다.');
shot(U,'관리 시스템','01-overview.png',[
['운영 현황','초기 자료는 사업자 4개, 서비스 10개입니다. CA 4개·OCSP 3개·TSA 3개로 구성합니다.'],['사업자·서비스','서비스 인증서를 등록하고 인정·정지 상태를 변경합니다.'],['TL 발행','초안을 검사하고 서명·발행한 뒤 공개 API로 제공할 TL을 선택합니다.']]);
table(U,'시험 자료',['파일·입력','내용'],[
['samples/tl-1.xml','순번 1 · 시험 CA 인정'],['samples/tl-2.xml','순번 2 · 시험 CA 정지'],['samples/signed.pdf','두 TL로 검증할 동일한 PDF'],['samples/tl-root.pem','TL 서명 검증용 신뢰앵커 · 시작 스크립트에서 설정합니다.'],['samples/service-ca.pem','관리 시스템에 등록할 PDF용 CA 인증서'],['평가시각','2026-10-27T00:00:00Z']], '샘플 TL 비교는 새 배포 폴더에서 순번 1부터 진행합니다.');
shot(U,'샘플 TL 등록','05-inspect-tl1.png',[
['파일 선택','01 TL 적용에서 samples/tl-1.xml을 선택합니다.'],['내용 확인·검사','발행정보, 사업자·서비스, 인증서와 인정 상태를 확인합니다.'],['TL 적용','확인한 TL 적용을 누릅니다. 현재 적용 TL 읽기로 등록된 구조를 조회합니다.']], '샘플 TL 비교 · 1/4');
shot(U,'PDF 검증','07-verify-tl1.png',[
['PDF','02 서명 검증에서 samples/signed.pdf를 선택합니다.'],['평가시각','2026-10-27T00:00:00Z를 입력합니다. 추가 인증서는 비워 둡니다.'],['실행','실제 검증 실행·저장을 누릅니다. 서비스 인정 상태와 TL 신뢰 조건 충족을 확인합니다.']], '샘플 TL 비교 · 2/4');
shot(U,'TL 변경·재검증','08-tl2-diff.png',[
['변경 확인','samples/tl-2.xml을 선택하고 내용 확인·검사를 누릅니다. 서비스 상태와 이력의 변경을 확인합니다.'],['TL 적용','확인한 TL 적용을 누르고 현재 순번 2를 확인합니다.'],['재검증','같은 signed.pdf와 평가시각으로 실제 검증 실행·저장을 다시 누릅니다.']], '샘플 TL 비교 · 3/4');
shot(U,'검증 결과 비교','10-compare.png',[
['실행 선택','03 결과 비교에서 TL 1 실행과 TL 2 실행을 선택하고 결과 비교를 누릅니다.'],['상태 변화','인정 → 정지\nTL 신뢰 조건 충족 → 불충족으로 바뀝니다.'],['유지되는 결과','두 실행 모두 문서 무결성은 PASSED, 인증 경로는 VALID입니다.']], '샘플 TL 비교 · 4/4 · 같은 PDF·평가시각에서 TL 변경에 따른 판단 차이를 확인합니다.');
table(U,'관리 TL 시험 입력값',['항목','입력값'],[
['시작 환경','기존 환경을 Stop-Poc.cmd로 종료합니다. ZIP을 새 폴더에 풀고 시작합니다.'],['CA 서비스','Whale PDF 시험 CA / CA / 공동인증 / 0 (RootCA)'],['인정 시작','2026-10-26 09:00:00 KST'],['첫 발행','정상(BASELINE) / 순번 1 / 2026-10-26 21:00:00 KST'],['정지 효력·평가시각','2026-10-27 09:00:00 KST = 2026-10-27T00:00:00Z'],['둘째 발행','정상(BASELINE) / 순번 2 / 2026-10-27 21:00:00 KST'],['NextUpdate','두 발행 모두 시험 실행 시각 이후로 지정합니다.']], '관리 화면에는 KST를, 작업대 평가시각에는 UTC를 입력합니다.');
shot(U,'CA 서비스 등록','11-service-register.png',[
['사업자 선택','사업자·서비스에서 기존 사업자를 선택하고 서비스 등록을 누릅니다.'],['서비스 입력','앞의 시험 입력값에 따라 CA 서비스와 인정 시작 시각을 입력합니다.'],['인증서 등록','samples/service-ca.pem을 등록하고 저장합니다. 서비스 수는 11개가 됩니다.']], '관리 TL 발행 · 1/5');
shot(U,'TL 서명·발행','03-issuance-before.png',[
['발행 설정','TL 발행에서 정상(BASELINE), 순번 1과 첫 발행 시각을 입력합니다.'],['사전 검사','NextUpdate를 시험 실행 이후로 지정하고 사전 검사를 누릅니다.'],['발행','서명 키 준비 상태와 검사 결과를 확인한 뒤 서명·발행을 누릅니다.']], '관리 TL 발행 · 2/5');
shot(U,'TL 제공·다운로드','04-published.png',[
['제공본 선택','발행본 목록에서 이번 발행의 제공본으로를 누릅니다.'],['다운로드','다음 쪽의 명령으로 공개 API의 XML을 저장합니다.'],['검증','작업대에 파일을 적용하고 signed.pdf를 고정 평가시각으로 검증합니다.']], '관리 TL 발행 · 3/5');
U[U.length-1].body='curl.exe --fail-with-body -o management-tl.xml http://localhost:18081/tl/kr-tl.xml';
U[U.length-1].language='powershell';
// The download command is displayed on the following compact instruction slide too.
code(U,'공개 TL 수신',String.raw`curl.exe --fail-with-body `+'`'+String.raw`
  -o management-tl.xml `+'`'+String.raw`
  http://localhost:18081/tl/kr-tl.xml

# TL 작업대
# 01 TL 적용 → management-tl.xml 선택
# → 내용 확인·검사 → 확인한 TL 적용
# 02 서명 검증 → samples/signed.pdf
# 평가시각: 2026-10-27T00:00:00Z
# → 실제 검증 실행·저장`,[['실행 위치','배포 폴더에서 PowerShell로 실행합니다.'],['웨일 연동','웨일의 원격 수신 URL에도 같은 /tl/kr-tl.xml 주소를 설정합니다.']], '다운로드한 TL을 작업대와 웨일의 검증에 사용합니다.');
U[U.length-1].language='powershell';
shot(U,'서비스 정지·재발행','12-status-change.png',[
['상태 변경','Whale PDF 시험 CA에서 상태 변경을 누르고 정지(SUSPENDED)를 선택합니다.'],['효력 시각','2026-10-27 09:00:00 KST를 입력합니다. 사유는 동일 PDF의 TL 상태 변경 비교로 입력합니다.'],['재발행·제공','시험 입력값의 둘째 발행 시각으로 순번 2를 발행하고 제공본으로 선택합니다.']], '관리 TL 발행 · 4/5');
shot(U,'관리 TL 결과 비교','13-management-compare.png',[
['재수신·적용','공개 TL을 다시 다운로드하고 작업대에 적용합니다.'],['재검증','같은 signed.pdf와 2026-10-27T00:00:00Z로 검증합니다.'],['결과 비교','순번 1과 2의 실행을 비교합니다. 인정 → 정지, TL 신뢰 조건 충족 → 불충족을 확인합니다.']], '관리 TL 발행 · 5/5');
table(U,'문제 해결',['증상·코드','조치'],[
['Java 21 is required','config.json의 javaHome에 JDK 21 루트를 지정합니다.'],['management is already running','Status-Poc.cmd로 확인합니다. 재시작은 Stop-Poc.cmd → Start-Poc.cmd 순서로 실행합니다.'],['공개 URL 404','TL을 발행하고 제공본으로 선택합니다.'],['TL_ROLLBACK / TL_CONFLICT','새 배포 폴더에서 순번 1부터 진행합니다. 샘플 TL과 관리 TL 시험은 별도 폴더를 사용합니다.'],['TL_EXPIRED','NextUpdate를 시험 실행 이후로 설정해 새 TL을 발행합니다.'],['TL_CHAIN_UNTRUSTED','TL과 신뢰앵커(samples/tl-root.pem)의 인증서 구성을 확인합니다.']], '파일 검사 후 확인한 TL 적용까지 완료하면 현재 적용 TL을 조회할 수 있습니다.');
};
