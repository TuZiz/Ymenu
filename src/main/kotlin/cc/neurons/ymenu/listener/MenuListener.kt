package cc.neurons.ymenu.listener

import cc.neurons.ymenu.config.MenuRepository
import cc.neurons.ymenu.menu.MenuHolder
import cc.neurons.ymenu.menu.MenuService
import cc.neurons.ymenu.model.ClickType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent

class MenuListener(
    private val menuService: MenuService,
    private val menuRepository: MenuRepository,
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.topInventory.holder !is MenuHolder) {
            return
        }
        if (event.clickedInventory == null || event.view.topInventory != event.clickedInventory) {
            return
        }

        event.isCancelled = true
        menuService.handleClick(player, event.view.topInventory, event.rawSlot, ClickType.fromBukkit(event.click))
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.holder !is MenuHolder) {
            return
        }
        val player = event.player as? Player ?: return
        menuService.handleClose(player, event.inventory)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        menuService.handleQuit(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val label = event.message.removePrefix("/").substringBefore(' ').trim()
        if (label.isBlank()) {
            return
        }
        val menu = menuRepository.getByCommandBinding(label) ?: return
        event.isCancelled = true
        menuService.openMenu(event.player, menu.id)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncPlayerChatEvent) {
        if (menuService.handleChatInput(event.player, event.message)) {
            event.isCancelled = true
        }
    }
}
