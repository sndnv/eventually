package eventually.client.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import eventually.client.R

@Composable
fun ChildTopBar(subtitle: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    AppTopBar(
        title = { AppTitle(subtitle = subtitle) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back_hint))
            }
        },
        actions = actions
    )
}
