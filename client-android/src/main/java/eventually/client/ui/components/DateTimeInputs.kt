package eventually.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eventually.client.R
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.settings.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    format: Settings.DateTimeFormat,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }

    InputButton(onClick = { open = true }, modifier = modifier) {
        Text(text = date.formatAsDate(format))
    }

    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.asPickerMillis())

        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(it.asPickerDate()) }
                        open = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInput(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    format: Settings.DateTimeFormat,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }

    InputButton(onClick = { open = true }, modifier = modifier) {
        Text(text = time.formatAsTime(format))
    }

    if (open) {
        val state = if (format == Settings.DateTimeFormat.Iso) {
            rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        } else {
            rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        }

        TimePickerDialog(
            onDismissRequest = { open = false },
            title = { Text(text = stringResource(R.string.time_picker_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(LocalTime.of(state.hour, state.minute))
                        open = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        ) {
            TimePicker(state = state)
        }
    }
}

@Composable
private fun InputButton(onClick: () -> Unit, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = OutlinedTextFieldDefaults.MinHeight),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(
            OutlinedTextFieldDefaults.UnfocusedBorderThickness,
            OutlinedTextFieldDefaults.colors().unfocusedIndicatorColor
        ),
        content = content
    )
}

fun LocalDate.asPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.asPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
