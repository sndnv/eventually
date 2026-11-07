package eventually.test.client.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import eventually.client.ui.TextExtensions.linkified
import eventually.client.ui.TextExtensions.withBold
import eventually.client.ui.TextExtensions.withStyles
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TextExtensionsSpec {
    @Test
    fun linkifyWebUrls() {
        val original = "Visit https://github.com/sndnv and\nhttp://www.apache.org/licenses/LICENSE-2.0 for details"
        val style = SpanStyle(textDecoration = TextDecoration.Underline)

        val linkified = original.linkified(style)

        assertThat(linkified.text, equalTo(original))

        val links = linkified.getLinkAnnotations(start = 0, end = linkified.length).map {
            val link = it.item as LinkAnnotation.Url
            Triple(linkified.substring(it.start, it.end), link.url, link.styles?.style)
        }

        assertThat(
            links,
            equalTo(
                listOf(
                    Triple("https://github.com/sndnv", "https://github.com/sndnv", style),
                    Triple(
                        "http://www.apache.org/licenses/LICENSE-2.0",
                        "http://www.apache.org/licenses/LICENSE-2.0",
                        style
                    )
                )
            )
        )
    }

    @Test
    fun replacePlaceholdersWithBoldValues() {
        val text = "Show dates as %1\$s and time as %2\$s".withBold("2026-11-05", "12:34")

        assertThat(text.text, equalTo("Show dates as 2026-11-05 and time as 12:34"))
        assertThat(text.boldParts(), equalTo(listOf("2026-11-05", "12:34")))
    }

    @Test
    fun replacePlaceholdersInAnyOrder() {
        val text = "%2\$s then %1\$s".withBold("first", "second")

        assertThat(text.text, equalTo("second then first"))
        assertThat(text.boldParts(), equalTo(listOf("second", "first")))
    }

    @Test
    fun keepPlaceholdersWithoutValues() {
        val text = "Show expected tasks in the next %2\$s".withBold("1", "hour")

        assertThat(text.text, equalTo("Show expected tasks in the next hour"))
        assertThat(text.boldParts(), equalTo(listOf("hour")))

        val missing = "Version %1\$s and %2\$s".withBold("1.2.3")

        assertThat(missing.text, equalTo("Version 1.2.3 and %2\$s"))
        assertThat(missing.boldParts(), equalTo(listOf("1.2.3")))
    }

    @Test
    fun replacePlaceholdersWithStyledValues() {
        val bold = SpanStyle(fontWeight = FontWeight.Bold)
        val italic = SpanStyle(fontStyle = FontStyle.Italic)

        val text = "%1\$s - %2\$s (%3\$s)".withStyles("10:20" to bold, "task" to SpanStyle(), "goal" to italic)

        assertThat(text.text, equalTo("10:20 - task (goal)"))
        assertThat(
            text.spanStyles.map { text.substring(it.start, it.end) to it.item },
            equalTo(listOf("10:20" to bold, "task" to SpanStyle(), "goal" to italic))
        )
    }

    @Test
    fun keepTextWithoutUrls() {
        val linkified = "No links here".linkified(SpanStyle())

        assertThat(linkified.text, equalTo("No links here"))
        assertThat(linkified.getLinkAnnotations(start = 0, end = linkified.length), equalTo(emptyList()))
    }

    private fun AnnotatedString.boldParts(): List<String> =
        spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { substring(it.start, it.end) }
}
