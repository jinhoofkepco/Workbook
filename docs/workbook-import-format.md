# Workbook Copy ZIP Import Format

문제집 사진에서 문제 글은 GPT가 그대로 추출하고, 문제에 딸린 그림만 별도 이미지로 넣는 형식입니다.

## ZIP 구조

```text
workbook-copy.zip
  workbook.json
  images/
    1-1.png
    1-2.png
```

`images/`에는 기본적으로 도형, 표, 그래프 같은 보조 그림만 넣습니다. 글자 추출이 어려운 문제는 예외로 문제 전체 사진을 넣을 수 있습니다.

## 작성 원칙

- `questionText`에는 원본 문제의 한글, 숫자, 단위, 기호, 괄호, 보기 문장을 그대로 넣습니다.
- 문제를 요약하거나 쉽게 바꾸거나 글자를 줄이지 않습니다.
- 줄바꿈은 필요하면 넣어도 되지만, 문장 내용과 순서는 원본과 같아야 합니다.
- `cm²`, `cm³`, `mL`, `L`, `㉠`, `ㄱ`, `□` 같은 표기는 원본 그대로 유지합니다.
- 그림이 있는 문제만 `imagePath`를 넣고, 그림 파일명은 문제 번호와 비슷하게 맞춥니다.
- 그림이 없는 문제는 `imagePath`를 생략합니다.
- 만들고 나서 원본 사진과 `questionText`를 다시 대조합니다.

## 최소 예시

```json
{
  "workbook": {
    "workbookId": "math-copy-6-1",
    "title": "6-1 수학",
    "description": "사진에서 추출한 문제집",
    "grade": "6",
    "version": 1
  },
  "chapters": [
    {
      "chapterId": "math-copy-6-1-ch01",
      "title": "직육면체의 겉넓이",
      "orderIndex": 1
    }
  ],
  "problems": [
    {
      "problemId": "math-copy-6-1-p001",
      "chapterId": "math-copy-6-1-ch01",
      "problemType": "IMAGE_BASED",
      "questionText": "오른쪽 그림은 직육면체를 위와 앞에서 본 모양이다. 이 직육면체의 겉넓이는 몇 cm²인가?",
      "imagePath": "images/1-1.png",
      "imageDisplayJson": {
        "heightDp": 240,
        "widthFraction": 0.75,
        "align": "center",
        "placement": "belowText"
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
          "correctAnswerRaw": "100",
          "normalizedAnswer": "100",
          "unitType": "NONE"
        }
      ]
    }
  ]
}
```

## 이미지 표시

- 보조 그림: `heightDp` 180-320
- 큰 도형/그래프: `heightDp` 320-520
- 기본값: `widthFraction: 0.75`, `align: "center"`, `placement: "belowText"`
- `contentScale`: `auto`, `fit`, `fillWidth`, `crop`
- `auto`와 `fillWidth`는 폭을 맞추되 이미지를 자르지 않습니다.
- 문제 전체를 사진으로 넣을 때는 `questionText`를 비우고 `imagePath`에 원본 문제 이미지를 넣은 뒤 `heightDp`를 충분히 크게 잡습니다.

```json
{
  "problemId": "photo-only-p001",
  "problemType": "IMAGE_BASED",
  "questionText": "",
  "imagePath": "images/photo-only-p001.png",
  "imageDisplayJson": {
    "heightDp": 900,
    "widthFraction": 1.0,
    "contentScale": "fillWidth"
  }
}
```

## 검수

- 숫자, 단위, 제곱/세제곱 표기 확인
- 원문에 있는 보기, 조건, 단서 누락 확인
- 그림 파일이 해당 문제의 그림인지 확인
- 정답이 있는 경우 `correctAnswerRaw` 확인

## 답 표시 단위와 입력칸 단위

학생이 입력하고 채점하는 값은 숫자나 문자 값만 둡니다. `correctAnswerRaw`와 `normalizedAnswer`에는 단위, 접두어, 접미어를 넣지 않고, 화면에 붙여 보여 줄 내용은 답칸 메타데이터에 분리합니다.

```json
{
  "answerFieldId": "p007-answer",
  "label": "답",
  "fieldType": "NUMBER",
  "keyboardType": "NUMBER",
  "displaySuffix": "명",
  "showSuffixInInput": true
}
```

학생이 `200`을 입력하면 채점은 `200`으로 하고, 학생 화면과 마스터 모드의 답 표시에는 `200명`으로 보입니다. `showSuffixInInput`이 `true`이면 입력칸 안에도 `명`이 붙어 보여서 학생이 단위를 직접 입력하지 않아도 됩니다.

사용할 수 있는 표시 메타데이터:

- `displayPrefix`: 정답 표시 앞에 붙일 글자입니다. 예: `약 `
- `displaySuffix`: 정답 표시 뒤에 붙일 글자입니다. 예: `일`, `cm`, `%`
- `showPrefixInInput`: `displayPrefix`를 입력칸에도 보여 줄 때 `true`
- `showSuffixInInput`: `displaySuffix`를 입력칸에도 보여 줄 때 `true`
- `inputPrefix`, `inputSuffix`: 정답 표시와 입력칸 표시를 다르게 해야 할 때만 쓰는 입력칸 전용 표시값입니다.

새로 생성하는 JSON은 `label`에 단위를 중복해서 넣지 않습니다. 예를 들어 답이 `4일`이면 `label`은 `답` 또는 빈 문자열로 두고, `correctAnswerRaw`는 `4`, `displaySuffix`는 `일`, `showSuffixInInput`은 `true`로 둡니다.

여러 답칸이 있는 문제는 각 필드마다 `label`과 `displaySuffix`를 둡니다.

```json
{
  "answerFields": [
    {
      "answerFieldId": "p005-us",
      "label": "미국",
      "fieldType": "NUMBER",
      "keyboardType": "NUMBER",
      "displaySuffix": "%",
      "showSuffixInInput": true
    },
    {
      "answerFieldId": "p005-china",
      "label": "중국",
      "fieldType": "NUMBER",
      "keyboardType": "NUMBER",
      "displaySuffix": "%",
      "showSuffixInInput": true
    }
  ],
  "answerRules": [
    {
      "answerRuleId": "p005-r-us",
      "answerFieldId": "p005-us",
      "answerType": "INTEGER",
      "correctAnswerRaw": "20",
      "normalizedAnswer": "20",
      "unitType": "PERCENT"
    },
    {
      "answerRuleId": "p005-r-china",
      "answerFieldId": "p005-china",
      "answerType": "INTEGER",
      "correctAnswerRaw": "35",
      "normalizedAnswer": "35",
      "unitType": "PERCENT"
    }
  ]
}
```

이 경우 파란 답 스탬프에는 `미국 20%, 중국 35%`처럼 표시됩니다.

시간처럼 한 답에 여러 단위가 붙는 경우에는 값마다 답칸을 나눕니다.

```json
{
  "answerFields": [
    {
      "answerFieldId": "p008-hour",
      "label": "",
      "fieldType": "NUMBER",
      "keyboardType": "NUMBER",
      "displaySuffix": "시간",
      "showSuffixInInput": true
    },
    {
      "answerFieldId": "p008-minute",
      "label": "",
      "fieldType": "NUMBER",
      "keyboardType": "NUMBER",
      "displaySuffix": "분",
      "showSuffixInInput": true
    }
  ],
  "answerRules": [
    {
      "answerRuleId": "p008-r-hour",
      "answerFieldId": "p008-hour",
      "answerType": "INTEGER",
      "correctAnswerRaw": "13",
      "normalizedAnswer": "13"
    },
    {
      "answerRuleId": "p008-r-minute",
      "answerFieldId": "p008-minute",
      "answerType": "INTEGER",
      "correctAnswerRaw": "30",
      "normalizedAnswer": "30"
    }
  ]
}
```

이 경우 학생은 `13`, `30`만 입력하고, 입력칸과 정답 표시에는 각각 `13시간`, `30분`처럼 보입니다.

## 비활성 안내 답칸

답은 앱 입력칸에 쓰지 않고 노트나 그래프 위에 직접 쓰게 하고 싶을 때는 비활성 답칸을 둡니다. 학생에게 “어디에 작성해야 하는지”만 보여 주고, 자동 채점은 하지 않습니다.

```json
{
  "answerFieldId": "p001-a1",
  "label": "(1)",
  "fieldType": "TEXT",
  "required": false,
  "disabled": true,
  "readOnly": true,
  "displayValue": "노트에 작성",
  "skipAutoGrading": true,
  "manualReviewRequired": true
}
```

그래프나 표 위에 직접 쓰게 할 때는 `displayValue`만 바꿉니다.

```json
"displayValue": "그래프에 작성"
```

## 수동 채점

직접 그리기, 표 완성, 서술형처럼 자동 채점하지 않을 문제는 아래처럼 표시합니다. 학생 화면에는 `teacherMemo`, `answerNote`, `solutionText`가 나오지 않고, 마스터 모드에서만 참고용으로 보입니다.

```json
{
  "gradingPolicy": {
    "mode": "manual_review",
    "skipOnSubmit": true,
    "reason": "서술 및 그림 작성 문항"
  },
  "teacherMemo": "직접 확인 필요",
  "solutionText": "교사용 풀이를 여기에 적습니다.",
  "answerFields": [
    {
      "answerFieldId": "p001-a1",
      "label": "답",
      "fieldType": "TEXT",
      "skipAutoGrading": true,
      "manualReviewRequired": true
    }
  ],
  "answerRules": [
    {
      "answerRuleId": "p001-r1",
      "answerFieldId": "p001-a1",
      "answerType": "MANUAL_REVIEW",
      "skipAutoGrading": true,
      "manualReviewRequired": true
    }
  ]
}
```

일부 필드만 수동 확인이면 `mixed`를 사용합니다.

```json
"gradingPolicy": {
  "mode": "mixed",
  "skipFieldsOnSubmit": ["p002-a3"]
}
```

## 교사용 풀이와 답안 메모

마스터 모드에서만 보일 설명은 문제 객체에 아래처럼 넣습니다. 학생 화면에는 나오지 않습니다.

```json
{
  "solutionText": "가=26000, 나=56000, 다=37000이므로 나에는 1만 그루 나무 5개와 1천 그루 나무 6개를 그립니다.",
  "teacherMemo": "지도 점이 흐리면 원본 선명본으로 확인",
  "answerNote": "나의 그루수에는 1만 그루 나무 5개, 1천 그루 나무 6개가 들어가야 합니다.",
  "gradingPolicy": {
    "mode": "manual_review",
    "expectedValue": 56000,
    "expectedValues": {
      "가": 26000,
      "나": 56000,
      "다": 37000
    },
    "expectedSymbols": {
      "1만 그루 나무": 5,
      "1천 그루 나무": 6
    }
  }
}
```

간단히 쓰려면 `answerNote`만 넣어도 됩니다. 계산 기준이 여러 개면 `expectedValues`, 그림 기호 기준이면 `expectedSymbols`를 넣으면 마스터 모드의 기준 영역에 표시됩니다.

## 사진 속 객관식 보기

보기 문항이 사진 안에 이미 들어 있는 경우에는 보기 문장을 JSON에 다시 쓰지 않고, 답칸에 누를 버튼만 지정할 수 있습니다. 학생 화면에서는 `① ② ③ ④` 버튼이 답칸 위에 표시되고, 누르면 답칸에 들어갑니다.

```json
{
  "answerFields": [
    {
      "answerFieldId": "p006-answer",
      "label": "답",
      "fieldType": "TEXT",
      "keyboardType": "TEXT",
      "choiceOptions": ["1", "2", "3", "4"],
      "choiceStyle": "circled",
      "choiceValueStyle": "circled",
      "choiceMultiSelect": true
    }
  ],
  "answerRules": [
    {
      "answerRuleId": "p006-rule",
      "answerFieldId": "p006-answer",
      "answerType": "TEXT",
      "correctAnswerRaw": "①, ③, ④",
      "normalizedAnswer": "①, ③, ④"
    }
  ]
}
```

현재 앱은 정답이 `①, ③, ④`처럼 동그라미 숫자로 들어온 문제는 `choiceOptions`가 없어도 자동으로 `① ② ③ ④` 버튼을 보여줍니다.

## 사진 위 수정

문제 전체 사진이 흐리거나 그림 설명을 보강해야 하면 마스터 모드에서 사진 위에 펜으로 표시한 뒤 `사진에 합쳐 저장`을 누릅니다. 앱은 현재 사진과 마스터 노트를 합쳐 새 문제 이미지로 저장하고, 기존 마스터 노트는 비워 둡니다.

## GPT 저장 설명

외부에서 GPT 설명을 미리 넣어둘 때는 문제 객체 최상위에 `workbookApp.gptExplanations`를 넣는 방식을 권장합니다. ZIP을 다시 가져오면 앱 안에서 저장한 설명과 외부 JSON 설명을 `id` 기준으로 합쳐 보여줍니다. 같은 `id`가 있으면 외부 JSON 값이 우선합니다.

```json
{
  "problemId": "p001",
  "questionText": "",
  "imagePath": "images/p001.png",
  "workbookApp": {
    "gptExplanations": [
      {
        "id": "gpt-p001-note-1",
        "title": "자리값과 곱의 성질",
        "prompt": "이 문제를 학생에게 설명해줘.",
        "explanationText": "검색과 미리보기용 순수 텍스트입니다.",
        "explanationHtml": "<p><strong>자리값</strong>을 먼저 비교합니다.</p><ol><li>큰 곱은 큰 수끼리 균형 있게 배치합니다.</li></ol>",
        "savedAt": 1781940000000,
        "updatedAt": 1781940000000
      }
    ]
  }
}
```

기존 내부 layout JSON 형태를 직접 만들 때는 `imageCropRectJson.workbookApp.gptExplanations`에 넣어도 됩니다. 다만 생성 프롬프트에는 최상위 `workbookApp` 방식을 쓰는 편이 덜 헷갈립니다.

## enum 값

- `problemType`: `IMAGE_BASED`, `SHORT_NUMBER`, `MULTI_FIELD`, `MANUAL_ONLY`
- `fieldType`: `TEXT`, `NUMBER`, `FRACTION`, `CHOICE`, `MONEY`, `ANGLE`, `DRAWING`, `TABLE`, `TEXTAREA`
- `answerType`: `TEXT`, `MANUAL`, `MANUAL_REVIEW`, `INTEGER`, `DECIMAL`, `FRACTION`, `UNIT_VALUE`, `CHOICE`
- `unitType`: `NONE`, `WON`, `DEGREE`, `PERCENT`, `CUSTOM`
