package eventually.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun ItemListDialog(
    title: String,
    items: List<AnnotatedString>,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String? = null,
    confirmButton: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = confirmButton,
        title = {
            Text(
                text = title,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.headlineSmall.fontSize)
            )
        },
        text = {
            if (items.isEmpty() && emptyText != null) {
                Text(text = emptyText)
            } else {
                LazyColumn {
                    itemsIndexed(items) { index, item ->
                        Text(
                            text = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(index) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    )
}
