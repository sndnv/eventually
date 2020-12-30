package eventually.test.core.scheduling

import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummaryConfig
import eventually.core.scheduling.SchedulerOps
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SchedulerOpsSpec : WordSpec({
    "SchedulerOps" should {
        val task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Once(instant = Instant.now().plus(Duration.ofMinutes(3))),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        val config = TaskSummaryConfig(
            summarySize = Duration.ofMinutes(15)
        )

        fun evaluate(schedules: Map<Int, TaskSchedule>): Map<Int, TaskSchedule> =
            SchedulerOps.evaluate(schedules, SchedulerOps.Message.Evaluate(config)).schedules

        "evaluate task schedules" {
            val now = Instant.now()

            val contextSwitchTime = now.plusSeconds(60)
            val futureTime = now.plusSeconds(10 * 60)

            val taskInExecution = task.copy(id = 1, schedule = Task.Schedule.Once(now))
            val taskInContextSwitch = task.copy(id = 2, schedule = Task.Schedule.Once(contextSwitchTime))
            val taskInFuture = task.copy(id = 3, schedule = Task.Schedule.Once(futureTime))
            val disabledTask = task.copy(id = 4, isActive = false)
            val repeatingTask = task.copy(
                id = 5,
                schedule = Task.Schedule.Repeating(start = now, every = Duration.ofMinutes(5)),
                contextSwitch = config.summarySize
            )

            val schedules = mapOf(
                taskInExecution.id to TaskSchedule(taskInExecution),
                taskInContextSwitch.id to TaskSchedule(taskInContextSwitch),
                taskInFuture.id to TaskSchedule(taskInFuture),
                disabledTask.id to TaskSchedule(disabledTask),
                repeatingTask.id to TaskSchedule(repeatingTask)
            )

            schedules[taskInExecution.id]?.instances shouldBe (emptyMap())
            schedules[taskInContextSwitch.id]?.instances shouldBe (emptyMap())
            schedules[taskInFuture.id]?.instances shouldBe (emptyMap())
            schedules[disabledTask.id]?.instances shouldBe (emptyMap())
            schedules[repeatingTask.id]?.instances shouldBe (emptyMap())

            val result = SchedulerOps.evaluate(schedules, SchedulerOps.Message.Evaluate(config))

            result.schedules.map { it.value.task } shouldBe (listOf(
                taskInExecution,
                taskInContextSwitch,
                taskInFuture,
                disabledTask,
                repeatingTask
            ))

            val instanceInExecution = result.schedules[taskInExecution.id]?.instances?.values?.first()
            instanceInExecution shouldNotBe (null)

            val instanceInContextSwitch = result.schedules[taskInContextSwitch.id]?.instances?.values?.first()
            instanceInContextSwitch shouldNotBe (null)

            val instancesInRepeatingTask = result.schedules[repeatingTask.id]?.instances?.values?.toList() ?: emptyList()
            instancesInRepeatingTask.isNotEmpty() shouldBe (true)

            result.summary shouldNotBe (null)

            val scheduledOnceNotifications = listOf(
                SchedulerOps.Notification.PutInstanceExecutionNotification(taskInExecution, instanceInExecution!!),
                SchedulerOps.Notification.PutInstanceContextSwitchNotification(
                    taskInContextSwitch,
                    instanceInContextSwitch!!
                )
            )

            val scheduledRepeatingNotifications = instancesInRepeatingTask.map {
                SchedulerOps.Notification.PutInstanceContextSwitchNotification(
                    repeatingTask,
                    it
                )
            }
            result.notifications shouldBe (
                    listOf(SchedulerOps.Notification.DeleteEvaluationAlarm)
                            + scheduledOnceNotifications
                            + scheduledRepeatingNotifications
                            + listOf(SchedulerOps.Notification.PutEvaluationAlarm(instanceInContextSwitch.execution()))
                    )

            result.schedules[taskInExecution.id]?.instances?.values?.first()?.execution() shouldBe (now)
            result.schedules[taskInContextSwitch.id]?.instances?.values?.first()?.execution() shouldBe (contextSwitchTime)
            result.schedules[taskInFuture.id]?.instances?.values?.first()?.execution() shouldBe (futureTime)
            result.schedules[disabledTask.id]?.instances shouldBe (emptyMap())

            result.affectedSchedules shouldBe (listOf(
                taskInExecution.id,
                taskInContextSwitch.id,
                taskInFuture.id,
                repeatingTask.id
            ))
        }

        "support initializing schedules (no existing schedules)" {
            val taskCount = 5

            val tasks = (0 until taskCount).map { task.copy(task.id + it) }

            val result = SchedulerOps.init(SchedulerOps.Message.Init(tasks, emptyList()))

            result.schedules.size shouldBe (taskCount)
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (emptyList())

            val schedulesWithInstances = result.schedules.count { it.value.instances.isNotEmpty() }
            schedulesWithInstances shouldBe (0)
        }

        "support initializing schedules (some existing schedules)" {
            val taskCount = 5
            val existingSchedulesCount = 3

            val instance = TaskInstance(instant = Instant.now())

            val tasks = (0 until taskCount).map { task.copy(task.id + it) }
            val schedules = tasks.take(existingSchedulesCount).map {
                TaskSchedule(
                    task = it,
                    instances = mapOf(instance.id to instance),
                    dismissed = emptyList()
                )
            }

            val result = SchedulerOps.init(SchedulerOps.Message.Init(tasks, schedules))

            result.schedules.size shouldBe (taskCount)
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (emptyList())

            val schedulesWithInstances = result.schedules.count { it.value.instances.isNotEmpty() }
            schedulesWithInstances shouldBe (existingSchedulesCount)
        }

        "support initializing schedules (all existing schedules)" {
            val taskCount = 5

            val instance = TaskInstance(instant = Instant.now())

            val tasks = (0 until taskCount).map { task.copy(task.id + it) }
            val schedules = tasks.map {
                TaskSchedule(
                    task = it,
                    instances = mapOf(instance.id to instance),
                    dismissed = emptyList()
                )
            }

            val result = SchedulerOps.init(SchedulerOps.Message.Init(tasks, schedules))

            result.schedules.size shouldBe (taskCount)
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (emptyList())

            val schedulesWithInstances = result.schedules.count { it.value.instances.isNotEmpty() }
            schedulesWithInstances shouldBe (taskCount)
        }

        "support creating new tasks" {
            val schedules = emptyMap<Int, TaskSchedule>()

            val result = SchedulerOps.put(schedules, SchedulerOps.Message.Put(task))

            result.schedules.map { it.value.task } shouldBe (listOf(task))
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (listOf(task.id))
        }

        "support updating existing tasks" {
            val within = Duration.ofMinutes(5)
            val schedules = mapOf(Pair(task.id, TaskSchedule(task).update(after = Instant.now(), within = within)))

            val updatedTask = task.copy(
                schedule = Task.Schedule.Once(instant = Instant.now().plus(Duration.ofMinutes(15))),
            )

            val result = SchedulerOps.put(schedules, SchedulerOps.Message.Put(updatedTask))

            val instance = schedules.values.first().instances.values.first()

            result.schedules.map { it.value.task } shouldBe (listOf(updatedTask))
            result.notifications shouldBe (listOf(SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)))
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (listOf(task.id))
        }

        "support removing existing tasks" {
            val schedules = evaluate(mapOf(Pair(task.id, TaskSchedule(task))))

            val result = SchedulerOps.delete(schedules, SchedulerOps.Message.Delete(task.id))

            val instance = schedules.values.first().instances.values.first()

            result.schedules shouldBe (emptyMap())
            result.notifications shouldBe (listOf(SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)))
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (listOf(task.id))
        }

        "do nothing when removing missing tasks" {
            val schedules = emptyMap<Int, TaskSchedule>()

            val result = SchedulerOps.delete(schedules, SchedulerOps.Message.Delete(task = 0))

            result.schedules shouldBe (emptyMap())
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (emptyList())
        }

        "dismiss existing tasks" {
            val schedules = evaluate(mapOf(Pair(task.id, TaskSchedule(task))))

            val instances = schedules.values.first().instances
            instances.size shouldBe (1)

            val instance = instances.values.first()

            val result = SchedulerOps.dismiss(schedules, SchedulerOps.Message.Dismiss(task.id, instance.id))

            result.schedules.map { it.value.task } shouldBe (listOf(task))
            result.notifications shouldBe (listOf(SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)))
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (listOf(task.id))

            result.schedules.values.first().instances shouldBe (emptyMap())
        }

        "do nothing when dismissing missing tasks" {
            val schedules = emptyMap<Int, TaskSchedule>()

            val result = SchedulerOps.dismiss(schedules, SchedulerOps.Message.Dismiss(task = 0, instance = UUID.randomUUID()))

            result.schedules shouldBe (emptyMap())
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (emptyList())
        }

        "postpone existing tasks" {
            val schedules = evaluate(mapOf(Pair(task.id, TaskSchedule(task))))

            val instances = schedules.values.first().instances
            instances.size shouldBe (1)

            val instance = instances.values.first()
            instance.postponed shouldBe (null)

            val postponedDuration = Duration.ofSeconds(30)
            val result = SchedulerOps.postpone(schedules, SchedulerOps.Message.Postpone(task.id, instance.id, postponedDuration))

            val updatedInstance = result.schedules.values.first().instances.values.first()
            updatedInstance.postponed shouldBe (postponedDuration)

            result.schedules.map { it.value.task } shouldBe (listOf(task))
            result.notifications shouldBe (listOf(SchedulerOps.Notification.DeleteInstanceNotifications(task.id, instance.id)))
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (listOf(task.id))
        }

        "do nothing when postponing missing tasks" {
            val schedules = emptyMap<Int, TaskSchedule>()

            val result = SchedulerOps.postpone(
                schedules,
                SchedulerOps.Message.Postpone(task = 0, instance = UUID.randomUUID(), by = Duration.ofSeconds(1))
            )

            result.schedules shouldBe (emptyMap())
            result.notifications shouldBe (emptyList())
            result.summary shouldBe (null)
            result.affectedSchedules shouldBe (emptyList())
        }
    }
})
