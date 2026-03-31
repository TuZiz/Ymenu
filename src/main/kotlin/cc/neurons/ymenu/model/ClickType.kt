package cc.neurons.ymenu.model

import org.bukkit.event.inventory.ClickType as BukkitClickType

enum class ClickType {
    ALL,
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP,
    UNKNOWN,
    ;

    companion object {
        fun fromBukkit(click: BukkitClickType): ClickType = when {
            click == BukkitClickType.LEFT -> LEFT
            click == BukkitClickType.RIGHT -> RIGHT
            click == BukkitClickType.SHIFT_LEFT -> SHIFT_LEFT
            click == BukkitClickType.SHIFT_RIGHT -> SHIFT_RIGHT
            click == BukkitClickType.MIDDLE -> MIDDLE
            click == BukkitClickType.DROP || click == BukkitClickType.CONTROL_DROP -> DROP
            else -> UNKNOWN
        }

        fun fromConfig(key: String): ClickType = when (key.trim().lowercase()) {
            "all" -> ALL
            "left", "left_click" -> LEFT
            "right", "right_click" -> RIGHT
            "shift-left", "shift_left", "shiftleft" -> SHIFT_LEFT
            "shift-right", "shift_right", "shiftright" -> SHIFT_RIGHT
            "middle", "middle_click" -> MIDDLE
            "drop" -> DROP
            else -> UNKNOWN
        }
    }
}
