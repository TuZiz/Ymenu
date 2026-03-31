package cc.neurons.ymenu.platform

import org.bukkit.entity.Player

interface PlatformScheduler {
    fun runPlayer(player: Player, task: () -> Unit): CancellableTask?

    fun runPlayerDelayed(player: Player, delayTicks: Long, task: () -> Unit): CancellableTask?

    fun runPlayerRepeating(player: Player, initialDelayTicks: Long, periodTicks: Long, task: () -> Unit): CancellableTask?

    fun runGlobal(task: () -> Unit): CancellableTask?

    fun runGlobalDelayed(delayTicks: Long, task: () -> Unit): CancellableTask?

    fun cancelTasks()
}
