package com.mathworkbook.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mathworkbook.app.core.domain.AnswerFieldType
import com.mathworkbook.app.core.domain.AnswerType
import com.mathworkbook.app.core.domain.ExamSessionStatus
import com.mathworkbook.app.core.domain.FinalStatus
import com.mathworkbook.app.core.domain.GradingStatus
import com.mathworkbook.app.core.domain.ManualReviewStatus
import com.mathworkbook.app.core.domain.ProblemType
import com.mathworkbook.app.core.domain.UnitType

@Entity
data class WorkbookEntity(
    @PrimaryKey val workbookId: String,
    val title: String,
    val description: String,
    val grade: String,
    val subject: String = "math",
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int
)

@Entity(indices = [Index("workbookId")])
data class ChapterEntity(
    @PrimaryKey val chapterId: String,
    val workbookId: String,
    val title: String,
    val orderIndex: Int
)

@Entity(indices = [Index("workbookId"), Index("chapterId")])
data class ProblemEntity(
    @PrimaryKey val problemId: String,
    val workbookId: String,
    val chapterId: String,
    val problemType: ProblemType,
    val questionText: String?,
    val questionLatex: String?,
    val imagePath: String?,
    val sourcePageImagePath: String?,
    val imageCropRectJson: String?,
    val maskOverlayJson: String?,
    val difficulty: Int?,
    val orderIndex: Int,
    val hintText: String?,
    val hasGenerationTemplate: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(indices = [Index("problemId")])
data class AnswerFieldEntity(
    @PrimaryKey val answerFieldId: String,
    val problemId: String,
    val label: String,
    val fieldType: AnswerFieldType,
    val orderIndex: Int,
    val positionJson: String?,
    val required: Boolean
)

@Entity(indices = [Index("problemId"), Index("answerFieldId")])
data class AnswerRuleEntity(
    @PrimaryKey val answerRuleId: String,
    val problemId: String,
    val answerFieldId: String?,
    val answerType: AnswerType,
    val correctAnswerRaw: String,
    val normalizedAnswer: String,
    val allowEquivalentFraction: Boolean,
    val requireSimplifiedFraction: Boolean,
    val decimalTolerance: Double?,
    val allowMultipleAnswers: Boolean,
    val acceptedAnswersJson: String?,
    val unitType: UnitType?,
    val manualGradingRequired: Boolean
)

@Entity(indices = [Index("problemId")])
data class ChoiceEntity(
    @PrimaryKey val choiceId: String,
    val problemId: String,
    val choiceText: String,
    val choiceValue: String,
    val isCorrect: Boolean,
    val orderIndex: Int
)

@Entity(indices = [Index("problemId")])
data class ProblemTemplateEntity(
    @PrimaryKey val templateId: String,
    val problemId: String,
    val templateText: String,
    val templateLatex: String?,
    val variableRulesJson: String,
    val answerFormulaJson: String,
    val validationRulesJson: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(indices = [Index("sourceProblemId"), Index("studentId")])
data class GeneratedProblemEntity(
    @PrimaryKey val generatedProblemId: String,
    val sourceProblemId: String,
    val studentId: String,
    val generatedQuestionText: String,
    val generatedQuestionLatex: String?,
    val generatedAnswerRawJson: String,
    val generatedVariablesJson: String,
    val reason: String,
    val difficultyLevel: Int?,
    val createdAt: Long
)

@Entity
data class StudentEntity(
    @PrimaryKey val studentId: String,
    val name: String
)

@Entity(indices = [Index("studentId"), Index("problemId"), Index("generatedProblemId")])
data class PracticeAttemptEntity(
    @PrimaryKey val attemptId: String,
    val studentId: String,
    val problemId: String,
    val generatedProblemId: String?,
    val workbookId: String,
    val chapterId: String,
    val attemptNumber: Int,
    val finalStatus: FinalStatus,
    val isCorrect: Boolean?,
    val inputTryCount: Int,
    val maxInputTryCount: Int,
    val maxAttemptsReached: Boolean,
    val movedToNextByLimit: Boolean,
    val hintUsed: Boolean,
    val startedAt: Long,
    val submittedAt: Long?,
    val elapsedSeconds: Long,
    val solutionImagePath: String?,
    val reviewerComment: String?,
    val manualReviewStatus: ManualReviewStatus?
)

@Entity(indices = [Index("attemptId"), Index("answerFieldId")])
data class AttemptInputLogEntity(
    @PrimaryKey val inputLogId: String,
    val attemptId: String,
    val answerFieldId: String?,
    val tryNumber: Int,
    val submittedAnswerRaw: String,
    val normalizedSubmittedAnswer: String,
    val isCorrect: Boolean,
    val submittedAt: Long
)

@Entity(indices = [Index("workbookId")])
data class ExamEntity(
    @PrimaryKey val examId: String,
    val title: String,
    val workbookId: String,
    val chapterIdsJson: String,
    val problemIdsJson: String,
    val timeLimitSeconds: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(indices = [Index("examId"), Index("studentId")])
data class ExamSessionEntity(
    @PrimaryKey val examSessionId: String,
    val examId: String,
    val studentId: String,
    val startedAt: Long,
    val submittedAt: Long?,
    val status: ExamSessionStatus,
    val totalProblemCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val score: Double,
    val reviewCompleted: Boolean
)

@Entity(indices = [Index("examSessionId"), Index("problemId"), Index("answerFieldId")])
data class ExamAnswerEntity(
    @PrimaryKey val examAnswerId: String,
    val examSessionId: String,
    val problemId: String,
    val answerFieldId: String?,
    val submittedAnswerRaw: String?,
    val normalizedSubmittedAnswer: String?,
    val isCorrect: Boolean?,
    val gradingStatus: GradingStatus,
    val lastEditedAt: Long,
    val solutionImagePath: String?
)

@Entity(indices = [Index("examSessionId"), Index("problemId")])
data class ExamNavigationLogEntity(
    @PrimaryKey val logId: String,
    val examSessionId: String,
    val problemId: String,
    val enteredAt: Long,
    val leftAt: Long?
)

@Entity(indices = [Index("attemptId"), Index("examSessionId"), Index("problemId")])
data class ReviewEntity(
    @PrimaryKey val reviewId: String,
    val attemptId: String?,
    val examSessionId: String?,
    val problemId: String,
    val reviewerId: String,
    val processScore: ManualReviewStatus,
    val comment: String,
    val reviewedAt: Long
)

@Entity
data class AppSettingsEntity(
    @PrimaryKey val settingId: String = "default",
    val defaultMaxInputTryCount: Int = 3,
    val enableSimilarProblemAfterWrong: Boolean = true,
    val enableSimilarProblemAfterMaxAttempts: Boolean = true,
    val requireMasterPin: Boolean = false,
    val autoMoveNextAfterMaxAttempts: Boolean = true,
    val autoMoveDelayMillis: Long = 1_000L,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity
data class WorkbookSettingsEntity(
    @PrimaryKey val workbookId: String,
    val maxInputTryCount: Int?,
    val enableSimilarProblemAfterWrong: Boolean?,
    val enableSimilarProblemAfterMaxAttempts: Boolean?
)

@Entity
data class ProblemSettingsEntity(
    @PrimaryKey val problemId: String,
    val maxInputTryCount: Int?
)
