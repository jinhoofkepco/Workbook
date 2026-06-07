package com.mathworkbook.app.core.grading

import com.mathworkbook.app.core.database.AnswerRuleEntity
import com.mathworkbook.app.core.domain.AnswerType
import java.math.BigDecimal
import java.math.MathContext

data class NormalizedAnswer(
    val canonical: String,
    val number: BigDecimal?,
    val fraction: Fraction?
)

class AnswerNormalizer {
    fun normalize(raw: String, rule: AnswerRuleEntity): NormalizedAnswer {
        val unitStripped = raw
            .trim()
            .replace(",", "")
            .replace("−", "-")
            .let { removeUnitSuffixes(it, rule.answerType) }
            .trim()
        val fractionCandidate = unitStripped.collapseWhitespace()
        val cleaned = normalizeWhitespace(unitStripped, rule.answerType)

        val parsedFraction = Fraction.parse(fractionCandidate) ?: Fraction.parse(cleaned)
        val parsedNumber = cleaned.toBigDecimalOrNull()
        val fraction = parsedFraction ?: if (rule.answerType == AnswerType.FRACTION) {
            parsedNumber?.toFractionOrNull()
        } else {
            null
        }
        val number = parsedNumber ?: parsedFraction?.toBigDecimalOrNull()
        val canonical = when {
            fraction != null -> fraction.simplified.toString()
            number != null -> number.stripTrailingZeros().toPlainString()
            else -> cleaned.lowercase()
        }
        return NormalizedAnswer(canonical = canonical, number = number, fraction = fraction)
    }

    private fun removeUnitSuffixes(value: String, answerType: AnswerType): String {
        val strippedByType = when (answerType) {
            AnswerType.ANGLE -> value.removeSuffix("도").removeSuffix("°")
            AnswerType.MONEY -> value.removeSuffix("원")
            AnswerType.PERCENT -> value.removeSuffix("%")
            else -> value
        }
        val numericTypes = setOf(
            AnswerType.INTEGER,
            AnswerType.DECIMAL,
            AnswerType.PERCENT,
            AnswerType.ANGLE,
            AnswerType.MONEY,
            AnswerType.UNIT_VALUE
        )
        return if (answerType in numericTypes && strippedByType.toBigDecimalOrNull() == null) {
            Regex("""^\s*([+-]?\d+(?:\.\d+)?)\s*[%\p{L}가-힣]+\s*$""")
                .replace(strippedByType, "$1")
        } else {
            strippedByType
        }
    }

    private fun normalizeWhitespace(value: String, answerType: AnswerType): String {
        return when {
            answerType == AnswerType.FRACTION -> value.collapseWhitespace()
            answerType.isWhitespaceInsensitiveNumber() -> value.removeWhitespace()
            else -> value.collapseWhitespace()
        }
    }

    private fun AnswerType.isWhitespaceInsensitiveNumber(): Boolean {
        return when (this) {
            AnswerType.INTEGER,
            AnswerType.DECIMAL,
            AnswerType.PERCENT,
            AnswerType.ANGLE,
            AnswerType.MONEY,
            AnswerType.UNIT_VALUE -> true
            else -> false
        }
    }

    private fun String.collapseWhitespace(): String {
        return replace(Regex("""\s+"""), " ")
    }

    private fun String.removeWhitespace(): String {
        return replace(Regex("""\s+"""), "")
    }

    private fun Fraction.toBigDecimalOrNull(): BigDecimal? {
        return runCatching {
            BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL128)
        }.getOrNull()
    }

    private fun BigDecimal.toFractionOrNull(): Fraction? {
        return runCatching {
            val stripped = stripTrailingZeros()
            val scale = stripped.scale()
            if (scale <= 0) {
                Fraction(stripped.longValueExact(), 1)
            } else {
                val denominator = 10L.pow(scale)
                val numerator = stripped.movePointRight(scale).longValueExact()
                Fraction(numerator, denominator)
            }
        }.getOrNull()
    }

    private fun Long.pow(exponent: Int): Long {
        var result = 1L
        repeat(exponent) {
            result = Math.multiplyExact(result, this)
        }
        return result
    }
}
