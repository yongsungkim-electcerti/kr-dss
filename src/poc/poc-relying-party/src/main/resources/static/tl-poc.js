/* 실제 API 응답만 표시한다. 예시 결과를 실행 결과로 대체하지 않는다. */
'use strict';
const $ = id => document.getElementById(id);
const labels = {PASSED:'통과',SIGNATURE_INVALID:'서명값 불일치',VALID:'정상',CHAIN_INVALID:'인증서 서명 관계·경로 오류',CHAIN_INCOMPLETE:'인증 경로 자료 부족',NOT_IN_TL:'TL 미등재',NO_VALID_TL:'사용 가능한 TL 없음',TRUST_CONDITIONS_MET:'TL 신뢰 조건 충족',TRUST_CONDITIONS_NOT_MET:'TL 신뢰 조건 불충족',ACCREDITED:'인정',SUSPENDED:'정지',WITHDRAWN:'철회',NOT_CHECKED:'미검증',NOT_ESTABLISHED:'신뢰 미확립',COMPLETED:'실행 완료',ERROR:'실행 오류',DOCUMENT_COVERAGE_INVALID:'전체 문서 서명 범위 불일치'};
const label = value => labels[value] || value || '—';
const esc = value => String(value ?? '—').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const documents=new Map();
const typeLabel=value=>value?.endsWith(':ca')?'인증기관(CA)':value?.endsWith(':ocsp')?'인증서 상태 확인(OCSP)':value?.endsWith(':tsa')?'시각확인(TSA)':value;
function xmlButton(id,element,text,path=''){return '<button type="button" data-xml="'+id+'" data-element="'+element+'" data-path="'+esc(path)+'">'+text+'</button>';}
function renderDocument(id,doc,download,state){
    documents.set(id,doc);if(!doc){$(id).textContent='표시할 TL이 없습니다. XML을 해석하지 못한 경우에도 적용하지 않습니다.';return;}
    let html='<p class="notice">'+esc(state)+'</p><div class="tl-summary"><span class="badge">TL #'+esc(doc.sequence)+'</span><span class="badge">사업자 '+doc.providerCount+'곳</span><span class="badge">서비스 '+doc.services.length+'개</span></div>';
    html+=details([['목록 운영기관',doc.operator],['발행시각(UTC)',doc.issuedAt],['다음 갱신시각(UTC)',doc.nextUpdate]]);
    html+='<p class="explain">목록 운영기관은 이 TL의 발행 주체입니다. 다음 갱신시각이 지나면 이 파일을 정상 신뢰 근거로 사용할 수 없습니다.</p>';
    html+='<div class="toolbar">'+xmlButton(id,'TSLSequenceNumber','발행 번호의 XML')+xmlButton(id,'NextUpdate','갱신시각의 XML')+'</div>';
    html+='<details><summary>TL 파일을 보증하는 서명 정보</summary><p>아래는 XML에 포함된 서명 인증서입니다. 사업자 CA 신뢰목록과 별개이며, 실제 신뢰 여부는 서버의 별도 앵커 검사 결과를 따릅니다.</p>';
    for(const cert of doc.signingCertificates)html+=details([['주체',cert.subject],['발급자',cert.issuer]]);
    html+='</details><h3>목록 운영기관 → 사업자 → 서비스</h3>';
    const providers=new Map();for(const service of doc.services){if(!providers.has(service.provider))providers.set(service.provider,[]);providers.get(service.provider).push(service);}
    for(const [provider,services]of providers){html+='<h3>'+esc(provider)+'</h3>';for(const service of services){
        html+='<article class="service"><h3>'+esc(service.name)+' <span class="badge">'+esc(service.status)+'</span></h3>';
        html+=details([['서비스 유형',typeLabel(service.type)],['현재 상태',service.status],['효력 시작(UTC)',service.since]]);
        html+='<p class="explain">상태는 이 서비스의 인정 여부를 뜻합니다. TL 파일 서명이 정상이라는 사실과 서비스의 인정 상태는 별개입니다.</p>';
        html+='<div class="toolbar">'+xmlButton(id,'ServiceStatus','상태의 XML',service.xmlPath)+xmlButton(id,'StatusStartingTime','효력시각의 XML',service.xmlPath)+xmlButton(id,'ServiceDigitalIdentity','인증서의 XML',service.xmlPath)+'</div>';
        html+='<details open><summary>상태 이력 · 실제 XML 구간</summary>'+(service.timeline.length<2?'<p class="explain">과거 이력 없음. 현재 구간만 기록되어 있습니다.</p>':'')+'<ol class="timeline">';
        for(const period of service.timeline)html+='<li><strong>'+esc(period.status)+'</strong> · '+esc(period.from)+' → '+esc(period.until||'종료시각 미지정')+'</li>';
        html+='</ol><p class="explain">각 구간은 시작시각을 포함하고 다음 구간 시작시각은 포함하지 않습니다. 복원 후에도 기록된 과거 정지 구간은 유지됩니다.</p></details>';
        html+='<details><summary>신뢰점 인증서 '+service.certificates.length+'장</summary>';
        for(const cert of service.certificates){html+=details([['주체',cert.subject],['발급자',cert.issuer],['유효 시작',cert.notBefore],['유효 종료',cert.notAfter],['CA 여부',cert.ca?'CA 인증서':'일반 인증서']]);html+='<details class="technical"><summary>인증서·공개키 지문</summary>'+details([['인증서 SHA-256',cert.fingerprint],['공개키 SHA-256',cert.publicKey]])+'</details>';}
        html+='</details></article>';
    }}
    html+='<details class="technical"><summary>기술정보 · 원문 해시</summary>'+details([['TL SHA-256',doc.sha256]])+'</details>';
    html+='<details id="'+id+'-xml"><summary>XML 근거 보기 · 표시용 정렬</summary><p class="explain">표시를 위해 줄을 정렬했습니다. 다운로드는 원문 바이트 그대로입니다.</p><div class="toolbar">'+xmlButton(id,'','전체 XML')+'<a class="link" href="'+esc(download)+'" download>원문 내려받기</a></div><p id="'+id+'-xml-label"></p><pre id="'+id+'-xml-text"></pre></details>';
    $(id).innerHTML=html;$(id+'-xml-text').textContent=doc.formattedXml;
}
document.addEventListener('click',event=>{
    const button=event.target.closest('[data-xml]');if(!button)return;const id=button.dataset.xml,doc=documents.get(id);if(!doc)return;
    let text=doc.formattedXml;const name=button.dataset.element;
    if(name){const xml=new DOMParser().parseFromString(text,'application/xml');let scope=xml;const indices=button.dataset.path?.match(/\[(\d+)\]/g);
        if(indices){const provider=xml.getElementsByTagNameNS('*','TrustServiceProvider')[Number(indices[0].slice(1,-1))-1];scope=provider?.getElementsByTagNameNS('*','TSPService')[Number(indices[1].slice(1,-1))-1]||xml;}
        const elements=Array.from(scope.getElementsByTagNameNS('*',name));text=elements.length?elements.map(el=>new XMLSerializer().serializeToString(el)).join('\n\n'):'해당 XML 요소가 없습니다.';
    }
    $(id+'-xml-text').textContent=text;$(id+'-xml-label').textContent=name?'선택한 근거 요소: '+name+' · '+(button.dataset.path||'목록 정보'):'전체 XML 원문(표시용 정렬)';
    $(id+'-xml').open=true;$(id+'-xml').scrollIntoView({behavior:'smooth',block:'nearest'});
});
function renderChanges(changes,first=false){
    if(!changes.length)return '<p>구조·서비스 정보의 의미상 변경이 없습니다. 서명 바이트나 기술 상세는 원문으로 확인할 수 있습니다.</p>';
    const counts=new Map();for(const c of changes)counts.set(c.kind,(counts.get(c.kind)||0)+1);
    let html=(first?'<p class="notice">최초 입력: 기존 적용 TL이 없어 등재 서비스 목록으로 표시합니다.</p>':'')+'<div class="tl-summary">'+Array.from(counts,([k,v])=>'<span class="badge">'+esc(k)+' '+v+'건</span>').join('')+'</div><p class="explain">발행 정보 변경과 서비스 신뢰정보 변경을 구분합니다. ID가 없는 서비스의 키 교체는 추가/삭제로 표시될 수 있습니다.</p>';
    html+='<div class="scroll"><table><thead><tr><th>대상</th><th>항목</th><th>기존</th><th>새 TL</th><th>변경 의미</th></tr></thead><tbody>';
    for(const c of changes)html+='<tr class="'+(c.kind==='상태 변경'?'changed':'')+'"><td>'+esc(c.target)+'</td><td>'+esc(c.field)+'</td><td>'+esc(c.before)+'</td><td>'+esc(c.after)+'</td><td>'+esc(c.kind)+'</td></tr>';
    return html+'</tbody></table></div>';
}
async function api(path, options) {
    const response = await fetch('/api/tl-poc' + path, options);
    let data; try { data = await response.json(); } catch { throw new Error('실증 API 응답을 읽지 못했습니다. 서버 설정을 확인하십시오.'); }
    if (!response.ok) throw new Error(data.error || (response.status === 404 ? '실증 API가 활성화되지 않았습니다.' : '요청 실패'));
    return data;
}
async function action(button, fn) {
    $('error').hidden = true; if (button) button.disabled = true;
    try { await fn(); } catch (e) { $('error').textContent = e.message; $('error').hidden = false; }
    finally { if (button) button.disabled = false; }
}
function details(rows) { return '<dl>' + rows.map(([k,v])=>'<dt>'+esc(k)+'</dt><dd>'+esc(v)+'</dd>').join('') + '</dl>'; }
async function active() {
    const response = await api('/active');
    const {active: tl,document} = response;
    $('active').innerHTML = tl ? details([['사용 상태',tl.usable?'사용 가능':'만료 · 사용 불가'],['운영기관',document?.operator],['TL 번호',tl.sequence],['채택시각',tl.acceptedAt],['다음 갱신',tl.nextUpdate]]) : '채택한 TL이 없습니다.';
    if (!Object.prototype.hasOwnProperty.call(response,'document')) {
        documents.delete('activeDocument');
        $('activeDocument').textContent='서버가 이전 버전으로 실행 중이어서 TL 구조 정보를 제공하지 않습니다. Start-TlPoc.ps1로 서버를 재시작한 뒤 화면을 새로고침하십시오. 적용된 TL은 보존됩니다.';
        return;
    }
    if (!tl) {
        documents.delete('activeDocument');
        $('activeDocument').textContent='아직 적용한 TL이 없습니다. 위에서 TL XML 파일을 선택하고 내용 확인·검사 후 확인한 TL 적용을 누르십시오. 폴더에 파일이 있는 것만으로 자동 적용되지는 않습니다.';
        return;
    }
    renderDocument('activeDocument',document,'/api/tl-poc/xml/active',tl?.usable?'채택한 TL · 사용 가능':'미적용 또는 만료');
}
document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>{
    $('error').hidden=true;
    document.querySelectorAll('[data-tab]').forEach(b=>{const selected=b===button;b.setAttribute('aria-selected',String(selected));$(b.dataset.tab).hidden=!selected;});
    if(button.dataset.tab==='compare') action(null,loadRuns);
});
$('refresh').onclick=()=>action($('refresh'),active);
let candidateId=null,selectionVersion=0;
$('tlFile').onchange=()=>{selectionVersion++;candidateId=null;$('applyCandidate').disabled=true;$('candidateReview').hidden=true;$('adoptMessage').hidden=true;};
$('adoptForm').onsubmit=e=>{e.preventDefault();action(e.submitter,async()=>{
    const version=selectionVersion;candidateId=null;$('applyCandidate').disabled=true;
    const data=new FormData();data.append('tl',$('tlFile').files[0]);
    const review=await api('/inspect',{method:'POST',body:data});if(version!==selectionVersion)return;
    candidateId=review.candidateId;$('applyCandidate').disabled=!review.ready;
    $('adoptMessage').textContent=review.ready?'검사 통과 · 아직 적용하지 않았습니다. 변경 내용을 확인한 뒤 적용하세요.':'채택 불가 · 신뢰 미확인: '+review.reason;
    $('adoptMessage').hidden=false;$('candidateReview').hidden=false;
    $('tlChanges').innerHTML=review.document?renderChanges(review.changes,review.baseline==null):'<p>XML을 해석할 수 없어 내용 변경을 비교하지 않았습니다.</p>';
    renderDocument('baselineDocument',review.baseline,'/api/tl-poc/xml/active','현재 적용본');
    renderDocument('candidateDocument',review.document,'/api/tl-poc/xml/'+encodeURIComponent(candidateId),review.ready?'검사 통과 · 미적용':'신뢰 미확인 · 채택 불가');
    const note=document.createElement('p');note.className='notice';note.textContent='사전 설정 TL 신뢰앵커: '+review.anchors.join(' / ')+'. XML 안의 인증서를 자동 신뢰하지 않습니다.';$('candidateDocument').prepend(note);
});};
$('applyCandidate').onclick=()=>action($('applyCandidate'),async()=>{
    const id=candidateId;if(!id)throw new Error('파일을 다시 검사하십시오.');
    try{const data=new URLSearchParams({candidateId:id});const tl=await api('/adopt',{method:'POST',body:data});$('adoptMessage').textContent='적용 완료 · TL #'+tl.sequence;$('candidateReview').hidden=true;candidateId=null;await active();}
    finally{candidateId=null;}
}).finally(()=>$('applyCandidate').disabled=true);
$('at').value=new Date().toISOString();
function renderRun(run){
    const r=run.result;
    $('result').innerHTML=details([['실행 상태',label(run.execution)],['사용 TL',run.tlSequence],['평가시각',run.evaluationTime],['문서 무결성',label(r?.integrity)],['인증 경로',label(r?.chain)],['TL 신뢰',label(r?.trust)],['서비스',r?.service],['서비스 상태',label(r?.serviceStatus)],['미검증',r?.unchecked?.join(', ')],['오류',run.error]])+'<details class="technical"><summary>실행 ID·TL 해시</summary>'+details([['실행 ID',run.id],['TL 해시',run.tlHash]])+'</details>';
    $('path').replaceChildren();for(const text of r?.path || []){const li=document.createElement('li');const [name,key]=text.split(' · ');li.innerHTML=esc(name)+'<details class="technical"><summary>공개키 지문</summary>'+esc(key)+'</details>';$('path').append(li);}
}
$('verifyForm').onsubmit=e=>{e.preventDefault();action(e.submitter,async()=>{
    const date=new Date($('at').value);if(Number.isNaN(date.getTime()) || !/Z$/.test($('at').value))throw new Error('UTC 시각을 Z로 끝나는 ISO 형식으로 입력하십시오.');
    const data=new FormData();data.append('pdf',$('pdfFile').files[0]);data.append('at',date.toISOString());if($('certFile').files[0])data.append('certificates',$('certFile').files[0]);
    renderRun(await api('/verify',{method:'POST',body:data}));await active();
});};
async function loadRuns(){const runs=await api('/runs');for(const id of ['baseline','comparison']){$(id).replaceChildren();for(const run of runs){const option=document.createElement('option');option.value=run.id;option.textContent=run.executedAt+' · TL '+(run.tlSequence||'없음')+' · '+run.id.slice(0,8)+' · '+label(run.execution);$(id).append(option);}}$('compareButton').disabled=runs.length<2;if(runs.length>1)$('baseline').selectedIndex=1;}
$('reloadRuns').onclick=()=>action($('reloadRuns'),loadRuns);
$('compareButton').onclick=()=>action($('compareButton'),async()=>{
    const q=new URLSearchParams({baseline:$('baseline').value,comparison:$('comparison').value});const c=await api('/compare?'+q);
    const fields=[['정책','policy'],['평가시각','evaluationTime'],['TL 번호','tlSequence']];
    let html='<p class="notice">'+esc(c.comparable?'조건 일치 · '+(c.tlChanged?'TL 변경 비교 가능':'동일 TL'):'조건 불일치 · TL 단독 효과 판정 보류: '+c.mismatches.join(', '))+'</p>';
    html+='<div class="scroll"><table><thead><tr><th>항목</th><th>기준</th><th>비교</th></tr></thead><tbody>';
    for(const [name,key] of fields)html+='<tr><th>'+esc(name)+'</th><td>'+esc(c.baseline[key])+'</td><td>'+esc(c.comparison[key])+'</td></tr>';
    for(const [name,key] of [['무결성','integrity'],['인증 경로','chain'],['TL 신뢰','trust'],['서비스 상태','serviceStatus']])html+='<tr><th>'+name+'</th><td>'+esc(label(c.baseline.result?.[key]))+'</td><td>'+esc(label(c.comparison.result?.[key]))+'</td></tr>';
    $('comparisonResult').innerHTML=html+'</tbody></table></div><p>관찰된 차이: '+esc(c.differences.join(', ')||'동일 결과')+'</p><details class="technical"><summary>문서·TL 해시와 엔진 버전</summary>'+details([['기준 문서',c.baseline.inputHash],['비교 문서',c.comparison.inputHash],['기준 TL',c.baseline.tlHash],['비교 TL',c.comparison.tlHash],['기준 엔진',c.baseline.engine],['비교 엔진',c.comparison.engine]])+'</details><h3>두 실행에 실제 사용한 TL의 내용 차이</h3><div id="runTlChanges"></div><div class="grid"><div><h3>기준 실행 TL</h3><div id="runBaselineDocument"></div></div><div><h3>비교 실행 TL</h3><div id="runComparisonDocument"></div></div></div>';
    const [before,after,changes]=await Promise.all([c.baseline.tlHash?api('/runs/'+c.baseline.id+'/tl'):null,c.comparison.tlHash?api('/runs/'+c.comparison.id+'/tl'):null,api('/tl-diff?'+q)]);
    $('runTlChanges').innerHTML=renderChanges(changes,!before);
    renderDocument('runBaselineDocument',before,'/api/tl-poc/runs/'+c.baseline.id+'/xml','기준 실행에 사용한 TL');
    renderDocument('runComparisonDocument',after,'/api/tl-poc/runs/'+c.comparison.id+'/xml','비교 실행에 사용한 TL');
});
action(null,active);
