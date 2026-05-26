# Paper Album v1 Skin Prompts

공통 스타일:

```text
초등 수학 문제풀이 태블릿 앱용 UI 스킨. 밝은 종이 질감, 문제집을 책장이나 앨범에 꽂아 둔 느낌. 과한 캐릭터 없이 차분하고 선명한 교육용 스타일. 색상은 흰 종이, 연한 아이보리, 잉크 블루, 차분한 라벤더, 채점용 빨강/초록을 보조색으로 사용. 앱 텍스트가 위에 올라가므로 중앙 영역은 저대비, 고가독성, 깔끔한 flat illustration, subtle paper texture, no dark background, no heavy gradient, no cartoon characters, no clutter.
```

## 1. dashboard_bg_1600x2560.png

```text
1600x2560 portrait tablet background for a Korean elementary math workbook app. Very light paper album and bookshelf atmosphere, subtle vertical book silhouettes at the far left and right edges, center area mostly clean for a 3-column grid of workbook covers. Low contrast ivory paper texture, faint ruled-paper fibers, soft shadows only near the edges. Do not include readable text, icons, characters, or strong patterns. The top 8% must stay visually quiet for system status space.
```

## 2. book_cover_base_512x700.png

```text
512x700 PNG workbook cover template for a tablet app grid. It should look like one workbook standing upright among other similar books. Left 14% is reserved as a visible book spine with a slightly darker ink-blue/lavender strip. Main cover is warm ivory paper with subtle printed workbook texture, thin border, small blank sticker area near top-right for progress percent, large quiet blank center where app title text will be placed. No readable text. No characters. Keep shadows soft and consistent so multiple covers placed side by side look like one matching set.
```

## 3. book_cover_spine_96x700.png

```text
96x700 transparent PNG book spine overlay. Ink-blue muted cloth-paper texture, thin highlight on the left edge, soft inner shadow on the right edge. Designed to be placed on the left side of a workbook cover. No text or symbols. Must match book_cover_base_512x700.png.
```

## 4. book_cover_shadow_512x700.png

```text
512x700 transparent PNG soft shadow for a standing workbook cover. Shadow should fall slightly down-right, very subtle, suitable for a white or ivory dashboard. No hard black shadow. Must not cover the center content.
```

## 5. book_shelf_band_1600x260.png

```text
1600x260 PNG horizontal shelf band for the workbook selection screen. Looks like a very light paper or pale wood shelf seen front-on, with a soft top edge shadow where workbook covers sit. Low contrast, no text, no objects, no characters. It should visually connect a row of workbook cards without becoming a heavy divider.
```

## 6. chapter_row_tab_1200x180.png

```text
1200x180 PNG background for a chapter row card. Thin paper tab or index-card style, slightly rounded 8px corners, left edge has a small colored index tab area, main area clean white paper. Very subtle gray border and soft shadow. No text. Designed for dense progress list rows.
```

## 7. problem_paper_bg_1600x2560.png

```text
1600x2560 portrait tablet note paper background. Nearly white paper, extremely subtle dot-grid or fiber texture, no visible strong lines. The problem image and handwriting will be placed on top, so all texture must be low contrast. Top area must remain clean for a thin toolbar. No text, no icons, no decoration blobs.
```

## 8. toolbar_strip_1600x96.png

```text
1600x96 transparent or near-white PNG toolbar strip. Very thin paper tape look with slight shadow under it, designed for small circular pen buttons and arrow buttons. No labels, no icons. Keep it light enough that handwritten notes underneath remain visually primary.
```

## 9. bottom_menu_plate_1600x160.png

```text
1600x160 PNG bottom navigation plate for tablet app. Pale paper tab bar with gentle top shadow, small circular buttons will sit on top. No text or icons. Must avoid dark colors and leave safe margins at the bottom for Android navigation area.
```

## 10. round_btn_idle_128x128.png

```text
128x128 transparent PNG circular idle button frame. Thin ink-stamp circular border, off-white fill, subtle paper texture, no text or icon. Center must be empty for app-provided letter or icon. Clear alpha outside the circle.
```

## 11. round_btn_active_128x128.png

```text
128x128 transparent PNG circular active button frame. Same as idle button but with stronger ink-blue border and a tiny inner glow or pressed-paper effect. No text or icon. Clear alpha outside the circle.
```

## 12. master_action_group_900x96.png

```text
900x96 transparent PNG grouped master tool bar background. Looks like a small paper label strip holding several compact buttons. Very light blue-gray fill, 8px rounded corners, subtle border, no text. Designed to group related buttons such as save/note and image adjustment.
```

## 13. answer_stamp_blue_512x180.png

```text
512x180 transparent PNG answer stamp badge. White paper fill with hand-stamped blue rectangular border, slightly imperfect but clean. Center must be blank for answer text such as 200명. No text. Must be readable when placed over a workbook page.
```

## 14. answer_wrong_slash_512x180.png

```text
512x180 transparent PNG red wrong-answer slash overlay. Two or one red hand-drawn strike line crossing a blue answer stamp area. Transparent outside the red mark. No text. It should look like a teacher correction mark, not too thick.
```

## 15. review_note_card_960x360.png

```text
960x360 PNG teacher review note card. Pale sticky-note or index card with light border, soft shadow, enough empty area for Korean note text. No readable text. Color should not fight with red/blue grading marks.
```

## 16. import_dropzone_1200x520.png

```text
1200x520 PNG workbook import drop zone background. Light dashed border area on paper, subtle zip/document silhouette in the corner, no readable text. Inviting but not playful, suitable for a parent/teacher importing a workbook ZIP.
```

## 17. log_popup_bg_1200x1000.png

```text
1200x1000 PNG modal background for master log popup. Clean paper panel, very subtle header band, rounded 8px corners, low shadow, no text. Designed for dense attempt history rows and review buttons.
```

생성 후 체크:

- 파일명은 `skin.json`의 `assets` 값과 정확히 맞춘다.
- 버튼/스탬프/오버레이 파일은 투명 PNG로 저장한다.
- 문제 화면에 쓰이는 배경은 글씨와 필기를 방해하지 않도록 대비를 낮춘다.
- 책 표지는 3열로 함께 놓였을 때 색감과 그림자 방향이 맞아야 한다.
