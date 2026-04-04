package cc.neurons.ymenu.action

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class VaultBridge(
    private val plugin: Plugin,
) : EconomyGateway {
    @Volatile
    private var missingWarningLogged = false

    override fun give(player: Player, amount: Double): TransactionResult {
        val economy = economy() ?: return TransactionResult(false, "Vault economy provider is unavailable")
        val response = runCatching { economy.depositPlayer(player, amount) }
            .getOrElse { return TransactionResult(false, it.message ?: "deposit failed") }
        return response.toResult()
    }

    override fun take(player: Player, amount: Double): TransactionResult {
        val economy = economy() ?: return TransactionResult(false, "Vault economy provider is unavailable")
        val response = runCatching { economy.withdrawPlayer(player, amount) }
            .getOrElse { return TransactionResult(false, it.message ?: "withdraw failed") }
        return response.toResult()
    }

    override fun getBalance(player: Player): Double? {
        val economy = economy() ?: return null
        return runCatching { economy.getBalance(player) }.getOrNull()
    }

    private fun economy(): Economy? {
        if (!plugin.server.pluginManager.isPluginEnabled("Vault")) {
            if (!missingWarningLogged) {
                missingWarningLogged = true
                plugin.logger.warning("Vault action ignored because Vault is not enabled")
            }
            return null
        }

        val economy = plugin.server.servicesManager.load(Economy::class.java)
        if (economy == null && !missingWarningLogged) {
            missingWarningLogged = true
            plugin.logger.warning("Vault action ignored because no Economy provider is registered")
        }
        if (economy != null) {
            missingWarningLogged = false
        }
        return economy
    }

    private fun EconomyResponse.toResult(): TransactionResult {
        return TransactionResult(
            success = transactionSuccess(),
            error = errorMessage?.takeIf { it.isNotBlank() },
        )
    }

    data class TransactionResult(
        val success: Boolean,
        val error: String? = null,
    )
}
