# Paper Album v1 Skin Prompts

이 프롬프트 묶음은 `workbook_skin_paper_album_v1.zip`에 들어갈 PNG를 같은 톤으로 만들기 위한 기준입니다. 생성 이미지에는 글자, 숫자, 실제 아이콘을 넣지 않고, 앱이 위에 텍스트와 아이콘을 얹을 수 있도록 중앙부를 비워 둡니다.

## 공통 좌표 기준

- 기준 캔버스는 세로 태블릿 `1600x2560`입니다.
- 상단 `0~160px`은 시스템 시간/상태바와 앱 상단 여백이 지나가는 영역이므로 아주 조용해야 합니다.
- 문제 화면의 도구 레이어는 대략 `160~260px` 사이에 붙습니다.
- 하단 메뉴는 대략 `2380~2560px`에 놓입니다. 하단 180px은 어두운 장식이나 중요한 오브젝트를 넣지 않습니다.
- 문제집 선택 화면은 가운데 `160~2260px`에 책 표지 3열 그리드가 놓이는 구조입니다.
- 마스터 모드의 작은 액션 버튼은 하단 메뉴 바로 위, `2240~2360px` 부근에 얹힙니다.

## 공통 스타일 프롬프트

```text
Korean elementary math workbook tablet app UI skin, calm paper album and workbook shelf style, warm white paper, pale ivory, muted ink blue, soft lavender gray, small accents of teacher red and correct green only. Flat illustration with subtle paper fibers, gentle printed-paper texture, soft shadows, low contrast center zones for app text and handwriting. No readable text, no letters, no numbers, no characters, no mascots, no strong gradients, no decorative blobs, no dark background, no busy patterns. All assets must feel like one matching product family.
```

## 1. dashboard_bg_1600x2560.png

용도: 문제집 선택/진도판 배경. 위에 책 표지 카드들이 3열로 올라갑니다.

```text
1600x2560 portrait tablet dashboard background for a Korean elementary math workbook app. Composition: top 0-160px almost blank warm white for system status space; center 180-2200px clean low-contrast paper album surface where a 3-column grid of workbook covers will sit; far left and far right 0-120px and 1480-1600px may contain very faint vertical silhouettes of book spines, cropped softly at the edges; bottom 2380-2560px quiet pale paper for bottom navigation. Add a barely visible shelf/album ambience, soft edge shadows only at the far sides and under imaginary book rows, no objects in the center. No readable text, icons, characters, labels, numbers, heavy pattern, or high contrast marks.
```

## 2. book_cover_base_512x700.png

용도: 문제집 선택 화면의 책 표지 기본판. 여러 권이 나란히 놓여도 어색하지 않아야 합니다.

```text
512x700 PNG workbook cover base, upright book cover for a tablet grid. Composition: left 0-72px reserved as book spine area with muted ink-blue/lavender cloth-paper strip; main cover 72-512px warm ivory paper. Top 40-120px has a small blank sticker area on the upper-right, about 120x70px, for app progress text; center 130-520px remains mostly empty for app workbook title; bottom 560-660px may have one faint printed rule line or very subtle paper band. Thin border around the full cover, 8px rounded corners, soft shadow direction down-right but very light. No readable text, no icons, no characters, no math symbols. It must look harmonious when repeated in a 3-column book grid.
```

## 3. book_cover_spine_96x700.png

용도: 책 표지 왼쪽 책등 오버레이.

```text
96x700 transparent PNG book spine overlay. Composition: full-height vertical strip, alpha outside the strip if needed; left edge has a thin pale highlight, right edge has a very soft inner shadow so it reads as a spine attached to the cover. Muted ink-blue and lavender gray cloth-paper texture, low contrast. Keep the middle empty because app may place small title marks. No text, no symbols, no hard dark line. Must align visually with the left 14% of book_cover_base_512x700.png.
```

## 4. book_cover_shadow_512x700.png

용도: 책 표지 아래 그림자.

```text
512x700 transparent PNG soft shadow for an upright workbook cover. Composition: transparent background; shadow begins just outside the cover edge, strongest along the right edge and bottom edge, falling 16-28px down-right. Center area mostly transparent so it never darkens app title text. Use pale gray-blue shadow, no black, no hard edge. It should make one cover lift slightly from the dashboard but still look flat and clean when 3 covers are side by side.
```

## 5. book_shelf_band_1600x260.png

용도: 문제집 선택 화면에서 책들이 놓이는 얇은 선반/앨범 띠.

```text
1600x260 PNG horizontal shelf band for workbook selection. Composition: a very light paper or pale wood shelf viewed straight-on; top 0-40px has a soft contact shadow where book covers sit; middle 40-210px is a calm ivory strip with faint paper grain; bottom 210-260px fades out softly. No vertical dividers, no text, no objects. It should visually connect a row of workbook covers without becoming a thick dark bar.
```

## 6. chapter_row_tab_1200x180.png

용도: 책 선택 후 소단원 목록의 한 줄 배경.

```text
1200x180 PNG chapter row tab background. Composition: left 0-95px is a small colored index tab area in muted ink-blue or lavender; main area 95-1200px is clean off-white paper for chapter title, progress, and status text. Corners around 8px, thin gray paper border, soft shadow below only 4-8px. Top and bottom edges must be straight and quiet so many rows can stack vertically. No readable text, icons, numbers, characters, or heavy texture.
```

## 7. problem_paper_bg_1600x2560.png

용도: 문제 풀이 화면의 노트 배경. 문제 이미지와 필기, 답 스탬프가 이 위에 올라갑니다.

```text
1600x2560 portrait note paper background for solving math problems. Composition: top 0-160px blank for system status; toolbar area 160-260px nearly white and very quiet; main writing area 260-2360px clean paper with extremely subtle dot-grid or fiber texture, low enough that black/red/blue pen strokes and printed problem images remain dominant; bottom 2360-2560px quiet area for navigation. Do not add visible lines that could interfere with math diagrams. No text, no icons, no decorative blobs, no dark border, no strong pattern.
```

## 8. toolbar_strip_1600x96.png

용도: 문제 화면 노트 위에 붙는 얇은 도구 레이어 배경.

```text
1600x96 transparent or near-white PNG toolbar strip. Composition: full-width very thin paper tape strip; center 480-1120px must be extra clean because book title and current problem position text sit there; left 0-220px and right 1380-1600px remain clean for small arrow buttons; small circular pen buttons sit across the strip, so keep texture subtle. Add only a faint lower shadow, 2-5px. No labels, no icons, no readable text.
```

## 9. bottom_menu_plate_1600x160.png

용도: 하단 메뉴판 배경. 메/진도판/문제/마스터 버튼이 얹힙니다.

```text
1600x160 PNG bottom navigation plate. Composition: top 0-20px soft upward shadow line; middle 20-120px pale paper tab bar with subtle rounded top corners; bottom 120-160px fades to transparent or very pale so Android navigation area remains unobtrusive. Leave circular button positions visually clean across the bar, especially far left for menu toggle and far right for master button. No text, no icons, no dark colors, no important decoration at the bottom edge.
```

## 10. round_btn_idle_128x128.png

용도: 일반 원형 버튼 프레임.

```text
128x128 transparent PNG circular idle button frame. Composition: perfect centered circle from about x/y 10 to 118, transparent outside. Off-white paper fill, thin hand-ink circular border in gray-blue, slight pressed paper texture, center 40-88px completely empty for app letter or icon. No text, no symbol, no inner graphic. Must remain readable at small 28-40dp sizes.
```

## 11. round_btn_active_128x128.png

용도: 선택된 원형 버튼 프레임.

```text
128x128 transparent PNG circular active button frame. Same geometry as round_btn_idle_128x128.png. Stronger ink-blue border, subtle inner pressed-paper shadow, very pale blue fill, transparent outside. Center remains blank for app-provided icon or letter. No text, no symbol, no glow outside the circle. It should look active without being loud.
```

## 12. master_action_group_900x96.png

용도: 마스터 모드 하단 액션 버튼 묶음 배경. 정답/노트/그림 조정 관련 버튼들이 붙습니다.

```text
900x96 transparent PNG compact master action group background. Composition: horizontal rounded paper label strip, 8px corner radius, transparent outside. Use very light blue-gray fill and thin border. Divide the strip visually into three subtle zones: left 0-260px for answer/note actions, center 260-620px for image and border adjustment actions, right 620-900px for confirm/save actions. Dividers should be barely visible, not hard lines. No text, no icons, no labels.
```

## 13. answer_stamp_blue_512x180.png

용도: 학생이 제출한 답을 파란 스탬프처럼 표시하는 배경.

```text
512x180 transparent PNG answer stamp badge. Composition: transparent outside; centered rounded rectangle from x 18-494 and y 24-156; white paper fill; hand-stamped blue rectangular border, slightly imperfect but still clean; center x 70-442 and y 58-122 completely blank for answer text such as 200명. Border thickness medium, not thick enough to cover text. No text, no icon, no extra decoration. Must be readable over a printed workbook image.
```

## 14. answer_wrong_slash_512x180.png

용도: 오답 제출 시 답 스탬프 위에 얹는 빨간 표시.

```text
512x180 transparent PNG wrong answer overlay. Composition: transparent outside; one confident teacher correction slash from x 72 y 52 to x 438 y 128, optionally a second shorter parallel red stroke very close to it; keep the mark inside the same area as answer_stamp_blue_512x180.png. Red ink should be visible but not so thick that it hides the answer. No text, no circle, no X icon, no border.
```

## 15. review_note_card_960x360.png

용도: 마스터 노트나 정답 관련 메모를 표시하는 카드.

```text
960x360 PNG teacher review note card. Composition: pale sticky-note or index card centered in the canvas, rounded 8px corners, thin warm gray border, very soft shadow down-right. Top 0-56px may have a faint header band but no text; main 60-320px clean blank area for Korean note text. Color should be pale cream or soft blue-gray, not yellow-heavy, and must not fight with red/blue grading marks. No readable text, no icon, no character.
```

## 16. import_dropzone_1200x520.png

용도: 문제집 ZIP 가져오기 영역.

```text
1200x520 PNG workbook import drop zone background. Composition: large rounded rectangle drop area from x 40-1160 and y 40-480; light dashed border; center mostly empty for app instructions; bottom-right x 940-1110 y 300-440 has a very faint zip/document stack silhouette, low contrast; top-left may have subtle paper clip or file corner shape but no readable text. Inviting, parent/teacher friendly, calm and practical. No characters, no icons that look like app buttons, no text.
```

## 17. log_popup_bg_1200x1000.png

용도: 마스터 로그 팝업 배경. 완료 풀이 기록이 행으로 표시됩니다.

```text
1200x1000 PNG modal background for master log popup. Composition: rounded paper panel filling x 20-1180 and y 20-980, transparent outside if possible; top 20-130px has a very subtle header band; body 140-920px is clean white paper for dense attempt history rows; bottom 920-980px quiet for close/action buttons. Use low shadow around the panel, 8px rounded corners, no text, no icons, no decorations in the body. It must feel consistent with chapter_row_tab_1200x180.png.
```

## 생성 후 체크

- 파일명은 `skin.json`의 `assets` 값과 정확히 맞춘다.
- 버튼, 스탬프, 오버레이, 팝업 배경은 가능한 투명 PNG로 저장한다.
- 문제 화면에 쓰이는 배경은 필기와 문제 이미지를 방해하지 않도록 대비를 낮춘다.
- 책 표지, 책등, 그림자는 같은 방향의 빛을 써야 한다. 기본 그림자 방향은 아래쪽-오른쪽이다.
- 한 스킨 안에서는 종이색, 잉크색, 그림자 색이 서로 달라 보이지 않아야 한다.
