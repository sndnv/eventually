package eventually.client.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import eventually.client.R
import eventually.client.settings.Settings
import eventually.client.ui.components.ChildTopBar
import eventually.client.ui.components.TaskForm
import eventually.client.ui.components.rememberTaskFormState
import eventually.core.model.Task
import java.time.DayOfWeek
import java.time.Instant

@Composable
fun NewTaskScreen(
    goals: List<String>,
    default: Instant,
    dateTimeFormat: Settings.DateTimeFormat,
    firstDayOfWeek: DayOfWeek,
    onCreate: (Task) -> Unit,
    onBack: () -> Unit
) {
    val defaultColor = colorResource(R.color.calendar_event).toArgb()
    val state = rememberTaskFormState(task = null, default = default, defaultColor = defaultColor)

    Scaffold(
        topBar = { ChildTopBar(subtitle = stringResource(R.string.new_task_subtitle), onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            TaskForm(
                state = state,
                goals = goals,
                dateTimeFormat = dateTimeFormat,
                firstDayOfWeek = firstDayOfWeek,
                operation = R.string.new_task_action,
                onOperation = {
                    if (state.validate()) {
                        onCreate(state.toTask(id = 0, defaultColor = defaultColor))
                    }
                }
            )
        }
    }
}
