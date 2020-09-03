package eventually.test.core.scheduling

import eventually.core.model.Task
import eventually.core.model.TaskSummaryConfig
import eventually.core.scheduling.DefaultScheduler
import eventually.test.core.mocks.MockNotifier
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.be
import io.kotest.matchers.should
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

            scheduler.getAsync().await() should be(emptyList())

            scheduler.put(task)
            scheduler.getAsync().await().map { it.task } should be(listOf(task))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(1)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(1)
        }

        "support updating existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.put(task)
            scheduler.getAsync().await().map { it.task } should be(listOf(task))

            val updatedTask = task.copy(name = "other-name")
            scheduler.put(updatedTask)
            scheduler.getAsync().await().map { it.task } should be(listOf(updatedTask))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(2)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(2)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(2)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(2)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(2)
        }

        "support removing existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.put(task)
            scheduler.getAsync().await().map { it.task } should be(listOf(task))

            scheduler.delete(task.id)

            scheduler.getAsync().await() should be(emptyList())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(2)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(1)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(2)
        }

        "do nothing when removing missing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.delete(task = UUID.randomUUID())

            scheduler.getAsync().await() should be(emptyList())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)
        }

        "dismiss existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.put(task)

            val schedules = scheduler.getAsync().await()
            schedules.map { it.task } should be(listOf(task))

            val instances = schedules.first().instances
            instances.size should be(1)

            val instance = instances.values.first()

            scheduler.dismiss(task.id, instance.id)

            val updatedSchedules = scheduler.getAsync().await()
            updatedSchedules.map { it.task } should be(listOf(task))

            updatedSchedules.first().instances should be(emptyMap())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(2)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(1)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(1)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(2)
        }

        "do nothing when dismissing missing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.dismiss(task.id, instance = UUID.randomUUID())

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)
        }

        "postpone existing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.put(task)

            val schedules = scheduler.getAsync().await()
            schedules.map { it.task } should be(listOf(task))

            val instances = schedules.first().instances
            instances.size should be(1)

            val instance = instances.values.first()

            val postponedDuration = Duration.ofSeconds(30)
            scheduler.postpone(task.id, instance.id, by = postponedDuration)
            scheduler.postpone(task.id, instance.id, by = postponedDuration)
            scheduler.postpone(task.id, instance.id, by = postponedDuration)

            val updatedSchedules = scheduler.getAsync().await()
            updatedSchedules.map { it.task } should be(listOf(task))

            val updatedInstances = updatedSchedules.first().instances
            updatedInstances.size should be(1)

            val updatedInstance = updatedInstances.values.first()
            updatedInstance.postponed should be(postponedDuration.multipliedBy(3))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(4)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(4)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(4)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(3)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(4)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(4)
        }

        "do nothing when postponing missing tasks" {
            val scope = TestCoroutineScope()

            val notifier = MockNotifier()

            val scheduler = DefaultScheduler(
                notifier = notifier,
                config = config,
                scope = scope
            )

            scheduler.getAsync().await() should be(emptyList())

            scheduler.postpone(task.id, instance = UUID.randomUUID(), by = Duration.ofSeconds(1))

            notifier.statistics()[MockNotifier.Statistic.PutInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInternalAlarm] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceExecutionNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutInstanceContextSwitchNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteInstanceNotifications] should be(0)
            notifier.statistics()[MockNotifier.Statistic.PutSummaryNotification] should be(0)
            notifier.statistics()[MockNotifier.Statistic.DeleteSummaryNotification] should be(0)
        }
    }
})
