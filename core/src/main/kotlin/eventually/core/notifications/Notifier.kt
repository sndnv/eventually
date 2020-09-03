package eventually.core.notifications

import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSummary
import java.time.Instant
import java.util.UUID

interface Notifier {
    fun putInternalAlarm(instant: Instant)
    fun deleteInternalAlarm()

    fun putInstanceExecutionNotification(task: Task, instance: TaskInstance)
    fun putInstanceContextSwitchNotification(task: Task, instance: TaskInstance)
    fun deleteInstanceNotifications(task: UUID, instance: UUID)

    fun putSummaryNotification(summary: TaskSummary)
    fun deleteSummaryNotification()
}
