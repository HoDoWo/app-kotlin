package com.hodowo.data

/** Style of a piece of an AI answer, from its Markdown. */
enum class MdStyle { BOLD, ITALIC, CODE, LINK, HEADING }

/** A piece of text and the styles it has; [text] never contains Markdown markers. */
data class MdSpan(val text: String, val styles: Set<MdStyle> = emptySet())

/**
 * The little Markdown the AI writes in the chat, turned into styled text instead
 * of showing the raw markers ("**Power On**", "### Steps", "- item").
 *
 * Lines: `#` headings, `-` / `*` / `+` bullets (shown as "•"), numbered items,
 * `>` quotes and `---` rules. Inline: `**bold**`, `__bold__`, `*italic*`,
 * `` `code` `` and `[text](url)` (the text only). A marker that is not closed,
 * for example while the answer is still streaming, stays as it is.
 */
object ChatMarkdown {

    private val HEADING = Regex("""^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
    private val QUOTE = Regex("""^\s{0,3}>\s?(.*)$""")
    private val RULE = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val INLINE = Regex(
        """\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)|(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])"""
    )

    /** The answer as styled pieces; lines are joined with "\n". */
    fun parse(text: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        text.lines().forEachIndexed { index, line ->
            if (index > 0) spans += MdSpan("\n")
            spans += parseLine(line)
        }
        return merge(spans)
    }

    private fun parseLine(line: String): List<MdSpan> {
        if (RULE.matches(line)) return emptyList()
        HEADING.matchEntire(line)?.let { return inlineSpans(it.groupValues[1], setOf(MdStyle.BOLD, MdStyle.HEADING)) }
        BULLET.matchEntire(line)?.let {
            val indent = "  ".repeat(it.groupValues[1].length / 2)
            return listOf(MdSpan("$indent•  ")) + inlineSpans(it.groupValues[2])
        }
        NUMBERED.matchEntire(line)?.let {
            val indent = "  ".repeat(it.groupValues[1].length / 2)
            return listOf(MdSpan("$indent${it.groupValues[2]}. ")) + inlineSpans(it.groupValues[3])
        }
        QUOTE.matchEntire(line)?.let { return inlineSpans(it.groupValues[1], setOf(MdStyle.ITALIC)) }
        return inlineSpans(line)
    }

    /** Inline markers of one line; [base] styles apply to all of it. */
    private fun inlineSpans(text: String, base: Set<MdStyle> = emptySet()): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        var last = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > last) spans += MdSpan(text.substring(last, match.range.first), base)
            // Every group of INLINE needs at least one character, so "" means "not this one".
            val (bold, underscoreBold, code, link, _, italic) = match.destructured
            spans += when {
                bold.isNotEmpty() -> inlineSpans(bold, base + MdStyle.BOLD)
                underscoreBold.isNotEmpty() -> inlineSpans(underscoreBold, base + MdStyle.BOLD)
                code.isNotEmpty() -> listOf(MdSpan(code, base + MdStyle.CODE))
                link.isNotEmpty() -> listOf(MdSpan(link, base + MdStyle.LINK))
                else -> inlineSpans(italic, base + MdStyle.ITALIC)
            }
            last = match.range.last + 1
        }
        if (last < text.length) spans += MdSpan(text.substring(last), base)
        return spans
    }

    /** Joins neighbours with the same styles, so the text has as few pieces as possible. */
    private fun merge(spans: List<MdSpan>): List<MdSpan> {
        val merged = mutableListOf<MdSpan>()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val previous = merged.lastOrNull()
            if (previous != null && previous.styles == span.styles) {
                merged[merged.lastIndex] = previous.copy(text = previous.text + span.text)
            } else {
                merged += span
            }
        }
        return merged
    }
}
