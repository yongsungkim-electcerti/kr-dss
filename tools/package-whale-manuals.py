from pathlib import Path
import hashlib,json,zipfile,shutil,os,datetime
root=Path('docs/guides/poc/tl-management/whale')
package=Path('outputs/TrustList-PoC-Windows-20261028')
archive=package.with_suffix('.zip')
content=json.loads((root/'slide-content.json').read_text(encoding='utf8'))
api_count=len(content['api']); user_count=len(content['user'])
sha=lambda b:hashlib.sha256(b).hexdigest()
files=list(root.glob('Whale_*.pptx'))+list((root/'examples').glob('*.ps1'))
assert len(files)==3,len(files)
manuals={}
for f in files:
    rel='manuals/'+('examples/' if f.parent.name=='examples' else '')+f.name
    manuals[rel]=f.read_bytes()
    target=package/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(manuals[rel])
manuals['manuals/README.txt']=f'''네이버 웨일 개발자용 TrustList PoC 매뉴얼 · 2026-10-28
1. Whale_API_연동_가이드_v1.0.pptx ({api_count}쪽)
2. Whale_관리시스템_TL작업대_사용매뉴얼_v1.0.pptx ({user_count}쪽)
이번 개정본은 PPTX입니다. PDF는 추후 작성합니다. 명령 예제는 본문에서 확인합니다.
examples/Test-TlPublicApi.ps1은 공개 XML/32바이트 해시/ETag 계약 시험용입니다.
발행·제공 후 패키지 루트에서 다음과 같이 실행합니다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./manuals/examples/Test-TlPublicApi.ps1
과정 A와 과정 B는 새로 해제한 별도 환경에서 진행하고, 과정 B의 시각 입력표를 따르십시오.
'''.encode('utf-8-sig')
(package/'manuals/README.txt').write_bytes(manuals['manuals/README.txt'])
with zipfile.ZipFile(archive) as z:
    entries={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
prefix=next(k.rsplit('config.json',1)[0] for k in entries if k.endswith('/config.json'))
before={k:sha(v) for k,v in entries.items()}
# Exclude earlier PDF drafts from the revised distribution.
entries={k:v for k,v in entries.items() if not (k.startswith(prefix+'manuals/') and k.endswith('.pdf'))}
for rel,data in manuals.items():entries[prefix+rel]=data
manifest=json.loads(entries[prefix+'package-manifest.json'].decode('utf-8-sig'))
records=[{'path':k[len(prefix):],'bytes':len(v),'sha256':sha(v)} for k,v in entries.items() if k not in [prefix+'package-manifest.json',prefix+'SHA256SUMS.txt']]
manifest['files']=records
manifest['manuals']={'version':'1.0','date':'2026-10-28','apiSlides':api_count,'usageSlides':user_count,'validation':f'{api_count+user_count} revised slides rendered in PowerPoint; prior 24 API/browser checks retained (not rerun for documentation revision)'}
entries[prefix+'package-manifest.json']=json.dumps(manifest,ensure_ascii=False,indent=2).encode('utf-8-sig')
entries[prefix+'SHA256SUMS.txt']=('\n'.join(f"{r['sha256']}  {r['path']}" for r in records)+'\n').encode('utf-8')
assert json.loads(entries[prefix+'config.json'].decode('utf-8-sig'))['javaHome']=='', 'ZIP must keep portable Java config'
assert not any('/logs/' in k or '/run/' in k for k in entries), 'Do not package live runtime files'
tmp=archive.with_name(archive.stem+'.manuals.tmp.zip')
with zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for k,v in entries.items():z.writestr(k,v)
with zipfile.ZipFile(tmp) as z:
    assert z.testzip() is None
    for k,h in before.items():
        if k not in [prefix+'package-manifest.json',prefix+'SHA256SUMS.txt'] and not k.startswith(prefix+'manuals/'):
            assert sha(z.read(k))==h,k
os.replace(tmp,archive)
archive.with_name(archive.name+'.sha256').write_text(f'{sha(archive.read_bytes())}  {archive.name}\n',encoding='ascii')
# Local runtime config/data may differ from the portable archive. Update only its manual records.
lm=package/'package-manifest.json';local=json.loads(lm.read_text(encoding='utf-8-sig'))
local['files']=[r for r in local['files'] if not r['path'].startswith('manuals/')]+[{'path':k,'bytes':len(v),'sha256':sha(v)} for k,v in manuals.items()]
local['manuals']=manifest['manuals'];lm.write_text(json.dumps(local,ensure_ascii=False,indent=2),encoding='utf-8-sig')
(package/'SHA256SUMS.txt').write_text('\n'.join(f"{r['sha256']}  {r['path']}" for r in local['files'])+'\n',encoding='utf8')
artifact={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'pptxValidation':'PASSED','powerPointRenderSlides':api_count+user_count,'apiChecks':15,'managementChecks':9,'exampleScript':'HTTP 200 / raw32 / hash match / 304 verified','humanize':'QUICK_RULES_REVIEWED; see 윤문_검수기록.md','testEvidence':'Existing 2026-10-28 runtime checks retained; documentation-only revision','whaleNative':'NOT_VERIFIED','files':[{'name':f.name,'bytes':f.stat().st_size,'sha256':sha(f.read_bytes())} for f in files],'distributionZip':{'name':archive.name,'sha256':sha(archive.read_bytes()),'originalPayloadPreserved':True,'portableJavaConfig':True}}
(root/'evidence/artifact-manifest.json').write_text(json.dumps(artifact,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(artifact,ensure_ascii=False,indent=2))
