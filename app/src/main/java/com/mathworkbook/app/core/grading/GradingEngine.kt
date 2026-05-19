package com.mathworkbook.app.core.grading

import com.mathworkbook.app.core.database.AnswerFieldEntity
import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.database.ChoiceEntity
import com.mathworkbook.app.core.database.ProblemEntity
import com.mathworkbook.app.core.domain.AnswerType
import com.mathworkbook.app.core.domain.FieldGradingResult
import com.mathworkbook.app.core.domain.GradingResult
import com.mathworkbook.app.core.domain.ProblemType
import org.json.JSONArray
import java.math.BigDecimal

interface GradingEngine {
    fun grade(
        problem: ProblemEntity,
        fields: List<AnswerFieldEntity>,
        rules: List<AnswerRuleEntity>,
        choices: List<ChoiceEntity>,
        submittedAnswers: Map<String, String>
    ): GradingResult
}

class DefaultGradingEngine(
    private val normalizer: AnswerNormalizer = AnswerNormalizer()
) : GradingEngine {
    override fun grade(
        problem: ProblemEntity,
        fields: List<AnswerFieldEntity>,
        rules: List<AnswerRuleEntity>,
        choices: List<ChoiceEntity>,
        submittedAnswers: Map<String, String>
    ): GradingResult {
        if (rules.any { it.manualGradingRequired || it.answerType == AnswerType.MANUAL }) {
            val manualResults = fields.ifEmpty { listOf(null) }.map { field ->
                val raw = field?.let { submittedAnswers[it.answerFieldId] }.orEmpty()
                FieldGradingResult(
                    answerFieldId = field?.answerFieldId,
                    submittedRaw = raw,
                    normalizedSubmitted = raw.trim(),
                    isCorrect = false,
                    requiresManualReview = true
                )
            }
            return GradingResult(
                isCorrect = false,
                fieldResults = manualResults,
                requiresManualReview = true
            )
        }

        if (problem.problemType == ProblemType.MULTIPLE_CHOICE) {
            return gradeChoices(fields, choices, submittedAnswers)
        }

        val fieldResults = fields.map { field ->
            val rule = rules.firstOrNull { it.answerFieldId == field.answerFieldId }
                ?: rules.firstOrNull { it.answerFieldId == null }
            val raw = submittedAnswers[field.answerFieldId].orEmpty()
            val normalized = rule?.let { normalizer.normalize(raw, it).canonical } ?: raw.trim()
            val correct = if (rule == null || raw.isBlank()) false else isAccepted(raw, rule)
            FieldGradingResult(
                answerFieldId = field.answerFieldId,
                submittedRaw = raw,
                normalizedSubmitted = normalized,
                isCorrect = correct
            )
        }
        val blankCount = fieldResults.count { it.submittedRaw.isBlank() }
        return GradingResult(
            isCorrect = fieldResults.isNotEmpty() && fieldResults.all { it.isCorrect },
            fieldResults = fieldResults,
            blankCount = blankCount
        )
    }

    private fun gradeChoices(
        fields: List<AnswerFieldEntity>,
        choices: List<ChoiceEntity>,
        submittedAnswers: Map<String, String>
    ): GradingResult {
        val fieldId = fields.firstOrNull()?.answerFieldId
        val raw = fieldId?.let { submittedAnswers[it] }.orEmpty()
        val submittedIds = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val correctIds = choices.filter { it.isCorrect }.map { it.choiceId }.toSet()
        val correct = submittedIds == correctIds && submittedIds.isNotEmpty()
        return GradingResult(
            isCorrect = correct,
            fieldResults = listOf(
                FieldGradingResult(
                    answerFieldId = fieldId,
                    submittedRaw = raw,
                    normalizedSubmitted = submittedIds.sorted().joinToString(","),
                    isCorrect = correct
                )
            ),
            blankCount = if (submittedIds.isEmpty()) 1 else 0
        )
    }

    private fun isAccepted(raw: String, rule: AnswerRuleEntity): Boolean {
        val submitted = normalizer.normalize(raw, rule)
        val acceptedRawValues = buildList {
            add(rule.correctAnswerRaw)
            if (rule.allowMultipleAnswers && !rule.acceptedAnswersJson.isNullOrBlank()) {
                addAll(parseAcceptedAnswers(rule.acceptedAnswersJson))
            }
        }
        return acceptedRawValues.any { acceptedRaw ->
            val accepted = normalizer.normalize(acceptedRaw, rule)
            compare(submitted, accepted, rule)
        }
    }

    private fun parseAcceptedAnswers(json: String): List<String> {
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index -> array.getString(index) }
        }.getOrDefault(emptyList())
    }

    private fun compare(
        submitted: NormalizedAnswer,
        accepted: NormalizedAnswer,
        rule: AnswerRuleEntity
    ): Boolean {
        return when (rule.answerType) {
            AnswerType.FRACTION -> compareFraction(submitted, accepted, rule)
            AnswerType.DECIMAL -> compareDecimal(submitted, accepted, rule.decimalTolerance)
            AnswerType.INTEGER,
            AnswerType.PERCENT,
            AnswerType.ANGLE,
            AnswerType.MONEY,
            AnswerType.UNIT_VALUE -> compareNumberOrText(submitted, accepted)
            AnswerType.CHOICE -> submitted.canonical == accepted.canonical
            AnswerType.MANUAL -> false
        }
    }

    private fun compareFraction(
        submitted: NormalizedAnswer,
        accepted: NormalizedAnswer,
        rule: AnswerRuleEntity
    ): Boolean {
        val submittedFraction = submitted.fraction ?: return false
        val acceptedFraction = accepted.fraction ?: return false
        if (rule.requireSimplifiedFraction && !submittedFraction.isSimplified) return false
        return if (rule.allowEquivalentFraction) {
            submittedFraction.equivalentTo(acceptedFraction)
        } else {
            submittedFraction == acceptedFraction
        }
    }

    private fun compareDecimal(
        submitted: NormalizedAnswer,
        accepted: NormalizedAnswer,
        tolerance: Double?
    ): Boolean {
        val submittedNumber = submitted.number ?: return false
        val acceptedNumber = accepted.number ?: return false
        if (tolerance == null) return submittedNumber.compareTo(acceptedNumber) == 0
        val delta = submittedNumber.subtract(acceptedNumber).abs()
        return delta <= BigDecimal.valueOf(tolerance)
    }

    private fun compareNumberOrText(submitted: NormalizedAnswer, accepted: NormalizedAnswer): Boolean {
        val left = submitted.number
        val right = accepted.number
        return if (left != null && right != null) {
            left.compareTo(right) == 0
        } else {
            submitted.canonical == accepted.canonical
        }
    }
}
