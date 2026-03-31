package cc.neurons.ymenu.platform

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class SpigotScheduler(private val plugin: Plugin) : PlatformScheduler {
    override fun runPlayer(player: Player, task: () -> Unit): CancellableTask {
        val handle = plugin.server.scheduler.runTask(plugin, Runnable(task))
        return CancellableTask(handle::cancel)
    }

    override fun runPlayerDelayed(player: Player, delayTicks: Long, task: () -> Unit): CancellableTask {
        val handle = plugin.server.scheduler.runTaskLater(plugin, Runnable(task), delayTicks)
        return CancellableTask(handle::cancel)
    }

    override fun runPlayerRepeating(player: Player, initialDelayTicks: Long, periodTicks: Long, task: () -> Unit): CancellableTask {
        val handle = plugin.server.scheduler.runTaskTimer(plugin, Runnable(task), initialDelayTicks, periodTicks)
        return CancellableTask(handle::cancel)
    }

    override fun runGlobal(task: () -> Unit): CancellableTask {
        val handle = plugin.server.scheduler.runTask(plugin, Runnable(task))
        return CancellableTask(handle::cancel)
    }

    override fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): CancellableTask {
        val handle = plugin.server.scheduler.runTaskLater(plugin, Runnable(task), delayTicks)
        return CancellableTask(handle::cancel)
    }

    override fun cancelTasks() {
        plugin.server.scheduler.cancelTasks(plugin)
    }
}
