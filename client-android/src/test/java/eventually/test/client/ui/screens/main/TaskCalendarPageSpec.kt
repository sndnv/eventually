package eventually.test.client.ui.screens.main

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import eventually.client.R
import eventually.client.settings.Settings
import eventually.client.ui.LayoutExtensions.isLandscape
import eventually.client.ui.screens.main.TaskCalendarPage
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TaskCalendarPageSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Int>()
    private val created = mutableListOf<Instant>()

    @Test
    fun showTasksForSelectedDays() {
        show()

        composeRule.onNodeWithContentDescription(today.toString()).performClick()

        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText(today.toString())).assertExists()

        val items = composeRule.onAllNodes(hasAnyAncestor(isDialog()) and hasText(" - task-", substring = true))
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].first() }

        assertThat(
            items.map { it.text },
            equalTo(listOf("● 08:00 - task-2 (goal-2)", "● 12:00 - task-1 (goal-1)"))
        )

        assertThat(
            items.map { item -> item.spanStyles.first { item.substring(it.start, it.end) == "●" }.item.color },
            equalTo(listOf(Color(CustomColor), Color(context.getColor(R.color.calendar_event))))
        )
    }

    @Test
    fun openTasksFromSelectedDays() {
        show()

        composeRule.onNodeWithContentDescription(today.toString()).performClick()
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText("● 12:00 - task-1 (goal-1)")).performClick()

        assertThat(opened.toList(), equalTo(listOf(1)))
        assertThat(created.toList(), equalTo(emptyList()))
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun createTasksFromSelectedDays() {
        show()

        composeRule.onNodeWithContentDescription(today.toString()).performClick()
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText("Create Task")).performClick()

        assertThat(created.toList(), equalTo(listOf(today.atStartOfDay(ZoneId.systemDefault()).toInstant())))
        assertThat(opened.toList(), equalTo(emptyList()))
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun createTasksOnDaysWithoutTasks() {
        val empty = today.withDayOfMonth(if (today.dayOfMonth == 1) 2 else 1)

        show()

        composeRule.onNodeWithContentDescription(empty.toString()).performClick()

        assertThat(created.toList(), equalTo(listOf(empty.atStartOfDay(ZoneId.systemDefault()).toInstant())))
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun startWeeksOnConfiguredDay() {
        show(firstDayOfWeek = DayOfWeek.MONDAY)
        assertThat(left("Mon") < left("Sun"), equalTo(true))
    }

    @Test
    fun startWeeksOnSunday() {
        show(firstDayOfWeek = DayOfWeek.SUNDAY)
        assertThat(left("Sun") < left("Mon"), equalTo(true))
    }

    @Test
    fun scrollByYears() {
        val current = YearMonth.now()

        show()

        composeRule.onNodeWithText(current.year.toString()).assertExists()
        composeRule.onNodeWithText(current.header()).assertExists()

        composeRule.onNodeWithContentDescription("Next year").performClick()

        composeRule.onNodeWithText(current.plusYears(1).year.toString()).assertExists()
        composeRule.onNodeWithText(current.plusYears(1).header()).assertExists()

        composeRule.onNodeWithContentDescription("Previous year").performClick()
        composeRule.onNodeWithContentDescription("Previous year").performClick()

        composeRule.onNodeWithText(current.minusYears(1).year.toString()).assertExists()
        composeRule.onNodeWithText(current.minusYears(1).header()).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun showAgendaForTodayInLandscape() {
        show()

        composeRule.onNodeWithText(today.toString()).assertExists()
        composeRule.onNodeWithContentDescription(today.toString()).assertIsSelected()

        assertThat(
            agendaItems(),
            equalTo(listOf("● 08:00 - task-2 (goal-2)", "● 12:00 - task-1 (goal-1)"))
        )

        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun selectDaysWithTasksInLandscape() {
        show(schedules = schedules + schedule(id = 3, hour = 9, color = null, date = other))

        composeRule.onNodeWithContentDescription(other.toString()).performClick()

        composeRule.onNodeWithText(other.toString()).assertExists()
        composeRule.onNodeWithContentDescription(other.toString()).assertIsSelected()
        composeRule.onNodeWithContentDescription(today.toString()).assertIsNotSelected()
        assertThat(agendaItems(), equalTo(listOf("● 09:00 - task-3 (goal-3)")))

        assertThat(created.toList(), equalTo(emptyList()))
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun openTasksFromAgenda() {
        show()

        composeRule.onNodeWithText("● 12:00 - task-1 (goal-1)").performClick()

        assertThat(opened.toList(), equalTo(listOf(1)))
        assertThat(created.toList(), equalTo(emptyList()))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun createTasksFromAgenda() {
        show(schedules = schedules + schedule(id = 3, hour = 9, color = null, date = other))

        composeRule.onNodeWithContentDescription(other.toString()).performClick()
        composeRule.onNodeWithText("Create Task").performClick()

        assertThat(created.toList(), equalTo(listOf(other.atStartOfDay(ZoneId.systemDefault()).toInstant())))
        assertThat(opened.toList(), equalTo(emptyList()))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun createTasksOnDaysWithoutTasksInLandscape() {
        show()

        composeRule.onNodeWithContentDescription(other.toString()).performClick()

        assertThat(created.toList(), equalTo(listOf(other.atStartOfDay(ZoneId.systemDefault()).toInstant())))
        composeRule.onNodeWithContentDescription(today.toString()).assertIsSelected()
        composeRule.onNodeWithText(today.toString()).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun showEmptyAgendaInLandscape() {
        show(schedules = emptyMap())

        composeRule.onNodeWithText("No Tasks").assertExists()
        composeRule.onNodeWithText("Create Task").performClick()

        assertThat(created.toList(), equalTo(listOf(today.atStartOfDay(ZoneId.systemDefault()).toInstant())))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun scrollByMonthsAndYearsInLandscape() {
        val current = YearMonth.now()

        show()

        composeRule.onNodeWithText(current.header()).assertExists()

        composeRule.onNodeWithContentDescription("Next month").performClick()
        composeRule.onNodeWithText(current.plusMonths(1).header()).assertExists()

        composeRule.onNodeWithContentDescription("Next year").performClick()
        composeRule.onNodeWithText(current.plusMonths(13).header()).assertExists()

        composeRule.onNodeWithContentDescription("Previous year").performClick()
        composeRule.onNodeWithText(current.plusMonths(1).header()).assertExists()

        composeRule.onNodeWithContentDescription("Previous month").performClick()
        composeRule.onNodeWithContentDescription("Previous month").performClick()
        composeRule.onNodeWithText(current.minusMonths(1).header()).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun keepSelectionWhenRecreated() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            Page(schedules = schedules + schedule(id = 3, hour = 9, color = null, date = other))
        }

        composeRule.onNodeWithContentDescription(other.toString()).performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(other.toString()).assertExists()
        composeRule.onNodeWithContentDescription(other.toString()).assertIsSelected()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = Square)
    fun keepStateWhenOrientationChanges() {
        val restorationTester = StateRestorationTester(composeRule)
        val current = YearMonth.now()
        var landscape = false

        restorationTester.setContent {
            Box(modifier = Modifier.size(if (landscape) LandscapeSize else PortraitSize)) {
                Page(schedules = schedules + schedule(id = 3, hour = 9, color = null, date = other))
            }
        }

        composeRule.onNodeWithContentDescription(other.toString()).performClick()
        composeRule.onNode(isDialog()).assertExists()

        landscape = true
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithText(other.toString()).assertExists()
        composeRule.onNodeWithContentDescription(other.toString()).assertIsSelected()

        composeRule.onNodeWithContentDescription("Next month").performClick()

        landscape = false
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithText(current.plusMonths(1).year.toString()).assertExists()
        composeRule.onNodeWithText(current.plusMonths(1).header()).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun keepPortraitLayoutWhenNotInLandscape() {
        composeRule.setContent { Page(landscape = false) }

        composeRule.onNodeWithText(YearMonth.now().year.toString()).assertExists()
        composeRule.onNodeWithContentDescription("Next month").assertDoesNotExist()
        composeRule.onNodeWithText("Create Task").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun scrollByMonthsQuicklyInLandscape() {
        val current = YearMonth.now()

        show()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Next month").performClick()
        composeRule.onNodeWithContentDescription("Next month").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText(current.plusMonths(2).header()).assertExists()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText(current.plusMonths(2).header()).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun keepMonthTitleWhileDraggingInLandscape() {
        val current = YearMonth.now()

        show()

        val calendar = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasContentDescription(today.toString())))

        calendar.performTouchInput {
            down(center)
            moveBy(Offset(width * PartialDrag, 0f))
        }

        composeRule.onNodeWithText(current.header()).assertExists()

        calendar.performTouchInput { up() }

        composeRule.onNodeWithText(current.header()).assertExists()
    }

    @Test
    @Config(qualifiers = Landscape)
    fun scrollFromShownMonthWhileSettlingInLandscape() {
        val current = YearMonth.now()

        show()

        val calendar = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasContentDescription(today.toString())))

        calendar.performTouchInput {
            down(center)
            moveBy(Offset(width * PartialDrag, 0f))
        }

        composeRule.mainClock.autoAdvance = false
        calendar.performTouchInput { up() }
        composeRule.onNodeWithContentDescription("Previous month").performClick()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText(current.minusMonths(1).header()).assertExists()
    }

    @Test
    @Config(qualifiers = TallLandscape)
    fun centerDaysInRowsInLandscape() {
        val first = today.withDayOfMonth(1)

        show()

        val header = composeRule.onNodeWithText("Mon").getUnclippedBoundsInRoot()
        val day = composeRule.onNodeWithContentDescription(first.toString()).getUnclippedBoundsInRoot()
        val nextWeek = composeRule.onNodeWithContentDescription(first.plusDays(7).toString()).getUnclippedBoundsInRoot()
        val rowHeight = nextWeek.top - day.top
        val offset = (day.top + day.bottom) / 2 - (header.bottom + rowHeight / 2)

        assertThat(day.width, equalTo(day.height))
        assertThat(day.height < rowHeight, equalTo(true))
        assertThat(abs(offset.value) <= 1f, equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun showTaskColorsAsRingInLandscape() {
        val colors = listOf(0xFFF44336, 0xFF4CAF50, 0xFF2196F3, 0xFFFFEB3B).map { it.toInt() }

        show(
            schedules = colors
                .mapIndexed { index, color -> schedule(id = index + 3, hour = 9 + index, color = color, date = other) }
                .toMap()
        )

        val image = composeRule.onNodeWithContentDescription(other.toString()).captureToImage().toPixelMap()

        assertThat(colors.map { image.contains(Color(it)) }, equalTo(listOf(true, true, true, false)))
        assertThat(image.contains(Color(colors.first()), rows = 0 until image.height / 2), equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun groupPanesForAccessibilityInLandscape() {
        show()

        val group = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)
        val calendar = hasAnyDescendant(hasContentDescription("Next month"))
        val agenda = hasAnyDescendant(hasText("Create Task"))

        composeRule.onNode(group and calendar and !agenda).assertExists()
        composeRule.onNode(group and agenda and !calendar).assertExists()
    }

    private fun show(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY, schedules: Map<Int, TaskSchedule> = this.schedules) {
        composeRule.setContent { Page(firstDayOfWeek = firstDayOfWeek, schedules = schedules) }
    }

    @Composable
    private fun Page(
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        schedules: Map<Int, TaskSchedule> = this.schedules,
        landscape: Boolean? = null
    ) {
        EventuallyTheme {
            BoxWithConstraints {
                TaskCalendarPage(
                    schedules = schedules,
                    firstDayOfWeek = firstDayOfWeek,
                    dateTimeFormat = Settings.DateTimeFormat.Iso,
                    landscape = landscape ?: isLandscape,
                    onOpenTask = { opened.add(it) },
                    onCreateTask = { created.add(it) }
                )
            }
        }
    }

    private fun agendaItems(): List<String> =
        composeRule.onAllNodes(hasText(" - task-", substring = true))
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].first().text }

    private fun PixelMap.contains(color: Color, rows: IntRange = 0 until height): Boolean =
        (0 until width).any { x -> rows.any { y -> this[x, y].isCloseTo(color) } }

    private fun Color.isCloseTo(other: Color): Boolean =
        abs(red - other.red) < ColorTolerance &&
            abs(green - other.green) < ColorTolerance &&
            abs(blue - other.blue) < ColorTolerance

    private fun left(text: String): Float =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left

    private fun YearMonth.header(): String =
        format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))

    private fun schedule(id: Int, hour: Int, color: Int?, date: LocalDate = today): Pair<Int, TaskSchedule> {
        val instant = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant()
        val instance = TaskInstance(instant = instant)

        val task = Task(
            id = id,
            name = "task-$id",
            description = "",
            goal = "goal-$id",
            schedule = Task.Schedule.Once(instant),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true,
            color = color
        )

        return id to TaskSchedule(task = task, instances = mapOf(instance.id to instance), dismissed = emptyList())
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val today: LocalDate = LocalDate.now()
    private val other: LocalDate = today.withDayOfMonth(if (today.dayOfMonth == 1) 2 else 1)

    private val schedules = mapOf(
        schedule(id = 1, hour = 12, color = null),
        schedule(id = 2, hour = 8, color = CustomColor)
    )

    companion object {
        private const val CustomColor: Int = 0xFF2196F3.toInt()
        private const val Landscape: String = "w800dp-h360dp-land"
        private const val TallLandscape: String = "w800dp-h600dp-land"
        private const val Square: String = "w800dp-h800dp"
        private val LandscapeSize = DpSize(800.dp, 360.dp)
        private val PortraitSize = DpSize(360.dp, 800.dp)
        private const val PartialDrag: Float = 0.3f
        private const val ColorTolerance: Float = 0.02f
    }
}
