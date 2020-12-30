package eventually.client.scheduling

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceContextSwitchNotification
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionNotification
import eventually.client.scheduling.WorkManagerExtensions.deleteEvaluationAlarm
import eventually.client.scheduling.WorkManagerExtensions.putEvaluationAlarm
import eventually.core.scheduling.SchedulerOps
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

class NotificationQueue(
    val notifications: Queue<SchedulerOps.Notification>
) {
    fun enqueue(additional: List<SchedulerOps.Notification>) {
        notifications.addAll(additional)
    }

    suspend fun distinct(
        notificationViewModel: NotificationViewModel
    ): NotificationQueue {
        val updated = LinkedList<SchedulerOps.Notification>()
        val instances = mutableMapOf<UUID, ExistingNotifications>()

        notifications.forEach {
            when (it) {
                is SchedulerOps.Notification.PutEvaluationAlarm -> updated.offer(it)

                is SchedulerOps.Notification.DeleteEvaluationAlarm -> updated.offer(it)

                is SchedulerOps.Notification.PutInstanceExecutionNotification -> {
                    val existing = notificationViewModel.get(it.task.id, it.instance.id, type = "execution").await()

                    if (existing == null || existing.hash != it.instance.hashCode()) {
                        instances[it.instance.id] = (instances[it.instance.id] ?: ExistingNotifications()).updated(it)
                    }
                }

                is SchedulerOps.Notification.PutInstanceContextSwitchNotification -> {
                    val existing = notificationViewModel.get(it.task.id, it.instance.id, type = "context").await()

                    if (existing == null || existing.hash != it.instance.hashCode()) {
                        instances[it.instance.id] = (instances[it.instance.id] ?: ExistingNotifications()).updated(it)
                    }
                }

                is SchedulerOps.Notification.DeleteInstanceNotifications -> {
                    instances[it.instance] = (instances[it.instance] ?: ExistingNotifications()).updated(it)
                }
            }
        }

        instances.values.forEach { existing -> updated.addAll(existing.toList()) }

        return NotificationQueue(notifications = updated)
    }

    suspend fun release(
        context: Context,
        workManager: WorkManager,
        notificationManager: NotificationManager,
        notificationViewModel: NotificationViewModel
    ): NotificationQueue {
        notifications.forEach {
            when (it) {
                is SchedulerOps.Notification.PutEvaluationAlarm -> {
                    workManager.putEvaluationAlarm(it.instant)
                }

                is SchedulerOps.Notification.DeleteEvaluationAlarm -> {
                    workManager.deleteEvaluationAlarm()
                }

                is SchedulerOps.Notification.PutInstanceExecutionNotification -> {
                    notificationManager.putInstanceExecutionNotification(context, it.task, it.instance)
                    notificationViewModel.put(it.task, it.instance, type = "execution").await()
                }

                is SchedulerOps.Notification.PutInstanceContextSwitchNotification -> {
                    notificationManager.putInstanceContextSwitchNotification(context, it.task, it.instance)
                    notificationViewModel.put(it.task, it.instance, type = "context").await()
                }

                is SchedulerOps.Notification.DeleteInstanceNotifications -> {
                    notificationManager.deleteInstanceNotifications(it.task, it.instance)
                    notificationViewModel.delete(it.task, it.instance).await()
                }
            }
        }

        return this
    }

    data class ExistingNotifications(
        val execution: SchedulerOps.Notification.PutInstanceExecutionNotification?,
        val context: SchedulerOps.Notification.PutInstanceContextSwitchNotification?,
        val deletion: SchedulerOps.Notification.DeleteInstanceNotifications?
    ) {
        fun updated(newExecution: SchedulerOps.Notification.PutInstanceExecutionNotification): ExistingNotifications =
            copy(
                execution = newExecution,
                deletion = null
            )

        fun updated(newContext: SchedulerOps.Notification.PutInstanceContextSwitchNotification): ExistingNotifications =
            copy(
                context = newContext,
                deletion = null
            )

        fun updated(newDeletion: SchedulerOps.Notification.DeleteInstanceNotifications): ExistingNotifications =
            copy(
                context = null,
                execution = null,
                deletion = newDeletion
            )

        fun toList(): List<SchedulerOps.Notification> = listOfNotNull(execution, context, deletion)

        companion object {
            operator fun invoke(): ExistingNotifications = ExistingNotifications(
                context = null,
                execution = null,
                deletion = null
            )
        }
    }

    companion object {
        operator fun invoke(): NotificationQueue = NotificationQueue(LinkedList())
    }
}
