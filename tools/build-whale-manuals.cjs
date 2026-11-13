const fs=require('fs'),path=require('path'),{createRequire}=require('module');
const req=createRequire('C:/Users/Public/Documents/ESTsoft/CreatorTemp/tl-excel-20261026/package.json');const pptxgen=req('pptxgenjs');
const root=path.resolve('docs/guides/poc/tl-management/whale');
const C={ink:'123638',teal:'007F7B',mint:'E5F5F1',muted:'53696B',line:'D2E3E0',white:'FFFFFF',amber:'9C5B16',light:'F3F7F7'};
const sources={D1:'https://docs.google.com/document/d/1m_oghJaikYZJ2qa6mYUFOzY16AJdt8IuRbTo0Quw4_w/edit',D2:'https://docs.google.com/document/d/1244QPvq3buAQ4KZ-x-AZgWGIEyfpC0MPZhq1QyrehIQ/edit',D3:'https://docs.google.com/document/d/1ZADkFJPatquWtQPJAJPmkdh9E3QBiCFsRaPiobryRTY/edit',D4:'https://docs.google.com/document/d/1hOH8BtT9UB8_NpDi5ZtyZjJkeTVfpdNhamrBNuuiqjI/edit',D5:'https://docs.google.com/document/d/1jaEztftE4CA5eIsZQ2oRdYtELsUSMYhI8Wmsf-SIv0U/edit'};
const A=[];const U=[];
function cards(a,title,items,sub='',source='구현·배포본 확인'){a.push({kind:'cards',title,items,sub,source});}
function table(a,title,headers,rows,sub='',source='구현·배포본 확인'){a.push({kind:'table',title,headers,rows,sub,source});}
function code(a,title,body,items,sub=''){a.push({kind:'code',title,body,items,sub,source:'실제 API 계약 · 예시 값은 실행 환경에 맞게 변경'});}
function flow(a,title,items,sub,source='D1 §6–7 · D4 §4'){a.push({kind:'flow',title,items,sub,source});}
function shot(a,title,file,items,sub=''){a.push({kind:'shot',title,file,items,sub,source:'별도 배포본 실측 화면 · 2026-10-28 · 기본 접속 포트는 18081 / 18082'});}
require('./whale-manual-content.cjs')({A,U,cards,table,code,shot,flow});
function tx(s,t,x,y,w,h,size=17,color=C.ink,bold=false){s.addText(t,{x,y,w,h,fontFace:'Malgun Gothic',fontSize:size,color,bold,margin:0,breakLine:false,valign:'top',paraSpaceAfterPt:8});}
function rect(s,x,y,w,h,color){s.addShape('rect',{x,y,w,h,line:{color,transparency:100},fill:{color}});}
function base(p,d,i,total,label){let s=p.addSlide();s.background={color:C.white};tx(s,label,0.55,0.25,10,0.3,10,C.teal,true);tx(s,d.title,0.55,0.82,12.2,0.68,30,C.ink,true);if(d.sub)tx(s,d.sub,0.55,1.62,12.15,0.68,13,C.muted);tx(s,'TrustList PoC · v1.0 · 2026-10-28',0.55,7.03,11.6,0.24,9,C.muted);tx(s,String(i+1).padStart(2,'0')+' / '+total,12.1,7.02,0.7,0.25,9,C.muted);return s;}
async function build(data,name,label){const p=new pptxgen();p.layout='LAYOUT_WIDE';p.author='KR-TL PoC';p.subject=label;p.title=data[0].title.replace('\n',' ');p.company='KR-TL';p.lang='ko-KR';p.theme={headFontFace:'Malgun Gothic',bodyFontFace:'Malgun Gothic',lang:'ko-KR'};
for(let i=0;i<data.length;i++){let d=data[i],s;if(d.kind==='cover'){s=p.addSlide();s.background={color:C.ink};tx(s,'KR-TL  /  DEVELOPER DOCUMENTATION',0.7,0.6,11,0.35,13,'73DCCD',true);tx(s,d.sub,0.7,1.65,11,0.5,22,'A3DCD3');tx(s,d.title,0.7,2.45,12,1.7,38,C.white,true);tx(s,d.items.join('\n\n'),0.7,5.1,11.7,1.4,17,C.white);s.addShape('ellipse',{x:10.7,y:0.65,w:1.4,h:1.4,line:{color:'73DCCD',width:2},fill:{color:C.ink}});tx(s,'TL',10.93,1.03,1,0.5,26,'73DCCD',true);}
else {s=base(p,d,i,data.length,label);let y=2.55;
if(d.kind==='cards'){let w=(12.2-(d.items.length-1)*0.28)/d.items.length;d.items.forEach(([h,t],j)=>{let x=.55+j*(w+.28);rect(s,x,y,w,3.9,j%2?C.light:C.mint);tx(s,String(j+1).padStart(2,'0'),x+.22,y+.22,w-.44,.5,25,C.teal,true);tx(s,h,x+.22,y+.95,w-.44,.6,20,C.ink,true);tx(s,t,x+.22,y+1.72,w-.44,1.98,17);});}
if(d.kind==='table'){let rows=[d.headers,...d.rows],widths=d.headers.length===2?[3.0,9.2]:[2.6,4.8,4.8],rh=4.22/rows.length;rows.forEach((row,ri)=>{let x=.55;row.forEach((cell,ci)=>{rect(s,x,2.45+ri*rh,widths[ci]-.025,rh-.025,ri===0?C.ink:ri%2?C.light:C.white);tx(s,cell,x+.13,2.45+ri*rh+.09,widths[ci]-.26,rh-.12,ri===0?15:ci===0?13:14,ri===0?C.white:C.ink,ri===0);x+=widths[ci];});});}
if(d.kind==='flow'){const w=2.8;d.items.forEach(([h,t],j)=>{let x=.55+j*3.12;rect(s,x,2.75,w,2.45,j%2?C.light:C.mint);tx(s,h,x+.2,3.05,w-.4,.6,24,C.teal,true);tx(s,t,x+.2,3.9,w-.4,1,17);if(j<d.items.length-1)tx(s,'→',x+w+.02,3.55,.3,.4,21,C.teal,true);});rect(s,.55,5.6,12.2,.78,C.light);}
if(d.kind==='code'){rect(s,.55,2.5,8.25,4.14,C.ink);tx(s,d.body,.78,2.72,7.79,3.75,14,'ECF8F5');let yy=2.65;d.items.forEach(([h,t])=>{tx(s,h,9.15,yy,3.6,.45,19,C.teal,true);tx(s,t,9.15,yy+.55,3.6,1.35,16);yy+=2.0;});}
if(d.kind==='shot'){let f=path.join(root,'evidence',d.file);if(!fs.existsSync(f))throw Error('Missing screenshot '+f);let crop=path.join(root,'evidence','crop-'+d.file);let top=/compare/.test(d.file)?240:120;await req('sharp')(f).extract({left:0,top,width:1440,height:760}).toFile(crop);s.addImage({path:crop,x:.55,y:2.35,w:8.05,h:4.25});let yy=2.35;d.items.forEach(([h,t])=>{tx(s,h,8.95,yy,3.8,.33,17,C.teal,true);tx(s,t,8.95,yy+.43,3.8,1.0,d.items.length>3?14:16);yy+=d.items.length>3?1.07:1.4;});}
}
s.addNotes((d.notes||'')+'\n\n본문:\n'+[d.title,d.sub,...(d.items||[]).map(v=>Array.isArray(v)?v.join(': '):v),...(d.rows||[]).map(v=>v.join(' | '))].join('\n')+'\n\n근거: '+(d.source||'배포본 확인')+'\n'+Object.entries(sources).filter(([k])=>(d.source||'').includes(k)).map(([k,v])=>k+' '+v).join('\n'));
}
await p.writeFile({fileName:path.join(root,name+'.pptx')});fs.writeFileSync(path.join(root,name+'.md'),data.map((d,i)=>{
 const title=(i===0?'# ':'## ')+d.title.replace(/\n/g,' ');
 const parts=[title,d.sub||''];
 if(d.items) parts.push(d.items.map(v=>Array.isArray(v)?`- **${v[0]}**: ${v[1].replace(/\n/g,' ')}`:v).join('\n\n'));
 if(d.rows) parts.push([d.headers,d.headers.map(()=> '---'),...d.rows].map(r=>'| '+r.map(c=>c.replace(/\n/g,'<br>')).join(' | ')+' |').join('\n'));
 if(d.body) parts.push('```'+(d.language||'text')+'\n'+d.body+'\n```');
 if(d.file) parts.push('!['+d.title+'](evidence/'+d.file+')');
 return parts.filter(Boolean).join('\n\n');
}).join('\n\n')+'\n');
console.log(name+': '+data.length+' slides');}
(async()=>{await build(A,'Whale_API_연동_가이드_v1.0','GUIDE 01 · API 연동');await build(U,'Whale_관리시스템_TL작업대_사용매뉴얼_v1.0','GUIDE 02 · 관리 시스템과 TL 작업대');fs.writeFileSync(path.join(root,'slide-content.json'),JSON.stringify({api:A,user:U},null,2));})();
