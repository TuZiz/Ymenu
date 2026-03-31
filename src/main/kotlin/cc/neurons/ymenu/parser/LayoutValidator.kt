package cc.neurons.ymenu.parser

class LayoutValidator(
    private val tokenizer: LayoutTokenizer = LayoutTokenizer(),
) {
    fun validate(rows: List<String>, menuId: String): List<List<String>> {
        require(rows.isNotEmpty()) { "Menu $menuId must declare at least one layout row." }
        require(rows.size <= 6) { "Menu $menuId has too many rows (${rows.size}); Bukkit inventories support at most 6 rows." }

        return rows.mapIndexed { index, row ->
            val tokens = tokenizer.tokenize(row)
            require(tokens.size <= 9) {
                "Menu $menuId row ${index + 1} must resolve to 9 slots, but got ${tokens.size}: $row"
            }
            if (tokens.size == 9) {
                tokens
            } else {
                tokens + List(9 - tokens.size) { " " }
            }
        }
    }
}
