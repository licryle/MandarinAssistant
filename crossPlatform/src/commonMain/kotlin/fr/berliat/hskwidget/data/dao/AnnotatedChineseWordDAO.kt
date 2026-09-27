package fr.berliat.hskwidget.data.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.RewriteQueriesToDropUnusedColumns
import androidx.room3.Transaction
import co.touchlab.kermit.Logger

import fr.berliat.hskwidget.core.ExpectedLogging
import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.model.AnnotatedChineseWord
import fr.berliat.hskwidget.data.model.WordDefinition

private const val select_left_join =
    "SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word_annotation AS a LEFT JOIN chinese_word AS w" +
            " ON a.a_simplified = w.simplified" +
            " "

private const val select_right_join =
    "SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word AS w LEFT JOIN chinese_word_annotation AS a" +
            " ON a.a_simplified = w.simplified" +
            " "

private const val order_by_logic =
    "ORDER BY ( " +
            "  (CASE WHEN simplified = :str OR a_simplified = :str THEN 100 ELSE 0 END) + " +
            "  (CASE WHEN simplified LIKE :str || '%' OR a_simplified LIKE :str || '%' THEN 50 ELSE 0 END) + " +
            "  (CASE WHEN traditional LIKE :str || '%' THEN 50 ELSE 0 END) + " +
            "  (CASE WHEN searchable_text = :str THEN 100 ELSE 0 END) + " +
            "  (CASE WHEN searchable_text LIKE :str || '%' THEN 50 ELSE 0 END) + " +
            "  (CASE WHEN a_searchable_text = :str THEN 100 ELSE 0 END) + " +
            "  (CASE WHEN a_searchable_text LIKE :str || '%' THEN 50 ELSE 0 END) + " +
            "  (CASE WHEN notes LIKE :str || '%' OR notes LIKE '% ' || :str || '%' OR notes LIKE '%;' || :str || '%' THEN 50 ELSE 0 END) + " +
            "  (CASE WHEN simplified LIKE '%' || :str || '%' OR a_simplified LIKE '%' || :str || '%' THEN 10 ELSE 0 END) + " +
            "  (CASE WHEN searchable_text LIKE '%' || :str || '%' THEN 10 ELSE 0 END) + " +
            "  (CASE WHEN a_searchable_text LIKE '%' || :str || '%' THEN 10 ELSE 0 END) " +
            ") DESC, popularity DESC, is_first_seen_null, first_seen DESC "

internal fun sanitizeFtsQuery(query: String): String {
    val escaped = query.replace("\"", "\"\"")
    return "\"$escaped\""
}

@Dao
interface AnnotatedChineseWordDAO {
    val TAG: String
        get() = "AnnotatedChineseWordDAO"

    @Query("SELECT * FROM (" +
            "  $select_left_join WHERE " +
            "    (0=:hasAnnotation OR (1=:hasAnnotation AND a.first_seen IS NOT NULL))" +
            "    AND (a.is_exam=:atExam OR :atExam IS NULL)" +
            "  UNION ALL " +
            "  $select_right_join WHERE a.a_simplified IS NULL AND (0=:hasAnnotation) AND (:atExam IS NULL)" +
            ") " +
            "ORDER BY is_first_seen_null, first_seen DESC, popularity DESC " +
            "LIMIT :pageSize OFFSET (:page * :pageSize)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getEmptySearchRows(hasAnnotation: Boolean, atExam: Boolean? = null, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord>

    @Query("SELECT * FROM (" +
            "  $select_left_join WHERE (" +
            "    :str = '' OR a.a_simplified IN (" +
            "      SELECT simplified FROM chinese_word_fts WHERE searchable_text MATCH :ftsQuery || '*' " +
            "      UNION SELECT simplified FROM word_definition_fts WHERE definition MATCH :ftsQuery || '*' AND language = :language " +
            "      UNION SELECT a_simplified AS simplified FROM chinese_word_annotation_fts WHERE a_searchable_text MATCH :ftsQuery || '*' " +
            "    )" +
            "  ) " +
            "  AND (0=:hasAnnotation OR (1=:hasAnnotation AND a.first_seen IS NOT NULL))" +
            "  AND (a.is_exam=:atExam OR :atExam IS NULL)" +
            "  UNION ALL " +
            "  $select_right_join WHERE a.a_simplified IS NULL AND (" +
            "    :str = '' OR w.simplified IN (" +
            "      SELECT simplified FROM chinese_word_fts WHERE searchable_text MATCH :ftsQuery || '*' " +
            "      UNION SELECT simplified FROM word_definition_fts WHERE definition MATCH :ftsQuery || '*' AND language = :language " +
            "      UNION SELECT a_simplified AS simplified FROM chinese_word_annotation_fts WHERE a_searchable_text MATCH :ftsQuery || '*' " +
            "    )" +
            "  ) " +
            "  AND (0=:hasAnnotation)" +
            "  AND (:atExam IS NULL)" +
            ") " +
            order_by_logic +
            " LIMIT :pageSize OFFSET (:page * :pageSize)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun searchFromStrLikeRows(str: String?, ftsQuery: String, language: String, hasAnnotation: Boolean, atExam: Boolean? = null, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord>

    @Query("SELECT * FROM (" +
            "  $select_left_join WHERE (" +
            "    :str = '' OR a.a_simplified LIKE :str || '%' OR simplified LIKE :str || '%' OR a_searchable_text LIKE :str || '%' OR searchable_text LIKE :str || '%'" +
            "    OR notes LIKE '%' || :str || '%' OR traditional LIKE '%' || :str || '%'" +
            "  ) " +
            "  AND (0=:hasAnnotation OR (1=:hasAnnotation AND a.first_seen IS NOT NULL))" +
            "  AND (a.is_exam=:atExam OR :atExam IS NULL)" +
            "  UNION ALL " +
            "  $select_right_join WHERE a.a_simplified IS NULL AND (" +
            "    :str = '' OR w.simplified LIKE :str || '%' OR searchable_text LIKE :str || '%'" +
            "    OR traditional LIKE '%' || :str || '%'" +
            "  ) " +
            "  AND (0=:hasAnnotation)" +
            "  AND (:atExam IS NULL)" +
            ") " +
            order_by_logic +
            " LIMIT :pageSize OFFSET (:page * :pageSize)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun searchFromStrLikeSimpleRows(str: String?, hasAnnotation: Boolean, atExam: Boolean? = null, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord>

    @Transaction
    suspend fun searchFromStrLike(str: String?, language: Locale, hasAnnotation: Boolean, atExam: Boolean? = null, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord> {
        val query = str?.trim() ?: ""

        if (query.isEmpty()) {
            return hydrate(getEmptySearchRows(hasAnnotation, atExam, page, pageSize))
        }

        val ftsQuery = sanitizeFtsQuery(query)

        // Use FTS search as primary (much faster)
        val ftsResults = try {
            searchFromStrLikeRows(query, ftsQuery, language.code, hasAnnotation, atExam, page, pageSize)
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "searchFromStrLike failed", throwable = e)
            ExpectedLogging.logCrashalytics(e)
            emptyList()
        }
        if (ftsResults.isNotEmpty()) return hydrate(ftsResults)

        // Fallback to simple LIKE if FTS returns nothing (e.g. index out of sync)
        return hydrate(searchFromStrLikeSimpleRows(query, hasAnnotation, atExam, page, pageSize))
    }

    @Query("SELECT * FROM (" +
            "       $select_left_join WHERE a.a_simplified IN (SELECT simplified FROM word_list_entry WHERE list_id IN (:listIds) AND simplified NOT IN (:bannedWords))" +
            " UNION ALL " +
            "$select_right_join WHERE a.a_simplified IS NULL AND w.simplified IN (SELECT simplified FROM word_list_entry WHERE list_id IN (:listIds) AND simplified NOT IN (:bannedWords))" +
            ") ORDER BY RANDOM() LIMIT 1")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getRandomWordFromListsRow(listIds: List<Long>, bannedWords: Array<String>): AnnotatedChineseWord?

    suspend fun getRandomWordFromLists(listIds: List<Long>, bannedWords: Array<String>): AnnotatedChineseWord? =
        getRandomWordFromListsRow(listIds, bannedWords)?.let { hydrate(listOf(it)).first() }

    @Query("SELECT * FROM (" +
            "SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word_annotation AS a INNER JOIN word_list_entry AS wle ON a.a_simplified = wle.simplified " +
            " INNER JOIN word_list AS wl ON wl.id = wle.list_id " +
            " LEFT JOIN chinese_word AS w ON a.a_simplified = w.simplified " +
            " WHERE wl.name = :listName " +
            " AND (0=:hasAnnotation OR (1=:hasAnnotation AND a.first_seen IS NOT NULL)) " +
            " UNION ALL " +
            " SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word AS w INNER JOIN word_list_entry AS wle ON w.simplified = wle.simplified " +
            " INNER JOIN word_list AS wl ON wl.id = wle.list_id " +
            " LEFT JOIN chinese_word_annotation AS a ON a.a_simplified = w.simplified " +
            " WHERE wl.name = :listName AND a.a_simplified IS NULL " +
            " AND (0=:hasAnnotation) " +
            ") " +
            "ORDER BY popularity DESC, is_first_seen_null, first_seen DESC " +
            "LIMIT :pageSize OFFSET (:page * :pageSize)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getEmptyWordListRows(listName: String, hasAnnotation: Boolean, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord>

    @Query("SELECT * FROM (" +
            "SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word_annotation AS a INNER JOIN word_list_entry AS wle ON a.a_simplified = wle.simplified " +
            " INNER JOIN word_list AS wl ON wl.id = wle.list_id " +
            " LEFT JOIN chinese_word AS w ON a.a_simplified = w.simplified " +
            " WHERE wl.name = :listName " +
            " AND (0=:hasAnnotation OR (1=:hasAnnotation AND a.first_seen IS NOT NULL)) " +
            " AND (:str = '' OR a.a_simplified IN (" +
            "      SELECT simplified FROM chinese_word_fts WHERE searchable_text MATCH :ftsQuery || '*' " +
            "      UNION SELECT simplified FROM word_definition_fts WHERE definition MATCH :ftsQuery || '*' AND language = :language " +
            "      UNION SELECT a_simplified AS simplified FROM chinese_word_annotation_fts WHERE a_searchable_text MATCH :ftsQuery || '*' " +
            "    ))" +
            " UNION ALL " +
            " SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word AS w  INNER JOIN word_list_entry AS wle ON w.simplified = wle.simplified " +
            " INNER JOIN word_list AS wl ON wl.id = wle.list_id " +
            " LEFT JOIN chinese_word_annotation AS a ON a.a_simplified = w.simplified " +
            " WHERE wl.name = :listName AND a.a_simplified IS NULL " +
            " AND (0=:hasAnnotation) " +
            " AND (:str = '' OR w.simplified IN (" +
            "      SELECT simplified FROM chinese_word_fts WHERE searchable_text MATCH :ftsQuery || '*' " +
            "      UNION SELECT simplified FROM word_definition_fts WHERE definition MATCH :ftsQuery || '*' AND language = :language " +
            "      UNION SELECT a_simplified AS simplified FROM chinese_word_annotation_fts WHERE a_searchable_text MATCH :ftsQuery || '*' " +
            "    ))" +
            ") " +
            order_by_logic +
            " LIMIT :pageSize OFFSET (:page * :pageSize)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun searchFromWordListRows(listName: String, str: String, ftsQuery: String, language: String, hasAnnotation: Boolean, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord>

    @Query("SELECT * FROM (" +
            "SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word_annotation AS a INNER JOIN word_list_entry AS wle ON a.a_simplified = wle.simplified " +
            " INNER JOIN word_list AS wl ON wl.id = wle.list_id " +
            " LEFT JOIN chinese_word AS w ON a.a_simplified = w.simplified " +
            " WHERE wl.name = :listName " +
            " AND (0=:hasAnnotation OR (1=:hasAnnotation AND a.first_seen IS NOT NULL)) " +
            " AND (:str = '' OR a.a_simplified LIKE :str || '%' OR w.simplified LIKE :str || '%' OR a.a_searchable_text LIKE :str || '%' OR w.searchable_text LIKE :str || '%'" +
            "      OR a.notes LIKE '%' || :str || '%' OR w.traditional LIKE '%' || :str || '%')" +
            " UNION ALL " +
            " SELECT a.a_simplified, w.simplified, " +
            " a.a_pinyins, a.notes, a.class_type, a.class_level, a.themes, a.first_seen, a.is_exam, a.a_searchable_text, " +
            " w.traditional, w.hsk_level, w.pinyins, w.popularity, " +
            " w.modality, w.examples, w.type, w.synonyms, w.antonym, w.collocations, w.searchable_text, " +
            " (a.first_seen IS NULL) AS is_first_seen_null " +
            " FROM chinese_word AS w  INNER JOIN word_list_entry AS wle ON w.simplified = wle.simplified " +
            " INNER JOIN word_list AS wl ON wl.id = wle.list_id " +
            " LEFT JOIN chinese_word_annotation AS a ON a.a_simplified = w.simplified " +
            " WHERE wl.name = :listName AND a.a_simplified IS NULL " +
            " AND (0=:hasAnnotation) " +
            " AND (:str = '' OR w.simplified LIKE :str || '%' OR w.searchable_text LIKE :str || '%'" +
            "      OR w.traditional LIKE '%' || :str || '%')" +
            ") " +
            order_by_logic +
            " LIMIT :pageSize OFFSET (:page * :pageSize)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun searchFromWordListSimpleRows(listName: String, str: String, hasAnnotation: Boolean, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord>

    @Transaction
    suspend fun searchFromWordList(listName: String, str: String, language: Locale, hasAnnotation: Boolean, page: Int = 0, pageSize: Int = 30): List<AnnotatedChineseWord> {
        val query = str.trim()
        if (query.isEmpty()) {
            return hydrate(getEmptyWordListRows(listName, hasAnnotation, page, pageSize))
        }

        val ftsQuery = sanitizeFtsQuery(query)
        val ftsResults = try {
            searchFromWordListRows(listName, query, ftsQuery, language.code, hasAnnotation, page, pageSize)
        } catch (e: Exception) {
            Logger.e(tag = TAG, messageString = "searchFromWordList failed", throwable = e)
            ExpectedLogging.logCrashalytics(e)
            emptyList()
        }
        if (ftsResults.isNotEmpty()) return hydrate(ftsResults)

        return hydrate(searchFromWordListSimpleRows(listName, query, hasAnnotation, page, pageSize))
    }

    suspend fun getAllAnnotated(): List<AnnotatedChineseWord> {
        return searchFromStrLike("", Locale.ENGLISH, hasAnnotation = true, atExam = null, 0, Int.MAX_VALUE)
    }

    suspend fun getAllAtExam(): List<AnnotatedChineseWord> {
        return searchFromStrLike("", Locale.ENGLISH, hasAnnotation = true, atExam = true, page = 0, pageSize = Int.MAX_VALUE)
    }

    @Query("$select_left_join WHERE a_simplified = :simplifiedWord" +
            " UNION " +
            "$select_right_join WHERE simplified = :simplifiedWord" +
            " LIMIT 1")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getFromSimplifiedRow(simplifiedWord: String?): AnnotatedChineseWord?

    suspend fun getFromSimplified(simplifiedWord: String?): AnnotatedChineseWord? =
        getFromSimplifiedRow(simplifiedWord)?.let { hydrate(listOf(it)).first() }

    @Query("$select_left_join WHERE a_simplified IN (:simplifiedWords)" +
            " UNION " +
            "$select_right_join WHERE simplified IN (:simplifiedWords)")
    @RewriteQueriesToDropUnusedColumns
    suspend fun getFromSimplifiedRows(simplifiedWords: List<String>): List<AnnotatedChineseWord>

    suspend fun getFromSimplified(simplifiedWords: List<String>): List<AnnotatedChineseWord> =
        hydrate(getFromSimplifiedRows(simplifiedWords))

    @Query("SELECT * FROM word_definition WHERE simplified IN (:simplifiedWords)")
    suspend fun definitionsForWords(simplifiedWords: List<String>): List<WordDefinition>

    private suspend fun hydrate(words: List<AnnotatedChineseWord>): List<AnnotatedChineseWord> {
        if (words.isEmpty()) return words
        val definitions = definitionsForWords(words.map { it.simplified }).groupBy { it.simplified }
        return words.map { item ->
            item.copy(word = item.word?.also { word ->
                word.definition = definitions[item.simplified].orEmpty()
                    .mapNotNull { definition -> Locale.fromCode(definition.language)?.let { it to definition.definition } }
                    .toMap()
            })
        }
    }
}
