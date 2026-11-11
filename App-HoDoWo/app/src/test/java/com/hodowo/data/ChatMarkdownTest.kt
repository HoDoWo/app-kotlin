package com.hodowo.data

import com.hodowo.data.MdStyle.BOLD
import com.hodowo.data.MdStyle.CODE
import com.hodowo.data.MdStyle.HEADING
import com.hodowo.data.MdStyle.ITALIC
import com.hodowo.data.MdStyle.LINK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Markdown of the AI answers is shown as styled text, without the raw markers. */
class ChatMarkdownTest {

    private fun text(spans: List<MdSpan>) = spans.joinToString("") { it.text }

    @Test
    fun boldItalicCodeAndLinksLoseTheirMarkers() {
        val spans = ChatMarkdown.parse("Press **Start**, then *wait* for `E18` (see [the manual](https://x.test/m)).")

        assertEquals("Press Start, then wait for E18 (see the manual).", text(spans))
        assertEquals(
            listOf(
                MdSpan("Press "), MdSpan("Start", setOf(BOLD)), MdSpan(", then "),
                MdSpan("wait", setOf(ITALIC)), MdSpan(" for "), MdSpan("E18", setOf(CODE)),
                MdSpan(" (see "), MdSpan("the manual", setOf(LINK)), MdSpan(").")
            ),
            spans
        )
    }

    @Test
    fun headingsListsQuotesAndRules() {
        val spans = ChatMarkdown.parse(
            """
            ### Quick wash
            - Turn the **dial**
              * to Rapido 30
            1. Press start
            2) Wait
            > Keep the door closed
            ---
            Done
            """.trimIndent()
        )

        assertEquals(
            "Quick wash\n•  Turn the dial\n  •  to Rapido 30\n1. Press start\n2. Wait\nKeep the door closed\n\nDone",
            text(spans)
        )
        assertEquals(MdSpan("Quick wash", setOf(BOLD, HEADING)), spans.first())
        assertTrue(MdSpan("dial", setOf(BOLD)) in spans)
        assertTrue(MdSpan("Keep the door closed", setOf(ITALIC)) in spans)
    }

    @Test
    fun boldInsideAHeadingKeepsBoth() {
        assertEquals(listOf(MdSpan("Step 1", setOf(BOLD, HEADING))), ChatMarkdown.parse("## **Step 1**"))
    }

    @Test
    fun anUnclosedMarkerStaysWhileStreaming() {
        assertEquals("Press **Sta", text(ChatMarkdown.parse("Press **Sta")))
        assertEquals("2 * 3 = 6", text(ChatMarkdown.parse("2 * 3 = 6")))
        assertEquals("file_name_here", text(ChatMarkdown.parse("file_name_here")))
    }

    @Test
    fun plainTextIsOneSpan() {
        assertEquals(listOf(MdSpan("Hello there")), ChatMarkdown.parse("Hello there"))
        assertEquals(emptyList<MdSpan>(), ChatMarkdown.parse(""))
    }
}
