package fr.berliat.hskwidget.data.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import fr.berliat.hskwidget.data.type.Pinyins
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "chinese_word_annotation")
data class ChineseWordAnnotation (
    @PrimaryKey @ColumnInfo(name = "a_simplified") val simplified: String = "",
    @ColumnInfo(name = "a_pinyins") val pinyins: Pinyins?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "class_type") val classType: ClassType?,
    @ColumnInfo(name = "class_level") val level: ClassLevel?,
    @ColumnInfo(name = "themes") val themes: String?,

    @ColumnInfo(name = "first_seen") val firstSeen: Instant?,
    @ColumnInfo(name = "is_exam") val isExam: Boolean?,
    @ColumnInfo(name = "a_searchable_text", defaultValue = "") val searchableText: String = ""
) {
    init {
        if (simplified.isBlank()) {
            throw WordMissingSimplifiedException()
        }
    }

    fun withSearchableText(): ChineseWordAnnotation {
        val toneless = pinyins?.toString()?.let { fr.berliat.pinyin4kot.Hanzi2Pinyin().pinyinToToneless(it) } ?: ""
        val concatenated = toneless.replace(" ", "")
        val hanziSplit = simplified.map { it.toString() }.joinToString(" ")

        val text = listOfNotNull(simplified, hanziSplit, notes, toneless, concatenated)
            .joinToString(" ")
            .lowercase()
        return copy(searchableText = text)
    }

    companion object {
        fun getBlank(simplified: String = ""): ChineseWordAnnotation {
            return ChineseWordAnnotation(simplified, null, "", ClassType.NotFromClass,
                ClassLevel.NotFromClass, "", null, false)
        }
    }
}

// Recomputes the derived FTS index text for imported/stored annotations.
// Rows whose index is empty or stale become searchable again without changing
// anything else. Falls back to the row as-is on any failure.
internal fun List<ChineseWordAnnotation>.withRefreshedSearchableText(): List<ChineseWordAnnotation> =
    map { annotation ->
        try {
            annotation.withSearchableText()
        } catch (_: Exception) {
            annotation
        }
    }
