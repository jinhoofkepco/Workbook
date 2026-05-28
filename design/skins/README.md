# Workbook Skin Pack

수학 문제집 앱의 스킨은 `skin.json`과 `assets/*.png`로 구성합니다. 이제 권장 제작 방식은 개별 에셋을 AI에 따로 요청하는 방식이 아니라, 한 장 이상의 고품질 컨셉 보드를 만든 뒤 필요한 영역을 잘라 투명 PNG로 내보내는 방식입니다.

## 권장 작업 흐름

1. AI 이미지 도구로 전체 컨셉 보드를 1장 이상 생성합니다.
2. `tools/skin_concept_cutter.py`를 실행합니다.
3. 컨셉 이미지를 여러 장 불러옵니다.
4. 왼쪽 이미지에서 에셋별 영역을 드래그해서 자릅니다.
5. 필요하면 투명화 색상, 모서리 자동 투명화, 형태 마스크를 적용합니다.
6. Book Shelf, Chapter List, Problem 미리보기에서 실제 적용감을 확인합니다.
7. `Export skin zip`으로 앱에 넣을 ZIP을 만듭니다.

```powershell
python tools\skin_concept_cutter.py
```

Codex 번들 Python을 쓸 때:

```powershell
C:\Users\sara92\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe tools\skin_concept_cutter.py
```

## ZIP 구조

```text
workbook_skin_<skin_id>.zip
  skin.json
  cut_recipe.json
  assets/
    dashboard_bg_1600x2560.png
    book_cover_base_512x700.png
    book_cover_spine_96x700.png
    dashboard_page_frame_1600x2400.png
    dashboard_title_banner_1200x220.png
    dashboard_title_frame_1000x180.png
    chapter_row_tab_1200x180.png
    problem_paper_bg_1600x2560.png
    toolbar_strip_1600x96.png
    problem_header_pill_900x96.png
    master_button_idle_128x128.png
    master_button_active_128x128.png
    nav_arrow_previous_160x128.png
    nav_arrow_next_160x128.png
    hint_button_128x128.png
    submit_button_320x128.png
    grading_button_320x128.png
    answer_stamp_blue_512x180.png
    answer_wrong_slash_512x180.png
```

## 제거된 에셋

다음 에셋은 새 스킨 분해안에서 더 이상 권장하지 않습니다.

- `book_cover_shadow_512x700.png`
- `book_shelf_band_1600x260.png`
- `bottom_menu_plate_1600x160.png`
- `round_btn_idle_128x128.png`
- `round_btn_active_128x128.png`
- `master_action_group_900x96.png`
- `review_note_card_960x360.png`
- `import_dropzone_1200x520.png`
- `log_popup_bg_1200x1000.png`

앱은 기존 ZIP 호환을 위해 일부 과거 키를 읽을 수 있지만, 새 스킨은 위 파일 없이 만드는 것을 기준으로 합니다.

## 에셋 선택 기준

모든 에셋을 반드시 만들 필요는 없습니다. 커터에서 자르지 않은 에셋은 `skin.json`에 들어가지 않고, 앱은 해당 그래픽을 없는 것으로 처리합니다.

- `dashboardBackground`는 책 화면 전체 배경입니다.
- `dashboardPageFrame`은 전체 배경 위에 얹는 투명 프레임 오버레이입니다.
- 둘이 거의 같은 그림이라면 둘 다 만들 필요가 없습니다.
- `dashboardBackground`에 프레임까지 포함했다면 `dashboardPageFrame`을 생략해도 됩니다.
- 반대로 배경은 단순하게 두고 테두리만 따로 바꾸고 싶다면 `dashboardBackground`와 `dashboardPageFrame`을 분리하는 편이 좋습니다.
- `dashboardPageFrame`을 생략하면 뒤의 `dashboardBackground`가 그대로 보입니다.
- `dashboardBackground`도 생략하면 앱의 기본 배경 위에 다른 스킨 요소만 올라갑니다.

## 표시 순서

책 화면은 `dashboardBackground` 위에 `dashboardPageFrame`이 올라가고, 그 위에 제목 프레임과 책 표지가 올라갑니다. 책 표지 안에서는 `bookCoverBase` 위에 `bookCoverSpine`이 올라가고, 실제 제목/진도 글자는 그보다 위에 표시됩니다.

세부진도 화면은 `dashboardBackground` 위에 선택한 책 요약과 `chapterRowTab` 행들이 올라갑니다. `chapterRowTab`은 글자와 진행바 아래에 깔리는 배경입니다.

문제 화면은 노트/문제 풀이 배경 위에 `problemPaperBackground`가 아주 약하게 깔리고, 그 위에 문제 내용과 답/풀이 UI가 올라갑니다. `toolbarStrip`은 상단 도구 버튼 뒤에 깔리고, `problemHeaderPill`, 이전/다음/힌트/제출 버튼은 그 위에 표시됩니다.

## 제작 규칙

- 큰 배경을 여러 장 만들기보다, 작은 투명 PNG 오버레이를 재사용합니다.
- 버튼 PNG는 앱이 위에 `<`, `>`, `?`, `제출`, `채점중`, `마` 같은 문자를 얹지 않는 전제로 만듭니다.
- 버튼은 텍스트보다 아이콘, 방향성, 상태 차이로 의미가 드러나게 합니다.
- 제출 버튼과 채점중 버튼은 서로 다른 PNG로 만듭니다.
- 버튼 PNG는 네모 파일이어도 됩니다. 다만 원형/화살표 밖 배경은 투명해야 합니다.
- 문제 풀이를 방해하지 않도록 문제 화면 배경과 노트 줄은 아주 연하게 둡니다.
- 제목, 문제 번호, 학생 답, 필기 내용이 올라가는 중앙 영역은 대비가 낮고 단순해야 합니다.
- PNG는 투명 배경을 유지하고, 저장 시 `optimize=True`, `compress_level=9`를 사용합니다.
- 추가 압축이 필요하면 `oxipng -o 4 --strip all assets/*.png`를 사용할 수 있습니다.
