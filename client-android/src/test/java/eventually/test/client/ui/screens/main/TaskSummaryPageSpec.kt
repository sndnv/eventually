package eventually.test.client.ui.screens.main

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import eventually.client.settings.Settings
import eventually.client.ui.screens.main.TaskSummaryPage
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TaskSummaryPageSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Int>()

    @Test
    fun showUpcomingAndExpiredTasks() {
        show(summary = TaskSummary(expired = listOf(expired), upcoming = listOf(upcoming), nextEvent = null))

        composeRule.onNodeWithText("12:00 - task-1 (goal-1)", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2026-01-02 at 10:20 - task-2 (goal-2)", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("One task expected in the next 15 minutes.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("One task expired since 2026-01-02 at 10:20", useUnmergedTree = true).assertExists()
    }

    @Test
    fun styleSummaryItems() {
        show(summary = TaskSummary(expired = emptyList(), upcoming = listOf(upcoming), nextEvent = null))

        val item = text("12:00 - task-1 (goal-1)")

        assertThat(
            item.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { item.substring(it.start, it.end) },
            equalTo(listOf("12:00"))
        )
        assertThat(
            item.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }.map { item.substring(it.start, it.end) },
            equalTo(listOf("goal-1"))
        )
    }

    @Test
    fun showEmptySummary() {
        show(summary = TaskSummary.empty())

        composeRule.onNodeWithText("None", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("No tasks expected in the next 15 minutes.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun showSummarySizeUnits() {
        show(summary = TaskSummary.empty(), summarySize = Duration.ofHours(1))
        composeRule.onNodeWithText("No tasks expected in the next hour.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun showMultipleTasksInFooters() {
        val upcoming = (1..2).map { item(id = it, at = today.atTime(12, it)) }
        val expired = (3..4).map { item(id = it, at = pastDate.plusMinutes(it.toLong())) }

        show(summary = TaskSummary(expired = expired, upcoming = upcoming, nextEvent = null), summarySize = Duration.ofHours(2))

        composeRule.onNodeWithText("2 tasks expected in the next 2 hours.", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2 tasks expired since 2026-01-02 at 10:23", useUnmergedTree = true).assertExists()
    }

    @Test
    fun truncateLongSummaries() {
        val upcoming = (1..9).map { item(id = it, at = today.atTime(12, it)) }.reversed()

        show(summary = TaskSummary(expired = emptyList(), upcoming = upcoming, nextEvent = null), maxTasks = 7)

        listOf(1, 2, 3, 7, 8, 9).forEach { id ->
            composeRule.onNodeWithText("12:0$id - task-$id (goal-$id)", useUnmergedTree = true).assertExists()
        }

        listOf(4, 5, 6).forEach { id ->
            composeRule.onNodeWithText("12:0$id - task-$id (goal-$id)", useUnmergedTree = true).assertDoesNotExist()
        }

        composeRule.onNodeWithText("...", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("9 tasks expected in the next 15 minutes.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun openUpcomingTasks() {
        show(summary = TaskSummary(expired = listOf(expired), upcoming = listOf(upcoming), nextEvent = null))

        composeRule.onNodeWithText("Upcoming").performClick()
        dialogItem("12:00 - task-1 (goal-1)").performClick()

        assertThat(opened.toList(), equalTo(listOf(1)))
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun openExpiredTasks() {
        show(summary = TaskSummary(expired = listOf(expired), upcoming = listOf(upcoming), nextEvent = null))

        composeRule.onNodeWithText("Overdue").performClick()
        dialogItem("2026-01-02 at 10:20 - task-2 (goal-2)").performClick()

        assertThat(opened.toList(), equalTo(listOf(2)))
    }

    @Test
    fun notOpenEmptyLists() {
        show(summary = TaskSummary.empty())

        composeRule.onNodeWithText("Upcoming").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()

        composeRule.onNodeWithText("Overdue").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun showTimelineForTodayAndTomorrow() {
        val later = item(id = 3, at = today.atTime(18, 30))
        val tomorrow = item(id = 4, at = today.plusDays(1).atTime(9, 0))

        show(
            summary = TaskSummary.empty(),
            schedules = listOf(tomorrow, expired, later, upcoming).associate { (task, instance) ->
                task.id to TaskSchedule(task = task, instances = mapOf(instance.id to instance), dismissed = emptyList())
            }
        )

        composeRule.onNodeWithText("Show Timeline").performClick()

        composeRule.onNodeWithText("Timeline - Today and Tomorrow").assertExists()

        val items = composeRule.onAllNodes(hasAnyAncestor(isDialog()) and hasText(" - task-", substring = true))
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].first().text }

        assertThat(
            items,
            equalTo(
                listOf(
                    "12:00 - task-1 (goal-1)",
                    "18:30 - task-3 (goal-3)",
                    "Tomorrow at 09:00 - task-4 (goal-4)"
                )
            )
        )

        dialogItem("Tomorrow at 09:00 - task-4 (goal-4)").performClick()

        assertThat(opened.toList(), equalTo(listOf(4)))
    }

    @Test
    fun showEmptyTimeline() {
        val nextWeek = item(id = 5, at = today.plusDays(7).atTime(9, 0))

        show(
            summary = TaskSummary.empty(),
            schedules = listOf(nextWeek).associate { (task, instance) ->
                task.id to TaskSchedule(task = task, instances = mapOf(instance.id to instance), dismissed = emptyList())
            }
        )

        composeRule.onNodeWithText("Show Timeline").performClick()

        composeRule.onNodeWithText("Timeline - Today and Tomorrow").assertExists()
        dialogItem("No Tasks").assertExists()
    }

    @Test
    fun stackCardsOnNarrowScreens() {
        show(summary = TaskSummary(expired = listOf(expired), upcoming = listOf(upcoming), nextEvent = null))

        val upcomingCard = bounds("Upcoming")
        val expiredCard = bounds("Overdue")
        val timeline = bounds("Show Timeline")

        assertThat(upcomingCard.left, equalTo(expiredCard.left))
        assertThat(upcomingCard.bottom < expiredCard.top, equalTo(true))
        assertThat(expiredCard.bottom < timeline.top, equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun showCardsSideBySideOnWideScreens() {
        val upcoming = (1..3).map { item(id = it, at = today.atTime(12, it)) }

        show(summary = TaskSummary(expired = listOf(expired), upcoming = upcoming, nextEvent = null))

        val upcomingCard = bounds("Upcoming")
        val expiredCard = bounds("Overdue")
        val timeline = bounds("Show Timeline")
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        assertThat(upcomingCard.top, equalTo(expiredCard.top))
        assertThat(upcomingCard.height, equalTo(expiredCard.height))
        assertThat(upcomingCard.right < expiredCard.left, equalTo(true))
        assertThat(timeline.top > upcomingCard.bottom, equalTo(true))
        assertThat(abs(timeline.center.x - root.center.x) <= 1f, equalTo(true))
    }

    @Test
    @Config(qualifiers = Landscape)
    fun openTasksOnWideScreens() {
        show(summary = TaskSummary(expired = listOf(expired), upcoming = listOf(upcoming), nextEvent = null))

        composeRule.onNodeWithText("Overdue").performClick()
        dialogItem("2026-01-02 at 10:20 - task-2 (goal-2)").performClick()

        composeRule.onNodeWithText("Upcoming").performClick()
        dialogItem("12:00 - task-1 (goal-1)").performClick()

        assertThat(opened.toList(), equalTo(listOf(2, 1)))
    }

    private fun bounds(text: String): Rect =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun show(
        summary: TaskSummary,
        schedules: Map<Int, TaskSchedule> = emptyMap(),
        summarySize: Duration = Duration.ofMinutes(15),
        maxTasks: Int = 7
    ) {
        composeRule.setContent {
            EventuallyTheme {
                TaskSummaryPage(
                    summary = summary,
                    schedules = schedules,
                    summarySize = summarySize,
                    maxTasks = maxTasks,
                    dateTimeFormat = Settings.DateTimeFormat.Iso,
                    onOpenTask = { opened.add(it) }
                )
            }
        }
    }

    private fun dialogItem(text: String) =
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText(text))

    private fun text(text: String): AnnotatedString =
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].first()

    private fun item(id: Int, at: LocalDateTime): Pair<Task, TaskInstance> {
        val instant = at.atZone(ZoneId.systemDefault()).toInstant()

        return Task(
            id = id,
            name = "task-$id",
            description = "",
            goal = "goal-$id",
            schedule = Task.Schedule.Once(instant),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true,
            color = null
        ) to TaskInstance(instant = instant)
    }

    private val today: LocalDate = LocalDate.now()
    private val pastDate: LocalDateTime = LocalDateTime.of(2026, 1, 2, 10, 20)

    private val upcoming = item(id = 1, at = today.atTime(12, 0))
    private val expired = item(id = 2, at = pastDate)

    companion object {
        private const val Landscape: String = "w800dp-h360dp-land"
    }
}
