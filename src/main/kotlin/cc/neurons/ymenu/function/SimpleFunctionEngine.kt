package cc.neurons.ymenu.function

import kotlin.math.roundToLong

class SimpleFunctionEngine(
    private val resolveText: (String) -> String,
) {
    fun evaluate(script: String): String? {
        val body = extractBody(script) ?: return null
        val lines = normalizeLines(body)
        val result = executeBlock(lines, 0, linkedMapOf())
        return result.value?.asString()
    }

    private fun extractBody(script: String): String? {
        val start = script.indexOf('{')
        val end = script.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            return null
        }
        return script.substring(start + 1, end)
    }

    private fun normalizeLines(body: String): List<String> {
        val lines = mutableListOf<String>()
        val buffer = StringBuilder()
        var parenthesisDepth = 0
        var bracketDepth = 0
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("//")) {
                continue
            }
            if (buffer.isNotEmpty()) {
                buffer.append(' ')
            }
            buffer.append(line)
            parenthesisDepth += line.count { it == '(' } - line.count { it == ')' }
            bracketDepth += line.count { it == '[' } - line.count { it == ']' }
            if (parenthesisDepth <= 0 && bracketDepth <= 0) {
                lines += buffer.toString()
                buffer.clear()
                parenthesisDepth = 0
                bracketDepth = 0
            }
        }
        if (buffer.isNotEmpty()) {
            lines += buffer.toString()
        }
        return lines
    }

    private fun executeBlock(lines: List<String>, startIndex: Int, env: MutableMap<String, Value>): BlockResult {
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            when {
                line == "}" -> return BlockResult(null, index + 1)
                line.startsWith("if (") || line.startsWith("if(") -> {
                    val result = executeIfChain(lines, index, env)
                    if (result.returned) {
                        return result
                    }
                    index = result.nextIndex
                }
                line.startsWith("for (") || line.startsWith("for(") -> {
                    val result = executeForLoop(lines, index, env)
                    if (result.returned) {
                        return result
                    }
                    index = result.nextIndex
                }
                line.startsWith("else") -> return BlockResult(null, index)
                line.startsWith("return ") -> {
                    val expression = line.removePrefix("return ").trim().removeSuffix(";")
                    return BlockResult(evaluateExpression(expression, env), index + 1, returned = true)
                }
                line.startsWith("var ") -> {
                    assign(line.removePrefix("var ").trim(), env)
                    index++
                }
                line.contains('=') -> {
                    assign(line, env)
                    index++
                }
                else -> index++
            }
        }
        return BlockResult(null, index)
    }

    private fun executeIfChain(lines: List<String>, startIndex: Int, env: MutableMap<String, Value>): BlockResult {
        var index = startIndex
        var matched = false
        while (index < lines.size) {
            val line = lines[index]
            val condition = when {
                line.startsWith("if (") || line.startsWith("if(") -> parseCondition(line, env)
                line.startsWith("else if (") || line.startsWith("else if(") -> parseCondition(line.removePrefix("else ").trimStart(), env)
                line.startsWith("else") -> true
                else -> return BlockResult(null, index)
            }

            val blockStart = index + 1
            if (!matched && condition) {
                val branchResult = executeBlock(lines, blockStart, env)
                if (branchResult.returned) {
                    return branchResult
                }
                matched = true
                index = skipElseChain(lines, branchResult.nextIndex)
                return BlockResult(null, index)
            }

            index = skipBlock(lines, blockStart)
            val next = lines.getOrNull(index)?.trim().orEmpty()
            if (!next.startsWith("else")) {
                return BlockResult(null, index)
            }
        }
        return BlockResult(null, index)
    }

    private val maxIterations = 10_000

    private fun executeForLoop(lines: List<String>, startIndex: Int, env: MutableMap<String, Value>): BlockResult {
        val line = lines[startIndex]
        val header = line.substringAfter('(').substringBeforeLast(')')
        val segments = header.split(';', limit = 3).map(String::trim)
        if (segments.size != 3) {
            return BlockResult(null, startIndex + 1)
        }

        val initializer = segments[0]
        val condition = segments[1]
        val increment = segments[2]
        if (initializer.isNotBlank()) {
            if (initializer.startsWith("var ")) {
                assign(initializer.removePrefix("var ").trim(), env)
            } else {
                applyStep(initializer, env)
            }
        }

        val blockStart = startIndex + 1
        val blockEnd = skipBlock(lines, blockStart)
        var iterations = 0
        while (evaluateCondition(condition, env)) {
            if (++iterations > maxIterations) {
                break
            }
            val result = executeBlock(lines, blockStart, env)
            if (result.returned) {
                return result
            }
            applyStep(increment, env)
        }
        return BlockResult(null, blockEnd)
    }

    private fun skipElseChain(lines: List<String>, startIndex: Int): Int {
        var index = startIndex
        while (index < lines.size && lines[index].trim().startsWith("else")) {
            index = skipBlock(lines, index + 1)
        }
        return index
    }

    private fun skipBlock(lines: List<String>, startIndex: Int): Int {
        var depth = 1
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            depth += line.count { it == '{' }
            depth -= line.count { it == '}' }
            index++
            if (depth <= 0) {
                return index
            }
        }
        return index
    }

    private fun parseCondition(line: String, env: MutableMap<String, Value>): Boolean {
        val raw = line.substringAfter('(').substringBeforeLast(')')
        return evaluateCondition(raw, env)
    }

    private fun assign(statement: String, env: MutableMap<String, Value>) {
        val parts = statement.split('=', limit = 2)
        if (parts.size < 2) {
            return
        }
        val name = parts[0].trim()
        val expression = parts[1].trim().removeSuffix(";")
        env[name] = evaluateExpression(expression, env)
    }

    private fun applyStep(statement: String, env: MutableMap<String, Value>) {
        val normalized = statement.trim().removeSuffix(";")
        when {
            normalized.endsWith("++") -> {
                val name = normalized.removeSuffix("++").trim()
                val current = env[name]?.asNumber() ?: 0.0
                env[name] = Value.NumberValue(current + 1)
            }
            normalized.endsWith("--") -> {
                val name = normalized.removeSuffix("--").trim()
                val current = env[name]?.asNumber() ?: 0.0
                env[name] = Value.NumberValue(current - 1)
            }
            normalized.isNotBlank() -> assign(normalized, env)
        }
    }

    private fun evaluateCondition(raw: String, env: MutableMap<String, Value>): Boolean {
        val match = CONDITION_PATTERN.matchEntire(raw.trim()) ?: return false
        val operator = match.groupValues[2]
        val leftExpression = match.groupValues[1].trim()
        val rightExpression = match.groupValues[3].trim()
        if (leftExpression.isEmpty() || rightExpression.isEmpty()) {
            return false
        }
        val left = evaluateExpression(leftExpression, env)
        val right = evaluateExpression(rightExpression, env)
        val leftNumber = left.asNumber()
        val rightNumber = right.asNumber()
        return if (leftNumber != null && rightNumber != null) {
            when (operator) {
                "==" -> leftNumber == rightNumber
                "!=" -> leftNumber != rightNumber
                ">=" -> leftNumber >= rightNumber
                "<=" -> leftNumber <= rightNumber
                ">" -> leftNumber > rightNumber
                "<" -> leftNumber < rightNumber
                else -> false
            }
        } else {
            val leftText = left.asString()
            val rightText = right.asString()
            when (operator) {
                "==" -> leftText == rightText
                "!=" -> leftText != rightText
                ">=" -> leftText.compareTo(rightText) >= 0
                "<=" -> leftText.compareTo(rightText) <= 0
                ">" -> leftText.compareTo(rightText) > 0
                "<" -> leftText.compareTo(rightText) < 0
                else -> false
            }
        }
    }

    private fun evaluateExpression(raw: String, env: MutableMap<String, Value>): Value {
        val parser = ExpressionParser(tokenize(raw), env)
        return parser.parseExpression()
    }

    private fun tokenize(raw: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < raw.length) {
            when (val ch = raw[index]) {
                ' ', '\t' -> index++
                '+', '-', '*', '/', '(', ')', ',', '.', '[', ']' -> {
                    tokens += Token.Symbol(ch.toString())
                    index++
                }
                '"' -> {
                    val start = index + 1
                    val end = raw.indexOf('"', start).takeIf { it >= 0 } ?: raw.length
                    tokens += Token.StringLiteral(raw.substring(start, end))
                    index = (end + 1).coerceAtMost(raw.length)
                }
                else -> {
                    if (ch.isDigit()) {
                        val start = index
                        while (index < raw.length && (raw[index].isDigit() || raw[index] == '.')) {
                            index++
                        }
                        tokens += Token.NumberLiteral(raw.substring(start, index))
                    } else {
                        val start = index
                        while (index < raw.length && (raw[index].isLetterOrDigit() || raw[index] == '_' )) {
                            index++
                        }
                        tokens += Token.Identifier(raw.substring(start, index))
                    }
                }
            }
        }
        return tokens
    }

    private inner class ExpressionParser(
        private val tokens: List<Token>,
        private val env: MutableMap<String, Value>,
    ) {
        private var index = 0

        fun parseExpression(): Value = parseAddition()

        private fun parseAddition(): Value {
            var value = parseMultiplication()
            while (matchSymbol("+") || matchSymbol("-")) {
                val operator = (tokens[index - 1] as Token.Symbol).value
                val right = parseMultiplication()
                val leftNumber = value.asNumber() ?: 0.0
                val rightNumber = right.asNumber() ?: 0.0
                value = Value.NumberValue(
                    if (operator == "+") leftNumber + rightNumber else leftNumber - rightNumber
                )
            }
            return value
        }

        private fun parseMultiplication(): Value {
            var value = parseUnary()
            while (matchSymbol("*") || matchSymbol("/")) {
                val operator = (tokens[index - 1] as Token.Symbol).value
                val right = parseUnary()
                val leftNumber = value.asNumber() ?: 0.0
                val rightNumber = right.asNumber() ?: 0.0
                value = Value.NumberValue(
                    if (operator == "*") leftNumber * rightNumber else leftNumber / rightNumber
                )
            }
            return value
        }

        private fun parseUnary(): Value {
            return when {
                matchSymbol("-") -> {
                    val value = parseUnary()
                    Value.NumberValue(-(value.asNumber() ?: 0.0))
                }
                matchSymbol("+") -> parseUnary()
                else -> parsePostfix()
            }
        }

        private fun parsePostfix(): Value {
            var value = parsePrimary()
            while (true) {
                when {
                    matchSymbol("[") -> {
                        val indexValue = parseExpression().asNumber()?.toInt() ?: -1
                        expectSymbol("]")
                        value = value.asArray()?.getOrNull(indexValue) ?: Value.Null
                    }
                    peekProperty("length") -> {
                        matchSymbol(".")
                        matchIdentifier("length")
                        value = Value.NumberValue(
                            value.asArray()?.size?.toDouble()
                                ?: value.asString().length.toDouble()
                        )
                    }
                    peekMethodCall("toFixed") -> {
                        matchSymbol(".")
                        matchIdentifier("toFixed")
                        expectSymbol("(")
                        val digits = parseExpression().asNumber()?.toInt() ?: 0
                        expectSymbol(")")
                        value = Value.TextValue(value.toFixed(digits))
                    }
                    else -> return value
                }
            }
        }

        private fun parsePrimary(): Value {
            val token = tokens.getOrNull(index++) ?: return Value.Null
            return when (token) {
                is Token.NumberLiteral -> Value.NumberValue(token.value.toDoubleOrNull() ?: 0.0)
                is Token.StringLiteral -> Value.TextValue(token.value)
                is Token.Identifier -> parseIdentifier(token.value)
                is Token.Symbol -> {
                    if (token.value == "(") {
                        val value = parseExpression()
                        expectSymbol(")")
                        value
                    } else {
                        Value.Null
                    }
                }
            }
        }

        private fun parseIdentifier(name: String): Value {
            if (name == "new" && matchIdentifier("Array")) {
                expectSymbol("(")
                val values = mutableListOf<Value>()
                if (!matchSymbol(")")) {
                    do {
                        values += parseExpression()
                    } while (matchSymbol(","))
                    expectSymbol(")")
                }
                return Value.ArrayValue(values)
            }
            if (matchSymbol("(")) {
                val argument = parseExpression()
                expectSymbol(")")
                return when (name) {
                    "vars" -> Value.TextValue(resolveText(argument.asString()))
                    "varInt" -> Value.NumberValue(resolveText(argument.asString()).replace(",", "").trim().toDoubleOrNull() ?: 0.0)
                    else -> Value.Null
                }
            }
            if (name.equals("null", ignoreCase = true)) {
                return Value.Null
            }
            if (name.equals("true", ignoreCase = true) || name.equals("false", ignoreCase = true)) {
                return Value.TextValue(name.lowercase())
            }
            return env[name] ?: Value.TextValue(name)
        }

        private fun peekMethodCall(method: String): Boolean {
            val dot = tokens.getOrNull(index) as? Token.Symbol ?: return false
            if (dot.value != ".") {
                return false
            }
            val token = tokens.getOrNull(index + 1) as? Token.Identifier ?: return false
            if (token.value != method) {
                return false
            }
            return true
        }

        private fun peekProperty(name: String): Boolean {
            val dot = tokens.getOrNull(index) as? Token.Symbol ?: return false
            if (dot.value != ".") {
                return false
            }
            val token = tokens.getOrNull(index + 1) as? Token.Identifier ?: return false
            return token.value == name
        }

        private fun matchSymbol(symbol: String): Boolean {
            val token = tokens.getOrNull(index) as? Token.Symbol ?: return false
            if (token.value != symbol) {
                return false
            }
            index++
            return true
        }

        private fun matchIdentifier(identifier: String): Boolean {
            val token = tokens.getOrNull(index) as? Token.Identifier ?: return false
            if (token.value != identifier) {
                return false
            }
            index++
            return true
        }

        private fun expectSymbol(symbol: String) {
            matchSymbol(symbol)
        }
    }

    private sealed interface Token {
        data class Identifier(val value: String) : Token
        data class NumberLiteral(val value: String) : Token
        data class StringLiteral(val value: String) : Token
        data class Symbol(val value: String) : Token
    }

    private sealed interface Value {
        fun asString(): kotlin.String
        fun asNumber(): Double?
        fun asArray(): List<Value>?

        data class TextValue(val value: kotlin.String) : Value {
            override fun asString(): kotlin.String = value
            override fun asNumber(): Double? = value.toDoubleOrNull()
            override fun asArray(): List<Value>? = null
        }

        data class NumberValue(val value: Double) : Value {
            override fun asString(): kotlin.String = if (value % 1.0 == 0.0) value.roundToLong().toString() else value.toString()
            override fun asNumber(): Double = value
            override fun asArray(): List<Value>? = null
        }

        data class ArrayValue(val values: List<Value>) : Value {
            override fun asString(): kotlin.String = values.joinToString(",") { it.asString() }
            override fun asNumber(): Double? = null
            override fun asArray(): List<Value> = values
        }

        data object Null : Value {
            override fun asString(): kotlin.String = "null"
            override fun asNumber(): Double? = null
            override fun asArray(): List<Value>? = null
        }
    }

    private data class BlockResult(
        val value: Value?,
        val nextIndex: Int,
        val returned: Boolean = false,
    )

    private companion object {
        val CONDITION_PATTERN = Regex("""(.+?)(==|!=|>=|<=|>|<)(.+)""")
    }

    private fun Value.toFixed(digits: Int): String {
        val number = asNumber() ?: return asString()
        return "%.${digits}f".format(number)
    }
}
