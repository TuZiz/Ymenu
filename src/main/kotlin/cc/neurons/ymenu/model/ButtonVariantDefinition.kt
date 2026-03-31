package cc.neurons.ymenu.model

data class ButtonVariantDefinition(
    val condition: String,
    val priority: Int = 0,
    val updateTicks: Long? = null,
    val display: DisplaySpec? = null,
    val actionsByClick: Map<ClickType, List<ActionSpec>> = emptyMap(),
)
