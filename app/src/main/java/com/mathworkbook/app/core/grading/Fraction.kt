package com.mathworkbook.app.core.grading

import kotlin.math.abs

data class Fraction(val numerator: Long, val denominator: Long) {
    init {
        require(denominator != 0L) { "denominator must not be zero" }
    }

    val simplified: Fraction by lazy {
        val sign = if (denominator < 0) -1 else 1
        val gcd = gcd(abs(numerator), abs(denominator))
        Fraction(sign * numerator / gcd, abs(denominator) / gcd)
    }

    val isSimplified: Boolean
        get() = this == simplified

    fun equivalentTo(other: Fraction): Boolean {
        return numerator * other.denominator == other.numerator * denominator
    }

    override fun toString(): String {
        val reduced = simplified
        return "${reduced.numerator}/${reduced.denominator}"
    }

    companion object {
        fun parse(raw: String): Fraction? {
            val cleaned = raw
                .trim()
                .replace("−", "-")
            if (cleaned.isBlank()) return null

            parseMixed(cleaned)?.let { return it }
            return parseSimple(cleaned)
        }

        fun gcd(a: Long, b: Long): Long {
            var x = abs(a)
            var y = abs(b)
            while (y != 0L) {
                val next = x % y
                x = y
                y = next
            }
            return if (x == 0L) 1L else x
        }

        private fun parseMixed(value: String): Fraction? {
            val match = MixedFractionRegex.matchEntire(value) ?: return null
            val whole = match.groupValues[1].toLongOrNull() ?: return null
            val numerator = match.groupValues[2].toLongOrNull() ?: return null
            val denominator = match.groupValues[3].toLongOrNull() ?: return null
            if (denominator == 0L) return null

            val sign = if (whole < 0) -1L else 1L
            val absoluteNumerator = abs(whole) * abs(denominator) + abs(numerator)
            return Fraction(sign * absoluteNumerator, abs(denominator))
        }

        private fun parseSimple(value: String): Fraction? {
            val match = SimpleFractionRegex.matchEntire(value) ?: return null
            val numerator = match.groupValues[1].toLongOrNull() ?: return null
            val denominator = match.groupValues[2].toLongOrNull() ?: return null
            if (denominator == 0L) return null
            return Fraction(numerator, denominator)
        }

        private val MixedFractionRegex = Regex("""([+-]?\d+)\s+(\d+)\s*/\s*(\d+)""")
        private val SimpleFractionRegex = Regex("""([+-]?\d+)\s*/\s*([+-]?\d+)""")
    }
}
