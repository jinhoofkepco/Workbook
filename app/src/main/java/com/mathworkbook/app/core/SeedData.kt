package com.mathworkbook.app.core

import android.content.Context
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
import com.mathworkbook.app.core.files.SCAN_MVP_ASSET_MANIFEST
import com.mathworkbook.app.core.files.SCAN_MVP_WORKBOOK_ID
import com.mathworkbook.app.core.files.WorkbookManifestType
import com.mathworkbook.app.core.files.detectWorkbookManifestType
import org.json.JSONArray
import org.json.JSONObject

class SeedData(
    private val context: Context,
    private val dao: MathDao
) {
    suspend fun ensure() {
        val now = System.currentTimeMillis()
        val hasExistingProblems = dao.getAllProblemsOnce().isNotEmpty()
        ensureScanWorkbookMvp(now)
        if (hasExistingProblems) return

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

    private suspend fun ensureScanWorkbookMvp(now: Long) {
        runCatching {
            val root = context.assets.open(SCAN_MVP_ASSET_MANIFEST).use { input ->
                JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
            }
            if (detectWorkbookManifestType(root) != WorkbookManifestType.ScanPageCoordinates) return

            val workbookObject = root.optJSONObject("workbook")
            val workbookId = workbookObject
                ?.optString("workbookId")
                ?.ifBlank { SCAN_MVP_WORKBOOK_ID }
                ?: SCAN_MVP_WORKBOOK_ID
            val title = root.optString("title")
                .ifBlank { workbookObject?.optString("title").orEmpty() }
                .ifBlank { "스캔 문제집" }
            val chapterId = "$workbookId-scan-pages"

            dao.upsertWorkbook(
                WorkbookEntity(
                    workbookId = workbookId,
                    title = title,
                    description = "스캔 원본 위에 답칸을 얹어 푸는 문제집",
                    grade = "",
                    subject = "math",
                    createdAt = now,
                    updatedAt = now,
                    version = workbookObject?.optInt("version", 1) ?: 1
                )
            )
            dao.upsertChapter(
                ChapterEntity(
                    chapterId = chapterId,
                    workbookId = workbookId,
                    title = "스캔 페이지",
                    orderIndex = 1
                )
            )

            var orderIndex = 1
            val pages = root.getJSONArray("pages")
            for (pageIndex in 0 until pages.length()) {
                val pageJson = pages.getJSONObject(pageIndex)
                val pageNumber = pageJson.optInt("pageNumber", pageIndex + 1)
                val pageId = pageJson.getString("pageId")
                val assetPath = pageJson.optString("assetPath")
                val problems = pageJson.getJSONArray("problems")
                for (problemIndex in 0 until problems.length()) {
                    val problemJson = problems.getJSONObject(problemIndex)
                    val problemId = problemJson.optString("problemId")
                        .ifBlank { "$pageId-problem-${problemIndex + 1}" }
                    val label = problemJson.optString("label").ifBlank { "문제 ${problemIndex + 1}" }
                    dao.upsertProblem(
                        ProblemEntity(
                            problemId = problemId,
                            workbookId = workbookId,
                            chapterId = chapterId,
                            problemType = ProblemType.IMAGE_BASED,
                            questionText = "${pageNumber}쪽 $label",
                            questionLatex = null,
                            imagePath = assetPath,
                            sourcePageImagePath = assetPath,
                            imageCropRectJson = null,
                            maskOverlayJson = null,
                            difficulty = null,
                            orderIndex = orderIndex++,
                            hintText = null,
                            hasGenerationTemplate = false,
                            createdAt = now,
                            updatedAt = now
                        )
                    )

                    val answerFields = problemJson.getJSONArray("answerFields")
                    for (fieldIndex in 0 until answerFields.length()) {
                        val fieldJson = answerFields.getJSONObject(fieldIndex)
                        val fieldId = fieldJson.optString("fieldId")
                            .ifBlank { "$problemId-answer-${fieldIndex + 1}" }
                        val answer = fieldJson.optString("answer")
                        val answerType = inferScanAnswerType(answer)
                        dao.upsertAnswerField(
                            AnswerFieldEntity(
                                answerFieldId = fieldId,
                                problemId = problemId,
                                label = fieldJson.optString("label", "답"),
                                fieldType = answerType.toAnswerFieldType(),
                                orderIndex = fieldIndex + 1,
                                positionJson = null,
                                required = true
                            )
                        )
                        dao.upsertAnswerRule(
                            AnswerRuleEntity(
                                answerRuleId = "$fieldId-rule",
                                problemId = problemId,
                                answerFieldId = fieldId,
                                answerType = answerType,
                                correctAnswerRaw = answer,
                                normalizedAnswer = answer.trim().replace(",", ""),
                                allowEquivalentFraction = answerType == AnswerType.FRACTION,
                                requireSimplifiedFraction = false,
                                decimalTolerance = null,
                                allowMultipleAnswers = false,
                                acceptedAnswersJson = null,
                                unitType = UnitType.NONE,
                                manualGradingRequired = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun inferScanAnswerType(answer: String): AnswerType {
        val cleaned = answer.trim().replace(",", "")
        return when {
            "/" in answer -> AnswerType.FRACTION
            cleaned.toLongOrNull() != null -> AnswerType.INTEGER
            cleaned.toDoubleOrNull() != null -> AnswerType.DECIMAL
            else -> AnswerType.TEXT
        }
    }

    private fun AnswerType.toAnswerFieldType(): AnswerFieldType {
        return when (this) {
            AnswerType.INTEGER,
            AnswerType.DECIMAL,
            AnswerType.PERCENT,
            AnswerType.UNIT_VALUE -> AnswerFieldType.NUMBER
            AnswerType.FRACTION -> AnswerFieldType.FRACTION
            AnswerType.MONEY -> AnswerFieldType.MONEY
            AnswerType.ANGLE -> AnswerFieldType.ANGLE
            else -> AnswerFieldType.TEXT
        }
    }
}
