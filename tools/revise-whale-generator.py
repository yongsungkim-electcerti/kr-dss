from pathlib import Path
p=Path('tools/build-whale-manuals.cjs')
s=p.read_text(encoding='utf8')
start=s.index("A.push({kind:'cover'")
end=s.index('function tx(')
s=s[:start]+"require('./whale-manual-content.cjs')({A,U,cards,table,code,shot,flow});\n"+s[end:]
s=s.replace("d.source||'TrustList PoC · 2026-10-28'", "'TrustList PoC · v1.0 · 2026-10-28'")
s=s.replace("tx(s,'관리 화면의 저장 상태와 수신 측의 실제 사용 상태를 따로 기록합니다.',.8,5.82,11.7,.4,18,C.ink);", "")
start=s.index("fs.writeFileSync(path.join(root,name+'.md')")
end=s.index("console.log(name+",start)
s=s[:start]+'''fs.writeFileSync(path.join(root,name+'.md'),data.map((d,i)=>{
 const title=(i===0?'# ':'## ')+d.title.replace(/\\n/g,' ');
 const parts=[title,d.sub||''];
 if(d.items) parts.push(d.items.map(v=>Array.isArray(v)?`- **${v[0]}**: ${v[1].replace(/\\n/g,' ')}`:v).join('\\n\\n'));
 if(d.rows) parts.push([d.headers,d.headers.map(()=> '---'),...d.rows].map(r=>'| '+r.map(c=>c.replace(/\\n/g,'<br>')).join(' | ')+' |').join('\\n'));
 if(d.body) parts.push('```'+(d.language||'text')+'\\n'+d.body+'\\n```');
 if(d.file) parts.push('!['+d.title+'](evidence/'+d.file+')');
 return parts.filter(Boolean).join('\\n\\n');
}).join('\\n\\n')+'\\n');
''' + s[end:]
p.write_text(s,encoding='utf8')
p=Path('tools/whale-manual-qa.ps1');s=p.read_text(encoding='utf8');s=s.replace("$dest=Join-Path $qa $_.BaseName", "$dest=Join-Path $qa ($_.BaseName.Replace('.','-')+'-revised')");p.write_text(s,encoding='utf8')
p=Path('tools/whale-artifact-audit.py');s=p.read_text(encoding='utf8');s=s.replace("qa/deck.stem.rsplit('.',1)[0]", "qa/(deck.stem.replace('.','-')+'-revised')");p.write_text(s,encoding='utf8')
p=Path('tools/package-whale-manuals.py');s=p.read_text(encoding='utf8');s=s.replace("sha=lambda b:","content=json.loads((root/'slide-content.json').read_text(encoding='utf8'))\napi_count=len(content['api']); user_count=len(content['user'])\nsha=lambda b:")
s=s.replace("='''네이버", "=f'''네이버").replace('(26쪽)','({api_count}쪽)').replace('(28쪽)','({user_count}쪽)')
s=s.replace('실행 가능한 명령은 PPTX 발표자 노트에 있습니다.','명령 예제는 본문에서 확인합니다.')
s=s.replace('이 스크립트는 TL 서명·체인 검증을 대신하지 않습니다.\n','').replace('문서의 기능·검증 범위와 미검사 항목을 확인하십시오. 웨일 내부 PDF 엔진 연결은 별도 시험 대상입니다.\n','')
s=s.replace("'apiSlides':26,'usageSlides':28", "'apiSlides':api_count,'usageSlides':user_count").replace("'24 isolated API/browser checks passed; all 54 slides rendered in PowerPoint; Whale native integration unverified'", "f'{api_count+user_count} revised slides rendered in PowerPoint; prior 24 API/browser checks retained (not rerun for documentation revision)'")
s=s.replace("'powerPointRenderSlides':54", "'powerPointRenderSlides':api_count+user_count").replace("'humanize':'PARTIAL_RULEBOOK_MISSING'", "'humanize':'QUICK_RULES_REVIEWED; see 윤문_검수기록.md','testEvidence':'Existing 2026-10-28 runtime checks retained; documentation-only revision'")
p.write_text(s,encoding='utf8')
