package cc.neurons.ymenu.render

import org.bukkit.Material
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class ItemResolver(
    plugin: Plugin? = null,
) {
    private val fallbackMaterial = Material.matchMaterial(
        plugin?.config?.getString("fallback-material", "PAPER") ?: "PAPER"
    ) ?: Material.PAPER
    private val warned = ConcurrentHashMap.newKeySet<String>()
    private val logger = plugin?.logger ?: Logger.getLogger("Ymenu")

    fun resolve(materialId: String?): Material {
        val normalized = extractCandidateId(materialId?.trim().orEmpty())
        if (normalized.isBlank()) {
            return fallbackMaterial
        }

        if (normalized.startsWith("head-", ignoreCase = true) ||
            normalized.startsWith("head:", ignoreCase = true) ||
            normalized.startsWith("player_head:", ignoreCase = true)) {
            return Material.PLAYER_HEAD
        }

        val direct = Material.matchMaterial(normalized)
        if (direct != null) {
            return direct
        }

        val minecraftNamespaced = normalized.removePrefix("minecraft:")
        val minecraftMatch = Material.matchMaterial(minecraftNamespaced)
        if (minecraftMatch != null) {
            return minecraftMatch
        }

        val upperSnake = minecraftNamespaced
            .replace(':', '_')
            .replace('-', '_')
            .replace(' ', '_')
            .uppercase(Locale.ROOT)
        val upperMatch = Material.matchMaterial(upperSnake)
        if (upperMatch != null) {
            return upperMatch
        }

        warnOnce(normalized)
        return fallbackMaterial
    }

    fun isExternalDescription(materialId: String?): Boolean {
        val normalized = materialId?.trim().orEmpty()
        return normalized.startsWith("{") || normalized.contains("pixelmon", ignoreCase = true) || normalized.contains(":")
    }

    private fun extractCandidateId(raw: String): String {
        if (!raw.startsWith("{")) {
            return raw
        }
        val doubleQuoted = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
        if (!doubleQuoted.isNullOrBlank()) {
            return doubleQuoted
        }
        val singleLike = Regex("id\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
        if (!singleLike.isNullOrBlank()) {
            return singleLike
        }
        return raw
    }

    private fun warnOnce(materialId: String) {
        if (warned.add(materialId)) {
            logger.warning("Unsupported item id '$materialId', falling back to $fallbackMaterial")
        }
    }
}
