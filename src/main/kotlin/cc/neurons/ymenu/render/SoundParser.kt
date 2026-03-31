package cc.neurons.ymenu.render

import org.bukkit.Sound

data class SoundSpec(
    val sound: Sound,
    val volume: Float,
    val pitch: Float,
)

class SoundParser {
    fun parse(raw: String): SoundSpec? {
        val parts = raw.split('-')
        if (parts.isEmpty()) {
            return null
        }

        val sound = runCatching { Sound.valueOf(parts[0].trim().uppercase()) }.getOrNull() ?: return null
        val volume = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        val pitch = parts.getOrNull(2)?.toFloatOrNull() ?: 1.0f
        return SoundSpec(sound, volume, pitch)
    }
}
