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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

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
            isActive = true,
            color = 1
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

        "not update scheduling for unreachable days".config(timeout = 5.seconds, blockingTest = true) {
            val monday = LocalDate.of(2026, 11, 9).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()

            val unreachable = task.copy(
                schedule = Task.Schedule.Repeating(
                    start = monday,
                    every = Period.ofDays(7).toInterval(),
                    days = setOf(DayOfWeek.TUESDAY)
                )
            )

            val schedule = TaskSchedule(unreachable).update(after = monday, within = Duration.ofDays(30))

            schedule.task shouldBe (unreachable)
            schedule.instances shouldBe (emptyMap())
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

        "not recreate once instances after dismissing them when they were postponed while overdue" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minusSeconds(30)))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val instance = schedule.instances.values.single()

            val postponed = schedule.postpone(instance.id, by = Duration.ofMinutes(10))
            val postponedInstance = postponed.instances.values.single()
            postponedInstance.instant.isAfter(instance.instant) shouldBe (true)

            val dismissed = postponed.dismiss(postponedInstance.id)
            dismissed.dismissed shouldBe (listOf(postponedInstance.instant))

            val updated = dismissed.update(after = Instant.now(), within = within)
            updated.instances shouldBe (emptyMap())
            updated.dismissed shouldBe (listOf(postponedInstance.instant))
        }

        "restore once instances that were postponed while overdue and then dismissed" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minusSeconds(30)))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val postponed = schedule.postpone(schedule.instances.keys.single(), by = Duration.ofMinutes(10))
            val postponedInstance = postponed.instances.values.single()
            val dismissed = postponed.dismiss(postponedInstance.id)

            val restored = dismissed
                .undoDismiss(postponedInstance.instant)
                .update(after = Instant.now(), within = within)
            restored.instances.values.map { it.instant } shouldBe (listOf(postponedInstance.instant))
            restored.dismissed shouldBe (emptyList())
        }

        "create once instances when rescheduled after an earlier dismissal" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minusSeconds(30)))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val dismissed = schedule.dismiss(schedule.instances.keys.single())

            val rescheduled = once.copy(schedule = Task.Schedule.Once(instant = now.plus(Duration.ofHours(1))))
            val updated = dismissed.withTask(rescheduled).update(after = Instant.now(), within = within)

            updated.instances.values.map { it.instant } shouldBe (listOf(now.plus(Duration.ofHours(1))))
            updated.dismissed shouldBe (emptyList())
        }

        "create once instances when rescheduled before an earlier dismissal" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minusSeconds(30)))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val dismissed = schedule.dismiss(schedule.instances.keys.single())

            val rescheduled = once.copy(schedule = Task.Schedule.Once(instant = now.minusSeconds(60)))
            val updated = dismissed.withTask(rescheduled).update(after = Instant.now(), within = within)

            updated.instances.values.map { it.instant } shouldBe (listOf(now.minusSeconds(60)))
            updated.dismissed shouldBe (emptyList())
        }

        "create once instances when a repeating task with upcoming dismissals becomes a once task" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val repeating = task.copy(
                schedule = Task.Schedule.Repeating(
                    start = now.plus(Duration.ofHours(2)),
                    every = Duration.ofDays(1).toInterval()
                )
            )

            val schedule = TaskSchedule(repeating).update(after = now, within = within)
            val dismissed = schedule.dismiss(schedule.instances.keys.single())
            dismissed.dismissed shouldBe (listOf(now.plus(Duration.ofHours(2))))

            val once = repeating.copy(schedule = Task.Schedule.Once(instant = now.plus(Duration.ofHours(1))))
            val updated = dismissed.withTask(once).update(after = Instant.now(), within = within)

            updated.instances.values.map { it.instant } shouldBe (listOf(now.plus(Duration.ofHours(1))))
            updated.dismissed shouldBe (emptyList())
        }

        "keep instances when only the details of a once task are updated" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minus(Duration.ofMinutes(30))))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val postponed = schedule.postpone(schedule.instances.keys.single(), by = Duration.ofMinutes(10))

            val updated = postponed.withTask(once.copy(name = "other-name", executionAlarm = true))

            updated.instances shouldBe (postponed.instances)
            updated.dismissed shouldBe (postponed.dismissed)
        }

        "replace all instances when a once task is rescheduled" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minus(Duration.ofMinutes(30))))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val past = schedule.instances.values.single()
            past.execution().isBefore(now) shouldBe (true)

            val rescheduled = once.copy(schedule = Task.Schedule.Once(instant = now.plus(Duration.ofHours(1))))
            val withTask = schedule.withTask(rescheduled)
            withTask.instances shouldBe (emptyMap())

            val updated = withTask.update(after = Instant.now(), within = within)
            updated.instances.values.map { it.instant } shouldBe (listOf(now.plus(Duration.ofHours(1))))
            updated.instances.values.map { it.postponed } shouldBe (listOf(null))
        }

        "replace postponed instances when a once task is rescheduled" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minusSeconds(30)))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val postponed = schedule.postpone(schedule.instances.keys.single(), by = Duration.ofMinutes(10))

            val rescheduled = once.copy(schedule = Task.Schedule.Once(instant = now.plus(Duration.ofHours(1))))
            postponed.withTask(rescheduled).instances shouldBe (emptyMap())
        }

        "keep past instances when a once task is disabled" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minus(Duration.ofMinutes(30))))

            val schedule = TaskSchedule(once).update(after = now, within = within)

            schedule.withTask(once.copy(isActive = false)).instances shouldBe (schedule.instances)
        }

        "keep past instances when a once task becomes repeating" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)
            val once = task.copy(schedule = Task.Schedule.Once(instant = now.minus(Duration.ofMinutes(30))))

            val schedule = TaskSchedule(once).update(after = now, within = within)
            val repeating = once.copy(
                schedule = Task.Schedule.Repeating(
                    start = now.plus(Duration.ofHours(1)),
                    every = Duration.ofDays(1).toInterval()
                )
            )

            schedule.withTask(repeating).instances shouldBe (schedule.instances)
        }

        "fail to undo dismissal of missing task instants (non-repeating schedules)" {
            val once = task.copy(schedule = Task.Schedule.Once(instant = after()))
            val schedule = TaskSchedule(once)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.undoDismiss(instant = Instant.now())
            }

            e.message should startWith("Cannot undo dismissal")
        }

        "fail to undo dismissal of future instants that were not dismissed (repeating schedules)" {
            val schedule = TaskSchedule(task)
            val future = Instant.now().plus(Duration.ofHours(1))

            schedule.canUndoDismiss(future) shouldBe (false)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.undoDismiss(instant = future)
            }

            e.message should startWith("Cannot undo dismissal")
        }

        "fail to undo dismissal of instants with an existing instance (repeating schedules)" {
            val schedule = TaskSchedule(task).update(after = after(), within = Duration.ofMinutes(5))
            val instance = schedule.instances.values.first()

            val e = shouldThrow<IllegalArgumentException> {
                schedule.undoDismiss(instant = instance.instant)
            }

            e.message should startWith("Cannot undo dismissal")
        }

        "trim dismissed instants older than the retention period (repeating schedules)" {
            val after = after()

            val schedule = TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = listOf(
                    after.minus(Duration.ofDays(31)),
                    after.minus(Duration.ofDays(29)),
                    after.minus(Duration.ofHours(1))
                )
            ).update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 100)

            schedule.dismissed shouldBe (listOf(after.minus(Duration.ofDays(29)), after.minus(Duration.ofHours(1))))
        }

        "keep dismissed instants at the retention boundary (repeating schedules)" {
            val after = after()
            val boundary = after.minus(Duration.ofDays(30))

            val schedule = TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = listOf(boundary.minusMillis(1), boundary)
            ).update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 100)

            schedule.dismissed shouldBe (listOf(boundary))
        }

        "drop dismissed instants at the update instant beyond the limit (repeating schedules)" {
            val after = after()
            val upcoming = after.plus(Duration.ofHours(1))

            val schedule = TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = listOf(after, upcoming)
            ).update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 1)

            schedule.dismissed shouldBe (listOf(upcoming))
        }

        "keep only the newest dismissed instants up to the limit (repeating schedules)" {
            val after = after()

            val schedule = TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = listOf(
                    after.minus(Duration.ofHours(1)),
                    after.minus(Duration.ofHours(3)),
                    after.minus(Duration.ofHours(2))
                )
            ).update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 2)

            schedule.dismissed shouldBe (listOf(after.minus(Duration.ofHours(1)), after.minus(Duration.ofHours(2))))
        }

        "keep upcoming dismissed instants regardless of the limit (repeating schedules)" {
            val after = after()
            val within = Duration.ofMinutes(40)
            val upcoming = task.schedule.next(after = after, within = within)

            upcoming.size shouldBe (2)

            val schedule = TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = listOf(after.minus(Duration.ofHours(1))) + upcoming
            ).update(after = after, within = within, retention = Duration.ofDays(30), limit = 1)

            schedule.dismissed shouldBe (upcoming)
            schedule.instances.values.none { it.instant in upcoming } shouldBe (true)

            val rescheduled = schedule
                .copy(instances = emptyMap())
                .update(after = after, within = within, retention = Duration.ofDays(30), limit = 1)

            rescheduled.instances.values.none { it.instant in upcoming } shouldBe (true)
        }

        "not trim dismissed instants of non-repeating schedules" {
            val after = after()
            val dismissed = listOf(after.minus(Duration.ofDays(60)), after.minus(Duration.ofDays(45)))

            val once = task.copy(schedule = Task.Schedule.Once(instant = dismissed.last()))

            val schedule = TaskSchedule(task = once, instances = emptyMap(), dismissed = dismissed)
                .update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 1)

            schedule.dismissed shouldBe (dismissed)
        }

        "not trim dismissed instants of inactive tasks" {
            val after = after()
            val dismissed = listOf(after.minus(Duration.ofDays(60)), after.minus(Duration.ofDays(45)))

            val inactive = task.copy(isActive = false)

            val schedule = TaskSchedule(task = inactive, instances = emptyMap(), dismissed = dismissed)
                .update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 1)

            schedule.dismissed shouldBe (dismissed)
        }

        "support undoing dismissal of instants kept after trimming" {
            val after = after()
            val trimmed = after.minus(Duration.ofDays(31))
            val kept = after.minus(Duration.ofHours(1))

            val schedule = TaskSchedule(task = task, instances = emptyMap(), dismissed = listOf(trimmed, kept))
                .update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 100)

            schedule.dismissed shouldBe (listOf(kept))

            val undone = schedule.undoDismiss(kept)
            undone.dismissed shouldBe (emptyList())
            undone.instances.values.any { it.instant == kept } shouldBe (true)
        }

        "support undoing dismissal of instants already trimmed (repeating schedules)" {
            val after = after()
            val overdue = TaskInstance(instant = after.minus(Duration.ofDays(31)))

            val schedule = TaskSchedule(task = task, instances = mapOf(overdue.id to overdue), dismissed = emptyList())
                .dismiss(overdue.id)
                .update(after = after, within = Duration.ofMinutes(5), retention = Duration.ofDays(30), limit = 100)

            schedule.dismissed shouldBe (emptyList())
            schedule.canUndoDismiss(overdue.instant) shouldBe (true)

            val undone = schedule.undoDismiss(overdue.instant)
            undone.dismissed shouldBe (emptyList())
            undone.instances.values.count { it.instant == overdue.instant } shouldBe (1)
            undone.canUndoDismiss(overdue.instant) shouldBe (false)

            shouldThrow<IllegalArgumentException> { undone.undoDismiss(overdue.instant) }
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
                contextSwitch = Duration.ofMinutes(42),
                executionAlarm = true
            )

            val scheduleWithUpdatedTask = updatedSchedule.withTask(updatedTask)
            scheduleWithUpdatedTask.task shouldBe (updatedTask)
            scheduleWithUpdatedTask.instances.size shouldBe (3)
        }
    }
})
