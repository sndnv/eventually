package eventually.test.client.activities.helpers

import android.content.Context
import android.content.Intent
import eventually.client.activities.helpers.TaskManagement
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.requireDuration
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireInstant
import eventually.client.serialization.Extras.requireTask
import eventually.client.serialization.Extras.requireTaskId
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
class TaskManagementSpec {
    @Test
    fun putTasks() {
        val intent = slot<Intent>()

        val context = mockk<Context>()
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        TaskManagement.putTask(context, task)

        verify(exactly = 1) { context.packageName }
        verify(exactly = 1) { context.startService(any()) }

        confirmVerified(context)

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionPut))
        assertThat(intent.captured.requireTask(SchedulerService.ActionPutExtraTask), equalTo(task))
    }

    @Test
    fun deleteTasks() {
        val intent = slot<Intent>()

        val context = mockk<Context>()
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        TaskManagement.deleteTask(context, task.id)

        verify(exactly = 1) { context.packageName }
        verify(exactly = 1) { context.startService(any()) }

        confirmVerified(context)

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionDelete))
        assertThat(intent.captured.requireTaskId(SchedulerService.ActionDeleteExtraTask), equalTo(task.id))
    }

    @Test
    fun dismissTaskInstances() {
        val intent = slot<Intent>()

        val context = mockk<Context>()
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        TaskManagement.dismissTaskInstance(context, task.id, instance.id)

        verify(exactly = 1) { context.packageName }
        verify(exactly = 1) { context.startService(any()) }

        confirmVerified(context)

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionDismiss))
        assertThat(intent.captured.requireTaskId(SchedulerService.ActionDismissExtraTask), equalTo(task.id))
        assertThat(intent.captured.requireInstanceId(SchedulerService.ActionDismissExtraInstance), equalTo(instance.id))
    }

    @Test
    fun undoTaskInstanceDismissal() {
        val intent = slot<Intent>()

        val context = mockk<Context>()
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        TaskManagement.undoDismissTaskInstance(context, task.id, instance.instant)

        verify(exactly = 1) { context.packageName }
        verify(exactly = 1) { context.startService(any()) }

        confirmVerified(context)

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionUndoDismiss))
        assertThat(intent.captured.requireTaskId(SchedulerService.ActionUndoDismissExtraTask), equalTo(task.id))
        assertThat(intent.captured.requireInstant(SchedulerService.ActionUndoDismissExtraInstant), equalTo(instance.instant))
    }

    @Test
    fun postponeTaskInstances() {
        val intent = slot<Intent>()

        val context = mockk<Context>()
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        val by = Duration.ofMinutes(42)

        TaskManagement.postponeTaskInstance(context, task.id, instance.id, by)

        verify(exactly = 1) { context.packageName }
        verify(exactly = 1) { context.startService(any()) }

        confirmVerified(context)

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionPostpone))
        assertThat(intent.captured.requireTaskId(SchedulerService.ActionPostponeExtraTask), equalTo(task.id))
        assertThat(intent.captured.requireInstanceId(SchedulerService.ActionPostponeExtraInstance), equalTo(instance.id))
        assertThat(intent.captured.requireDuration(SchedulerService.ActionPostponeExtraBy), equalTo(by))
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
        isActive = true
    )

    private val instance = TaskInstance(
        instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )
}
