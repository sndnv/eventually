package eventually.client.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import eventually.client.R

@Composable
fun ColorPickerDialog(
    initial: Int,
    onSelect: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val controller = rememberColorPickerController()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.task_details_field_title_color)) },
        text = {
            Column {
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    controller = controller,
                    initialColor = Color(initial)
                )

                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(32.dp),
                    controller = controller,
                    initialColor = Color(initial)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(controller.selectedColor.value.toArgb()) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(text = stringResource(R.string.task_details_color_reset))
                }

                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        }
    )
}
