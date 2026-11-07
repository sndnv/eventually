package eventually.client.ui

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LayoutExtensions {
    val BoxWithConstraintsScope.isWide: Boolean
        get() = maxWidth >= WideMinWidth

    val BoxWithConstraintsScope.isLandscape: Boolean
        get() = maxWidth > maxHeight

    private val WideMinWidth: Dp = 600.dp
}
