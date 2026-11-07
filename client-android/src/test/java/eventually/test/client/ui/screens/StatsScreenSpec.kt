package eventually.test.client.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import eventually.client.persistence.Converters.Companion.toJson
import eventually.client.persistence.DataExport
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.ui.screens.Stats
import eventually.client.ui.screens.StatsScreen
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class StatsScreenSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val imported = mutableListOf<DataExport>()
    private var evaluationsForced = 0

    @Test
    fun showStats() {
        showStats(stats)

        composeRule.onNodeWithText("Total: 2 (enabled: 1, disabled: 1)").assertExists()
        composeRule.onNodeWithText("Total: 1 (with 2 instance(s))").assertExists()
        composeRule.onNodeWithText("Total: 3 (context: 1, execution: 2)").assertExists()
        composeRule.onNodeWithText(lastEvaluation.formatted()).assertExists()
        composeRule.onNodeWithText(nextEvaluation.formatted()).assertExists()
        composeRule.onNodeWithText("Total: 5").assertExists()
    }

    @Test
    fun showTitlesBeforeStatsAreAvailable() {
        showStats(Stats())

        listOf(
            "Tasks",
            "Task Schedules",
            "Notifications",
            "Last Evaluation",
            "Next Evaluation",
            "Evaluations"
        ).forEach { title -> composeRule.onNodeWithText(title).assertExists() }

        composeRule.onNode(hasText("Total:", substring = true)).assertDoesNotExist()
    }

    @Test
    fun exportDataToClipboard() {
        showStats(stats)

        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNodeWithText(export.toJson()).assertExists()
        composeRule.onNodeWithText("Copy").performClick()

        val clip = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
            .primaryClip

        assertThat(clip?.description?.label?.toString(), equalTo("Data export"))
        assertThat(clip?.getItemAt(0)?.text?.toString(), equalTo(export.toJson()))
        assertThat(ShadowToast.getTextOfLatestToast(), equalTo("Copied to clipboard"))

        composeRule.onNodeWithText("Copy").assertDoesNotExist()
    }

    @Test
    fun showDialogTitles() {
        showStats(stats)

        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText("Export")).assertExists()
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()

        composeRule.onNodeWithText("Import").performClick()
        composeRule.onNode(hasAnyAncestor(isDialog()) and hasText("Import")).assertExists()
    }

    @Test
    fun exportEmptyDataBeforeStatsAreAvailable() {
        showStats(Stats())

        composeRule.onNodeWithText("Export").performClick()
        composeRule.onNodeWithText("""{"tasks":[],"schedules":[],"notifications":[]}""").assertExists()
    }

    @Test
    fun importCurrentAndLegacyExports() {
        showStats(Stats())

        importJson(export.toJson())
        composeRule.onNodeWithText("Load").assertDoesNotExist()

        importJson(legacyExport)
        composeRule.onNodeWithText("Load").assertDoesNotExist()

        assertThat(imported.toList(), equalTo(listOf(export, legacyExpected)))
    }

    @Test
    fun rejectInvalidImports() {
        showStats(Stats())

        composeRule.onNodeWithText("Import").performClick()

        listOf("", "{", "{}", "not-json", """{"tasks":[]}""").forEach { invalid ->
            composeRule.onNode(hasSetTextAction()).performTextReplacement(invalid)
            composeRule.onNodeWithText("Load").performClick()
            composeRule.onNodeWithText("Invalid data provided").assertExists()
        }

        assertThat(imported.toList(), equalTo(emptyList()))
    }

    @Test
    fun keepOpenDialogsWhenRecreated() {
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            EventuallyTheme {
                StatsScreen(stats = stats, onImport = {}, onForceEvaluation = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("Export").performClick()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText(export.toJson()).assertExists()
        composeRule.onNodeWithText("Copy").assertExists()
    }

    @Test
    fun forceEvaluation() {
        showStats(stats)

        composeRule.onNodeWithText("Force Evaluation").performScrollTo().performClick()

        assertThat(evaluationsForced, equalTo(1))
    }

    private fun showStats(stats: Stats) {
        composeRule.setContent {
            EventuallyTheme {
                StatsScreen(
                    stats = stats,
                    onImport = { imported.add(it) },
                    onForceEvaluation = { evaluationsForced += 1 },
                    onBack = {}
                )
            }
        }
    }

    private fun importJson(json: String) {
        composeRule.onNodeWithText("Import").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(json)
        composeRule.onNodeWithText("Load").performClick()
    }

    private fun Instant.formatted(): String =
        atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private val start = Instant.ofEpochSecond(1700000000)

    private val tasks = listOf(
        Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = start,
                every = Duration.ofMinutes(20).toInterval(),
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true,
            color = 1
        ),
        Task(
            id = 43,
            name = "other-task",
            description = "other-description",
            goal = "other-goal",
            schedule = Task.Schedule.Once(instant = start.plusSeconds(3600)),
            contextSwitch = Duration.ofMinutes(10),
            isActive = false,
            color = null
        )
    )

    private val instances = listOf(
        TaskInstance(id = UUID.randomUUID(), instant = start.plusSeconds(1200), postponed = null),
        TaskInstance(id = UUID.randomUUID(), instant = start.plusSeconds(2400), postponed = Duration.ofMinutes(10))
    )

    private val schedules = listOf(
        TaskScheduleEntity(
            task = 42,
            instances = instances.associateBy { it.id },
            dismissed = listOf(start.plusSeconds(600))
        )
    )

    private val notifications = listOf(
        NotificationEntity(id = 1, task = 42, instance = instances[0].id, type = "context", hash = 1),
        NotificationEntity(id = 2, task = 42, instance = instances[0].id, type = "execution", hash = 2),
        NotificationEntity(id = 3, task = 42, instance = instances[1].id, type = "execution", hash = 3)
    )

    private val lastEvaluation = Instant.ofEpochSecond(1700001000)
    private val nextEvaluation = Instant.ofEpochSecond(1700002000)

    private val stats = Stats(
        tasks = tasks,
        schedules = schedules,
        notifications = notifications,
        lastEvaluation = lastEvaluation,
        nextEvaluation = nextEvaluation,
        evaluations = 5
    )

    private val export = DataExport(tasks = tasks, schedules = schedules, notifications = notifications)

    private val legacyExport =
        """{"tasks":[{"id":42,"name":"test-task","description":"test-description","goal":"test-goal",""" +
            """"schedule":{"type":"repeating","start":${start.epochSecond},""" +
            """"every":{"unit":"seconds","amount":1200},"days":[1,5]},""" +
            """"context_switch":300,"is_active":true}],"schedules":[],"notifications":[]}"""

    private val legacyExpected = DataExport(
        tasks = listOf(tasks[0].copy(color = null)),
        schedules = emptyList(),
        notifications = emptyList()
    )
}
