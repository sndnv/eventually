package eventually.test.core.model

import eventually.core.model.Task
import eventually.core.model.TaskSchedule
import eventually.test.core.mocks.MockNotifier
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.startWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.*

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
            schedule.task shouldBe(task)
            schedule.instances shouldBe(emptyMap())

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.task shouldBe(task)
            updatedSchedule.instances.size shouldBe(1)

            val instance = updatedSchedule.instances.values.first()
            instance.instant shouldBe(task.schedule.next(after = now))
            instance.postponed shouldBe(null)
        }

        "not update scheduling if a task's next instance already exists" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.task shouldBe(task)
            schedule.instances.size shouldBe(1)

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.task shouldBe(task)
            updatedSchedule.instances.size shouldBe(1)

            val instance = updatedSchedule.instances.values.first()
            instance.instant shouldBe(task.schedule.next(after = now))
            instance.postponed shouldBe(null)
        }

        "not update scheduling if a task's next instance was already dismissed" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.task shouldBe(task)
            schedule.instances.size shouldBe(1)

            val instance = schedule.instances.values.first()

            val updatedSchedule = schedule.dismiss(instance = instance.id)
            updatedSchedule.task shouldBe(task)
            updatedSchedule.instances shouldBe(emptyMap())

            val finalSchedule = updatedSchedule.update(after = now)
            finalSchedule.task shouldBe(task)
            finalSchedule.instances shouldBe(emptyMap())
        }

        "support providing the next task instance" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.instances.size shouldBe(1)

            val instance = schedule.instances.values.first()
            when (val next = schedule.next(after = now)) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    next.first shouldBe(instance)
                    next.second.isAfter(now) shouldBe(true)
                }
            }

            val multiInstanceSchedule = schedule
                .update(after = now.plus(Duration.ofMinutes(15)))
                .update(after = now.plus(Duration.ofMinutes(25)))
                .update(after = now.plus(Duration.ofMinutes(35)))

            multiInstanceSchedule.instances.size shouldBe(3)

            when (val next = multiInstanceSchedule.next(after = now)) {
                null -> fail("Expected next task instance but none found")
                else -> {
                    next.first shouldBe(instance)
                    next.second.isAfter(now) shouldBe(true)
                }
            }
        }

        "support dismissing task instances" {
            val now = Instant.now()

            val schedule = TaskSchedule(task).update(after = now)
            schedule.instances.size shouldBe(1)

            val instance = schedule.instances.keys.first()
            val updatedSchedule = schedule.dismiss(instance)
            updatedSchedule.instances shouldBe(emptyMap())
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
            schedule.instances.size shouldBe(1)

            val instance = schedule.instances.values.first()
            instance.postponed shouldBe(null)

            val postponedDuration = Duration.ofMinutes(2)

            val updatedSchedule = schedule.postpone(instance = instance.id, by = postponedDuration)
            updatedSchedule.instances.size shouldBe(1)

            val updatedInstance = updatedSchedule.instances.values.first()
            updatedInstance.postponed shouldBe(postponedDuration)

            val finalSchedule = updatedSchedule.postpone(instance = instance.id, by = postponedDuration)
            finalSchedule.instances.size shouldBe(1)

            val finalInstance = finalSchedule.instances.values.first()
            finalInstance.postponed shouldBe(postponedDuration.multipliedBy(2))
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
            ) shouldBe(TaskSchedule.Matched.None)

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.instances.size shouldBe(1)

            val instance = updatedSchedule.instances.values.first()
            val diff = Duration.between(now, instance.execution())

            updatedSchedule.match(
                instant = now.minus(diff).minusSeconds(1),
                withTolerance = tolerance
            ) shouldBe(TaskSchedule.Matched.None)

            updatedSchedule.match(
                instant = now.plus(diff).minus(task.contextSwitch).plusSeconds(1),
                withTolerance = tolerance
            ) shouldBe(TaskSchedule.Matched.ContextSwitch(instance))

            updatedSchedule.match(
                instant = now.plus(diff),
                withTolerance = tolerance
            ) shouldBe(TaskSchedule.Matched.Instant(instance))
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
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)

            val updatedSchedule = schedule.update(after = now)
            updatedSchedule.instances.size shouldBe(1)

            val instance = updatedSchedule.instances.values.first()
            val diff = Duration.between(now, instance.execution())

            updatedSchedule.notify(
                instant = now.minus(diff),
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)

            updatedSchedule.notify(
                instant = now.plus(diff).minus(task.contextSwitch).plusSeconds(1),
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)

            updatedSchedule.notify(
                instant = now.plus(diff),
                withTolerance = tolerance,
                notifier = notifier
            )
            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)
        }
    }
})
