package cc.neurons.ymenu.action

import cc.neurons.ymenu.condition.ConditionEvaluator
import cc.neurons.ymenu.data.PlayerDataStore
import cc.neurons.ymenu.menu.MenuRuntime
import cc.neurons.ymenu.menu.MenuSession
import cc.neurons.ymenu.model.ActionBarActionSpec
import cc.neurons.ymenu.model.ActionSpec
import cc.neurons.ymenu.model.BackActionSpec
import cc.neurons.ymenu.model.BroadcastActionSpec
import cc.neurons.ymenu.model.CatcherActionSpec
import cc.neurons.ymenu.model.CatcherType
import cc.neurons.ymenu.model.CloseActionSpec
import cc.neurons.ymenu.model.ConditionMatchMode
import cc.neurons.ymenu.model.ConditionalActionSpec
import cc.neurons.ymenu.model.ConsoleActionSpec
import cc.neurons.ymenu.model.DelayActionSpec
import cc.neurons.ymenu.model.ItemActionSpec
import cc.neurons.ymenu.model.ItemOperation
import cc.neurons.ymenu.model.MenuActionSpec
import cc.neurons.ymenu.model.MetaActionSpec
import cc.neurons.ymenu.model.MetaOperation
import cc.neurons.ymenu.model.NextPageActionSpec
import cc.neurons.ymenu.model.PlayerCommandActionSpec
import cc.neurons.ymenu.model.PointsActionSpec
import cc.neurons.ymenu.model.PointsOperation
import cc.neurons.ymenu.model.PrevPageActionSpec
import cc.neurons.ymenu.model.RefreshActionSpec
import cc.neurons.ymenu.model.ResetActionSpec
import cc.neurons.ymenu.model.SetPageActionSpec
import cc.neurons.ymenu.model.SetTitleActionSpec
import cc.neurons.ymenu.model.SoundActionSpec
import cc.neurons.ymenu.model.StopActionSpec
import cc.neurons.ymenu.model.TellActionSpec
import cc.neurons.ymenu.model.TitleActionSpec
import cc.neurons.ymenu.model.DataActionSpec
import cc.neurons.ymenu.model.DataOperation
import cc.neurons.ymenu.model.TransactionalConsoleActionSpec
import cc.neurons.ymenu.model.UnknownActionSpec
import cc.neurons.ymenu.model.VariableActionSpec
import cc.neurons.ymenu.model.VariableOperation
import cc.neurons.ymenu.model.VaultActionSpec
import cc.neurons.ymenu.model.VaultOperation
import cc.neurons.ymenu.platform.PlatformScheduler
import cc.neurons.ymenu.render.ItemResolver
import cc.neurons.ymenu.render.PlaceholderResolver
import cc.neurons.ymenu.render.SoundParser
import cc.neurons.ymenu.util.Texts
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

class ActionExecutor(
    private val plugin: Plugin,
    private val placeholderResolver: PlaceholderResolver,
    private val conditionEvaluator: ConditionEvaluator,
    private val soundParser: SoundParser,
    private val menuRuntime: MenuRuntime,
    private val scheduler: PlatformScheduler,
    private val itemResolver: ItemResolver,
    private val playerDataStore: PlayerDataStore,
    private val pointsGateway: PointsGateway = PlayerPointsBridge(plugin),
    private val economyGateway: EconomyGateway = VaultBridge(plugin),
    private val transactionalConsoleExecutor: TransactionalConsoleExecutor = BukkitTransactionalConsoleExecutor(plugin),
) {
    private val pendingChatInputs = ConcurrentHashMap<UUID, PendingChatInput>()
    private val cancelWords = plugin.config.getStringList("input-cancel-words")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::lowercase)
        .toSet()
        .ifEmpty { setOf("cancel", "quit", "end", "q") }

    fun execute(player: Player, session: MenuSession, actions: List<ActionSpec>, context: Map<String, String> = emptyMap()) {
        executeChain(player, session, actions, 0, 0L, context, transactionStateFor(actions)) { }
    }

    fun handleChatInput(player: Player, message: String): Boolean {
        val pending = pendingChatInputs.remove(player.uniqueId) ?: return false
        val trimmed = message.trim()
        val isCancel = cancelWords.contains(trimmed.lowercase())
        val resolvedContext = pending.context + mapOf("input" to message)

        scheduler.runPlayer(player) {
            pending.session.metadata["input"] = message
            pending.session.metadata["input_${pending.inputKey}"] = message
            if (isCancel) {
                execute(player, pending.session, pending.cancelActions, resolvedContext)
            } else {
                execute(player, pending.session, pending.endActions, resolvedContext)
            }
        }
        return true
    }

    fun clearInputCapture(playerId: UUID) {
        pendingChatInputs.remove(playerId)
    }

    fun clearAllInputCaptures() {
        pendingChatInputs.clear()
    }

    private fun executeChain(
        player: Player,
        session: MenuSession,
        actions: List<ActionSpec>,
        index: Int,
        pendingDelay: Long,
        context: Map<String, String>,
        transaction: TransactionState?,
        onComplete: (Boolean) -> Unit,
    ) {
        val activeTransaction = transaction ?: transactionStateFor(actions)
        if (index >= actions.size) {
            onComplete(true)
            return
        }

        when (val action = actions[index]) {
            is DelayActionSpec -> executeChain(player, session, actions, index + 1, pendingDelay + action.ticks, context, activeTransaction, onComplete)
            is ConditionalActionSpec -> scheduleStep(player, pendingDelay) {
                val matches = when (action.matchMode) {
                    ConditionMatchMode.ALL -> action.conditions.all { conditionEvaluator.evaluate(player, session, it, context) }
                    ConditionMatchMode.ANY -> action.conditions.any { conditionEvaluator.evaluate(player, session, it, context) }
                }
                val branch = if (matches) action.actions else action.deny
                executeChain(player, session, branch, 0, 0L, context, activeTransaction ?: transactionStateFor(branch)) { branchCompleted ->
                    if (branchCompleted) {
                        executeChain(player, session, actions, index + 1, 0L, context, activeTransaction, onComplete)
                    } else {
                        abortTransactionIfNeeded(activeTransaction)
                        onComplete(false)
                    }
                }
            }
            is StopActionSpec -> {
                abortTransactionIfNeeded(activeTransaction)
                onComplete(false)
            }
            else -> scheduleStep(player, pendingDelay) {
                val shouldContinue = runAction(player, session, action, context, activeTransaction)
                if (shouldContinue) {
                    executeChain(player, session, actions, index + 1, 0L, context, activeTransaction, onComplete)
                } else {
                    abortTransactionIfNeeded(activeTransaction)
                    onComplete(false)
                }
            }
        }
    }

    private fun scheduleStep(player: Player, delayTicks: Long, task: () -> Unit) {
        val handle = if (delayTicks <= 0) {
            scheduler.runPlayer(player, task)
        } else {
            scheduler.runPlayerDelayed(player, delayTicks, task)
        }
        if (handle == null) {
            plugin.logger.warning("Failed to schedule menu action step for ${player.name}")
        }
    }

    private fun runAction(
        player: Player,
        session: MenuSession,
        action: ActionSpec,
        context: Map<String, String>,
        transaction: TransactionState?,
    ): Boolean {
        return when (action) {
            is MenuActionSpec -> {
                menuRuntime.openMenu(player, resolve(player, session, action.menuId, context))
                true
            }
            is BackActionSpec -> {
                menuRuntime.goBack(player, action.fallbackMenuId?.let { resolve(player, session, it, context) })
                true
            }
            is CloseActionSpec -> {
                menuRuntime.close(player, action.bypassGuard)
                true
            }
            is CatcherActionSpec -> beginChatCatcher(player, session, action, context)
            is RefreshActionSpec -> {
                menuRuntime.refresh(player, action.reopen)
                true
            }
            is NextPageActionSpec -> {
                menuRuntime.changePage(player, action.amount.absoluteValue)
                true
            }
            is PrevPageActionSpec -> {
                menuRuntime.changePage(player, -action.amount.absoluteValue)
                true
            }
            is SetPageActionSpec -> {
                val resolved = resolve(player, session, action.pageExpression, context)
                val page = resolved.toIntOrNull() ?: return false
                menuRuntime.setPage(player, if (action.zeroBased) page + 1 else page)
                true
            }
            is SoundActionSpec -> {
                val parsed = soundParser.parse(resolve(player, session, action.spec, context)) ?: return false
                player.playSound(player.location, parsed.sound, parsed.volume, parsed.pitch)
                true
            }
            is ConsoleActionSpec -> {
                dispatchConsole(resolve(player, session, action.command, context))
                true
            }
            is TransactionalConsoleActionSpec -> handleTransactionalConsole(player, session, action, context, transaction)
            is PlayerCommandActionSpec -> {
                player.performCommand(resolve(player, session, action.command, context))
                true
            }
            is TellActionSpec -> {
                player.sendMessage(Texts.colorize(resolve(player, session, action.message, context)))
                true
            }
            is SetTitleActionSpec -> {
                menuRuntime.updateTitle(player, resolve(player, session, action.titleExpression, context))
                true
            }
            is ActionBarActionSpec -> {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(Texts.colorize(resolve(player, session, action.message, context))))
                true
            }
            is BroadcastActionSpec -> {
                plugin.server.broadcastMessage(Texts.colorize(resolve(player, session, action.message, context)))
                true
            }
            is TitleActionSpec -> {
                player.sendTitle(
                    Texts.colorize(resolve(player, session, action.title, context)),
                    Texts.colorize(resolve(player, session, action.subtitle, context)),
                    action.fadeIn,
                    action.stay,
                    action.fadeOut,
                )
                true
            }
            is VaultActionSpec -> handleVault(player, session, action, context, transaction)
            is PointsActionSpec -> handlePoints(player, session, action, context, transaction)
            is ItemActionSpec -> {
                handleItem(player, session, action, context)
                true
            }
            is VariableActionSpec -> {
                handleVariable(session, action, player, context)
                true
            }
            is MetaActionSpec -> {
                handleMeta(session, action, player, context)
                true
            }
            is DataActionSpec -> {
                handleData(player, session, action, context)
                true
            }
            is ResetActionSpec -> {
                handleReset(session, action)
                true
            }
            is UnknownActionSpec -> {
                plugin.logger.warning("Unknown action ignored: ${action.raw}")
                true
            }
            is StopActionSpec -> false
            else -> true
        }
    }

    private fun beginChatCatcher(player: Player, session: MenuSession, action: CatcherActionSpec, context: Map<String, String>): Boolean {
        if (action.type != CatcherType.CHAT && action.type != CatcherType.SIGN) {
            plugin.logger.warning("Unsupported catcher type ignored: ${action.type}")
            return false
        }

        pendingChatInputs[player.uniqueId] = PendingChatInput(
            session = session,
            inputKey = action.inputKey,
            cancelActions = action.cancelActions,
            endActions = action.endActions,
            context = context,
        )
        if (action.startActions.isNotEmpty()) {
            execute(player, session, action.startActions, context)
        }
        return true
    }

    private fun handleVault(
        player: Player,
        session: MenuSession,
        action: VaultActionSpec,
        context: Map<String, String>,
        transaction: TransactionState?,
    ): Boolean {
        val amount = resolve(player, session, action.amountExpression, context).toDoubleOrNull()
        if (amount == null) {
            plugin.logger.warning("Vault action ignored because amount is invalid: ${action.amountExpression}")
            return false
        }
        if (amount <= 0.0) {
            plugin.logger.warning("Vault action ignored because amount must be positive: $amount")
            return false
        }

        val result = when (action.operation) {
            VaultOperation.GIVE -> economyGateway.give(player, amount)
            VaultOperation.TAKE -> economyGateway.take(player, amount)
        }
        if (!result.success) {
            plugin.logger.warning("Vault action failed: ${action.operation} $amount for ${player.name}${result.error?.let { " ($it)" } ?: ""}")
        }
        if (result.success && transaction?.preCommit == true) {
            transaction.rollbacks.addFirst {
                when (action.operation) {
                    VaultOperation.GIVE -> economyGateway.take(player, amount).success
                    VaultOperation.TAKE -> economyGateway.give(player, amount).success
                }
            }
        }
        return result.success
    }

    private fun handlePoints(
        player: Player,
        session: MenuSession,
        action: PointsActionSpec,
        context: Map<String, String>,
        transaction: TransactionState?,
    ): Boolean {
        val amount = parseWholePoints(resolve(player, session, action.amountExpression, context))
        if (amount == null) {
            plugin.logger.warning("PlayerPoints action ignored because amount is invalid: ${action.amountExpression}")
            return false
        }
        if (amount <= 0) {
            plugin.logger.warning("PlayerPoints action ignored because amount must be positive: $amount")
            return false
        }

        val success = when (action.operation) {
            PointsOperation.GIVE -> pointsGateway.give(player.uniqueId, amount)
            PointsOperation.TAKE -> pointsGateway.take(player.uniqueId, amount)
        }
        if (!success) {
            plugin.logger.warning("PlayerPoints action failed: ${action.operation} $amount for ${player.name}")
        }
        if (success && transaction?.preCommit == true) {
            transaction.rollbacks.addFirst {
                when (action.operation) {
                    PointsOperation.GIVE -> pointsGateway.take(player.uniqueId, amount)
                    PointsOperation.TAKE -> pointsGateway.give(player.uniqueId, amount)
                }
            }
        }
        return success
    }

    private fun handleItem(player: Player, session: MenuSession, action: ItemActionSpec, context: Map<String, String>) {
        val itemId = resolve(player, session, action.itemId, context)
        val material = itemResolver.resolve(itemId)
        val stack = ItemStack(material, action.amount.coerceAtLeast(1))
        when (action.operation) {
            ItemOperation.GIVE -> player.inventory.addItem(stack)
            ItemOperation.TAKE -> player.inventory.removeItem(stack)
        }
    }

    private fun handleVariable(session: MenuSession, action: VariableActionSpec, player: Player, context: Map<String, String>) {
        val key = action.key
        when (action.operation) {
            VariableOperation.SET -> session.variables.values[key] = resolve(player, session, action.value.orEmpty(), context)
            VariableOperation.UNSET -> session.variables.values.remove(key)
            VariableOperation.INC -> {
                val current = session.variables.values[key]?.toDoubleOrNull() ?: 0.0
                session.variables.values[key] = (current + action.step).toString()
            }
            VariableOperation.DEC -> {
                val current = session.variables.values[key]?.toDoubleOrNull() ?: 0.0
                session.variables.values[key] = (current - action.step).toString()
            }
        }
    }

    private fun handleMeta(session: MenuSession, action: MetaActionSpec, player: Player, context: Map<String, String>) {
        when (action.operation) {
            MetaOperation.SET -> session.metadata[action.key] = resolve(player, session, action.value.orEmpty(), context)
            MetaOperation.DELETE -> session.metadata.remove(action.key)
        }
    }

    private fun handleData(player: Player, session: MenuSession, action: DataActionSpec, context: Map<String, String>) {
        when (action.operation) {
            DataOperation.SET -> playerDataStore.set(player.uniqueId, action.key, resolve(player, session, action.value.orEmpty(), context))
            DataOperation.DELETE -> playerDataStore.remove(player.uniqueId, action.key)
        }
    }

    private fun handleReset(session: MenuSession, action: ResetActionSpec) {
        session.functionCache.clear()
        if (action.clearState) {
            session.variables.values.clear()
            session.metadata.clear()
        }
    }

    private fun dispatchConsole(command: String) {
        scheduler.runGlobal {
            plugin.server.dispatchCommand(plugin.server.consoleSender, command)
        }
    }

    private fun handleTransactionalConsole(
        player: Player,
        session: MenuSession,
        action: TransactionalConsoleActionSpec,
        context: Map<String, String>,
        transaction: TransactionState?,
    ): Boolean {
        val command = resolve(player, session, action.command, context)
        val success = transactionalConsoleExecutor.execute(command)
        if (!success) {
            plugin.logger.warning("Transactional console action failed: $command")
            return false
        }
        if (transaction?.preCommit == true) {
            action.rollbackCommand
                ?.let { resolve(player, session, it, context) }
                ?.let { rollbackCommand ->
                    transaction.rollbacks.addFirst { transactionalConsoleExecutor.execute(rollbackCommand) }
                }
        }
        if (action.commitTransaction) {
            transaction?.commit()
        }
        return true
    }

    private fun transactionStateFor(actions: List<ActionSpec>): TransactionState? {
        return if (containsTransactionalAction(actions)) TransactionState() else null
    }

    private fun containsTransactionalAction(actions: List<ActionSpec>): Boolean {
        return actions.any { action ->
            when (action) {
                is TransactionalConsoleActionSpec -> true
                is ConditionalActionSpec -> containsTransactionalAction(action.actions) || containsTransactionalAction(action.deny)
                else -> false
            }
        }
    }

    private fun abortTransactionIfNeeded(transaction: TransactionState?) {
        if (transaction == null || !transaction.preCommit) {
            return
        }
        transaction.rollback()
    }

    private fun parseWholePoints(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val decimal = raw.toDoubleOrNull() ?: return null
        if (decimal % 1.0 != 0.0) {
            return null
        }
        return decimal.toInt()
    }

    private fun resolve(player: Player, session: MenuSession, input: String, context: Map<String, String>): String =
        placeholderResolver.resolve(player, input, session, context)

    private data class PendingChatInput(
        val session: MenuSession,
        val inputKey: String,
        val cancelActions: List<ActionSpec>,
        val endActions: List<ActionSpec>,
        val context: Map<String, String>,
    )

    private class TransactionState {
        val rollbacks: ArrayDeque<() -> Boolean> = ArrayDeque()
        var preCommit: Boolean = true
            private set

        fun commit() {
            preCommit = false
            rollbacks.clear()
        }

        fun rollback() {
            while (rollbacks.isNotEmpty()) {
                rollbacks.removeFirst().invoke()
            }
            preCommit = false
        }
    }
}
