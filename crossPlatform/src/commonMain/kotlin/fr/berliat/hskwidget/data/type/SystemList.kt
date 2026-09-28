package fr.berliat.hskwidget.data.type

/**
 * System lists seeded in DB (see database_generation/inputs/1_HSK/provider.py
 * and 4_Annotations/provider.py).
 *
 * The [dbName] values are the reference: they must exactly match the `name`
 * column in the `word_list` table.
 */
enum class SystemList(val dbName: String) {
    HSK1("HSK1"),
    HSK2("HSK2"),
    HSK3("HSK3"),
    HSK4("HSK4"),
    HSK5("HSK5"),
    HSK6("HSK6"),
    HSK7("HSK7"),
    ANNOTATED_WORDS("Annotated words"),
    AT_EXAM("At the exam");

    companion object {
        fun fromDbName(dbName: String): SystemList? =
            SystemList.entries.find { it.dbName == dbName }

        infix fun from(dbName: String): SystemList? = fromDbName(dbName)
    }
}
