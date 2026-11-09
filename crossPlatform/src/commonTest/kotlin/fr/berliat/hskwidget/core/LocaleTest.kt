package fr.berliat.hskwidget.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleTest {

    @Test
    fun testFromCode() {
        assertEquals(Locale.ENGLISH, Locale.fromCode("en"))
        assertEquals(Locale.SPANISH, Locale.fromCode("es"))
        assertEquals(Locale.FRENCH, Locale.fromCode("fr"))
        assertEquals(Locale.CN_HSK3, Locale.fromCode("zh-CN-HSK03"))
        assertEquals(null, Locale.fromCode("unknown"))
    }

    @Test
    fun testGetDefault() {
        assertEquals(Locale.ENGLISH, Locale.getDefault())
    }

    @Test
    fun testSerialization() {
        val json = Json
        assertEquals("\"en\"", json.encodeToString(LocaleSerializer, Locale.ENGLISH))
        assertEquals("\"zh-CN-HSK03\"", json.encodeToString(LocaleSerializer, Locale.CN_HSK3))
    }

    @Test
    fun testDeserialization() {
        val json = Json
        assertEquals(Locale.ENGLISH, json.decodeFromString(LocaleSerializer, "\"en\""))
        assertEquals(Locale.CN_HSK3, json.decodeFromString(LocaleSerializer, "\"zh-CN-HSK03\""))
    }

    @Test
    fun testDeserializationDefault() {
        val json = Json
        assertEquals(Locale.ENGLISH, json.decodeFromString(LocaleSerializer, "\"unknown\""))
    }

    @Test
    fun testResolve() {
        // Test explicit preference
        assertEquals(Locale.FRENCH, Locale.resolve(Locale.FRENCH))
        assertEquals(Locale.ENGLISH, Locale.resolve(Locale.ENGLISH))

        // Test fallback to system/default (smoke test as system depends on platform)
        val resolved = Locale.resolve(null)
        // At least it should return a non-null Locale
        kotlin.test.assertNotNull(resolved)
    }
}
