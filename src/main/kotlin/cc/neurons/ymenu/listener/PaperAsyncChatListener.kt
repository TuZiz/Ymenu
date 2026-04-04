package cc.neurons.ymenu.listener

import cc.neurons.ymenu.menu.MenuService
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin

class PaperAsyncChatListener(
    private val plugin: Plugin,
    private val menuService: MenuService,
) : Listener {
    private val asyncChatEventClass = runCatching {
        Class.forName("io.papermc.paper.event.player.AsyncChatEvent").asSubclass(Event::class.java)
    }.getOrNull()

    fun register(): Boolean {
        val eventClass = asyncChatEventClass ?: return false
        plugin.server.pluginManager.registerEvent(
            eventClass,
            this,
            EventPriority.LOWEST,
            EventExecutor { _, event ->
                if (!eventClass.isInstance(event)) {
                    return@EventExecutor
                }
                handleAsyncChat(event)
            },
            plugin,
            false,
        )
        return true
    }

    private fun handleAsyncChat(event: Event) {
        val player = extractPlayer(event) ?: return
        val message = extractMessage(event) ?: return
        if (!menuService.handleChatInput(player, message)) {
            return
        }
        (event as? Cancellable)?.isCancelled = true
    }

    private fun extractPlayer(event: Event): Player? {
        val method = event.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 0 && (method.name == "player" || method.name == "getPlayer")
        } ?: return null
        return runCatching { method.invoke(event) as? Player }.getOrNull()
    }

    private fun extractMessage(event: Event): String? {
        val method = event.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 0 && (method.name == "message" || method.name == "getMessage")
        } ?: return null
        val rawMessage = runCatching { method.invoke(event) }.getOrNull() ?: return null
        if (rawMessage is String) {
            return rawMessage
        }
        return serializeComponent(rawMessage)
    }

    private fun serializeComponent(component: Any): String? {
        return runCatching {
            val componentClass = Class.forName("net.kyori.adventure.text.Component")
            val serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer")
            val serializer = serializerClass.getMethod("plainText").invoke(null)
            serializerClass.getMethod("serialize", componentClass).invoke(serializer, component) as? String
        }.recover {
            component.toString()
        }.getOrNull()
    }
}
