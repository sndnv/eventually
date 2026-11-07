package eventually.client.ui.screens.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.helpers.DateTimeExtensions.isToday
import eventually.client.activities.helpers.DateTimeExtensions.isTomorrow
import eventually.client.settings.Settings
import eventually.client.ui.LayoutExtensions.isWide
import eventually.client.ui.TextExtensions.withStyles
import eventually.client.ui.components.ItemListDialog
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import java.time.Duration
import java.time.Instant

@Composable
fun TaskSummaryPage(
    summary: TaskSummary,
    schedules: Map<Int, TaskSchedule>,
    summarySize: Duration,
    maxTasks: Int,
    dateTimeFormat: Settings.DateTimeFormat,
    onOpenTask: (Int) -> Unit
) {
    var dialog by rememberSaveable { mutableStateOf(SummaryDialog.None) }

    val expired = summary.expired.sortedBy { it.second.execution() }
    val upcoming = summary.upcoming.sortedBy { it.second.execution() }

    val upcomingCard = @Composable { modifier: Modifier ->
        SummaryCard(
            title = R.string.summary_upcoming_overview_title,
            content = upcoming.truncated(maxTasks).map { item ->
                item?.let { taskItem(R.string.summary_item_content, it, dateTimeFormat) } ?: AnnotatedString(SkippedItems)
            },
            footer = upcomingFooter(upcoming = upcoming.size, summarySize = summarySize),
            onClick = { if (upcoming.isNotEmpty()) dialog = SummaryDialog.Upcoming },
            modifier = modifier
        )
    }

    val expiredCard = @Composable { modifier: Modifier ->
        SummaryCard(
            title = R.string.summary_expired_overview_title,
            content = expired.truncated(maxTasks).map { item ->
                item?.let { taskItem(R.string.summary_item_content, it, dateTimeFormat) } ?: AnnotatedString(SkippedItems)
            },
            footer = expiredFooter(expired = expired, format = dateTimeFormat),
            onClick = { if (expired.isNotEmpty()) dialog = SummaryDialog.Expired },
            modifier = modifier
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = isWide

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (wide) {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    upcomingCard(Modifier.weight(1f).fillMaxHeight())
                    expiredCard(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                upcomingCard(Modifier.fillMaxWidth())
                expiredCard(Modifier.fillMaxWidth())
            }

            TextButton(
                onClick = { dialog = SummaryDialog.Timeline },
                modifier = if (wide) {
                    Modifier.align(Alignment.CenterHorizontally)
                } else {
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                }
            ) {
                Text(
                    text = stringResource(R.string.summary_timeline_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }

    val dismiss = { dialog = SummaryDialog.None }

    when (dialog) {
        SummaryDialog.Upcoming -> TaskItemsDialog(
            title = R.string.summary_upcoming_dialog_title,
            template = R.string.summary_upcoming_dialog_item_text,
            items = upcoming,
            format = dateTimeFormat,
            onOpenTask = onOpenTask,
            onDismiss = dismiss
        )

        SummaryDialog.Expired -> TaskItemsDialog(
            title = R.string.summary_expired_dialog_title,
            template = R.string.summary_expired_dialog_item_text,
            items = expired,
            format = dateTimeFormat,
            onOpenTask = onOpenTask,
            onDismiss = dismiss
        )

        SummaryDialog.Timeline -> TaskItemsDialog(
            title = R.string.summary_timeline_dialog_title,
            template = R.string.summary_timeline_dialog_item_text,
            items = schedules.timeline(),
            format = dateTimeFormat,
            onOpenTask = onOpenTask,
            onDismiss = dismiss,
            emptyText = stringResource(R.string.summary_timeline_dialog_empty_text)
        )

        SummaryDialog.None -> Unit
    }
}

enum class SummaryDialog {
    None,
    Upcoming,
    Expired,
    Timeline
}

@Composable
private fun SummaryCard(
    @StringRes title: Int,
    content: List<AnnotatedString>,
    footer: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onClick, modifier = modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleLarge)

            if (content.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = footer,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TaskItemsDialog(
    @StringRes title: Int,
    @StringRes template: Int,
    items: List<Pair<Task, TaskInstance>>,
    format: Settings.DateTimeFormat,
    onOpenTask: (Int) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String? = null
) {
    ItemListDialog(
        title = stringResource(title),
        items = items.map { taskItem(template, it, format) },
        onItemClick = { index ->
            onDismiss()
            onOpenTask(items[index].first.id)
        },
        onDismiss = onDismiss,
        emptyText = emptyText
    )
}

@Composable
private fun taskItem(@StringRes template: Int, item: Pair<Task, TaskInstance>, format: Settings.DateTimeFormat): AnnotatedString {
    val (task, instance) = item

    return stringResource(template).withStyles(
        instance.execution().rendered(format) to Bold,
        task.name to SpanStyle(),
        task.goal to Italic
    )
}

@Composable
private fun expiredFooter(expired: List<Pair<Task, TaskInstance>>, format: Settings.DateTimeFormat): AnnotatedString =
    when (val first = expired.firstOrNull()) {
        null -> AnnotatedString(stringResource(R.string.summary_expired_footer_empty))
        else -> pluralStringResource(R.plurals.summary_expired_footer_non_empty, expired.size).withStyles(
            expired.size.toString() to Bold,
            first.second.execution().rendered(format) to Bold
        )
    }

@Composable
private fun upcomingFooter(upcoming: Int, summarySize: Duration): AnnotatedString {
    val (amount, unit) = summarySize.toFields()
    val period = arrayOf(amount.toString() to Bold, unit.asQuantityString(amount, LocalContext.current) to Bold)

    return if (upcoming == 0) {
        pluralStringResource(R.plurals.summary_upcoming_footer_empty_period, amount).withStyles(*period)
    } else {
        val tasks = pluralStringResource(R.plurals.summary_upcoming_footer_non_empty_tasks, upcoming)
            .withStyles(upcoming.toString() to Bold)

        tasks + AnnotatedString(" ") +
            pluralStringResource(R.plurals.summary_upcoming_footer_non_empty_period, amount).withStyles(*period)
    }
}

@Composable
private fun Instant.rendered(format: Settings.DateTimeFormat): String =
    if (isToday()) {
        stringResource(R.string.summary_item_instant_today, formatAsTime(format))
    } else {
        val (date, time) = formatAsDateTime(LocalContext.current, format)
        stringResource(R.string.summary_item_instant, date, time)
    }

private fun Map<Int, TaskSchedule>.timeline(): List<Pair<Task, TaskInstance>> =
    values
        .flatMap { schedule ->
            schedule.instances.values
                .filter { it.instant.isToday() || it.instant.isTomorrow() }
                .map { schedule.task to it }
        }
        .sortedBy { it.second.instant }

private fun <T> List<T>.truncated(max: Int): List<T?> =
    if (size > max) take(max / 2) + listOf(null) + takeLast(max / 2) else this

private val Bold: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
private val Italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic)
private const val SkippedItems: String = "..."
