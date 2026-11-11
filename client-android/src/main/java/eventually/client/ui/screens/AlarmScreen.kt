package eventually.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.settings.Settings
import eventually.client.ui.LayoutExtensions.isLandscape
import eventually.core.model.Task
import java.time.Duration
import java.time.Instant

@Composable
fun AlarmScreen(
    task: Task,
    execution: Instant,
    dateTimeFormat: Settings.DateTimeFormat,
    postponeLength: Duration,
    onPostpone: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {}

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    AlarmDetails(
                        task = task,
                        execution = execution,
                        dateTimeFormat = dateTimeFormat,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    AlarmActions(
                        postponeLength = postponeLength,
                        onPostpone = onPostpone,
                        onDismiss = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    AlarmDetails(
                        task = task,
                        execution = execution,
                        dateTimeFormat = dateTimeFormat,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    AlarmActions(
                        postponeLength = postponeLength,
                        onPostpone = onPostpone,
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmDetails(
    task: Task,
    execution: Instant,
    dateTimeFormat: Settings.DateTimeFormat,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alarm),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text = execution.formatAsTime(dateTimeFormat), style = MaterialTheme.typography.displayLarge)
            Text(text = task.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                text = task.goal,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (task.description.isNotEmpty()) {
                Text(text = task.description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun AlarmActions(
    postponeLength: Duration,
    onPostpone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = postponeLength.toMinutes().toInt()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onPostpone, modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight)) {
            Text(text = pluralStringResource(R.plurals.alarm_action_postpone, minutes, minutes))
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = ActionHeight)) {
            Text(text = stringResource(R.string.notification_instance_action_dismiss_title))
        }
    }
}

private val ActionHeight = 64.dp
