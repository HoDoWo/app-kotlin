package com.hodowo.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    /** Keeps the tags in memory and counts the changes (each one recreates the activity). */
    private class FakeLocales(var tags: String = "") : AppLocales {
        var changes = 0
        override fun get() = tags
        override fun set(tags: String) {
            this.tags = tags
            changes++
        }
    }

    private val fake = FakeLocales().also { AppLanguage.locales = it }

    @After
    fun restore() {
        AppLanguage.locales = AppCompatLocales
    }

    @Test
    fun aNewLanguageIsSet() {
        AppLanguage.apply("it")
        assertEquals("it", fake.tags)
        assertEquals(1, fake.changes)
    }

    @Test
    fun theSameLanguageIsNotSetAgain() {
        fake.tags = "es"
        AppLanguage.apply("es")
        assertEquals(0, fake.changes)
    }

    @Test
    fun anEmptyTagGoesBackToTheSystemLanguage() {
        fake.tags = "ca"
        AppLanguage.apply("")
        assertEquals("", fake.tags)
        assertEquals(1, fake.changes)
    }

    @Test
    fun theCurrentLanguageIsTheSupportedOneShown() {
        fake.tags = "it-IT,en-US"
        assertEquals("it", AppLanguage.current())
        fake.tags = "fr-FR"
        assertEquals("", AppLanguage.current())
        fake.tags = ""
        assertEquals("", AppLanguage.current())
    }
}
