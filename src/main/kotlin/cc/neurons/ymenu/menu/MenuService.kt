package cc.neurons.ymenu.menu

import cc.neurons.ymenu.action.ActionExecutor
import cc.neurons.ymenu.condition.ConditionEvaluator
import cc.neurons.ymenu.condition.ConditionHandlers
import cc.neurons.ymenu.config.MenuRepository
import cc.neurons.ymenu.data.PlayerDataStore
import cc.neurons.ymenu.model.ButtonDefinition
import cc.neurons.ymenu.model.ButtonType
import cc.neurons.ymenu.model.ClickType
import cc.neurons.ymenu.model.DisplaySpec
import cc.neurons.ymenu.model.MenuDefinition
import cc.neurons.ymenu.model.MenuVariables
import cc.neurons.ymenu.platform.PlatformScheduler
import cc.neurons.ymenu.render.ItemRenderer
import cc.neurons.ymenu.render.ItemResolver
import cc.neurons.ymenu.render.PlaceholderResolver
import cc.neurons.ymenu.render.SoundParser
import cc.neurons.ymenu.util.Texts
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MenuService(
    private val plugin: Plugin,
    val scheduler: PlatformScheduler,
    private val placeholderResolver: PlaceholderResolver,
    private val repository: MenuRepository,
    private val playerDataStore: PlayerDataStore,
) : MenuRuntime {
    private val packetSoftSwitch = plugin.config.getBoolean("packet-soft-switch", true)
    private val itemResolver = ItemResolver(plugin)
    private val conditionHandlers = ConditionHandlers(itemResolver)
    private val conditionEvaluator = ConditionEvaluator(placeholderResolver, conditionHandlers)
    private val itemRenderer = ItemRenderer(placeholderResolver, itemResolver)
    private val pageRenderer = PageRenderer()
    private val screenBridge = MenuScreenBridge(plugin)
    private val sessions = ConcurrentHashMap<UUID, MenuSession>()
    private val actionExecutor = ActionExecutor(
        plugin,
        placeholderResolver,
        conditionEvaluator,
        SoundParser(),
        this,
        scheduler,
        itemResolver,
        playerDataStore,
    )

    override fun openMenu(player: Player, menuId: String): Boolean = openMenu(player, menuId, pushHistory = true)

    fun openMenu(player: Player, menuId: String, pushHistory: Boolean = true): Boolean {
        val menu = repository.get(menuId) ?: return false
        val scheduled = scheduler.runPlayer(player) {
            val previous = sessions[player.uniqueId]
            val session = if (previous != null && canSoftSwitch(player, previous, menu)) {
                previous.updateTask?.cancel()
                transitionSession(player, previous, menu, pushHistory)
            } else {
                createSession(player, menu, previous, pushHistory).also {
                    previous?.updateTask?.cancel()
                }
            }
            sessions[player.uniqueId] = session
            actionExecutor.execute(player, session, menu.openActions)
            renderSession(player, session)
            if (previous != null && previous === session) {
                refreshTitle(player, session, force = true)
            } else {
                screenBridge.open(player, session.inventory, session.renderedTitle)
            }
            installUpdateTask(player, session)
        }
        return scheduled != null
    }

    fun handleClick(player: Player, inventory: Inventory, rawSlot: Int, clickType: ClickType) {
        val session = sessions[player.uniqueId] ?: return
        if (session.inventory != inventory) {
            return
        }
        val buttonKey = session.slotButtons[rawSlot] ?: return
        val button = session.menu.buttons[buttonKey] ?: return
        val resolved = resolveButton(player, session, button) ?: return
        val actions = actionsForClick(resolved, clickType)
        if (actions.isEmpty()) {
            return
        }
        if (isOnCooldown(session, button)) {
            return
        }
        applyCooldown(session, button)
        actionExecutor.execute(player, session, actions, resolved.context)
    }

    fun handleClose(player: Player, inventory: Inventory) {
        val session = sessions[player.uniqueId] ?: return
        if (session.inventory != inventory) {
            return
        }

        when (session.closeReason) {
            CloseReason.INTERNAL_REOPEN -> {
                return
            }
            CloseReason.ACTION -> {
                session.closeReason = CloseReason.NORMAL
                sessions.remove(player.uniqueId)
                session.updateTask?.cancel()
            }
            CloseReason.NORMAL -> {
                if (shouldDenyClose(player, session)) {
                    session.closeReason = CloseReason.INTERNAL_REOPEN
                    actionExecutor.execute(player, session, session.menu.closeDenyActions)
                    scheduler.runPlayer(player) {
                        val active = sessions[player.uniqueId] ?: return@runPlayer
                        active.closeReason = CloseReason.NORMAL
                        renderSession(player, active)
                        screenBridge.open(player, active.inventory, active.renderedTitle)
                    }
                    return
                }

                sessions.remove(player.uniqueId)
                session.updateTask?.cancel()
                actionExecutor.execute(player, session, session.menu.closeActions)
            }
        }
    }

    fun handleQuit(player: Player) {
        conditionEvaluator.clearPlayer(player)
        actionExecutor.clearInputCapture(player.uniqueId)
        val session = sessions.remove(player.uniqueId) ?: return
        session.updateTask?.cancel()
    }

    fun handleChatInput(player: Player, message: String): Boolean {
        return actionExecutor.handleChatInput(player, message)
    }

    fun reload() {
        sessions.values.forEach { it.updateTask?.cancel() }
        sessions.clear()
        actionExecutor.clearAllInputCaptures()
        repository.reload()
    }

    fun shutdown() {
        reload()
        scheduler.cancelTasks()
    }

    override fun refresh(player: Player, reopen: Boolean) {
        val session = sessions[player.uniqueId] ?: return
        if (reopen) {
            session.closeReason = CloseReason.INTERNAL_REOPEN
            openMenu(player, session.menu.id, pushHistory = false)
            return
        }
        renderSession(player, session)
    }

    override fun close(player: Player, bypassGuard: Boolean) {
        val session = sessions[player.uniqueId] ?: return
        session.closeReason = if (bypassGuard) CloseReason.ACTION else CloseReason.NORMAL
        scheduler.runPlayer(player) {
            player.closeInventory()
        }
    }

    override fun goBack(player: Player, fallbackMenuId: String?) {
        val session = sessions[player.uniqueId]
        val target = session?.history?.removeLastOrNull() ?: fallbackMenuId ?: return
        openMenu(player, target, pushHistory = false)
    }

    override fun changePage(player: Player, delta: Int) {
        val session = sessions[player.uniqueId] ?: return
        val target = (session.currentPage + delta).coerceIn(1, session.maxPage)
        if (target == session.currentPage) {
            return
        }
        session.currentPage = target
        renderSession(player, session)
    }

    override fun setPage(player: Player, page: Int) {
        val session = sessions[player.uniqueId] ?: return
        session.currentPage = page.coerceIn(1, session.maxPage)
        renderSession(player, session)
    }

    fun session(player: Player): MenuSession? = sessions[player.uniqueId]

    override fun updateTitle(player: Player, titleExpression: String) {
        val session = sessions[player.uniqueId] ?: return
        session.titleExpression = titleExpression
        session.renderedTitle = resolveTitle(player, session)
        if (player.openInventory.topInventory != session.inventory) {
            return
        }
        refreshTitle(player, session, force = true)
    }

    private fun createSession(player: Player, menu: MenuDefinition, previous: MenuSession?, pushHistory: Boolean): MenuSession {
        val holder = MenuHolder(UUID.randomUUID())
        val initialTitle = Texts.colorize(placeholderResolver.resolve(player, menu.title, previous))
        val inventory = Bukkit.createInventory(holder, menu.size, initialTitle)
        holder.backingInventory = inventory
        val session = MenuSession(
            id = holder.sessionId,
            playerId = player.uniqueId,
            menu = menu,
            inventory = inventory,
            slotButtons = linkedMapOf(),
            history = previous?.history?.toMutableList() ?: mutableListOf(),
            variables = MenuVariables(previous?.variables?.values?.toMutableMap() ?: mutableMapOf()),
            metadata = previous?.metadata?.toMutableMap() ?: mutableMapOf(),
            titleExpression = menu.title,
            renderedTitle = initialTitle,
        )
        if (pushHistory && previous != null && previous.menu.id != menu.id) {
            session.history += previous.menu.id
        }
        return session
    }

    private fun transitionSession(player: Player, session: MenuSession, menu: MenuDefinition, pushHistory: Boolean): MenuSession {
        if (pushHistory && session.menu.id != menu.id) {
            session.history += session.menu.id
        }
        session.menu = menu
        session.elapsedTicks = 0
        session.currentPage = 1
        session.maxPage = 1
        session.slotButtons.clear()
        session.functionCache.clear()
        session.clickCooldowns.clear()
        session.titleExpression = menu.title
        session.renderedTitle = resolveTitle(player, session)
        session.closeReason = CloseReason.NORMAL
        session.updateTask = null
        return session
    }

    private fun canSoftSwitch(player: Player, session: MenuSession, target: MenuDefinition): Boolean {
        if (!packetSoftSwitch) {
            return false
        }
        if (session.inventory.size != target.size) {
            return false
        }
        return player.openInventory.topInventory == session.inventory
    }

    private fun renderSession(player: Player, session: MenuSession) {
        session.slotButtons.clear()
        for (slot in 0 until session.inventory.size) {
            session.inventory.setItem(slot, null)
        }

        session.maxPage = pageRenderer.maxPage(session) { isButtonVisible(player, session, it) }
        session.currentPage = session.currentPage.coerceIn(1, session.maxPage)

        session.menu.layoutForPage(session.currentPage).forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, token ->
                if (token == " ") {
                    return@forEachIndexed
                }
                val button = session.menu.buttons[token] ?: return@forEachIndexed
                val resolved = resolveButton(player, session, button) ?: return@forEachIndexed
                if (button.buttonType == ButtonType.PAGE_ITEM) {
                    return@forEachIndexed
                }
                val slot = rowIndex * 9 + columnIndex
                session.inventory.setItem(slot, itemRenderer.render(player, resolved, session))
                session.slotButtons[slot] = button.key
            }
        }

        pageRenderer.pageSlice(session) { resolveButton(player, session, it) != null }.forEach { (slot, button) ->
            val resolved = resolveButton(player, session, button) ?: return@forEach
            session.inventory.setItem(slot, itemRenderer.render(player, resolved, session))
            session.slotButtons[slot] = button.key
        }
    }

    private fun installUpdateTask(player: Player, session: MenuSession) {
        val periods = session.menu.buttons.values
            .flatMap { button -> listOfNotNull(button.updateTicks) + button.variants.mapNotNull { it.updateTicks } } +
            listOfNotNull(session.menu.titleUpdateTicks)
        val period = periods
            .minOrNull()
            ?: return
        session.updateTask = scheduler.runPlayerRepeating(player, period, period) {
            val active = sessions[player.uniqueId] ?: return@runPlayerRepeating
            if (player.openInventory.topInventory != active.inventory) {
                return@runPlayerRepeating
            }
            active.elapsedTicks += period
            rerenderActiveButtons(player, active)
            refreshTitleIfNeeded(player, active)
        }
    }

    private fun rerenderActiveButtons(player: Player, session: MenuSession) {
        session.slotButtons.forEach { (slot, key) ->
            val button = session.menu.buttons[key] ?: return@forEach
            val resolved = resolveButton(player, session, button) ?: return@forEach
            val period = resolved.updateTicks ?: return@forEach
            if (session.elapsedTicks % period == 0L) {
                session.inventory.setItem(slot, itemRenderer.render(player, resolved, session))
            }
        }
    }

    private fun refreshTitleIfNeeded(player: Player, session: MenuSession) {
        val period = session.menu.titleUpdateTicks ?: return
        if (period <= 0L || session.elapsedTicks % period != 0L) {
            return
        }
        refreshTitle(player, session, force = false)
    }

    private fun refreshTitle(player: Player, session: MenuSession, force: Boolean) {
        val resolved = resolveTitle(player, session)
        if (!force && resolved == session.renderedTitle) {
            return
        }
        session.renderedTitle = resolved
        if (!screenBridge.updateTitle(player, session.inventory, resolved)) {
            renderSession(player, session)
            screenBridge.open(player, session.inventory, resolved)
        }
    }

    private fun resolveTitle(player: Player, session: MenuSession): String {
        val expression = session.titleExpression ?: session.menu.title
        return Texts.colorize(placeholderResolver.resolve(player, expression, session))
    }

    private fun shouldDenyClose(player: Player, session: MenuSession): Boolean {
        if (session.menu.closeDenyConditions.isEmpty()) {
            return false
        }
        return session.menu.closeDenyConditions.all { conditionEvaluator.evaluate(player, session, it) }
    }

    private fun isButtonVisible(player: Player, session: MenuSession, button: ButtonDefinition): Boolean {
        return resolveButton(player, session, button) != null
    }

    private fun isOnCooldown(session: MenuSession, button: ButtonDefinition): Boolean {
        val cooldown = button.clickCooldownTicks ?: return false
        val now = session.elapsedTicks
        val next = session.clickCooldowns[button.key] ?: return false
        return now < next + cooldown
    }

    private fun applyCooldown(session: MenuSession, button: ButtonDefinition) {
        if (button.clickCooldownTicks != null) {
            session.clickCooldowns[button.key] = session.elapsedTicks
        }
    }

    private fun resolveButton(player: Player, session: MenuSession, button: ButtonDefinition): ResolvedButton? {
        val context = mutableMapOf<String, String>()
        if (button.key.all { it.isDigit() }) {
            context["iconid"] = button.key
        }

        if (!session.isVisible(button) { conditionEvaluator.evaluate(player, session, it, context) }) {
            return null
        }

        val variant = button.variants.firstOrNull { conditionEvaluator.evaluate(player, session, it.condition, context) }
        val display = mergeDisplay(button.display, variant?.display, player, session, context)
        val actionsByClick = LinkedHashMap(button.actionsByClick)
        if (variant != null) {
            variant.actionsByClick.forEach { (type, actions) ->
                actionsByClick[type] = actions
            }
        }
        return ResolvedButton(
            key = button.key,
            display = display,
            updateTicks = variant?.updateTicks ?: button.updateTicks,
            actionsByClick = actionsByClick,
            context = context,
        )
    }

    private fun mergeDisplay(
        base: DisplaySpec,
        override: DisplaySpec?,
        player: Player,
        session: MenuSession,
        context: Map<String, String>,
    ): DisplaySpec {
        val merged = DisplaySpec(
            material = override?.material ?: base.material,
            mat = override?.mat ?: base.mat,
            mats = override?.mats ?: base.mats,
            name = override?.name ?: base.name,
            lore = if (!override?.lore.isNullOrEmpty()) override?.lore ?: emptyList() else base.lore,
            amount = if (override != null && override.amount != 1) override.amount else base.amount,
            shiny = override?.shiny ?: base.shiny,
            shinyCondition = override?.shinyCondition ?: base.shinyCondition,
        )
        val shiny = merged.shinyCondition?.let { conditionEvaluator.evaluate(player, session, it, context) } ?: merged.shiny
        return merged.copy(shiny = shiny)
    }

    private fun actionsForClick(button: ResolvedButton, clickType: ClickType): List<cc.neurons.ymenu.model.ActionSpec> {
        val specific = button.actionsByClick[clickType].orEmpty()
        val all = button.actionsByClick[ClickType.ALL].orEmpty()
        return if (clickType == ClickType.ALL) {
            all
        } else {
            specific + all
        }
    }
}
