package eventually.client.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import eventually.client.R

@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp, top = 10.dp, end = 48.dp, bottom = 6.dp),
        thickness = 1.dp,
        color = colorResource(R.color.divider)
    )
}
