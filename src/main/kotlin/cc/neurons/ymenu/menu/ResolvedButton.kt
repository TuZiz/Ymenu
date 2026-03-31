package cc.neurons.ymenu.menu

import cc.neurons.ymenu.model.ActionSpec
import cc.neurons.ymenu.model.ClickType
import cc.neurons.ymenu.model.DisplaySpec

data class ResolvedButton(
    val key: String,
    val display: DisplaySpec,
    val updateTicks: Long?,
    val actionsByClick: Map<ClickType, List<ActionSpec>>,
    val context: Map<String, String> = emptyMap(),
)
