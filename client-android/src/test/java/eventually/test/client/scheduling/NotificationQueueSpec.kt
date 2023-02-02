package eventually.test.client.scheduling

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.client.scheduling.NotificationQueue
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.scheduling.SchedulerOps
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class NotificationQueueSpec {
    @Test
    fun distinctExistingNotifications() {
        val notifications = NotificationQueue.ExistingNotifications()
        assertThat(notifications.toList(), equalTo(emptyList()))

        val withExecutionOnly = notifications.updated(newExecution = executionNotification)
        assertThat(withExecutionOnly.toList(), equalTo(listOf(executionNotification)))
        assertThat(withExecutionOnly.execution, equalTo(executionNotification))
        assertThat(withExecutionOnly.context, equalTo(null))
        assertThat(withExecutionOnly.deletion, equalTo(null))

        val withContextOnly = notifications.updated(newContext = contextSwitchNotification)
        assertThat(withContextOnly.toList(), equalTo(listOf(contextSwitchNotification)))
        assertThat(withContextOnly.execution, equalTo(null))
        assertThat(withContextOnly.context, equalTo(contextSwitchNotification))
        assertThat(withContextOnly.deletion, equalTo(null))

        val withDeletionOnly = notifications.updated(newDeletion = deleteInstanceNotification)
        assertThat(withDeletionOnly.toList(), equalTo(listOf(deleteInstanceNotification)))
        assertThat(withDeletionOnly.execution, equalTo(null))
        assertThat(withDeletionOnly.context, equalTo(null))
        assertThat(withDeletionOnly.deletion, equalTo(deleteInstanceNotification))

        val withExecutionAndContext = notifications
            .updated(newExecution = executionNotification)
            .updated(newContext = contextSwitchNotification)
        assertThat(withExecutionAndContext.toList(), equalTo(listOf(executionNotification, contextSwitchNotification)))
        assertThat(withExecutionAndContext.execution, equalTo(executionNotification))
        assertThat(withExecutionAndContext.context, equalTo(contextSwitchNotification))
        assertThat(withExecutionAndContext.deletion, equalTo(null))

        val withLastDeletion = notifications
            .updated(newExecution = executionNotification)
            .updated(newContext = contextSwitchNotification)
            .updated(newDeletion = deleteInstanceNotification)
        assertThat(withLastDeletion.toList(), equalTo(listOf(deleteInstanceNotification)))
        assertThat(withLastDeletion.execution, equalTo(null))
        assertThat(withLastDeletion.context, equalTo(null))
        assertThat(withLastDeletion.deletion, equalTo(deleteInstanceNotification))

        val withFirstDeletion = notifications
            .updated(newDeletion = deleteInstanceNotification)
            .updated(newExecution = executionNotification)
        assertThat(withFirstDeletion.toList(), equalTo(listOf(executionNotification)))
        assertThat(withFirstDeletion.execution, equalTo(executionNotification))
        assertThat(withFirstDeletion.context, equalTo(null))
        assertThat(withFirstDeletion.deletion, equalTo(null))
    }

    @Test
    fun addNewNotificationsToQueue() {
        val queue = NotificationQueue()

        assertThat(queue.notifications.toList(), equalTo(emptyList()))

        val notifications = listOf(
            executionNotification,
            contextSwitchNotification,
            deleteInstanceNotification,
            putEvaluationAlarm,
            deleteEvaluationAlarm
        )

        queue.enqueue(notifications)
        assertThat(queue.notifications.toList(), equalTo(notifications))

        queue.enqueue(notifications)
        assertThat(
            queue.notifications.toList(),
            equalTo(notifications + notifications)
        )
    }

    @Test
    fun removeUnnecessaryNotifications() {
        val model = mockk<NotificationViewModel>()
        every { model.get(any(), any(), any()) } returns CompletableDeferred(null as NotificationEntity?)

        val queue = NotificationQueue()

        assertThat(queue.notifications.toList(), equalTo(emptyList()))

        val notifications = listOf(
            executionNotification,
            contextSwitchNotification,
            deleteInstanceNotification,
            putEvaluationAlarm,
            deleteEvaluationAlarm
        )

        queue.enqueue(notifications)
        assertThat(
            runBlocking { queue.distinct(model).notifications.toList() },
            equalTo(
                listOf(
                    putEvaluationAlarm,
                    deleteEvaluationAlarm,
                    deleteInstanceNotification
                )
            )
        )

        queue.enqueue(notifications)
        assertThat(
            runBlocking { queue.distinct(model).notifications.toList() },
            equalTo(
                listOf(
                    putEvaluationAlarm,
                    deleteEvaluationAlarm,
                    putEvaluationAlarm,
                    deleteEvaluationAlarm,
                    deleteInstanceNotification
                )
            )
        )

        verify(exactly = 6) { model.get(any(), any(), any()) }

        confirmVerified(model)
    }


    @Test
    fun removeExistingNotifications() {
        val executionNotificationEntity = NotificationEntity(
            id = 1,
            task = task.id,
            instance = instance.id,
            type = "execution",
            hash = instance.hashCode()
        )

        val contextNotificationEntity = NotificationEntity(
            id = 2,
            task = task.id,
            instance = instance.id,
            type = "execution",
            hash = instance.hashCode()
        )

        val model = mockk<NotificationViewModel>()
        every { model.get(any(), any(), "execution") } returns CompletableDeferred(executionNotificationEntity)
        every { model.get(any(), any(), "context") } returns CompletableDeferred(contextNotificationEntity)

        val queue = NotificationQueue()

        assertThat(queue.notifications.toList(), equalTo(emptyList()))

        val futureExecutionNotification = executionNotification.copy(instance = TaskInstance(Instant.now().plusSeconds(42)))

        val notifications = listOf(
            executionNotification,
            executionNotification,
            executionNotification,
            futureExecutionNotification,
            contextSwitchNotification,
            contextSwitchNotification
        )

        queue.enqueue(notifications)

        assertThat(
            runBlocking { queue.distinct(model).notifications.toList() },
            equalTo(
                listOf(
                    futureExecutionNotification
                )
            )
        )

        verify(exactly = notifications.size) { model.get(any(), any(), any()) }

        confirmVerified(model)
    }

    @Test
    fun releaseAccumulatedNotifications() {
        val model = mockk<NotificationViewModel>()
        every { model.put(any(), any(), any()) } returns CompletableDeferred(42)
        every { model.delete(any(), any()) } returns CompletableDeferred(Unit)

        val notifications = listOf(
            executionNotification,
            contextSwitchNotification,
            deleteInstanceNotification,
            putEvaluationAlarm,
            deleteEvaluationAlarm
        )

        val queue = NotificationQueue()
        queue.enqueue(notifications)

        assertThat(queue.notifications.isNotEmpty(), equalTo(true))

        val context = ApplicationProvider.getApplicationContext<Context>()

        val alarmManager = mockk<AlarmManager>()
        justRun { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        justRun { alarmManager.cancel(any<PendingIntent>()) }

        val notificationManager = mockk<NotificationManager>()
        justRun { notificationManager.notify(any(), any()) }
        justRun { notificationManager.cancel(any()) }

        runBlocking { queue.release(context, alarmManager, notificationManager, model) }

        verify(exactly = 2) { model.put(any(), any(), any()) }
        verify(exactly = 1) { model.delete(any(), any()) }

        verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 1) { alarmManager.cancel(any<PendingIntent>()) }

        verify(exactly = 2) { notificationManager.notify(any(), any()) }
        verify(exactly = 1) { notificationManager.cancel(any()) }

        confirmVerified(model)
        confirmVerified(alarmManager)
        confirmVerified(notificationManager)
    }

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Repeating(
            start = Instant.now().truncatedTo(ChronoUnit.SECONDS),
            every = Duration.ofMinutes(20).toInterval()
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = 1
    )

    private val instance = TaskInstance(
        instant = Instant.now()
    )

    private val executionNotification = SchedulerOps.Notification.PutInstanceExecutionNotification(task, instance)

    private val contextSwitchNotification = SchedulerOps.Notification.PutInstanceContextSwitchNotification(task, instance)

    private val deleteInstanceNotification = SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)

    private val putEvaluationAlarm = SchedulerOps.Notification.PutEvaluationAlarm(instant = Instant.now().plusSeconds(42))

    private val deleteEvaluationAlarm = SchedulerOps.Notification.DeleteEvaluationAlarm
}
