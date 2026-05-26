# Paper Album v1

`paper_album_v1`은 문제집 선택 화면을 책장/앨범처럼 보이게 만드는 기본 스킨 템플릿입니다.

작업 순서:

1. `prompts.md`의 각 프롬프트로 이미지를 생성합니다.
2. 생성한 PNG를 `assets/` 폴더에 파일명 그대로 넣습니다.
3. `skin.json`과 `assets/` 폴더를 함께 ZIP으로 묶습니다.
4. ZIP 파일명은 `workbook_skin_paper_album_v1.zip`을 권장합니다.

앱에 스킨 import 기능을 붙일 때는 `skin.json`의 `assets` 경로를 기준으로 로드하면 됩니다.
