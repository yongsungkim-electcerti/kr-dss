# 수정 개념 설계 v02 로컬 보관본

- 원문: [KR-TL 시스템 및 신뢰체계 설계서_v02](https://docs.google.com/document/d/1hOH8BtT9UB8_NpDi5ZtyZjJkeTVfpdNhamrBNuuiqjI/edit)
- Google 문서 ID: `1hOH8BtT9UB8_NpDi5ZtyZjJkeTVfpdNhamrBNuuiqjI`
- 원문 수정 시각: 2026-10-08 15:58:05.687 KST
- 수집·검토 완료: 2026-10-09 KST. 폴더 날짜는 원문 수정일 기준.
- 범위: 전체 탭 1개(`t.0`), 본문·목차·별첨 A~C, 표 23개, 삽입 이미지 13개.
- 이 보관본은 수집 시점 자료다. 기존 PoC 확정 범위를 자동 변경하지 않는다.

## 읽기

- [본문 Markdown](concept-design.md): 제목·표·그림을 읽기 편하게 변환했다.
- [Google 내보내기 HTML](html/KRTL_v02.html): Google이 생성한 HTML과 그림을 함께 저장했다. 원문 서식 확인에 사용한다.
- [PoC 영향 검토](../../../../../development/poc/tl-management/reviews/30-수정-개념설계-v02-영향검토.md)

## 보존·검증

`document.json`은 Google Docs API 구조 응답이다. 본문 인덱스·표·제목 ID·스타일·이미지 메타데이터를 보존했고, 만료되는 이미지 contentUri/sourceUri는 제거했다. 따라서 원 응답의 바이트 단위 사본은 아니다.

이미지 직접 주소는 HTTP 403으로 다운로드되지 않아 Google Drive의 HTML ZIP 내보내기를 사용했다. ZIP 내부 경로를 검사한 후 `html/`에 저장했다. 이미지 파일을 재인코딩하지 않았다. Markdown의 이미지 참조는 문서와 HTML의 등장 순서 13개를 대응시켰으며 전체 그림 배치를 확인했다.

- `export-manifest.json`: HTML·그림 14개 파일의 크기와 SHA-256.
- `image-map.json`: Google Docs 객체 ID와 HTML 이미지 파일의 대응.
- `verification.json`: 표·이미지 수와 본문 대조 결과. 네이티브 텍스트 구간 929개 모두 HTML에 존재함을 공백 정규화 후 확인했다.
- 본문에서 참조하는 별도 「KR-TL 이용 시나리오」와 draw.io 원본은 이 Google 문서의 첨부 파일이 아니다. 해당 파일의 최신본까지 취득했다고 간주하지 않는다.
- Markdown은 페이지 나눔·병합 셀·정밀 배치를 완전히 재현하지 않는다. 원문 서식은 HTML과 구조 JSON을 함께 참조한다.

원문은 수정하지 않았다. 기존 보고서 보관본도 덮어쓰지 않았다.
