package me.mudkip.moememos.widget

/**
 * Converts memo markdown into plain text plus style ranges for the widgets, which cannot use the
 * app's Compose markdown renderer. Line based: headings, lists, task items, quotes, code fences and
 * rules, plus inline bold, italic, strikethrough, code, links and images. Tags stay as written.
 */
object WidgetMarkdown {
    sealed class Style {
        object Bold : Style()
        object Italic : Style()
        object Strikethrough : Style()
        object Code : Style()
        data class Heading(val level: Int) : Style()
    }

    data class Span(val start: Int, val end: Int, val style: Style)

    data class StyledText(val text: String, val spans: List<Span>)

    private val fence = Regex("^\\s{0,3}(```|~~~)")
    private val heading = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)(\\s+#+)?\\s*$")
    private val task = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d+)([.)])\\s+(.*)$")
    private val quote = Regex("^\\s{0,3}>\\s?(.*)$")
    private val rule = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!~>|"
    // Escaped characters are swapped for private-use code points while parsing, then restored
    private const val ESCAPE_BASE = 0xE000

    fun render(markdown: String): StyledText {
        val out = Builder()
        var inFence = false
        var previousBlank = true
        val lines = protectEscapes(markdown).replace("\r\n", "\n").split('\n')

        for (line in lines) {
            if (fence.containsMatchIn(line)) {
                inFence = !inFence
                continue
            }
            if (inFence) {
                out.newLine()
                out.styled(Style.Code) { out.append(line) }
                previousBlank = false
                continue
            }
            if (line.isBlank()) {
                // Collapse runs of blank lines into one, and drop leading ones
                if (!previousBlank) {
                    out.newLine()
                }
                previousBlank = true
                continue
            }
            out.newLine()
            previousBlank = false

            heading.matchEntire(line)?.let { match ->
                out.styled(Style.Heading(match.groupValues[1].length)) {
                    parseInline(match.groupValues[2], out)
                }
            } ?: task.matchEntire(line)?.let { match ->
                out.append(indent(match.groupValues[1]))
                out.append(if (match.groupValues[2] == " ") "☐ " else "☑ ")
                parseInline(match.groupValues[3], out)
            } ?: bullet.matchEntire(line)?.takeUnless { rule.matches(line) }?.let { match ->
                out.append(indent(match.groupValues[1]))
                out.append("• ")
                parseInline(match.groupValues[2], out)
            } ?: ordered.matchEntire(line)?.let { match ->
                out.append(indent(match.groupValues[1]))
                out.append(match.groupValues[2] + match.groupValues[3] + " ")
                parseInline(match.groupValues[4], out)
            } ?: quote.matchEntire(line)?.let { match ->
                out.append("▎ ")
                out.styled(Style.Italic) { parseInline(match.groupValues[1], out) }
            } ?: rule.matchEntire(line)?.let {
                out.append("――――――")
            } ?: parseInline(line.trim(), out)
        }

        return out.build()
    }

    private fun indent(whitespace: String): String {
        val width = whitespace.replace("\t", "    ").length
        return "  ".repeat(width / 2)
    }

    private sealed class Inline(val pattern: Regex) {
        class Styled(pattern: Regex, val style: Style) : Inline(pattern)
        class Literal(pattern: Regex, val style: Style) : Inline(pattern)
        class Link(pattern: Regex) : Inline(pattern)
    }

    // Order breaks ties between matches starting at the same index
    private val inlines = listOf(
        Inline.Literal(Regex("`([^`]+)`"), Style.Code),
        Inline.Link(Regex("!?\\[([^\\]]*)]\\(([^)]*)\\)")),
        // Closing ** must not be followed by *, so in "**a *b***" the italic keeps its closing *
        Inline.Styled(Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)"), Style.Bold),
        Inline.Styled(Regex("(?<![\\w])__(?=\\S)(.+?)(?<=\\S)__(?![\\w])"), Style.Bold),
        Inline.Styled(Regex("~~(?=\\S)(.+?)(?<=\\S)~~"), Style.Strikethrough),
        Inline.Styled(Regex("\\*(?=\\S)(.+?)(?<=\\S)\\*"), Style.Italic),
        Inline.Styled(Regex("(?<![\\w])_(?=\\S)(.+?)(?<=\\S)_(?![\\w])"), Style.Italic),
    )

    private fun parseInline(text: String, out: Builder) {
        var position = 0
        while (position < text.length) {
            var best: Pair<Inline, MatchResult>? = null
            for (candidate in inlines) {
                val match = candidate.pattern.find(text, position) ?: continue
                if (best == null || match.range.first < best.second.range.first) {
                    best = candidate to match
                }
            }
            val (kind, match) = best ?: break
            out.append(text.substring(position, match.range.first))
            when (kind) {
                is Inline.Literal -> out.styled(kind.style) { out.append(match.groupValues[1]) }
                is Inline.Styled -> out.styled(kind.style) { parseInline(match.groupValues[1], out) }
                is Inline.Link -> {
                    val label = match.groupValues[1]
                    if (match.value.startsWith("!")) {
                        out.append(if (label.isBlank()) "🖼" else "🖼 $label")
                    } else {
                        parseInline(label.ifBlank { match.groupValues[2] }, out)
                    }
                }
            }
            position = match.range.last + 1
        }
        out.append(text.substring(position))
    }

    private fun protectEscapes(text: String): String {
        val result = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            if (c == '\\' && next != null && next in ESCAPABLE) {
                result.append((ESCAPE_BASE + ESCAPABLE.indexOf(next)).toChar())
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    private fun restoreEscapes(text: String): String = buildString(text.length) {
        for (c in text) {
            val index = c.code - ESCAPE_BASE
            append(if (index in ESCAPABLE.indices) ESCAPABLE[index] else c)
        }
    }

    private class Builder {
        private val text = StringBuilder()
        private val spans = mutableListOf<Span>()

        fun append(value: String) {
            text.append(value)
        }

        fun newLine() {
            if (text.isNotEmpty()) {
                text.append('\n')
            }
        }

        fun styled(style: Style, block: () -> Unit) {
            val start = text.length
            block()
            if (text.length > start) {
                spans.add(Span(start, text.length, style))
            }
        }

        // Escape placeholders are single chars, so restoring them keeps every span offset valid
        fun build(): StyledText {
            val trimmed = text.toString().trimEnd()
            val clamped = spans.map { it.copy(end = minOf(it.end, trimmed.length)) }.filter { it.start < it.end }
            return StyledText(restoreEscapes(trimmed), clamped)
        }
    }
}
