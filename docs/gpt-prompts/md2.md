# md2 - 그림 보존 우선

이 사진은 도형/무늬/격자 문제가 많다. 앱에서 그림이 잘리면 안 되므로 이미지 보존을 최우선으로 `workbook.json`을 만든다.

반드시 지킬 것:
- 원문 문장은 `questionText`에 사진 그대로 적는다. 한 글자도 바꾸지 않는다.
- 도형, 격자, 화살표, 보기 상자, 빈칸, 예시 그림은 모두 `imagePath` 이미지 안에 포함한다.
- 그림 일부가 애매하면 더 넓게 자른다. 절대 타이트하게 자르지 않는다.
- 사진의 손글씨 체크, 낙서, 페이지 번호, 제본 링은 문제 내용이 아니면 제외한다.
- 자동으로 답을 만들지 않는다. 그림을 그리는 문제는 수동채점 문제다.

각 문제 JSON:
```json
{
  "problemType": "MANUAL_ONLY",
  "questionText": "사진 원문 그대로",
  "imagePath": "images/p056-2-1.jpg",
  "imageDisplayJson": {
    "heightDp": 420,
    "widthFraction": 1.0,
    "align": "center",
    "placement": "belowText"
  },
  "answerFields": [
    { "label": "풀이장에 그리기", "fieldType": "TEXT", "required": false }
  ],
  "answerRules": [
    { "answerType": "MANUAL", "correctAnswerRaw": "", "manualGradingRequired": true }
  ]
}
```

출력:
- `workbook.json` 전체를 JSON 코드블록 하나로 출력한다.
- 이어서 `image_manifest` 배열을 출력한다.
- `image_manifest`에는 각 `imagePath`별로 “어느 영역을 잘라야 하는지”를 짧게 적는다.
- 불확실한 OCR은 추측하지 말고 `NEEDS_REVIEW: 원문 후보`로 남긴다.
