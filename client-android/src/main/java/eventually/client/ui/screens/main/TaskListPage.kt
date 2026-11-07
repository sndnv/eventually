package eventually.client.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.core.model.Task

@Composable
fun TaskListPage(tasks: List<Task>?, query: String, onOpenTask: (Int) -> Unit) {
    when {
        tasks == null -> Unit

        tasks.isEmpty() -> Text(
            text = stringResource(R.string.task_list_empty_text),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )

        else -> {
            val visible = remember(tasks, query) { tasks.reversed().filter { it.matches(query) } }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = visible, key = { it.id }) { task ->
                    TaskListItem(task = task, onClick = { onOpenTask(task.id) })
                }
            }
        }
    }
}

@Composable
private fun TaskListItem(task: Task, onClick: () -> Unit) {
    val colors = if (task.isActive) {
        ListItemDefaults.colors()
    } else {
        ListItemDefaults.colors(
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
        )
    }

    ListItem(
        headlineContent = {
            Text(text = task.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = task.description.takeIf { it.isNotEmpty() }?.let { description ->
            { Text(text = description, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Text(
                text = task.goal,
                modifier = Modifier.width(GoalWidth),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = colors
    )
}

private fun Task.matches(query: String): Boolean =
    query.isEmpty() ||
        name.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        goal.contains(query, ignoreCase = true)

private val GoalWidth = 64.dp
private const val DisabledAlpha: Float = 0.38f
