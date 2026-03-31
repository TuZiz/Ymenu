package cc.neurons.ymenu.parser

class LayoutTokenizer {
    fun tokenize(row: String): List<String> {
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < row.length) {
            val char = row[index]
            if (char == '`') {
                val endIndex = row.indexOf('`', startIndex = index + 1)
                require(endIndex != -1) { "Unclosed backtick token in row: $row" }
                tokens += row.substring(index + 1, endIndex)
                index = endIndex + 1
                continue
            }

            tokens += char.toString()
            index++
        }
        return tokens
    }
}
