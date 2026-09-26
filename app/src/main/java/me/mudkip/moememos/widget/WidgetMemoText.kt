package me.mudkip.moememos.widget

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.RemoteViews
import me.mudkip.moememos.R
import me.mudkip.moememos.widget.WidgetMarkdown.Style

private val headingSizes = floatArrayOf(1.35f, 1.25f, 1.15f, 1.05f, 1f, 1f)

/** Markdown as a styled CharSequence; only parcelable spans, so it survives RemoteViews. */
fun widgetMarkdownText(markdown: String): CharSequence {
    val styled = WidgetMarkdown.render(markdown)
    return SpannableStringBuilder(styled.text).apply {
        styled.spans.forEach { span ->
            fun set(what: Any) = setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            when (val style = span.style) {
                Style.Bold -> set(StyleSpan(Typeface.BOLD))
                Style.Italic -> set(StyleSpan(Typeface.ITALIC))
                Style.Strikethrough -> set(StrikethroughSpan())
                Style.Code -> set(TypefaceSpan("monospace"))
                is Style.Heading -> {
                    set(StyleSpan(Typeface.BOLD))
                    set(RelativeSizeSpan(headingSizes[(style.level - 1).coerceIn(0, headingSizes.lastIndex)]))
                }
            }
        }
    }
}

/**
 * A TextView showing the memo's formatted text, for Glance's AndroidRemoteViews (Glance's own Text
 * cannot style parts of a string). [maxLines] 0 means no limit.
 */
fun widgetMemoTextViews(context: Context, markdown: String, maxLines: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_memo_text).apply {
        setTextViewText(R.id.widget_memo_text, widgetMarkdownText(markdown))
        setInt(R.id.widget_memo_text, "setMaxLines", if (maxLines > 0) maxLines else Int.MAX_VALUE)
    }
