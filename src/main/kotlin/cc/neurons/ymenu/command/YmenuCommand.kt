package cc.neurons.ymenu.command

import cc.neurons.ymenu.config.MenuRepository
import cc.neurons.ymenu.menu.MenuService
import cc.neurons.ymenu.util.Texts
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class YmenuCommand(
    private val menuService: MenuService,
    private val menuRepository: MenuRepository,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage(Texts.colorize("&cUsage: /$label <open|reload> ..."))
            return true
        }

        return when (args[0].lowercase()) {
            "open" -> handleOpen(sender, args)
            "reload" -> handleReload(sender)
            else -> {
                sender.sendMessage(Texts.colorize("&cUnknown subcommand."))
                true
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        return when (args.size) {
            1 -> listOf("open", "reload").filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
            2 -> if (args[0].equals("open", ignoreCase = true)) {
                menuRepository.menuIds().filter { it.startsWith(args[1], ignoreCase = true) }.toMutableList()
            } else {
                mutableListOf()
            }
            3 -> if (args[0].equals("open", ignoreCase = true)) {
                Bukkit.getOnlinePlayers().map(Player::getName).filter { it.startsWith(args[2], ignoreCase = true) }.toMutableList()
            } else {
                mutableListOf()
            }
            else -> mutableListOf()
        }
    }

    private fun handleOpen(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(Texts.colorize("&cUsage: /ymenu open <menuId> [player]"))
            return true
        }

        val target = when {
            args.size >= 3 -> Bukkit.getPlayerExact(args[2])
            sender is Player -> sender
            else -> null
        }

        if (target == null) {
            sender.sendMessage(Texts.colorize("&cTarget player not found."))
            return true
        }

        val opened = menuService.openMenu(target, args[1])
        if (!opened) {
            sender.sendMessage(Texts.colorize("&cMenu '&f${args[1]}&c' not found."))
            return true
        }

        sender.sendMessage(Texts.colorize("&aOpened menu '&f${args[1]}&a' for &f${target.name}&a."))
        return true
    }

    private fun handleReload(sender: CommandSender): Boolean {
        menuService.reload()
        sender.sendMessage(Texts.colorize("&aYmenu reloaded. Loaded &f${menuRepository.menuIds().size}&a menus."))
        return true
    }
}
