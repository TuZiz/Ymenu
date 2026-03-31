package cc.neurons.ymenu.menu

import org.bukkit.entity.Player

interface MenuRuntime {
    fun openMenu(player: Player, menuId: String): Boolean

    fun goBack(player: Player, fallbackMenuId: String? = null)

    fun close(player: Player, bypassGuard: Boolean = true)

    fun refresh(player: Player, reopen: Boolean = false)

    fun changePage(player: Player, delta: Int)

    fun setPage(player: Player, page: Int)

    fun updateTitle(player: Player, titleExpression: String)
}
