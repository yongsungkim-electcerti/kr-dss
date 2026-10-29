from pathlib import Path
import zipfile,json,hashlib,io

out=Path('outputs')
source=out/'TrustList-PoC-Windows-20261028.zip'
target=out/'Whale_TrustList_PoC_20261028.zip'
docs=Path('docs/guides/poc/tl-management/whale')
prefix='TrustList-PoC-Windows-20261028/'
sha=lambda data:hashlib.sha256(data).hexdigest()
readme='''TrustList PoC · 네이버 웨일 전달본 · 2026-10-28

1. 실행
Windows 10/11, PowerShell 5.1 이상, Java 21 환경에서 실행합니다.
ZIP을 쓰기 가능한 폴더에 전체 해제합니다.
config.json의 javaHome에 JDK 21 설치 경로를 입력합니다.
예: "javaHome": "C:/Java/jdk-21"
JAVA_HOME 또는 PATH에 Java 21이 설정돼 있으면 javaHome을 비워 둡니다.
Start-Poc.cmd를 실행합니다.

관리 시스템: http://localhost:18081/#overview
TL 작업대: http://localhost:18082/tl-poc.html
TL 조회 API: http://localhost:18081/tl/kr-tl.xml
상태 확인: Status-Poc.cmd
종료: Stop-Poc.cmd
포트 변경: config.json의 managementPort / verificationPort

2. 포함 자료
modules/: 관리 시스템·TL 검증 모듈 JAR 2개
manuals/: API 연동 가이드 12쪽, 사용 매뉴얼 17쪽 (PPTX)
manuals/examples/: 공개 TL API 시험 스크립트
samples/: TL XML 2개, signed.pdf, 인증서, TL 발행용 키·체인
data/: 사업자 4개·서비스 10개의 초기 자료
Start/Status/Stop-Poc.cmd 및 .ps1: 실행·상태·종료 스크립트

3. 시험 순서
사용 매뉴얼의 샘플 TL 비교부터 진행합니다.
tl-1.xml을 적용하고 signed.pdf를 검증합니다.
tl-2.xml을 적용해 같은 PDF·평가시각으로 다시 검증하고 결과를 비교합니다.
평가시각: 2026-10-27T00:00:00Z

관리 시스템에서 직접 TL을 발행하는 시험은 기존 환경을 종료한 후
ZIP을 새 폴더에 풀어 진행합니다. CA 등록·발행 시각은 사용 매뉴얼을 따릅니다.
공개 TL API는 관리 화면에서 TL을 발행하고 제공본으로 선택한 뒤 조회합니다.
웨일의 수동 등록과 주기적 API 갱신은 API 연동 가이드를 참고합니다.

4. 파일 확인
SHA256SUMS.txt와 package-manifest.json에 파일별 해시를 제공합니다.
Java는 별도 설치합니다. 실행 모듈에 라이브러리가 포함돼 있어 빌드 도구는 필요하지 않습니다.
이번 문서 산출물은 PPTX입니다.
'''
mail='''제목: [TrustList PoC] 웨일 연동 시험용 모듈 및 가이드 전달

안녕하세요.

웨일의 신뢰목록 연동 시험에 사용할 TrustList PoC 패키지를 전달드립니다.

첨부 파일: Whale_TrustList_PoC_20261028.zip

패키지에는 다음 자료가 포함되어 있습니다.

- TrustList 관리 시스템 및 TL 검증 모듈
- Windows 실행·종료 스크립트
- 시험용 TL XML, 서명 PDF, 인증서 및 초기 사업자·서비스 자료
- API 연동 가이드와 관리 시스템·TL 작업대 사용 매뉴얼(PPTX)

Windows와 Java 21 환경에서 ZIP을 해제한 후, config.json에 Java 경로를 설정하고 Start-Poc.cmd를 실행합니다. 상세 절차는 패키지의 README.txt와 사용 매뉴얼에 정리했습니다.

관리 시스템은 http://localhost:18081/, TL 작업대는 http://localhost:18082/tl-poc.html에서 접속합니다.

TL 작업대에서는 동일한 PDF와 평가시각에 대해 신뢰목록의 인정·정지 상태 변경에 따른 검증 결과를 비교합니다. 관리 시스템에서 직접 TL을 발행하고 공개 API로 수신하는 시험도 진행할 수 있습니다.

웨일은 TL XML을 다운로드해 파일로 등록하거나, API를 주기적으로 호출해 갱신하도록 연동합니다. 등록 시 배포 버전을 확인해 최신 신뢰목록을 적용하는 절차와 API 예제는 연동 가이드에 담았습니다.

검토 후 연동 과정에서 필요한 사항이나 문의를 전달해 주시면 확인하겠습니다.

감사합니다.
'''
(docs/'Whale_전달_메일초안.md').write_text(mail,encoding='utf8')
(out/'Whale_전달_메일초안.md').write_text(mail,encoding='utf8')
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    entries={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
original=entries.copy()
entries[prefix+'README.txt']=readme.encode('utf-8-sig')
manifest=json.loads(entries[prefix+'package-manifest.json'].decode('utf-8-sig'))
manifest['delivery']={'recipient':'Naver Whale developers','date':'2026-10-28','documents':'PPTX only'}
records=[{'path':n[len(prefix):],'bytes':len(b),'sha256':sha(b)} for n,b in entries.items() if n not in (prefix+'package-manifest.json',prefix+'SHA256SUMS.txt')]
manifest['files']=records
entries[prefix+'package-manifest.json']=json.dumps(manifest,ensure_ascii=False,indent=2).encode('utf-8-sig')
entries[prefix+'SHA256SUMS.txt']=(''.join(f"{r['sha256']}  {r['path']}\n" for r in records)).encode('utf8')
assert json.loads(entries[prefix+'config.json'].decode('utf-8-sig'))['javaHome']==''
assert len([n for n in entries if n.endswith('.pptx')])==2
assert not any(n.startswith(prefix+'manuals/') and n.endswith('.pdf') for n in entries)
assert not any('/logs/' in n or '/run/' in n or '~$' in n for n in entries)
for name,data in entries.items():
    if name.endswith('.jar'):
        with zipfile.ZipFile(io.BytesIO(data)) as jar: assert jar.testzip() is None
    if name not in (prefix+'README.txt',prefix+'package-manifest.json',prefix+'SHA256SUMS.txt'):
        assert data==original[name]
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for name,data in entries.items(): z.writestr(name,data)
with zipfile.ZipFile(target) as z:
    assert z.testzip() is None
    for r in records: assert sha(z.read(prefix+r['path']))==r['sha256']
target.with_name(target.name+'.sha256').write_text(sha(target.read_bytes())+'  '+target.name+'\n',encoding='ascii')
report={'zip':target.name,'bytes':target.stat().st_size,'files':len(entries),'sha256':sha(target.read_bytes()),'checks':{'zipCRC':'PASSED','allFileHashes':'PASSED','jarIntegrity':'PASSED','portableJavaConfig':'PASSED','twoPptx':'PASSED','runtimePayloadUnchanged':'PASSED','noRuntimeLogs':'PASSED'},'runtimeTest':'Prior evidence retained; no runtime restart for packaging'}
(docs/'evidence/handoff-manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(report,ensure_ascii=True,indent=2))
