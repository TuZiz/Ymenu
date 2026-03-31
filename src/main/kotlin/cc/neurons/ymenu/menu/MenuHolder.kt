package cc.neurons.ymenu.menu

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class MenuHolder(
    val sessionId: UUID,
) : InventoryHolder {
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory = backingInventory
}
