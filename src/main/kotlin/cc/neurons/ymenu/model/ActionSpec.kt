package cc.neurons.ymenu.model

sealed interface ActionSpec

data class MenuActionSpec(val menuId: String) : ActionSpec

data class SoundActionSpec(val spec: String) : ActionSpec

data class DelayActionSpec(val ticks: Long) : ActionSpec

data class ConsoleActionSpec(val command: String) : ActionSpec

data class TransactionalConsoleActionSpec(
    val command: String,
    val rollbackCommand: String? = null,
    val commitTransaction: Boolean = true,
) : ActionSpec

data class PlayerCommandActionSpec(val command: String) : ActionSpec

data class TellActionSpec(val message: String) : ActionSpec

data class SetTitleActionSpec(val titleExpression: String) : ActionSpec

data class CatcherActionSpec(
    val inputKey: String,
    val type: CatcherType,
    val startActions: List<ActionSpec> = emptyList(),
    val cancelActions: List<ActionSpec> = emptyList(),
    val endActions: List<ActionSpec> = emptyList(),
) : ActionSpec

data class ActionBarActionSpec(val message: String) : ActionSpec

data class TitleActionSpec(
    val title: String,
    val subtitle: String,
    val fadeIn: Int = 10,
    val stay: Int = 40,
    val fadeOut: Int = 10,
) : ActionSpec

data class BroadcastActionSpec(val message: String) : ActionSpec

data class CloseActionSpec(val bypassGuard: Boolean = true) : ActionSpec

data class RefreshActionSpec(val reopen: Boolean = false) : ActionSpec

data class BackActionSpec(val fallbackMenuId: String? = null) : ActionSpec

data class NextPageActionSpec(val amount: Int = 1) : ActionSpec

data class PrevPageActionSpec(val amount: Int = 1) : ActionSpec

data class SetPageActionSpec(val pageExpression: String, val zeroBased: Boolean = false) : ActionSpec

data class VaultActionSpec(val operation: VaultOperation, val amountExpression: String) : ActionSpec

data class PointsActionSpec(val operation: PointsOperation, val amountExpression: String) : ActionSpec

data class ItemActionSpec(val operation: ItemOperation, val itemId: String, val amount: Int) : ActionSpec

data class VariableActionSpec(val operation: VariableOperation, val key: String, val value: String? = null, val step: Double = 1.0) : ActionSpec

data class MetaActionSpec(val operation: MetaOperation, val key: String, val value: String? = null) : ActionSpec

data class DataActionSpec(val operation: DataOperation, val key: String, val value: String? = null) : ActionSpec

data class StopActionSpec(val raw: String = "return") : ActionSpec

data class ResetActionSpec(val clearState: Boolean = false) : ActionSpec

data class ConditionalActionSpec(
    val conditions: List<String>,
    val matchMode: ConditionMatchMode = ConditionMatchMode.ALL,
    val actions: List<ActionSpec>,
    val deny: List<ActionSpec>,
) : ActionSpec

data class UnknownActionSpec(val raw: String) : ActionSpec

data class BossBarActionSpec(
    val message: String,
    val color: String = "GREEN",
    val style: String = "SOLID",
    val progress: Double = 1.0,
    val durationTicks: Int = 60,
) : ActionSpec

data class TellrawActionSpec(val json: String) : ActionSpec

data class ConnectActionSpec(val server: String) : ActionSpec

data class CommandOpActionSpec(val command: String) : ActionSpec

data class RepairItemActionSpec(val slot: String = "hand") : ActionSpec

data class EnchantItemActionSpec(val enchantment: String, val level: Int = 1) : ActionSpec

data class SetArgumentsActionSpec(val arguments: List<String>) : ActionSpec

enum class VaultOperation {
    GIVE,
    TAKE,
    SET,
}

enum class PointsOperation {
    GIVE,
    TAKE,
    SET,
}

enum class ItemOperation {
    GIVE,
    TAKE,
}

enum class VariableOperation {
    SET,
    UNSET,
    INC,
    DEC,
}

enum class MetaOperation {
    SET,
    DELETE,
}

enum class DataOperation {
    SET,
    DELETE,
}

enum class ConditionMatchMode {
    ALL,
    ANY,
}

enum class CatcherType {
    CHAT,
    SIGN,
    UNKNOWN,
}

