package cc.neurons.ymenu.model

data class ConditionSpec(
    val source: ConditionSource,
    val expression: String,
    val operator: ComparisonOperator? = null,
    val expected: String? = null,
    val arguments: List<String> = emptyList(),
    val negate: Boolean = false,
)

enum class ConditionSource {
    PAPI,
    VAR,
    STRING,
    NUMBER,
    PERMISSION,
    OP,
    ITEM,
    SPACE,
    SLOT,
    COOLDOWN,
    LIMIT,
}

enum class ComparisonOperator(val token: String) {
    GREATER_OR_EQUAL(">="),
    LESS_OR_EQUAL("<="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER(">"),
    LESS("<"),
    CONTAINS("contains"),
    STARTS_WITH("startsWith"),
    ENDS_WITH("endsWith"),
    MATCHES("matches"),
}
