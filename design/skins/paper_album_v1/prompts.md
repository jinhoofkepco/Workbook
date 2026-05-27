# Paper Album v1 Skin Prompts

이 프롬프트 묶음은 `workbook_skin_paper_album_v1.zip`에 들어갈 PNG를 같은 톤으로 만들기 위한 기준입니다. 생성 이미지에는 글자, 숫자, 실제 아이콘을 넣지 않고, 앱이 위에 텍스트와 아이콘을 얹을 수 있도록 중앙부를 비워 둡니다.

스킨 이미지는 앱의 버튼, 카드, 문제, 답안 입력 UI를 대체하는 그림이 아니라 **뒤에 깔리는 조용한 재질/프레임**입니다. 장식이 버튼처럼 보이거나, 텍스트 영역을 차지하거나, 화면 레이아웃을 밀어내는 듯한 큰 형태가 생기면 실제 앱에 얹었을 때 이질감이 커집니다.

## 공통 좌표 기준

- 기준 캔버스는 세로 태블릿 `1600x2560`입니다.
- 상단 `0~160px`은 시스템 시간/상태바와 앱 상단 여백이 지나가는 영역이므로 아주 조용해야 합니다.
- 문제 화면의 도구 레이어는 대략 `160~260px` 사이에 붙습니다.
- 학생용 하단 진도/문제 메뉴는 제거되었습니다. 하단 180px은 마스터 `마` 버튼 외에는 어두운 장식이나 중요한 오브젝트를 넣지 않습니다.
- 문제집 선택 화면은 가운데 `190~2260px`에 책 표지가 놓이는 구조입니다. 책 폭은 앱에서 다소 작게 쓰며, 줄 사이 간격을 넓혀 책장 느낌을 냅니다.
- 책꽂이 화면은 `dashboard_bg` 위에 작은 투명 프레임 에셋(`dashboard_page_frame`, `dashboard_title_frame`, `book_shelf_band`)을 늘려 얹습니다. 큰 배경을 여러 장 만들지 말고 작은 오버레이를 재사용합니다.
- 마스터 모드의 작은 액션 버튼은 하단 메뉴 바로 위, `2240~2360px` 부근에 얹힙니다.
- 앱의 실제 텍스트, 진행률, 버튼, 입력칸, 화살표 기호, 힌트 기호, 제출 문구는 Compose UI가 그립니다. 스킨 이미지 안에는 글자, 숫자, 버튼 문구, 진행률 자리, 탭 자리처럼 보이는 진한 장식을 만들지 않습니다.
- 콘텐츠가 올라가는 중앙 영역은 시각적 대비를 낮게 유지합니다. 장식은 가장자리, 얇은 그림자, 종이 질감 수준으로 제한합니다.
- 투명 PNG가 가능한 에셋은 투명 배경을 사용합니다. 특히 버튼, 스탬프, 오버레이, 하단 메뉴판, 팝업 배경은 바깥쪽 알파를 반드시 유지합니다.

## 공통 스타일 프롬프트

```text
Korean elementary math workbook tablet app UI skin, calm paper album and workbook shelf style, warm white paper, pale ivory, muted ink blue, soft lavender gray, tiny accents of teacher red and correct green only. The artwork must be quiet background material behind real app UI, not a decorative poster. Flat illustration with subtle paper fibers, gentle printed-paper texture, very soft shadows, low contrast center zones for app text, workbook cards, problem images, handwriting, and input fields. Keep all functional-looking shapes extremely restrained. No readable text, no letters, no numbers, no characters, no mascots, no strong gradients, no decorative blobs, no dark background, no busy patterns, no fake buttons, no fake icons, no fake progress bars. All assets must feel like one matching product family and must not compete with app text.
```

## 1. dashboard_bg_1600x2560.png

용도: 문제집 선택/진도판 배경. 위에 책 표지 카드들이 3열로 올라갑니다.

```text
1600x2560 portrait tablet dashboard background for a Korean elementary math workbook app. Composition: top 0-160px almost blank warm white for system status space; center 180-2200px clean low-contrast paper album surface where a 3-column grid of workbook covers will sit; far left and far right 0-110px and 1490-1600px may contain very faint vertical silhouettes of book spines, cropped softly at the edges at only 8-15% opacity; bottom 2380-2560px quiet pale paper for bottom navigation. The central 140-1460px width must remain almost empty and readable. Add a barely visible shelf/album ambience and soft edge shadows only at the far sides. No objects in the center, no oval placeholders, no fake buttons, no readable text, icons, characters, labels, numbers, heavy pattern, or high contrast marks.
```

## 2. book_cover_base_512x700.png

용도: 문제집 선택 화면의 책 표지 기본판. 여러 권이 나란히 놓여도 어색하지 않아야 합니다.

```text
512x700 PNG workbook cover base, upright book cover for a tablet grid. Composition: left 0-64px reserved as book spine area with muted ink-blue/lavender cloth-paper strip; main cover 64-512px warm ivory paper. Top-right 340-500px and y 28-120px must stay clean for the app progress badge, but do not draw a sticker, circle, or label there. Center 100-520px remains mostly empty for app workbook title and description. Bottom 560-660px may have one extremely faint printed rule line or subtle paper band at under 10% contrast. Thin border around the full cover, 8px rounded corners, soft shadow direction down-right but very light. No readable text, no icons, no characters, no math symbols, no progress bar, no fake badge. It must look harmonious when repeated in a 3-column book grid.
```

## 3. book_cover_spine_96x700.png

용도: 책 표지 왼쪽 책등 오버레이.

```text
96x700 transparent PNG book spine overlay. Composition: full-height vertical strip, alpha outside the strip; visible spine should mainly occupy x 0-64 so it aligns with book_cover_base_512x700.png. Left edge has a thin pale highlight, right edge has a very soft inner shadow so it reads as a spine attached to the cover. Muted ink-blue and lavender gray cloth-paper texture, low contrast. Keep the middle quiet because app title text may overlap. No text, no symbols, no hard dark line, no repeated stripe pattern that dominates the cover.
```

## 4. book_cover_shadow_512x700.png

용도: 책 표지 아래 그림자.

```text
512x700 transparent PNG soft shadow for an upright workbook cover. Composition: transparent background; shadow begins just outside the cover edge, strongest along the right edge and bottom edge, falling 10-20px down-right. Center area fully transparent so it never darkens app title text. Use pale gray-blue shadow at low opacity, no black, no hard edge. It should make one cover lift slightly from the dashboard but still look flat and clean when 3 covers are side by side.
```

## 5. book_shelf_band_1600x260.png

용도: 문제집 선택 화면에서 책들이 놓이는 얇은 선반/앨범 띠.

```text
1600x260 transparent PNG horizontal shelf band for workbook selection. Composition: mostly transparent background; top 0-36px has a very soft contact shadow where book covers sit; middle 36-190px is a calm ivory paper wash at low opacity; bottom 190-260px fades to transparent. No vertical dividers, no text, no objects, no thick shelf plank. It should visually connect a row of workbook covers without becoming a dark bar or changing the page layout.
```

## 6. chapter_row_tab_1200x180.png

용도: 책 선택 후 소단원 목록의 한 줄 배경.

```text
1200x180 transparent PNG chapter row background. Composition: transparent outside the row; full row should be mostly clean off-white paper with only a very faint edge tint. Avoid a strong colored tab where Korean chapter title text begins; any color accent must stay at the far edge under 10% contrast and must not cross behind the first 260px of text. Corners around 8px, thin gray paper border, soft shadow below only 2-5px. Top and bottom edges must be straight and quiet so many rows can stack vertically. No readable text, icons, numbers, characters, fake progress bars, or heavy texture.
```

## 7. problem_paper_bg_1600x2560.png

용도: 문제 풀이 화면의 노트 배경. 문제 이미지와 필기, 답 스탬프가 이 위에 올라갑니다.

```text
1600x2560 portrait note paper background for solving math problems. Composition: top 0-160px blank for system status; toolbar area 160-260px nearly white and very quiet; main writing area 260-2360px clean paper with extremely subtle horizontal notebook lines or fiber texture at only 4-8% contrast, low enough that black/red/blue pen strokes, printed problem images, answer input fields, and screenshots remain dominant; bottom 2360-2560px quiet area for navigation. Do not add visible grids, thick margins, dark borders, decorations, or anything that looks like an input field. No text, no icons, no decorative blobs, no dark border, no strong pattern.
```

## 8. toolbar_strip_1600x96.png

용도: 문제 화면 노트 위에 붙는 얇은 도구 레이어 배경.

```text
1600x96 transparent PNG toolbar strip. Composition: transparent outside; full-width very thin paper tape strip at low opacity; center 480-1120px must be extra clean because book title and current problem position text sit there; left 0-220px and right 1380-1600px remain clean for small arrow buttons; small circular pen buttons sit across the strip, so keep texture extremely subtle. Add only a faint lower shadow, 1-3px. No labels, no icons, no readable text, no fake buttons, no thick tape.
```

## 9. bottom_menu_plate_1600x160.png

용도: 기존 하단 메뉴판 배경. 현재 학생용 진도/문제 메뉴는 제거되어 필수 에셋이 아닙니다. 필요하면 마스터 도구 주변의 아주 약한 받침으로만 씁니다.

```text
1600x160 transparent PNG optional bottom backing plate. Composition: almost fully transparent, only a very thin pale paper wash near the far right where a small master button may float. Top 0-18px can have a barely visible upward shadow line; bottom 112-160px fades fully to transparent so Android navigation area remains unobtrusive. Do not draw menu tabs, oval placeholders, button slots, fake controls, icons, labels, or dark decorations. The asset must not imply that there are student navigation buttons.
```

## 10. round_btn_idle_128x128.png

용도: 일반 원형 버튼 프레임.

```text
128x128 transparent PNG circular idle button frame. Composition: perfect centered circle from about x/y 12 to 116, transparent outside. Off-white paper fill, thin hand-ink circular border in gray-blue, slight pressed paper texture, center 40-88px completely empty for app letter or icon. Border must not be thicker than 4px. No text, no symbol, no inner graphic, no outer glow. Must remain readable at small 28-48dp sizes without looking like a decorative sticker.
```

## 11. round_btn_active_128x128.png

용도: 선택된 원형 버튼 프레임.

```text
128x128 transparent PNG circular active button frame. Same geometry as round_btn_idle_128x128.png. Slightly stronger ink-blue border, subtle inner pressed-paper shadow, very pale blue fill, transparent outside. Center remains blank for app-provided icon or letter. No text, no symbol, no glow outside the circle, no heavy fill. It should look active without being loud.
```

## 12. master_action_group_900x96.png

용도: 마스터 모드 하단 액션 버튼 묶음 배경. 정답/노트/그림 조정 관련 버튼들이 붙습니다.

```text
900x96 transparent PNG compact master action group background. Composition: horizontal rounded paper label strip, 8px corner radius, transparent outside. Use very light blue-gray fill and thin border. Divide the strip visually into three subtle zones: left 0-260px for answer/note actions, center 260-620px for image and border adjustment actions, right 620-900px for confirm/save actions. Dividers should be barely visible, not hard lines. No text, no icons, no labels, no fake button capsules.
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
1200x520 transparent PNG workbook import drop zone background. Composition: large rounded rectangle drop area from x 40-1160 and y 40-480; light dashed border; center mostly empty for app instructions; bottom-right x 940-1110 y 300-440 has a very faint zip/document stack silhouette, low contrast; top-left may have subtle paper clip or file corner shape but no readable text. Inviting, parent/teacher friendly, calm and practical. No characters, no icons that look like app buttons, no text, no strong fill that makes the popup feel cramped.
```

## 17. log_popup_bg_1200x1000.png

용도: 마스터 로그 팝업 배경. 완료 풀이 기록이 행으로 표시됩니다.

```text
1200x1000 transparent PNG modal background for master log popup. Composition: rounded paper panel filling x 20-1180 and y 20-980, transparent outside; top 20-130px has a very subtle header band; body 140-920px is clean white paper for dense attempt history rows; bottom 920-980px quiet for close/action buttons. Use low shadow around the panel, 8px rounded corners, no text, no icons, no decorations in the body, no fake row dividers. It must feel consistent with chapter_row_tab_1200x180.png.
```

## 18. dashboard_title_banner_1200x220.png

용도: 문제집 선택 화면의 `박서아의 문제집들` 제목 뒤 그래픽. 앱이 제목을 직접 그립니다.

```text
1200x220 transparent PNG soft title banner for a Korean elementary workbook dashboard. Composition: transparent outside; a quiet paper ribbon or album label shape from x 40-1160 and y 36-184, with very light ivory fill, muted blue-gray edge, and soft shadow. The left 80-540px must stay clean because Korean title text will sit there; do not place color boundaries behind the text baseline. Add only tiny paper texture and maybe faint corner tape at under 10% contrast. No readable text, no letters, no numbers, no icons, no title printed inside, no decorative character, no strong gradient.
```

## 19. problem_header_pill_900x96.png

용도: 문제 풀이 화면 상단 중앙의 책 제목-소제목-문제 번호 영역 뒤 그래픽. 이 영역을 누르면 진도판으로 돌아갑니다.

```text
900x96 transparent PNG compact header pill for a math workbook solving screen. Composition: rounded paper label centered with 10px radius, transparent outside; very pale warm-white fill, thin blue-gray border, soft pressed-paper shadow. Center x 60-840 and y 24-72 must be clean for Korean workbook title, chapter title, and problem count text. Keep all texture extremely subtle. No readable text, no letters, no numbers, no icons, no fake button label, no strong color split behind the text.
```

## 20. nav_arrow_previous_160x128.png

용도: 문제 화면 이전 버튼의 그래픽 프레임. 앱이 `‹` 기호를 얹습니다.

```text
160x128 transparent PNG previous navigation button frame. Composition: transparent outside; soft paper arrow-shaped or notched frame pointing left, but do not include a drawn arrow glyph or text. Leave center x 48-112 and y 34-94 blank for the app-provided arrow symbol. Pale ivory fill, thin muted ink-blue outline, very soft shadow. It must be readable at about 34dp wide without becoming a loud sticker. No text, no letters, no numbers, no icon glyph inside, no dark fill.
```

## 21. nav_arrow_next_160x128.png

용도: 문제 화면 다음 버튼의 그래픽 프레임. 앱이 `›` 기호를 얹습니다.

```text
160x128 transparent PNG next navigation button frame. Composition mirrors nav_arrow_previous_160x128.png, transparent outside; soft paper arrow-shaped or notched frame pointing right, but do not include a drawn arrow glyph or text. Leave center x 48-112 and y 34-94 blank for the app-provided arrow symbol. Pale ivory fill, thin muted ink-blue outline, very soft shadow. No text, no letters, no numbers, no icon glyph inside, no dark fill.
```

## 22. hint_button_128x128.png

용도: 문제 화면 힌트 버튼 프레임. 앱이 `?` 기호를 얹습니다.

```text
128x128 transparent PNG hint button frame. Composition: centered circular or softly rounded paper button from x/y 12 to 116, transparent outside. Pale ivory fill, thin lavender-blue outline, subtle paper texture, center x/y 40-88 completely blank for the app-provided hint symbol. Slightly playful but still quiet, not a sticker. No question mark, no text, no letters, no numbers, no icon, no dark color, no glow.
```

## 23. submit_button_320x128.png

용도: 답 입력 영역 제출 버튼 프레임. 앱이 `제출` 또는 `채점중` 텍스트를 얹습니다.

```text
320x128 transparent PNG submit button frame for a Korean math workbook app. Composition: rounded rectangular paper button, transparent outside, 14px corner radius, pale ivory fill with a thin muted green-blue border and very soft lower shadow. Center x 70-250 and y 38-90 must be clean for app-provided Korean text. It may feel slightly more important than hint/arrow buttons but must remain calm. No readable text, no letters, no numbers, no checkmark icon, no fake label, no heavy gradient, no dark fill.
```

## 24. dashboard_page_frame_1600x2400.png

용도: 책꽂이 화면 전체를 감싸는 얇은 상용 앱 느낌의 페이지 프레임. 앱 화면 전체에 늘려서 얹습니다.

```text
1600x2400 transparent PNG elegant page frame overlay for a Korean elementary workbook shelf screen. Composition: transparent center from x 90-1510 and y 120-2240 so workbook covers and text remain clear; thin decorative paper-album border around all four edges, slightly stronger at corners, with a subtle bookbinding or stationery feel. Use pale ivory, muted ink blue, and very light lavender-gray accents. Corners may have tiny layered paper tabs or pressed-paper shadows, but keep them low contrast and away from app text. No readable text, no numbers, no icons, no mascot, no large illustration, no fake controls, no heavy texture. Target PNG should stay small by using broad flat transparent areas and simple shapes.
```

## 25. dashboard_title_frame_1000x180.png

용도: `박서아의 문제집들` 제목을 감싸는 더 분명한 전용 프레임. 기존 `dashboard_title_banner`보다 우선 사용됩니다.

```text
1000x180 transparent PNG centered title frame for a workbook shelf dashboard. Composition: transparent outside; a polished paper label, ribbon, or slim album nameplate from x 60-940 and y 24-156. Center x 170-830 and y 48-122 must be clean for large Korean title text rendered by the app. Add a refined thin border, soft lower shadow, and subtle corner tape or folded-paper detail at very low contrast. It should look more premium than a plain banner but not like a button. No readable text, no letters, no numbers, no icons, no characters, no fake title printed inside.
```

## PNG 용량/내보내기 지침

스킨은 태블릿에서 여러 화면에 반복적으로 로드됩니다. 생성 이미지가 너무 크면 앱 실행과 화면 전환이 느려질 수 있으므로 아래 기준을 지킵니다.

- 모든 PNG는 sRGB, 8-bit/channel로 저장합니다. 16-bit PNG는 사용하지 않습니다.
- EXIF, 생성툴 메타데이터, 프롬프트 메타데이터는 제거합니다.
- 전체 배경처럼 큰 이미지(`1600x2560`)는 고주파 종이 노이즈를 넣지 않습니다. 미세 노이즈가 많을수록 PNG 압축률이 급격히 나빠집니다.
- 투명도가 필요한 에셋은 알파 채널을 유지하되, 완전 투명 영역은 실제로 alpha 0이어야 합니다. 흰색 불투명 배경을 남기지 않습니다.
- 버튼/스탬프/팝업처럼 색 수가 적은 이미지는 PNG-8 또는 indexed PNG도 허용합니다. 단, 투명 가장자리에 계단 현상이 생기면 PNG-24로 되돌립니다.
- 권장 용량 목표:
  - `dashboard_bg_1600x2560.png`: 1.2MB 이하
  - `problem_paper_bg_1600x2560.png`: 900KB 이하
  - `book_cover_base_512x700.png`: 180KB 이하
  - `chapter_row_tab_1200x180.png`: 120KB 이하
  - `bottom_menu_plate_1600x160.png`: 120KB 이하
  - `round_btn_*.png`: 25KB 이하
  - `dashboard_page_frame_1600x2400.png`: 220KB 이하
  - `dashboard_title_banner_1200x220.png`: 130KB 이하
  - `dashboard_title_frame_1000x180.png`: 90KB 이하
  - `problem_header_pill_900x96.png`: 70KB 이하
  - `nav_arrow_*.png`, `hint_button_128x128.png`: 30KB 이하
  - `submit_button_320x128.png`: 45KB 이하
- PNG 압축 예시:
  - 무손실: `oxipng -o 4 --strip all *.png`
  - 더 작은 용량: `pngquant --quality=75-92 --strip --skip-if-larger --ext .png --force *.png`
- 압축 후 앱에 넣기 전에 확대해서 가장자리 투명도, 텍스트 가독성, 버튼처럼 보이는 장식이 없는지 확인합니다.

## 생성 후 체크

- 파일명은 `skin.json`의 `assets` 값과 정확히 맞춘다.
- 버튼, 스탬프, 오버레이, 팝업 배경은 가능한 투명 PNG로 저장한다.
- 문제 화면에 쓰이는 배경은 필기와 문제 이미지를 방해하지 않도록 대비를 낮춘다.
- 책 표지, 책등, 그림자는 같은 방향의 빛을 써야 한다. 기본 그림자 방향은 아래쪽-오른쪽이다.
- 한 스킨 안에서는 종이색, 잉크색, 그림자 색이 서로 달라 보이지 않아야 한다.
- 앱 화면 위에 얹었을 때 장식이 버튼, 진행률, 입력칸처럼 보이지 않아야 한다.
- 실제 태블릿 화면 캡처에서 텍스트와 문제 이미지가 스킨보다 먼저 보여야 한다.
