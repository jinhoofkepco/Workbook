package com.mathworkbook.app.core.generation

import kotlin.math.roundToLong

class SafeFormulaEvaluator {
    fun evaluate(expression: String, variables: Map<String, Double>): Double {
        return Parser(expression, variables).parse()
    }

    fun evaluateCondition(expression: String, variables: Map<String, Double>): Boolean {
        val operators = listOf("<=", ">=", "==", "!=", "<", ">")
        val operator = operators.firstOrNull { expression.contains(it) } ?: return evaluate(expression, variables) != 0.0
        val parts = expression.split(operator, limit = 2)
        if (parts.size != 2) return false
        val left = evaluate(parts[0], variables)
        val right = evaluate(parts[1], variables)
        return when (operator) {
            "<=" -> left <= right
            ">=" -> left >= right
            "==" -> left == right
            "!=" -> left != right
            "<" -> left < right
            ">" -> left > right
            else -> false
        }
    }

    private class Parser(
        private val input: String,
        private val variables: Map<String, Double>
    ) {
        private var index = 0

        fun parse(): Double {
            val value = parseExpression()
            skipWhitespace()
            require(index == input.length) { "Unexpected token at $index in $input" }
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                value = when {
                    match('+') -> value + parseTerm()
                    match('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                value = when {
                    match('*') -> value * parseFactor()
                    match('/') -> value / parseFactor()
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()
            if (match('(')) {
                val value = parseExpression()
                require(match(')')) { "Missing closing parenthesis in $input" }
                return value
            }
            if (peek()?.isDigit() == true) return parseNumber()
            if (peek()?.isLetter() == true) return parseIdentifier()
            error("Unexpected token at $index in $input")
        }

        private fun parseNumber(): Double {
            val start = index
            while (peek()?.isDigit() == true || peek() == '.') index++
            return input.substring(start, index).toDouble()
        }

        private fun parseIdentifier(): Double {
            val name = parseName()
            skipWhitespace()
            if (!match('(')) {
                return variables[name] ?: error("Unknown variable $name")
            }
            val args = mutableListOf<Double>()
            skipWhitespace()
            if (!match(')')) {
                do {
                    args += parseExpression()
                    skipWhitespace()
                } while (match(','))
                require(match(')')) { "Missing function closing parenthesis for $name" }
            }
            return callFunction(name, args)
        }

        private fun callFunction(name: String, args: List<Double>): Double {
            return when (name) {
                "min" -> args.minOrNull() ?: error("min needs arguments")
                "max" -> args.maxOrNull() ?: error("max needs arguments")
                "gcd" -> gcd(args.getOrNull(0), args.getOrNull(1)).toDouble()
                "largestNumber" -> args
                    .map { it.roundToLong().toString() }
                    .sortedDescending()
                    .joinToString("")
                    .toDouble()
                "smallestNumber" -> {
                    val digits = args.map { it.roundToLong().toString() }.sorted().toMutableList()
                    val firstNonZero = digits.indexOfFirst { it != "0" }
                    if (firstNonZero > 0) {
                        val first = digits.removeAt(firstNonZero)
                        digits.add(0, first)
                    }
                    digits.joinToString("").toDouble()
                }
                else -> error("Function $name is not allowed")
            }
        }

        private fun parseName(): String {
            val start = index
            while (peek()?.isLetterOrDigit() == true || peek() == '_') index++
            return input.substring(start, index)
        }

        private fun gcd(a: Double?, b: Double?): Long {
            var x = kotlin.math.abs((a ?: 0.0).roundToLong())
            var y = kotlin.math.abs((b ?: 0.0).roundToLong())
            while (y != 0L) {
                val next = x % y
                x = y
                y = next
            }
            return if (x == 0L) 1L else x
        }

        private fun match(char: Char): Boolean {
            skipWhitespace()
            if (peek() != char) return false
            index += 1
            return true
        }

        private fun peek(): Char? = input.getOrNull(index)

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) index++
        }
    }
}
