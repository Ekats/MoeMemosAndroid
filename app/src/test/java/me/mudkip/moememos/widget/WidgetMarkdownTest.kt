package me.mudkip.moememos.widget

import me.mudkip.moememos.widget.WidgetMarkdown.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMarkdownTest {
    private fun styled(markdown: String) = WidgetMarkdown.render(markdown)

    private fun WidgetMarkdown.StyledText.spanText(style: Style) =
        spans.filter { it.style == style }.map { text.substring(it.start, it.end) }

    @Test
    fun headingDropsMarkersAndIsStyledByLevel() {
        val result = styled("# Title\n### Sub ###\nbody")
        assertEquals("Title\nSub\nbody", result.text)
        assertEquals(listOf("Title"), result.spanText(Style.Heading(1)))
        assertEquals(listOf("Sub"), result.spanText(Style.Heading(3)))
    }

    @Test
    fun tagsAreNotHeadings() {
        val result = styled("#todo buy milk")
        assertEquals("#todo buy milk", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun inlineStylesNestAndDropMarkers() {
        val result = styled("a **bold *both*** and ~~gone~~ `co*de*`")
        assertEquals("a bold both and gone co*de*", result.text)
        assertEquals(listOf("bold both"), result.spanText(Style.Bold))
        assertEquals(listOf("both"), result.spanText(Style.Italic))
        assertEquals(listOf("gone"), result.spanText(Style.Strikethrough))
        assertEquals(listOf("co*de*"), result.spanText(Style.Code))
    }

    @Test
    fun underscoresInsideWordsStayLiteral() {
        assertEquals("snake_case_name", styled("snake_case_name").text)
        assertEquals(listOf("it"), styled("_it_").spanText(Style.Italic))
    }

    @Test
    fun listsAndTasks() {
        val result = styled("- one\n  - nested\n1. first\n- [ ] open\n- [x] done")
        assertEquals("• one\n  • nested\n1. first\n☐ open\n☑ done", result.text)
    }

    @Test
    fun quotesRulesLinksAndImages() {
        val result = styled("> said\n---\n[site](https://a.b) ![pic](x.png)")
        assertEquals("▎ said\n――――――\nsite 🖼 pic", result.text)
        assertEquals(listOf("said"), result.spanText(Style.Italic))
    }

    @Test
    fun codeFenceIsLiteral() {
        val result = styled("```\n# not heading\n**x**\n```")
        assertEquals("# not heading\n**x**", result.text)
        assertEquals(listOf("# not heading", "**x**"), result.spanText(Style.Code))
    }

    @Test
    fun escapesAreLiteral() {
        assertEquals("*not italic* #1", styled("\\*not italic\\* \\#1").text)
    }

    @Test
    fun blankLinesCollapse() {
        assertEquals("a\n\nb", styled("\n\na\n\n\n\nb\n\n").text)
    }
}
