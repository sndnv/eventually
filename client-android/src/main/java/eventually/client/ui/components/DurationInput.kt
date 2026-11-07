package eventually.client.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eventually.client.activities.helpers.Common.asString
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    unit: ChronoUnit,
    onUnitChange: (ChronoUnit) -> Unit,
    units: List<ChronoUnit>,
    error: String?,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = error != null,
            supportingText = error?.let { { Text(text = it) } },
            trailingIcon = trailingIcon
        )

        Spacer(modifier = Modifier.width(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = unit.asString(context),
                onValueChange = {},
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )

            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                units.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(text = type.asString(context)) },
                        onClick = {
                            onUnitChange(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
