package eventually.test.client.ui.screens

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.style.TextDecoration
import eventually.client.settings.Settings
import eventually.client.ui.screens.TaskDetailsActions
import eventually.client.ui.screens.TaskDetailsScreen
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TaskDetailsScreenSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val updated = mutableListOf<Task>()
    private val deleted = mutableListOf<Task>()
    private val dismissed = mutableListOf<TaskInstance>()
    private val postponed = mutableListOf<TaskInstance>()
    private val undone = mutableListOf<Instant>()

    private val actions = TaskDetailsActions(
        update = { updated.add(it) },
        delete = { deleted.add(it) },
        dismiss = { dismissed.add(it) },
        postpone = { postponed.add(it) },
        undo = { undone.add(it) }
    )

    @Test
    fun showTaskDetails() {
        show(task = task, schedule = TaskSchedule(task = task, instances = emptyMap(), dismissed = emptyList()))

        composeRule.onAllNodesWithText("test-task").assertCountEquals(2)
        composeRule.onNodeWithText("test-goal").assertExists()
        composeRule.onNodeWithText("5 minutes").assertExists()
        composeRule.onNodeWithText("On 2026-01-02 at 10:20").assertExists()
        composeRule.onNodeWithText("None").assertExists()
        composeRule.onNodeWithText("Dismissed").assertDoesNotExist()

        composeRule.waitUntil { composeRule.onAllNodesWithText("bold", substring = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(hasText("bold", substring = true)).assertExists()
        composeRule.onNode(hasText("**", substring = true)).assertDoesNotExist()
    }

    @Test
    fun showPlaceholderForEmptyDescriptions() {
        show(task = task.copy(description = ""), schedule = null)

        composeRule.onNodeWithText("-").assertExists()
    }

    @Test
    fun showRepeatingSchedules() {
        val everyDay = task.copy(
            schedule = Task.Schedule.Repeating(
                start = start,
                every = Duration.ofHours(2).toInterval(),
                days = Task.Schedule.Repeating.DefaultDays
            )
        )

        show(task = everyDay, schedule = null)
        composeRule.onNodeWithText("Starting from 2026-01-02 at 10:20\nEvery 2 hours").assertExists()
    }

    @Test
    fun showRepeatingSchedulesOnSelectedDays() {
        val onDays = task.copy(
            schedule = Task.Schedule.Repeating(
                start = start,
                every = Duration.ofMinutes(1).toInterval(),
                days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)
            )
        )

        show(task = onDays, schedule = null)
        composeRule.onNodeWithText("Starting from 2026-01-02 at 10:20\nEvery 1 minute\nOnly on Mon, Fri").assertExists()
    }

    @Test
    fun showDisabledTasks() {
        val disabled = task.copy(isActive = false)
        show(task = disabled, schedule = TaskSchedule(task = disabled, instances = instances, dismissed = emptyList()))

        val schedule = composeRule.onNodeWithText("On 2026-01-02 at 10:20 (disabled)").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().first()

        assertThat(
            schedule.spanStyles
                .filter { it.item.textDecoration == TextDecoration.LineThrough }
                .map { schedule.substring(it.start, it.end) },
            equalTo(listOf("On 2026-01-02 at 10:20"))
        )

        composeRule.onAllNodesWithText("Postpone")[0].assertIsNotEnabled()
        composeRule.onAllNodesWithText("Dismiss")[0].assertIsEnabled()
    }

    @Test
    fun showInstances() {
        show(task = task, schedule = TaskSchedule(task = task, instances = instances, dismissed = emptyList()))

        composeRule.onNodeWithText("2026-01-02 10:40").assertExists()
        composeRule.onNodeWithText("2026-01-02 11:10 (postponed)").assertExists()
        composeRule.onAllNodesWithText("Postpone")[0].assertIsEnabled()
    }

    @Test
    fun limitShownInstances() {
        val many = (1L..101L).map { TaskInstance(instant = start.plusSeconds(it * 60)) }.associateBy { it.id }

        show(task = task, schedule = TaskSchedule(task = task, instances = many, dismissed = emptyList()))

        composeRule.onNodeWithText("Showing 100 out of 101 instances").assertExists()
        composeRule.onAllNodesWithText("Dismiss").assertCountEquals(100)
    }

    @Test
    fun showAllAndDismissedInstances() {
        val many = (1L..101L).map { TaskInstance(instant = start.plusSeconds(it * 60)) }.associateBy { it.id }
        val instant = start.minusSeconds(3600)

        show(
            task = task,
            schedule = TaskSchedule(task = task, instances = many, dismissed = listOf(instant)),
            showAllInstances = true
        )

        composeRule.onNode(hasText("Showing", substring = true)).assertDoesNotExist()
        composeRule.onAllNodesWithText("Dismiss").assertCountEquals(101)
        composeRule.onNodeWithText("Dismissed").assertExists()

        composeRule.onNodeWithText("2026-01-02 09:20").performScrollTo()
        composeRule.onNode(hasText("Undo") and hasClickAction()).performScrollTo().performClick()
        composeRule.onNode(hasText("Undo") and hasClickAction()).assertIsNotEnabled()

        assertThat(undone.toList(), equalTo(listOf(instant)))
    }

    @Test
    fun dismissInstancesWithUndo() {
        show(task = task, schedule = TaskSchedule(task = task, instances = instances, dismissed = emptyList()))

        composeRule.onAllNodesWithText("Dismiss")[0].performClick()
        composeRule.onAllNodesWithText("Dismiss")[0].assertIsNotEnabled()
        composeRule.onAllNodesWithText("Dismiss")[1].assertIsEnabled()
        composeRule.onNodeWithText("Task dismissed").assertExists()
        composeRule.onNodeWithText("Undo").performClick()

        assertThat(dismissed.toList(), equalTo(listOf(first)))
        assertThat(undone.toList(), equalTo(listOf(first.instant)))
    }

    @Test
    fun postponeInstances() {
        show(task = task, schedule = TaskSchedule(task = task, instances = instances, dismissed = emptyList()))

        composeRule.onAllNodesWithText("Postpone")[0].performClick()
        composeRule.onAllNodesWithText("Postpone")[0].assertIsNotEnabled()
        composeRule.onNodeWithText("Task postponed").assertExists()

        assertThat(postponed.toList(), equalTo(listOf(first)))
        assertThat(dismissed.toList(), equalTo(emptyList()))
    }

    @Test
    fun deleteTasksAfterConfirmation() {
        show(task = task, schedule = null)

        composeRule.onNodeWithContentDescription("Delete Task").performClick()
        composeRule.onNodeWithText("Delete task test-task?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(deleted.toList(), equalTo(emptyList()))

        composeRule.onNodeWithContentDescription("Delete Task").performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(deleted.toList(), equalTo(listOf(task)))
    }

    @Test
    fun editTasks() {
        show(task = task, schedule = null)

        composeRule.onNodeWithContentDescription("Edit Task").performClick()
        composeRule.onNodeWithContentDescription("Edit Task").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete Task").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Show more").assertDoesNotExist()

        textField("test-task").performTextReplacement("updated-task")
        composeRule.onNodeWithText("Update").performScrollTo().performClick()

        assertThat(updated.toList(), equalTo(listOf(task.copy(name = "updated-task"))))

        composeRule.onNodeWithContentDescription("Edit Task").assertExists()
        composeRule.onNodeWithText("Update").assertDoesNotExist()
    }

    @Test
    fun stopEditingOnBack() {
        lateinit var dispatcher: OnBackPressedDispatcher

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            EventuallyTheme { Screen(task = task, schedule = null) }
        }

        composeRule.onNodeWithContentDescription("Edit Task").performClick()
        composeRule.onNodeWithText("Update").assertExists()

        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        composeRule.onNodeWithText("Update").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Edit Task").assertExists()
        assertThat(updated.toList(), equalTo(emptyList()))
    }

    @Test
    fun keepEditingWhenRecreated() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            EventuallyTheme { Screen(task = task, schedule = null) }
        }

        composeRule.onNodeWithContentDescription("Edit Task").performClick()
        textField("test-task").performTextReplacement("updated-task")

        restorationTester.emulateSavedInstanceStateRestore()

        textField("updated-task").assertExists()
        composeRule.onNodeWithText("Update").assertExists()
    }

    @Test
    fun keepEditingWhenRecreatedBeforeTasksAreAvailable() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            var available by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { available = true }

            EventuallyTheme { Screen(task = if (available) task else null, schedule = null) }
        }

        composeRule.onNodeWithContentDescription("Edit Task").performClick()
        textField("test-task").performTextReplacement("updated-task")

        restorationTester.emulateSavedInstanceStateRestore()

        textField("updated-task").assertExists()
        composeRule.onNodeWithText("Update").assertExists()
    }

    @Test
    fun hideActionsBeforeTasksAreAvailable() {
        show(task = null, schedule = null)

        composeRule.onNodeWithContentDescription("Edit Task").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete Task").assertDoesNotExist()
        composeRule.onNodeWithText("test-task").assertDoesNotExist()
    }

    private fun show(task: Task?, schedule: TaskSchedule?, showAllInstances: Boolean = false) {
        composeRule.setContent {
            EventuallyTheme { Screen(task = task, schedule = schedule, showAllInstances = showAllInstances) }
        }
    }

    @Composable
    private fun Screen(task: Task?, schedule: TaskSchedule?, showAllInstances: Boolean = false) {
        TaskDetailsScreen(
            task = task,
            schedule = schedule,
            goals = listOfNotNull(task?.goal),
            dateTimeFormat = Settings.DateTimeFormat.Iso,
            firstDayOfWeek = DayOfWeek.MONDAY,
            showAllInstances = showAllInstances,
            actions = actions,
            onBack = {}
        )
    }

    private fun textField(text: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasSetTextAction() and (hasText(text) or hasAnyDescendant(hasText(text))),
            useUnmergedTree = true
        ).performScrollTo()

    private val zone = ZoneId.systemDefault()

    private val start = LocalDateTime.of(2026, 1, 2, 10, 20).atZone(zone).toInstant()

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "some **bold** text",
        goal = "test-goal",
        schedule = Task.Schedule.Once(instant = start),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = null
    )

    private val first = TaskInstance(id = UUID.randomUUID(), instant = start.plusSeconds(1200), postponed = null)

    private val second = TaskInstance(
        id = UUID.randomUUID(),
        instant = start.plusSeconds(1800),
        postponed = Duration.ofMinutes(20)
    )

    private val instances = listOf(first, second).associateBy { it.id }
}
