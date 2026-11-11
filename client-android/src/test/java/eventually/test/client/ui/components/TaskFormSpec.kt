package eventually.test.client.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import eventually.client.R
import eventually.client.settings.Settings
import eventually.client.ui.components.FullScreenAlarms
import eventually.client.ui.components.ScheduleType
import eventually.client.ui.components.TaskForm
import eventually.client.ui.components.TaskFormError
import eventually.client.ui.components.TaskFormState
import eventually.client.ui.screens.NewTaskScreen
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TaskFormSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val created = mutableListOf<Task>()

    private val zone = ZoneId.systemDefault()
    private val default = LocalDateTime.of(2026, 1, 2, 10, 0, 30).atZone(zone).toInstant()
    private val defaultColor = 0xFFFF6D00.toInt()

    @Test
    fun showDefaultsForNewTasks() {
        showNewTask()

        composeRule.onNodeWithText("10:20").assertExists()
        composeRule.onNodeWithText("2026-01-02").assertExists()
        composeRule.onNodeWithText("Once").assertIsSelected()
        composeRule.onNode(isToggleable() and hasText("Enabled")).assertIsOn()
        composeRule.onNodeWithText("Description (optional)").assertDoesNotExist()

        expand()

        composeRule.onNodeWithText("Description (optional)").assertExists()
        textField("15").assertExists()
        composeRule.onNodeWithText("minutes").assertExists()
        composeRule.onNodeWithText("Calendar Event Color").assertExists()
        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).assertIsOff()
    }

    @Test
    fun createTasksWithDefaults() {
        verifyCreatingTasksWithDefaults()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun createTasksWithDefaultsOnWideScreens() {
        verifyCreatingTasksWithDefaults()
    }

    private fun verifyCreatingTasksWithDefaults() {
        showNewTask()

        textField("Task name").performTextReplacement("  test-task ")
        textField("Goal, category or group for the task").performTextReplacement(" test-goal ")
        click("Create")

        assertThat(
            created.toList(),
            equalTo(
                listOf(
                    Task(
                        id = 0,
                        name = "test-task",
                        description = "",
                        goal = "test-goal",
                        schedule = Task.Schedule.Once(
                            instant = LocalDateTime.of(2026, 1, 2, 10, 20).atZone(zone).toInstant()
                        ),
                        contextSwitch = Duration.ofMinutes(15),
                        isActive = true,
                        color = null
                    )
                )
            )
        )
    }

    @Test
    fun requireValidFields() {
        showNewTask()

        click("Create")

        composeRule.onNodeWithText("Name cannot be empty").assertExists()
        composeRule.onNodeWithText("Category cannot be empty").assertExists()

        textField("Task name").performTextReplacement("test-task")
        textField("Goal, category or group for the task").performTextReplacement("test-goal")
        click("Repeating")
        expand()
        textField("1").performTextReplacement("abc")
        textField("15").performTextReplacement("0")
        click("Create")

        composeRule.onNodeWithText("Name cannot be empty").assertDoesNotExist()
        composeRule.onNodeWithText("Category cannot be empty").assertDoesNotExist()
        composeRule.onAllNodesWithText("Duration cannot be empty or 0").assertCountEquals(2)

        assertThat(created.toList(), equalTo(emptyList()))
    }

    @Test
    fun createRepeatingTasks() {
        verifyCreatingRepeatingTasks()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun createRepeatingTasksOnWideScreens() {
        verifyCreatingRepeatingTasks()
    }

    @Test
    fun limitDurationsToOneHundredYears() {
        val withinLimit = listOf(
            "100" to ChronoUnit.YEARS,
            "1200" to ChronoUnit.MONTHS,
            "36524" to ChronoUnit.DAYS,
            "876582" to ChronoUnit.HOURS,
            "2147483647" to ChronoUnit.SECONDS
        )

        val overLimit = listOf(
            "101" to ChronoUnit.YEARS,
            "1000000000" to ChronoUnit.YEARS,
            "1201" to ChronoUnit.MONTHS,
            "36525" to ChronoUnit.DAYS,
            "876583" to ChronoUnit.HOURS,
            "3000000000" to ChronoUnit.YEARS,
            "99999999999999999999" to ChronoUnit.SECONDS
        )

        withinLimit.forEach { (amount, unit) ->
            assertThat(validationErrors(every = amount to unit), equalTo(emptySet()))
        }

        overLimit.forEach { (amount, unit) ->
            assertThat(validationErrors(every = amount to unit), equalTo(setOf(TaskFormError.EveryTooLong)))
        }

        assertThat(validationErrors(contextSwitch = "36524" to ChronoUnit.DAYS), equalTo(emptySet()))
        assertThat(
            validationErrors(contextSwitch = "36525" to ChronoUnit.DAYS),
            equalTo(setOf(TaskFormError.ContextSwitchTooLong))
        )
        assertThat(
            validationErrors(contextSwitch = "3000000000" to ChronoUnit.DAYS),
            equalTo(setOf(TaskFormError.ContextSwitchTooLong))
        )

        listOf("0", "-5", "abc", "", "-3000000000").forEach { amount ->
            assertThat(
                validationErrors(every = amount to ChronoUnit.YEARS, contextSwitch = amount to ChronoUnit.DAYS),
                equalTo(setOf(TaskFormError.Every, TaskFormError.ContextSwitch))
            )
        }
    }

    @Test
    fun requireReachableDays() {
        val monday = setOf(DayOfWeek.MONDAY)

        assertThat(validationErrors(every = "7" to ChronoUnit.DAYS, days = monday), equalTo(setOf(TaskFormError.DaysUnreachable)))
        assertThat(validationErrors(every = "168" to ChronoUnit.HOURS, days = monday), equalTo(setOf(TaskFormError.DaysUnreachable)))
        assertThat(validationErrors(every = "7" to ChronoUnit.DAYS, days = setOf(DayOfWeek.FRIDAY)), equalTo(emptySet()))
        assertThat(validationErrors(every = "1" to ChronoUnit.DAYS, days = monday), equalTo(emptySet()))
        assertThat(validationErrors(every = "1" to ChronoUnit.MONTHS, days = monday), equalTo(emptySet()))

        val once = repeatingState(every = "7" to ChronoUnit.DAYS, days = monday)
        once.scheduleType = ScheduleType.Once

        assertThat(once.validate(), equalTo(true))
    }

    @Test
    fun expandFormsWhenHiddenInputsAreInvalid() {
        val state = repeatingState(every = "7" to ChronoUnit.DAYS, days = setOf(DayOfWeek.MONDAY))

        assertThat(state.expanded, equalTo(false))
        assertThat(state.validate(), equalTo(false))
        assertThat(state.expanded, equalTo(true))
    }

    @Test
    fun rejectSchedulesThatNeverReachTheSelectedDays() {
        showNewTask()

        textField("Task name").performTextReplacement("test-task")
        textField("Goal, category or group for the task").performTextReplacement("test-goal")
        click("Repeating")
        expand()
        textField("1").performTextReplacement("7")
        click("F")
        click("Create")

        composeRule.onNodeWithText("The selected days are never reached with this interval")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        assertThat(created.toList(), equalTo(emptyList()))
    }

    @Test
    fun rejectDurationsLongerThanOneHundredYears() {
        showNewTask()

        textField("Task name").performTextReplacement("test-task")
        textField("Goal, category or group for the task").performTextReplacement("test-goal")
        click("Repeating")
        expand()
        textField("1").performTextReplacement("101")
        click("days")
        menuItem("years").performClick()
        click("Create")

        composeRule.onNodeWithText("Duration cannot be longer than 100 years").assertExists()
        assertThat(created.toList(), equalTo(emptyList()))
    }

    @Test
    fun limitContextSwitchUnitsToFixedDurations() {
        showNewTask()

        textField("Task name").performTextReplacement("test-task")
        textField("Goal, category or group for the task").performTextReplacement("test-goal")
        click("Repeating")
        expand()

        click("days")
        menuItem("months").assertExists()
        menuItem("years").assertExists()
        menuItem("months").performClick()

        click("minutes")
        composeRule.onAllNodes(hasAnyAncestor(isPopup()) and (hasText("months") or hasText("years"))).assertCountEquals(0)
        menuItem("days").performClick()

        click("Create")

        assertThat(
            created.toList(),
            equalTo(
                listOf(
                    Task(
                        id = 0,
                        name = "test-task",
                        description = "",
                        goal = "test-goal",
                        schedule = Task.Schedule.Repeating(
                            start = LocalDateTime.of(2026, 1, 2, 10, 20).atZone(zone).toInstant(),
                            every = Period.ofMonths(1).toInterval()
                        ),
                        contextSwitch = Duration.ofDays(15),
                        isActive = true,
                        color = null
                    )
                )
            )
        )
    }

    private fun verifyCreatingRepeatingTasks() {
        showNewTask()

        textField("Task name").performTextReplacement("test-task")
        textField("Goal, category or group for the task").performTextReplacement("test-goal")
        click("Repeating")
        expand()
        textField("1").performTextReplacement("2")
        click("days")
        click("hours")
        composeRule.onAllNodesWithText("S")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithText("S")[1].performScrollTo().performClick()
        textField("Extra task information").performTextReplacement(" test-description ")
        textField("15").performTextReplacement("30")
        click("minutes")
        click("seconds")
        composeRule.onNode(isToggleable() and hasText("Enabled")).performScrollTo().performClick()
        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().performClick()
        click("Create")

        assertThat(
            created.toList(),
            equalTo(
                listOf(
                    Task(
                        id = 0,
                        name = "test-task",
                        description = "test-description",
                        goal = "test-goal",
                        schedule = Task.Schedule.Repeating(
                            start = LocalDateTime.of(2026, 1, 2, 10, 20).atZone(zone).toInstant(),
                            every = Duration.ofHours(2).toInterval(),
                            days = setOf(
                                DayOfWeek.MONDAY,
                                DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY,
                                DayOfWeek.FRIDAY
                            )
                        ),
                        contextSwitch = Duration.ofSeconds(30),
                        isActive = false,
                        color = null,
                        executionAlarm = true
                    )
                )
            )
        )
    }

    @Test
    fun useDefaultDaysWhenNoneAreSelected() {
        showNewTask(firstDayOfWeek = DayOfWeek.SUNDAY)

        textField("Task name").performTextReplacement("test-task")
        textField("Goal, category or group for the task").performTextReplacement("test-goal")
        click("Repeating")
        expand()

        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, label ->
            val occurrence = listOf("S", "M", "T", "W", "T", "F", "S").take(index).count { it == label }
            composeRule.onAllNodesWithText(label)[occurrence].performScrollTo().performClick()
        }

        click("Create")

        assertThat(
            (created.single().schedule as Task.Schedule.Repeating).days,
            equalTo(Task.Schedule.Repeating.DefaultDays)
        )
    }

    @Test
    fun suggestExistingGoals() {
        showNewTask(goals = listOf("work", "Home chores", "workout", "work"))

        textField("Goal, category or group for the task").performTextReplacement("wo")

        composeRule.onAllNodesWithText("work").assertCountEquals(1)
        composeRule.onNodeWithText("workout").assertExists()
        composeRule.onNodeWithText("Home chores").assertDoesNotExist()

        textField("wo").performTextReplacement("ch")
        composeRule.onNodeWithText("Home chores").performClick()

        textField("Home chores").assertExists()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun resetColorsToDefault() {
        val task = Task(
            id = 1,
            name = "test-task",
            description = "",
            goal = "test-goal",
            schedule = Task.Schedule.Once(instant = default),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true,
            color = 0xFF123456.toInt()
        )

        val state = TaskFormState.of(task = task, default = default, defaultColor = defaultColor)

        composeRule.setContent {
            EventuallyTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TaskForm(
                        state = state,
                        goals = emptyList(),
                        dateTimeFormat = Settings.DateTimeFormat.Iso,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                        operation = R.string.existing_task_update_action,
                        onOperation = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Show more").assertDoesNotExist()
        click("Calendar Event Color")
        composeRule.onNodeWithText("Reset").performClick()
        composeRule.onNodeWithText("Reset").assertDoesNotExist()

        assertThat(state.toTask(id = task.id, defaultColor = defaultColor).color, equalTo(null))
    }

    @Test
    fun promptForFullScreenAlarmsWhenNotAllowed() {
        var requested = 0
        val state = TaskFormState.of(task = null, default = default, defaultColor = defaultColor)
        state.expanded = true

        showForm(state = state, fullScreenAlarms = FullScreenAlarms(allowed = false) { requested++ })

        composeRule.onNodeWithText(FullScreenAlarmsDenied).assertDoesNotExist()

        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().performClick()
        composeRule.onNodeWithText(FullScreenAlarmsDenied)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithContentDescription("Allow full-screen alarms").assertExists()

        click("Allow")
        assertThat(requested, equalTo(1))

        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().performClick()
        composeRule.onNodeWithText(FullScreenAlarmsDenied).assertDoesNotExist()
    }

    @Test
    fun notPromptForFullScreenAlarmsWhenAllowed() {
        val state = TaskFormState.of(task = null, default = default, defaultColor = defaultColor)
        state.expanded = true
        state.executionAlarm = true

        showForm(state = state, fullScreenAlarms = FullScreenAlarms(allowed = true) {})

        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().assertIsOn()
        composeRule.onNodeWithText(FullScreenAlarmsDenied).assertDoesNotExist()
    }

    @Test
    fun allowFullScreenAlarmsBeforeAndroid14() {
        showNewTask()
        expand()

        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().performClick()
        composeRule.onNodeWithText(FullScreenAlarmsDenied).assertDoesNotExist()
    }

    @Test
    fun keepInputWhenRecreated() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            EventuallyTheme {
                NewTaskScreen(
                    goals = emptyList(),
                    default = default,
                    dateTimeFormat = Settings.DateTimeFormat.Iso,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    onCreate = {},
                    onBack = {}
                )
            }
        }

        textField("Task name").performTextReplacement("test-task")
        click("Repeating")
        expand()
        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        textField("test-task").assertExists()
        composeRule.onNodeWithText("Repeating").assertIsSelected()
        composeRule.onNodeWithText("Description (optional)").assertExists()
        composeRule.onNode(isToggleable() and hasText(ExecutionAlarm)).performScrollTo().assertIsOn()
    }

    @Test
    fun stackInputsOnNarrowScreens() {
        showNewTask()
        expand()

        val (name, schedule, description, contextSwitch) = listOf(
            "Name (required)",
            "Schedule",
            "Description (optional)",
            "Context Switch (required)"
        ).map(::bounds)

        assertThat(name.left, equalTo(schedule.left))
        assertThat(description.left, equalTo(name.left))
        assertThat(contextSwitch.left, equalTo(name.left))
        assertThat(name.top < schedule.top, equalTo(true))
        assertThat(schedule.top < description.top, equalTo(true))
        assertThat(description.top < contextSwitch.top, equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun placeInputsInColumnsOnWideScreens() {
        showNewTask()
        expand()

        val (name, schedule, description, contextSwitch) = listOf(
            "Name (required)",
            "Schedule",
            "Description (optional)",
            "Context Switch (required)"
        ).map(::bounds)

        assertThat(name.top, equalTo(schedule.top))
        assertThat(name.right < schedule.left, equalTo(true))
        assertThat(description.left, equalTo(name.left))
        assertThat(description.top > name.top, equalTo(true))
        assertThat(contextSwitch.left, equalTo(schedule.left))
        assertThat(contextSwitch.top > schedule.top, equalTo(true))
        assertThat(bounds("Create").top > maxOf(description.bottom, contextSwitch.bottom), equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun alignRowsOnWideScreens() {
        showNewTask()

        val name = textField("Task name").getUnclippedBoundsInRoot()

        assertThat(bounds("Once").height, equalTo(name.height))
        assertThat(bounds("Repeating").height, equalTo(name.height))
        assertThat(bounds("Category (required)").top, equalTo(bounds("At").top))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun resizeDescriptionWithScheduleOnWideScreens() {
        showNewTask()
        expand()

        val once = textField("Extra task information").getUnclippedBoundsInRoot()
        val onceOffset = once.top - bounds("Name (required)").top

        click("Repeating")

        val repeating = textField("Extra task information").getUnclippedBoundsInRoot()
        val repeatingOffset = repeating.top - bounds("Name (required)").top

        assertThat(onceOffset, equalTo(repeatingOffset))
        assertThat(repeating.height > once.height, equalTo(true))
        assertThat(repeating.bottom > bounds("Every").bottom, equalTo(true))
    }

    @Test
    fun centerColorInputOnNarrowScreens() {
        showNewTask()
        expand()

        assertCentered(bounds("Calendar Event Color"))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun centerColorInputOnWideScreens() {
        showNewTask()
        expand()

        assertCentered(bounds("Calendar Event Color"))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun groupColumnsForAccessibilityOnWideScreens() {
        showNewTask()
        expand()

        val group = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)
        val details = hasAnyDescendant(hasText("Name (required)"))
        val schedule = hasAnyDescendant(hasText("Schedule"))

        composeRule.onNode(group and details and !schedule).assertExists()
        composeRule.onNode(group and schedule and !details).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun keepDialogsWhenLayoutChanges() {
        val restorationTester = StateRestorationTester(composeRule)
        var wide = false

        restorationTester.setContent {
            EventuallyTheme {
                Box(modifier = if (wide) Modifier.fillMaxSize() else Modifier.width(NarrowWidth)) {
                    NewTaskScreen(
                        goals = emptyList(),
                        default = default,
                        dateTimeFormat = Settings.DateTimeFormat.Iso,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                        onCreate = {},
                        onBack = {}
                    )
                }
            }
        }

        textField("Task name").performTextReplacement("test-task")
        click("10:20")
        composeRule.onNode(isDialog()).assertExists()

        wide = true
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(isDialog()).assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        textField("test-task").assertExists()

        expand()
        click("Calendar Event Color")
        composeRule.onNode(isDialog()).assertExists()

        wide = false
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(isDialog()).assertExists()
        composeRule.onNodeWithText("Description (optional)").assertExists()
    }

    @Test
    fun convertExistingTasksWithoutChanges() {
        val start = LocalDateTime.of(2026, 3, 4, 5, 6).atZone(zone).toInstant()

        val tasks = listOf(
            Task(
                id = 1,
                name = "once",
                description = "description",
                goal = "goal",
                schedule = Task.Schedule.Once(instant = start),
                contextSwitch = Duration.ofMinutes(5),
                isActive = true,
                color = 0xFF123456.toInt()
            ),
            Task(
                id = 2,
                name = "repeating-days",
                description = "",
                goal = "goal",
                schedule = Task.Schedule.Repeating(
                    start = start,
                    every = Period.ofDays(2).toInterval(),
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
                ),
                contextSwitch = Duration.ofHours(1),
                isActive = false,
                color = null,
                executionAlarm = true
            ),
            Task(
                id = 3,
                name = "repeating-minutes",
                description = "",
                goal = "goal",
                schedule = Task.Schedule.Repeating(
                    start = start,
                    every = Duration.ofMinutes(90).toInterval(),
                    days = Task.Schedule.Repeating.DefaultDays
                ),
                contextSwitch = Duration.ofSeconds(30),
                isActive = true,
                color = null
            ),
            Task(
                id = 4,
                name = "repeating-months",
                description = "",
                goal = "goal",
                schedule = Task.Schedule.Repeating(
                    start = start,
                    every = Period.ofMonths(3).toInterval(),
                    days = setOf(DayOfWeek.FRIDAY)
                ),
                contextSwitch = Duration.ofDays(1),
                isActive = true,
                color = null
            ),
            Task(
                id = 5,
                name = "repeating-hours",
                description = "",
                goal = "goal",
                schedule = Task.Schedule.Repeating(
                    start = start,
                    every = Duration.ofHours(24).toInterval(),
                    days = Task.Schedule.Repeating.DefaultDays
                ),
                contextSwitch = Duration.ofMinutes(15),
                isActive = true,
                color = null
            )
        )

        tasks.forEach { task ->
            val state = TaskFormState.of(task = task, default = default, defaultColor = defaultColor)

            assertThat(state.validate(), equalTo(true))
            assertThat(state.expandable, equalTo(false))
            assertThat(state.expanded, equalTo(true))
            assertThat(state.toTask(id = task.id, defaultColor = defaultColor), equalTo(task))
        }
    }

    @Test
    fun ignoreSecondsInExistingInstants() {
        val task = Task(
            id = 1,
            name = "once",
            description = "",
            goal = "goal",
            schedule = Task.Schedule.Once(instant = LocalDateTime.of(2026, 3, 4, 5, 6, 7).atZone(zone).toInstant()),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true,
            color = null
        )

        val converted = TaskFormState.of(task = task, default = default, defaultColor = defaultColor)
            .toTask(id = task.id, defaultColor = defaultColor)

        assertThat(
            converted.schedule,
            equalTo(Task.Schedule.Once(instant = LocalDateTime.of(2026, 3, 4, 5, 6).atZone(zone).toInstant()))
        )
    }

    private fun showNewTask(goals: List<String> = emptyList(), firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY) {
        composeRule.setContent {
            EventuallyTheme {
                NewTaskScreen(
                    goals = goals,
                    default = default,
                    dateTimeFormat = Settings.DateTimeFormat.Iso,
                    firstDayOfWeek = firstDayOfWeek,
                    onCreate = { created.add(it) },
                    onBack = {}
                )
            }
        }
    }

    private fun showForm(state: TaskFormState, fullScreenAlarms: FullScreenAlarms) {
        composeRule.setContent {
            EventuallyTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TaskForm(
                        state = state,
                        goals = emptyList(),
                        dateTimeFormat = Settings.DateTimeFormat.Iso,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                        operation = R.string.existing_task_update_action,
                        onOperation = {},
                        fullScreenAlarms = fullScreenAlarms
                    )
                }
            }
        }
    }

    private fun textField(text: String): SemanticsNodeInteraction =
        composeRule.onNode(
            hasSetTextAction() and (hasText(text) or hasAnyDescendant(hasText(text))),
            useUnmergedTree = true
        ).performScrollTo()

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun validationErrors(
        every: Pair<String, ChronoUnit> = "1" to ChronoUnit.DAYS,
        contextSwitch: Pair<String, ChronoUnit> = "15" to ChronoUnit.MINUTES,
        days: Set<DayOfWeek> = Task.Schedule.Repeating.DefaultDays
    ): Set<TaskFormError> {
        val state = repeatingState(every = every, contextSwitch = contextSwitch, days = days)
        state.validate()

        return state.errors
    }

    private fun repeatingState(
        every: Pair<String, ChronoUnit>,
        contextSwitch: Pair<String, ChronoUnit> = "15" to ChronoUnit.MINUTES,
        days: Set<DayOfWeek>
    ): TaskFormState {
        val state = TaskFormState.of(task = null, default = default, defaultColor = defaultColor)

        state.name = "test-task"
        state.goal = "test-goal"
        state.scheduleType = ScheduleType.Repeating
        state.everyAmount = every.first
        state.everyUnit = every.second
        state.contextSwitchAmount = contextSwitch.first
        state.contextSwitchUnit = contextSwitch.second
        state.days = days

        return state
    }

    private fun menuItem(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasAnyAncestor(isPopup()) and hasText(text))

    private fun expand() {
        composeRule.onNodeWithContentDescription("Show more").performScrollTo().performClick()
    }

    private fun bounds(text: String): DpRect =
        composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()

    private fun assertCentered(bounds: DpRect) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val offset = (bounds.left + bounds.right) / 2 - (root.left + root.right) / 2

        assertThat(abs(offset.value) <= 1f, equalTo(true))
    }

    companion object {
        private const val Landscape: String = "w800dp-h360dp-land"
        private const val ExecutionAlarm: String = "Alarm when due"
        private const val FullScreenAlarmsDenied: String =
            "Full-screen alarms are not allowed; alarms will only show as notifications"
        private val NarrowWidth = 360.dp
    }
}
