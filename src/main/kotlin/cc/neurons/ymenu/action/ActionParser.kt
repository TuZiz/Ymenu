package cc.neurons.ymenu.action

import cc.neurons.ymenu.model.*

class ActionParser {
    private val selfTargets = setOf("%player_name%", "%player%", "{player_name}", "{player}")
    private val transactionalConsoleCommands = setOf("pokedel", "pokeedit", "pokegive", "lp", "nye")

    fun parseAll(entries: List<*>): List<ActionSpec> = normalize(entries.mapNotNull(::parseEntry))

    fun parseEntry(entry: Any?): ActionSpec? = when (entry) {
        null -> null
        is String -> parseString(entry)
        is Map<*, *> -> parseMap(entry)
        else -> UnknownActionSpec(entry.toString())
    }

    private fun parseString(raw: String): ActionSpec {
        val separatorIndex = raw.indexOf(':')
        if (separatorIndex == -1) {
            val normalized = raw.trim()
            val lowered = normalized.lowercase()
            return when {
                lowered == "back" -> BackActionSpec()
                lowered == "close" -> CloseActionSpec()
                lowered == "refresh" -> RefreshActionSpec()
                lowered == "update" -> RefreshActionSpec()
                lowered == "reset" -> ResetActionSpec()
                lowered == "return" -> StopActionSpec(normalized)
                lowered == "next page" -> NextPageActionSpec()
                lowered == "prev page" -> PrevPageActionSpec()
                lowered.startsWith("set var ") -> parseVariableSet(normalized.removePrefix("set var ").trim(), raw)
                lowered.startsWith("unset var ") -> VariableActionSpec(VariableOperation.UNSET, normalized.removePrefix("unset var ").trim())
                lowered.startsWith("inc var ") -> parseVariableStep(normalized.removePrefix("inc var ").trim(), VariableOperation.INC, raw)
                lowered.startsWith("dec var ") -> parseVariableStep(normalized.removePrefix("dec var ").trim(), VariableOperation.DEC, raw)
                else -> UnknownActionSpec(raw)
            }
        }

        val key = raw.substring(0, separatorIndex).trim().lowercase()
        val value = raw.substring(separatorIndex + 1).trim()
        return when (key) {
            "menu", "open" -> MenuActionSpec(value)
            "sound" -> SoundActionSpec(value)
            "delay" -> value.toLongOrNull()?.let(::DelayActionSpec) ?: UnknownActionSpec(raw)
            "console" -> parseConsole(value, raw)
            "player", "command" -> PlayerCommandActionSpec(value)
            "tell", "message" -> TellActionSpec(value)
            "set-title" -> SetTitleActionSpec(value)
            "actionbar" -> ActionBarActionSpec(value)
            "broadcast" -> BroadcastActionSpec(value)
            "title" -> parseTitle(value)
            "close" -> CloseActionSpec(value.toBooleanStrictOrNull() ?: true)
            "refresh" -> RefreshActionSpec(value.equals("reopen", ignoreCase = true))
            "back" -> BackActionSpec(value.ifBlank { null })
            "next page" -> NextPageActionSpec(value.toIntOrNull() ?: 1)
            "prev page" -> PrevPageActionSpec(value.toIntOrNull() ?: 1)
            "set page" -> SetPageActionSpec(value)
            "page" -> SetPageActionSpec(value, zeroBased = true)
            "vault give" -> parseAmountExpression(value, raw) { VaultActionSpec(VaultOperation.GIVE, it) }
            "vault take" -> parseAmountExpression(value, raw) { VaultActionSpec(VaultOperation.TAKE, it) }
            "vault set" -> parseAmountExpression(value, raw) { VaultActionSpec(VaultOperation.SET, it) }
            "give-money", "give money" -> parseAmountExpression(value, raw) { VaultActionSpec(VaultOperation.GIVE, it) }
            "take-money", "take money" -> parseAmountExpression(value, raw) { VaultActionSpec(VaultOperation.TAKE, it) }
            "set-money", "set money" -> parseAmountExpression(value, raw) { VaultActionSpec(VaultOperation.SET, it) }
            "give-point", "give point" -> parseAmountExpression(value, raw) { PointsActionSpec(PointsOperation.GIVE, it) }
            "take-point", "take point" -> parseAmountExpression(value, raw) { PointsActionSpec(PointsOperation.TAKE, it) }
            "set-point", "set point", "points set" -> parseAmountExpression(value, raw) { PointsActionSpec(PointsOperation.SET, it) }
            "give item" -> parseItem(value, ItemOperation.GIVE, raw)
            "take item" -> parseItem(value, ItemOperation.TAKE, raw)
            "set var" -> parseVariableSet(value, raw)
            "unset var" -> VariableActionSpec(VariableOperation.UNSET, value)
            "inc var" -> parseVariableStep(value, VariableOperation.INC, raw)
            "dec var" -> parseVariableStep(value, VariableOperation.DEC, raw)
            "set-meta" -> parseKeyValue(value, raw) { key, actual -> MetaActionSpec(MetaOperation.SET, key, actual) }
            "del-meta", "remove-meta" -> MetaActionSpec(MetaOperation.DELETE, value)
            "set-data" -> parseKeyValue(value, raw) { key, actual -> DataActionSpec(DataOperation.SET, key, actual) }
            "del-data", "remove-data" -> DataActionSpec(DataOperation.DELETE, value)
            "update" -> RefreshActionSpec(reopen = value.equals("reopen", ignoreCase = true))
            "bossbar" -> parseBossBar(value)
            "tellraw" -> TellrawActionSpec(value)
            "connect" -> ConnectActionSpec(value)
            "commandop", "command-op" -> CommandOpActionSpec(value)
            "repair" -> RepairItemActionSpec(value.ifBlank { "hand" })
            "enchant" -> parseEnchant(value, raw)
            "set-args", "set args" -> SetArgumentsActionSpec(value.split(' '))
            else -> UnknownActionSpec(raw)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMap(entry: Map<*, *>): ActionSpec {
        findKey(entry, "catcher")?.let { catcherKey ->
            return parseCatcher(entry[catcherKey], entry.toString())
        }

        val conditions = buildList {
            entry["condition"]?.toString()?.let(::add)
            addAll((entry["conditions"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList())
        }
        if (conditions.isEmpty()) {
            return UnknownActionSpec(entry.toString())
        }
        val actions = parseAll(entry["actions"] as? List<*> ?: emptyList<Any?>())
        val deny = parseAll(entry["deny"] as? List<*> ?: emptyList<Any?>())
        val mode = when (entry["match"]?.toString()?.uppercase()) {
            "ANY" -> ConditionMatchMode.ANY
            else -> ConditionMatchMode.ALL
        }
        return ConditionalActionSpec(conditions, mode, actions, deny)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseCatcher(raw: Any?, fallback: String): ActionSpec {
        val catcherMap = raw as? Map<*, *> ?: return UnknownActionSpec(fallback)
        val (inputKey, specRaw) = catcherMap.entries.firstOrNull() ?: return UnknownActionSpec(fallback)
        val spec = specRaw as? Map<*, *> ?: return UnknownActionSpec(fallback)
        val type = when (findValue(spec, "type")?.toString()?.uppercase()) {
            "CHAT" -> CatcherType.CHAT
            "SIGN" -> CatcherType.SIGN
            else -> CatcherType.UNKNOWN
        }
        return CatcherActionSpec(
            inputKey = inputKey.toString(),
            type = type,
            startActions = parseFlexibleActions(findValue(spec, "start")),
            cancelActions = parseFlexibleActions(findValue(spec, "cancel")),
            endActions = parseFlexibleActions(findValue(spec, "end")),
        )
    }

    private fun parseTitle(value: String): ActionSpec {
        val parts = value.split('|')
        return TitleActionSpec(
            title = parts.getOrElse(0) { "" },
            subtitle = parts.getOrElse(1) { "" },
            fadeIn = parts.getOrNull(2)?.toIntOrNull() ?: 10,
            stay = parts.getOrNull(3)?.toIntOrNull() ?: 40,
            fadeOut = parts.getOrNull(4)?.toIntOrNull() ?: 10,
        )
    }

    private fun parseConsole(value: String, raw: String): ActionSpec {
        parseVaultConsoleAlias(value)?.let { return it }
        parseTransactionalGiveAlias(value)?.let { return it }
        if (isTransactionalConsoleCommand(value)) {
            return TransactionalConsoleActionSpec(value)
        }
        return ConsoleActionSpec(value)
    }

    private fun normalize(actions: List<ActionSpec>): List<ActionSpec> {
        val normalized = actions.map { action ->
            when (action) {
                is ConditionalActionSpec -> action.copy(
                    actions = normalize(action.actions),
                    deny = normalize(action.deny),
                )
                else -> action
            }
        }
        return applyTransactionalCommitPolicy(normalizeTransactionalOrder(normalized))
    }

    private fun normalizeTransactionalOrder(actions: List<ActionSpec>): List<ActionSpec> {
        val pivot = actions.indexOfLast { it is TransactionalConsoleActionSpec }
        if (pivot <= 0) {
            return actions
        }

        val before = actions.subList(0, pivot)
        val deferred = before.filter(::shouldRunAfterTransactionalConsole)
        if (deferred.isEmpty()) {
            return actions
        }

        val kept = before.filterNot(::shouldRunAfterTransactionalConsole)
        return buildList(actions.size) {
            addAll(kept)
            add(actions[pivot])
            addAll(deferred)
            addAll(actions.subList(pivot + 1, actions.size))
        }
    }

    private fun shouldRunAfterTransactionalConsole(action: ActionSpec): Boolean {
        return when (action) {
            is TellActionSpec,
            is DataActionSpec,
            is MetaActionSpec,
            is VariableActionSpec,
            is RefreshActionSpec,
            is SetTitleActionSpec,
            is ActionBarActionSpec,
            is BroadcastActionSpec,
            is TitleActionSpec,
            is ResetActionSpec -> true
            else -> false
        }
    }

    private fun applyTransactionalCommitPolicy(actions: List<ActionSpec>): List<ActionSpec> {
        val transactionalIndexes = actions.mapIndexedNotNull { index, action ->
            index.takeIf { action is TransactionalConsoleActionSpec }
        }
        if (transactionalIndexes.size <= 1) {
            return actions
        }
        val finalTransactionalIndex = transactionalIndexes.last()
        return actions.mapIndexed { index, action ->
            if (action is TransactionalConsoleActionSpec && index != finalTransactionalIndex) {
                action.copy(commitTransaction = false)
            } else {
                action
            }
        }
    }

    private fun isTransactionalConsoleCommand(value: String): Boolean {
        val command = value.trim().substringBefore(' ').lowercase()
        return command in transactionalConsoleCommands
    }

    private fun parseVaultConsoleAlias(value: String): ActionSpec? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.size != 4) {
            return null
        }
        val namespace = parts[0].lowercase()
        val operation = when (parts[1].lowercase()) {
            "give" -> VaultOperation.GIVE
            "take" -> VaultOperation.TAKE
            else -> return null
        }
        if (namespace !in setOf("money", "eco")) {
            return null
        }
        if (parts[2].lowercase() !in selfTargets) {
            return null
        }
        return VaultActionSpec(operation, parts[3])
    }

    private fun parseTransactionalGiveAlias(value: String): ActionSpec? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.size !in 3..4) {
            return null
        }
        val command = parts[0].lowercase()
        if (command !in setOf("give", "minecraft:give")) {
            return null
        }
        val target = parts[1].lowercase()
        if (target !in selfTargets) {
            return null
        }
        val item = parts[2]
        val amount = parts.getOrNull(3)?.toIntOrNull() ?: 1
        val clearCommand = if (command == "minecraft:give") "minecraft:clear" else "clear"
        return TransactionalConsoleActionSpec(
            command = value,
            rollbackCommand = "$clearCommand ${parts[1]} $item $amount",
        )
    }

    private fun parseItem(value: String, operation: ItemOperation, raw: String): ActionSpec {
        val parts = value.split(' ')
        val itemId = parts.firstOrNull() ?: return UnknownActionSpec(raw)
        val amount = parts.getOrNull(1)?.toIntOrNull() ?: 1
        return ItemActionSpec(operation, itemId, amount)
    }

    private fun parseVariableSet(value: String, raw: String): ActionSpec {
        val parts = value.split(' ', limit = 2)
        if (parts.size < 2) {
            return UnknownActionSpec(raw)
        }
        return VariableActionSpec(VariableOperation.SET, parts[0], parts[1])
    }

    private fun parseVariableStep(value: String, operation: VariableOperation, raw: String): ActionSpec {
        val parts = value.split(' ', limit = 2)
        val key = parts.firstOrNull() ?: return UnknownActionSpec(raw)
        val step = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
        return VariableActionSpec(operation, key, step = step)
    }

    private fun parseKeyValue(value: String, raw: String, factory: (String, String) -> ActionSpec): ActionSpec {
        val parts = value.split(' ', limit = 2)
        if (parts.size < 2) {
            return UnknownActionSpec(raw)
        }
        return factory(parts[0], parts[1])
    }

    private fun parseAmountExpression(value: String, raw: String, factory: (String) -> ActionSpec): ActionSpec {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            return UnknownActionSpec(raw)
        }
        return factory(normalized)
    }

    private fun parseFlexibleActions(raw: Any?): List<ActionSpec> {
        return when (raw) {
            null -> emptyList()
            is List<*> -> parseAll(raw)
            else -> parseAll(listOf(raw))
        }
    }

    private fun parseBossBar(value: String): BossBarActionSpec {
        val parts = value.split('|')
        return BossBarActionSpec(
            message = parts.getOrElse(0) { "" },
            color = parts.getOrNull(1)?.trim()?.uppercase() ?: "GREEN",
            style = parts.getOrNull(2)?.trim()?.uppercase() ?: "SOLID",
            progress = parts.getOrNull(3)?.trim()?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0,
            durationTicks = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 60,
        )
    }

    private fun parseEnchant(value: String, raw: String): ActionSpec {
        val parts = value.split(' ', limit = 2)
        val enchantment = parts.firstOrNull() ?: return UnknownActionSpec(raw)
        val level = parts.getOrNull(1)?.toIntOrNull() ?: 1
        return EnchantItemActionSpec(enchantment, level)
    }

    private fun findKey(entry: Map<*, *>, target: String): Any? {
        return entry.keys.firstOrNull { it?.toString()?.equals(target, ignoreCase = true) == true }
    }

    private fun findValue(entry: Map<*, *>, target: String): Any? {
        val key = findKey(entry, target) ?: return null
        return entry[key]
    }
}
