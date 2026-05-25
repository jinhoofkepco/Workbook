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
import org.json.JSONObject
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
        val gradingPolicy = ProblemGradingPolicy.fromJson(problem.imageCropRectJson)
        if (gradingPolicy.skipAll || problem.problemType == ProblemType.MANUAL_ONLY) {
            return manualReviewResult(fields, submittedAnswers)
        }

        if (rules.any { it.answerFieldId == null && it.requiresManualReview() }) {
            return manualReviewResult(fields, submittedAnswers)
        }

        if (problem.problemType == ProblemType.MULTIPLE_CHOICE) {
            return gradeChoices(fields, choices, submittedAnswers)
        }

        val fieldResults = fields.map { field ->
            val rule = rules.firstOrNull { it.answerFieldId == field.answerFieldId }
                ?: rules.firstOrNull { it.answerFieldId == null }
            val raw = submittedAnswers[field.answerFieldId].orEmpty()
            val requiresManualReview = gradingPolicy.skipFieldIds.contains(field.answerFieldId) ||
                field.requiresManualReview() ||
                rule?.requiresManualReview() == true
            if (requiresManualReview) {
                return@map FieldGradingResult(
                    answerFieldId = field.answerFieldId,
                    submittedRaw = raw,
                    normalizedSubmitted = raw.trim(),
                    isCorrect = false,
                    requiresManualReview = true
                )
            }
            val normalized = rule?.let { normalizer.normalize(raw, it).canonical } ?: raw.trim()
            val correct = if (rule == null || raw.isBlank()) false else isAccepted(raw, rule)
            FieldGradingResult(
                answerFieldId = field.answerFieldId,
                submittedRaw = raw,
                normalizedSubmitted = normalized,
                isCorrect = correct
            )
        }
        val blankCount = fieldResults.count { !it.requiresManualReview && it.submittedRaw.isBlank() }
        val requiresManualReview = fieldResults.any { it.requiresManualReview }
        val autoResults = fieldResults.filterNot { it.requiresManualReview }
        return GradingResult(
            isCorrect = !requiresManualReview && autoResults.isNotEmpty() && autoResults.all { it.isCorrect },
            fieldResults = fieldResults,
            requiresManualReview = requiresManualReview,
            blankCount = blankCount
        )
    }

    private fun manualReviewResult(
        fields: List<AnswerFieldEntity>,
        submittedAnswers: Map<String, String>
    ): GradingResult {
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
            if (rule.normalizedAnswer.isNotBlank() && rule.normalizedAnswer != rule.correctAnswerRaw) {
                add(rule.normalizedAnswer)
            }
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
            AnswerType.TEXT -> compareNumberOrText(submitted, accepted)
            AnswerType.FRACTION -> compareFraction(submitted, accepted, rule)
            AnswerType.DECIMAL -> compareDecimal(submitted, accepted, rule.decimalTolerance)
            AnswerType.INTEGER,
            AnswerType.PERCENT,
            AnswerType.ANGLE,
            AnswerType.MONEY,
            AnswerType.UNIT_VALUE -> compareNumberOrText(submitted, accepted)
            AnswerType.CHOICE -> submitted.canonical == accepted.canonical
            AnswerType.MANUAL,
            AnswerType.MANUAL_REVIEW -> false
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
            val submittedChoices = submitted.canonical.toChoiceSelectionCanonical()
            val acceptedChoices = accepted.canonical.toChoiceSelectionCanonical()
            if (submittedChoices != null && acceptedChoices != null) {
                submittedChoices == acceptedChoices
            } else {
                submitted.canonical == accepted.canonical
            }
        }
    }

    private fun String.toChoiceSelectionCanonical(): String? {
        val selected = mutableListOf<Int>()
        var index = 0
        while (index < length) {
            val char = this[index]
            when {
                char.isWhitespace() || char in ChoiceSeparators -> index += 1
                circledNumberValue(char) != null -> {
                    selected += circledNumberValue(char) ?: 0
                    index += 1
                }
                char.isDigit() -> {
                    val start = index
                    while (index < length && this[index].isDigit()) index += 1
                    selected += substring(start, index).toIntOrNull() ?: return null
                }
                else -> return null
            }
        }
        return selected
            .takeIf { it.isNotEmpty() }
            ?.distinct()
            ?.sorted()
            ?.joinToString(",")
    }

    private fun circledNumberValue(char: Char): Int? {
        return CircledNumbers.indexOf(char.toString()).takeIf { it > 0 }
    }

    private fun AnswerRuleEntity.requiresManualReview(): Boolean {
        return manualGradingRequired ||
            answerType == AnswerType.MANUAL ||
            answerType == AnswerType.MANUAL_REVIEW
    }

    private fun AnswerFieldEntity.requiresManualReview(): Boolean {
        if (positionJson.isNullOrBlank()) return false
        return runCatching {
            val meta = JSONObject(positionJson.orEmpty())
            meta.optBoolean("manualReviewRequired", false) ||
                meta.optBoolean("skipAutoGrading", false) ||
                meta.optBoolean("disabled", false) ||
                meta.optBoolean("readOnly", false)
        }.getOrDefault(false)
    }

    private data class ProblemGradingPolicy(
        val mode: String,
        val skipOnSubmit: Boolean,
        val skipFieldIds: Set<String>
    ) {
        val skipAll: Boolean
            get() = skipOnSubmit || mode == "manual_review"

        companion object {
            fun fromJson(json: String?): ProblemGradingPolicy {
                if (json.isNullOrBlank()) return ProblemGradingPolicy("auto", false, emptySet())
                return runCatching {
                    val root = JSONObject(json)
                    val policy = root.optJSONObject("gradingPolicy") ?: return@runCatching ProblemGradingPolicy("auto", false, emptySet())
                    val skipFields = policy.optJSONArray("skipFieldsOnSubmit")
                    ProblemGradingPolicy(
                        mode = policy.optString("mode", "auto").lowercase(),
                        skipOnSubmit = policy.optBoolean("skipOnSubmit", false),
                        skipFieldIds = if (skipFields == null) {
                            emptySet()
                        } else {
                            List(skipFields.length()) { index -> skipFields.optString(index) }
                                .filter { it.isNotBlank() }
                                .toSet()
                        }
                    )
                }.getOrDefault(ProblemGradingPolicy("auto", false, emptySet()))
            }
        }
    }
}

private val ChoiceSeparators = setOf(',', '/', '·', '.', '，', '、')

private val CircledNumbers = listOf(
    "",
    "①", "②", "③", "④", "⑤",
    "⑥", "⑦", "⑧", "⑨", "⑩",
    "⑪", "⑫", "⑬", "⑭", "⑮",
    "⑯", "⑰", "⑱", "⑲", "⑳"
)
