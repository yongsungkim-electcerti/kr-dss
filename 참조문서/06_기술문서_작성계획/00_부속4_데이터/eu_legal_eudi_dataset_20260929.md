# EU/EEA 30개국 eIDAS 법·거버넌스 이행과 EUDI 지갑 현황 데이터셋

조사일: 2026-09-29. WebSearch를 쓸 수 없어 공식 사이트를 직접 열어 읽었다.

**출처 표기**
- `확인됨`: 해당 페이지를 직접 읽은 경우
- `미확인`: 차단·접속 실패로 못 읽었거나 찾지 못한 경우
- `(비공식)`: 공식 사이트가 막혀 법령 미러로 대체한 경우. CZ는 zakonyprolidi, CY는 cylaw, BE는 etaamb를 썼다.

**근거 파일**: `group_A.md`, `group_B.md`, `group_C.md`(국가별 원자료와 URL), `lsp_matrix.md`, `apt_countries.txt`(APTITUDE 국가 페이지 원문)

## 0. 공통 근거

| 항목 | 내용 | 상태 |
|---|---|---|
| 신뢰목록 운영자 | 로컬 EU LOTL TLv6 Seq395(2026-09-24)의 국가별 SchemeOperatorName(en) | 확인됨 |
| LSP 1차(2023) | **POTENTIAL**: AT BE CY CZ EE FI FR DE EL HU IT LT LU NL PL PT SK SI ES(+UA) | 확인됨 |
| | **NOBID**: NO DK IS LV IT가 지갑 발급, DE는 DSGV(저축은행협회)만 참여 | 확인됨 |
| | **DC4EU**: 국기 목록 기준 AT BE CZ DK FI FR DE EL HU IE IT LT LU MT NL PL PT RO ES SE + NO. 본문은 "22 MS"라 적어 국기 목록(EU 20개국)과 맞지 않음 | 확인됨 |
| | **EWC**: 사이트 403. GitHub에서 "Sweden·Finland가 조정하고 18개 MS+UA 참여"까지만 확인. 나머지 명단은 미확인 | 부분 확인 |
| LSP 2차(2025~) | **APTITUDE**: CZ EE(2026 봄 합류) FR DE EL HU IT LV LT NL PL PT(+UA) | 확인됨 |
| | **WE BUILD**: 참여기관 소재국 기준 AT BE BG CZ DK EE FI FR DE EL HU IE IT LU MT NL NO PL PT RO SI ES SE(+BA MD CH UK US). 28개국 233개 기관 | 확인됨 |
| EEA 편입 | 910/2014는 JCD 022/2018로 편입되어 EEA에서 2019-06-01 발효 | 확인됨 (efta.int) |
| | 2024/1183은 "under scrutiny"로 **아직 EEA 미편입**. IS·LI·NO에는 아직 EUDI 의무가 법적으로 발효되지 않음 | 확인됨 (efta.int) |
| 인증 월렛 목록(제5d조) | EC가 공표한 인증·통지 월렛 목록을 찾지 못함. eidas.ec.europa.eu/efda는 JS 전용 | 미확인 |
| EC 공식 일정 | "EUDI wallets set to launch at the end of 2026" (EC EUDI News) | 확인됨 |

## 1. 국가별 표

| 국 | 신뢰목록 운영자(LOTL, 확인됨) | 신뢰서비스 국내법 | eID 법·거버넌스 | EUDI 지갑(명칭·상태) | LSP | EUDI 국내 이행법 | 출처 상태 |
|---|---|---|---|---|---|---|---|
| AT | RTR (Rundfunk und Telekom Regulierungs-GmbH) | SVG, BGBl. I 50/2016 (확인됨) | E-GovG, BGBl. I 10/2004 (최종 117/2024), ID Austria (확인됨) | eAusweise 앱 운영 중(면허·등록증·ID·연령). EU Wallet은 "soll bald"라고만 안내 → **개발 중** | POT, DC4EU, WB | RIS 검색에서 찾지 못함 → **미확인** | RIS OGD API, digitalaustria.gv.at 확인됨 |
| BE | FPS Economy (SPF Économie) | Loi 21-07-2016 (numac 2016009485) (확인됨, 비공식) | Loi 18-07-2017 전자식별 + 왕령 2017-10-22, FAS 인정(BOSA). 로컬 기록 확인됨 | **미확인** (공식 사이트 CAPTCHA) | POT, DC4EU, WB | 미확인 | etaamb(비공식) |
| BG | Communications Regulation Commission (CRC) | 미확인 (ЗЕДЕУУ 추정) | 미확인 | **미확인** | WB | 미확인 | lex.bg·정부 도메인 모두 차단 |
| HR | Ministry of Justice, Public Administration and Digital Transformation | 910/2014 이행법, NN 62/2017 (확인됨) | NIAS / e-Građani (법령명 미확인) | mGrađani 앱 운영 중(204k 이용자, 연령확인). EUDI 월렛이라는 표현은 없음 → **국내앱만 확인** | 확인 안 됨 | "새 법적 틀 준비 중" → **준비 중** | narodne-novine, mpu.gov.hr(2026-09-21) 확인됨 |
| CY | Department of Electronic Communications | N. 55(I)/2018, 개정 60(I)/2021 (확인됨, 비공식) | eID 사업은 확인, 법령은 미확인 | Digital Citizen(Ψηφιακός Πολίτης) 앱 운영 중. EUDI 연계는 미확인 → **국내앱만 확인** | POT | 미확인 | cylaw, gov.cy 확인됨 |
| CZ | Digital and Information Agency (DIA) | Zákon 297/2016 Sb. (확인됨) | Zákon 250/2017 Sb. 전자식별법. 인정 관리자 제도(DIA) (확인됨) | eDoklady 운영 중(2024-01~). EUDIW는 별도로 **개발 중**: 인증스킴 초안 회람 2026-09-10, 상용 운영자 모집 | POT, DC4EU, APT, WB | DIA가 입법 준비 중 → **초안 준비** | dia.gov.cz/eudiw, APTITUDE 확인됨 |
| DK | Danish Agency for Digitisation (Digst) | LOV nr 617 af 08/06/2016 (확인됨) | MitID·NemLog-in법 LBK 333/2025 + NSIS v2.1 (확인됨) | **AltID** 운영 중. Digst가 이를 EUDI 월렛으로 제공 예정 → **운영(전환형)** | NOBID, DC4EU, WB | **제정: LOV nr 301 af 24/02/2026**, 2026-04-01 시행 (확인됨) | retsinformation API, digst.dk |
| EE | Estonian Information System Authority (RIA) | EUTS(e-식별·신뢰서비스법), RT I 25.10.2016, 1 (현행 RT I 30.12.2025, 16) (확인됨) | ITDS 신분증명문서법 (현행 RT I 03.06.2026, 7) (확인됨) | Digikukkur(EUDI Wallet). 운영사업자 조달 진행 → **개발 중** | POT, APT, WB | 현행 EUTS에 월렛 조항 없음 → **미확인** | riigiteataja API, ria.ee, APTITUDE 확인됨 |
| FI | Traficom (Finnish Transport and Communications Agency) | 법 617/2009. 811/2026으로 개정되어 2026-10-01 시행, 개정 eIDAS에 맞춤 (확인됨) | 617/2009 + Traficom 규정 M72B + 신뢰망 신고제. 로컬 기록 확인됨 | 발급자는 DVV(법 812/2026). 앱 명칭·상태는 dvv.fi 403으로 **미확인** | POT, DC4EU, EWC(조정국), WB | **제정: 디지털 신분증명법 812/2026**(2026-09-04 공포, 2026-10-01 시행). 제3조 42호에 EUDI 월렛 (확인됨) | finlex opendata |
| FR | ANSSI | 미확인 (Légifrance 403) | Décret 2022-676(SGIN, France Identité) (확인됨). CPCE L.102 + 데크레 2022-1004 + ANSSI MIE 인증은 로컬 기록 확인됨 | **France Identité** 운영 중. 미래 EUDI 월렛으로 지정 → **운영(전환형)** | POT, DC4EU, APT, WB | 미확인 | france-identite.gouv.fr, APTITUDE 확인됨 |
| DE | Bundesnetzagentur (Federal Network Agency) | VDG 신뢰서비스법 (확인됨) | PAuswG(§10a 모바일 eID), BSI TR-03107-1. BMDS 소관 (확인됨) | **d-you**. 샌드박스 단계(PID 통합), 2027-01-02 go-live 예정 → **샌드박스/시범** | POT, NOBID(DSGV), DC4EU, APT, WB | **법안: DIdG** 2026-03-26 공개, 2026-05-20 연방내각 의결. 의회 통과는 미확인 | bmds.bund.de, gesetze-im-internet 확인됨 |
| EL | EETT (Hellenic Telecommunications and Post Commission) | Law 4070/2012 제12조(1)(y): EETT를 910/2014 감독기관으로 지정 (확인됨) | 미확인 | **Gov.gr Wallet** 운영 중(2022~, 근거 Ν.4954/2022 제80조 등). EUDIW 호환판은 2026말 목표로 개발 → **운영(전환형)** | POT, DC4EU, APT, WB | 미확인 | eett.gr, wallet.gov.gr, APTITUDE 확인됨 |
| HU | NMHH (National Media and Infocommunications Authority) | 미확인 (njt.hu 접속 실패) | 미확인. KAÜ 로그인은 DÁP 앱 | **DÁP Digitális Tárca**. 2025말 v2에서 PID·PUB-EAA 발급 → **운영(전환형)** | POT, DC4EU, APT, WB | 미확인 | dap.gov.hu, APTITUDE 확인됨 |
| IE | Department of Culture, Communications and Sport. 단 gov.ie는 DECC가 감독·TL을 담당한다고 표기 → 불일치 | Electronic Commerce Act 2000 + S.I. 233/2010 (확인됨) | 미확인 | **Government Digital Wallet** 파일럿(2026-06-23) → **시범** | DC4EU, WB | 미확인 | gov.ie 확인됨 |
| IT | AgID | CAD D.Lgs. 82/2005 (확인됨) | CAD 제64조 SPID(AgID 인정), DPCM 2014 (확인됨) | **IT-Wallet**. IO 앱 공공 월렛에 민간 월렛 인가. 2024-12 출시, 530만 이용자 → **운영(전환형)** | POT, NOBID, DC4EU, APT, WB | **제정: DL 19/2024 → L. 56/2024**, CAD 제64-quater조 (확인됨) | normattiva, agid, ioapp 확인됨 |
| LV | Supervisory Committee of Digital Security | Elektronisko dokumentu likums (확인됨) | 자연인 전자식별법 (확인됨) | Eiropas digitālās identitātes maks(국가 소유, VDAA 제공자). 2026-12 첫 버전 → **개발 중** | NOBID, APT | "법 개정 진행 중"(APTITUDE) → **준비 중** | likumi.lv, varam.gov.lv, APTITUDE 확인됨 |
| LT | RRT (Communications Regulatory Authority) | 전자식별·신뢰서비스법(번호 미확인). RRT가 2024-01-02부터 감독 (확인됨) | 같은 법 | 샌드박스를 조달해 운영, 완전 가동 2027 → **개발 중** | POT, DC4EU, APT | "국내 입법 마무리 중"(APTITUDE) → **준비 중** | rrt.lt, APTITUDE 확인됨 |
| LU | ILNAS | Loi modifiée 14-08-2000 전자상거래법(2020-07-17 개정) (확인됨) | GouvID 앱(CTIE, eID 인증·서명). 법령은 미확인 | EUDI 월렛 **미확인**. GouvID는 eID 앱임 | POT, DC4EU, WB | 미확인 | public.lu, ctie 확인됨 |
| MT | Malta Communications Authority (MCA) | Electronic Commerce Act Cap. 426 (확인됨) | 미확인 | **미확인** (mita·mdia 403) | DC4EU, WB | 미확인 | legislation.mt 확인됨 |
| NL | RDI (Dutch Authority for Digital Infrastructure) | Telecommunicatiewet 제2.5a~2.5e조, 제46b조 감독은 EZK 장관(실무 RDI) (확인됨) | Wet digitale overheid(DigiD). eHerkenning은 계약형 Afsprakenstelsel (확인됨) | **NL Wallet**(BZK). 로그인·PID 등 기능은 있으나 **개발 중**, 전면 이행 2028 | POT, DC4EU, APT, WB | **준비 중**: 입법예고 2026말, 시행 2028, 24개월 기한 초과를 인정 (확인됨) | wetten.overheid.nl, APTITUDE, 로컬 00_부속3 |
| PL | National Bank of Poland (NCCert). 감독기관(디지털부)과 TL 운영자가 다름 | 신뢰서비스·전자식별법 2016-09-05, Dz.U. 2016 poz. 1579 (확인됨) | mObywatel 앱법 2023, Dz.U. 2023 poz. 1234 (확인됨) | EUDIW 시행일 2026-12-24로 설정, 인증 준비 → **개발 중**. mObywatel과의 관계는 미확인 | POT, DC4EU, APT, WB | **부분**: 각의 결의 152/2026(M.P. 2026 poz. 675)로 제5c조 담당 장관 지정. 이행법 자체는 미확인 | Sejm ELI API, APTITUDE 확인됨 |
| PT | GNS (National Security Cabinet) | DL 12/2021 (미확인, DRE는 JS 전용) | Chave Móvel Digital, 시민카드(autenticacao.gov.pt) (확인됨) | **gov.pt 앱**(id.gov.pt 2019~ 통합)을 Portuguese Digital Identity Wallet으로 개발·시범. 기업 지갑은 2026-01부터 → **운영(전환형)** | POT, DC4EU, APT, WB | 미확인 | autenticacao.gov.pt, gov.pt, APTITUDE 확인됨 |
| RO | ADR (Authority for the Digitalisation of Romania) | Legea 214/2024(roeid.ro가 인용). 원문은 미확인 | ROeID(ADR) (확인됨) | **미확인** | DC4EU, WB | 미확인 | roeid.ro 확인됨. legislatie.just.ro 접속 불가 |
| SK | National Security Authority (NBÚ) | Zákon 272/2016 Z. z., 최종 개정 365/2024 (확인됨) | Zákon 305/2013 e-Government (확인됨) | **미확인** | POT | 미확인 | slov-lex 확인됨 |
| SI | Ministry of Digital Transformation, Information Society Inspectorate | ZEISZ(Ur. l. 121/21 등). 원문은 안 읽고 검색 스니펫만 → 미확인 | 미확인 | **eDenarnica**. 2026-06-09 연계 행사 → **개발 중** | POT, WB | 미확인 | gov.si 확인됨 |
| ES | Ministry for Digital Transformation | Ley 6/2020 (확인됨) | Cl@ve(Orden PRE/1838/2014), RD 255/2025(모바일 DNI, 2024/1183 인용) (확인됨) | **Cartera Digital BETA**. 성년 증명 자격증명 → **베타/시범** | POT, DC4EU, WB | 전용 법 없음. RD 255/2025는 인용만 → **미확인** | boe.es, digital.gob.es 확인됨 |
| SE | PTS (Swedish Post and Telecom Agency) | Lag 2016:561 (확인됨) | Lag 2026:1358 국가 e-legitimation(2026-12-01 시행, Sverige-id LoA4), Digg 신뢰프레임워크 (확인됨) | 국가 디지털 신원 지갑(Digg·PTS). 1차 버전 2026-12, 인증 EUDIW 2029 → **개발 중** | DC4EU, EWC(조정국), WB | 지갑 전용 법 미확인 | riksdagen, digg.se 확인됨 |
| IS | Electronic Communications Office (Fjarskiptastofa) | Lög 55/2019 전자식별·신뢰서비스법 (확인됨) | Stafræn skilríki, Ísland.is 앱. 법적 근거는 미확인 | **미확인** | NOBID | 2024/1183 EEA 미편입. 국내법 없음(미확인) | althingi.is, efta.int 확인됨 |
| LI | Office for Communications (Amt für Kommunikation) | SigVG, LGBl. 2019.114 (확인됨) | 미확인 (llv.li Cloudflare) | **미확인** | 확인 안 됨 | 2024/1183 EEA 미편입 | gesetze.li, efta.int 확인됨 |
| NO | Nkom (Norwegian Communications Authority) | LOV-2018-06-15-44, 최종 개정 2024-06-21-39 (확인됨) | Selvdeklarasjonsforskriften FOR-2019-11-21-1578(로컬 확인됨), Digdir eID 전략 | 디지털 신원 지갑. Digdir 샌드박스, 2026 콘셉트 선택 → **개발 중** | NOBID, DC4EU, WB | **준비 전**: DFD 부처가 법안 작성 예정. 2024/1183 EEA 미편입 (확인됨) | lovdata, digdir.no, efta.int 확인됨 |

## 2. EUDI 상태 집계 (30개국)

**지갑 상태**

"운영(전환형)"은 국가가 운영 중인 지갑 앱을 EUDI 월렛으로 지정했거나 전환 중인 경우다. **제5d조 인증을 받은 EUDI 월렛은 한 건도 확인하지 못했다.**

| 분류 | 국가 | 수 |
|---|---|---|
| 운영(전환형) | DK, FR, IT, EL, HU, PT | 6 |
| 시범·베타·샌드박스 | DE, IE, ES | 3 |
| 개발 중 | AT, CZ, EE, LV, LT, NL, PL, SE, SI, NO | 10 |
| 국내 앱만 확인(EUDI 연계 미확인) | HR, CY | 2 |
| 미확인 | BE, BG, FI(법 제정, 앱 미확인), LU, MT, RO, SK, IS, LI | 9 |

**국내 이행법**

| 분류 | 국가 | 수 |
|---|---|---|
| 제정 | DK(LOV 301/2026), FI(812/2026·811/2026), IT(L.56/2024) | 3 |
| 법안·준비 중 | DE(내각 의결), CZ, NL, LV, LT, HR, NO | 7 |
| 부분(행정 지정) | PL | 1 |
| 미확인·찾지 못함 | 나머지 | 19 |

**LSP 참여**
- 어느 LSP에서도 확인되지 않음: HR, LI. 단 EWC 명단은 미확인이다.
- 1·2차를 합쳐 5개 LSP에 참여: DE, IT

## 3. 주의·불일치
- **PL**: TL 운영자는 NBP(NCCert)다. 감독기관은 별도(디지털화 담당 장관)로, 각의 결의 152/2026 기준이다.
- **IE**: LOTL의 운영자명(Department of Culture, Communications and Sport)과 gov.ie 표기(DECC)가 다르다. 부처 개편이 반영되지 않은 것으로 보이며, 개편 여부는 미확인이다.
- **DC4EU**: 본문은 "22 MS"라 쓰나 국기 목록은 EU 20개국이다.
- **WE BUILD**: 참여기관 소재국 기준이다. 정부가 참여했는지는 구분하지 않았다.
- **DK·FI 법번호**: DK 신뢰서비스법 LOV 617/2016과 FI 법 617/2009는 번호가 같지만 다른 법이다.
- **비공식 미러**: CZ, CY, BE 행은 비공식 미러에 근거했으므로 인용 전에 공식본과 대조해야 한다.
