package eventually.test.client.ui.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import eventually.client.R
import eventually.client.ui.screens.AboutScreen
import eventually.client.ui.theme.EventuallyTheme
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class AboutScreenSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun showAppNameAndVersion() {
        composeRule.setContent { EventuallyTheme { AboutScreen(version = "1.2.3") } }

        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()

        val version = composeRule.onNodeWithText("Version 1.2.3").assertIsDisplayed().annotatedText()

        assertThat(
            version.spanStyles.map { version.substring(it.start, it.end) to it.item.fontWeight },
            equalTo(listOf("1.2.3" to FontWeight.Bold))
        )
    }

    @Test
    fun showLicenseDetailsWithLinks() {
        composeRule.setContent { EventuallyTheme { AboutScreen(version = "1.2.3") } }

        composeRule.onNodeWithText(context.getString(R.string.license_header)).assertExists()

        val copyright = composeRule.onNodeWithText(context.getString(R.string.license_copyright)).annotatedText()
        val info = composeRule.onNodeWithText(context.getString(R.string.license_info)).annotatedText()
        val footer = composeRule.onNodeWithText(context.getString(R.string.license_footer)).annotatedText()

        assertThat(copyright.urls(), equalTo(listOf("https://github.com/sndnv")))
        assertThat(info.urls(), equalTo(listOf("http://www.apache.org/licenses/LICENSE-2.0")))
        assertThat(footer.urls(), equalTo(listOf("https://github.com/sndnv/eventually")))
    }

    private fun SemanticsNodeInteraction.annotatedText(): AnnotatedString =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().first()

    private fun AnnotatedString.urls(): List<String> =
        getLinkAnnotations(start = 0, end = length).map { (it.item as LinkAnnotation.Url).url }
}
