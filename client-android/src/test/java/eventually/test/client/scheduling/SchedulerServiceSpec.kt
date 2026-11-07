package eventually.test.client.scheduling

import android.content.Context
import android.content.Intent
import android.os.Message
import androidx.test.core.app.ApplicationProvider
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.schedules.TaskScheduleRepository
import eventually.client.persistence.tasks.TaskRepository
import eventually.client.scheduling.NotificationQueue
import eventually.client.scheduling.SchedulerService
import eventually.client.scheduling.SchedulerService.Companion.applyTo
import eventually.client.scheduling.SchedulerService.Companion.handle
import eventually.client.scheduling.SchedulerService.Companion.toSchedulerMessage
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putInstant
import eventually.client.serialization.Extras.putTask
import eventually.client.serialization.Extras.putTaskId
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import eventually.core.model.TaskSummaryConfig
import eventually.core.scheduling.SchedulerOps
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
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
            }.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(
                SchedulerOps.Message.Put(task)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionDelete) {
                it.putTaskId(SchedulerService.ActionDeleteExtraTask, task.id)
            }.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(
                SchedulerOps.Message.Delete(task.id)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionDismiss) {
                it.putTaskId(SchedulerService.ActionDismissExtraTask, task.id)
                it.putInstanceId(SchedulerService.ActionDismissExtraInstance, instance.id)
            }.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(
                SchedulerOps.Message.Dismiss(task.id, instance.id)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionUndoDismiss) {
                it.putTaskId(SchedulerService.ActionUndoDismissExtraTask, task.id)
                it.putInstant(SchedulerService.ActionUndoDismissExtraInstant, instance.instant)
            }.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(
                SchedulerOps.Message.UndoDismiss(task.id, instance.instant)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionPostpone) {
                it.putTaskId(SchedulerService.ActionPostponeExtraTask, task.id)
                it.putInstanceId(SchedulerService.ActionPostponeExtraInstance, instance.id)
                it.putDuration(SchedulerService.ActionPostponeExtraBy, postponeBy)
            }.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(
                SchedulerOps.Message.Postpone(task.id, instance.id, postponeBy)
            )
        )

        assertThat(
            context.createIntent(SchedulerService.ActionEvaluate) { it }.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(
                SchedulerOps.Message.Evaluate(config)
            )
        )

        val reloaded = AtomicBoolean(false)
        assertThat(
            context.createIntent(SchedulerService.ActionReload) { it }
                .toSchedulerMessage(::requireConfig, reloadData = { reloaded.set(true) }, pruneNotifications = ::prune),
            equalTo(null)
        )
        assertThat(reloaded.get(), equalTo(true))

        val pruned = AtomicBoolean(false)
        assertThat(
            context.createIntent(SchedulerService.ActionPruneNotifications) { it }
                .toSchedulerMessage(::requireConfig, ::reload, pruneNotifications = { pruned.set(true) }),
            equalTo(null)
        )
        assertThat(pruned.get(), equalTo(true))

        assertThat(
            null.toSchedulerMessage(::requireConfig, ::reload, ::prune),
            equalTo(null)
        )

        try {
            context.createIntent("other") { it }.toSchedulerMessage(::requireConfig, ::reload, ::prune)
            fail("Excepted failure but none encountered")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected action encountered: [other]"))
        }
    }

    @Test
    fun handleSchedulerMessages() {
        val repo = mockk<TaskRepository>()
        coEvery { repo.put(task) } returns task.id.toLong()
        coEvery { repo.delete(task.id) } returns Unit

        val scheduleRepo = mockk<TaskScheduleRepository>()

        runBlocking {
            assertThat(
                Message().apply { obj = SchedulerOps.Message.Put(task) }.handle(emptyMap(), repo, scheduleRepo),
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
                    .handle(mapOf(Pair(task.id, TaskSchedule(task))), repo, scheduleRepo),
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
                    .handle(mapOf(Pair(task.id, TaskSchedule(task, mapOf(Pair(instance.id, instance)), emptyList()))), repo, scheduleRepo),
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

            val undoResult = Message().apply { obj = SchedulerOps.Message.UndoDismiss(task.id, instance.instant) }
                .handle(mapOf(Pair(task.id, TaskSchedule(task, emptyMap(), listOf(instance.instant)))), repo, scheduleRepo)
            val newInstance = undoResult?.schedules?.values?.first()?.instances?.values?.first()!!
            assertThat(
                undoResult,
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = mapOf(
                            Pair(
                                task.id,
                                TaskSchedule(task, mapOf(newInstance.id to newInstance), emptyList())
                            )
                        ),
                        notifications = emptyList(),
                        summary = null,
                        affectedSchedules = listOf(task.id)
                    )
                )
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Postpone(task.id, instance.id, postponeBy) }
                    .handle(mapOf(Pair(task.id, TaskSchedule(task, mapOf(Pair(instance.id, instance)), emptyList()))), repo, scheduleRepo),
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
                Message().apply { obj = SchedulerOps.Message.Evaluate(config) }.handle(emptyMap(), repo, scheduleRepo),
                equalTo(null)
            )

            assertThat(
                Message().apply { obj = SchedulerService.PruneNotifications }.handle(emptyMap(), repo, scheduleRepo),
                equalTo(null)
            )

            assertThat(
                Message().apply { obj = SchedulerOps.Message.Init(tasks = listOf(task), schedules = emptyList()) }
                    .handle(emptyMap(), repo, scheduleRepo),
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
                Message().apply { obj = "other" }.handle(emptyMap(), repo, scheduleRepo)
                fail("Excepted failure but none encountered")
            } catch (e: IllegalArgumentException) {
                assertThat(e.message, equalTo("Unexpected message encountered: [other]"))
            }

            coVerify(exactly = 1) { repo.put(any()) }
            coVerify(exactly = 1) { repo.delete(any()) }
            confirmVerified(repo)
            confirmVerified(scheduleRepo)
        }
    }

    @Test
    fun loadDataWhenHandlingLoadMessages() {
        runBlocking {
            val instance = TaskInstance(instant = Instant.now().truncatedTo(ChronoUnit.SECONDS))
            val existingSchedule = TaskScheduleEntity(
                task = task.id,
                instances = mapOf(instance.id to instance),
                dismissed = emptyList()
            )

            val taskRepo = mockk<TaskRepository>()
            coEvery { taskRepo.all() } returns listOf(task)

            val scheduleRepo = mockk<TaskScheduleRepository>()
            coEvery { scheduleRepo.all() } returns listOf(existingSchedule)

            assertThat(
                Message().apply { obj = SchedulerService.Load }.handle(emptyMap(), taskRepo, scheduleRepo),
                equalTo(
                    SchedulerOps.SchedulerResult(
                        schedules = mapOf(task.id to TaskSchedule(task, existingSchedule.instances, emptyList())),
                        notifications = emptyList(),
                        summary = null,
                        affectedSchedules = emptyList()
                    )
                )
            )

            coVerify(exactly = 1) { taskRepo.all() }
            coVerify(exactly = 1) { scheduleRepo.all() }
            confirmVerified(taskRepo)
            confirmVerified(scheduleRepo)
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

            val scheduleRepo = mockk<TaskScheduleRepository>()
            coEvery { scheduleRepo.put(any() as TaskSchedule) } returns Unit
            coEvery { scheduleRepo.delete(any()) } returns Unit

            result.applyTo(
                notifications = notifications,
                taskScheduleRepository = scheduleRepo,
                updateSchedules = { schedulesUpdated.set(true) },
                updateSummary = { summaryUpdated.set(true) }
            )

            assertThat(schedulesUpdated.get(), equalTo(true))
            assertThat(summaryUpdated.get(), equalTo(true))

            coVerify(exactly = 1) { scheduleRepo.put(TaskSchedule(task)) }
            coVerify(exactly = 1) { scheduleRepo.delete(removedSchedule) }
            confirmVerified(scheduleRepo)
        }
    }

    @Test
    fun loadInitialDataFromRepositories() {
        runBlocking {
            val otherTask = task.copy(id = 43, name = "other-task")
            val instance = TaskInstance(instant = Instant.now().truncatedTo(ChronoUnit.SECONDS))

            val existingSchedule = TaskScheduleEntity(
                task = task.id,
                instances = mapOf(instance.id to instance),
                dismissed = listOf(instance.instant.minusSeconds(60))
            )
            val orphanedSchedule = TaskScheduleEntity(task = 21, instances = emptyMap(), dismissed = emptyList())

            val taskRepo = mockk<TaskRepository>()
            coEvery { taskRepo.all() } returns listOf(task, otherTask)

            val scheduleRepo = mockk<TaskScheduleRepository>()
            coEvery { scheduleRepo.all() } returns listOf(existingSchedule, orphanedSchedule)

            val init = SchedulerService.loadInit(taskRepo, scheduleRepo)

            assertThat(init.tasks, equalTo(listOf(task, otherTask)))
            assertThat(
                init.schedules,
                equalTo(
                    listOf(
                        TaskSchedule(
                            task = task,
                            instances = existingSchedule.instances,
                            dismissed = existingSchedule.dismissed
                        )
                    )
                )
            )

            coVerify(exactly = 1) { taskRepo.all() }
            coVerify(exactly = 1) { scheduleRepo.all() }
            confirmVerified(taskRepo)
            confirmVerified(scheduleRepo)
        }
    }

    private fun Context.createIntent(withAction: String, apply: (Intent) -> Intent): Intent =
        Intent(this, SchedulerService::class.java).apply {
            action = withAction
            apply(this)
        }

    private fun requireConfig(): TaskSummaryConfig = config

    private fun reload() = Unit

    private fun prune() = Unit

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
        isActive = true,
        color = 1
    )

    private val instance = TaskInstance(
        instant = Instant.now().plusSeconds(20).truncatedTo(ChronoUnit.MILLIS)
    )

    private val postponeBy = Duration.ofMinutes(5)
}
