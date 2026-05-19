# Workbook ZIP Import Format

앱의 마스터 화면에서 ZIP 파일을 올리면 `workbook.json`과 이미지 파일을 읽어 문제집으로 등록합니다.

## ZIP 구조

권장 구조:

```text
math-4-1.zip
  workbook.json
  images/
    p001.jpg
    p002.jpg
```

ZIP 안에 폴더가 한 겹 더 있어도 `workbook.json`은 자동으로 찾습니다.

## 작성 원칙

- 문제집 제목은 `4-1 수학`, `4-2 수학`처럼 4-12글자가 보기 좋습니다.
- 단원명은 `큰 수`, `각도`, `분수의 덧셈`처럼 2-16글자를 권장합니다.
- `questionText`는 한 문제당 15-80글자 정도가 가장 안정적입니다.
- 한 줄이 너무 길면 태블릿에서 읽기 불편하므로 24-32글자마다 자연스럽게 문장을 나누세요.
- 답 입력칸 label은 `답`, `가장 큰 수`, `㉠의 각도`처럼 2-8글자가 좋습니다.
- 객관식 보기 문장은 보기 하나당 1-20글자를 권장합니다.
- 숫자 좌표와 크기 값은 반드시 `0.62`처럼 소수점이 있는 숫자로 적습니다.
- 좌표는 이미지 기준 상대값입니다. `left`, `top`, `width`, `height`는 모두 `0.0`부터 `1.0` 사이입니다.
- 소수점은 보통 세 자리까지면 충분합니다. 예: `0.125`, `0.64`
- 숫자 답안을 입력받는 문제는 `fieldType`을 반드시 `NUMBER`, `MONEY`, `ANGLE` 중 하나로 설정하세요. 그래야 태블릿에서 해당 입력칸을 누를 때 숫자 키보드가 뜹니다.
- 정답 숫자는 쉼표 없이 저장합니다. 예: `62854000`
- 소수 정답은 마침표를 씁니다. 예: `3.14`
- 돈 문제 정답도 기본은 숫자만 씁니다. 예: `62854000`
- 각도 문제 정답도 기본은 숫자만 씁니다. 예: `50`
- 분수 정답은 `분자/분모`로 씁니다. 예: `1/2`

## 이미지 제작 지침

- 문제 이미지는 이미 외부에서 잘라진 상태로 넣는 것을 권장합니다.
- 권장 이미지 크기: 가로 `1200-1800px`, 세로 `500-1200px`
- 아주 긴 세로 이미지는 문제 영역에서 작아지므로 문제별로 잘라서 넣으세요.
- 문제 이미지 안쪽 여백은 최소 40px 정도 남기면 보기 좋습니다.
- 글자 크기는 태블릿 기준 최소 36px 이상을 권장합니다.
- 이미지가 문제 영역 전체를 차지해야 하면 `widthFraction: 1.0`을 사용합니다.
- 작은 도형 문제는 `widthFraction: 0.55-0.75`, `align: "center"`가 보기 좋습니다.
- 오른쪽에 놓는 보조 그림은 `widthFraction: 0.45-0.6`, `align: "end"`를 사용할 수 있습니다.
- 이미지가 본문보다 먼저 보여야 하면 `placement: "aboveText"`를 사용합니다.
- 기본 배치는 `placement: "belowText"`입니다.

## 이미지 표시 옵션

문제별로 `imageDisplayJson`을 넣으면 앱에서 이미지 크기와 배치를 반영합니다.

```json
"imageDisplayJson": {
  "heightDp": 240,
  "widthFraction": 0.85,
  "align": "center",
  "placement": "belowText"
}
```

필드 설명:

- `heightDp`: 앱 화면에서 이미지 높이입니다. 권장 범위는 `180-320`입니다.
- `widthFraction`: 문제 영역 너비 대비 이미지 너비입니다. `1.0`이면 전체 너비입니다.
- `align`: `start`, `center`, `end` 중 하나입니다.
- `placement`: `aboveText`, `belowText` 중 하나입니다.

## 마스킹 좌표

답이나 빨간 채점 표시를 가릴 때는 `maskOverlayJson`을 사용합니다.

```json
"maskOverlayJson": {
  "items": [
    {
      "id": "mask-answer-1",
      "rect": {
        "left": 0.62,
        "top": 0.68,
        "width": 0.22,
        "height": 0.12
      },
      "color": "#FFFFFF"
    }
  ]
}
```

좌표는 이미지 기준입니다. 예를 들어 `left: 0.62`는 이미지 왼쪽에서 62% 지점입니다.

## workbook.json 예시

```json
{
  "workbook": {
    "workbookId": "math-4-1",
    "title": "4-1 수학",
    "description": "1학기 수학 문제집",
    "grade": "4",
    "version": 1
  },
  "chapters": [
    {
      "chapterId": "math-4-1-ch01",
      "title": "큰 수",
      "orderIndex": 1
    },
    {
      "chapterId": "math-4-1-ch02",
      "title": "각도",
      "orderIndex": 2
    }
  ],
  "problems": [
    {
      "problemId": "math-4-1-ch01-p001",
      "chapterId": "math-4-1-ch01",
      "problemType": "IMAGE_BASED",
      "questionText": "빈칸에 알맞은 수를 쓰세요.",
      "imagePath": "images/p001.jpg",
      "imageDisplayJson": {
        "heightDp": 240,
        "widthFraction": 1.0,
        "align": "center",
        "placement": "belowText"
      },
      "maskOverlayJson": {
        "items": [
          {
            "id": "mask-answer-1",
            "rect": {
              "left": 0.62,
              "top": 0.68,
              "width": 0.22,
              "height": 0.12
            },
            "color": "#FFFFFF"
          }
        ]
      },
      "orderIndex": 1,
      "answerFields": [
        {
          "answerFieldId": "p001-answer",
          "label": "답",
          "fieldType": "NUMBER",
          "orderIndex": 1,
          "required": true
        }
      ],
      "answerRules": [
        {
          "answerRuleId": "p001-rule",
          "answerFieldId": "p001-answer",
          "answerType": "INTEGER",
          "correctAnswerRaw": "62854000",
          "normalizedAnswer": "62854000",
          "unitType": "NONE"
        }
      ]
    },
    {
      "problemId": "math-4-1-ch01-p002",
      "chapterId": "math-4-1-ch01",
      "problemType": "MULTI_FIELD",
      "questionText": "숫자 카드 2, 5, 0으로 가장 큰 수와 가장 작은 수를 쓰세요.",
      "orderIndex": 2,
      "answerFields": [
        {
          "answerFieldId": "p002-largest",
          "label": "가장 큰 수",
          "fieldType": "NUMBER",
          "orderIndex": 1,
          "required": true
        },
        {
          "answerFieldId": "p002-smallest",
          "label": "가장 작은 수",
          "fieldType": "NUMBER",
          "orderIndex": 2,
          "required": true
        }
      ],
      "answerRules": [
        {
          "answerRuleId": "p002-rule-largest",
          "answerFieldId": "p002-largest",
          "answerType": "INTEGER",
          "correctAnswerRaw": "520",
          "normalizedAnswer": "520"
        },
        {
          "answerRuleId": "p002-rule-smallest",
          "answerFieldId": "p002-smallest",
          "answerType": "INTEGER",
          "correctAnswerRaw": "205",
          "normalizedAnswer": "205"
        }
      ]
    }
  ]
}
```

## 지원 enum 값

- `problemType`: `MULTIPLE_CHOICE`, `SHORT_NUMBER`, `MULTI_FIELD`, `IMAGE_BASED`, `LATEX`, `MANUAL_ONLY`
- `fieldType`: `TEXT`, `NUMBER`, `FRACTION`, `CHOICE`, `MONEY`, `ANGLE`
- `answerType`: `INTEGER`, `DECIMAL`, `FRACTION`, `PERCENT`, `ANGLE`, `MONEY`, `UNIT_VALUE`, `CHOICE`, `MANUAL`
- `unitType`: `NONE`, `WON`, `DEGREE`, `PERCENT`, `CUSTOM`
