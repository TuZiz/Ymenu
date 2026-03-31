package cc.neurons.ymenu.model

data class MenuDefinition(
    val id: String,
    val title: String,
    val titleUpdateTicks: Long? = null,
    val layoutRows: List<String>,
    val layoutTokens: List<List<String>>,
    val layoutPages: List<List<List<String>>> = listOf(layoutTokens),
    val buttons: Map<String, ButtonDefinition>,
    val commandBindings: List<String> = emptyList(),
    val pageSpec: PageSpec? = null,
    val openActions: List<ActionSpec> = emptyList(),
    val closeActions: List<ActionSpec> = emptyList(),
    val closeDenyConditions: List<String> = emptyList(),
    val closeDenyActions: List<ActionSpec> = emptyList(),
    val functions: Map<String, String> = emptyMap(),
) {
    val size: Int = layoutForPage(1).size * 9

    val totalLayoutPages: Int = layoutPages.size.coerceAtLeast(1)

    fun layoutForPage(page: Int): List<List<String>> {
        val safePages = if (layoutPages.isEmpty()) listOf(layoutTokens) else layoutPages
        return safePages[(page.coerceAtLeast(1) - 1).coerceAtMost(safePages.lastIndex)]
    }
}
