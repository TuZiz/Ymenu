package cc.neurons.ymenu.action

import org.bukkit.entity.Player
import java.util.UUID

interface EconomyGateway {
    fun give(player: Player, amount: Double): VaultBridge.TransactionResult

    fun take(player: Player, amount: Double): VaultBridge.TransactionResult
}

interface PointsGateway {
    fun give(playerId: UUID, amount: Int): Boolean

    fun take(playerId: UUID, amount: Int): Boolean
}

interface TransactionalConsoleExecutor {
    fun execute(command: String): Boolean
}
