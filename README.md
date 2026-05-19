# Math Workbook MVP

Android Native 수학 문제집 앱 MVP입니다.

## 포함된 기능

- Kotlin + Jetpack Compose + Room DB 구조
- 문제 원본, 답 입력칸, 정답 규칙, 학생 풀이 기록, 시험 답안 분리 저장
- 연습 모드 즉시 채점
- 기본 3회 오답 후 자동 다음 문제 이동
- 시험 모드 draft 답안 저장, 제출 전 검토, 최종 제출 후 일괄 채점
- 객관식, 숫자 단답형, 복수 답 입력 문제 예시
- 손글씨 풀이 벡터 JSON 저장
- 사진 문제 crop/mask 저장 구조
- 유사 문제 템플릿과 안전 수식 파서
- 마스터 화면에서 문제/기록/시험 결과 확인
- 마스터 화면에서 `workbook.json + jpg` ZIP 문제집 가져오기
- `imageDisplayJson`으로 문제 이미지 높이, 너비, 정렬, 텍스트 위/아래 배치 지정
- 풀이 기록 목록에서 행을 눌러 상세 풀이 미리보기 확인

## 실행

1. Android Studio에서 이 폴더를 엽니다.
2. Gradle Sync를 실행합니다.
3. `app` 구성을 Android 에뮬레이터나 태블릿 기기에 실행합니다.

현재 Codex 세션에는 `java`, `gradle`, `kotlinc`가 PATH에 없어 이 자리에서 빌드 실행은 하지 못했습니다.

## 주요 코드 위치

- Room 엔티티: `app/src/main/java/com/mathworkbook/app/core/database/Entities.kt`
- DAO: `app/src/main/java/com/mathworkbook/app/core/database/MathDao.kt`
- 채점 엔진: `app/src/main/java/com/mathworkbook/app/core/grading/GradingEngine.kt`
- 연습 제출 로직: `app/src/main/java/com/mathworkbook/app/core/usecase/SubmitPracticeAnswerUseCase.kt`
- 시험 제출 로직: `app/src/main/java/com/mathworkbook/app/core/usecase/SubmitExamUseCase.kt`
- 유사 문제 생성: `app/src/main/java/com/mathworkbook/app/core/generation/SimilarProblemGenerator.kt`
- 파일 저장: `app/src/main/java/com/mathworkbook/app/core/files/FileStorage.kt`
- 연습 화면: `app/src/main/java/com/mathworkbook/app/ui/practice/PracticeScreen.kt`
- 시험 화면: `app/src/main/java/com/mathworkbook/app/ui/exam/ExamScreen.kt`
- 마스터 화면: `app/src/main/java/com/mathworkbook/app/ui/master/MasterScreen.kt`

## 외부 문제집 ZIP 가져오기

마스터 화면의 `ZIP 파일 올리기` 버튼으로 문제집을 가져올 수 있습니다.
ZIP 형식은 `docs/workbook-import-format.md`를 참고하세요.
