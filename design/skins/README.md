# Workbook Skin Pack

이 폴더는 문제집 앱의 그래픽 스킨을 만들기 위한 규격과 프롬프트 템플릿을 보관합니다.

스킨 ZIP은 다음 구조를 권장합니다.

```text
workbook_skin_<skin_id>.zip
  skin.json
  prompts.md
  assets/
    dashboard_bg_1600x2560.png
    book_cover_base_512x700.png
    book_cover_spine_96x700.png
    book_cover_shadow_512x700.png
    book_shelf_band_1600x260.png
    chapter_row_tab_1200x180.png
    problem_paper_bg_1600x2560.png
    toolbar_strip_1600x96.png
    bottom_menu_plate_1600x160.png
    round_btn_idle_128x128.png
    round_btn_active_128x128.png
    master_action_group_900x96.png
    answer_stamp_blue_512x180.png
    answer_wrong_slash_512x180.png
    review_note_card_960x360.png
    import_dropzone_1200x520.png
    log_popup_bg_1200x1000.png
```

이미지는 모두 PNG를 기본으로 합니다. 투명 배경이 필요한 버튼/스탬프 계열은 알파 채널을 유지합니다.

현재 앱은 우선 문제집 선택 화면의 카드가 책 표지/책등 구조로 보이도록 Compose 그래픽을 적용했습니다. 실제 이미지 스킨 로딩 기능을 붙일 때도 위 파일명을 그대로 쓰면 됩니다.

프롬프트를 새로 만들 때는 `paper_album_v1/prompts.md`의 좌표 기준을 먼저 유지하는 것을 권장합니다. 특히 상단 0~160px, 하단 2380~2560px, 책 표지 3열 그리드 중앙 영역, 문제 화면 도구 레이어 영역을 명확히 비워 두면 생성 이미지가 앱 UI와 덜 충돌합니다.
