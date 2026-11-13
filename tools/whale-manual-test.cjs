const fs=require('fs'), path=require('path'), crypto=require('crypto');
const {createRequire}=require('module');
const {chromium}=createRequire('C:/Users/Public/Documents/ESTsoft/CreatorTemp/tl-excel-20261026/package.json')('playwright');
const qa='C:/Users/Public/Documents/ESTsoft/CreatorTemp/whale-manual-20261028';
const pkg=fs.readFileSync(qa+'/package-root.txt','utf8').replace(/^\uFEFF/,'').trim();
const dest=path.resolve('docs/guides/poc/tl-management/whale/evidence');fs.mkdirSync(dest,{recursive:true});
const M='http://127.0.0.1:19081', V='http://127.0.0.1:19082';
const report={date:new Date().toISOString(),management:M,verification:V,checks:[]};
function check(name,ok,details){report.checks.push({name,ok,details});if(!ok)throw Error(name+': '+JSON.stringify(details));}
async function json(base,url,body){let r=await fetch(base+url,body?{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}:{});let j=await r.json();if(!r.ok)throw Error(url+': '+JSON.stringify(j));return j;}
async function upload(url,file,field,extra={}){let f=new FormData();f.set(field,new Blob([fs.readFileSync(pkg+'/samples/'+file)]),file);Object.entries(extra).forEach(([k,v])=>f.set(k,v));let r=await fetch(V+url,{method:'POST',body:f});return r.json();}
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
 const page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
 async function shot(name,url){if(url)await page.goto(url);await page.waitForTimeout(650);await page.screenshot({path:dest+'/'+name+'.png',fullPage:false});}
 const state=await json(M,'/api/admin/state');check('seed4providers10services',state.overview.providers===4&&state.overview.services===10,state.overview);
 await shot('01-overview',M+'/#overview');await shot('02-providers',M+'/#providers');
 let r=await fetch(M+'/tl/kr-tl.xml');check('initial404',r.status===404,{status:r.status,body:await r.text()});
 await shot('03-issuance-before',M+'/#publish');
 const plan=await json(M,'/api/admin/issuance/plan');report.plan=plan;
 const request={requestId:crypto.randomUUID(),expectedDraftRevision:plan.draftRevision,purpose:'BASELINE',sequenceNumber:plan.sequenceNumber,issuedAt:plan.issuedAt,nextUpdate:plan.nextUpdate};
 const issued=await json(M,'/api/admin/issuance/issuances',request);report.issued=issued;
 const replay=await json(M,'/api/admin/issuance/issuances',request);check('issueReplay',replay.replay===true&&replay.manifest.issuanceId===issued.manifest.issuanceId,replay.replay);
 const pub=await json(M,'/api/admin/issuance/publication');await json(M,'/api/admin/issuance/publication',{expectedPublicationRevision:pub.publicationRevision,issuanceId:issued.manifest.issuanceId});
 await shot('04-published',M+'/#publish');
 r=await fetch(M+'/tl/kr-tl.xml');const xml=Buffer.from(await r.arrayBuffer());const etag=r.headers.get('etag');report.publicHeaders=Object.fromEntries(r.headers);check('public200',r.status===200,r.status);
 const hash=Buffer.from(await(await fetch(M+'/tl/kr-tl.sha2')).arrayBuffer());check('sha2Raw32',hash.length===32&&hash.equals(crypto.createHash('sha256').update(xml).digest()),{bytes:hash.length});
 r=await fetch(M+'/tl/kr-tl.xml',{headers:{'If-None-Match':etag}});check('conditional304',r.status===304&& (await r.arrayBuffer()).byteLength===0,r.status);
 r=await fetch(M+'/tl/kr-tl.xml',{method:'HEAD'});check('head200',r.status===200&&(await r.arrayBuffer()).byteLength===0,r.status);
 r=await fetch(M+'/tl/kr-tl.xml',{headers:{Accept:'application/xml'}});check('accept406',r.status===406,r.status);
 r=await fetch(M+'/tl/kr-tl.xml',{method:'POST'});check('method405',r.status===405,r.status);
 r=await fetch(M+'/tl/kr-tl.xml',{headers:{Origin:'http://localhost:19082'}});check('cors',r.headers.get('access-control-allow-origin')==='*',{origin:r.headers.get('access-control-allow-origin'),expose:r.headers.get('access-control-expose-headers')});
 await page.goto(V+'/tl-poc.html');await page.locator('#tlFile').setInputFiles(pkg+'/samples/tl-1.xml');await page.locator('#adoptForm button.primary').click();await page.locator('#applyCandidate').waitFor({state:'visible'});await page.waitForFunction(()=>!document.querySelector('#applyCandidate').disabled);await shot('05-inspect-tl1');await page.locator('#applyCandidate').click();await page.waitForTimeout(500);await shot('06-active-tl1');
 await page.locator('[data-tab=verify]').click();await page.locator('#pdfFile').setInputFiles(pkg+'/samples/signed.pdf');await page.locator('#at').fill('2026-10-27T00:00:00Z');await page.locator('#verifyForm button').click();await page.waitForTimeout(1000);await shot('07-verify-tl1');
 const runs1=await json(V,'/api/tl-poc/runs');report.run1=runs1[0];check('tl1trust',runs1[0].result.trust==='TRUST_CONDITIONS_MET',runs1[0].result);
 await page.locator('[data-tab=apply]').click();await page.locator('#tlFile').setInputFiles(pkg+'/samples/tl-2.xml');await page.locator('#adoptForm button.primary').click();await page.waitForFunction(()=>!document.querySelector('#applyCandidate').disabled);await shot('08-tl2-diff');await page.locator('#applyCandidate').click();await page.waitForTimeout(400);
 await page.locator('[data-tab=verify]').click();await page.locator('#verifyForm button').click();await page.waitForTimeout(1000);await shot('09-verify-tl2');
 const runs2=await json(V,'/api/tl-poc/runs');report.run2=runs2[0];check('tl2trust',runs2[0].result.trust==='TRUST_CONDITIONS_NOT_MET',runs2[0].result);
 await page.locator('[data-tab=compare]').click();await page.locator('#baseline').selectOption(runs1[0].id);await page.locator('#comparison').selectOption(runs2[0].id);await page.locator('#compareButton').click();await page.waitForTimeout(500);await shot('10-compare');
 const comparison=await json(V,'/api/tl-poc/compare?baseline='+runs1[0].id+'&comparison='+runs2[0].id);report.comparison=comparison;check('sameConditions',comparison.comparable&&comparison.tlChanged,comparison.differences);
 const review=await upload('/api/tl-poc/inspect','tl-1.xml','tl');check('rollbackRejected',!review.ready&&review.reason==='TL_ROLLBACK',{ready:review.ready,reason:review.reason});
 check('activePreserved',(await json(V,'/api/tl-poc/active')).active.sequence==='2',null);
 console.log(JSON.stringify(report.checks.map(x=>({name:x.name,ok:x.ok}))));
 } finally {await browser.close();fs.writeFileSync(dest+'/test-report.json',JSON.stringify(report,null,2));}
})().catch(e=>{console.error(e);process.exitCode=1;});
