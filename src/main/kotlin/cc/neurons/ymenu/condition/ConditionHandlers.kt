package cc.neurons.ymenu.condition

import cc.neurons.ymenu.menu.MenuSession
import cc.neurons.ymenu.model.ComparisonOperator
import cc.neurons.ymenu.model.ConditionSource
import cc.neurons.ymenu.model.ConditionSpec
import cc.neurons.ymenu.render.ItemResolver
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class ConditionHandlers(
    private val itemResolver: ItemResolver,
) {
    private val cooldowns = ConcurrentHashMap<String, Long>()
    private val limits = ConcurrentHashMap<String, MutableList<Long>>()

    fun evaluate(player: Player, session: MenuSession, spec: ConditionSpec): Boolean {
        pruneExpired()
        val result = when (spec.source) {
            ConditionSource.PAPI, ConditionSource.VAR, ConditionSource.STRING, ConditionSource.NUMBER -> false
            ConditionSource.PERMISSION -> player.hasPermission(spec.expression)
            ConditionSource.OP -> player.isOp
            ConditionSource.ITEM -> hasItem(player, spec)
            ConditionSource.SPACE -> hasSpace(player, spec)
            ConditionSource.SLOT -> slotContains(player, spec)
            ConditionSource.COOLDOWN -> checkCooldown(player, session, spec)
            ConditionSource.LIMIT -> checkLimit(player, session, spec)
        }
        return if (spec.negate) !result else result
    }

    fun compare(actual: String, spec: ConditionSpec): Boolean {
        val operator = spec.operator ?: return false
        val expected = spec.expected.orEmpty()
        val actualNumeric = actual.replace(",", "").trim().toDoubleOrNull()
        val expectedNumeric = expected.replace(",", "").trim().toDoubleOrNull()
        if (actualNumeric != null && expectedNumeric != null && spec.source != ConditionSource.STRING) {
            return when (operator) {
                ComparisonOperator.GREATER -> actualNumeric > expectedNumeric
                ComparisonOperator.GREATER_OR_EQUAL -> actualNumeric >= expectedNumeric
                ComparisonOperator.LESS -> actualNumeric < expectedNumeric
                ComparisonOperator.LESS_OR_EQUAL -> actualNumeric <= expectedNumeric
                ComparisonOperator.EQUAL -> actualNumeric == expectedNumeric
                ComparisonOperator.NOT_EQUAL -> actualNumeric != expectedNumeric
                else -> false
            }
        }

        val left = actual.trim()
        val right = expected.trim()
        return when (operator) {
            ComparisonOperator.EQUAL -> left == right
            ComparisonOperator.NOT_EQUAL -> left != right
            ComparisonOperator.GREATER -> left > right
            ComparisonOperator.GREATER_OR_EQUAL -> left >= right
            ComparisonOperator.LESS -> left < right
            ComparisonOperator.LESS_OR_EQUAL -> left <= right
            ComparisonOperator.CONTAINS -> left.contains(right)
            ComparisonOperator.STARTS_WITH -> left.startsWith(right)
            ComparisonOperator.ENDS_WITH -> left.endsWith(right)
            ComparisonOperator.MATCHES -> right.toRegex().matches(left)
        }
    }

    fun clearPlayer(playerId: String) {
        cooldowns.keys.removeIf { it.startsWith("$playerId:") }
        limits.keys.removeIf { it.startsWith("$playerId:") }
    }

    private fun hasItem(player: Player, spec: ConditionSpec): Boolean {
        val material = itemResolver.resolve(spec.expression)
        val amount = spec.expected?.toIntOrNull() ?: 1
        return player.inventory.contents.filterNotNull()
            .filter { it.type == material }
            .sumOf { it.amount } >= amount
    }

    private fun hasSpace(player: Player, spec: ConditionSpec): Boolean {
        val needed = spec.expected?.toIntOrNull() ?: 1
        val free = player.inventory.storageContents.count { it == null || it.type.isAir }
        return free >= needed
    }

    private fun slotContains(player: Player, spec: ConditionSpec): Boolean {
        val slot = spec.arguments.getOrNull(0)?.toIntOrNull() ?: return false
        val itemId = spec.arguments.getOrNull(1) ?: return false
        val material = itemResolver.resolve(itemId)
        return player.inventory.getItem(slot)?.type == material
    }

    private fun checkCooldown(player: Player, session: MenuSession, spec: ConditionSpec): Boolean {
        val key = "${player.uniqueId}:${spec.expression}"
        val durationTicks = spec.expected?.toLongOrNull() ?: return false
        val durationMs = durationTicks * 50L
        val now = System.currentTimeMillis()
        val expiresAt = cooldowns[key]
        if (expiresAt == null) {
            cooldowns[key] = now + durationMs
            return true
        }
        if (now >= expiresAt) {
            cooldowns[key] = now + durationMs
            return true
        }
        return false
    }

    private fun checkLimit(player: Player, session: MenuSession, spec: ConditionSpec): Boolean {
        val key = "${player.uniqueId}:${spec.expression}"
        val maxTimes = spec.expected?.toIntOrNull() ?: return false
        val windowTicks = spec.arguments.getOrNull(0)?.toLongOrNull() ?: return false
        val windowMs = windowTicks * 50L
        val now = System.currentTimeMillis()
        val queue = limits.computeIfAbsent(key) { mutableListOf() }
        synchronized(queue) {
            queue.removeIf { now - it > windowMs }
            return if (queue.size < maxTimes) {
                queue += now
                true
            } else {
                false
            }
        }
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value <= now }
        limits.entries.removeIf { (_, values) ->
            synchronized(values) {
                values.removeIf { now - it > 600_000L }
                values.isEmpty()
            }
        }
    }
}
