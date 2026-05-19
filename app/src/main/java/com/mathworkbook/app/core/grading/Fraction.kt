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
            val parts = raw.replace(" ", "").split("/")
            if (parts.size != 2) return null
            val numerator = parts[0].toLongOrNull() ?: return null
            val denominator = parts[1].toLongOrNull() ?: return null
            if (denominator == 0L) return null
            return Fraction(numerator, denominator)
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
    }
}
