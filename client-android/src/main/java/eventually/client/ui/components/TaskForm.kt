package eventually.client.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.client.settings.Settings
import eventually.client.ui.LayoutExtensions.isWide
import java.time.DayOfWeek

@Composable
fun TaskForm(
    state: TaskFormState,
    goals: List<String>,
    dateTimeFormat: Settings.DateTimeFormat,
    firstDayOfWeek: DayOfWeek,
    @StringRes operation: Int,
    onOperation: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = isWide
        val group = Modifier.semantics { isTraversalGroup = true }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().then(if (wide) Modifier.height(IntrinsicSize.Min) else Modifier)) {
                if (wide) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().then(group)) {
                        DetailInputs(state = state, goals = goals)

                        if (state.expanded) {
                            DescriptionInput(state = state, modifier = Modifier.weight(1f), fillHeight = true)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).then(group)) {
                    if (!wide) DetailInputs(state = state, goals = goals)

                    ScheduleInputs(state = state, dateTimeFormat = dateTimeFormat, firstDayOfWeek = firstDayOfWeek)

                    if (state.expanded) {
                        if (!wide) {
                            SectionDivider()
                            DescriptionInput(state = state)
                        }

                        Section(title = R.string.task_details_field_title_context_switch) {
                            ContextSwitchInput(state = state)
                        }
                    }
                }
            }

            if (state.expanded) ColorInput(state = state)

            if (state.expandable) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { state.expanded = !state.expanded }) {
                        Icon(
                            painter = painterResource(if (state.expanded) R.drawable.ic_collapse else R.drawable.ic_expand),
                            contentDescription = stringResource(
                                if (state.expanded) R.string.section_collapse_tooltip else R.string.section_expand_tooltip
                            )
                        )
                    }
                }
            }

            SectionDivider()

            Button(onClick = onOperation, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = stringResource(operation))
            }
        }
    }
}

@Composable
private fun DetailInputs(state: TaskFormState, goals: List<String>) {
    Section(title = R.string.task_details_field_title_name) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = stringResource(R.string.task_details_field_title_name_hint)) },
            singleLine = true,
            isError = TaskFormError.Name in state.errors,
            supportingText = state.errorText(TaskFormError.Name, R.string.task_details_field_error_name)
        )
    }

    Section(title = R.string.task_details_field_title_goal) {
        GoalInput(state = state, goals = goals)
    }
}

@Composable
private fun DescriptionInput(state: TaskFormState, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    Section(title = R.string.task_details_field_title_description, modifier = modifier) {
        OutlinedTextField(
            value = state.description,
            onValueChange = { state.description = it },
            modifier = Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
            placeholder = { Text(text = stringResource(R.string.task_details_field_title_description_hint)) }
        )
    }
}

@Composable
private fun ScheduleInputs(state: TaskFormState, dateTimeFormat: Settings.DateTimeFormat, firstDayOfWeek: DayOfWeek) {
    Section(title = R.string.task_details_field_title_schedule) {
        ScheduleTypeInput(state = state)
    }

    when (state.scheduleType) {
        ScheduleType.Once -> Row(modifier = Modifier.fillMaxWidth()) {
            Section(title = R.string.task_details_field_title_schedule_once_time, fill = false) {
                TimeInput(time = state.onceTime, onTimeChange = { state.onceTime = it }, format = dateTimeFormat)
            }

            Section(title = R.string.task_details_field_title_schedule_once_date, modifier = Modifier.weight(1f)) {
                DateInput(
                    date = state.onceDate,
                    onDateChange = { state.onceDate = it },
                    format = dateTimeFormat,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ScheduleType.Repeating -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                Section(title = R.string.task_details_field_title_schedule_repeating_start_time, fill = false) {
                    TimeInput(
                        time = state.startTime,
                        onTimeChange = { state.startTime = it },
                        format = dateTimeFormat
                    )
                }

                Section(
                    title = R.string.task_details_field_title_schedule_repeating_start_date,
                    modifier = Modifier.weight(1f)
                ) {
                    DateInput(
                        date = state.startDate,
                        onDateChange = { state.startDate = it },
                        format = dateTimeFormat,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Section(title = R.string.task_details_field_title_schedule_repeating_every) {
                DurationInput(
                    amount = state.everyAmount,
                    onAmountChange = { state.everyAmount = it },
                    unit = state.everyUnit,
                    onUnitChange = { state.everyUnit = it },
                    units = TaskFormState.Defaults.DurationTypes,
                    error = state.errorMessage(
                        TaskFormError.Every,
                        R.string.task_details_field_error_schedule_repeating_duration
                    ) ?: state.errorMessage(
                        TaskFormError.EveryTooLong,
                        R.string.task_details_field_error_duration_too_long
                    )
                )
            }

            if (state.expanded) {
                Section(title = R.string.task_details_field_title_schedule_repeating_days) {
                    DayPicker(
                        selected = state.days,
                        onSelectedChange = { state.days = it },
                        firstDayOfWeek = firstDayOfWeek
                    )

                    state.errorMessage(
                        TaskFormError.DaysUnreachable,
                        R.string.task_details_field_error_schedule_repeating_days
                    )?.let { error ->
                        Text(
                            text = error,
                            modifier = Modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = stringResource(title), style = MaterialTheme.typography.bodySmall)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalInput(state: TaskFormState, goals: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    val suggestions = remember(goals, state.goal) {
        goals.distinct().sorted().filter { it.matchesGoal(state.goal) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = state.goal,
            onValueChange = {
                state.goal = it
                expanded = true
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            placeholder = { Text(text = stringResource(R.string.task_details_field_title_goal_hint)) },
            singleLine = true,
            isError = TaskFormError.Goal in state.errors,
            supportingText = state.errorText(TaskFormError.Goal, R.string.task_details_field_error_goal)
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { goal ->
                DropdownMenuItem(
                    text = { Text(text = goal) },
                    onClick = {
                        state.goal = goal
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduleTypeInput(state: TaskFormState) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SingleChoiceSegmentedButtonRow {
            ScheduleType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.scheduleType == type,
                    onClick = { state.scheduleType = type },
                    modifier = Modifier.heightIn(min = OutlinedTextFieldDefaults.MinHeight),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ScheduleType.entries.size,
                        baseShape = MaterialTheme.shapes.extraSmall
                    )
                ) {
                    Text(
                        text = stringResource(
                            when (type) {
                                ScheduleType.Once -> R.string.task_details_field_title_schedule_once
                                ScheduleType.Repeating -> R.string.task_details_field_title_schedule_repeating
                            }
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(value = state.isActive, role = Role.Switch, onValueChange = { state.isActive = it }),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.task_details_field_title_is_active))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = state.isActive, onCheckedChange = null)
        }
    }
}

@Composable
private fun ContextSwitchInput(state: TaskFormState) {
    var help by rememberSaveable { mutableStateOf(false) }

    DurationInput(
        amount = state.contextSwitchAmount,
        onAmountChange = { state.contextSwitchAmount = it },
        unit = state.contextSwitchUnit,
        onUnitChange = { state.contextSwitchUnit = it },
        units = TaskFormState.Defaults.ContextSwitchTypes,
        error = state.errorMessage(
            TaskFormError.ContextSwitch,
            R.string.task_details_field_error_context_switch_duration
        ) ?: state.errorMessage(
            TaskFormError.ContextSwitchTooLong,
            R.string.task_details_field_error_duration_too_long
        ),
        trailingIcon = {
            IconButton(onClick = { help = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_about),
                    contentDescription = stringResource(R.string.task_details_field_help_content_context_switch)
                )
            }
        }
    )

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text(text = stringResource(R.string.task_details_field_help_title_context_switch)) },
            text = { Text(text = stringResource(R.string.task_details_field_help_content_context_switch)) },
            confirmButton = {
                TextButton(onClick = { help = false }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun ColorInput(state: TaskFormState) {
    val defaultColor = colorResource(R.color.calendar_event).toArgb()
    var open by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        AssistChip(
            onClick = { open = true },
            label = { Text(text = stringResource(R.string.task_details_field_title_color)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_circle),
                    contentDescription = null,
                    tint = Color(state.color)
                )
            }
        )
    }

    if (open) {
        ColorPickerDialog(
            initial = state.color,
            onSelect = {
                state.color = it
                open = false
            },
            onReset = {
                state.color = defaultColor
                open = false
            },
            onDismiss = { open = false }
        )
    }
}

@Composable
private fun TaskFormState.errorMessage(error: TaskFormError, @StringRes message: Int): String? =
    if (error in errors) stringResource(message) else null

@Composable
private fun TaskFormState.errorText(error: TaskFormError, @StringRes message: Int): (@Composable () -> Unit)? =
    errorMessage(error, message)?.let { text -> { Text(text = text) } }

private fun String.matchesGoal(input: String): Boolean {
    val prefix = input.lowercase()
    val goal = lowercase()

    return goal.startsWith(prefix) || goal.split(" ").any { it.startsWith(prefix) }
}
