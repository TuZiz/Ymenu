package cc.neurons.ymenu.platform

import org.bukkit.plugin.Plugin

object PlatformDetector {
    fun detect(plugin: Plugin): PlatformScheduler {
        val isFolia = plugin.server.javaClass.methods.any { it.name == "getGlobalRegionScheduler" }
        if (isFolia) {
            plugin.logger.info("Detected Folia scheduler support.")
            return FoliaScheduler(plugin)
        }

        plugin.logger.info("Using Bukkit scheduler compatibility mode.")
        return SpigotScheduler(plugin)
    }
}
