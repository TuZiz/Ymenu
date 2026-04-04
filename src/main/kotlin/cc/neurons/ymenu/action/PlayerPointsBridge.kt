package cc.neurons.ymenu.action

import org.bukkit.plugin.Plugin
import java.lang.reflect.Method
import java.util.UUID

class PlayerPointsBridge(
    private val plugin: Plugin,
) : PointsGateway {
    @Volatile
    private var apiHandle: ApiHandle? = null

    @Volatile
    private var missingWarningLogged = false

    @Volatile
    private var resolutionWarningLogged = false

    override fun give(playerId: UUID, amount: Int): Boolean = invoke(playerId, amount) { handle, uuid, points ->
        handle.give.invoke(handle.api, uuid, points) as? Boolean ?: false
    }

    override fun take(playerId: UUID, amount: Int): Boolean = invoke(playerId, amount) { handle, uuid, points ->
        handle.take.invoke(handle.api, uuid, points) as? Boolean ?: false
    }

    override fun set(playerId: UUID, amount: Int): Boolean = invoke(playerId, amount) { handle, uuid, points ->
        handle.set.invoke(handle.api, uuid, points) as? Boolean ?: false
    }

    private fun invoke(playerId: UUID, amount: Int, action: (ApiHandle, UUID, Int) -> Boolean): Boolean {
        val handle = resolveApi() ?: return false
        return runCatching {
            action(handle, playerId, amount)
        }.onFailure {
            apiHandle = null
            plugin.logger.warning("PlayerPoints API call failed: ${it.message}")
        }.getOrDefault(false)
    }

    private fun resolveApi(): ApiHandle? {
        if (!plugin.server.pluginManager.isPluginEnabled("PlayerPoints")) {
            if (!missingWarningLogged) {
                missingWarningLogged = true
                plugin.logger.warning("PlayerPoints action ignored because PlayerPoints is not enabled")
            }
            return null
        }

        apiHandle?.let { return it }
        return synchronized(this) {
            apiHandle?.let { return@synchronized it }
            runCatching {
                val pluginClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints")
                val pluginInstance = pluginClass.getMethod("getInstance").invoke(null) ?: return@runCatching null
                val api = pluginClass.getMethod("getAPI").invoke(pluginInstance) ?: return@runCatching null
                val apiClass = api.javaClass
                ApiHandle(
                    api = api,
                    give = apiClass.getMethod("give", UUID::class.java, Integer.TYPE),
                    take = apiClass.getMethod("take", UUID::class.java, Integer.TYPE),
                    set = apiClass.getMethod("set", UUID::class.java, Integer.TYPE),
                ).also {
                    apiHandle = it
                    missingWarningLogged = false
                    resolutionWarningLogged = false
                }
            }.getOrElse {
                if (!resolutionWarningLogged) {
                    resolutionWarningLogged = true
                    plugin.logger.warning("Unable to hook PlayerPoints API: ${it.message}")
                }
                null
            }
        }
    }

    private data class ApiHandle(
        val api: Any,
        val give: Method,
        val take: Method,
        val set: Method,
    )
}
