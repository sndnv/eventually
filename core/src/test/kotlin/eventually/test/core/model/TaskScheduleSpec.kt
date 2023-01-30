package eventually.test.core.model

import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.startWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class TaskScheduleSpec : WordSpec({
    "A TaskSchedule" should {
        val task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = LocalTime.of(0, 5).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                every = Duration.ofMinutes(20).toInterval()
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        fun after(): Instant = LocalTime.of(0, 50).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC)

        "support updating scheduling" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task)
            schedule.task shouldBe (task)
            schedule.instances shouldBe (emptyMap())

            val updatedSchedule = schedule.update(after = after, within = within)
            updatedSchedule.task shouldBe (task)
            updatedSchedule.instances.size shouldBe (1)

            val instance = updatedSchedule.instances.values.first()
            instance.instant shouldBe (task.schedule.next(after = after, within = within).first())
            instance.postponed shouldBe (null)
        }

        "support updating scheduling (with expired instances)" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val expiredInstance = TaskInstance(
                instant = Instant.now().minusSeconds(42)
            )

            val schedule = TaskSchedule(
                task = task,
                instances = mapOf(expiredInstance.id to expiredInstance),
                dismissed = emptyList()
            )

            schedule.task shouldBe (task)
            schedule.instances.size shouldBe (1)

            val updatedSchedule = schedule.update(after = after, within = within)
            updatedSchedule.task shouldBe (task)
            updatedSchedule.instances.size shouldBe (1)

            val instance = updatedSchedule.instances.values.first()
            instance shouldBe (expiredInstance)
        }

        "not update scheduling if a task is not active" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val inactiveTask = task.copy(isActive = false)

            val schedule = TaskSchedule(inactiveTask)
            schedule.task shouldBe (inactiveTask)
            schedule.instances shouldBe (emptyMap())

            val updatedSchedule = schedule.update(after = after, within = within)
            updatedSchedule.task shouldBe (inactiveTask)
            schedule.instances shouldBe (emptyMap())
        }

        "not update scheduling if a task's next instance already exists" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task).update(after = after, within = within)
            schedule.task shouldBe (task)
            schedule.instances.size shouldBe (1)

            val updatedSchedule = schedule.update(after = after, within = within)
            updatedSchedule.task shouldBe (task)
            updatedSchedule.instances.size shouldBe (1)

            val instance = updatedSchedule.instances.values.first()
            instance.instant shouldBe (task.schedule.next(after = after, within = within).first())
            instance.postponed shouldBe (null)
        }

        "not update scheduling if a task's next instance was already dismissed (non-repeating schedules)" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val updatedTask = task.copy(
                schedule = Task.Schedule.Once(
                    instant = LocalTime.of(0, 5).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC)
                )
            )

            val schedule = TaskSchedule(updatedTask).update(after = after, within = within)
            schedule.task shouldBe (updatedTask)
            schedule.instances.size shouldBe (1)

            val instance = schedule.instances.values.first()

            val updatedSchedule = schedule.dismiss(instance = instance.id)
            updatedSchedule.task shouldBe (updatedTask)
            updatedSchedule.instances shouldBe (emptyMap())

            val finalSchedule = updatedSchedule.update(after = after, within = within)
            finalSchedule.task shouldBe (updatedTask)
            finalSchedule.instances shouldBe (emptyMap())
        }

        "support providing the next task instance" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task).update(after = after, within = within)
            schedule.instances.size shouldBe (1)

            val instance = schedule.instances.values.first()
            when (val next = schedule.next(after = after).firstOrNull()) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    next.first shouldBe (instance)
                    next.second.isAfter(after) shouldBe (true)
                }
            }

            val multiInstanceSchedule = schedule
                .update(after = after.plus(Duration.ofMinutes(15)), within = within)
                .update(after = after.plus(Duration.ofMinutes(25)), within = within)
                .update(after = after.plus(Duration.ofMinutes(45)), within = within)

            multiInstanceSchedule.instances.size shouldBe (3)

            when (val next = multiInstanceSchedule.next(after = after).firstOrNull()) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    next.first shouldBe (instance)
                    next.second.isAfter(after) shouldBe (true)
                }
            }

            val dismissedInstanceSchedule = TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = multiInstanceSchedule.instances.map { it.value.instant }
            ).update(after = after.plus(Duration.ofMinutes(45)), within = within)

            when (val next = dismissedInstanceSchedule.next(after = after).firstOrNull()) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    val every = (task.schedule as Task.Schedule.Repeating).every
                    val duration = (every as Task.Schedule.Repeating.Interval.DurationInterval).value

                    next.first.instant shouldBe (instance.instant.plus(duration.multipliedBy(3)))
                    next.second.isAfter(after) shouldBe (true)
                }
            }
        }

        "support dismissing task instances" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task).update(after = after, within = within)
            schedule.instances.size shouldBe (1)

            val instance = schedule.instances.keys.first()
            val updatedSchedule = schedule.dismiss(instance)
            updatedSchedule.instances shouldBe (emptyMap())
        }

        "fail to dismiss missing task instances" {
            val schedule = TaskSchedule(task)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.dismiss(instance = UUID.randomUUID())
            }

            e.message should startWith("Cannot dismiss instance")
        }

        "support undoing dismissal of task instances" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task).update(after = after, within = within)
            schedule.instances.size shouldBe (1)

            val instance = schedule.instances.values.first()
            val updatedSchedule = schedule.dismiss(instance.id)
            updatedSchedule.instances shouldBe (emptyMap())
            updatedSchedule.dismissed shouldBe (listOf(instance.instant))

            val latestSchedule = updatedSchedule.undoDismiss(instance.instant)
            latestSchedule.instances.size shouldBe (1)
            latestSchedule.dismissed shouldBe (emptyList())

            val newInstance = latestSchedule.instances.values.first()
            newInstance.id shouldNotBe (instance.id)
            newInstance.instant shouldBe (instance.instant)
        }

        "fail to undo dismissal of missing task instants" {
            val schedule = TaskSchedule(task)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.undoDismiss(instant = Instant.now())
            }

            e.message should startWith("Cannot undo dismissal")
        }

        "support postponing task instances" {
            val after = after()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task).update(after = after, within = within)
            schedule.instances.size shouldBe (1)

            val instance = schedule.instances.values.first()
            instance.postponed shouldBe (null)

            val postponedDuration = Duration.ofMinutes(2)

            val updatedSchedule = schedule.postpone(instance = instance.id, by = postponedDuration)
            updatedSchedule.instances.size shouldBe (1)

            val updatedInstance = updatedSchedule.instances.values.first()
            updatedInstance.postponed shouldBe (postponedDuration)

            val finalSchedule = updatedSchedule.postpone(instance = instance.id, by = postponedDuration)
            finalSchedule.instances.size shouldBe (1)

            val finalInstance = finalSchedule.instances.values.first()
            finalInstance.postponed shouldBe (postponedDuration.multipliedBy(2))
        }

        "fail to postpone missing task instances" {
            val schedule = TaskSchedule(task)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.postpone(instance = UUID.randomUUID(), by = Duration.ofSeconds(1))
            }

            e.message should startWith("Cannot postpone instance")
        }

        "support schedule matching" {
            val after = after()
            val within = Duration.ofMinutes(5)
            val tolerance = Duration.ofSeconds(2)

            val schedule = TaskSchedule(task)

            schedule.match(
                instant = after,
                withTolerance = tolerance
            ) shouldBe (emptyList())

            val updatedSchedule = schedule.update(after = after, within = within)
            updatedSchedule.instances.size shouldBe (1)

            val instance = updatedSchedule.instances.values.first()
            val diff = Duration.between(after, instance.execution())

            updatedSchedule.match(
                instant = after.minus(diff).minusSeconds(1),
                withTolerance = tolerance
            ) shouldBe (listOf(TaskSchedule.Matched.None))

            updatedSchedule.match(
                instant = after.plus(diff).minus(task.contextSwitch).plusSeconds(1),
                withTolerance = tolerance
            ) shouldBe (listOf(TaskSchedule.Matched.ContextSwitch(instance)))

            updatedSchedule.match(
                instant = after.plus(diff),
                withTolerance = tolerance
            ) shouldBe (listOf(TaskSchedule.Matched.Instant(instance)))
        }

        "support adjusting scheduling when a task's schedule is updated" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task)
            schedule.task shouldBe (task)
            schedule.instances shouldBe (emptyMap())

            val updatedSchedule = schedule
                .update(after = now.minus(Duration.ofMinutes(45)), within = within)
                .update(after = now.minus(Duration.ofMinutes(25)), within = within)
                .update(after = now, within = within)
            updatedSchedule.task shouldBe (task)
            updatedSchedule.instances.size shouldBe (3)

            val updatedTask = task.copy(
                schedule = Task.Schedule.Repeating(
                    start = LocalTime.of(0, 5).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                    every = Duration.ofMinutes(21).toInterval()
                )
            )

            val scheduleWithUpdatedTask = updatedSchedule.withTask(updatedTask)
            scheduleWithUpdatedTask.task shouldBe (updatedTask)
            scheduleWithUpdatedTask.instances.size shouldBe (2)

            scheduleWithUpdatedTask.instances.values.forEach { instance ->
                instance.execution().isBefore(now) shouldBe (true)
            }
        }

        "support adjusting scheduling when a task's state is updated" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task)
            schedule.task shouldBe (task)
            schedule.instances shouldBe (emptyMap())

            val updatedSchedule = schedule
                .update(after = now.minus(Duration.ofMinutes(45)), within = within)
                .update(after = now.minus(Duration.ofMinutes(25)), within = within)
                .update(after = now, within = within)
            updatedSchedule.task shouldBe (task)
            updatedSchedule.instances.size shouldBe (3)

            val updatedTask = task.copy(isActive = false)

            val scheduleWithUpdatedTask = updatedSchedule.withTask(updatedTask)
            scheduleWithUpdatedTask.task shouldBe (updatedTask)
            scheduleWithUpdatedTask.instances.size shouldBe (2)

            scheduleWithUpdatedTask.instances.values.forEach { instance ->
                instance.execution().isBefore(now) shouldBe (true)
            }
        }

        "not adjust scheduling when a task's details are updated" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)

            val schedule = TaskSchedule(task)
            schedule.task shouldBe (task)
            schedule.instances shouldBe (emptyMap())

            val updatedSchedule = schedule
                .update(after = now.minus(Duration.ofMinutes(45)), within = within)
                .update(after = now.minus(Duration.ofMinutes(25)), within = within)
                .update(after = now, within = within)
            updatedSchedule.task shouldBe (task)
            updatedSchedule.instances.size shouldBe (3)

            val updatedTask = task.copy(
                id = 1,
                name = "other-name",
                description = "other-description",
                goal = "other-goal",
                contextSwitch = Duration.ofMinutes(42)
            )

            val scheduleWithUpdatedTask = updatedSchedule.withTask(updatedTask)
            scheduleWithUpdatedTask.task shouldBe (updatedTask)
            scheduleWithUpdatedTask.instances.size shouldBe (3)
        }
    }
})
