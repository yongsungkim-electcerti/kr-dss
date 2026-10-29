from pathlib import Path
root=Path('docs/guides/poc/tl-management/whale')
p=Path('tools/whale-manual-content.cjs');s=p.read_text(encoding='utf8').replace('아래 명령으로 공개 API의 XML을 저장합니다.','다음 쪽의 명령으로 공개 API의 XML을 저장합니다.').replace('이름: Whale PDF 시험 CA / 유형: CA\\n분야: 공동인증 / 레벨: 0 (RootCA)','Whale PDF 시험 CA / CA / 공동인증 / 0 (RootCA)');p.write_text(s,encoding='utf8')
for name in ['tools/whale-artifact-audit.py','tools/package-whale-manuals.py']:
 p=Path(name);s=p.read_text(encoding='utf8').replace("glob('*.pptx')","glob('Whale_*.pptx')");p.write_text(s,encoding='utf8')
p=Path('tools/whale-manual-qa.ps1');s=p.read_text(encoding='utf8').replace('-Filter *.pptx','-Filter Whale_*.pptx');p.write_text(s,encoding='utf8')
p=Path('tools/package-whale-manuals.py');s=p.read_text(encoding='utf8').replace("+list(root.glob('*.pdf'))",'').replace('assert len(files)==5','assert len(files)==3').replace('같은 이름의 PDF는 열람용입니다. 명령 예제는 본문에서 확인합니다.','이번 개정본은 PPTX입니다. PDF는 추후 작성합니다. 명령 예제는 본문에서 확인합니다.');s=s.replace("for rel,data in manuals.items():entries[prefix+rel]=data", "# Exclude earlier PDF drafts from the revised distribution.\nentries={k:v for k,v in entries.items() if not (k.startswith(prefix+'manuals/') and k.endswith('.pdf'))}\nfor rel,data in manuals.items():entries[prefix+rel]=data")
p.write_text(s,encoding='utf8')
(root/'README.md').write_text('''# 네이버 웨일 개발자용 TrustList PoC 문서

2026-10-28 개정 · 개발자용 · v1.0

| 문서 | 분량 | 내용 |
| --- | --- | --- |
| [API 연동 가이드](Whale_API_연동_가이드_v1.0.pptx) | 12쪽 | API 구성, 신뢰목록 수용, 조회·갱신, 데이터, 등록 조건, 예제, 오류 코드 |
| [관리 시스템·TL 작업대 사용 매뉴얼](Whale_관리시스템_TL작업대_사용매뉴얼_v1.0.pptx) | 17쪽 | 실행, 화면, 샘플 TL 비교, 관리 TL 발행·검증, 문제 해결 |

이번 개정 산출물은 PPTX입니다. PDF는 추후 작성합니다. 기존 PDF는 이전 원고입니다.
같은 이름의 Markdown과 `slide-content.json`은 PPTX 생성 원고입니다.

## 참고·검수

사용자가 지정한 Drive 폴더의 설계 문서 5개와 도식 5개를 보관한 `sources/`를 참고했습니다. 시나리오 문서는 참고 자료로 사용했습니다.
실제 화면은 `evidence/`의 캡처를 사용했습니다. 기존 실행 검사 24건의 증거는 보존했으며, 문서 개정에 따른 서버 시험은 재실행하지 않았습니다.

PPTX 전체 29쪽은 PowerPoint 렌더링과 텍스트 배치를 검수합니다. 결과와 파일 해시는 `evidence/artifact-manifest.json`에 기록합니다.
[윤문 검수 기록](윤문_검수기록.md)에 적용 규칙과 검수 범위를 정리했습니다.
''',encoding='utf8')
(root/'윤문_검수기록.md').write_text('''# 윤문 검수 기록

2026-10-28 · humanize-korean · 개발자용 기술 문서

## 범위

사용자 요청에 따라 내용을 먼저 재구성했습니다. API 가이드는 공개 TL 수신·갱신으로 범위를 좁히고, 화면 조작과 발행 시험은 사용 매뉴얼로 정리했습니다. 이 과정의 삭제·축약은 내용 편집입니다. 이후 문체를 스킬 규칙으로 검토했습니다.

룰북은 `C:/Users/sol74/.claude/skills/humanize-korean/references/quick-rules.md`를 사용했습니다. 앞선 검수 기록의 룰북 미확인 상태를 정정합니다.

| 규칙 | 적용 |
| --- | --- |
| C-10 | 설명형 제목을 API 구성, 신뢰목록 수용, 등록 조건 등 명사형으로 정리했습니다. |
| C-8 | 반복 대조·부정 설명을 삭제하고 동작과 입력값을 직접 설명했습니다. |
| D-3·D-8 | 불필요한 도입과 강조 문장을 걷어냈습니다. |
| A-8·A-9 | 사용 주체와 조작을 능동문으로 표현했습니다. |
| Do-NOT | API 경로·헤더·상태 코드·인증서 파일명·시험 시각을 보존했습니다. |

## 검수

기술 값 보존, 장르 유지, 격식체 유지, 핵심 S1 잔존 여부, 새 수사 표현 유입 여부를 수동 검토했습니다. 본문은 ~합니다 체로 통일하고 표의 값은 명사형을 사용했습니다.
전용 `verify_change_rate.py`는 설치 경로에서 찾지 못했습니다. 변경률과 정식 등급은 산출하지 않았습니다. 내용 재구성을 단순 윤문의 변경률로 판정하지 않았습니다.
PPTX 구조·렌더링·텍스트 넘침은 별도 도구로 검수합니다.
''',encoding='utf8')
work=Path('_workspace/2026-10-28-002');work.mkdir(parents=True,exist_ok=True)
(work/'final.md').write_text((root/'윤문_검수기록.md').read_text(encoding='utf8')+'''\n<!-- HUMANIZE-SUMMARY
대상: API 가이드·사용 매뉴얼 개정 원고
내용 편집: 사용자 지시에 따른 축약·재구성
문체 검토: C-10, C-8, D-3, D-8, A-8, A-9 및 Do-NOT
원본/윤문본 글자수·변경률: 독립 윤문 전후 원고 및 공식 측정기 부재로 미산출
카테고리별 탐지 건수: 자동 집계 미실시
자체검증: 기술 값·장르·격식체·핵심 S1·인공 표현 5항 수동 확인, 변경률 1항 미확정
등급: 미산출 (공식 변경률 미확정)
주요 변경: 필요한 API를 먼저 구분합니다 → API 구성
주요 변경: 발행본, 제공본, 채택본은 다릅니다 → 신뢰목록 수용
주요 변경: XML은 이 구조로 읽습니다 → TL 데이터
-->\n''',encoding='utf8')
