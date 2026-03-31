package cc.neurons.ymenu.condition

import cc.neurons.ymenu.model.ComparisonOperator
import cc.neurons.ymenu.model.ConditionSource
import cc.neurons.ymenu.model.ConditionSpec

class ConditionParser {
    private val operators = ComparisonOperator.entries.sortedByDescending { it.token.length }

    fun parse(raw: String): ConditionSpec? {
        val normalized = raw.trim()
        return when {
            normalized.startsWith("check papi ", ignoreCase = true) -> parseComparison(normalized.removePrefixIgnoreCase("check papi "), ConditionSource.PAPI)
            normalized.startsWith("check var ", ignoreCase = true) -> parseComparison(normalized.removePrefixIgnoreCase("check var "), ConditionSource.VAR)
            normalized.startsWith("check string ", ignoreCase = true) -> parseComparison(normalized.removePrefixIgnoreCase("check string "), ConditionSource.STRING)
            normalized.startsWith("check number ", ignoreCase = true) -> parseComparison(normalized.removePrefixIgnoreCase("check number "), ConditionSource.NUMBER)
            normalized.equals("is op", ignoreCase = true) -> ConditionSpec(ConditionSource.OP, "is op")
            normalized.startsWith("perm ", ignoreCase = true) -> ConditionSpec(ConditionSource.PERMISSION, normalized.removePrefixIgnoreCase("perm ").trim().stripWildcardMarkers())
            normalized.startsWith("!perm ", ignoreCase = true) -> ConditionSpec(ConditionSource.PERMISSION, normalized.removePrefixIgnoreCase("!perm ").trim().stripWildcardMarkers(), negate = true)
            normalized.startsWith("has permission ", ignoreCase = true) -> ConditionSpec(ConditionSource.PERMISSION, normalized.removePrefixIgnoreCase("has permission ").trim())
            normalized.startsWith("!has permission ", ignoreCase = true) -> ConditionSpec(ConditionSource.PERMISSION, normalized.removePrefixIgnoreCase("!has permission ").trim(), negate = true)
            normalized.startsWith("has item ", ignoreCase = true) -> parseHasItem(normalized.removePrefixIgnoreCase("has item "))
            normalized.startsWith("has space", ignoreCase = true) -> parseHasSpace(normalized.removePrefixIgnoreCase("has space"))
            normalized.startsWith("slot contains ", ignoreCase = true) -> parseSlotContains(normalized.removePrefixIgnoreCase("slot contains "))
            normalized.startsWith("cooldown ", ignoreCase = true) -> parseCooldown(normalized.removePrefixIgnoreCase("cooldown "))
            normalized.startsWith("limit ", ignoreCase = true) -> parseLimit(normalized.removePrefixIgnoreCase("limit "))
            else -> null
        }
    }

    private fun parseComparison(remainder: String, source: ConditionSource): ConditionSpec? {
        val isOperator = " is "
        val operator = operators.firstOrNull { remainder.contains(" ${it.token} ") }
        val parts = when {
            operator != null -> remainder.split(" ${operator.token} ", limit = 2)
            remainder.contains(isOperator, ignoreCase = true) -> remainder.split(isOperator, limit = 2)
            else -> return null
        }
        if (parts.size != 2) {
            return null
        }
        return ConditionSpec(
            source = source,
            expression = normalizeComparisonOperand(parts[0], source),
            operator = operator ?: ComparisonOperator.EQUAL,
            expected = normalizeComparisonOperand(parts[1], source),
        )
    }

    private fun parseHasItem(value: String): ConditionSpec {
        val parts = value.trim().split(Regex("\\s+"))
        return ConditionSpec(
            source = ConditionSource.ITEM,
            expression = parts.first(),
            expected = parts.getOrNull(1) ?: "1",
        )
    }

    private fun parseHasSpace(value: String): ConditionSpec = ConditionSpec(
        source = ConditionSource.SPACE,
        expression = "space",
        expected = value.trim().ifBlank { "1" },
    )

    private fun parseSlotContains(value: String): ConditionSpec {
        val parts = value.trim().split(Regex("\\s+"))
        return ConditionSpec(
            source = ConditionSource.SLOT,
            expression = "slot contains",
            arguments = listOf(parts.getOrElse(0) { "0" }, parts.getOrElse(1) { "AIR" }),
        )
    }

    private fun parseCooldown(value: String): ConditionSpec {
        val parts = value.trim().split(Regex("\\s+"))
        return ConditionSpec(
            source = ConditionSource.COOLDOWN,
            expression = parts.getOrElse(0) { "default" },
            expected = parts.getOrElse(1) { "0" },
        )
    }

    private fun parseLimit(value: String): ConditionSpec {
        val parts = value.trim().split(Regex("\\s+"))
        return ConditionSpec(
            source = ConditionSource.LIMIT,
            expression = parts.getOrElse(0) { "default" },
            expected = parts.getOrElse(1) { "1" },
            arguments = listOf(parts.getOrElse(2) { "0" }),
        )
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String = if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private fun normalizeComparisonOperand(raw: String, source: ConditionSource): String {
        val trimmed = raw.trim()
        val normalized = when (source) {
            ConditionSource.PAPI -> trimmed.removePrefixIgnoreCase("papi ").trim()
            ConditionSource.VAR -> trimmed.removePrefixIgnoreCase("var ").trim()
            ConditionSource.STRING -> trimmed.removePrefixIgnoreCase("string ").trim()
            ConditionSource.NUMBER -> trimmed.removePrefixIgnoreCase("number ").trim()
            else -> trimmed
        }
        return normalized.stripWildcardMarkers()
    }

    private fun String.stripWildcardMarkers(): String {
        var value = trim()
        while (value.startsWith('*')) {
            value = value.substring(1)
        }
        while (value.endsWith('*')) {
            value = value.dropLast(1)
        }
        return value.trim()
    }
}
