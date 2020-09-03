package eventually.test.core.model

import eventually.core.model.Task
import eventually.core.model.TaskSchedule
import eventually.test.core.mocks.MockNotifier
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.string.startWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class TaskScheduleSpec : WordSpec({
    "A TaskSchedule" should {
        val task = Task(
            id = UUID.randomUUID(),
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                time = LocalTime.of(0, 15),
                every = Duration.ofMinutes(20)
            ),
            priority = Task.Priority.High,
            contextSwitch = Duration.ofMinutes(5)
        )

        "support updating scheduling" {
            val now = Instant.now()

            val schedule = TaskSchedule(task)
            schedule.task should be(task)
            schedule.instances should be(emptyMap())

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.task should be(task)
            updatedSchedule.instances.size should be(1)

            val instance = updatedSchedule.instances.values.first()
            instance.instant should be(task.schedule.next(after = now))
            instance.postponed should be(null)
        }

        "not update scheduling if a task's next instance already exists" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.task should be(task)
            schedule.instances.size should be(1)

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.task should be(task)
            updatedSchedule.instances.size should be(1)

            val instance = updatedSchedule.instances.values.first()
            instance.instant should be(task.schedule.next(after = now))
            instance.postponed should be(null)
        }

        "not update scheduling if a task's next instance was already dismissed" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.task should be(task)
            schedule.instances.size should be(1)

            val instance = schedule.instances.values.first()

            val updatedSchedule = schedule.dismiss(instance = instance.id)
            updatedSchedule.task should be(task)
            updatedSchedule.instances should be(emptyMap())

            val finalSchedule = updatedSchedule.update(after = now)
            finalSchedule.task should be(task)
            finalSchedule.instances should be(emptyMap())
        }

        "support providing the next task instance" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.instances.size should be(1)

            val instance = schedule.instances.values.first()
            when (val next = schedule.next(after = now)) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    next.first should be(instance)
                    next.second.isAfter(now) should be(true)
                }
            }

            val multiInstanceSchedule = schedule
                .update(after = now.plus(Duration.ofMinutes(15)))
                .update(after = now.plus(Duration.ofMinutes(25)))
                .update(after = now.plus(Duration.ofMinutes(35)))

            multiInstanceSchedule.instances.size should be(3)

            when (val next = multiInstanceSchedule.next(after = now)) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    next.first should be(instance)
                    next.second.isAfter(now) should be(true)
                }
            }
        }

        "support dismissing task instances" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.instances.size should be(1)

            val instance = schedule.instances.keys.first()
            val updatedSchedule = schedule.dismiss(instance)
            updatedSchedule.instances should be(emptyMap())
        }

        "fail to dismiss missing task instances" {
            val schedule = TaskSchedule(task)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.dismiss(instance = UUID.randomUUID())
            }

            e.message should startWith("Cannot dismiss instance")
        }

        "support postponing task instances" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.instances.size should be(1)

            val instance = schedule.instances.values.first()
            instance.postponed should be(null)

            val postponedDuration = Duration.ofMinutes(2)

            val updatedSchedule = schedule.postpone(instance = instance.id, by = postponedDuration)
            updatedSchedule.instances.size should be(1)

            val updatedInstance = updatedSchedule.instances.values.first()
            updatedInstance.postponed should be(postponedDuration)

            val finalSchedule = updatedSchedule.postpone(instance = instance.id, by = postponedDuration)
            finalSchedule.instances.size should be(1)

            val finalInstance = finalSchedule.instances.values.first()
            finalInstance.postponed should be(postponedDuration.multipliedBy(2))
        }

        "fail to postpone missing task instances" {
            val schedule = TaskSchedule(task)

            val e = shouldThrow<IllegalArgumentException> {
                schedule.postpone(instance = UUID.randomUUID(), by = Duration.ofSeconds(1))
            }

            e.message should startWith("Cannot postpone instance")
        }

        "support schedule matching" {
            val now = Instant.now()
            val tolerance = Duration.ofSeconds(2)

            val schedule = TaskSchedule(task)

            schedule.match(
                instant = now,
                withTolerance = tolerance
            ) should be(TaskSchedule.Matched.None)

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.instances.size should be(1)

            val instance = updatedSchedule.instances.values.first()
            val diff = Duration.between(now, instance.execution())

            updatedSchedule.match(
                instant = now.minus(diff).minusSeconds(1),
                withTolerance = tolerance
            ) should be(TaskSchedule.Matched.None)

            updatedSchedule.match(
                instant = now.plus(diff).minus(task.contextSwitch).plusSeconds(1),
                withTolerance = tolerance
            ) should be(TaskSchedule.Matched.ContextSwitch(instance))

            updatedSchedule.match(
                instant = now.plus(diff),
                withTolerance = tolerance
            ) should be(TaskSchedule.Matched.Instant(instance))
        }

        "support sending notifications via a provided notifier" {
            val notifier = MockNotifier()

            val now = Instant.now()
            val tolerance = Duration.ofSeconds(2)

            val schedule = TaskSchedule(task)

            schedule.notify(
                instant = now,
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.instances.size should be(1)

            val instance = updatedSchedule.instances.values.first()
            val diff = Duration.between(now, instance.execution())

            updatedSchedule.notify(
                instant = now.minus(diff),
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)

            updatedSchedule.notify(
                instant = now.plus(diff).minus(task.contextSwitch).plusSeconds(1),
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)

            updatedSchedule.notify(
                instant = now.plus(diff),
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)
        }
    }
})
