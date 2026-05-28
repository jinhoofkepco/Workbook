# Skin Concept Element Prompts

아래 프롬프트는 특정 디자인 컨셉을 정하지 않습니다. 먼저 원하는 컨셉을 직접 적고, 그 뒤에 필요한 요소 목록만 붙여서 사용하세요.

## Prompt 1: 전체 요소 보드

```text
Use my chosen visual concept and create one cohesive UI asset concept board for an Android tablet math workbook app. I will manually crop the assets later, so exact pixel sizes are not important. Please draw these separate-looking elements on one board: workbook selection page background, page frame, title frame, slim workbook cover, workbook spine, chapter/progress list row, problem-solving page background, top toolbar strip, centered problem title header, previous button, next button, hint button, master button idle state, master button active state, submit button, grading/loading button, answer stamp, and wrong-answer mark. Keep text areas blank and easy to read. Do not add readable words, numbers, fake app labels, problem text, or completed UI screens.
```

## Prompt 2: 배경과 큰 프레임 중심

```text
Use my chosen visual concept and make a crop-friendly UI asset board for a tablet workbook app. Focus on large reusable pieces: workbook selection page background, outer page frame, title frame, workbook cover, workbook spine, chapter/progress row, problem-solving paper background, top toolbar strip, and centered problem title header. The elements should feel like one matching set and have clean areas where real app text will appear. No readable text, no numbers, no fake app content.
```

## Prompt 3: 버튼과 작은 장식 중심

```text
Use my chosen visual concept and create a crop-friendly button and overlay sheet for a tablet math workbook app. Include: previous button, next button, hint button, master idle button, master active button, submit button, grading/loading button, answer stamp, wrong-answer mark, and a few small matching decorative frame pieces. Buttons must already contain their visual meaning through shape or icon because the app will not draw extra text or symbols on top. No readable text, no numbers, no labels.
```

## 커터에서 자를 때 기준

- AI에게 정확한 픽셀 크기를 맞추라고 강하게 시키지 않습니다. 컨셉과 통일감을 먼저 만들고, 정확한 크기는 `tools/skin_concept_cutter.py`에서 맞춥니다.
- PNG 파일은 실제로는 네모 파일이어도 됩니다. 중요한 것은 버튼 주변의 필요 없는 영역이 투명해야 한다는 점입니다.
- 동그란 버튼은 네모로 넉넉하게 자른 뒤 `Clip shape = circle`을 적용하면 됩니다.
- 둥근 사각 버튼은 `Clip shape = rounded`가 어울립니다.
- 이전/다음 화살표 버튼은 네모로 자른 뒤 배경색 투명화만 해도 되고, 더 날렵하게 보이고 싶으면 `Clip shape = left_arrow` 또는 `right_arrow`를 적용합니다.
- 화살표 버튼 안에 화살표 그림 자체는 들어 있어야 합니다. 앱이 `<`, `>` 문자를 따로 얹지 않습니다.
- 힌트, 제출, 채점중 버튼도 이미지 자체만 보고 의미가 보여야 합니다. 앱이 `?`, `제출`, `채점중` 글자를 얹지 않습니다.
- 버튼 밖에 배경이 남아 있으면 실제 앱에서 네모난 자국처럼 보입니다. 이때 `Pick transparent color from image`, `Auto transparent from crop corners`, `Threshold`를 조절합니다.
- 문제 풀이 화면 배경은 예뻐도 아주 연해야 합니다. 문제 사진과 학생 필기가 더 중요합니다.
