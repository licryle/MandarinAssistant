package fr.berliat.hskwidget.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import fr.berliat.hskwidget.data.model.ChineseWordFrequency

@Dao
interface ChineseWordFrequencyDAO {
    companion object {
        const val CHINESE_REGEX = "^[\\u4E00-\\u9FFF]+\$"
    }

    @Query("SELECT * FROM chinese_word_frequency")
    suspend fun getAll(): List<ChineseWordFrequency>

    @Query("SELECT * FROM chinese_word_frequency WHERE simplified = :simplifiedWord")
    suspend fun getFrequency(simplifiedWord: String?): ChineseWordFrequency?

    @Query("SELECT * FROM chinese_word_frequency WHERE simplified IN (:simplifiedWords)")
    suspend fun getFrequency(simplifiedWords: List<String>): List<ChineseWordFrequency>

    suspend fun getFrequencyMapped(simplifiedWords: List<String>): Map<String, ChineseWordFrequency> {
        val current = getFrequency(simplifiedWords)
        return current.associateBy { it.simplified }
    }

    @Insert
    suspend fun insertAll(annotations: List<ChineseWordFrequency>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(freq: ChineseWordFrequency)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(freq: List<ChineseWordFrequency>)

    @Query("DELETE FROM chinese_word_frequency")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM chinese_word_frequency")
    suspend fun getCount(): Int
}