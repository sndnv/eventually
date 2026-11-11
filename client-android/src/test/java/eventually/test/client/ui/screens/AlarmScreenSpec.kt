package eventually.test.client.ui.screens

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eventually.client.settings.Settings
import eventually.client.ui.screens.AlarmScreen
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
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class AlarmScreenSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private var postponed = 0
    private var dismissed = 0

    @Test
    fun showAlarmDetails() {
        showAlarm(task)

        composeRule.onNodeWithText("10:30").assertExists()
        composeRule.onNodeWithText("test-task").assertExists()
        composeRule.onNodeWithText("test-goal").assertExists()
        composeRule.onNodeWithText("test-description").assertExists()
        composeRule.onNodeWithText("Postpone by 10 minutes").assertExists()
        composeRule.onNodeWithText("Dismiss").assertExists()
    }

    @Test
    fun showAlarmsWithoutDescriptions() {
        showAlarm(task.copy(description = ""), postponeLength = Duration.ofMinutes(1))

        composeRule.onNodeWithText("test-task").assertExists()
        composeRule.onNodeWithText("test-description").assertDoesNotExist()
        composeRule.onNodeWithText("Postpone by 1 minute").assertExists()
    }

    @Test
    fun postponeAlarms() {
        showAlarm(task)

        composeRule.onNodeWithText("Postpone by 10 minutes").performClick()

        assertThat(postponed, equalTo(1))
        assertThat(dismissed, equalTo(0))
    }

    @Test
    fun dismissAlarms() {
        showAlarm(task)

        composeRule.onNodeWithText("Dismiss").performClick()

        assertThat(postponed, equalTo(0))
        assertThat(dismissed, equalTo(1))
    }

    @Test
    fun stackActionsBelowDetailsInPortrait() {
        showAlarm(task)

        val name = composeRule.onNodeWithText("test-task").getUnclippedBoundsInRoot()
        val postpone = composeRule.onNodeWithText("Postpone by 10 minutes").getUnclippedBoundsInRoot()
        val dismiss = composeRule.onNodeWithText("Dismiss").getUnclippedBoundsInRoot()

        assertThat(postpone.top > name.bottom, equalTo(true))
        assertThat(dismiss.top > postpone.bottom, equalTo(true))
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun placeActionsNextToDetailsInLandscape() {
        showAlarm(task)

        val name = composeRule.onNodeWithText("test-task").getUnclippedBoundsInRoot()
        val postpone = composeRule.onNodeWithText("Postpone by 10 minutes").getUnclippedBoundsInRoot()
        val dismiss = composeRule.onNodeWithText("Dismiss").getUnclippedBoundsInRoot()

        assertThat(postpone.left > name.right, equalTo(true))
        assertThat(dismiss.left > name.right, equalTo(true))
        assertThat(dismiss.top > postpone.bottom, equalTo(true))
    }

    private fun showAlarm(task: Task, postponeLength: Duration = Duration.ofMinutes(10)) {
        composeRule.setContent {
            EventuallyTheme {
                AlarmScreen(
                    task = task,
                    execution = execution,
                    dateTimeFormat = Settings.DateTimeFormat.Iso,
                    postponeLength = postponeLength,
                    onPostpone = { postponed++ },
                    onDismiss = { dismissed++ }
                )
            }
        }
    }

    private val execution = LocalDateTime.of(2026, 1, 2, 10, 30).atZone(ZoneId.systemDefault()).toInstant()

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Once(instant = execution),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = null,
        executionAlarm = true
    )
}
