package com.mathworkbook.app.core

import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.database.AppSettingsEntity
import com.mathworkbook.app.core.database.ChapterEntity
import com.mathworkbook.app.core.database.ChoiceEntity
import com.mathworkbook.app.core.database.ExamEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.database.ProblemTemplateEntity
import com.mathworkbook.app.core.database.StudentEntity
import com.mathworkbook.app.core.database.WorkbookEntity
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.AnswerType
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.domain.UnitType
import org.json.JSONArray

class SeedData(private val dao: MathDao) {
    suspend fun ensure() {
        if (dao.getAllProblemsOnce().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val workbookId = "workbook-demo"
        val chapterId = "chapter-demo-1"

        dao.upsertAppSettings(
            AppSettingsEntity(
                defaultMaxInputTryCount = 3,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.upsertStudent(StudentEntity(studentId = "student-demo", name = "학생"))
        dao.upsertWorkbook(
            WorkbookEntity(
                workbookId = workbookId,
                title = "초등 수학 MVP 문제집",
                description = "사진 문제, 단답형, 객관식, 복수 답 흐름 확인용",
                grade = "초등",
                createdAt = now,
                updatedAt = now,
                version = 1
            )
        )
        dao.upsertChapter(
            ChapterEntity(
                chapterId = chapterId,
                workbookId = workbookId,
                title = "1단원 계산과 큰 수",
                orderIndex = 1
            )
        )

        insertAdditionProblem(workbookId, chapterId, now)
        insertMultiFieldProblem(workbookId, chapterId, now)
        insertChoiceProblem(workbookId, chapterId, now)

        dao.upsertExam(
            ExamEntity(
                examId = "exam-demo",
                title = "MVP 테스트 시험",
                workbookId = workbookId,
                chapterIdsJson = JSONArray(listOf(chapterId)).toString(),
                problemIdsJson = JSONArray(listOf("problem-addition", "problem-large-small", "problem-choice")).toString(),
                timeLimitSeconds = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun insertAdditionProblem(workbookId: String, chapterId: String, now: Long) {
        dao.upsertProblem(
            ProblemEntity(
                problemId = "problem-addition",
                workbookId = workbookId,
                chapterId = chapterId,
                problemType = ProblemType.SHORT_NUMBER,
                questionText = "23 + 18 = ?",
                questionLatex = null,
                imagePath = null,
                sourcePageImagePath = null,
                imageCropRectJson = null,
                maskOverlayJson = null,
                difficulty = 1,
                orderIndex = 1,
                hintText = "일의 자리부터 더해보자.",
                hasGenerationTemplate = true,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.upsertAnswerField(
            AnswerFieldEntity(
                answerFieldId = "field-addition-answer",
                problemId = "problem-addition",
                label = "답",
                fieldType = AnswerFieldType.NUMBER,
                orderIndex = 1,
                positionJson = null,
                required = true
            )
        )
        dao.upsertAnswerRule(
            AnswerRuleEntity(
                answerRuleId = "rule-addition-answer",
                problemId = "problem-addition",
                answerFieldId = "field-addition-answer",
                answerType = AnswerType.INTEGER,
                correctAnswerRaw = "41",
                normalizedAnswer = "41",
                allowEquivalentFraction = false,
                requireSimplifiedFraction = false,
                decimalTolerance = null,
                allowMultipleAnswers = false,
                acceptedAnswersJson = null,
                unitType = UnitType.NONE,
                manualGradingRequired = false
            )
        )
        dao.upsertTemplate(
            ProblemTemplateEntity(
                templateId = "template-addition",
                problemId = "problem-addition",
                templateText = "{a} + {b} = ?",
                templateLatex = null,
                variableRulesJson = """{"variables":[{"name":"a","min":10,"max":99},{"name":"b","min":10,"max":99}]}""",
                answerFormulaJson = """{"fields":[{"answerFieldId":"field-addition-answer","formula":"a + b"}]}""",
                validationRulesJson = null,
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun insertMultiFieldProblem(workbookId: String, chapterId: String, now: Long) {
        dao.upsertProblem(
            ProblemEntity(
                problemId = "problem-large-small",
                workbookId = workbookId,
                chapterId = chapterId,
                problemType = ProblemType.MULTI_FIELD,
                questionText = "숫자 카드 2, 5, 0을 한 번씩 사용하여 가장 큰 수와 가장 작은 수를 구하세요.",
                questionLatex = null,
                imagePath = null,
                sourcePageImagePath = null,
                imageCropRectJson = null,
                maskOverlayJson = null,
                difficulty = 2,
                orderIndex = 2,
                hintText = "가장 작은 수를 만들 때 첫 자리는 0이 될 수 없어.",
                hasGenerationTemplate = false,
                createdAt = now,
                updatedAt = now
            )
        )
        val fields = listOf(
            AnswerFieldEntity("field-largest", "problem-large-small", "가장 큰 수", AnswerFieldType.NUMBER, 1, null, true),
            AnswerFieldEntity("field-smallest", "problem-large-small", "가장 작은 수", AnswerFieldType.NUMBER, 2, null, true)
        )
        val rules = listOf(
            AnswerRuleEntity("rule-largest", "problem-large-small", "field-largest", AnswerType.INTEGER, "520", "520", false, false, null, false, null, UnitType.NONE, false),
            AnswerRuleEntity("rule-smallest", "problem-large-small", "field-smallest", AnswerType.INTEGER, "205", "205", false, false, null, false, null, UnitType.NONE, false)
        )
        fields.forEach { dao.upsertAnswerField(it) }
        rules.forEach { dao.upsertAnswerRule(it) }
    }

    private suspend fun insertChoiceProblem(workbookId: String, chapterId: String, now: Long) {
        dao.upsertProblem(
            ProblemEntity(
                problemId = "problem-choice",
                workbookId = workbookId,
                chapterId = chapterId,
                problemType = ProblemType.MULTIPLE_CHOICE,
                questionText = "다음 중 1/2와 같은 값을 모두 고르세요.",
                questionLatex = null,
                imagePath = null,
                sourcePageImagePath = null,
                imageCropRectJson = null,
                maskOverlayJson = null,
                difficulty = 2,
                orderIndex = 3,
                hintText = null,
                hasGenerationTemplate = false,
                createdAt = now,
                updatedAt = now
            )
        )
        dao.upsertAnswerField(
            AnswerFieldEntity(
                answerFieldId = "field-choice",
                problemId = "problem-choice",
                label = "보기",
                fieldType = AnswerFieldType.CHOICE,
                orderIndex = 1,
                positionJson = null,
                required = true
            )
        )
        dao.upsertAnswerRule(
            AnswerRuleEntity(
                answerRuleId = "rule-choice",
                problemId = "problem-choice",
                answerFieldId = "field-choice",
                answerType = AnswerType.CHOICE,
                correctAnswerRaw = "choice-2,choice-3",
                normalizedAnswer = "choice-2,choice-3",
                allowEquivalentFraction = false,
                requireSimplifiedFraction = false,
                decimalTolerance = null,
                allowMultipleAnswers = true,
                acceptedAnswersJson = null,
                unitType = UnitType.NONE,
                manualGradingRequired = false
            )
        )
        listOf(
            ChoiceEntity("choice-1", "problem-choice", "1/3", "1/3", false, 1),
            ChoiceEntity("choice-2", "problem-choice", "2/4", "2/4", true, 2),
            ChoiceEntity("choice-3", "problem-choice", "0.5", "0.5", true, 3),
            ChoiceEntity("choice-4", "problem-choice", "3/5", "3/5", false, 4)
        ).forEach { dao.upsertChoice(it) }
    }
}
