package cc.neurons.ymenu.action

import org.bukkit.plugin.Plugin

class BukkitTransactionalConsoleExecutor(
    private val plugin: Plugin,
) : TransactionalConsoleExecutor {
    override fun execute(command: String): Boolean {
        return plugin.server.dispatchCommand(plugin.server.consoleSender, command)
    }
}
