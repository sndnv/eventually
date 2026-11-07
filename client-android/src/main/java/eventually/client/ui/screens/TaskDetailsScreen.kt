package eventually.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import eventually.client.R
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.asString
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.settings.Settings
import eventually.client.ui.TextExtensions.withBold
import eventually.client.ui.TextExtensions.withStyle
import eventually.client.ui.components.ChildTopBar
import eventually.client.ui.components.SectionDivider
import eventually.client.ui.components.TaskForm
import eventually.client.ui.components.rememberTaskFormState
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

class TaskDetailsActions(
    val update: (Task) -> Unit,
    val delete: (Task) -> Unit,
    val dismiss: (TaskInstance) -> Unit,
    val postpone: (TaskInstance) -> Unit,
    val undo: (Instant) -> Unit
)

@Composable
fun TaskDetailsScreen(
    task: Task?,
    schedule: TaskSchedule?,
    goals: List<String>,
    dateTimeFormat: Settings.DateTimeFormat,
    firstDayOfWeek: DayOfWeek,
    showAllInstances: Boolean,
    actions: TaskDetailsActions,
    onBack: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dismissedMessage = stringResource(R.string.snackbar_task_dismissed)
    val dismissedAction = stringResource(R.string.snackbar_task_dismissed_action)
    val postponedMessage = stringResource(R.string.snackbar_task_postponed)

    BackHandler(enabled = editing) { editing = false }

    Scaffold(
        topBar = {
            ChildTopBar(
                subtitle = task?.name ?: "",
                onBack = onBack,
                actions = {
                    if (task != null && !editing) {
                        IconButton(onClick = { editing = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.edit_task_hint)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (task != null && !editing) {
                FloatingActionButton(
                    onClick = { confirmDelete = true },
                    containerColor = colorResource(R.color.button_delete),
                    contentColor = colorResource(R.color.button_delete_icon)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.task_preview_button_description_delete)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(hostState = snackbars) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when {
                task == null -> Unit

                editing -> {
                    val defaultColor = colorResource(R.color.calendar_event).toArgb()
                    val default = remember { Instant.now() }
                    val form = rememberTaskFormState(task = task, default = default, defaultColor = defaultColor)

                    TaskForm(
                        state = form,
                        goals = goals,
                        dateTimeFormat = dateTimeFormat,
                        firstDayOfWeek = firstDayOfWeek,
                        operation = R.string.existing_task_update_action,
                        onOperation = {
                            if (form.validate()) {
                                actions.update(form.toTask(id = task.id, defaultColor = defaultColor))
                                editing = false
                            }
                        }
                    )
                }

                else -> TaskPreview(
                    task = task,
                    schedule = schedule,
                    dateTimeFormat = dateTimeFormat,
                    firstDayOfWeek = firstDayOfWeek,
                    showAllInstances = showAllInstances,
                    onDismiss = { instance ->
                        actions.dismiss(instance)
                        scope.launch {
                            val result = snackbars.showSnackbar(
                                message = dismissedMessage,
                                actionLabel = dismissedAction,
                                duration = SnackbarDuration.Short
                            )

                            if (result == SnackbarResult.ActionPerformed) {
                                actions.undo(instance.instant)
                            }
                        }
                    },
                    onPostpone = { instance ->
                        actions.postpone(instance)
                        scope.launch { snackbars.showSnackbar(message = postponedMessage) }
                    },
                    onUndo = actions.undo
                )
            }
        }
    }

    if (confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = {
                Text(
                    text = stringResource(R.string.existing_task_delete_message).withStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        task.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.delete(task)
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TaskPreview(
    task: Task,
    schedule: TaskSchedule?,
    dateTimeFormat: Settings.DateTimeFormat,
    firstDayOfWeek: DayOfWeek,
    showAllInstances: Boolean,
    onDismiss: (TaskInstance) -> Unit,
    onPostpone: (TaskInstance) -> Unit,
    onUndo: (Instant) -> Unit
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Field(title = R.string.task_preview_field_title_name) {
                Text(text = task.name, style = MaterialTheme.typography.titleLarge)
            }

            Field(title = R.string.task_preview_field_title_goal) {
                Text(text = task.goal, style = MaterialTheme.typography.bodyLarge)
            }

            Field(title = R.string.task_preview_field_title_description) {
                if (task.description.isNotBlank()) {
                    Markdown(content = task.description, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(text = "-", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Field(title = R.string.task_preview_field_title_context_switch) {
                val (amount, unit) = task.contextSwitch.toFields()
                Text(
                    text = stringResource(
                        R.string.task_preview_field_content_context_switch,
                        amount,
                        unit.asQuantityString(amount, context)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Field(title = R.string.task_preview_field_title_schedule) {
                Text(
                    text = scheduleText(task = task, format = dateTimeFormat, firstDayOfWeek = firstDayOfWeek),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            SectionDivider()

            Instances(
                schedule = schedule,
                format = dateTimeFormat,
                showAllInstances = showAllInstances,
                onDismiss = onDismiss,
                onPostpone = onPostpone
            )

            if (showAllInstances) {
                DismissedInstances(schedule = schedule, format = dateTimeFormat, onUndo = onUndo)
            }
        }
    }
}

@Composable
private fun Field(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Caption(title = title)
        content()
    }
}

@Composable
private fun Caption(@StringRes title: Int) {
    Text(text = stringResource(title), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Instances(
    schedule: TaskSchedule?,
    format: Settings.DateTimeFormat,
    showAllInstances: Boolean,
    onDismiss: (TaskInstance) -> Unit,
    onPostpone: (TaskInstance) -> Unit
) {
    val instances = schedule?.instances?.values?.sortedBy { it.instant } ?: emptyList()
    val shown = if (showAllInstances) instances else instances.take(MaxShownInstances)
    val postponeEnabled = schedule?.task?.isActive ?: false

    val dismissing = remember(schedule) { mutableStateListOf<UUID>() }
    val postponing = remember(schedule) { mutableStateListOf<UUID>() }

    Caption(title = R.string.task_preview_field_title_instances)

    if (!showAllInstances && instances.size > MaxShownInstances) {
        Text(
            text = stringResource(R.string.task_preview_instances_too_many_text)
                .withBold(MaxShownInstances.toString(), instances.size.toString()),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.task_preview_instances_too_many_tooltip),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }

    if (shown.isEmpty()) {
        Text(text = stringResource(R.string.task_preview_instances_empty_text), style = MaterialTheme.typography.bodyMedium)
    } else {
        shown.forEach { instance ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (instance.postponed != null) {
                            R.string.task_preview_field_content_instance_execution_postponed
                        } else {
                            R.string.task_preview_field_content_instance_execution
                        },
                        instance.execution().formatAsFullDateTime(format)
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )

                TextButton(
                    onClick = {
                        dismissing.add(instance.id)
                        onDismiss(instance)
                    },
                    enabled = instance.id !in dismissing
                ) {
                    Text(text = stringResource(R.string.task_preview_button_text_dismiss))
                }

                TextButton(
                    onClick = {
                        postponing.add(instance.id)
                        onPostpone(instance)
                    },
                    enabled = postponeEnabled && instance.id !in postponing
                ) {
                    Text(text = stringResource(R.string.task_preview_button_text_postpone))
                }
            }
        }
    }
}

@Composable
private fun DismissedInstances(schedule: TaskSchedule?, format: Settings.DateTimeFormat, onUndo: (Instant) -> Unit) {
    val dismissed = schedule?.dismissed?.sorted() ?: emptyList()
    val undoing = remember(schedule) { mutableStateListOf<Instant>() }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Caption(title = R.string.task_preview_field_title_instances_dismissed)

        if (dismissed.isEmpty()) {
            Text(
                text = stringResource(R.string.task_preview_instances_dismissed_empty_text),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            dismissed.forEach { instant ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.task_preview_field_content_instance_dismissed,
                            instant.formatAsFullDateTime(format)
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    TextButton(
                        onClick = {
                            undoing.add(instant)
                            onUndo(instant)
                        },
                        enabled = instant !in undoing
                    ) {
                        Text(text = stringResource(R.string.task_preview_button_text_undo))
                    }
                }
            }
        }
    }
}

@Composable
private fun scheduleText(task: Task, format: Settings.DateTimeFormat, firstDayOfWeek: DayOfWeek): AnnotatedString {
    val context = LocalContext.current

    val text = when (val schedule = task.schedule) {
        is Task.Schedule.Once -> stringResource(R.string.task_preview_field_content_schedule_once).withBold(
            schedule.instant.formatAsDate(format),
            schedule.instant.formatAsTime(format)
        )

        is Task.Schedule.Repeating -> {
            val (amount, unit) = schedule.every.toFields()

            stringResource(
                if (schedule.days.size == DaysInWeek) {
                    R.string.task_preview_field_content_schedule_repeating_every_day
                } else {
                    R.string.task_preview_field_content_schedule_repeating_on_days
                }
            ).withBold(
                schedule.start.formatAsDate(format),
                schedule.start.formatAsTime(format),
                amount.toString(),
                unit.asQuantityString(amount, context),
                schedule.days.asString(withFirstDayOfWeek = firstDayOfWeek)
            )
        }
    }

    val inactive = stringResource(R.string.task_preview_field_content_inactive)

    return if (task.isActive) {
        text
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text) }
            append(" ")
            append(inactive)
        }
    }
}

private const val MaxShownInstances: Int = 100
private const val DaysInWeek: Int = 7
