package eventually.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.client.ui.TextExtensions.linkified
import eventually.client.ui.TextExtensions.withBold
import eventually.client.ui.components.SectionDivider

@Composable
fun AboutScreen(version: String) {
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        textDecoration = TextDecoration.Underline
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(colorResource(R.color.primary_dark))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.statusBars)
                    .safeDrawingPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    CenteredText(
                        text = AnnotatedString(stringResource(R.string.app_name)),
                        style = MaterialTheme.typography.displaySmall,
                        secondary = true
                    )

                    CenteredText(
                        text = stringResource(R.string.about_version).withBold(version),
                        style = MaterialTheme.typography.bodySmall,
                        secondary = true
                    )

                    SectionDivider()

                    CenteredText(
                        text = AnnotatedString(stringResource(R.string.license_header)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    CenteredText(
                        text = stringResource(R.string.license_copyright).linkified(linkStyle),
                        style = MaterialTheme.typography.bodySmall,
                        secondary = true,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    CenteredText(
                        text = stringResource(R.string.license_info).linkified(linkStyle),
                        style = MaterialTheme.typography.bodySmall,
                        secondary = true,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    SectionDivider()

                    CenteredText(
                        text = stringResource(R.string.license_footer).linkified(linkStyle),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    secondary: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        style = style
    )
}
