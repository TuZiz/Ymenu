package cc.neurons.ymenu.util

import org.bukkit.ChatColor

object Texts {
    fun colorize(text: String): String = ChatColor.translateAlternateColorCodes('&', text)

    fun colorize(lines: List<String>): List<String> = lines.map(::colorize)
}
