# Paper Album v1

`paper_album_v1`은 컨셉 보드 기반으로 만드는 공주풍 종이 앨범 스킨입니다. 개별 요소를 AI에 따로 요청하지 않고, 먼저 전체 컨셉 이미지를 만든 뒤 `tools/skin_concept_cutter.py`에서 앱 규격에 맞게 잘라냅니다.

## 만들기

1. `prompts.md`의 컨셉 보드 프롬프트로 큰 이미지를 만듭니다.
2. 마음에 드는 버전을 여러 장 저장합니다.
3. 아래 명령으로 커터를 실행합니다.

```powershell
python tools\skin_concept_cutter.py
```

4. `Import concept images`로 컨셉 이미지를 불러옵니다.
5. 왼쪽 목록에서 에셋을 고르고, 이미지 위에서 영역을 드래그합니다.
6. `Lock asset ratio`를 켜면 앱 규격 비율에 맞춰 잘립니다.
7. 배경을 지워야 할 때는 `Pick transparent color from image` 또는 `Auto transparent from crop corners`를 사용합니다.
8. 둥근 버튼, 원형 버튼, 화살표 버튼은 `Clip shape`을 적용해 모양을 맞춥니다.
9. Book Shelf, Chapter List, Problem 미리보기로 적용감을 확인합니다.
10. `Export skin zip`으로 앱에 넣을 ZIP을 만듭니다.

## 압축 기준

커터는 PNG를 `optimize=True`, `compress_level=9`로 저장합니다. 이미지가 너무 크면 컨셉 이미지 자체를 과도하게 고해상도로 만들지 말고, 투명한 여백이 많은 오버레이는 단순한 형태로 유지합니다.

추가 압축:

```powershell
oxipng -o 4 --strip all assets\*.png
```

## 확인 기준

- 책꽂이 화면에서 책 표지가 너무 넓적해 보이지 않아야 합니다.
- `박서아의 문제집들` 제목 뒤 그래픽은 글씨를 방해하지 않아야 합니다.
- 문제 화면 헤더는 제목이 읽히고, 문제/노트 시작 위치를 밀지 않아야 합니다.
- 이전/다음/힌트/제출/채점중 버튼은 앱이 문자를 얹지 않아도 의미가 보여야 합니다.
- 문제지 이미지와 학생 필기 위에 배경 장식이 튀면 실패입니다.

## 투명 배경 기준

PNG 파일은 네모여도 괜찮습니다. 동그란 버튼도 `128x128` 네모 PNG 안에 저장됩니다. 다만 동그라미 밖은 투명해야 앱에서 네모난 종이 조각처럼 보이지 않습니다.

- 동그란 버튼: 네모로 자른 뒤 `Clip shape = circle`
- 둥근 사각 버튼: `Clip shape = rounded`
- 화살표 버튼: 네모로 자른 뒤 배경색을 투명화하거나 `Clip shape = left_arrow/right_arrow`
- 그림자나 리본이 버튼 밖으로 자연스럽게 삐져나와야 하는 경우: `Clip shape`은 쓰지 말고, 배경색 투명화만 사용

## 겹치는 에셋 처리

모든 에셋을 고를 필요는 없습니다. 예를 들어 `dashboardBackground`와 `dashboardPageFrame`이 거의 같은 면적을 덮는다면 하나만 써도 됩니다.

- 배경 그림 안에 테두리까지 포함되어 있으면 `dashboardPageFrame`을 생략합니다.
- 배경과 테두리를 따로 조정하고 싶으면 둘 다 만듭니다.
- `dashboardPageFrame`을 고르지 않으면 뒤의 `dashboardBackground`가 그대로 보입니다.
- 고르지 않은 에셋은 ZIP에 포함되지 않습니다.

## 표시 순서

- 책 화면: 기본 배경 -> `dashboardBackground` -> `dashboardPageFrame` -> 제목 프레임/책 표지 -> 실제 글자와 진도
- 세부진도 화면: 기본 배경 -> `dashboardBackground` -> 선택 책/단원 행 -> 실제 글자와 진행 상태
- 문제 화면: 노트 배경 -> `problemPaperBackground` -> 문제/답 영역 -> `toolbarStrip` -> 상단 헤더와 버튼

커터 오른쪽 미리보기의 노란색 표시는 이제 채우지 않고 테두리만 보여줍니다. 버튼 모서리나 화살표 바깥이 투명하면 아래 배경 또는 체크무늬 미리보기에서 그대로 확인됩니다.
