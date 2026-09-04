# -*- coding: utf-8 -*-
"""KR-TL 설명자료 v02 — 16:9 HTML 슬라이드 생성기
   원고: KR-TL_설명자료_본문_v02.md
   출력: v02/slides/s01..s14.html, v02/deck_view.html
   캡처: ../assets/p02_pdf_viewer.png, ../assets/p05_ca_manual.png
"""
import os, html

OUT = "/home/claude/v02"
SLIDES = os.path.join(OUT, "slides")
os.makedirs(SLIDES, exist_ok=True)

DECK = "KR-TL 국내 전자서명 신뢰목록 개요"
ORG = "㈜일렉서티"
TOTAL = 14

CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body { width:1280px; height:720px; font-family:'Noto Sans KR',sans-serif;
       background:#FFFFFF; overflow:hidden; -webkit-font-smoothing:antialiased; }
.slide { width:1280px; height:720px; display:flex; flex-direction:column; }

.header { background:#1A1A2E; height:58px; flex-shrink:0; display:flex;
          align-items:center; justify-content:space-between; padding:0 48px; }
.header .t { font-size:21px; font-weight:700; color:#FFFFFF; letter-spacing:-0.01em; }
.header .b { font-size:12px; color:#7BA7D4; letter-spacing:0.04em; }

.gov { flex-shrink:0; background:#F6F8FC; border-bottom:1px solid #EDF2F7;
       padding:14px 48px 14px 44px; border-left:4px solid #5B9BD5; }
.gov p { font-size:15.5px; font-weight:700; color:#1A1A2E; line-height:1.55; }

.body { flex:1; padding:20px 48px 14px; overflow:hidden; display:flex;
        flex-direction:column; gap:13px; min-height:0; }
.body.mid { justify-content:center; }

.ask { flex-shrink:0; margin:0 48px 9px; background:#FFFFFF;
       border:1px solid #DCE4F0; border-left:3px solid #A0AEC0;
       padding:9px 15px; display:flex; gap:13px; align-items:flex-start; }
.ask .lb { font-size:10px; font-weight:700; color:#718096; letter-spacing:0.08em;
           white-space:nowrap; padding-top:1px; }
.ask .tx { font-size:12px; color:#4A5568; line-height:1.55; }

.footer { height:30px; flex-shrink:0; border-top:1px solid #EDF2F7;
          padding:0 48px; display:flex; align-items:center; justify-content:space-between; }
.footer span { font-size:10.5px; color:#A0AEC0; }

/* components */
.row { display:flex; gap:16px; }
.row > * { min-width:0; }
.row.fill { flex:1; min-height:0; }
.col { flex:1; display:flex; flex-direction:column; gap:9px; min-height:0; }
.cap { font-size:11.5px; font-weight:700; color:#5B9BD5; letter-spacing:0.05em; flex-shrink:0; }
.cap.mute { color:#A0AEC0; }
.cap.warn { color:#B7791F; }
.sub { font-size:11px; color:#718096; line-height:1.6; }

table { width:100%; border-collapse:collapse; }
th { background:#1A1A2E; color:#BDD5EA; font-size:12px; font-weight:700;
     text-align:left; padding:8px 13px; letter-spacing:0.02em; }
td { font-size:12.5px; color:#2D3748; padding:8px 13px;
     border-bottom:1px solid #EDF2F7; line-height:1.5; vertical-align:middle; }
tr:last-child td { border-bottom:none; }
.t-lite th { background:#EDF2F7; color:#4A5568; }
.t-cmp th { font-size:11px; padding:6px 11px; }
.t-cmp td { font-size:11.5px; padding:6px 11px; }
td.k { font-weight:700; color:#1A1A2E; white-space:nowrap; }
td.m { color:#718096; }
.tw { flex:1; min-height:0; display:flex; }
.tw table { height:100%; }

.tag { display:inline-block; font-size:9px; font-weight:700; padding:2px 6px;
       letter-spacing:0.04em; vertical-align:1px; }
.tag.spec { background:#EBF4FF; color:#1A4A7A; }
.tag.inst { background:#F0F0F5; color:#5A5A70; }
.tag.warn { background:#FFFDE7; color:#7B341E; }

.card { border:1px solid #DCE4F0; display:flex; flex-direction:column; }
.card > .h { background:#1A1A2E; color:#7BA7D4; font-size:11px; font-weight:700;
             padding:7px 14px; letter-spacing:0.05em; flex-shrink:0; }
.card > .c { padding:12px 14px; font-size:12.5px; color:#2D3748; line-height:1.65; flex:1; }
.card.on { border-color:#5B9BD5; }
.card.on > .h { background:#5B9BD5; color:#FFFFFF; }
.card.mute > .h { background:#EDF2F7; color:#718096; }
.card.fillc { flex:1; min-height:0; }

.note { background:#F6F8FC; border:1px solid #E2E8F0; padding:10px 14px;
        font-size:12px; color:#4A5568; line-height:1.6; }
.fix { background:#FFFFFF; border:1px solid #DCE4F0; border-left:3px solid #1A1A2E;
       padding:11px 14px; font-size:12.5px; font-weight:700; color:#1A1A2E; line-height:1.55; }
.fix.acc { border-left-color:#5B9BD5; }
.fix.warn { border-left-color:#B7791F; color:#7B341E; }

ol.steps { list-style:none; counter-reset:s; }
ol.steps li { counter-increment:s; position:relative; padding:7px 0 7px 30px;
              font-size:12.5px; color:#2D3748; line-height:1.5;
              border-bottom:1px solid #F1F5F9; }
ol.steps li:last-child { border-bottom:none; }
ol.steps li::before { content:counter(s); position:absolute; left:0; top:7px;
              width:20px; height:20px; border-radius:50%; background:#EDF2F7;
              color:#4A5568; font-size:10.5px; font-weight:700; text-align:center;
              line-height:20px; }

ul.plain { list-style:none; }
ul.plain li { position:relative; padding:6px 0 6px 15px; font-size:12.5px;
              color:#2D3748; line-height:1.55; }
ul.plain li::before { content:''; position:absolute; left:0; top:13px; width:5px;
              height:5px; background:#5B9BD5; }

/* 캡처 자리표시 */
.shot { position:relative; border:1px dashed #C3CFE0; background:#FAFBFD;
        flex:1; min-height:0; display:flex; align-items:center; justify-content:center; }
.shot .ph { font-size:11.5px; color:#A0AEC0; line-height:1.9; text-align:center; padding:14px; }
.shot .ph b { display:block; color:#718096; font-size:12px; margin:7px 0 3px;
              font-family:ui-monospace,Menlo,Consolas,monospace; }
.shot img { position:absolute; top:0; left:0; width:100%; height:100%;
            object-fit:contain; background:#FFFFFF; }

/* 흐름 바 */
.flow { display:flex; align-items:stretch; gap:0; flex-shrink:0; }
.flow .st { flex:1; border:1px solid #DCE4F0; padding:9px 10px; position:relative;
            display:flex; flex-direction:column; justify-content:center; }
.flow .st + .st { margin-left:8px; }
.flow .st::after { content:'\\203A'; position:absolute; right:-8px; top:50%;
                   transform:translate(50%,-50%); color:#CBD5E0; font-size:16px; z-index:2; }
.flow .st:last-child::after { display:none; }
.flow .n { font-size:9.5px; font-weight:700; color:#A0AEC0; }
.flow .x { font-size:11.5px; font-weight:700; color:#2D3748; margin-top:3px; line-height:1.4; }
.flow .z { font-size:9px; color:#A0AEC0; margin-top:6px; letter-spacing:0.04em; }
.flow .st.on { border-color:#5B9BD5; background:#EBF4FF; }
.flow .st.on .n { color:#5B9BD5; }
.flow .st.on .x { color:#1A4A7A; }
.flow .st.on .z { color:#5B9BD5; font-weight:700; }
"""

COVER_CSS = """
.cover { width:1280px; height:720px; display:flex; flex-direction:column;
         justify-content:center; padding:0 96px; position:relative; }
.cover .rule { width:56px; height:4px; background:#5B9BD5; margin-bottom:30px; }
.cover h1 { font-size:56px; font-weight:700; color:#1A1A2E; letter-spacing:-0.02em; }
.cover h2 { font-size:26px; font-weight:400; color:#4A5568; margin-top:10px; }
.cover .sb { font-size:15px; color:#718096; margin-top:34px; line-height:1.7; }
.cover .meta { position:absolute; left:96px; bottom:64px; font-size:13px; color:#A0AEC0; }
.cover .side { position:absolute; right:0; top:0; width:14px; height:720px; background:#1A1A2E; }
"""


def page(num, title, crumb, body, ask=None):
    ask_html = ""
    if ask:
        ask_html = ('\n  <div class="ask">\n    <span class="lb">열어 둘 질문</span>\n'
                    '    <span class="tx">' + ask + '</span>\n  </div>')
    return ("""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8"/>
<title>%02d %s</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap" rel="stylesheet">
<style>%s</style>
</head>
<body>
<div class="slide">
  <div class="header">
    <span class="t">%s</span>
    <span class="b">%s</span>
  </div>
%s%s
  <div class="footer">
    <span>%s</span>
    <span>%s &nbsp;&nbsp; %02d / %d</span>
  </div>
</div>
</body>
</html>""" % (num, re_plain(title), CSS, title, crumb, body, ask_html, DECK, ORG, num, TOTAL))


def re_plain(t):
    import re
    return re.sub(r"<[^>]+>", "", t)


def gov(text):
    return '  <div class="gov"><p>' + text + '</p></div>'


def shot(path, desc):
    return ('<div class="shot"><div class="ph">캡처 삽입 위치<b>' + path + '</b>'
            + desc + '</div><img src="../' + path + '" onerror="this.remove()"></div>')


pages = []

# =============================================================== P01 표지
p01 = """<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8"/>
<title>01 표지</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap" rel="stylesheet">
<style>%s%s</style>
</head>
<body>
<div class="cover">
  <div class="side"></div>
  <div class="rule"></div>
  <h1>KR-TL</h1>
  <h2>국내 전자서명 신뢰목록 개요</h2>
  <div class="sb">웨일 브라우저 PDF 전자서명 검증 실증 사전 공유</div>
  <div class="meta">㈜일렉서티 &nbsp;·&nbsp; 2026.08</div>
</div>
</body>
</html>""" % (CSS, COVER_CSS)
pages.append(("s01_cover.html", "표지", "표지", "0:20", p01))

# =============================================================== P02 지금 PDF 서명을 열면
body = gov('브라우저는 서명값이 맞는지는 확인하지만 그 인증서를 발급한 사업자를 믿어도 되는지는 답하지 못한다. '
           '그리고 더 큰 문제는, 일반 사용자가 서명을 확인해야 한다는 사실 자체를 모른다는 것이다.') + """
  <div class="body">
    <div class="row fill">
      <div class="col" style="flex:1.02;">
        """ + shot("assets/p02_pdf_viewer.png", "브라우저 내장 PDF 뷰어에서<br>전자서명된 문서를 연 화면") + """
      </div>
      <div class="col">
        <div class="card mute fillc">
          <div class="h">확인되는 것</div>
          <div class="c">
            <ul class="plain">
              <li style="color:#718096;">문서 무결성</li>
              <li style="color:#718096;">서명값 일치</li>
              <li style="color:#718096;">인증서 유효기간</li>
            </ul>
            <div style="margin-top:6px;font-size:10px;color:#A0AEC0;letter-spacing:0.04em;">로컬에서 계산 가능</div>
          </div>
        </div>
        <div class="card on fillc">
          <div class="h">확인되지 않는 것</div>
          <div class="c">
            <ul class="plain">
              <li>발급 사업자가 인정받은 곳인가</li>
              <li>서명 시점에 그 사업자가 정상이었는가</li>
              <li>이 인증서가 어떤 용도로 발급된 것인가</li>
              <li>폐지·정지 여부를 어디서 확인하는가</li>
            </ul>
            <div style="margin-top:6px;font-size:10px;color:#5B9BD5;font-weight:700;letter-spacing:0.04em;">외부 근거가 필요</div>
          </div>
        </div>
      </div>
    </div>
    <div class="fix acc">사용자는 왼쪽 화면에서 이 문서에 서명이 붙어 있다는 사실조차 인지하지 못한다.
      확인할 수단이 없는 것이 아니라, <span style="color:#1A4A7A;">확인해야 한다는 것을 모른다.</span></div>
  </div>"""
pages.append(("s02_gap.html", "지금 PDF 서명을 열면", "문제 › 현재 상황", "1:30",
              page(2, "지금 PDF 서명을 열면", "문제 › 현재 상황", body,
                   "웨일 내장 PDF 뷰어에서 서명이 포함된 문서를 만났을 때 지금은 어떤 표시가 나가는지, 그리고 이 화면이 사용자 문의로 올라온 적이 있는지 궁금합니다.")))

# =============================================================== P03 PKI 인증 체계
PKI_CSS = """
<style>
.chain { display:flex; flex-direction:column; align-items:center; gap:0;
         flex:1; min-height:0; justify-content:center; padding:4px 0; }
.chain .bx { border:1px solid #DCE4F0; padding:5px 14px; font-size:11.5px; color:#2D3748;
             background:#FFFFFF; text-align:center; min-width:172px; }
.chain .bx.hd { background:#1A1A2E; color:#FFFFFF; font-weight:700; border-color:#1A1A2E; }
.chain .bx.an { border-color:#5B9BD5; background:#EBF4FF; color:#1A4A7A; font-weight:700; }
.chain .bx small { display:block; font-size:9.5px; color:#A0AEC0; margin-top:1px; }
.chain .bx.hd small { color:#7BA7D4; }
.chain .ar { color:#CBD5E0; font-size:12px; line-height:1.1; padding:1px 0; }
.pki2 { display:flex; gap:10px; justify-content:center; align-items:flex-start; }
.pki2 .br { text-align:center; }
.pki2 .rt { border:1px solid #DCE4F0; background:#F6F8FC; font-size:10.5px; font-weight:700;
            color:#1A1A2E; padding:4px 12px; }
.pki2 .st { width:1px; height:11px; background:#CBD5E0; margin:0 auto; }
.pki2 .lf { border:1px solid #EDF2F7; font-size:10px; color:#718096; padding:3px 12px; }
.link { text-align:center; font-size:10px; color:#5B9BD5; font-weight:700; margin-top:6px;
        border-top:1px dashed #5B9BD5; padding-top:5px; }
.cut { display:flex; gap:7px; justify-content:center; }
.cut .c1 { flex:1; text-align:center; border:1px dashed #E0A96D; background:#FFFDF6; padding:5px 3px; }
.cut .c1 .n { font-size:10.5px; font-weight:700; color:#7B341E; }
.cut .c1 .r { font-size:9px; color:#B7791F; margin-top:3px; }
.cutmsg { text-align:center; font-size:10.5px; font-weight:700; color:#7B341E; margin-top:6px; }
</style>
"""
body = gov('전자서명의 신뢰는 지금까지 인증서 경로 검증으로 해결해 왔다. '
           '서명자 인증서에서 신뢰앵커까지 경로가 하나로 이어질 때만 성립하는 방식이다.') + """
  <div class="body">""" + PKI_CSS + """
    <div class="row fill">
      <div class="col" style="flex:0.95;">
        <div class="cap">CMS 구조와 인증서 체인 검증</div>
        <div class="chain">
          <div class="bx hd">SignedData<small>eContent · certificates · SignerInfo</small></div>
          <div class="ar">&#9660;</div>
          <div class="bx">서명자 인증서<small>공개키로 서명값 검증</small></div>
          <div class="ar">&#9650;</div>
          <div class="bx">중간 CA</div>
          <div class="ar">&#9650;</div>
          <div class="bx an">루트 &nbsp;·&nbsp; 신뢰앵커</div>
        </div>
        <table class="t-cmp t-lite">
          <tbody>
            <tr><td class="k" style="width:78px;">경로 제약</td><td class="m">유효기간 · Basic Constraints · Key Usage · 정책</td></tr>
            <tr><td class="k">폐지 상태</td><td class="m">CRL / OCSP</td></tr>
          </tbody>
        </table>
      </div>
      <div class="col">
        <div class="cap">GPKI · NPKI 구조</div>
        <div style="border:1px solid #DCE4F0;padding:11px 13px;flex:1;min-height:0;display:flex;flex-direction:column;justify-content:center;">
          <div class="pki2">
            <div class="br"><div class="rt">GPKI Root</div><div class="st"></div><div class="lf">행정 영역 서명</div></div>
            <div class="br"><div class="rt">NPKI Root</div><div class="st"></div><div class="lf">민간 영역 서명</div></div>
          </div>
          <div class="link">두 체계를 연결해 경로가 이어진다</div>
        </div>
        <div class="cap warn" style="margin-top:2px;">민간사업자 개별 구조 <span class="tag warn">현재</span></div>
        <div style="border:1px solid #E0A96D;padding:11px 13px;background:#FFFDF6;flex:1;min-height:0;display:flex;flex-direction:column;justify-content:center;">
          <div class="cut">
            <div class="c1"><div class="n">A사</div><div class="r">자체 루트</div></div>
            <div class="c1"><div class="n">B사</div><div class="r">자체 루트</div></div>
            <div class="c1"><div class="n">C사</div><div class="r">자체 루트</div></div>
            <div class="c1"><div class="n">D사</div><div class="r">자체 루트</div></div>
          </div>
          <div class="cutmsg">상호 연결 없음 &nbsp;—&nbsp; 경로 단절</div>
        </div>
      </div>
    </div>
    <div class="fix warn">경로가 끊긴 곳에서는 경로 검증이 성립하지 않는다.
      이용기관은 사업자별 앵커를 개별 수집해 코드에 넣는 방식으로 대응하고 있다.</div>
  </div>"""
pages.append(("s03_pki.html", "PKI 전자서명 인증 체계", "배경 › 기존 신뢰체계", "1:30",
              page(3, "PKI 전자서명 인증 체계", "배경 › 기존 신뢰체계", body,
                   "웨일이 현재 문서 서명 검증에 쓰고 있는 신뢰앵커가 있다면 어디서 오는지, 그리고 그 목록을 어떻게 갱신하고 계신지 알고 싶습니다.")))

# =============================================================== P04 신뢰목록 정의
body = gov('신뢰목록은 국가가 인정한 전자서명 사업자와 그 서비스의 현재·과거 상태를, 국가가 서명해 배포하는 기계판독 목록이다. '
           '끊어진 경로를 잇는 대신, 신뢰의 근거를 목록으로 옮긴 방식이다.') + """
  <div class="body">
    <div class="cap">성립 조건 네 가지</div>
    <div class="tw">
    <table>
      <thead><tr><th style="width:180px;">조건</th><th>의미</th><th style="width:78px;">근거</th></tr></thead>
      <tbody>
        <tr><td class="k">권위 있는 발행자</td><td>목록 발행 주체가 인정제도 운영 기관과 일치한다</td><td><span class="tag inst">제도</span></td></tr>
        <tr><td class="k">기계판독</td><td>사람이 읽는 공고문이 아니라 검증 소프트웨어가 파싱하는 XML이다</td><td><span class="tag spec">규격</span></td></tr>
        <tr><td class="k">서명된 목록</td><td>목록 자체가 전자서명되어 위·변조를 검출한다</td><td><span class="tag spec">규격</span></td></tr>
        <tr><td class="k">이력 보존</td><td>현재 상태만이 아니라 언제부터 그 상태였는지를 담는다</td><td><span class="tag spec">규격</span></td></tr>
      </tbody>
    </table>
    </div>
    <div class="note"><b style="color:#1A1A2E;">참고</b> &nbsp; EU는 eIDAS 체계에서 ETSI TS 119 612 규격의 Trusted List를
      회원국별로 발행하고, 이를 LOTL로 묶어 배포한다. KR-TL은 같은 구조를 국내 인정제도에 맞춰 적용한 것이다.</div>
    <div class="fix">인증서 목록이 아니라 서비스 상태 목록이다.</div>
  </div>"""
pages.append(("s04_definition.html", "신뢰목록이란 무엇인가", "개념 › 정의", "1:00",
              page(4, "신뢰목록이란 무엇인가", "개념 › 정의", body)))

# =============================================================== P05 신뢰목록 사용
AUTO_CSS = """
<style>
.auto { display:flex; flex-direction:column; gap:0; flex:1; min-height:0; justify-content:space-between; }
.auto .n { border:1px solid #DCE4F0; padding:7px 12px; }
.auto .n .w { font-size:10px; font-weight:700; color:#5B9BD5; letter-spacing:0.04em; }
.auto .n .d { font-size:11.5px; color:#2D3748; margin-top:2px; line-height:1.45; }
.auto .a { text-align:center; color:#CBD5E0; font-size:12px; line-height:1.2; padding:2px 0; }
.auto .n.last { border-color:#5B9BD5; background:#EBF4FF; }
.auto .n.last .d { color:#1A4A7A; font-weight:700; }
</style>
"""
body = gov('지금은 확인이 필요할 때 사람이 각 인증센터에 접속해 눈으로 대조한다. '
           '신뢰목록은 이 확인을 검증 소프트웨어가 하도록 옮기는 것이다.') + """
  <div class="body">""" + AUTO_CSS + """
    <div class="row fill">
      <div class="col">
        <div class="cap">인증센터 자동연결 구조 <span class="tag spec">신뢰목록 적용 후</span></div>
        <div class="auto">
          <div class="n"><div class="w">KISA</div><div class="d">인정사업자 정보를 수집해 KR-TL 발행·서명</div></div>
          <div class="a">&#9660;<span style="font-size:9px;color:#A0AEC0;margin-left:6px;">주기 동기화</span></div>
          <div class="n"><div class="w">브라우저</div><div class="d">목록 서명·발행시각·유효기간 검증 후 적용</div></div>
          <div class="a">&#9660;</div>
          <div class="n"><div class="w">검증 엔진</div><div class="d">문서를 열 때 목록을 조회해 자동 판정</div></div>
          <div class="a">&#9660;</div>
          <div class="n last"><div class="w">사용자</div><div class="d">결과를 화면에서 즉시 확인</div></div>
        </div>
      </div>
      <div class="col">
        <div class="cap mute">인증센터 수동관리 <span class="tag inst">현재</span></div>
        """ + shot("assets/p05_ca_manual.png", "인증센터에서 인증서·서비스 정보를<br>사람이 조회하는 화면") + """
        <ol class="steps" style="flex-shrink:0;">
          <li>사업자별 인증센터 사이트에 접속</li>
          <li>인증서·서비스 정보를 화면에서 조회</li>
          <li>유효 여부를 눈으로 대조</li>
          <li>필요 시 앵커를 내려받아 시스템에 반영</li>
        </ol>
      </div>
    </div>
    <div class="fix">사람이 하는 확인은 재현되지 않고 근거도 남지 않는다. 사업자가 늘어날수록 확인 비용이 사업자 수만큼 늘어난다.</div>
  </div>"""
pages.append(("s05_manual.html", "신뢰목록 사용, 지금은 수동이다", "개념 › 현재 이용 방식", "1:15",
              page(5, "신뢰목록 사용, 지금은 수동이다", "개념 › 현재 이용 방식", body,
                   "웨일에서 외부 기준정보를 주기적으로 받아 쓰는 기존 사례가 있다면, 그 갱신 경로를 KR-TL에도 재사용할 수 있을지 궁금합니다.")))

# =============================================================== P06 KR-TL 구조
TREE = """
<style>
.tree { font-size:12px; color:#2D3748; line-height:1.72; }
.tree .lv { border-left:1px solid #DCE4F0; margin-left:9px; padding-left:16px; }
.tree .nd { font-weight:700; color:#1A1A2E; font-size:12.5px; margin:3px 0; }
.tree .nd small { font-weight:400; color:#A0AEC0; font-size:10.5px; margin-left:6px; }
.tree .f { color:#718096; padding:2px 0; }
.tree .f.hi { color:#1A4A7A; font-weight:700; background:#EBF4FF; display:block;
              padding:3px 9px; margin:2px 0; border-left:3px solid #5B9BD5; }
.tree .f span.d { color:#A0AEC0; font-weight:400; margin-left:8px; }
</style>
<div class="tree">
  <div class="nd">Scheme <small>스킴 정보</small></div>
  <div class="lv">
    <div class="f">운영 주체 · 스킴 유형 · 적용 국가 · 목록 일련번호</div>
    <div class="f">발행시각 · 다음 갱신 예정시각</div>
    <div class="nd" style="margin-top:8px;">TSP <small>전자서명인증사업자</small></div>
    <div class="lv">
      <div class="f">사업자명 · 사업자 식별자 · 소재지</div>
      <div class="nd" style="margin-top:8px;">Service <small>서비스</small></div>
      <div class="lv">
        <div class="f">서비스 유형 <span class="d">인증서 발급 / 타임스탬프 / 검증</span></div>
        <div class="f">서비스 명칭</div>
        <div class="f hi">Digital Identity <span class="d" style="color:#5B9BD5;">서비스 인증서 · 공개키 · SKI</span></div>
        <div class="f hi">현재 상태 + 상태 개시시각</div>
        <div class="f hi">상태 이력 <span class="d" style="color:#5B9BD5;">과거 상태와 각 개시시각</span></div>
        <div class="f">부가 정보 <span class="d">정책 OID · OCSP/CRL 주소</span></div>
      </div>
    </div>
  </div>
</div>
"""
body = gov('KR-TL은 ETSI TS 119 612 구조를 따르며, 계층은 스킴 → 사업자 → 서비스 → 상태 이력 네 단으로 내려간다.') + """
  <div class="body">
    <div class="row fill">
      <div class="col" style="flex:1.35;">""" + TREE + """</div>
      <div class="col">
        <div class="cap">검증에서 실제로 쓰는 것</div>
        <div class="card"><div class="c">
          <ul class="plain">
            <li>신뢰앵커로 쓰는 것은 Service의 <b>Digital Identity</b>다.</li>
            <li>판정에 쓰는 것은 상태값 하나가 아니라 <b>상태값과 개시시각의 쌍</b>이다.</li>
            <li>같은 사업자라도 서비스별로 상태가 다를 수 있으므로 <b>서비스 단위</b>로 조회한다.</li>
          </ul>
        </div></div>
        <div class="sub">강조한 세 항목 외 나머지 필드는 배포자료 부록에서 다룬다.</div>
      </div>
    </div>
  </div>"""
pages.append(("s06_structure.html", "KR-TL의 구조", "구조 › 데이터 모델", "1:30",
              page(6, "KR-TL의 구조", "구조 › 데이터 모델", body,
                   "이 구조를 브라우저가 그대로 보관할지, 파싱한 뒤 필요한 필드만 정규화해서 보관할지는 웨일 쪽 저장 정책에 달려 있습니다. 어느 쪽이 편한지 의견을 듣고 싶습니다.")))

# =============================================================== P07 제공 기능 5가지
F5_CSS = """
<style>
.f5 { display:flex; gap:13px; flex:1; min-height:0; }
.f5 .fc { flex:1; border:1px solid #DCE4F0; display:flex; flex-direction:column; }
.f5 .fc .id { background:#EDF2F7; color:#4A5568; font-size:12.5px; font-weight:700;
              padding:6px 12px; letter-spacing:0.04em; }
.f5 .fc .nm { padding:11px 12px 0; font-size:14px; font-weight:700; color:#1A1A2E; line-height:1.35; }
.f5 .fc .ds { padding:8px 12px 11px; font-size:12px; color:#2D3748; line-height:1.6; flex:1; }
.f5 .fc .no { border-top:1px solid #EDF2F7; padding:9px 12px; font-size:11px;
              color:#718096; line-height:1.5; background:#FAFAFC; }
.f5 .fc .no b { display:block; font-size:9.5px; color:#A0AEC0; letter-spacing:0.08em; margin-bottom:3px; }
.f5 .fc.br { border-color:#5B9BD5; box-shadow:inset 0 0 0 1px #5B9BD5; }
.f5 .fc.br .id { background:#5B9BD5; color:#FFFFFF; }
</style>
"""
body = gov('KR-TL은 신뢰앵커 제공, 시점 기준 상태 판정, 서비스 유형 구분, 목록의 진위 보장, 배포·동기화 다섯 가지 기능을 제공한다. '
           '검증 엔진이 KR-TL에 요청하는 모든 것은 이 다섯 안에 들어온다.') + """
  <div class="body">""" + F5_CSS + """
    <div class="f5">
      <div class="fc"><div class="id">F1</div><div class="nm">신뢰앵커<br>제공</div>
        <div class="ds">인증서 경로 구성의 종착점이 될 서비스 인증서를 목록으로 내려준다</div>
        <div class="no"><b>없으면</b>이용기관이 앵커를 직접 수집·하드코딩</div></div>
      <div class="fc"><div class="id">F2</div><div class="nm">시점 기준<br>상태 판정</div>
        <div class="ds">서명 시점에 그 서비스가 어떤 상태였는지를 소급해 판정한다</div>
        <div class="no"><b>없으면</b>과거 서명이 사업자 지위 변경만으로 무효화</div></div>
      <div class="fc"><div class="id">F3</div><div class="nm">서비스<br>유형 구분</div>
        <div class="ds">서명용·타임스탬프·검증 서비스를 구분해 용도 밖 사용을 걸러낸다</div>
        <div class="no"><b>없으면</b>타임스탬프용 인증서로 문서 서명해도 통과</div></div>
      <div class="fc br"><div class="id">F4</div><div class="nm">목록의<br>진위 보장</div>
        <div class="ds">목록에 서명·발행시각·유효기간을 부여해 위조와 노후화를 막는다</div>
        <div class="no"><b>없으면</b>신뢰 판단의 근거 자체가 공격 대상</div></div>
      <div class="fc br"><div class="id">F5</div><div class="nm">배포<br>·동기화</div>
        <div class="ds">정해진 주소에서 주기적으로 목록을 받아 최신 상태를 유지한다</div>
        <div class="no"><b>없으면</b>상태 변경이 검증 시점에 반영되지 않음</div></div>
    </div>
    <div class="fix acc">F1~F3은 검증 엔진이 소비하는 기능이고,
      <span style="color:#1A4A7A;">F4~F5는 브라우저 쪽에 실제 기능이 생기는 영역</span>이다. 다음 세 장이 그 실제 동작이다.</div>
  </div>"""
pages.append(("s07_functions.html", "제공 기능 5가지", "기능 › 전체", "2:30",
              page(7, "제공 기능 5가지", "기능 › 전체", body,
                   "다섯 중 웨일에서 부담이 큰 것과 작은 것이 어떻게 갈리는지 궁금합니다. 특히 F5의 갱신 주체를 브라우저 본체로 볼지 별도 컴포넌트로 볼지는 웨일 구조를 아는 쪽에서 판단하는 편이 정확합니다.")))

# =============================================================== P08 배포시점·진위
body = gov('목록을 받아오는 순간부터 목록은 공격 대상이 된다. 브라우저는 쓰기 전에 배포시점과 진위를 각각 확인하고, '
           '네 가지를 모두 통과한 목록만 기존 목록을 대체한다.') + """
  <div class="body">
    <div class="row fill">
      <div class="col">
        <div class="cap">① 배포시점 확인 <span class="tag spec">규격</span></div>
        <div class="tw">
        <table class="t-lite">
          <thead><tr><th style="width:36%;">확인 항목</th><th>판단</th></tr></thead>
          <tbody>
            <tr><td class="k">발행시각</td><td>지금 적용 중인 목록보다 새 것인가 — 롤백 방지</td></tr>
            <tr><td class="k">다음 갱신 예정시각</td><td>갱신 주기를 넘겨 방치되고 있지 않은가</td></tr>
            <tr><td class="k">유효기간</td><td>만료되지 않았는가</td></tr>
          </tbody>
        </table>
        </div>
        <div class="note">만료된 목록은 정상 신뢰 판정에 사용하지 않는다. 기존 목록보다 오래된 목록으로 교체하지 않는다.</div>
      </div>
      <div class="col">
        <div class="cap">② 목록 진위 확인 <span class="tag spec">규격</span></div>
        <div class="tw">
        <table class="t-lite">
          <thead><tr><th style="width:36%;">확인 항목</th><th>판단</th></tr></thead>
          <tbody>
            <tr><td class="k">출처</td><td>배포 주소가 합의된 주소인가</td></tr>
            <tr><td class="k">목록 서명</td><td>고정된 신뢰앵커로 서명이 검증되는가</td></tr>
            <tr><td class="k">서명자</td><td>목록 서명자가 지정된 발행 주체인가</td></tr>
          </tbody>
        </table>
        </div>
        <div class="note" style="border-color:#E0A96D;background:#FFFDF6;color:#7B341E;">
          신뢰앵커는 브라우저에서 변경할 수 없다. 앵커가 바뀔 수 있으면 나머지 검사가 모두 무의미해진다.</div>
      </div>
    </div>
    <div class="fix">네 가지 중 하나라도 실패하면 적용하지 않고, 기존 정상 목록을 그대로 유지한다.
      실패한 목록이 정상 목록을 밀어내지 않는다. <span class="tag inst" style="margin-left:6px;">조정 대상 아님</span></div>
  </div>"""
pages.append(("s08_integrity.html", "상세 기능 — 배포시점 확인 · 목록 진위 확인", "기능 › F4 상세", "1:00",
              page(8, "상세 기능 — 배포시점 확인 · 목록 진위 확인", "기능 › F4 상세", body,
                   "목록 서명 검증에 쓰는 신뢰앵커를 브라우저 어디에 두는 것이 웨일 배포 구조에서 자연스러운지 판단이 필요합니다. 앵커 갱신 절차는 KISA 운영정책 항목이라 별도로 다룹니다.")))

# =============================================================== P09 목록 동기화
body = gov('목록은 브라우저 릴리스와 무관하게 갱신된다. 그래서 브라우저에 동기화 기능이 새로 생기고, 주기·주체·저장 위치를 정해야 한다.') + """
  <div class="body">
    <div class="row fill">
      <div class="col" style="flex:0.95;">
        <div class="cap">동기화 요구 기능</div>
        <div class="tw">
        <table class="t-cmp">
          <thead><tr><th style="width:86px;">구분</th><th>요구 내용</th></tr></thead>
          <tbody>
            <tr><td class="k">자동 동기화</td><td>설정 주기마다 백그라운드 실행. 시작 시 주기 경과분은 즉시 실행</td></tr>
            <tr><td class="k">수동 동기화</td><td>설정 화면에서 즉시 실행. 실증용 배포 주소 변경 지원</td></tr>
            <tr><td class="k">상태 표시</td><td>적용 버전 · 발행시각 · 유효기간 · 마지막 동기화 시각</td></tr>
            <tr><td class="k">실패 처리</td><td>실패 원인과 재시도 가능 여부 표시. 기존 목록 유지</td></tr>
            <tr><td class="k">중복 방지</td><td>이전 실행이 진행 중이면 새 동기화를 시작하지 않음</td></tr>
          </tbody>
        </table>
        </div>
      </div>
      <div class="col" style="flex:1.05;">
        <div class="cap">판단할 것 — 세 가지 <span class="tag warn">웨일 판단</span></div>
        <div class="tw">
        <table class="t-cmp t-lite">
          <thead><tr><th style="width:82px;">항목</th><th style="width:38%;">선택지</th><th>대가</th></tr></thead>
          <tbody>
            <tr><td class="k" rowspan="2">갱신 주기</td><td>짧게 (예: 6시간)</td><td class="m">반영이 빠르나 트래픽·배터리 부담</td></tr>
            <tr><td>길게 (24시간~7일)</td><td class="m">부담이 작으나 철회 반영이 늦어짐</td></tr>
            <tr><td class="k" rowspan="2">갱신 주체</td><td>브라우저 본체</td><td class="m">구현이 단순하나 릴리스와 결합</td></tr>
            <tr><td>별도 컴포넌트 업데이트</td><td class="m">독립적이나 관리 항목이 하나 늘어남</td></tr>
            <tr><td class="k" rowspan="2">캐시 저장</td><td>OS 보안 저장소</td><td class="m">보호 수준이 높으나 플랫폼별 구현이 갈림</td></tr>
            <tr><td>프로파일 내 암호화 저장</td><td class="m">구현이 균일하나 보호 수준 판단 필요</td></tr>
          </tbody>
        </table>
        </div>
        <div class="sub">현재 검토안은 기본 24시간 주기 + OS 보안 저장소이나, 웨일 플랫폼 정책에 맞춰 조정한다.</div>
      </div>
    </div>
  </div>"""
pages.append(("s09_sync.html", "요구 기능 — 목록 동기화", "기능 › F5 상세", "1:00",
              page(9, "요구 기능 — 목록 동기화", "기능 › F5 상세", body,
                   "위 세 가지는 웨일 구조와 배포 정책을 아는 쪽에서 정하는 것이 정확합니다. 어느 조합이 웨일에 맞는지 의견을 주시면 저희가 판정 로직과 결과코드를 거기에 맞추겠습니다.")))

# =============================================================== P10 목록 조회
FLOW = """
<div class="flow">
  <div class="st"><div class="n">①</div><div class="x">서명 존재 확인</div><div class="z">로컬</div></div>
  <div class="st"><div class="n">②</div><div class="x">서명 구조 · ByteRange<br>문서 무결성</div><div class="z">로컬</div></div>
  <div class="st"><div class="n">③</div><div class="x">서명값 검증<br>인증서 경로 구성</div><div class="z">로컬</div></div>
  <div class="st on"><div class="n">④</div><div class="x">목록 조회</div><div class="z">목록 참조</div></div>
  <div class="st"><div class="n">⑤</div><div class="x">인증서 상태 확인<br>CRL / OCSP</div><div class="z">외부 통신</div></div>
  <div class="st"><div class="n">⑥</div><div class="x">결과 산출</div><div class="z">&nbsp;</div></div>
</div>
"""
body = gov('목록 조회는 검증의 전부가 아니라 네 번째 단계다. 앞 단계가 실패하면 조회까지 가지 않고, 조회 결과는 뒤 단계 결과를 대체하지 않는다.') + """
  <div class="body">""" + FLOW + """
    <div class="row fill">
      <div class="col" style="flex:0.85;">
        <div class="cap">④에 넣는 것</div>
        <div class="tw">
        <table class="t-cmp t-lite">
          <thead><tr><th style="width:38%;">입력</th><th>내용</th></tr></thead>
          <tbody>
            <tr><td class="k">발급자 식별정보</td><td class="m">서명자 인증서의 발급자</td></tr>
            <tr><td class="k">서비스 식별정보</td><td class="m">서비스 인증서 · 공개키 · SKI 대조값</td></tr>
            <tr><td class="k">판정 기준시각</td><td class="m">서명시각 또는 시각증거 기준</td></tr>
          </tbody>
        </table>
        </div>
      </div>
      <div class="col">
        <div class="cap">④에서 나오는 것</div>
        <div class="tw">
        <table class="t-cmp">
          <thead><tr><th style="width:32%;">결과</th><th>의미</th></tr></thead>
          <tbody>
            <tr><td class="k" style="color:#1A4A7A;">등록됨</td><td>목록에 있고 해당 시점 인정 상태</td></tr>
            <tr><td class="k">미등록</td><td class="m">목록에 없음</td></tr>
            <tr><td class="k">서비스 철회</td><td class="m">목록에 있으나 해당 시점 철회 상태</td></tr>
            <tr><td class="k">목록 만료</td><td class="m">적용 중인 목록의 유효기간 경과</td></tr>
            <tr><td class="k">목록 접근 불가</td><td class="m">목록을 가져오지 못함</td></tr>
            <tr><td class="k">목록 서명 오류</td><td class="m">목록 진위 검증 실패</td></tr>
          </tbody>
        </table>
        </div>
      </div>
    </div>
    <div class="fix">④가 하지 않는 일 — 문서 변조 판정, 서명값 유효성 판정, 폐지 여부 판정.
      이 셋은 각각 <span style="color:#5B9BD5;">② ③ ⑤</span>의 결과다.</div>
  </div>"""
pages.append(("s10_lookup.html", "핵심 검증 — 목록 조회", "검증 › 목록 조회", "1:30",
              page(10, "핵심 검증 — 목록 조회", "검증 › 목록 조회", body,
                   "④와 ⑤는 외부 참조가 필요하고 ①~③은 로컬에서 끝납니다. 로컬 결과를 먼저 표시하고 나머지를 나중에 채우는 방식이 웨일 렌더링 구조에서 자연스러운지 판단이 필요합니다.")))

# =============================================================== P11 표시 체계
ICON = '<span style="display:inline-block;width:14px;height:14px;vertical-align:-2px;margin-right:7px;%s"></span>'
body = gov('변조·무효·미등록·확인불가는 원인도 대응도 다르다. 이를 같은 경고로 뭉치면 사용자는 정상 문서를 위조로 오인하거나 그 반대를 하게 된다.') + """
  <div class="body">
    <div class="row fill">
      <div class="col" style="flex:1.25;">
        <div class="cap">반드시 구분해야 할 네 가지 <span class="tag spec">원칙</span></div>
        <div class="tw">
        <table class="t-lite">
          <thead><tr><th style="width:112px;">상황</th><th style="width:32%;">사용자가 할 수 있는 일</th><th>뭉쳤을 때의 사고</th></tr></thead>
          <tbody>
            <tr><td class="k">문서 변조</td><td>문서를 신뢰하지 않는다</td><td class="m">—</td></tr>
            <tr><td class="k">목록 미등록</td><td>발급처를 확인한다</td><td style="color:#7B341E;">정상 문서를 위조로 오인</td></tr>
            <tr><td class="k">목록 접근 불가</td><td>연결 후 재검증한다</td><td style="color:#7B341E;">장애를 위조로 오인</td></tr>
            <tr><td class="k">상태정보 미확인</td><td>재검증한다</td><td style="color:#7B341E;">확인 안 된 것을 정상으로 오인</td></tr>
          </tbody>
        </table>
        </div>
      </div>
      <div class="col">
        <div class="cap mute">표시 체계 <span class="tag inst">검토안 · 확정 아님</span></div>
        <div class="tw">
        <table class="t-lite" style="background:#FAFAFC;">
          <thead><tr><th style="width:106px;">문구</th><th>언제</th></tr></thead>
          <tbody>
            <tr><td class="k">""" + (ICON % "border:1.5px solid #CBD5E0;border-radius:50%;") + """서명 없음</td><td class="m">서명이 없는 PDF</td></tr>
            <tr><td class="k">""" + (ICON % "background:#68D391;") + """진본 확인</td><td class="m">모든 축이 통과</td></tr>
            <tr><td class="k">""" + (ICON % "background:#F6AD55;") + """주의 필요</td><td class="m">미등록·철회 등 유보가 필요</td></tr>
            <tr><td class="k">""" + (ICON % "background:#CBD5E0;") + """확인 불가</td><td class="m">외부 정보 접근 실패, 제한 검증</td></tr>
            <tr><td class="k">""" + (ICON % "background:#FC8181;") + """서명 무효</td><td class="m">문서 변조 또는 서명값 검증 실패<b style="color:#7B341E;">에 한정</b></td></tr>
          </tbody>
        </table>
        </div>
      </div>
    </div>
  </div>"""
pages.append(("s11_display.html", "표시 체계 — 무엇을 보여줄 것인가", "표시 › 결과 표현", "1:00",
              page(11, "표시 체계 — 무엇을 보여줄 것인가", "표시 › 결과 표현", body,
                   "왼쪽 네 가지를 구분한다는 원칙은 규격에서 오고, 오른쪽 아이콘·문구·상태 개수는 웨일 UX 판단 영역입니다. 5단계가 많다면 줄여도 되고, 웨일 기존 보안 표시 체계에 맞추는 편이 나을 수도 있습니다.")))

# =============================================================== P12 오프라인
body = gov('네트워크가 없어도 문서 열람은 멈추지 않되, 만료된 캐시로 정상 판정을 내리지 않는다. 이 두 문장이 오프라인 정책의 뼈대다.') + """
  <div class="body mid">
    <table class="t-lite">
      <thead><tr><th style="width:26%;">상황</th><th style="width:34%;">동작</th><th>표시</th></tr></thead>
      <tbody>
        <tr><td class="k">동기화 성공</td><td>목록 갱신 후 온라인 검증</td><td class="m">정상 결과</td></tr>
        <tr><td class="k">실패 · 유효 캐시 있음</td><td>캐시로 제한 검증</td><td style="color:#7B341E;">확인 불가 / 재검증 필요</td></tr>
        <tr><td class="k">실패 · 유효 캐시 없음</td><td>신뢰 판정 보류</td><td style="color:#1A4A7A;">PDF 열람은 유지</td></tr>
      </tbody>
    </table>
    <div class="cap">고정 원칙 <span class="tag inst">조정 대상 아님</span></div>
    <div class="row">
      <div class="col"><div class="fix">어떤 경우에도 PDF 열람 자체는 차단하지 않는다.</div></div>
      <div class="col"><div class="fix">캐시는 서명·발행시각·유효기간을 재검증한 뒤 사용하고, 만료·손상 캐시는 폐기한다.</div></div>
      <div class="col"><div class="fix">제한 검증 결과에는 캐시 기준시각과 재검증 필요 여부를 함께 기록한다.</div></div>
    </div>
  </div>"""
pages.append(("s12_offline.html", "오프라인·장애 시 동작", "표시 › 예외 처리", "0:45",
              page(12, "오프라인·장애 시 동작", "표시 › 예외 처리", body,
                   "온라인 복귀 시 재검증을 자동으로 실행할지, 사용자가 요청할 때만 실행할지 판단이 필요합니다. 자동은 편하지만 사용자가 보고 있던 결과가 예고 없이 바뀝니다.")))

# =============================================================== P13 역할 분담
body = gov('웨일은 브라우저에서 보이고 저장되는 부분을, KISA는 목록과 기준정보를 맡는 것으로 그려 두었다. 경계선의 위치는 조정 가능하다.') + """
  <div class="body">
    <div class="tw">
    <table>
      <thead><tr>
        <th style="width:50%;background:#5B9BD5;color:#FFFFFF;">웨일</th>
        <th>KISA</th>
      </tr></thead>
      <tbody>
        <tr><td>내장 PDF 뷰어의 서명 탐지·검증 트리거</td><td class="m">KR-TL 발행·서명·배포</td></tr>
        <tr><td>검증 결과 표시와 상세 근거 화면</td><td class="m">목록 서명용 신뢰앵커 제공</td></tr>
        <tr><td>설정 화면 — 배포 주소·갱신 주기·동기화 이력</td><td class="m">배포 주소 운영과 가용성</td></tr>
        <tr><td>목록 동기화 실행과 캐시 저장·만료 관리</td><td class="m">인정사업자 기준정보 관리</td></tr>
        <tr><td>보고서 저장·출력</td><td class="m">실증 정책과 예외 기준</td></tr>
      </tbody>
    </table>
    </div>
    <div class="note">㈜일렉서티는 양쪽 사이에서 검증 SDK와 KR-AdES 처리, 표준 결과코드 체계, 판정 규칙,
      검증보고서 생성(ETSI TS 119 102-2)을 제공한다.</div>
    <div class="fix acc">이 초안대로면 웨일이 새로 만드는 화면은 세 곳이다 — 검증 상태 표시, 검증 결과 상세, 설정 내 KR-TL 항목.</div>
  </div>"""
pages.append(("s13_roles.html", "역할 분담 — 웨일 / KISA", "협업 › 역할 분담", "1:00",
              page(13, '역할 분담 — 웨일 / KISA <span style="font-weight:400;color:#7BA7D4;font-size:15px;">초안</span>',
                   "협업 › 역할 분담", body,
                   "경계가 어색한 항목이 있는지 봐 주시기 바랍니다. 특히 캐시 저장·만료 관리를 SDK 안으로 넣을지 브라우저가 직접 들지는 양쪽 다 성립하므로, 웨일 쪽 선호를 듣고 정하겠습니다.")))

# =============================================================== P14 향후 일정
body = gov('실증 완료 기준일은 2026년 12월 10일이다. 역산하면 9월 중 요구사항 확정, 10월 연동, 11월 시나리오 검증이 필요하다.') + """
  <div class="body">
    <div class="row fill">
      <div class="col" style="flex:1.5;">
        <div class="cap">일정 <span class="tag inst">초안 · 킥오프에서 확정</span></div>
        <div class="tw">
        <table class="t-cmp">
          <thead><tr><th style="width:52px;">시기</th><th style="width:112px;">단계</th><th>주요 활동</th><th style="width:23%;">산출</th></tr></thead>
          <tbody>
            <tr style="background:#EBF4FF;"><td class="k">8월</td><td class="k">사전 공유</td>
                <td>KR-TL 개념·기능 공유, 판단 항목 정리</td><td class="m">이 자료, 판단 항목 회신</td></tr>
            <tr><td class="k">9월</td><td class="k">킥오프 · 요구사항 확정</td>
                <td>기능요구사항 항목별 수용·조정·보류 확정, 인터페이스 합의</td><td class="m">요구사항 추적표, 연동 규격서</td></tr>
            <tr><td class="k">10월</td><td class="k">연동 개발</td>
                <td>SDK 연동, 목록 동기화·캐시 구현, 결과 표시 구현</td><td class="m">연동 빌드</td></tr>
            <tr><td class="k">11월</td><td class="k">시나리오 검증</td>
                <td>정상·오프라인·예외 3종 시나리오 실행, 결과 대조</td><td class="m">검증 결과 기록</td></tr>
            <tr><td class="k">12월</td><td class="k">실증 완료</td>
                <td>결과 정리, 운영 판단자료 도출</td><td class="m">실증 결과 보고서</td></tr>
          </tbody>
        </table>
        </div>
      </div>
      <div class="col">
        <div class="cap warn">킥오프까지 회신이 필요한 항목</div>
        <div class="tw">
        <table class="t-cmp t-lite">
          <thead><tr><th style="width:30px;"></th><th>항목</th><th style="width:52px;">참조</th></tr></thead>
          <tbody>
            <tr><td class="k">1</td><td>목록 갱신 주기와 갱신 주체</td><td class="m">P09</td></tr>
            <tr><td class="k">2</td><td>캐시 저장 위치와 보호 수준</td><td class="m">P09</td></tr>
            <tr><td class="k">3</td><td>검증 결과 표시 상태 개수와 문구</td><td class="m">P11</td></tr>
            <tr><td class="k">4</td><td>온라인 복귀 시 재검증 시점</td><td class="m">P12</td></tr>
            <tr><td class="k">5</td><td>캐시·만료 관리의 소재</td><td class="m">P13</td></tr>
            <tr><td class="k">6</td><td>지원 웨일 버전·운영체제 범위</td><td class="m">킥오프</td></tr>
          </tbody>
        </table>
        </div>
      </div>
    </div>
    <div class="note">기능요구사항서 초안과 추적표를 별도 전달하며, 항목별 수용·조정·보류 상태를 추적표에 기록해 관리한다.</div>
  </div>"""
pages.append(("s14_schedule.html", "향후 일정", "마무리 › 일정", "1:00",
              page(14, "향후 일정", "마무리 › 일정", body)))

# =============================================================== write
for fn, _t, _c, _m, htm in pages:
    with open(os.path.join(SLIDES, fn), "w", encoding="utf-8") as f:
        f.write(htm)

VIEWER_TPL = """<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8"/>
<title>KR-TL 설명자료 v02 — 16:9 스케치 뷰어</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap" rel="stylesheet">
<style>
* { box-sizing:border-box; margin:0; padding:0; }
body { font-family:'Noto Sans KR',sans-serif; background:#F4F6FA; color:#2D3748; }
.top { background:#1A1A2E; padding:16px 32px; display:flex; align-items:baseline; gap:16px; }
.top h1 { font-size:17px; color:#FFFFFF; font-weight:700; }
.top .m { font-size:11px; color:#7BA7D4; }
.top .r { margin-left:auto; font-size:11px; color:#7BA7D4; }
.wrap { padding:24px 32px 60px; display:grid; grid-template-columns:repeat(3,1fr); gap:20px; }
.th { background:#FFFFFF; border:1px solid #E2E8F0; cursor:pointer; transition:.12s; }
.th:hover { border-color:#5B9BD5; box-shadow:0 3px 14px rgba(26,26,46,.10); }
.fr { width:100%; aspect-ratio:16/9; overflow:hidden; position:relative; border-bottom:1px solid #E2E8F0; }
.fr iframe { width:1280px; height:720px; border:0; transform-origin:0 0; position:absolute; top:0; left:0; pointer-events:none; }
.mt { padding:9px 12px; display:flex; align-items:center; gap:9px; }
.mt .no { font-size:11px; font-weight:700; color:#5B9BD5; }
.mt .nm { font-size:11.5px; color:#2D3748; flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.mt .sc { font-size:9.5px; color:#718096; background:#EDF2F7; padding:2px 7px; }
.mt .tm { font-size:10px; color:#A0AEC0; }
.ov { display:none; position:fixed; inset:0; background:rgba(16,16,30,.90); z-index:9;
      align-items:center; justify-content:center; flex-direction:column; gap:14px; }
.ov.on { display:flex; }
.ov .stage { width:1280px; height:720px; background:#FFF; box-shadow:0 8px 40px rgba(0,0,0,.4); }
.ov iframe { width:1280px; height:720px; border:0; }
.ov .bar { display:flex; align-items:center; gap:18px; color:#CBD5E0; font-size:12px; }
.ov button { background:#2D3748; color:#E2E8F0; border:0; padding:7px 16px; font-size:12px;
             cursor:pointer; font-family:inherit; }
.ov button:hover { background:#4A5568; }
@media (max-width:1400px) { .wrap { grid-template-columns:repeat(2,1fr); } }
</style>
</head>
<body>
<div class="top">
  <h1>KR-TL 설명자료 v02 — 16:9 스케치</h1>
  <span class="m">14장 · 발표 12분 + 질의 3분 · 1280×720</span>
  <span class="r">썸네일 클릭 = 확대 · ← → 이동 · ESC 닫기</span>
</div>
<div class="wrap">@@CARDS@@</div>

<div class="ov" id="ov">
  <div class="stage"><iframe id="fr"></iframe></div>
  <div class="bar">
    <button id="pv">← 이전</button>
    <span id="lb"></span>
    <button id="nx">다음 →</button>
    <button id="cl">닫기 (ESC)</button>
  </div>
</div>

<script>
var FILES=[@@FILES@@], NAMES=[@@NAMES@@], cur=0;
function fit(){
  document.querySelectorAll('.fr').forEach(function(f){
    var s=f.clientWidth/1280;
    f.querySelector('iframe').style.transform='scale('+s+')';
  });
}
window.addEventListener('resize',fit);
window.addEventListener('load',fit);
function open_(i){
  cur=i; document.getElementById('fr').src=FILES[i];
  document.getElementById('lb').textContent=(i+1)+' / '+FILES.length+'  ·  '+NAMES[i];
  document.getElementById('ov').classList.add('on');
}
document.querySelectorAll('.th').forEach(function(t){
  t.addEventListener('click',function(){ open_(+t.dataset.i); });
});
document.getElementById('cl').onclick=function(){ document.getElementById('ov').classList.remove('on'); };
document.getElementById('pv').onclick=function(){ open_((cur-1+FILES.length)%FILES.length); };
document.getElementById('nx').onclick=function(){ open_((cur+1)%FILES.length); };
document.addEventListener('keydown',function(e){
  if(!document.getElementById('ov').classList.contains('on')) return;
  if(e.key==='Escape') document.getElementById('ov').classList.remove('on');
  if(e.key==='ArrowLeft') open_((cur-1+FILES.length)%FILES.length);
  if(e.key==='ArrowRight') open_((cur+1)%FILES.length);
});
</script>
</body>
</html>"""

# =============================================================== viewer
secs = ["표지", "문제", "배경", "개념", "개념", "구조", "기능", "기능", "기능",
        "검증", "표시", "표시", "협업", "마무리"]

cards = "\n".join("""
  <div class="th" data-i="%d">
    <div class="fr"><iframe src="slides/%s" scrolling="no"></iframe></div>
    <div class="mt"><span class="no">%02d</span><span class="nm">%s</span>
      <span class="sc">%s</span><span class="tm">%s</span></div>
  </div>""" % (i, p[0], i + 1, html.escape(p[1]), secs[i], p[3])
                  for i, p in enumerate(pages))

files_js = ",".join('"slides/%s"' % p[0] for p in pages)
names_js = ",".join('"%s"' % p[1].replace('"', '') for p in pages)

viewer = VIEWER_TPL.replace("@@CARDS@@", cards).replace("@@FILES@@", files_js).replace("@@NAMES@@", names_js)

with open(os.path.join(OUT, "deck_view.html"), "w", encoding="utf-8") as f:
    f.write(viewer)

print("v02 생성 완료 — 슬라이드 %d장" % len(pages))
for p in pages:
    print("  slides/" + p[0])
print("  deck_view.html")
