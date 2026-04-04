package cc.neurons.ymenu.platform

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.function.Consumer

class FoliaScheduler(private val plugin: Plugin) : PlatformScheduler {
    override fun shouldRunInline(player: Player): Boolean {
        val bukkitClass = runCatching { Class.forName("org.bukkit.Bukkit") }.getOrNull() ?: return false
        val method = bukkitClass.methods.firstOrNull { candidate ->
            candidate.name == "isOwnedByCurrentRegion" &&
                candidate.parameterCount == 1 &&
                candidate.parameterTypes[0].isAssignableFrom(player.javaClass)
        } ?: return false
        return runCatching { method.invoke(null, player) as? Boolean ?: false }.getOrDefault(false)
    }

    override fun runPlayer(player: Player, task: () -> Unit): CancellableTask? {
        val scheduler = playerScheduler(player) ?: return null
        val handle = invoke(
            scheduler = scheduler,
            methodName = "run",
            parameterCount = 3,
            args = arrayOf(plugin, Consumer<Any?> { task() }, Runnable {})
        )
        return wrap(handle)
    }

    override fun runPlayerDelayed(player: Player, delayTicks: Long, task: () -> Unit): CancellableTask? {
        val scheduler = playerScheduler(player) ?: return null
        val handle = invoke(
            scheduler = scheduler,
            methodName = "runDelayed",
            parameterCount = 4,
            args = arrayOf(plugin, Consumer<Any?> { task() }, Runnable {}, delayTicks)
        )
        return wrap(handle)
    }

    override fun runPlayerRepeating(player: Player, initialDelayTicks: Long, periodTicks: Long, task: () -> Unit): CancellableTask? {
        val scheduler = playerScheduler(player) ?: return null
        val handle = invoke(
            scheduler = scheduler,
            methodName = "runAtFixedRate",
            parameterCount = 5,
            args = arrayOf(plugin, Consumer<Any?> { task() }, Runnable {}, initialDelayTicks, periodTicks)
        )
        return wrap(handle)
    }

    override fun runGlobal(task: () -> Unit): CancellableTask? {
        val scheduler = globalScheduler() ?: return null
        val handle = invoke(
            scheduler = scheduler,
            methodName = "run",
            parameterCount = 2,
            args = arrayOf(plugin, Consumer<Any?> { task() })
        )
        return wrap(handle)
    }

    override fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): CancellableTask? {
        val scheduler = globalScheduler() ?: return null
        val handle = invoke(
            scheduler = scheduler,
            methodName = "runDelayed",
            parameterCount = 3,
            args = arrayOf(plugin, Consumer<Any?> { task() }, delayTicks)
        )
        return wrap(handle)
    }

    override fun cancelTasks() {
        val global = globalScheduler() ?: return
        runCatching {
            findMethod(global.javaClass.methods, "cancelTasks", 1)?.invoke(global, plugin)
        }.onFailure {
            plugin.logger.warning("Failed to cancel global Folia tasks: ${it.message}")
        }
    }

    private fun playerScheduler(player: Player): Any? = runCatching {
        player.javaClass.getMethod("getScheduler").invoke(player)
    }.getOrElse {
        plugin.logger.warning("Failed to get Folia player scheduler for ${player.name}: ${it.message}")
        null
    }

    private fun globalScheduler(): Any? = runCatching {
        plugin.server.javaClass.getMethod("getGlobalRegionScheduler").invoke(plugin.server)
    }.getOrElse {
        plugin.logger.warning("Failed to get Folia global scheduler: ${it.message}")
        null
    }

    private fun invoke(scheduler: Any, methodName: String, parameterCount: Int, args: Array<Any?>): Any? = runCatching {
        findMethod(scheduler.javaClass.methods, methodName, parameterCount)?.invoke(scheduler, *args)
    }.getOrElse {
        plugin.logger.warning("Failed to invoke Folia scheduler method $methodName: ${it.message}")
        null
    }

    private fun findMethod(methods: Array<Method>, methodName: String, parameterCount: Int): Method? {
        return methods.firstOrNull { it.name == methodName && it.parameterCount == parameterCount }
    }

    private fun wrap(handle: Any?): CancellableTask? {
        if (handle == null) {
            return null
        }

        return CancellableTask {
            runCatching {
                findMethod(handle.javaClass.methods, "cancel", 0)?.invoke(handle)
            }.onFailure {
                plugin.logger.warning("Failed to cancel Folia task: ${it.message}")
            }
        }
    }
}
