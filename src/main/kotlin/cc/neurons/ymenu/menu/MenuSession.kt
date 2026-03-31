package cc.neurons.ymenu.menu

import cc.neurons.ymenu.model.ButtonDefinition
import cc.neurons.ymenu.model.MenuDefinition
import cc.neurons.ymenu.model.MenuVariables
import cc.neurons.ymenu.platform.CancellableTask
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.UUID

data class MenuSession(
    val id: UUID,
    val playerId: UUID,
    var menu: MenuDefinition,
    var inventory: Inventory,
    val slotButtons: MutableMap<Int, String>,
    var updateTask: CancellableTask? = null,
    var elapsedTicks: Long = 0,
    var currentPage: Int = 1,
    var maxPage: Int = 1,
    val history: MutableList<String> = mutableListOf(),
    val variables: MenuVariables = MenuVariables(),
    val metadata: MutableMap<String, String> = mutableMapOf(),
    val functionCache: MutableMap<String, String> = mutableMapOf(),
    val clickCooldowns: MutableMap<String, Long> = mutableMapOf(),
    var titleExpression: String? = null,
    var renderedTitle: String = "",
    var closeReason: CloseReason = CloseReason.NORMAL,
) {
    fun isViewer(player: Player): Boolean = player.uniqueId == playerId

    fun isVisible(button: ButtonDefinition, conditionChecker: (String) -> Boolean): Boolean {
        return button.viewConditions.all(conditionChecker)
    }
}
