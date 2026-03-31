package cc.neurons.ymenu.model

data class ButtonDefinition(
    val key: String,
    val updateTicks: Long?,
    val display: DisplaySpec,
    val actionsByClick: Map<ClickType, List<ActionSpec>>,
    val viewConditions: List<String> = emptyList(),
    val clickCooldownTicks: Long? = null,
    val buttonType: ButtonType = ButtonType.NORMAL,
    val pageTarget: Int? = null,
    val variants: List<ButtonVariantDefinition> = emptyList(),
)
