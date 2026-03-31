package cc.neurons.ymenu.model

data class DisplaySpec(
    val material: String? = null,
    val mat: String? = null,
    val mats: String? = null,
    val name: String? = null,
    val lore: List<String> = emptyList(),
    val amount: Int = 1,
    val shiny: Boolean = false,
    val shinyCondition: String? = null,
) {
    val itemId: String?
        get() = mats ?: mat ?: material
}
