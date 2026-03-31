package cc.neurons.ymenu.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PlayerDataStore(
    private val plugin: Plugin,
) {
    private val file = File(plugin.dataFolder, "player-data.yml")
    private val config = YamlConfiguration()
    private val dirty = AtomicBoolean(false)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Ymenu-DataStore").apply { isDaemon = true }
    }

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        if (file.exists()) {
            config.load(file)
        }
        executor.scheduleAtFixedRate({ flushIfDirty() }, 60L, 60L, TimeUnit.SECONDS)
    }

    @Synchronized
    fun get(playerId: UUID, key: String): String? {
        return config.getString(path(playerId, key))
    }

    @Synchronized
    fun set(playerId: UUID, key: String, value: String) {
        config.set(path(playerId, key), value)
        dirty.set(true)
    }

    @Synchronized
    fun remove(playerId: UUID, key: String) {
        config.set(path(playerId, key), null)
        dirty.set(true)
    }

    @Synchronized
    fun flush() {
        if (dirty.compareAndSet(true, false)) {
            config.save(file)
        }
    }

    fun shutdown() {
        executor.shutdown()
        flush()
    }

    private fun flushIfDirty() {
        try {
            flush()
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save player data: ${e.message}")
        }
    }

    private fun path(playerId: UUID, key: String): String = "${playerId}.$key"
}

