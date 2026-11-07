package eventually.test.client.ui.screens.main

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eventually.client.ui.screens.main.TaskListPage
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TaskListPageSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Int>()

    @Test
    fun showNewestTasksFirst() {
        show(tasks = tasks)

        assertThat(shownTasks(), equalTo(listOf("task-3", "task-2", "task-1")))
    }

    @Test
    fun showTaskFields() {
        show(tasks = tasks)

        composeRule.onNodeWithText("description-1").assertExists()
        composeRule.onNodeWithText("goal-1").assertExists()
        composeRule.onNodeWithText("goal-2-shared").assertExists()

        val withoutDescription = composeRule.onNode(hasClickAction() and hasText("task-2")).fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }

        assertThat(withoutDescription.toSet(), equalTo(setOf("task-2", "goal-2-shared")))
    }

    @Test
    fun showEmptyListText() {
        show(tasks = emptyList())

        composeRule.onNodeWithText("No Tasks").assertExists()
    }

    @Test
    fun showNothingBeforeTasksAreLoaded() {
        show(tasks = null)

        composeRule.onNodeWithText("No Tasks").assertDoesNotExist()
        assertThat(shownTasks(), equalTo(emptyList()))
    }

    @Test
    fun filterTasksByName() {
        show(tasks = tasks, query = "TASK-2")

        assertThat(shownTasks(), equalTo(listOf("task-2")))
    }

    @Test
    fun filterTasksByDescription() {
        show(tasks = tasks, query = "Description")

        assertThat(shownTasks(), equalTo(listOf("task-1")))
    }

    @Test
    fun filterTasksByGoal() {
        show(tasks = tasks, query = "shared")

        assertThat(shownTasks(), equalTo(listOf("task-3", "task-2")))
    }

    @Test
    fun showNoTasksWhenNothingMatches() {
        show(tasks = tasks, query = "missing")

        assertThat(shownTasks(), equalTo(emptyList()))
        composeRule.onNodeWithText("No Tasks").assertDoesNotExist()
    }

    @Test
    fun openTasks() {
        show(tasks = tasks)

        composeRule.onNodeWithText("task-2").performClick()

        assertThat(opened.toList(), equalTo(listOf(2)))
    }

    private fun show(tasks: List<Task>?, query: String = "") {
        composeRule.setContent {
            EventuallyTheme {
                TaskListPage(tasks = tasks, query = query, onOpenTask = { opened.add(it) })
            }
        }
    }

    private fun shownTasks(): List<String> =
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.Text].map { it.text }.first { it.startsWith("task-") }
        }

    private fun task(id: Int, description: String, goal: String, isActive: Boolean = true): Task = Task(
        id = id,
        name = "task-$id",
        description = description,
        goal = goal,
        schedule = Task.Schedule.Once(Instant.now()),
        contextSwitch = Duration.ofMinutes(5),
        isActive = isActive,
        color = null
    )

    private val tasks = listOf(
        task(id = 1, description = "description-1", goal = "goal-1"),
        task(id = 2, description = "", goal = "goal-2-shared"),
        task(id = 3, description = "", goal = "shared", isActive = false)
    )
}
