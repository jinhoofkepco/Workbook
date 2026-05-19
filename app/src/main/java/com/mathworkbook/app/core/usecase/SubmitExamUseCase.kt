package com.mathworkbook.app.core.usecase

import com.mathworkbook.app.core.database.ExamAnswerEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.domain.ExamSessionStatus
import com.mathworkbook.app.core.domain.GradingStatus
import com.mathworkbook.app.core.generation.SimilarProblemQueueService
import com.mathworkbook.app.core.grading.GradingEngine

data class ExamSubmitResult(
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val score: Double
)

class SubmitExamUseCase(
    private val dao: MathDao,
    private val gradingEngine: GradingEngine,
    private val similarProblemQueueService: SimilarProblemQueueService
) {
    suspend fun submit(sessionId: String, queueSimilarProblems: Boolean = true): ExamSubmitResult {
        val session = dao.getExamSession(sessionId) ?: error("Exam session not found: $sessionId")
        val answers = dao.getExamAnswers(sessionId)
        val problemIds = answers.map { it.problemId }.distinct()

        var correctCount = 0
        var wrongCount = 0
        var blankCount = 0

        problemIds.forEach { problemId ->
            val problem = dao.getProblem(problemId) ?: return@forEach
            val fields = dao.getAnswerFields(problemId)
            val rules = dao.getAnswerRules(problemId)
            val choices = dao.getChoices(problemId)
            val answersForProblem = answers.filter { it.problemId == problemId }
            val submittedMap = answersForProblem.associate {
                (it.answerFieldId ?: "choice") to it.submittedAnswerRaw.orEmpty()
            }
            val result = gradingEngine.grade(problem, fields, rules, choices, submittedMap)
            val isBlank = result.blankCount >= fields.size.coerceAtLeast(1)

            when {
                isBlank -> blankCount += 1
                result.isCorrect -> correctCount += 1
                else -> wrongCount += 1
            }

            result.fieldResults.forEach { fieldResult ->
                val answer = answersForProblem.firstOrNull {
                    it.answerFieldId == fieldResult.answerFieldId
                } ?: answersForProblem.firstOrNull()
                if (answer != null) {
                    dao.upsertExamAnswer(
                        answer.copy(
                            normalizedSubmittedAnswer = fieldResult.normalizedSubmitted,
                            isCorrect = fieldResult.isCorrect,
                            gradingStatus = when {
                                result.requiresManualReview -> GradingStatus.MANUAL_REQUIRED
                                isBlank -> GradingStatus.BLANK
                                else -> GradingStatus.GRADED
                            },
                            lastEditedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            if (queueSimilarProblems && !result.isCorrect && !isBlank) {
                similarProblemQueueService.enqueueIfAllowed(problem, session.studentId, "EXAM_WRONG")
            }
        }

        val total = problemIds.size.coerceAtLeast(1)
        val score = correctCount.toDouble() / total.toDouble() * 100.0
        dao.updateExamSession(
            session.copy(
                submittedAt = System.currentTimeMillis(),
                status = ExamSessionStatus.GRADED,
                totalProblemCount = total,
                correctCount = correctCount,
                wrongCount = wrongCount,
                blankCount = blankCount,
                score = score
            )
        )

        return ExamSubmitResult(
            correctCount = correctCount,
            wrongCount = wrongCount,
            blankCount = blankCount,
            score = score
        )
    }
}
