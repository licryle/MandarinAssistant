package fr.berliat.hskwidget.data.dao

import kotlin.test.Test
import kotlin.test.assertEquals

class AnnotatedChineseWordDAOTest {

    @Test
    fun testSanitizeFtsQuery_dash() {
        val result = sanitizeFtsQuery("-")
        assertEquals("\"-\"", result)
    }

    @Test
    fun testSanitizeFtsQuery_wordWithDash() {
        val result = sanitizeFtsQuery("test-word")
        assertEquals("\"test-word\"", result)
    }

    @Test
    fun testSanitizeFtsQuery_doubleQuotes() {
        val result = sanitizeFtsQuery("say \"hello\"")
        assertEquals("\"say \"\"hello\"\"\"", result)
    }

    @Test
    fun testSanitizeFtsQuery_specialFtsOperators() {
        val result = sanitizeFtsQuery("(a+b):c")
        assertEquals("\"(a+b):c\"", result)
    }

    @Test
    fun testSanitizeFtsQuery_normalWord() {
        val result = sanitizeFtsQuery("ni3hao3")
        assertEquals("\"ni3hao3\"", result)
    }
}
