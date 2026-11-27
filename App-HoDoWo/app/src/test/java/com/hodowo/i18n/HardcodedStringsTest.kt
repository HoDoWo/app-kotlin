package com.hodowo.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fails when a user-facing text is written in the code instead of strings.xml (#230),
 * so a translated screen cannot quietly fall back to English. Every slice of the
 * string extraction (#230, #231, #232) adds its files to [TRANSLATED_FILES].
 * See docs/i18n.md.
 */
class HardcodedStringsTest {

    @Test
    fun translatedScreensHaveNoHardCodedTexts() {
        val found = TRANSLATED_FILES.flatMap { path ->
            val file = File(SOURCE_ROOT, path)
            assertTrue("$path does not exist", file.isFile)
            hardCodedTexts(file.readText()).map { "$path: $it" }
        }
        assertTrue("Move these texts to strings.xml:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test
    fun theCheckFindsTheUsualKindsOfText() {
        val code = """
            Text("Sign out")
            Text(
                "Welcome back",
            )
            label = { Text("Email") }
            contentDescription = "Show password",
            title = "Account"
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT)
            Text(if (isRegister) "Create account" else "Log in")
        """.trimIndent()
        assertTrue(hardCodedTexts(code).size >= 8)
    }

    @Test
    fun resourcesAndNonTextsAreAllowed() {
        val code = """
            Text(stringResource(R.string.profile_sign_out))
            contentDescription = null
            id = "active_session"
            SimpleDateFormat("yyyy", locale)
            Text("${'$'}{step.number}")
            Text("•")
        """.trimIndent()
        assertTrue(hardCodedTexts(code).toString(), hardCodedTexts(code).isEmpty())
    }

    private companion object {
        const val SOURCE_ROOT = "src/main/java/com/hodowo"

        val TRANSLATED_FILES = listOf(
            "ui/AuthScreen.kt",
            "ui/AuthDialog.kt",
            "ui/AuthMessages.kt",
            "ui/ProfileScreen.kt",
            "ui/SettingsScreen.kt"
        )

        /** A literal with at least two letters in a row: a word, not a symbol or a template. */
        private const val WORD = "\"(?:[^\"\\\\$]|\\\\.)*[A-Za-z]{2}(?:[^\"\\\\]|\\\\.)*\""

        private val PATTERNS = listOf(
            Regex("""\bText\(\s*$WORD"""),
            Regex("""\b(?:contentDescription|text|title|label|placeholder|subtitle|message)\s*=\s*$WORD"""),
            Regex("""Toast\.makeText\([^,]+,\s*$WORD"""),
            Regex("""\bif \([^)]*\)\s*$WORD\s*else"""),
            Regex("""\belse\s*$WORD""")
        )

        fun hardCodedTexts(code: String): List<String> =
            PATTERNS.flatMap { pattern -> pattern.findAll(code).map { it.value.replace(Regex("\\s+"), " ") } }
    }
}
