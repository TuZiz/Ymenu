package cc.neurons.ymenu.menu

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class MenuScreenBridge(
    private val plugin: Plugin,
) {
    private val packetScreenSync = plugin.config.getBoolean("packet-screen-sync", true)
    private val titleMethodCache = ConcurrentHashMap<Class<*>, Method?>()

    fun open(player: Player, inventory: Inventory, title: String) {
        val view = player.openInventory(inventory) ?: return
        syncTitle(player, inventory, title, view)
    }

    fun updateTitle(player: Player, inventory: Inventory, title: String): Boolean {
        val view = player.openInventory ?: return false
        return syncTitle(player, inventory, title, view)
    }

    internal fun applyTitle(view: InventoryView, title: String): Boolean {
        val method = titleMethod(view) ?: return false
        return runCatching {
            method.invoke(view, title)
            true
        }.getOrElse {
            false
        }
    }

    private fun syncTitle(player: Player, inventory: Inventory, title: String, view: InventoryView): Boolean {
        if (!packetScreenSync) {
            return false
        }
        if (view.topInventory != inventory) {
            return false
        }
        return runCatching {
            if (view.title != title && !applyTitle(view, title)) {
                return false
            }
            true
        }.getOrElse {
            plugin.logger.warning("Failed to sync packet menu title for ${player.name}: ${it.message}")
            false
        }
    }

    private fun titleMethod(view: InventoryView): Method? {
        return titleMethodCache.getOrPut(view.javaClass) {
            view.javaClass.methods.firstOrNull { method ->
                method.name == "setTitle" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
        }
    }
}
