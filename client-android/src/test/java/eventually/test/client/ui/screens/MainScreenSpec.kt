package eventually.test.client.ui.screens

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import eventually.client.settings.Settings
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.ui.screens.MainActions
import eventually.client.ui.screens.MainScreen
import eventually.client.ui.screens.MainSettings
import eventually.client.ui.screens.rememberMainSettings
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.TaskSummary
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class MainScreenSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val called = mutableListOf<String>()

    private val actions = MainActions(
        createTask = { called.add("createTask") },
        createTaskOn = { called.add("createTaskOn") },
        openTask = { called.add("openTask-$it") },
        openSettings = { called.add("openSettings") },
        openStats = { called.add("openStats") },
        openAbout = { called.add("openAbout") },
        openExactAlarmSettings = { called.add("openExactAlarmSettings") },
        evaluate = { called.add("evaluate") }
    )

    @Test
    fun showSubtitlesForSelectedPages() {
        show()

        composeRule.onNodeWithText("Summary").assertExists()
        composeRule.onNodeWithText("Upcoming").assertExists()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithText("Tasks").assertExists()
        composeRule.onNodeWithText("task-1").assertExists()

        composeRule.onNodeWithContentDescription("Calendar").performClick()
        composeRule.onNodeWithText("Calendar").assertExists()
        composeRule.onNodeWithContentDescription("Next year").assertExists()
    }

    @Test
    fun switchPagesBySwiping() {
        show()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Tasks").assertExists()
        composeRule.onNodeWithText("task-1").assertExists()

        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Summary").assertExists()
        composeRule.waitForIdle()

        assertThat(called.toList(), equalTo(listOf("evaluate")))
    }

    @Test
    fun evaluateWhenSummaryIsSelected() {
        show()

        assertThat(called.toList(), equalTo(emptyList()))

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Summary").performClick()
        composeRule.waitForIdle()

        assertThat(called.toList(), equalTo(listOf("evaluate")))
    }

    @Test
    fun searchOnlyInTaskList() {
        show()

        composeRule.onNodeWithContentDescription("Search Tasks").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").assertExists()

        composeRule.onNodeWithContentDescription("Calendar").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").assertDoesNotExist()
    }

    @Test
    fun searchTasks() {
        show()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("goal-2")

        composeRule.onNodeWithText("task-1").assertDoesNotExist()
        composeRule.onNodeWithText("task-2").assertExists()

        composeRule.onNodeWithContentDescription("Clear search").performClick()

        composeRule.onNodeWithText("task-1").assertExists()
        composeRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun closeSearch() {
        show()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("goal-2")

        composeRule.onNodeWithContentDescription("Close search").performClick()

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Tasks").assertExists()
        composeRule.onNodeWithText("task-1").assertExists()
    }

    @Test
    fun closeSearchOnBack() {
        lateinit var dispatcher: OnBackPressedDispatcher

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            EventuallyTheme { Screen() }
        }

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("goal-2")

        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText("task-1").assertExists()
    }

    @Test
    fun closeSearchWhenLeavingTaskList() {
        show()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("goal-2")

        composeRule.onNodeWithContentDescription("Calendar").performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithText("task-1").assertExists()
        composeRule.onNodeWithText("task-2").assertExists()
    }

    @Test
    fun keepSearchWhenRecreated() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent { EventuallyTheme { Screen() } }

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithContentDescription("Search Tasks").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("goal-2")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction() and hasText("goal-2")).assertExists()
        composeRule.onNodeWithText("task-1").assertDoesNotExist()
        composeRule.onNodeWithText("task-2").assertExists()
        assertThat(called.toList(), equalTo(emptyList()))
    }

    @Test
    fun createTasks() {
        show()

        composeRule.onNodeWithContentDescription("Create Task").performClick()

        assertThat(called.toList(), equalTo(listOf("createTask")))
    }

    @Test
    fun openTasks() {
        show()

        composeRule.onNodeWithContentDescription("Tasks").performClick()
        composeRule.onNodeWithText("task-2").performClick()

        assertThat(called.toList(), equalTo(listOf("openTask-2")))
    }

    @Test
    fun openMenuItems() {
        show(settings = settings.copy(statsEnabled = true))

        listOf("Settings" to "openSettings", "Statistics" to "openStats", "About" to "openAbout").forEach { (item, action) ->
            composeRule.onNodeWithContentDescription("More options").performClick()
            composeRule.onNodeWithText(item).performClick()
            composeRule.onNodeWithText(item).assertDoesNotExist()

            assertThat(called.last(), equalTo(action))
        }
    }

    @Test
    fun hideStatsWhenDisabled() {
        show(settings = settings.copy(statsEnabled = false))

        composeRule.onNodeWithContentDescription("More options").performClick()

        composeRule.onNodeWithText("Settings").assertExists()
        composeRule.onNodeWithText("Statistics").assertDoesNotExist()
        composeRule.onNodeWithText("About").assertExists()
    }

    @Test
    fun promptForExactAlarms() {
        show(exactAlarmPrompt = true)

        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertExists()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        assertThat(called.toList(), equalTo(listOf("exactAlarmPromptShown", "openExactAlarmSettings")))
    }

    @Test
    fun promptForExactAlarmsOnlyOnce() {
        var prompt by mutableStateOf(true)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            EventuallyTheme {
                if (visible) {
                    Screen(exactAlarmPrompt = prompt, onExactAlarmPromptShown = { prompt = false })
                }
            }
        }

        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertExists()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertDoesNotExist()

        visible = false
        composeRule.waitForIdle()
        visible = true

        composeRule.onNodeWithText("Summary").assertExists()
        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertDoesNotExist()
        assertThat(prompt, equalTo(false))
    }

    @Test
    fun notRepeatExactAlarmPromptsLeftWhileShowing() {
        var prompt by mutableStateOf(true)
        var visible by mutableStateOf(true)

        composeRule.setContent {
            EventuallyTheme {
                if (visible) {
                    Screen(exactAlarmPrompt = prompt, onExactAlarmPromptShown = { prompt = false })
                }
            }
        }

        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertExists()

        visible = false
        composeRule.waitForIdle()
        visible = true

        composeRule.onNodeWithText("Summary").assertExists()
        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertDoesNotExist()
    }

    @Test
    fun promptForExactAlarmsWhenRequestedLater() {
        var prompt by mutableStateOf(false)

        composeRule.setContent {
            EventuallyTheme { Screen(exactAlarmPrompt = prompt) }
        }

        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertDoesNotExist()

        prompt = true

        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertExists()
        assertThat(called.toList(), equalTo(listOf("exactAlarmPromptShown")))
    }

    @Test
    fun notPromptForExactAlarmsByDefault() {
        show()

        composeRule.onNodeWithText("Reminders may be delayed without exact alarm permission").assertDoesNotExist()
    }

    @Test
    fun updateSettingsWhenPreferencesChange() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultPreferences()

        composeRule.setContent {
            val current = rememberMainSettings(preferences)
            Text(text = "${current.summaryMaxTasks}/${current.statsEnabled}/${current.firstDayOfWeek}")
        }

        composeRule.onNodeWithText("7/false/SUNDAY").assertExists()

        preferences.edit {
            putString(Settings.Keys.SummaryMaxTasks, "9")
            putBoolean(Settings.Keys.StatsEnabled, true)
            putString(Settings.Keys.FirstDayOfWeek, "monday")
        }

        composeRule.onNodeWithText("9/true/MONDAY").assertExists()
    }

    @Test
    fun showTabsInPortrait() {
        show()

        val (summary, tasks, calendar) = listOf("Summary", "Tasks", "Calendar").map(::bounds)

        assertThat(summary.top, equalTo(tasks.top))
        assertThat(tasks.top, equalTo(calendar.top))
        assertThat(summary.left < tasks.left && tasks.left < calendar.left, equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun showRailInLandscape() {
        show()

        val (summary, tasks, calendar) = listOf("Summary", "Tasks", "Calendar").map(::bounds)

        assertThat(summary.left, equalTo(tasks.left))
        assertThat(tasks.left, equalTo(calendar.left))
        assertThat(summary.top < tasks.top && tasks.top < calendar.top, equalTo(true))
        composeRule.onNodeWithContentDescription("Summary").assertIsSelected()

        composeRule.onNodeWithContentDescription("Tasks").performClick()

        composeRule.onNodeWithContentDescription("Tasks").assertIsSelected()
        composeRule.onNodeWithText("Tasks").assertExists()
        composeRule.onNodeWithText("task-1").assertExists()
        assertThat(composeRule.onNodeWithText("task-1").fetchSemanticsNode().boundsInRoot.left >= tasks.right, equalTo(true))

        composeRule.onNodeWithContentDescription("Calendar").performClick()

        composeRule.onNodeWithText("Calendar").assertExists()
        composeRule.onNodeWithContentDescription("Next month").assertExists()
    }

    @Test
    @Config(qualifiers = Square)
    fun keepPagesWhenOrientationChanges() {
        val restorationTester = StateRestorationTester(composeRule)
        val current = YearMonth.now()
        var landscape = false

        restorationTester.setContent {
            EventuallyTheme {
                Box(modifier = Modifier.size(if (landscape) LandscapeSize else PortraitSize)) {
                    Screen()
                }
            }
        }

        composeRule.onNodeWithContentDescription("Calendar").performClick()
        composeRule.onNodeWithContentDescription("Next year").performClick()

        landscape = true
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithContentDescription("Calendar").assertIsSelected()
        composeRule.onNodeWithContentDescription("Next month").assertExists()
        composeRule.onNodeWithText(current.plusYears(1).format(DateTimeFormatter.ofPattern("LLLL yyyy"))).assertExists()
    }

    private fun bounds(description: String): Rect =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    private fun show(settings: MainSettings = this.settings, exactAlarmPrompt: Boolean = false) {
        composeRule.setContent {
            EventuallyTheme { Screen(settings = settings, exactAlarmPrompt = exactAlarmPrompt) }
        }
    }

    @Composable
    private fun Screen(
        settings: MainSettings = this.settings,
        exactAlarmPrompt: Boolean = false,
        onExactAlarmPromptShown: () -> Unit = { called.add("exactAlarmPromptShown") }
    ) {
        MainScreen(
            tasks = tasks,
            summary = TaskSummary.empty(),
            schedules = emptyMap(),
            settings = settings,
            exactAlarmPrompt = exactAlarmPrompt,
            onExactAlarmPromptShown = onExactAlarmPromptShown,
            actions = actions
        )
    }

    private fun task(id: Int): Task = Task(
        id = id,
        name = "task-$id",
        description = "",
        goal = "goal-$id",
        schedule = Task.Schedule.Once(Instant.now()),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = null
    )

    private val tasks = listOf(task(1), task(2))

    private val settings = MainSettings(
        summarySize = Duration.ofMinutes(15),
        summaryMaxTasks = 7,
        dateTimeFormat = Settings.DateTimeFormat.Iso,
        firstDayOfWeek = DayOfWeek.MONDAY,
        statsEnabled = false
    )

    companion object {
        private const val Landscape: String = "w800dp-h360dp-land"
        private const val Square: String = "w800dp-h800dp"
        private val LandscapeSize = DpSize(800.dp, 360.dp)
        private val PortraitSize = DpSize(360.dp, 800.dp)
    }
}
