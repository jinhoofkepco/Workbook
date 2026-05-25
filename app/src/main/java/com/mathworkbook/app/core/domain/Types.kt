package com.mathworkbook.app.core.domain

enum class ProblemType {
    MULTIPLE_CHOICE,
    SHORT_NUMBER,
    MULTI_FIELD,
    IMAGE_BASED,
    LATEX,
    MANUAL_ONLY
}

enum class AnswerFieldType {
    TEXT,
    NUMBER,
    FRACTION,
    CHOICE,
    MONEY,
    ANGLE,
    DRAWING,
    TABLE,
    TEXTAREA
}

enum class AnswerType {
    TEXT,
    INTEGER,
    DECIMAL,
    FRACTION,
    PERCENT,
    ANGLE,
    MONEY,
    UNIT_VALUE,
    CHOICE,
    MANUAL,
    MANUAL_REVIEW
}

enum class UnitType {
    NONE,
    WON,
    DEGREE,
    PERCENT,
    CUSTOM
}

enum class FinalStatus {
    IN_PROGRESS,
    CORRECT,
    WRONG,
    FAILED_AFTER_MAX_ATTEMPTS,
    MANUAL_REVIEW_REQUIRED
}

enum class GradingStatus {
    DRAFT,
    GRADED,
    MANUAL_REQUIRED,
    BLANK
}

enum class ManualReviewStatus {
    NONE,
    GOOD,
    NORMAL,
    NEEDS_EXPLANATION
}

enum class ExamSessionStatus {
    IN_PROGRESS,
    REVIEWING,
    SUBMITTED,
    GRADED
}

enum class KeyboardType {
    INTEGER,
    DECIMAL,
    FRACTION,
    ANGLE,
    MONEY,
    MULTIPLE_CHOICE,
    MULTI_FIELD
}

data class RelativeRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

data class MaskOverlay(
    val items: List<MaskItem>
)

data class MaskItem(
    val id: String,
    val rect: RelativeRect,
    val color: String = "#FFFFFF"
)

data class FieldGradingResult(
    val answerFieldId: String?,
    val submittedRaw: String,
    val normalizedSubmitted: String,
    val isCorrect: Boolean,
    val requiresManualReview: Boolean = false
)

data class GradingResult(
    val isCorrect: Boolean,
    val fieldResults: List<FieldGradingResult>,
    val requiresManualReview: Boolean = false,
    val blankCount: Int = 0
)
