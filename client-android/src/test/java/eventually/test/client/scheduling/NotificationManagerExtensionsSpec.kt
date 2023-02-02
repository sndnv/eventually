package eventually.test.client.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eventually.client.scheduling.NotificationManagerExtensions
import eventually.client.scheduling.NotificationManagerExtensions.createInstanceNotificationChannels
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceContextSwitchNotification
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionNotification
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
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
    fun removeInstanceNotifications() {
        val manager = mockk<NotificationManager>(relaxed = true)

        manager.deleteInstanceNotifications(task = 1, instance = UUID.randomUUID())

        verify(exactly = 1) { manager.cancel(any()) }

        confirmVerified(manager)
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
}
