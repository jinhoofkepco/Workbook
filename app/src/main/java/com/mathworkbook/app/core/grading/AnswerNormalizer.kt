package com.mathworkbook.app.core.grading

import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.domain.AnswerType
import java.math.BigDecimal

data class NormalizedAnswer(
    val canonical: String,
    val number: BigDecimal?,
    val fraction: Fraction?
)

class AnswerNormalizer {
    fun normalize(raw: String, rule: AnswerRuleEntity): NormalizedAnswer {
        val cleaned = raw
            .trim()
            .replace(",", "")
            .replace("−", "-")
            .let { removeUnitSuffixes(it, rule.answerType) }
            .trim()

        val fraction = Fraction.parse(cleaned)
        val number = cleaned.toBigDecimalOrNull()
        val canonical = when {
            fraction != null -> fraction.simplified.toString()
            number != null -> number.stripTrailingZeros().toPlainString()
            else -> cleaned.lowercase()
        }
        return NormalizedAnswer(canonical = canonical, number = number, fraction = fraction)
    }

    private fun removeUnitSuffixes(value: String, answerType: AnswerType): String {
        return when (answerType) {
            AnswerType.ANGLE -> value.removeSuffix("도").removeSuffix("°")
            AnswerType.MONEY -> value.removeSuffix("원")
            AnswerType.PERCENT -> value.removeSuffix("%")
            else -> value
        }
    }
}
