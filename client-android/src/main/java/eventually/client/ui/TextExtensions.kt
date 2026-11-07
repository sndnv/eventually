package eventually.client.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.core.util.PatternsCompat

object TextExtensions {
    fun String.linkified(style: SpanStyle): AnnotatedString = buildAnnotatedString {
        append(this@linkified)

        val matcher = PatternsCompat.WEB_URL.matcher(this@linkified)
        while (matcher.find()) {
            addLink(
                url = LinkAnnotation.Url(url = matcher.group(), styles = TextLinkStyles(style = style)),
                start = matcher.start(),
                end = matcher.end()
            )
        }
    }

    fun String.withBold(vararg values: String): AnnotatedString =
        withStyle(SpanStyle(fontWeight = FontWeight.Bold), *values)

    fun String.withStyle(style: SpanStyle, vararg values: String): AnnotatedString =
        withStyles(*values.map { it to style }.toTypedArray())

    fun String.withStyles(vararg values: Pair<String, SpanStyle>): AnnotatedString = buildAnnotatedString {
        var last = 0

        Placeholder.findAll(this@withStyles).forEach { match ->
            values.getOrNull(match.groupValues[1].toInt() - 1)?.let { (value, style) ->
                append(this@withStyles.substring(last, match.range.first))
                withStyle(style) { append(value) }
                last = match.range.last + 1
            }
        }

        append(this@withStyles.substring(last))
    }

    private val Placeholder: Regex = Regex("%(\\d+)\\\$s")
}
