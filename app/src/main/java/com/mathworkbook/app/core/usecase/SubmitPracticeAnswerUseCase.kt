package com.mathworkbook.app.core.usecase

import com.mathworkbook.app.core.database.AttemptInputLogEntity
import com.mathworkbook.app.core.database.MathDao
import com.mathworkbook.app.core.database.PracticeAttemptEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.core.domain.GradingResult
import com.mathworkbook.app.core.generation.SimilarProblemQueueService
import com.mathworkbook.app.core.grading.GradingEngine
import java.util.UUID

data class PracticeSubmissionResult(
    val attempt: PracticeAttemptEntity,
    val gradingResult: GradingResult,
    val remainingTryCount: Int,
    val autoMoveNext: Boolean,
    val similarProblemQueued: Boolean
)

class SubmitPracticeAnswerUseCase(
    private val dao: MathDao,
    private val gradingEngine: GradingEngine,
    private val attemptLimitResolver: AttemptLimitResolver,
    private val similarProblemQueueService: SimilarProblemQueueService
) {
    suspend fun submit(
        studentId: String,
        problem: ProblemEntity,
        generatedProblemId: String?,
        submittedAnswers: Map<String, String>,
        solutionImagePath: String?,
        now: Long = System.currentTimeMillis()
    ): PracticeSubmissionResult {
        val maxTryCount = attemptLimitResolver.resolve(problem.problemId, problem.workbookId)
        val existing = dao.getOpenPracticeAttempt(studentId, problem.problemId)
        val attempt = existing ?: createAttempt(studentId, problem, generatedProblemId, maxTryCount, now)
        if (existing == null) dao.upsertPracticeAttempt(attempt)

        val fields = dao.getAnswerFields(problem.problemId)
        val rules = dao.getAnswerRules(problem.problemId)
        val choices = dao.getChoices(problem.problemId)
        val gradingResult = gradingEngine.grade(problem, fields, rules, choices, submittedAnswers)
        val nextTryNumber = attempt.inputTryCount + 1

        gradingResult.fieldResults.forEach { field ->
            dao.upsertAttemptInputLog(
                AttemptInputLogEntity(
                    inputLogId = UUID.randomUUID().toString(),
                    attemptId = attempt.attemptId,
                    answerFieldId = field.answerFieldId,
                    tryNumber = nextTryNumber,
                    submittedAnswerRaw = field.submittedRaw,
                    normalizedSubmittedAnswer = field.normalizedSubmitted,
                    isCorrect = field.isCorrect,
                    submittedAt = now
                )
            )
        }

        val reachedLimit = !gradingResult.isCorrect && !gradingResult.requiresManualReview && nextTryNumber >= maxTryCount
        val finalStatus = when {
            gradingResult.requiresManualReview -> FinalStatus.MANUAL_REVIEW_REQUIRED
            gradingResult.isCorrect -> FinalStatus.CORRECT
            reachedLimit -> FinalStatus.FAILED_AFTER_MAX_ATTEMPTS
            else -> FinalStatus.IN_PROGRESS
        }
        val shouldQueueSimilar = !gradingResult.isCorrect && !gradingResult.requiresManualReview
        val similarQueued = if (shouldQueueSimilar) {
            val reason = if (reachedLimit) "MAX_ATTEMPTS" else "WRONG"
            similarProblemQueueService.enqueueIfAllowed(problem, studentId, reason)
        } else {
            false
        }

        val updated = attempt.copy(
            finalStatus = finalStatus,
            isCorrect = if (gradingResult.requiresManualReview) null else gradingResult.isCorrect,
            inputTryCount = nextTryNumber,
            maxAttemptsReached = reachedLimit,
            movedToNextByLimit = reachedLimit,
            submittedAt = if (finalStatus == FinalStatus.IN_PROGRESS) null else now,
            elapsedSeconds = ((now - attempt.startedAt) / 1000L).coerceAtLeast(0L),
            solutionImagePath = solutionImagePath ?: attempt.solutionImagePath
        )
        dao.updatePracticeAttempt(updated)

        return PracticeSubmissionResult(
            attempt = updated,
            gradingResult = gradingResult,
            remainingTryCount = (maxTryCount - nextTryNumber).coerceAtLeast(0),
            autoMoveNext = reachedLimit,
            similarProblemQueued = similarQueued
        )
    }

    private suspend fun createAttempt(
        studentId: String,
        problem: ProblemEntity,
        generatedProblemId: String?,
        maxTryCount: Int,
        now: Long
    ): PracticeAttemptEntity {
        val attemptNumber = dao.countAttempts(studentId, problem.problemId) + 1
        return PracticeAttemptEntity(
            attemptId = UUID.randomUUID().toString(),
            studentId = studentId,
            problemId = problem.problemId,
            generatedProblemId = generatedProblemId,
            workbookId = problem.workbookId,
            chapterId = problem.chapterId,
            attemptNumber = attemptNumber,
            finalStatus = FinalStatus.IN_PROGRESS,
            isCorrect = null,
            inputTryCount = 0,
            maxInputTryCount = maxTryCount,
            maxAttemptsReached = false,
            movedToNextByLimit = false,
            hintUsed = false,
            startedAt = now,
            submittedAt = null,
            elapsedSeconds = 0L,
            solutionImagePath = null,
            reviewerComment = null,
            manualReviewStatus = null
        )
    }
}
