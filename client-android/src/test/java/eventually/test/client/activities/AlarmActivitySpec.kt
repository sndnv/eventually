package eventually.test.client.activities

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.AlarmActivity
import eventually.client.activities.AlarmActivity.Companion.toAlarm
import eventually.client.scheduling.NotificationManagerExtensions
import eventually.client.scheduling.NotificationManagerExtensions.createInstanceNotificationChannels
import eventually.client.scheduling.NotificationManagerExtensions.isInstanceExecutionAlarmActive
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionAlarm
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.requireDuration
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireTaskId
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class AlarmActivitySpec {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun createNotificationChannels() {
        notificationManager.createInstanceNotificationChannels(context)
    }

    @Test
    fun showRingingAlarms() {
        ring(task, instance)

        val activity = launch(task, instance).get()

        composeRule.onNodeWithText("test-task").assertExists()
        composeRule.onNodeWithText("test-goal").assertExists()
        composeRule.onNodeWithText("Postpone by 10 minutes").assertExists()
        composeRule.onNodeWithText("Dismiss").assertExists()

        composeRule.mainClock.advanceTimeBy(RingingChecks)

        assertThat(activity.isFinishing, equalTo(false))
        composeRule.onNodeWithText("test-task").assertExists()
    }

    @Test
    fun handleAlarmsOnlyOnce() {
        ring(task, instance)

        val activity = launch(task, instance).get()

        composeRule.onNodeWithText("Postpone by 10 minutes").performClick()
        composeRule.onNodeWithText("Postpone by 10 minutes").performClick()
        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.waitForIdle()

        val application = shadowOf(context as Application)
        assertThat(application.nextStartedService.action, equalTo(SchedulerService.ActionPostpone))
        assertThat(application.nextStartedService, equalTo(null))
        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun showOverLockScreen() {
        val activity = launch(task, instance).get()

        val flags = activity.window.attributes.flags
        val expected = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        assertThat(flags and expected, equalTo(expected))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun showOverLockScreenOnNewerVersions() {
        val activity = launch(task, instance).get()

        assertThat(shadowOf(activity).showWhenLocked, equalTo(true))
        assertThat(shadowOf(activity).turnScreenOn, equalTo(true))
        assertThat(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            equalTo(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        )
    }

    @Test
    fun dismissAlarms() {
        ring(task, instance)

        val activity = launch(task, instance).get()

        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.waitForIdle()

        val started = shadowOf(context as Application).nextStartedService
        assertThat(started.action, equalTo(SchedulerService.ActionDismiss))
        assertThat(started.requireTaskId(SchedulerService.ActionDismissExtraTask), equalTo(task.id))
        assertThat(started.requireInstanceId(SchedulerService.ActionDismissExtraInstance), equalTo(instance.id))

        assertThat(notificationManager.isInstanceExecutionAlarmActive(task.id, instance.id), equalTo(false))
        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun postponeAlarms() {
        ring(task, instance)

        val activity = launch(task, instance).get()

        composeRule.onNodeWithText("Postpone by 10 minutes").performClick()
        composeRule.waitForIdle()

        val started = shadowOf(context as Application).nextStartedService
        assertThat(started.action, equalTo(SchedulerService.ActionPostpone))
        assertThat(started.requireTaskId(SchedulerService.ActionPostponeExtraTask), equalTo(task.id))
        assertThat(started.requireInstanceId(SchedulerService.ActionPostponeExtraInstance), equalTo(instance.id))
        assertThat(started.requireDuration(SchedulerService.ActionPostponeExtraBy), equalTo(Duration.ofMinutes(10)))

        assertThat(notificationManager.isInstanceExecutionAlarmActive(task.id, instance.id), equalTo(false))
        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun closeWhenAlarmsStopRinging() {
        ring(task, instance)

        val activity = launch(task, instance).get()
        composeRule.onNodeWithText("test-task").assertExists()

        composeRule.mainClock.advanceTimeBy(RingingChecks)
        assertThat(activity.isFinishing, equalTo(false))

        notificationManager.cancel(NotificationManagerExtensions.getInstanceNotificationId(task.id, instance.id))

        composeRule.mainClock.advanceTimeBy(RingingCheck)
        assertThat(activity.isFinishing, equalTo(true))

        assertThat(shadowOf(context as Application).nextStartedService, equalTo(null))
    }

    @Test
    fun closeAlarmsThatAreNotRinging() {
        val activity = launch(task, instance).get()

        composeRule.mainClock.advanceTimeBy(RingingCheck)
        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun closeWithoutAlarms() {
        val activity = Robolectric.buildActivity(AlarmActivity::class.java, Intent(context, AlarmActivity::class.java))
            .setup()
            .get()

        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun showNewAlarms() {
        val otherTask = task.copy(id = 43, name = "other-task")
        val otherInstance = TaskInstance(instant = Instant.now().truncatedTo(ChronoUnit.SECONDS))

        ring(task, instance)
        ring(otherTask, otherInstance)

        val controller = launch(task, instance)
        composeRule.onNodeWithText("test-task").assertExists()

        controller.newIntent(alarmIntent(otherTask, otherInstance))

        composeRule.onNodeWithText("other-task").assertExists()
        composeRule.onNodeWithText("test-task").assertDoesNotExist()
        assertThat(controller.get().intent.toAlarm()?.task, equalTo(otherTask))

        composeRule.mainClock.advanceTimeBy(RingingChecks)
        assertThat(controller.get().isFinishing, equalTo(false))
    }

    @Test
    fun ignoreInvalidNewAlarms() {
        ring(task, instance)

        val controller = launch(task, instance)

        controller.newIntent(Intent(context, AlarmActivity::class.java))

        composeRule.onNodeWithText("test-task").assertExists()
        assertThat(controller.get().isFinishing, equalTo(false))
        assertThat(controller.get().intent.toAlarm()?.task, equalTo(task))
    }

    private fun ring(task: Task, instance: TaskInstance) {
        notificationManager.putInstanceExecutionAlarm(context, task, instance, Duration.ofMinutes(10))
    }

    private fun launch(task: Task, instance: TaskInstance): ActivityController<AlarmActivity> =
        Robolectric.buildActivity(AlarmActivity::class.java, alarmIntent(task, instance)).setup()

    private fun alarmIntent(task: Task, instance: TaskInstance): Intent =
        shadowOf(NotificationManagerExtensions.Intents.createAlarmIntent(context, task, instance)).savedIntent

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Once(instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = null,
        executionAlarm = true
    )

    private val instance = TaskInstance(instant = Instant.now().truncatedTo(ChronoUnit.SECONDS))

    companion object {
        private const val RingingCheck: Long = 1000
        private const val RingingChecks: Long = 3 * RingingCheck
    }
}
