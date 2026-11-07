package eventually.test.client.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.MainActivity
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.notifications.NotificationRepository
import eventually.client.scheduling.NotificationManagerExtensions
import eventually.client.scheduling.NotificationManagerExtensions.createInstanceNotificationChannels
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.pruneInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceContextSwitchNotification
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionNotification
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
import org.hamcrest.CoreMatchers.equalTo
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

        assertThat(channels.toList().size, equalTo(3))
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

    companion object {
        private const val ExpectedActivityFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
