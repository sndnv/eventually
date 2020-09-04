package eventually.test.core.scheduling

import eventually.core.model.Task
import eventually.core.model.TaskSummaryConfig
import eventually.core.scheduling.DefaultScheduler
import eventually.test.core.mocks.MockNotifier
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import java.time.Duration
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSchedulerSpec : WordSpec({
    "A DefaultScheduler" should {
        val task = Task(
            id = UUID.randomUUID(),
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Once(instant = Instant.now().plus(Duration.ofMinutes(3))),
            priority = Task.Priority.High,
            contextSwitch = Duration.ofMinutes(5)
        )

        val config = TaskSummaryConfig(
            summarySize = Duration.ofMinutes(15)
        )

        "support creating new tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.put(task)
            scheduler.getAsync().await().map { it.task } shouldBe(listOf(task))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(1)
        }

        "support updating existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.put(task)
            scheduler.getAsync().await().map { it.task } shouldBe(listOf(task))

            val updatedTask = task.copy(name = "other-name")
            scheduler.put(updatedTask)
            scheduler.getAsync().await().map { it.task } shouldBe(listOf(updatedTask))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(2)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(2)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(2)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(2)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(2)
        }

        "support removing existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.put(task)
            scheduler.getAsync().await().map { it.task } shouldBe(listOf(task))

            scheduler.delete(task.id)

            scheduler.getAsync().await() shouldBe(emptyList())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(2)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(2)
        }

        "do nothing when removing missing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.delete(task = UUID.randomUUID())

            scheduler.getAsync().await() shouldBe(emptyList())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)
        }

        "dismiss existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.put(task)

            val schedules = scheduler.getAsync().await()
            schedules.map { it.task } shouldBe(listOf(task))

            val instances = schedules.first().instances
            instances.size shouldBe(1)

            val instance = instances.values.first()

            scheduler.dismiss(task.id, instance.id)

            val updatedSchedules = scheduler.getAsync().await()
            updatedSchedules.map { it.task } shouldBe(listOf(task))

            updatedSchedules.first().instances shouldBe(emptyMap())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(2)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(2)
        }

        "do nothing when dismissing missing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.dismiss(task.id, instance = UUID.randomUUID())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)
        }

        "postpone existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.put(task)

            val schedules = scheduler.getAsync().await()
            schedules.map { it.task } shouldBe(listOf(task))

            val instances = schedules.first().instances
            instances.size shouldBe(1)

            val instance = instances.values.first()

            val postponedDuration = Duration.ofSeconds(30)
            scheduler.postpone(task.id, instance.id, by = postponedDuration)
            scheduler.postpone(task.id, instance.id, by = postponedDuration)
            scheduler.postpone(task.id, instance.id, by = postponedDuration)

            val updatedSchedules = scheduler.getAsync().await()
            updatedSchedules.map { it.task } shouldBe(listOf(task))

            val updatedInstances = updatedSchedules.first().instances
            updatedInstances.size shouldBe(1)

            val updatedInstance = updatedInstances.values.first()
            updatedInstance.postponed shouldBe(postponedDuration.multipliedBy(3))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(4)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(4)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(4)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(3)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(4)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(4)
        }

        "do nothing when postponing missing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() shouldBe(emptyList())

            scheduler.postpone(task.id, instance = UUID.randomUUID(), by = Duration.ofSeconds(1))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] shouldBe(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] shouldBe(0)
        }
    }
})
