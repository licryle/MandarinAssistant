package fr.berliat.hskwidget.data.model

import fr.berliat.hskwidget.core.Locale
import fr.berliat.hskwidget.data.type.HSK_Level
import fr.berliat.hskwidget.data.type.Pinyins
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnotatedChineseWordTest {

    @Test
    fun testGetBlank() {
        val annotated = AnnotatedChineseWord.getBlank("测试")
        assertEquals("测试", annotated.simplified)
        assertNotNull(annotated.word)
        assertNotNull(annotated.annotation)
        assertEquals("测试", annotated.word.simplified)
        assertEquals("测试", annotated.annotation.simplified)
    }

    @Test
    fun testSimplifiedProperty() {
        val word = ChineseWord.getBlank("词")
        val annotation = ChineseWordAnnotation.getBlank("注")

        val annotatedWithWord = AnnotatedChineseWord(word.copy(hskLevel = HSK_Level.HSK1), null)
        assertEquals("词", annotatedWithWord.simplified)
        
        val annotatedWithAnnotation = AnnotatedChineseWord(null, annotation)
        assertEquals("注", annotatedWithAnnotation.simplified)
    }

    @Test
    fun testPinyinsProperty() {
        val word = ChineseWord.getBlank("词").copy(pinyins = Pinyins("cí"), hskLevel = HSK_Level.HSK1)
        val annotation = ChineseWordAnnotation.getBlank("注").copy(pinyins = Pinyins("zhù"))
        
        val annotatedWithWord = AnnotatedChineseWord(word, null)
        assertEquals("cí", annotatedWithWord.pinyins.toString())
        
        val annotatedWithAnnotation = AnnotatedChineseWord(null, annotation)
        assertEquals("zhù", annotatedWithAnnotation.pinyins.toString())
        
        val annotatedEmpty = AnnotatedChineseWord(null, null)
        assertEquals("", annotatedEmpty.pinyins.toString())
    }

    @Test
    fun testHskLevelProperty() {
        val word = ChineseWord.getBlank("词").copy(hskLevel = HSK_Level.HSK3)
        
        val annotatedWithWord = AnnotatedChineseWord(word, null)
        assertEquals(HSK_Level.HSK3, annotatedWithWord.hskLevel)
        
        val annotatedWithoutWord = AnnotatedChineseWord(null, null)
        assertEquals(HSK_Level.NOT_HSK, annotatedWithoutWord.hskLevel)
    }

    @Test
    fun testHasAnnotation() {
        val annotatedEmpty = AnnotatedChineseWord(null, null)
        assertFalse(annotatedEmpty.hasAnnotation())
        
        val annotation = ChineseWordAnnotation.getBlank("测")
        assertFalse(AnnotatedChineseWord(null, annotation).hasAnnotation())
        
        val annotationWithDate = annotation.copy(firstSeen = Instant.fromEpochMilliseconds(1000))
        assertTrue(AnnotatedChineseWord(null, annotationWithDate).hasAnnotation())
    }

    @Test
    fun testHasWord() {
        val annotatedEmpty = AnnotatedChineseWord(null, null)
        assertFalse(annotatedEmpty.hasWord())
        
        val word = ChineseWord.getBlank("测")
        assertFalse(AnnotatedChineseWord(word, null).hasWord())
        
        val wordWithHSK = word.copy(hskLevel = HSK_Level.HSK1)
        assertTrue(AnnotatedChineseWord(wordWithHSK, null).hasWord())
    }

    @Test
    fun testToChineseWord() {
        val word = ChineseWord.getBlank("词").copy(hskLevel = HSK_Level.HSK1)
        val annotated = AnnotatedChineseWord(word, null)
        assertEquals(word, annotated.toChineseWord())
        
        val annotation = ChineseWordAnnotation.getBlank("注").copy(
            notes = "Some note", 
            firstSeen = Instant.fromEpochMilliseconds(1000)
        )
        val annotatedFromAnnotation = AnnotatedChineseWord(null, annotation)
        val converted = annotatedFromAnnotation.toChineseWord()
        assertNotNull(converted)
        assertEquals("注", converted.simplified)
        assertEquals("Some note", annotatedFromAnnotation.annotation?.notes)
        assertEquals(HSK_Level.NOT_HSK, converted.hskLevel)
        
        val annotatedEmpty = AnnotatedChineseWord(null, null)
        assertNull(annotatedEmpty.toChineseWord())
    }

    @Test
    fun testSimplifiedProperty_throwsWhenNoSimplifiedAvailable() {
        val annotatedWithBothNull = AnnotatedChineseWord(null, null)
        assertFailsWith<WordMissingSimplifiedException> {
            annotatedWithBothNull.simplified
        }
    }
}
