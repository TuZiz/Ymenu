package cc.neurons.ymenu.render

import cc.neurons.ymenu.data.PlayerDataStore
import cc.neurons.ymenu.function.KeExpressionEvaluator
import cc.neurons.ymenu.function.SimpleFunctionEngine
import cc.neurons.ymenu.menu.MenuSession
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class PlaceholderResolver(
    private val plugin: Plugin,
    private val playerDataStore: PlayerDataStore,
) {
    private val functionPattern = Regex("\\$\\{([^}]+)}")
    private val dataPattern = Regex("%trmenu_data_([^%]+)%")
    private val braceDataPattern = Regex("\\{trmenu_data_([^}]+)}")
    private val inputPattern = Regex("\\{input}")
    private val metaPattern = Regex("\\{meta:([^}]+)}")
    private val trMenuMetaPattern = Regex("%trmenu_meta_([^%]+)%")
    private val variablePattern = Regex("%ymenu_var_([^%]+)%")
    private val contextPattern = Regex("@([^@]+)@")
    private val keExpressionEvaluator = KeExpressionEvaluator()
    private val papiMethod: java.lang.reflect.Method? = runCatching {
        val clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
        clazz.getMethod("setPlaceholders", org.bukkit.OfflinePlayer::class.java, String::class.java)
    }.getOrNull()

    fun resolve(
        player: Player?,
        input: String?,
        session: MenuSession? = null,
        context: Map<String, String> = emptyMap(),
    ): String {
        if (input == null) {
            return ""
        }

        val internal = resolveInternal(player, input, session, context, mutableSetOf(), applyPapi = true)
        return internal
    }

    fun resolve(
        player: Player?,
        lines: List<String>,
        session: MenuSession? = null,
        context: Map<String, String> = emptyMap(),
    ): List<String> = lines.map { resolve(player, it, session, context) }

    private fun resolveInternal(
        player: Player?,
        input: String,
        session: MenuSession?,
        context: Map<String, String>,
        stack: MutableSet<String>,
        applyPapi: Boolean,
    ): String {
        var result = applyInternalPlaceholders(player, input, session, context)
        result = expandFunctions(player, result, session, context, stack)
        val method = papiMethod
        if (!applyPapi || player == null || method == null) {
            return keExpressionEvaluator.expand(result)
        }

        val withPapi = runCatching {
            method.invoke(null, player, result) as? String ?: result
        }.getOrElse {
            result
        }
        return keExpressionEvaluator.expand(withPapi)
    }

    private fun applyInternalPlaceholders(
        player: Player?,
        input: String,
        session: MenuSession?,
        context: Map<String, String>,
    ): String {
        var result = input
        result = contextPattern.replace(result) { match ->
            context[match.groupValues[1]] ?: match.value
        }
        result = result
            .replace("%ymenu_page%", session?.currentPage?.toString() ?: "1")
            .replace("%ymenu_max_page%", session?.maxPage?.toString() ?: "1")
            .replace("%ymenu_has_next_page%", ((session?.currentPage ?: 1) < (session?.maxPage ?: 1)).toString())
            .replace("%ymenu_has_prev_page%", ((session?.currentPage ?: 1) > 1).toString())

        result = variablePattern.replace(result) { match ->
            session?.variables?.values?.get(match.groupValues[1]) ?: ""
        }
        result = inputPattern.replace(result, context["input"] ?: session?.metadata?.get("input") ?: "")
        result = trMenuMetaPattern.replace(result) { match ->
            session?.metadata?.get(match.groupValues[1]) ?: "null"
        }
        result = metaPattern.replace(result) { match ->
            session?.metadata?.get(match.groupValues[1]) ?: "null"
        }
        if (player != null) {
            result = dataPattern.replace(result) { match ->
                playerDataStore.get(player.uniqueId, match.groupValues[1]) ?: "0"
            }
            result = braceDataPattern.replace(result) { match ->
                playerDataStore.get(player.uniqueId, match.groupValues[1]) ?: "0"
            }
        }
        return result
    }

    private fun expandFunctions(
        player: Player?,
        input: String,
        session: MenuSession?,
        context: Map<String, String>,
        stack: MutableSet<String>,
    ): String {
        if (session == null || session.menu.functions.isEmpty() || !functionPattern.containsMatchIn(input)) {
            return input
        }
        return functionPattern.replace(input) { match ->
            val name = match.groupValues[1]
            val script = session.menu.functions[name] ?: return@replace match.value
            if (!stack.add(name)) {
                return@replace "null"
            }
            try {
                val engine = SimpleFunctionEngine { nested ->
                    resolveInternal(player, nested, session, context, stack, applyPapi = true)
                }
                engine.evaluate(script) ?: "null"
            } finally {
                stack.remove(name)
            }
        }
    }
}
