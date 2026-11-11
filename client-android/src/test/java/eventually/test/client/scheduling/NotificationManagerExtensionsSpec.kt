package eventually.test.client.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.AlarmActivity
import eventually.client.activities.AlarmActivity.Companion.toAlarm
import eventually.client.activities.MainActivity
import eventually.client.activities.receivers.DismissReceiver
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.notifications.NotificationRepository
import eventually.client.scheduling.NotificationManagerExtensions
import eventually.client.scheduling.NotificationManagerExtensions.canUseFullScreenAlarms
import eventually.client.scheduling.NotificationManagerExtensions.createInstanceNotificationChannels
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.pruneInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceContextSwitchNotification
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionAlarm
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionNotification
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceMissedAlarmNotification
import eventually.client.scheduling.NotificationManagerExtensions.replaceInstanceExecutionAlarm
import eventually.client.serialization.Extras.requireDuration
import eventually.client.serialization.Extras.requireTaskId
import eventually.client.settings.Settings
import eventually.client.settings.Settings.defaultPreferences
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Objects
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class NotificationManagerExtensionsSpec {
    @Test
    fun createInstanceNotificationChannels() {
        val channels = mutableListOf<NotificationChannel>()

        val context = mockk<Context>()
        every { context.getString(any()) } returns "test"

        val manager = mockk<NotificationManager>()
        justRun { manager.createNotificationChannel(capture(channels)) }

        manager.createInstanceNotificationChannels(context)

        assertThat(channels.toList().size, equalTo(5))

        val config = NotificationManagerExtensions.Config.Default

        val execution = channels.single { it.id == config.instanceExecutionChannel.id }
        assertThat(execution.audioAttributes.usage, equalTo(AudioAttributes.USAGE_NOTIFICATION))

        val alarm = channels.single { it.id == config.instanceExecutionAlarmChannel.id }
        assertThat(alarm.importance, equalTo(NotificationManager.IMPORTANCE_HIGH))
        assertThat(alarm.sound, equalTo(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))
        assertThat(alarm.audioAttributes.usage, equalTo(AudioAttributes.USAGE_ALARM))
        assertThat(alarm.shouldVibrate(), equalTo(true))

        val missed = channels.single { it.id == config.instanceMissedAlarmChannel.id }
        assertThat(missed.importance, equalTo(NotificationManager.IMPORTANCE_DEFAULT))
        assertThat(missed.audioAttributes.usage, equalTo(AudioAttributes.USAGE_NOTIFICATION))
    }

    @Test
    fun createInstanceExecutionAlarms() {
        val id = slot<Int>()
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(capture(id), capture(notification)) }

        manager.putInstanceExecutionAlarm(context, task, instance, timeout = Duration.ofMinutes(10))

        val alarm = notification.captured

        assertThat(id.captured, equalTo(Objects.hash(task.id, instance.id) and Int.MAX_VALUE))
        assertThat(alarm.channelId, equalTo(NotificationManagerExtensions.Config.Default.instanceExecutionAlarmChannel.id))
        assertThat(alarm.category, equalTo(Notification.CATEGORY_ALARM))
        assertThat(alarm.flags and Notification.FLAG_INSISTENT, equalTo(Notification.FLAG_INSISTENT))
        assertThat(alarm.flags and Notification.FLAG_ONGOING_EVENT, equalTo(Notification.FLAG_ONGOING_EVENT))
        assertThat(alarm.flags and Notification.FLAG_AUTO_CANCEL, equalTo(0))
        assertThat(alarm.timeoutAfter, equalTo(Duration.ofMinutes(11).toMillis()))

        assertThat(alarm.extras.getString(Notification.EXTRA_TITLE), containsString(task.name))
        assertThat(alarm.extras.getString(Notification.EXTRA_TEXT), containsString(task.goal))
        assertThat(alarm.extras.getString(Notification.EXTRA_TEXT), containsString(task.description))

        assertThat(alarm.fullScreenIntent, equalTo(alarm.contentIntent))
        assertThat(shadowOf(alarm.fullScreenIntent).savedIntent.component?.className, equalTo(AlarmActivity::class.java.name))

        assertThat(alarm.actions.size, equalTo(2))
        assertThat(alarm.actions[0].title, equalTo("Dismiss"))
        assertThat(alarm.actions[1].title, equalTo("Postpone"))
    }

    @Test
    fun createInstanceMissedAlarmNotifications() {
        val id = slot<Int>()
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(capture(id), capture(notification)) }

        manager.putInstanceMissedAlarmNotification(context, task, instance)

        val missed = notification.captured

        assertThat(id.captured, equalTo(Objects.hash(task.id, instance.id) and Int.MAX_VALUE))
        assertThat(missed.channelId, equalTo(NotificationManagerExtensions.Config.Default.instanceMissedAlarmChannel.id))
        assertThat(missed.extras.getString(Notification.EXTRA_TITLE), startsWith("Missed alarm: ["))
        assertThat(missed.extras.getString(Notification.EXTRA_TITLE), endsWith("] - ${task.name}"))
        assertThat(missed.extras.getString(Notification.EXTRA_TEXT), containsString(task.goal))
        assertThat(missed.flags and Notification.FLAG_AUTO_CANCEL, equalTo(Notification.FLAG_AUTO_CANCEL))
        assertThat(missed.flags and Notification.FLAG_INSISTENT, equalTo(0))
        assertThat(missed.fullScreenIntent, equalTo(null))

        val pending = shadowOf(missed.contentIntent)
        assertThat(pending.savedIntent.component?.className, equalTo(MainActivity::class.java.name))
        assertThat(pending.savedIntent.requireTaskId(MainActivity.ExtraTask), equalTo(task.id))

        assertThat(missed.actions.size, equalTo(2))
        assertThat(missed.actions[0].title, equalTo("Dismiss"))
        assertThat(missed.actions[1].title, equalTo("Postpone"))
    }

    @Test
    fun replaceRingingExecutionAlarmsWithMissedAlarmNotifications() {
        val id = Objects.hash(task.id, instance.id) and Int.MAX_VALUE
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        every { manager.activeNotifications } returns arrayOf(
            statusBarNotification(id = id, channel = NotificationManagerExtensions.Config.Default.instanceExecutionAlarmChannel.id)
        )
        justRun { manager.cancel(any()) }
        justRun { manager.notify(any(), capture(notification)) }

        manager.replaceInstanceExecutionAlarm(context, task, instance)

        verify(exactly = 1) { manager.activeNotifications }
        verify(exactly = 1) { manager.cancel(id) }
        verify(exactly = 1) { manager.notify(id, any()) }
        confirmVerified(manager)

        assertThat(notification.captured.channelId, equalTo(NotificationManagerExtensions.Config.Default.instanceMissedAlarmChannel.id))
    }

    @Test
    fun notReplaceExecutionAlarmsThatAreNoLongerRinging() {
        val id = Objects.hash(task.id, instance.id) and Int.MAX_VALUE

        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = NotificationManagerExtensions.Config.Default

        listOf(
            emptyArray(),
            arrayOf(statusBarNotification(id = id, channel = config.instanceExecutionChannel.id)),
            arrayOf(statusBarNotification(id = id, channel = config.instanceMissedAlarmChannel.id)),
            arrayOf(statusBarNotification(id = id + 1, channel = config.instanceExecutionAlarmChannel.id))
        ).forEach { active ->
            val manager = mockk<NotificationManager>()
            every { manager.activeNotifications } returns active

            manager.replaceInstanceExecutionAlarm(context, task, instance)

            verify(exactly = 1) { manager.activeNotifications }
            confirmVerified(manager)
        }
    }

    @Test
    fun openAlarmScreenFromExecutionAlarms() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pending = shadowOf(NotificationManagerExtensions.Intents.createAlarmIntent(context, task, instance))

        assertThat(pending.isActivityIntent, equalTo(true))
        assertThat(pending.savedIntent.component?.className, equalTo(AlarmActivity::class.java.name))
        assertThat(pending.savedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK, equalTo(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertThat(
            pending.savedIntent.toAlarm(),
            equalTo(
                AlarmActivity.Alarm(
                    task = task,
                    instance = instance.id,
                    execution = instance.execution().truncatedTo(ChronoUnit.MILLIS)
                )
            )
        )
    }

    @Test
    fun allowFullScreenAlarmsBeforeAndroid14() {
        val manager = mockk<NotificationManager>()

        assertThat(manager.canUseFullScreenAlarms(), equalTo(true))
        assertThat(
            NotificationManagerExtensions.Intents.createFullScreenAlarmSettingsIntent(ApplicationProvider.getApplicationContext()),
            equalTo(null)
        )

        confirmVerified(manager)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun checkFullScreenAlarmsOnAndroid14() {
        val manager = mockk<NotificationManager>()
        every { manager.canUseFullScreenIntent() } returnsMany listOf(true, false)

        assertThat(manager.canUseFullScreenAlarms(), equalTo(true))
        assertThat(manager.canUseFullScreenAlarms(), equalTo(false))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = NotificationManagerExtensions.Intents.createFullScreenAlarmSettingsIntent(context)

        assertThat(intent?.action, equalTo(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
        assertThat(intent?.data?.schemeSpecificPart, equalTo(context.packageName))
    }

    @Test
    fun createInstanceExecutionNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putInstanceExecutionNotification(context, task, instance)

        assertThat(notification.captured.extras.getString(Notification.EXTRA_TITLE) , containsString(task.name))
        assertThat(notification.captured.extras.getString(Notification.EXTRA_TEXT), containsString(task.goal))
        assertThat(notification.captured.extras.getString(Notification.EXTRA_TEXT), containsString(task.description))

        assertThat(notification.captured.actions.size, equalTo(2))

        assertThat(notification.captured.actions[0].title, equalTo("Dismiss"))
        assertThat(notification.captured.actions[1].title, equalTo("Postpone"))
    }

    @Test
    fun createInstanceContextSwitchNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putInstanceContextSwitchNotification(context, task, instance)

        assertThat(notification.captured.extras.getString(Notification.EXTRA_TITLE), containsString(task.name))
        assertThat(notification.captured.extras.getString(Notification.EXTRA_TEXT), containsString(task.goal))
        assertThat(notification.captured.extras.getString(Notification.EXTRA_TEXT), containsString(task.description))

        assertThat(notification.captured.actions.size, equalTo(2))

        assertThat(notification.captured.actions[0].title, equalTo("Dismiss"))
        assertThat(notification.captured.actions[1].title, equalTo("Postpone"))
    }

    @Test
    fun createSingleUseInstanceActionIntents() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val dismiss = NotificationManagerExtensions.Intents.createDismissIntent(context, task, instance)
        val postpone = NotificationManagerExtensions.Intents.createPostponeIntent(context, task, instance)

        listOf(dismiss, postpone).forEach { intent ->
            val flags = shadowOf(intent).flags

            assertThat(flags and PendingIntent.FLAG_ONE_SHOT, equalTo(PendingIntent.FLAG_ONE_SHOT))
            assertThat(flags and PendingIntent.FLAG_UPDATE_CURRENT, equalTo(PendingIntent.FLAG_UPDATE_CURRENT))
            assertThat(flags and PendingIntent.FLAG_IMMUTABLE, equalTo(PendingIntent.FLAG_IMMUTABLE))
        }
    }

    @Test
    fun deliverInstanceActionsOnlyToThisApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val dismiss = shadowOf(NotificationManagerExtensions.Intents.createDismissIntent(context, task, instance))
        val postpone = shadowOf(NotificationManagerExtensions.Intents.createPostponeIntent(context, task, instance))

        assertThat(dismiss.isBroadcastIntent, equalTo(true))
        assertThat(dismiss.savedIntent.action, equalTo(DismissReceiver.Action))
        assertThat(dismiss.savedIntent.`package`, equalTo(context.packageName))

        assertThat(postpone.isBroadcastIntent, equalTo(true))
        assertThat(postpone.savedIntent.action, equalTo(PostponeReceiver.Action))
        assertThat(postpone.savedIntent.`package`, equalTo(context.packageName))
    }

    @Test
    fun updateInstanceActionExtrasWhenRecreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultPreferences()

        preferences.edit().putString(Settings.Keys.PostponeLength, "10").commit()
        val original = NotificationManagerExtensions.Intents.createPostponeIntent(context, task, instance)

        preferences.edit().putString(Settings.Keys.PostponeLength, "25").commit()
        val recreated = NotificationManagerExtensions.Intents.createPostponeIntent(context, task, instance)

        assertThat(recreated, equalTo(original))
        assertThat(
            shadowOf(recreated).savedIntent.requireDuration(PostponeReceiver.ExtraBy),
            equalTo(Duration.ofMinutes(25))
        )
    }

    @Test
    fun removeInstanceNotifications() {
        val manager = mockk<NotificationManager>(relaxed = true)

        val task = 1
        val instance = UUID.randomUUID()

        manager.deleteInstanceNotifications(task = task, instance = instance)

        verify(exactly = 1) { manager.cancel(Objects.hash(task, instance) and Int.MAX_VALUE) }
        verify(exactly = 1) { manager.cancel(task + instance.hashCode()) }

        confirmVerified(manager)
    }

    @Test
    fun pruneStoredNotificationsThatAreNoLongerShown() {
        val shown = notification(id = 1)
        val shownWithLegacyId = notification(id = 2)
        val cleared = notification(id = 3)

        val manager = mockk<NotificationManager>()
        every { manager.activeNotifications } returns arrayOf(
            statusBarNotification(id = Objects.hash(shown.task, shown.instance) and Int.MAX_VALUE),
            statusBarNotification(id = shownWithLegacyId.task + shownWithLegacyId.instance.hashCode())
        )

        val repo = mockk<NotificationRepository>()
        coEvery { repo.all() } returns listOf(shown, shownWithLegacyId, cleared)
        coEvery { repo.delete(any()) } returns Unit

        runBlocking { manager.pruneInstanceNotifications(repo) }

        coVerify(exactly = 1) { repo.all() }
        coVerify(exactly = 1) { repo.delete(cleared.id) }
        confirmVerified(repo)
    }

    @Test
    fun openTaskDetailsFromInstanceNotifications() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pending = shadowOf(NotificationManagerExtensions.Intents.createTaskDetailsIntent(context, task, instance))

        assertThat(pending.isActivityIntent, equalTo(true))
        assertThat(pending.savedIntent.component?.className, equalTo(MainActivity::class.java.name))
        assertThat(pending.savedIntent.requireTaskId(MainActivity.ExtraTask), equalTo(task.id))
        assertThat(pending.savedIntent.flags and ExpectedActivityFlags, equalTo(ExpectedActivityFlags))
    }

    @Test
    fun openMainScreenFromForegroundServiceNotifications() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val (_, notification) = NotificationManagerExtensions.createForegroundServiceNotification(context)
        val pending = shadowOf(notification.contentIntent)

        assertThat(pending.isActivityIntent, equalTo(true))
        assertThat(pending.savedIntent.component?.className, equalTo(MainActivity::class.java.name))
        assertThat(pending.savedIntent.hasExtra(MainActivity.ExtraTask), equalTo(false))
        assertThat(pending.savedIntent.flags and ExpectedActivityFlags, equalTo(ExpectedActivityFlags))
    }

    @Test
    fun createForegroundServiceNotifications() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test"
        every { context.packageName } returns "eventually.client.scheduling"

        val (id, notification) = NotificationManagerExtensions.createForegroundServiceNotification(context)

        assertThat(id, equalTo(-1))
        assertThat(notification.group, equalTo("eventually.client.scheduling.foreground_service_notification"))
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE), equalTo("test"))
    }

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Repeating(
            start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
            every = Duration.ofMinutes(20).toInterval()
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = 1
    )

    private val instance = TaskInstance(
        instant = Instant.now()
    )

    private fun notification(id: Int): NotificationEntity =
        NotificationEntity(id = id, task = id, instance = UUID.randomUUID(), type = "context", hash = id)

    private fun statusBarNotification(id: Int): StatusBarNotification =
        mockk<StatusBarNotification>().also { every { it.id } returns id }

    private fun statusBarNotification(id: Int, channel: String): StatusBarNotification {
        val notification = mockk<Notification>()
        every { notification.channelId } returns channel

        return mockk<StatusBarNotification>().also {
            every { it.id } returns id
            every { it.notification } returns notification
        }
    }

    companion object {
        private const val ExpectedActivityFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
