package fr.berliat.hskwidget.data.model

import fr.berliat.hskwidget.data.type.ClassLevel
import fr.berliat.hskwidget.data.type.ClassType
import fr.berliat.hskwidget.data.type.Pinyins
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChineseWordAnnotationTest {

    @Test
    fun testConstructorAndProperties() {
        val pinyins = Pinyins("nǐ hǎo")
        val firstSeen = Instant.fromEpochMilliseconds(1672531200000L)
        val annotation = ChineseWordAnnotation(
            simplified = "你好",
            pinyins = pinyins,
            notes = "important note",
            classType = ClassType.Speaking,
            level = ClassLevel.Elementary1,
            themes = "greeting",
            firstSeen = firstSeen,
            isExam = true
        )
        assertEquals(true, annotation.isExam)
        assertEquals(pinyins, annotation.pinyins)
        assertEquals("important note", annotation.notes)
        assertEquals(ClassType.Speaking, annotation.classType)
        assertEquals(ClassLevel.Elementary1, annotation.level)
        assertEquals("greeting", annotation.themes)
        assertEquals(firstSeen, annotation.firstSeen)
        assertEquals(true, annotation.isExam)
    }

    @Test
    fun testConstructorThrowsOnEmptySimplified() {
        assertFailsWith<WordMissingSimplifiedException> {
            ChineseWordAnnotation(
                simplified = "",
                pinyins = null,
                notes = null,
                classType = null,
                level = null,
                themes = null,
                firstSeen = null,
                isExam = null
            )
        }
        
        assertFailsWith<WordMissingSimplifiedException> {
            ChineseWordAnnotation.getBlank("")
        }
    }

    @Test
    fun testGetBlank() {
        val annotation = ChineseWordAnnotation.getBlank("你好")
        assertEquals("你好", annotation.simplified)
        assertNull(annotation.pinyins)
        assertEquals("", annotation.notes)
        assertEquals(ClassType.NotFromClass, annotation.classType)
        assertEquals(ClassLevel.NotFromClass, annotation.level)
        assertNull(annotation.firstSeen)
        assertEquals(false, annotation.isExam)
    }

    @Test
    fun testWithSearchableText() {
        val annotation = ChineseWordAnnotation(
            simplified = "你好",
            pinyins = Pinyins("nǐ hǎo"),
            notes = "Greeting",
            classType = ClassType.NotFromClass,
            level = ClassLevel.NotFromClass,
            themes = "Social",
            firstSeen = null,
            isExam = false
        ).withSearchableText()

        val st = annotation.searchableText
        assertTrue(st.contains("你好"))
        assertTrue(st.contains("你 好"))
        assertTrue(st.contains("ni hao"))
        assertTrue(st.contains("nihao"))
        assertTrue(st.contains("greeting"))
    }

    private fun staleAnnotation() = ChineseWordAnnotation(
        simplified = "资本主义陷阱",
        pinyins = null,
        notes = "my note",
        classType = ClassType.NotFromClass,
        level = ClassLevel.NotFromClass,
        themes = "",
        firstSeen = null,
        isExam = false
    )

    @Test
    fun testWithRefreshedSearchableText_healsStaleRow() {
        val stale = staleAnnotation()
        val healed = listOf(stale).withRefreshedSearchableText()
        assertEquals(1, healed.size)
        // Untouched fields are preserved.
        assertEquals(stale.notes, healed[0].notes)
        assertEquals(stale.simplified, healed[0].simplified)
    }

    @Test
    fun testWithRefreshedSearchableText_emptyList() {
        assertEquals(emptyList(), emptyList<ChineseWordAnnotation>().withRefreshedSearchableText())
    }
}
