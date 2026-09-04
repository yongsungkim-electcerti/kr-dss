# -*- coding: utf-8 -*-
"""KR-TL 설명자료 16:9 HTML 스케치 생성기
   원고: KR-TL_설명자료_본문_v0.2.md
   출력: slides/s01..s15.html, deck_view.html
"""
import os, html

OUT = "/home/claude/krtl_deck"
SLIDES = os.path.join(OUT, "slides")
os.makedirs(SLIDES, exist_ok=True)

DECK = "KR-TL 국내 전자서명 신뢰목록 개요"
ORG = "㈜일렉서티"
TOTAL = 15

CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body { width:1280px; height:720px; font-family:'Noto Sans KR',sans-serif;
       background:#FFFFFF; overflow:hidden; -webkit-font-smoothing:antialiased; }
.slide { width:1280px; height:720px; display:flex; flex-direction:column; }

/* Z1 HEADER */
.header { background:#1A1A2E; height:58px; flex-shrink:0; display:flex;
          align-items:center; justify-content:space-between; padding:0 48px; }
.header .t { font-size:21px; font-weight:700; color:#FFFFFF; letter-spacing:-0.01em; }
.header .b { font-size:12px; color:#7BA7D4; letter-spacing:0.04em; }

/* Z2 GOVERNING */
.gov { flex-shrink:0; background:#F6F8FC; border-bottom:1px solid #EDF2F7;
       padding:15px 48px 15px 44px; border-left:4px solid #5B9BD5; }
.gov p { font-size:16px; font-weight:700; color:#1A1A2E; line-height:1.55; }

/* Z3 BODY */
.body { flex:1; padding:24px 48px 16px; overflow:hidden; display:flex;
        flex-direction:column; gap:16px; min-height:0; }

/* Z4 ASK */
.ask { flex-shrink:0; margin:0 48px 10px; background:#FFFFFF;
       border:1px solid #DCE4F0; border-left:3px solid #A0AEC0;
       padding:10px 15px; display:flex; gap:13px; align-items:flex-start; }
.ask .lb { font-size:10px; font-weight:700; color:#718096; letter-spacing:0.08em;
           white-space:nowrap; padding-top:1px; }
.ask .tx { font-size:12.5px; color:#4A5568; line-height:1.55; }

/* Z5 FOOTER */
.footer { height:30px; flex-shrink:0; border-top:1px solid #EDF2F7;
          padding:0 48px; display:flex; align-items:center; justify-content:space-between; }
.footer span { font-size:10.5px; color:#A0AEC0; }

/* ---- components ---- */
.row { display:flex; gap:18px; }
.row > * { min-width:0; }
.col { flex:1; display:flex; flex-direction:column; gap:9px; min-height:0; }
.cap { font-size:11.5px; font-weight:700; color:#5B9BD5; letter-spacing:0.06em; }
.cap.mute { color:#A0AEC0; }
.sub { font-size:11.5px; color:#718096; line-height:1.6; }

table { width:100%; border-collapse:collapse; }
th { background:#1A1A2E; color:#BDD5EA; font-size:12.5px; font-weight:700;
     text-align:left; padding:10px 14px; letter-spacing:0.02em; }
td { font-size:13px; color:#2D3748; padding:10px 14px;
     border-bottom:1px solid #EDF2F7; line-height:1.5; vertical-align:middle; }
tr:last-child td { border-bottom:none; }

/* 표가 본문 영역을 채우게 하는 래퍼 */
.tw { flex:1; min-height:0; display:flex; }
.tw table { height:100%; }
.row.fill { flex:1; min-height:0; }
.row.fill .fix { flex:1; display:flex; align-items:center; }
.body.mid { justify-content:center; }
.t-lite th { background:#EDF2F7; color:#4A5568; }
td.k { font-weight:700; color:#1A1A2E; white-space:nowrap; }
td.m { color:#718096; }
.tag { display:inline-block; font-size:9px; font-weight:700; padding:2px 6px;
       border-radius:2px; letter-spacing:0.04em; }
.tag.spec { background:#EBF4FF; color:#1A4A7A; }
.tag.inst { background:#F0F0F5; color:#5A5A70; }

.card { border:1px solid #DCE4F0; }
.card { display:flex; flex-direction:column; }
.card > .h { background:#1A1A2E; color:#7BA7D4; font-size:11px; font-weight:700;
             padding:8px 15px; letter-spacing:0.06em; flex-shrink:0; }
.card > .c { padding:14px 15px; font-size:13px; color:#2D3748; line-height:1.7; flex:1; }
.card.on { border-color:#5B9BD5; }
.card.on > .h { background:#5B9BD5; color:#FFFFFF; }
.card.fillc { flex:1; min-height:0; }

.note { background:#F6F8FC; border:1px solid #E2E8F0; padding:12px 15px;
        font-size:12.5px; color:#4A5568; line-height:1.65; }
.fix { background:#FFFFFF; border:1px solid #DCE4F0;
       border-left:3px solid #1A1A2E; padding:12px 15px; font-size:13px;
       font-weight:700; color:#1A1A2E; line-height:1.55; }

ol.steps { list-style:none; counter-reset:s; }
ol.steps li { counter-increment:s; position:relative; padding:9px 0 9px 32px;
              font-size:13px; color:#2D3748; line-height:1.5;
              border-bottom:1px solid #F1F5F9; }
ol.steps li:last-child { border-bottom:none; }
ol.steps li::before { content:counter(s); position:absolute; left:0; top:9px;
              width:21px; height:21px; border-radius:50%; background:#EDF2F7;
              color:#4A5568; font-size:11px; font-weight:700; text-align:center;
              line-height:21px; }

ul.plain { list-style:none; }
ul.plain li { position:relative; padding:7px 0 7px 15px; font-size:13px;
              color:#2D3748; line-height:1.6; }
ul.plain li::before { content:''; position:absolute; left:0; top:15px; width:5px;
              height:5px; background:#5B9BD5; }
"""

COVER_CSS = """
.cover { width:1280px; height:720px; display:flex; flex-direction:column;
         justify-content:center; padding:0 96px; position:relative; }
.cover .rule { width:56px; height:4px; background:#5B9BD5; margin-bottom:30px; }
.cover h1 { font-size:56px; font-weight:700; color:#1A1A2E; letter-spacing:-0.02em; }
.cover h2 { font-size:26px; font-weight:400; color:#4A5568; margin-top:10px; }
.cover .sub { font-size:15px; color:#718096; margin-top:34px; line-height:1.7; }
.cover .meta { position:absolute; left:96px; bottom:64px; font-size:13px; color:#A0AEC0; }
.cover .side { position:absolute; right:0; top:0; width:14px; height:720px; background:#1A1A2E; }
"""


def page(num, title, crumb, body, ask=None, foot=None):
    ask_html = ""
    if ask:
        ask_html = f"""
  <div class="ask">
    <span class="lb">열어 둘 질문</span>
    <span class="tx">{ask}</span>
  </div>"""
    return f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8"/>
<title>{num:02d} {title}</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap" rel="stylesheet">
<style>{CSS}</style>
</head>
<body>
<div class="slide">
  <div class="header">
    <span class="t">{title}</span>
    <span class="b">{crumb}</span>
  </div>
{body}
{ask_html}
  <div class="footer">
    <span>{foot or DECK}</span>
    <span>{ORG} &nbsp;&nbsp; {num:02d} / {TOTAL}</span>
  </div>
</div>
</body>
</html>"""


def gov(text):
    return f'  <div class="gov"><p>{text}</p></div>'


pages = []

# ---------------------------------------------------------------- P01 표지
p01 = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8"/>
<title>01 표지</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap" rel="stylesheet">
<style>{CSS}{COVER_CSS}</style>
</head>
<body>
<div class="cover">
  <div class="side"></div>
  <div class="rule"></div>
  <h1>KR-TL</h1>
  <h2>국내 전자서명 신뢰목록 개요</h2>
  <div class="sub">웨일 브라우저 PDF 전자서명 검증 실증 사전 공유</div>
  <div class="meta">㈜일렉서티 &nbsp;·&nbsp; 2026.08</div>
</div>
</body>
</html>"""
pages.append(("s01_cover.html", "표지", p01))

# ---------------------------------------------------------------- P02 범위
body = f"""{gov('KR-TL은 "이 서명을 믿어도 되는가"를 브라우저가 스스로 판단할 수 있게 해주는 기계판독 목록이다. 오늘은 구조와 기능을 공유하고, 판단이 필요한 지점을 목록으로 남긴다.')}
  <div class="body">
    <div class="row" style="flex:1;">
      <div class="col">
        <div class="card on fillc">
          <div class="h">오늘 공유합니다</div>
          <div class="c">
            <ul class="plain">
              <li>신뢰목록의 개념과 KR-TL 데이터 구조</li>
              <li>KR-TL이 제공하는 기능 5가지</li>
              <li>웨일 검증 흐름에서의 개입 지점</li>
              <li>판단이 필요한 지점의 목록</li>
            </ul>
          </div>
        </div>
      </div>
      <div class="col">
        <div class="card fillc">
          <div class="h" style="background:#EDF2F7;color:#718096;">오늘 정하지 않습니다</div>
          <div class="c" style="color:#718096;">
            <ul class="plain">
              <li style="color:#718096;">요구사항 항목별 수용 여부</li>
              <li style="color:#718096;">화면 배치와 문구</li>
              <li style="color:#718096;">지원 버전·플랫폼 범위</li>
              <li style="color:#718096;">구현 분담의 확정</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>"""
pages.append(("s02_scope.html", "오늘 다룰 것과, 오늘 정하지 않을 것",
              page(2, "오늘 다룰 것과, 오늘 정하지 않을 것", "도입 › 범위", body,
                   "오늘 자료에서 빠져 있어 판단하기 어려운 정보가 있다면 발표 중 언제든 말씀해 주시기 바랍니다.")))

# ---------------------------------------------------------------- P03 현재 상황
body = f"""{gov('브라우저는 서명값이 맞는지는 확인하지만, 그 인증서를 발급한 사업자를 믿어도 되는지는 답하지 못한다. 이 빈칸을 지금은 이용기관이 각자 메우고 있다.')}
  <div class="body mid">
    <div class="row">
      <div class="col">
        <div class="card">
          <div class="h" style="background:#EDF2F7;color:#718096;">확인되는 것</div>
          <div class="c" style="color:#718096;">
            <ul class="plain">
              <li style="color:#718096;">문서 무결성</li>
              <li style="color:#718096;">서명값 일치</li>
              <li style="color:#718096;">인증서 유효기간</li>
            </ul>
            <div style="margin-top:8px;font-size:10px;color:#A0AEC0;letter-spacing:0.04em;">로컬에서 계산 가능</div>
          </div>
        </div>
      </div>
      <div class="col">
        <div class="card on">
          <div class="h">확인되지 않는 것</div>
          <div class="c">
            <ul class="plain">
              <li>발급 사업자가 인정받은 곳인가</li>
              <li>서명 시점에 그 사업자가 정상이었는가</li>
              <li>이 인증서가 어떤 용도로 발급된 것인가</li>
              <li>폐지·정지 여부를 어디서 확인하는가</li>
            </ul>
            <div style="margin-top:8px;font-size:10px;color:#5B9BD5;font-weight:700;letter-spacing:0.04em;">외부 근거가 필요</div>
          </div>
        </div>
      </div>
    </div>
    <div class="note">
      <b style="color:#1A1A2E;">그 결과</b> &nbsp;
      ① 이용기관마다 신뢰 판단 기준이 다르고, 그 기준이 소스코드에 하드코딩되어 있다. &nbsp;
      ② 사업자 지위가 바뀌어도 검증하는 쪽이 이를 알 방법이 없다. &nbsp;
      ③ 사용자에게는 "서명됨" 또는 "알 수 없음" 두 가지로만 보인다.
    </div>
  </div>"""
pages.append(("s03_gap.html", "지금 브라우저에서 PDF 서명을 열면",
              page(3, "지금 브라우저에서 PDF 서명을 열면", "문제 › 현재 상황", body,
                   "웨일 내장 PDF 뷰어에서 서명이 포함된 문서를 만났을 때 지금은 어떻게 처리되고 있는지, 이 빈칸이 실제로 이슈로 올라온 적이 있는지 궁금합니다.")))

# ---------------------------------------------------------------- P04 정의
body = f"""{gov('신뢰목록은 국가가 인정한 전자서명 사업자와 그 서비스의 현재·과거 상태를, 국가가 서명해 배포하는 기계판독 목록이다.')}
  <div class="body">
    <div class="cap">성립 조건 네 가지</div>
    <div class="tw"><table>
      <thead><tr><th style="width:170px;">조건</th><th>의미</th><th style="width:80px;">근거</th></tr></thead>
      <tbody>
        <tr><td class="k">권위 있는 발행자</td><td>목록 발행 주체가 인정제도 운영 기관과 일치한다</td><td><span class="tag inst">제도</span></td></tr>
        <tr><td class="k">기계판독</td><td>사람이 읽는 공고문이 아니라 검증 소프트웨어가 파싱하는 XML이다</td><td><span class="tag spec">규격</span></td></tr>
        <tr><td class="k">서명된 목록</td><td>목록 자체가 전자서명되어 위·변조를 검출한다</td><td><span class="tag spec">규격</span></td></tr>
        <tr><td class="k">이력 보존</td><td>현재 상태만이 아니라 언제부터 그 상태였는지를 담는다</td><td><span class="tag spec">규격</span></td></tr>
      </tbody>
    </table></div>
    <div class="note">
      <b style="color:#1A1A2E;">참고</b> &nbsp; EU는 eIDAS 체계에서 ETSI TS 119 612 규격의 Trusted List를 회원국별로 발행하고,
      이를 LOTL로 묶어 배포한다. KR-TL은 같은 구조를 국내 인정제도에 맞춰 적용한 것이다.
    </div>
    <div class="fix">인증서 목록이 아니라 서비스 상태 목록이다.</div>
  </div>"""
pages.append(("s04_definition.html", "신뢰목록이란 무엇인가",
              page(4, "신뢰목록이란 무엇인가", "개념 › 정의", body)))

# ---------------------------------------------------------------- P05 루트 저장소 비교
body = f"""{gov('루트 저장소가 "이 서버와 안전하게 연결해도 되는가"를 다룬다면, 신뢰목록은 "이 문서에 붙은 서명을 제도적으로 인정할 수 있는가"를 다룬다. 둘은 대체 관계가 아니다.')}
  <div class="body">
    <div class="tw"><table>
      <thead><tr>
        <th style="width:120px;"></th>
        <th style="width:44%;">루트 저장소 &nbsp;<span style="font-weight:400;color:#8FB3D9;">Chromium Root Store 등</span></th>
        <th>신뢰목록 &nbsp;<span style="font-weight:400;color:#8FB3D9;">KR-TL, EUTL</span></th>
      </tr></thead>
      <tbody>
        <tr><td class="k">대상</td><td class="m">TLS 서버 인증서</td><td>전자서명·타임스탬프 서비스</td></tr>
        <tr><td class="k">단위</td><td class="m">CA 루트 인증서</td><td>사업자 아래 서비스 단위</td></tr>
        <tr><td class="k">상태 표현</td><td class="m">포함 / 미포함</td><td>인정·정지·철회 등 상태값 + 상태 개시시각</td></tr>
        <tr><td class="k">시점 처리</td><td class="m">현재 시점 기준</td><td>서명 시점 기준 소급 판정</td></tr>
        <tr style="background:#F6F8FC;"><td class="k">갱신</td><td class="m">브라우저 릴리스에 동봉</td><td style="font-weight:700;color:#1A4A7A;">목록을 주기 동기화</td></tr>
        <tr style="background:#F6F8FC;"><td class="k">배포 형식</td><td class="m">브라우저 내장 바이너리</td><td style="font-weight:700;color:#1A4A7A;">서명된 XML</td></tr>
      </tbody>
    </table></div>
    <div class="sub">음영 처리한 두 줄이 웨일 입장에서 실질적으로 다른 부분이다. 브라우저가 목록을 가져오고, 캐시하고, 만료를 관리하는 기능을 새로 갖게 된다.</div>
  </div>"""
pages.append(("s05_vs_rootstore.html", "루트 저장소와 신뢰목록은 다른 층이다",
              page(5, "루트 저장소와 신뢰목록은 다른 층이다", "개념 › 기존 체계와의 차이", body,
                   "릴리스 주기와 무관하게 갱신되는 데이터를 브라우저가 들고 있는 사례가 웨일에 이미 있는지, 있다면 그 방식을 재사용할 수 있는지 궁금합니다.")))

# ---------------------------------------------------------------- P06 데이터 구조
TREE = """
<style>
.tree { font-size:11.5px; color:#2D3748; line-height:1.75; }
.tree .lv { border-left:1px solid #DCE4F0; margin-left:9px; padding-left:16px; }
.tree .nd { font-weight:700; color:#1A1A2E; font-size:12px; margin:3px 0; }
.tree .nd small { font-weight:400; color:#A0AEC0; font-size:10.5px; margin-left:6px; }
.tree .f { color:#718096; padding:2px 0; }
.tree .f.hi { color:#1A4A7A; font-weight:700; background:#EBF4FF;
              display:block; padding:3px 8px; margin:2px 0; border-left:3px solid #5B9BD5; }
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
body = f"""{gov('KR-TL은 ETSI TS 119 612 구조를 따르며, 계층은 스킴 → 사업자 → 서비스 → 상태 이력 네 단으로 내려간다.')}
  <div class="body">
    <div class="row" style="flex:1;">
      <div class="col" style="flex:1.35;">{TREE}</div>
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
pages.append(("s06_structure.html", "KR-TL이 담는 것",
              page(6, "KR-TL이 담는 것", "구조 › 데이터 모델", body,
                   "이 구조를 브라우저가 그대로 보관할지, 파싱한 뒤 필요한 필드만 정규화해서 보관할지는 웨일 쪽 저장 정책에 달려 있습니다. 어느 쪽이 편한지 의견을 듣고 싶습니다.")))

# ---------------------------------------------------------------- P07 기능 5가지
body = f"""{gov('KR-TL은 신뢰앵커 제공, 시점 기준 상태 판정, 서비스 유형 구분, 목록의 진위 보장, 배포·동기화 다섯 가지 기능을 제공한다. 검증 엔진이 KR-TL에 요청하는 모든 것은 이 다섯 안에 들어온다.')}
  <div class="body">
    <style>
    .f5 {{ display:flex; gap:14px; flex:1; min-height:0; }}
    .f5 .fc {{ flex:1; border:1px solid #DCE4F0; display:flex; flex-direction:column; }}
    .f5 .fc .id {{ background:#EDF2F7; color:#4A5568; font-size:13px; font-weight:700;
                  padding:7px 13px; letter-spacing:0.04em; }}
    .f5 .fc .nm {{ padding:12px 13px 0; font-size:14.5px; font-weight:700; color:#1A1A2E;
                  line-height:1.35; }}
    .f5 .fc .ds {{ padding:9px 13px 12px; font-size:12.5px; color:#2D3748; line-height:1.6; flex:1; }}
    .f5 .fc .no {{ border-top:1px solid #EDF2F7; padding:10px 13px; font-size:11.5px;
                  color:#718096; line-height:1.5; background:#FAFAFC; }}
    .f5 .fc .no b {{ display:block; font-size:9.5px; color:#A0AEC0; letter-spacing:0.08em;
                    margin-bottom:3px; }}
    .f5 .fc.br {{ border-color:#5B9BD5; box-shadow:inset 0 0 0 1px #5B9BD5; }}
    .f5 .fc.br .id {{ background:#5B9BD5; color:#FFFFFF; }}
    </style>
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
    <div class="fix" style="border-left-color:#5B9BD5;">
      F1~F3은 검증 엔진이 소비하는 기능이고, <span style="color:#1A4A7A;">F4~F5는 브라우저 쪽에 실제 기능이 생기는 영역</span>이다.
    </div>
  </div>"""
pages.append(("s07_functions.html", "KR-TL이 제공하는 기능 5가지",
              page(7, "KR-TL이 제공하는 기능 5가지", "기능 › 전체", body,
                   "다섯 중 웨일에서 부담이 큰 것과 작은 것이 어떻게 갈리는지 궁금합니다. 특히 F5의 갱신 주체를 브라우저 본체로 볼지 별도 컴포넌트로 볼지는 웨일 구조를 아는 쪽에서 판단하는 편이 정확합니다.")))

# ---------------------------------------------------------------- P08 F2 시점 판정
TL = """
<style>
.tl { position:relative; height:132px; margin:6px 4px 0; }
.tl .axis { position:absolute; top:52px; left:0; right:0; height:2px; background:#DCE4F0; }
.tl .pt { position:absolute; top:44px; width:11px; height:11px; border-radius:50%;
          background:#FFFFFF; border:2px solid #A0AEC0; transform:translateX(-50%); }
.tl .pt.on { border-color:#5B9BD5; background:#5B9BD5; width:15px; height:15px; top:42px; }
.tl .dt { position:absolute; top:16px; font-size:11px; font-weight:700; color:#4A5568;
          transform:translateX(-50%); white-space:nowrap; }
.tl .lb { position:absolute; top:72px; font-size:11px; color:#718096;
          transform:translateX(-50%); white-space:nowrap; }
.tl .lb.on { color:#1A4A7A; font-weight:700; }
.tl .mk { position:absolute; top:96px; font-size:10px; font-weight:700; color:#5B9BD5;
          transform:translateX(-50%); white-space:nowrap;
          border:1px solid #5B9BD5; padding:2px 8px; background:#EBF4FF; }
</style>
<div class="tl">
  <div class="axis"></div>
  <div class="pt"  style="left:8%;"></div><div class="dt" style="left:8%;">2024.03</div><div class="lb" style="left:8%;">인정 개시</div>
  <div class="pt on" style="left:38%;"></div><div class="dt" style="left:38%;">2025.06</div><div class="lb on" style="left:38%;">서명 생성</div><div class="mk" style="left:38%;">판정 기준시각</div>
  <div class="pt"  style="left:68%;"></div><div class="dt" style="left:68%;">2026.01</div><div class="lb" style="left:68%;">인정 철회</div>
  <div class="pt"  style="left:94%;"></div><div class="dt" style="left:94%;">오늘</div><div class="lb" style="left:94%;">검증 시점</div>
</div>
"""
body = f"""{gov('"지금 인정 상태인가"가 아니라 "서명한 그 시각에 인정 상태였는가"를 묻는다. 이 한 가지 때문에 목록은 현재 상태가 아닌 상태 이력을 담는다.')}
  <div class="body">
    {TL}
    <div class="tw"><table class="t-lite">
      <thead><tr><th style="width:31%;">조회 방식</th><th style="width:120px;">판정</th><th>결과</th></tr></thead>
      <tbody>
        <tr><td class="m">현재 상태만 조회</td><td class="k" style="color:#B7791F;">신뢰 불가</td><td class="m">정상적으로 만들어진 과거 서명이 전부 무효가 된다</td></tr>
        <tr style="background:#EBF4FF;"><td class="k">상태 이력 + 서명시각</td><td class="k" style="color:#1A4A7A;">신뢰 가능</td><td>서명 시점 상태가 근거로 남는다</td></tr>
        <tr><td class="m">상태 이력 + 신뢰 못 할 시각원</td><td class="k" style="color:#718096;">판정 보류</td><td class="m">서명시각을 신뢰할 수 없으면 시점 판정도 성립하지 않는다 &nbsp;→ 타임스탬프·장기검증</td></tr>
      </tbody>
    </table></div>
  </div>"""
pages.append(("s08_pit.html", "기능 상세 ① 시점 기준 상태 판정",
              page(8, "기능 상세 ① 시점 기준 상태 판정", "기능 › F2 상세", body,
                   "판정 기준시각을 사용자 화면에 어느 수준으로 노출할지 판단이 필요합니다. 감추면 같은 문서가 다른 결과를 낼 때 이유를 알 수 없고, 그대로 드러내면 일반 사용자에게 읽히지 않는 정보가 하나 늘어납니다.")))

# ---------------------------------------------------------------- P09 F4·F5
body = f"""{gov('목록을 받아오는 순간부터 목록은 공격 대상이 된다. 브라우저는 목록을 쓰기 전에 네 가지를 검사하고, 통과한 목록만 기존 목록을 대체한다.')}
  <div class="body">
    <div class="row" style="flex:1;min-height:0;">
      <div class="col" style="flex:0.9;">
        <div class="cap">목록 수용 전 검사 &nbsp;<span class="tag spec">규격</span></div>
        <ol class="steps">
          <li>출처 확인 — 배포 주소가 합의된 주소인가</li>
          <li>목록 서명 검증 — 고정된 신뢰앵커로 검증되는가</li>
          <li>발행시각 확인 — 이전 목록보다 새 것인가 <span style="color:#A0AEC0;">(롤백 방지)</span></li>
          <li>유효기간 확인 — 만료되지 않았는가</li>
        </ol>
        <div style="flex:1;min-height:0;"></div>
        <div class="fix">하나라도 실패하면 적용하지 않고, 기존 정상 목록을 유지한다.</div>
        <div class="fix" style="border-left-color:#B7791F;color:#7B341E;">신뢰앵커 변경 기능은 브라우저에 두지 않는다. 이 항목만 선택지가 아니다.</div>
      </div>
      <div class="col" style="flex:1.1;">
        <div class="cap">판단할 것 — 세 가지 &nbsp;<span class="tag inst" style="background:#FFFDE7;color:#7B341E;">웨일 판단</span></div>
        <div class="tw"><table class="t-lite">
          <thead><tr><th style="width:96px;">항목</th><th style="width:38%;">선택지</th><th>대가</th></tr></thead>
          <tbody>
            <tr><td class="k" rowspan="2">갱신 주기</td><td>짧게 (예: 6시간)</td><td class="m">반영이 빠르나 트래픽·배터리 부담</td></tr>
            <tr><td>길게 (24시간~7일)</td><td class="m">부담이 작으나 철회 반영이 늦어짐</td></tr>
            <tr><td class="k" rowspan="2">갱신 주체</td><td>브라우저 본체</td><td class="m">구현이 단순하나 릴리스와 결합</td></tr>
            <tr><td>별도 컴포넌트 업데이트</td><td class="m">독립적이나 관리 항목이 하나 늘어남</td></tr>
            <tr><td class="k" rowspan="2">캐시 저장</td><td>OS 보안 저장소</td><td class="m">보호 수준 높으나 플랫폼별 구현이 갈림</td></tr>
            <tr><td>프로파일 내 암호화 저장</td><td class="m">구현이 균일하나 보호 수준 판단 필요</td></tr>
          </tbody>
        </table></div>
        <div class="sub">현재 검토안은 기본 24시간 주기 + OS 보안 저장소이나, 웨일 플랫폼 정책에 맞춰 조정한다.</div>
      </div>
    </div>
  </div>"""
pages.append(("s09_integrity.html", "기능 상세 ② 목록의 진위 보장과 동기화",
              page(9, "기능 상세 ② 목록의 진위 보장과 동기화", "기능 › F4·F5 상세", body)))

# ---------------------------------------------------------------- P10 검증 흐름
FLOW = """
<style>
.flow { display:flex; align-items:stretch; gap:0; margin:2px 0; }
.flow .st { flex:1; border:1px solid #DCE4F0; padding:12px 11px; position:relative;
            display:flex; flex-direction:column; justify-content:center; }
.flow .st + .st { margin-left:9px; }
.flow .st::after { content:'›'; position:absolute; right:-9px; top:50%;
                   transform:translate(50%,-50%); color:#CBD5E0; font-size:17px; z-index:2; }
.flow .st:last-child::after { display:none; }
.flow .n { font-size:10px; font-weight:700; color:#A0AEC0; }
.flow .x { font-size:11.5px; font-weight:700; color:#2D3748; margin-top:4px; line-height:1.45; }
.flow .z { font-size:9.5px; color:#A0AEC0; margin-top:9px; letter-spacing:0.04em; }
.flow .st.on { border-color:#5B9BD5; border-width:2px; background:#EBF4FF; }
.flow .st.on .n { color:#5B9BD5; }
.flow .st.on .x { color:#1A4A7A; }
.flow .st.on .z { color:#5B9BD5; font-weight:700; }
</style>
<div class="flow">
  <div class="st"><div class="n">①</div><div class="x">서명 존재 확인</div><div class="z">로컬</div></div>
  <div class="st"><div class="n">②</div><div class="x">서명 구조 · ByteRange<br>문서 무결성</div><div class="z">로컬</div></div>
  <div class="st"><div class="n">③</div><div class="x">서명값 검증<br>인증서 경로 구성</div><div class="z">로컬</div></div>
  <div class="st on"><div class="n">④</div><div class="x">KR-TL 대조<br>등록 여부 · 상태 · 기준시각</div><div class="z">목록 참조</div></div>
  <div class="st"><div class="n">⑤</div><div class="x">인증서 상태 확인<br>CRL / OCSP</div><div class="z">외부 통신</div></div>
  <div class="st"><div class="n">⑥</div><div class="x">결과 산출</div><div class="z">&nbsp;</div></div>
</div>
"""
body = f"""{gov('KR-TL 조회는 검증의 전부가 아니라 네 번째 단계다. 앞 단계가 실패하면 조회까지 가지 않고, 조회 결과는 뒤 단계 결과를 대체하지 않는다.')}
  <div class="body mid">
    {FLOW}
    <div class="row">
      <div class="col">
        <div class="card on">
          <div class="h">④에서 하는 일</div>
          <div class="c">서명자 인증서의 발급자·서비스 식별정보를 적용 중인 KR-TL과 대조한다. 결과는
            <b>등록됨 / 미등록 / 서비스 철회 / 목록 만료 / 목록 접근 불가 / 목록 서명 오류</b>로 구분한다.</div>
        </div>
      </div>
      <div class="col">
        <div class="card">
          <div class="h" style="background:#EDF2F7;color:#718096;">④에서 하지 않는 일</div>
          <div class="c" style="color:#718096;">문서 변조 판정, 서명값 유효성 판정, 폐지 여부 판정.
            이 셋은 각각 <b style="color:#4A5568;">② ③ ⑤</b>의 결과다.</div>
        </div>
      </div>
    </div>
  </div>"""
pages.append(("s10_flow.html", "검증 흐름에서 KR-TL이 개입하는 지점",
              page(10, "검증 흐름에서 KR-TL이 개입하는 지점", "연동 › 검증 흐름", body,
                   "④와 ⑤는 외부 참조가 필요하고 ①~③은 로컬에서 끝납니다. 로컬 결과를 먼저 표시하고 나머지를 나중에 채우는 방식이 웨일 렌더링 구조에서 자연스러운지 판단이 필요합니다.")))

# ---------------------------------------------------------------- P11 결과 표시
ICONS = """
<style>
.ic { display:inline-block; width:15px; height:15px; vertical-align:-3px; margin-right:7px; }
</style>
"""
body = f"""{gov('변조·무효·미등록·확인불가는 원인도 대응도 다르다. 이를 같은 경고로 뭉치면 사용자는 정상 문서를 위조로 오인하거나 그 반대를 하게 된다.')}
  <div class="body">{ICONS}
    <div class="row" style="flex:1;">
      <div class="col" style="flex:1.25;">
        <div class="cap">반드시 구분해야 할 네 가지 &nbsp;<span class="tag spec">원칙</span></div>
        <div class="tw"><table class="t-lite">
          <thead><tr><th style="width:110px;">상황</th><th style="width:30%;">사용자가 할 수 있는 일</th><th>뭉쳤을 때의 사고</th></tr></thead>
          <tbody>
            <tr><td class="k">문서 변조</td><td>문서를 신뢰하지 않는다</td><td class="m">—</td></tr>
            <tr><td class="k">KR-TL 미등록</td><td>발급처를 확인한다</td><td style="color:#7B341E;">정상 문서를 위조로 오인</td></tr>
            <tr><td class="k">목록 접근 불가</td><td>연결 후 재검증한다</td><td style="color:#7B341E;">장애를 위조로 오인</td></tr>
            <tr><td class="k">상태정보 미확인</td><td>재검증한다</td><td style="color:#7B341E;">확인 안 된 것을 정상으로 오인</td></tr>
          </tbody>
        </table></div>
      </div>
      <div class="col">
        <div class="cap mute">표시 체계 &nbsp;<span class="tag inst">검토안 · 확정 아님</span></div>
        <div class="tw"><table class="t-lite" style="background:#FAFAFC;">
          <thead><tr><th style="width:104px;">문구</th><th>언제</th></tr></thead>
          <tbody>
            <tr><td class="k"><span class="ic" style="border:1.5px solid #CBD5E0;border-radius:50%;"></span>서명 없음</td><td class="m">서명이 없는 PDF</td></tr>
            <tr><td class="k"><span class="ic" style="background:#68D391;"></span>진본 확인</td><td class="m">모든 축이 통과</td></tr>
            <tr><td class="k"><span class="ic" style="background:#F6AD55;"></span>주의 필요</td><td class="m">미등록·철회 등 유보가 필요</td></tr>
            <tr><td class="k"><span class="ic" style="background:#CBD5E0;"></span>확인 불가</td><td class="m">외부 정보 접근 실패, 제한 검증</td></tr>
            <tr><td class="k"><span class="ic" style="background:#FC8181;"></span>서명 무효</td><td class="m">문서 변조 또는 서명값 검증 실패<b style="color:#7B341E;">에 한정</b></td></tr>
          </tbody>
        </table></div>
      </div>
    </div>
  </div>"""
pages.append(("s11_display.html", "결과를 어떻게 나눠 보여줄 것인가",
              page(11, "결과를 어떻게 나눠 보여줄 것인가", "연동 › 결과 표시", body,
                   "왼쪽 네 가지를 구분한다는 원칙은 규격에서 오고, 오른쪽 아이콘·문구·상태 개수는 웨일 UX 판단 영역입니다. 5단계가 많다면 줄여도 되고, 웨일 기존 보안 표시 체계에 맞추는 편이 나을 수도 있습니다.")))

# ---------------------------------------------------------------- P12 오프라인
BR = """
<style>
.br { display:flex; align-items:center; gap:14px; }
.br .k { border:1px solid #DCE4F0; padding:10px 14px; font-size:11.5px; font-weight:700;
         color:#1A1A2E; white-space:nowrap; }
.br .arm { flex:1; display:flex; flex-direction:column; gap:9px; }
.br .ln { display:flex; align-items:center; gap:10px; }
.br .tagx { font-size:10px; font-weight:700; padding:3px 9px; white-space:nowrap; }
.br .ok { background:#EBF4FF; color:#1A4A7A; }
.br .no { background:#F0F0F5; color:#5A5A70; }
.br .bx { flex:1; border:1px solid #DCE4F0; padding:8px 12px; font-size:11.5px; color:#2D3748; }
.br .bx b { color:#1A1A2E; }
.br .ar { color:#CBD5E0; font-size:15px; }
</style>
<div class="br">
  <div class="k">동기화 시도</div>
  <div class="ar">›</div>
  <div class="arm">
    <div class="ln"><span class="tagx ok">성공</span><div class="bx">목록 갱신 &nbsp;›&nbsp; <b>온라인 검증</b></div></div>
    <div class="ln"><span class="tagx no">실패 · 유효 캐시 있음</span><div class="bx">제한 검증 &nbsp;›&nbsp; <b>"확인 불가 / 재검증 필요" 표시</b></div></div>
    <div class="ln"><span class="tagx no">실패 · 캐시 없음</span><div class="bx">신뢰 판정 보류 &nbsp;›&nbsp; <b>PDF 열람은 유지</b></div></div>
  </div>
</div>
"""
body = f"""{gov('네트워크가 없어도 문서 열람은 멈추지 않되, 만료된 캐시로 정상 판정을 내리지 않는다. 이 두 문장이 오프라인 정책의 뼈대다.')}
  <div class="body mid">
    {BR}
    <div class="cap">고정 원칙 &nbsp;<span class="tag inst" style="background:#F0F0F5;">조정 대상 아님</span></div>
    <div class="row">
      <div class="col"><div class="fix">어떤 경우에도 PDF 열람 자체는 차단하지 않는다.</div></div>
      <div class="col"><div class="fix">캐시는 서명·발행시각·유효기간을 재검증한 뒤 사용하고, 만료·손상 캐시는 폐기한다.</div></div>
      <div class="col"><div class="fix">제한 검증 결과에는 캐시 기준시각과 재검증 필요 여부를 함께 기록한다.</div></div>
    </div>
  </div>"""
pages.append(("s12_offline.html", "오프라인·장애 시 동작",
              page(12, "오프라인·장애 시 동작", "연동 › 예외 처리", body,
                   "온라인 복귀 시 재검증을 자동으로 실행할지, 사용자가 요청할 때만 실행할지 판단이 필요합니다. 자동은 편하지만 사용자가 보고 있던 결과가 예고 없이 바뀝니다.")))

# ---------------------------------------------------------------- P13 역할 경계
body = f"""{gov('현재는 웨일이 브라우저에서 보이고 저장되는 부분을, 일렉서티가 판정 로직과 결과코드를, KISA가 목록과 기준정보를 맡는 것으로 그려 두었다. 경계선의 위치는 조정 가능하다.')}
  <div class="body">
    <div class="tw"><table>
      <thead><tr>
        <th style="width:33.4%;background:#5B9BD5;color:#FFFFFF;">웨일</th>
        <th style="width:33.3%;">일렉서티</th>
        <th>KISA</th>
      </tr></thead>
      <tbody>
        <tr><td>내장 PDF 뷰어의 서명 탐지·검증 트리거</td><td class="m">검증 SDK 및 연동 가이드</td><td class="m">KR-TL 발행·서명·배포</td></tr>
        <tr><td>메뉴바 상태 표시, 상세 근거 화면</td><td class="m">KR-AdES 처리, 인증서 경로·상태 판정</td><td class="m">신뢰앵커 및 배포 주소 제공</td></tr>
        <tr><td>설정 화면 (주소·주기·동기화 이력)</td><td class="m">표준 결과코드 체계</td><td class="m">발행기관 기준정보 API</td></tr>
        <tr><td>목록 캐시 저장·만료 관리</td><td class="m">XML 검증보고서 생성 (ETSI TS 119 102-2)</td><td class="m">보관서비스 확인 기준</td></tr>
        <tr><td>보고서 저장·출력 UI</td><td class="m">오프라인 제한 검증 판정 규칙</td><td class="m">실증 정책·예외 기준</td></tr>
      </tbody>
    </table></div>
    <div class="fix" style="border-left-color:#5B9BD5;">
      이 초안대로면 웨일이 새로 만드는 화면은 세 곳이다 — 메뉴바 상태 표시, 검증 결과 상세, 설정 내 KR-TL 항목.
    </div>
  </div>"""
pages.append(("s13_roles.html", "역할 경계 (초안)",
              page(13, "역할 경계 <span style=\"font-weight:400;color:#7BA7D4;font-size:15px;\">초안</span>", "협업 › 역할 분담", body,
                   "경계가 어색한 항목이 있는지 봐 주시기 바랍니다. 특히 캐시 저장·만료 관리를 SDK 안으로 넣을지 브라우저가 직접 들지는 양쪽 다 성립하므로, 웨일 쪽 선호를 듣고 정하겠습니다.")))

# ---------------------------------------------------------------- P14 3층
LAYERS = """
<style>
.ly { border:1px solid #DCE4F0; display:flex; align-items:center; padding:14px 18px; gap:20px; }
.ly + .ly { border-top:none; }
.ly .n { width:150px; font-size:12px; font-weight:700; color:#1A1A2E; }
.ly .c { flex:1; font-size:12px; color:#4A5568; }
.ly .r { font-size:10.5px; color:#A0AEC0; font-weight:700; letter-spacing:0.04em; white-space:nowrap; }
.ly.off { background:#FAFAFC; }
.ly.off .n, .ly.off .c { color:#A0AEC0; }
.ly.on { background:#EBF4FF; border-color:#5B9BD5; border-width:2px; }
.ly.on .n { color:#1A4A7A; } .ly.on .r { color:#5B9BD5; }
</style>
<div>
  <div class="ly off"><div class="n">TLS 연결 신뢰</div><div class="c">Chromium Root Store</div><div class="r">영향 없음</div></div>
  <div class="ly on"><div class="n">문서 서명 신뢰</div>
    <div class="c" style="color:#1A1A2E;">Adobe AATL · EUTL &nbsp;&nbsp;|&nbsp;&nbsp; <b>KR-TL</b></div>
    <div class="r">병존 · 국내 인정제도 반영</div></div>
  <div class="ly"><div class="n">검증 절차 규격</div><div class="c">ETSI EN 319 102-1</div><div class="r">공통 판정 절차</div></div>
</div>
"""
body = f"""{gov('KR-TL은 브라우저의 기존 신뢰체계를 바꾸지 않는다. TLS 신뢰와는 층이 다르고, 문서 서명 신뢰체계와는 병존한다.')}
  <div class="body">
    {LAYERS}
    <ul class="plain">
      <li>KR-TL 적용이 기존 TLS 인증서 검증 경로에 개입하지 않는다.</li>
      <li>하나의 문서가 여러 신뢰체계에서 동시에 검증될 수 있으며, KR-TL 미등록이 다른 체계의 판정을 부정하지 않는다.</li>
      <li>판정 절차와 결과 표현은 국제 규격을 따르므로 결과를 국외 검증 도구와 대조할 수 있다.</li>
    </ul>
  </div>"""
pages.append(("s14_layers.html", "기존 신뢰체계와의 관계",
              page(14, "기존 신뢰체계와의 관계", "보완 › 영향 범위", body,
                   "웨일이 Chromium 업스트림과의 차이를 관리하는 기준선이 있다면, 이 기능이 그 기준에서 어느 쪽에 놓이는지 판단해 주시면 구현 형태를 거기에 맞추겠습니다.")))

# ---------------------------------------------------------------- P15 판단 목록
body = f"""{gov('오늘 남기고 가는 것은 결정 사항이 아니라 판단 목록이다. 아래 여섯 가지에 대한 웨일팀의 판단이 다음 자리의 출발점이 된다.')}
  <div class="body">
    <div class="row" style="flex:1;">
      <div class="col" style="flex:1.3;">
        <div class="cap">판단 목록</div>
        <div class="tw"><table>
          <thead><tr><th style="width:40px;"></th><th>판단할 것</th><th style="width:64px;">참조</th></tr></thead>
          <tbody>
            <tr><td class="k">1</td><td>목록 갱신 주기와 갱신 주체</td><td class="m">P09</td></tr>
            <tr><td class="k">2</td><td>캐시 저장 위치와 보호 수준</td><td class="m">P09</td></tr>
            <tr><td class="k">3</td><td>검증 결과 표시 상태 개수와 문구</td><td class="m">P11</td></tr>
            <tr><td class="k">4</td><td>판정 기준시각의 노출 수준</td><td class="m">P08</td></tr>
            <tr><td class="k">5</td><td>온라인 복귀 시 재검증 시점</td><td class="m">P12</td></tr>
            <tr><td class="k">6</td><td>캐시·만료 관리의 소재 (브라우저 / SDK)</td><td class="m">P13</td></tr>
          </tbody>
        </table></div>
        <div class="sub">지원 웨일 버전·운영체제 범위, 문서 접근 범위와 동의 UX 배치, SDK 호출 방식과 허용 출처는 킥오프에서 함께 확정한다.</div>
      </div>
      <div class="col">
        <div class="cap mute">실증 시나리오</div>
        <div class="tw"><table class="t-lite">
          <thead><tr><th style="width:80px;">시나리오</th><th>확인 사항</th></tr></thead>
          <tbody>
            <tr><td class="k">정상</td><td class="m">자동 검증 실행, KR-TL 대조, 결과 분리 표시</td></tr>
            <tr><td class="k">오프라인</td><td class="m">캐시 기반 제한 검증, 재검증 안내</td></tr>
            <tr><td class="k">예외</td><td class="m">변조·미등록·확인불가의 구분 표시, 열람 유지</td></tr>
          </tbody>
        </table></div>
        <div class="note">기능요구사항서 초안과 추적표를 별도 전달하며, 항목별 수용·조정·보류 상태를 추적표에 기록해 관리한다.
          <b style="color:#1A1A2E;">실증 완료 기준일은 2026.12.10이다.</b></div>
      </div>
    </div>
  </div>"""
pages.append(("s15_decisions.html", "웨일팀이 판단할 것",
              page(15, "웨일팀이 판단할 것", "마무리 › 판단 목록", body)))

# ---------------------------------------------------------------- write
for fn, _t, htm in pages:
    with open(os.path.join(SLIDES, fn), "w", encoding="utf-8") as f:
        f.write(htm)

# ---------------------------------------------------------------- deck viewer
titles = [
    ("s01_cover.html", "표지"),
    ("s02_scope.html", "오늘 다룰 것과, 오늘 정하지 않을 것"),
    ("s03_gap.html", "지금 브라우저에서 PDF 서명을 열면"),
    ("s04_definition.html", "신뢰목록이란 무엇인가"),
    ("s05_vs_rootstore.html", "루트 저장소와 신뢰목록은 다른 층이다"),
    ("s06_structure.html", "KR-TL이 담는 것"),
    ("s07_functions.html", "KR-TL이 제공하는 기능 5가지"),
    ("s08_pit.html", "기능 상세 ① 시점 기준 상태 판정"),
    ("s09_integrity.html", "기능 상세 ② 목록의 진위 보장과 동기화"),
    ("s10_flow.html", "검증 흐름에서 KR-TL이 개입하는 지점"),
    ("s11_display.html", "결과를 어떻게 나눠 보여줄 것인가"),
    ("s12_offline.html", "오프라인·장애 시 동작"),
    ("s13_roles.html", "역할 경계 (초안)"),
    ("s14_layers.html", "기존 신뢰체계와의 관계"),
    ("s15_decisions.html", "웨일팀이 판단할 것"),
]
secs = ["표지", "도입", "문제", "개념", "개념", "구조", "기능", "기능", "기능",
        "연동", "연동", "연동", "협업", "보완", "마무리"]
mins = ["0:20", "0:30", "1:30", "1:00", "1:00", "1:30", "2:30", "1:00", "1:00",
        "1:30", "1:00", "0:45", "1:00", "0:45", "1:00"]

cards = "\n".join(f"""
  <div class="th" data-i="{i}">
    <div class="fr"><iframe src="slides/{fn}" scrolling="no"></iframe></div>
    <div class="mt"><span class="no">{i+1:02d}</span><span class="nm">{html.escape(t)}</span>
      <span class="sc">{secs[i]}</span><span class="tm">{mins[i]}</span></div>
  </div>""" for i, (fn, t) in enumerate(titles))

files_js = ",".join(f'"slides/{fn}"' for fn, _ in titles)
names_js = ",".join('"%s"' % t.replace('"', '') for _, t in titles)

viewer = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8"/>
<title>KR-TL 설명자료 — 16:9 스케치 뷰어</title>
<link href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap" rel="stylesheet">
<style>
* {{ box-sizing:border-box; margin:0; padding:0; }}
body {{ font-family:'Noto Sans KR',sans-serif; background:#F4F6FA; color:#2D3748; }}
.top {{ background:#1A1A2E; padding:16px 32px; display:flex; align-items:baseline; gap:16px; }}
.top h1 {{ font-size:17px; color:#FFFFFF; font-weight:700; }}
.top .m {{ font-size:11px; color:#7BA7D4; }}
.top .r {{ margin-left:auto; font-size:11px; color:#7BA7D4; }}
.wrap {{ padding:24px 32px 60px; display:grid; grid-template-columns:repeat(3,1fr); gap:20px; }}
.th {{ background:#FFFFFF; border:1px solid #E2E8F0; cursor:pointer; transition:.12s; }}
.th:hover {{ border-color:#5B9BD5; box-shadow:0 3px 14px rgba(26,26,46,.10); }}
.fr {{ width:100%; aspect-ratio:16/9; overflow:hidden; position:relative; border-bottom:1px solid #E2E8F0; }}
.fr iframe {{ width:1280px; height:720px; border:0; transform-origin:0 0; position:absolute; top:0; left:0; pointer-events:none; }}
.mt {{ padding:9px 12px; display:flex; align-items:center; gap:9px; }}
.mt .no {{ font-size:11px; font-weight:700; color:#5B9BD5; }}
.mt .nm {{ font-size:11.5px; color:#2D3748; flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }}
.mt .sc {{ font-size:9.5px; color:#718096; background:#EDF2F7; padding:2px 7px; }}
.mt .tm {{ font-size:10px; color:#A0AEC0; }}
.ov {{ display:none; position:fixed; inset:0; background:rgba(16,16,30,.90); z-index:9;
      align-items:center; justify-content:center; flex-direction:column; gap:14px; }}
.ov.on {{ display:flex; }}
.ov .stage {{ width:1280px; height:720px; background:#FFF; box-shadow:0 8px 40px rgba(0,0,0,.4); }}
.ov iframe {{ width:1280px; height:720px; border:0; }}
.ov .bar {{ display:flex; align-items:center; gap:18px; color:#CBD5E0; font-size:12px; }}
.ov button {{ background:#2D3748; color:#E2E8F0; border:0; padding:7px 16px; font-size:12px;
             cursor:pointer; font-family:inherit; }}
.ov button:hover {{ background:#4A5568; }}
@media (max-width:1400px) {{ .wrap {{ grid-template-columns:repeat(2,1fr); }} }}
</style>
</head>
<body>
<div class="top">
  <h1>KR-TL 설명자료 — 16:9 스케치</h1>
  <span class="m">15장 · 발표 12분 + 질의 3분 · 1280×720</span>
  <span class="r">썸네일 클릭 = 확대 · ← → 이동 · ESC 닫기</span>
</div>
<div class="wrap">{cards}</div>

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
var FILES=[{files_js}], NAMES=[{names_js}], cur=0;
function fit(){{
  document.querySelectorAll('.fr').forEach(function(f){{
    var s=f.clientWidth/1280;
    f.querySelector('iframe').style.transform='scale('+s+')';
  }});
}}
window.addEventListener('resize',fit);
window.addEventListener('load',fit);
function open_(i){{
  cur=i; document.getElementById('fr').src=FILES[i];
  document.getElementById('lb').textContent=(i+1)+' / '+FILES.length+'  ·  '+NAMES[i];
  document.getElementById('ov').classList.add('on');
}}
document.querySelectorAll('.th').forEach(function(t){{
  t.addEventListener('click',function(){{ open_(+t.dataset.i); }});
}});
document.getElementById('cl').onclick=function(){{ document.getElementById('ov').classList.remove('on'); }};
document.getElementById('pv').onclick=function(){{ open_((cur-1+FILES.length)%FILES.length); }};
document.getElementById('nx').onclick=function(){{ open_((cur+1)%FILES.length); }};
document.addEventListener('keydown',function(e){{
  if(!document.getElementById('ov').classList.contains('on')) return;
  if(e.key==='Escape') document.getElementById('ov').classList.remove('on');
  if(e.key==='ArrowLeft') open_((cur-1+FILES.length)%FILES.length);
  if(e.key==='ArrowRight') open_((cur+1)%FILES.length);
}});
</script>
</body>
</html>"""

with open(os.path.join(OUT, "deck_view.html"), "w", encoding="utf-8") as f:
    f.write(viewer)

print("생성 완료")
for fn, _t, _h in pages:
    print("  slides/" + fn)
print("  deck_view.html")
