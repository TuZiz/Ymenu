package cc.neurons.ymenu.function

import kotlin.math.roundToLong

class KeExpressionEvaluator {
    private val pattern = Regex("\\{ke:\\s*([^{}]+)}", RegexOption.IGNORE_CASE)
    private val mathPattern = Regex("(?i)^math\\s+(add|sub|mul|div)\\s*\\[(.+)]$")

    fun expand(input: String): String {
        var result = input
        repeat(8) {
            var changed = false
            result = pattern.replace(result) { match ->
                val evaluated = evaluate(match.groupValues[1].trim()) ?: return@replace match.value
                changed = true
                evaluated
            }
            if (!changed) {
                return result
            }
        }
        return result
    }

    fun evaluate(expression: String): String? {
        val math = mathPattern.matchEntire(expression) ?: return null
        val operation = math.groupValues[1].lowercase()
        val arguments = math.groupValues[2]
        val tokens = arguments.split(Regex("\\s+")).filter(String::isNotBlank)
        val type = tokens.windowed(2, 1)
            .firstOrNull { it[0].equals("type", ignoreCase = true) }
            ?.getOrNull(1)
            ?.lowercase()
            ?: "double"
        val operands = tokens.filter { it.startsWith("*") }.map { it.removePrefix("*") }
        if (operands.size < 2) {
            return null
        }

        return when (type) {
            "int", "long" -> evaluateIntegral(operation, operands[0], operands[1])
            else -> evaluateDecimal(operation, operands[0], operands[1])
        }
    }

    private fun evaluateIntegral(operation: String, leftRaw: String, rightRaw: String): String? {
        val left = leftRaw.toDoubleOrNull()?.toLong() ?: return null
        val right = rightRaw.toDoubleOrNull()?.toLong() ?: return null
        val result = when (operation) {
            "add" -> left + right
            "sub" -> left - right
            "mul" -> left * right
            "div" -> if (right == 0L) return null else left / right
            else -> return null
        }
        return result.toString()
    }

    private fun evaluateDecimal(operation: String, leftRaw: String, rightRaw: String): String? {
        val left = leftRaw.toDoubleOrNull() ?: return null
        val right = rightRaw.toDoubleOrNull() ?: return null
        val result = when (operation) {
            "add" -> left + right
            "sub" -> left - right
            "mul" -> left * right
            "div" -> if (right == 0.0) return null else left / right
            else -> return null
        }
        return if (result % 1.0 == 0.0) result.roundToLong().toString() else result.toString()
    }
}
