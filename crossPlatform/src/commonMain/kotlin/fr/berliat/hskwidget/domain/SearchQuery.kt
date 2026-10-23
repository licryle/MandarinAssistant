package fr.berliat.hskwidget.domain

data class SearchQuery(
    val query: String = "",
    val ignoreAnnotation: Boolean = false,
    val exactWord: Boolean = false,
    val inListName: String? = null
) {
    override fun toString(): String {
        val params = mutableListOf(query)

        if (inListName != null) params.add("""list:"$inListName"""")
        if (ignoreAnnotation) params.add("""ignAnnot:""""")
        if (exactWord) params.add("""exactWord:""""")

        return params.joinToString(" ")
    }

    companion object {
        private const val FLAG_REGEX = """(\w+):"([^"]*)""""

        fun fromString(query: String?): SearchQuery {
            return processSearchQuery(query ?: "")
        }

        fun processSearchQuery(query: String): SearchQuery {
            val flags = extractFlags(query)
            val baseQuery = extractQuery(query)

            var inListName: String? = null
            var ignoreAnnotation = false
            var exactWord = false

            flags.forEach { (flag, value) ->
                when (flag) {
                    "list" -> inListName = value
                    "ignAnnot" -> ignoreAnnotation = true
                    "exactWord" -> exactWord = true
                }
            }

            return SearchQuery(
                query = baseQuery,
                ignoreAnnotation = ignoreAnnotation,
                exactWord = exactWord,
                inListName = inListName
            )
        }

        fun extractFlags(input: String): Map<String, String> {
            // Regex matches:
            //   name:"value with spaces"
            //   name:valueWithoutSpaces
            val regex = Regex(FLAG_REGEX)

            return regex.findAll(input).associate { match ->
                val name = match.groupValues[1]
                // group 2 is the quoted value
                val value = match.groupValues[2]
                name to value
            }
        }

        fun extractQuery(input: String): String {
            val regex = Regex(FLAG_REGEX)
            return input.replace(regex, "").replace(Regex("\\s+"), " ").trim()
        }
    }
}