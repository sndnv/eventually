package eventually.client.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.client.persistence.Converters.Companion.toDataExport
import eventually.client.persistence.Converters.Companion.toJson
import eventually.client.persistence.DataExport
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.ui.TextExtensions.withBold
import eventually.client.ui.components.ChildTopBar
import eventually.client.ui.components.SectionDivider
import eventually.core.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Stats(
    val tasks: List<Task>? = null,
    val schedules: List<TaskScheduleEntity>? = null,
    val notifications: List<NotificationEntity>? = null,
    val lastEvaluation: Instant? = null,
    val nextEvaluation: Instant? = null,
    val evaluations: Long? = null
)

@Composable
fun StatsScreen(
    stats: Stats,
    onImport: (DataExport) -> Unit,
    onForceEvaluation: () -> Unit,
    onBack: () -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf(StatsDialog.None) }

    Scaffold(
        topBar = { ChildTopBar(subtitle = stringResource(R.string.stats_subtitle), onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            StatRow(
                title = R.string.stats_title_tasks,
                content = stats.tasks?.let { tasks ->
                    val enabled = tasks.count { it.isActive }
                    stringResource(R.string.stats_content_tasks).withBold(
                        tasks.size.toString(),
                        enabled.toString(),
                        (tasks.size - enabled).toString()
                    )
                }
            )

            StatRow(
                title = R.string.stats_title_schedules,
                content = stats.schedules?.let { schedules ->
                    stringResource(R.string.stats_content_schedules).withBold(
                        schedules.size.toString(),
                        schedules.flatMap { it.instances.values }.size.toString()
                    )
                }
            )

            StatRow(
                title = R.string.stats_title_notifications,
                content = stats.notifications?.let { notifications ->
                    stringResource(R.string.stats_content_notifications).withBold(
                        notifications.size.toString(),
                        notifications.count { it.type == "context" }.toString(),
                        notifications.count { it.type == "execution" }.toString()
                    )
                }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = { dialog = StatsDialog.Export }, modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.stats_export_data))
                }

                Button(onClick = { dialog = StatsDialog.Import }, modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.stats_import_data))
                }
            }

            SectionDivider()

            StatRow(
                title = R.string.stats_title_last_evaluation,
                content = stats.lastEvaluation?.let {
                    stringResource(R.string.stats_content_last_evaluation).withBold(it.formatted())
                }
            )

            StatRow(
                title = R.string.stats_title_next_evaluation,
                content = stats.nextEvaluation?.let {
                    stringResource(R.string.stats_content_next_evaluation).withBold(it.formatted())
                }
            )

            StatRow(
                title = R.string.stats_title_evaluations,
                content = stats.evaluations?.let {
                    stringResource(R.string.stats_content_evaluations).withBold(it.toString())
                }
            )

            SectionDivider()

            Button(onClick = onForceEvaluation, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = stringResource(R.string.stats_force_evaluation))
            }
        }
    }

    when (dialog) {
        StatsDialog.Export -> {
            val data = remember(stats.tasks, stats.schedules, stats.notifications) {
                DataExport(
                    tasks = stats.tasks ?: emptyList(),
                    schedules = stats.schedules ?: emptyList(),
                    notifications = stats.notifications ?: emptyList()
                ).toJson()
            }

            ExportDialog(data = data, onDismiss = { dialog = StatsDialog.None })
        }

        StatsDialog.Import -> ImportDialog(
            onLoad = { data ->
                onImport(data)
                dialog = StatsDialog.None
            },
            onDismiss = { dialog = StatsDialog.None }
        )

        StatsDialog.None -> Unit
    }
}

enum class StatsDialog {
    None,
    Export,
    Import
}

@Composable
private fun StatRow(@StringRes title: Int, content: AnnotatedString?) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(title),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(text = content ?: AnnotatedString(""), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    )
}

@Composable
private fun ExportDialog(data: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val label = stringResource(R.string.stats_export_data_clip_label)
    val copied = stringResource(R.string.stats_export_data_clip_created)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(label, data))
                    onDismiss()
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(text = stringResource(R.string.stats_export_data_copy))
            }
        },
        title = { Text(text = stringResource(R.string.stats_export_data)) },
        text = { DataField(value = data, onValueChange = {}, readOnly = true) }
    )
}

@Composable
private fun ImportDialog(onLoad: (DataExport) -> Unit, onDismiss: () -> Unit) {
    var json by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val data =runCatching { json.toDataExport() }.getOrNull()
                    invalid = data == null
                    data?.let(onLoad)
                }
            ) {
                Text(text = stringResource(R.string.stats_import_data_load))
            }
        },
        title = { Text(text = stringResource(R.string.stats_import_data)) },
        text = {
            DataField(
                value = json,
                onValueChange = { json = it },
                error = if (invalid) stringResource(R.string.stats_import_data_invalid) else null
            )
        }
    )
}

@Composable
private fun DataField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        readOnly = readOnly,
        isError = error != null,
        supportingText = error?.let { { Text(text = it) } },
        minLines = DataFieldMinLines
    )
}

private fun Instant.formatted(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

private const val DataFieldMinLines: Int = 10
