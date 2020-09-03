package eventually.test.core.mocks

import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSummary
import eventually.core.notifications.Notifier
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class MockNotifier : Notifier {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.PutInternalAlarm to AtomicInteger(0),
        Statistic.DeleteInternalAlarm to AtomicInteger(0),
        Statistic.PutInstanceExecutionNotification to AtomicInteger(0),
        Statistic.PutInstanceContextSwitchNotification to AtomicInteger(0),
        Statistic.DeleteInstanceNotifications to AtomicInteger(0),
        Statistic.PutSummaryNotification to AtomicInteger(0),
        Statistic.DeleteSummaryNotification to AtomicInteger(0),
    )

    override fun putInternalAlarm(instant: Instant) {
        (stats[Statistic.PutInternalAlarm]
            ?: error("Statistic [${Statistic.PutInternalAlarm}] not found")).incrementAndGet()
    }

    override fun deleteInternalAlarm() {
        (stats[Statistic.DeleteInternalAlarm]
            ?: error("Statistic [${Statistic.DeleteInternalAlarm}] not found")).incrementAndGet()
    }

    override fun putInstanceExecutionNotification(task: Task, instance: TaskInstance) {
        (stats[Statistic.PutInstanceExecutionNotification]
            ?: error("Statistic [${Statistic.PutInstanceExecutionNotification}] not found")).incrementAndGet()
    }

    override fun putInstanceContextSwitchNotification(task: Task, instance: TaskInstance) {
        (stats[Statistic.PutInstanceContextSwitchNotification]
            ?: error("Statistic [${Statistic.PutInstanceContextSwitchNotification}] not found")).incrementAndGet()
    }

    override fun deleteInstanceNotifications(task: UUID, instance: UUID) {
        (stats[Statistic.DeleteInstanceNotifications]
            ?: error("Statistic [${Statistic.DeleteInstanceNotifications}] not found")).incrementAndGet()
    }

    override fun putSummaryNotification(summary: TaskSummary) {
        (stats[Statistic.PutSummaryNotification]
            ?: error("Statistic [${Statistic.PutSummaryNotification}] not found")).incrementAndGet()
    }

    override fun deleteSummaryNotification() {
        (stats[Statistic.DeleteSummaryNotification]
            ?: error("Statistic [${Statistic.DeleteSummaryNotification}] not found")).incrementAndGet()
    }

    fun statistics(): Map<Statistic, Int> = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object PutInternalAlarm : Statistic()
        object DeleteInternalAlarm : Statistic()
        object PutInstanceExecutionNotification : Statistic()
        object PutInstanceContextSwitchNotification : Statistic()
        object DeleteInstanceNotifications : Statistic()
        object PutSummaryNotification : Statistic()
        object DeleteSummaryNotification : Statistic()
    }
}
