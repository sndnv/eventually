package eventually.test.client.scheduling

import android.content.Context
import android.content.Intent
import android.os.Message
import androidx.test.core.app.ApplicationProvider
import eventually.client.persistence.schedules.TaskScheduleViewModel
import eventually.client.persistence.tasks.TaskViewModel
import eventually.client.scheduling.NotificationQueue
import eventually.client.scheduling.SchedulerService
import eventually.client.scheduling.SchedulerService.Companion.applyTo
import eventually.client.scheduling.SchedulerService.Companion.handle
import eventually.client.scheduling.SchedulerService.Companion.toSchedulerMessage
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putTask
import eventually.client.serialization.Extras.putTaskId
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import eventually.core.model.TaskSummaryConfig
import eventually.core.scheduling.SchedulerOps
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SchedulerServiceSpec {
    @Test
    fun convertIntentsToSchedulerMessages() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(
            context.createIntent(SchedulerService.ActionPut) {
                it.putTask(SchedulerService.ActionPutExtraTask, task)
            }.toSchedulerMessage(::requireConfig),
            equalTo(
                SchedulerOps.Message.Put(task)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionDelete) {
                it.putTaskId(SchedulerService.ActionDeleteExtraTask, task.id)
            }.toSchedulerMessage(::requireConfig),
            equalTo(
                SchedulerOps.Message.Delete(task.id)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionDismiss) {
                it.putTaskId(SchedulerService.ActionDismissExtraTask, task.id)
                it.putInstanceId(SchedulerService.ActionDismissExtraInstance, instance.id)
            }.toSchedulerMessage(::requireConfig),
            equalTo(
                SchedulerOps.Message.Dismiss(task.id, instance.id)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionPostpone) {
                it.putTaskId(SchedulerService.ActionPostponeExtraTask, task.id)
                it.putInstanceId(SchedulerService.ActionPostponeExtraInstance, instance.id)
                it.putDuration(SchedulerService.ActionPostponeExtraBy, postponeBy)
            }.toSchedulerMessage(::requireConfig),
            equalTo(
                SchedulerOps.Message.Postpone(task.id, instance.id, postponeBy)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionEvaluate) { it }.toSchedulerMessage(::requireConfig),
            equalTo(
                SchedulerOps.Message.Evaluate(config)
            )
        )

        assertThat(
            null.toSchedulerMessage(::requireConfig),
            equalTo(null)
        )

        try {
            context.createIntent("other") { it }.toSchedulerMessage(::requireConfig)
            fail("Excepted failure but none encountered")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected action encountered: [other]"))
        }
    }

    @Test
    fun handleSchedulerMessages() {
        val model = mockk<TaskViewModel>()
        every { model.put(task) } returns CompletableDeferred(task.id.toLong())
        every { model.delete(task.id) } returns CompletableDeferred(Unit)

        runBlocking {
            assertThat(
                Message().apply { obj = SchedulerOps.Message.Put(task) }.handle(emptyMap(), model),
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = mapOf(Pair(task.id, TaskSchedule(task))),
                        notifications = emptyList(),
                        summary = null,
                        affectedSchedules = listOf(task.id)
                    )
                )
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Delete(task.id) }
                    .handle(mapOf(Pair(task.id, TaskSchedule(task))), model),
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = emptyMap(),
                        notifications = emptyList(),
                        summary = null,
                        affectedSchedules = listOf(task.id)
                    )
                )
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Dismiss(task.id, instance.id) }
                    .handle(mapOf(Pair(task.id, TaskSchedule(task, mapOf(Pair(instance.id, instance)), emptyList()))), model),
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = mapOf(
                            Pair(
                                task.id,
                                TaskSchedule(task, emptyMap(), listOf(instance.execution()))
                            )
                        ),
                        notifications = listOf(
                            SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)
                        ),
                        summary = null,
                        affectedSchedules = listOf(task.id)
                    )
                )
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Postpone(task.id, instance.id, postponeBy) }
                    .handle(mapOf(Pair(task.id, TaskSchedule(task, mapOf(Pair(instance.id, instance)), emptyList()))), model),
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = mapOf(
                            Pair(
                                task.id,
                                TaskSchedule(task, mapOf(Pair(instance.id, instance.copy(postponed = postponeBy))), emptyList())
                            )
                        ),
                        notifications = listOf(
                            SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)
                        ),
                        summary = null,
                        affectedSchedules = listOf(task.id)
                    )
                )
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Evaluate(config) }.handle(emptyMap(), model),
                equalTo(null)
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Init(tasks = listOf(task), schedules = emptyList()) }
                    .handle(emptyMap(), model),
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = mapOf(Pair(task.id, TaskSchedule(task))),
                        notifications = emptyList(),
                        summary = null,
                        affectedSchedules = emptyList()
                    )
                )
            )

            try {
                Message().apply { obj = "other" }.handle(emptyMap(), model)
                fail("Excepted failure but none encountered")
            } catch (e: IllegalArgumentException) {
                assertThat(e.message, equalTo("Unexpected message encountered: [other]"))
            }

            verify(exactly = 1) { model.put(any()) }
            verify(exactly = 1) { model.delete(any()) }
            confirmVerified(model)
        }
    }

    @Test
    fun applySchedulerResults() {
        runBlocking {
            val schedulesUpdated = AtomicBoolean(false)
            val summaryUpdated = AtomicBoolean(false)

            val removedSchedule = 21

            val result = SchedulerOps.SchedulerResult(
                schedules = mapOf(Pair(task.id, TaskSchedule(task))),
                notifications = emptyList(),
                summary = TaskSummary.empty(),
                affectedSchedules = listOf(task.id, removedSchedule)
            )

            val notifications = NotificationQueue()
            assertThat(notifications.notifications.isEmpty(), equalTo(true))

            val model = mockk<TaskScheduleViewModel>()
            every { model.put(any()) } returns CompletableDeferred(Unit)
            every { model.delete(any()) } returns CompletableDeferred(Unit)

            result.applyTo(
                notifications = notifications,
                taskScheduleViewModel = model,
                updateSchedules = { schedulesUpdated.set(true) },
                updateSummary = { summaryUpdated.set(true) }
            )

            assertThat(schedulesUpdated.get(), equalTo(true))
            assertThat(summaryUpdated.get(), equalTo(true))

            verify(exactly = 1) { model.put(TaskSchedule(task)) }
            verify(exactly = 1) { model.delete(removedSchedule) }
            confirmVerified(model)
        }
    }

    private fun Context.createIntent(withAction: String, apply: (Intent) -> Intent): Intent =
        Intent(this, SchedulerService::class.java).apply {
            action = withAction
            apply(this)
        }

    private fun requireConfig(): TaskSummaryConfig = config

    private val config = TaskSummaryConfig(summarySize = Duration.ofMinutes(15))

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
        instant = Instant.now()
    )

    private val postponeBy = Duration.ofMinutes(5)
}
