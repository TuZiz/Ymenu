package cc.neurons.ymenu.condition

import cc.neurons.ymenu.menu.MenuSession
import cc.neurons.ymenu.model.ConditionSource
import cc.neurons.ymenu.model.ConditionSpec
import cc.neurons.ymenu.render.PlaceholderResolver
import org.bukkit.entity.Player

class ConditionEvaluator(
    private val placeholderResolver: PlaceholderResolver,
    private val handlers: ConditionHandlers,
    private val conditionParser: ConditionParser = ConditionParser(),
) {
    private val dynamicSources = setOf(
        ConditionSource.PAPI,
        ConditionSource.VAR,
        ConditionSource.STRING,
        ConditionSource.NUMBER,
    )

    fun evaluate(player: Player, session: MenuSession, raw: String, context: Map<String, String> = emptyMap()): Boolean {
        val normalized = raw.trim()
        if (normalized.startsWith("all [", ignoreCase = true) && normalized.endsWith(']')) {
            val nested = splitCompositeConditions(normalized.substringAfter('[').dropLast(1))
            return nested.all { evaluate(player, session, it, context) }
        }
        if (normalized.startsWith("any [", ignoreCase = true) && normalized.endsWith(']')) {
            val nested = splitCompositeConditions(normalized.substringAfter('[').dropLast(1))
            return nested.any { evaluate(player, session, it, context) }
        }

        val spec = conditionParser.parse(normalized) ?: return false
        return evaluate(player, session, spec, context)
    }

    fun evaluate(player: Player, session: MenuSession, spec: ConditionSpec, context: Map<String, String> = emptyMap()): Boolean {
        val actual = when (spec.source) {
            in dynamicSources ->
                placeholderResolver.resolve(player, spec.expression, session, context)
            else -> spec.expression
        }
        return if (spec.source in dynamicSources) {
            val resolvedSpec = spec.copy(
                expected = spec.expected?.let { placeholderResolver.resolve(player, it, session, context) },
            )
            val result = handlers.compare(actual, resolvedSpec)
            if (spec.negate) !result else result
        } else {
            handlers.evaluate(player, session, spec)
        }
    }

    fun evaluateRaw(actualValue: String, spec: ConditionSpec): Boolean = handlers.compare(actualValue, spec)

    fun clearPlayer(player: Player) {
        handlers.clearPlayer(player.uniqueId.toString())
    }

    private fun splitCompositeConditions(input: String): List<String> {
        val matcher = Regex("(?=(?:check\\s+(?:papi|var|string|number)\\s)|(?:!?has\\s+permission\\s)|(?:is\\s+op)|(?:cooldown\\s)|(?:limit\\s)|(?:all\\s*\\[)|(?:any\\s*\\[))", RegexOption.IGNORE_CASE)
        val indexes = matcher.findAll(input)
            .map { it.range.first }
            .distinct()
            .toList()
        if (indexes.isEmpty()) {
            return listOf(input.trim()).filter(String::isNotBlank)
        }

        val parts = mutableListOf<String>()
        indexes.forEachIndexed { index, start ->
            val end = indexes.getOrNull(index + 1) ?: input.length
            parts += input.substring(start, end).trim()
        }
        return parts.filter(String::isNotBlank)
    }
}
