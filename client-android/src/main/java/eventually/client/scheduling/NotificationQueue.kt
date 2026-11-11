package eventually.client.scheduling

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import eventually.client.persistence.notifications.NotificationRepository
import eventually.client.scheduling.AlarmManagerExtensions.deleteAlarmTimeout
import eventually.client.scheduling.AlarmManagerExtensions.deleteEvaluationAlarm
import eventually.client.scheduling.AlarmManagerExtensions.putAlarmTimeout
import eventually.client.scheduling.AlarmManagerExtensions.putEvaluationAlarm
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceContextSwitchNotification
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionAlarm
import eventually.client.scheduling.NotificationManagerExtensions.putInstanceExecutionNotification
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getAlarmTimeout
import eventually.core.scheduling.SchedulerOps
import java.time.Instant
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
        notificationRepository: NotificationRepository
    ): NotificationQueue {
        val updated = LinkedList<SchedulerOps.Notification>()
        val instances = mutableMapOf<UUID, ExistingNotifications>()

        notifications.forEach {
            when (it) {
                is SchedulerOps.Notification.PutEvaluationAlarm -> updated.offer(it)

                is SchedulerOps.Notification.DeleteEvaluationAlarm -> updated.offer(it)

                is SchedulerOps.Notification.PutInstanceExecutionNotification -> {
                    val existing = notificationRepository.get(it.task.id, it.instance.id, type = "execution")

                    if (existing == null || existing.hash != it.instance.hashCode()) {
                        instances[it.instance.id] = (instances[it.instance.id] ?: ExistingNotifications()).updated(it)
                    }
                }

                is SchedulerOps.Notification.PutInstanceContextSwitchNotification -> {
                    val existing = notificationRepository.get(it.task.id, it.instance.id, type = "context")

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
        alarmManager: AlarmManager,
        notificationManager: NotificationManager,
        notificationRepository: NotificationRepository
    ): NotificationQueue {
        notifications.forEach {
            when (it) {
                is SchedulerOps.Notification.PutEvaluationAlarm -> {
                    alarmManager.putEvaluationAlarm(context, it.instant)
                }

                is SchedulerOps.Notification.DeleteEvaluationAlarm -> {
                    alarmManager.deleteEvaluationAlarm(context)
                }

                is SchedulerOps.Notification.PutInstanceExecutionNotification -> {
                    if (it.task.executionAlarm) {
                        val timeout = context.defaultPreferences().getAlarmTimeout()
                        notificationManager.putInstanceExecutionAlarm(context, it.task, it.instance, timeout)
                        alarmManager.putAlarmTimeout(context, it.task.id, it.instance.id, Instant.now().plus(timeout))
                    } else {
                        notificationManager.putInstanceExecutionNotification(context, it.task, it.instance)
                    }
                    notificationRepository.put(it.task, it.instance, type = "execution")
                }

                is SchedulerOps.Notification.PutInstanceContextSwitchNotification -> {
                    notificationManager.putInstanceContextSwitchNotification(context, it.task, it.instance)
                    notificationRepository.put(it.task, it.instance, type = "context")
                }

                is SchedulerOps.Notification.DeleteInstanceNotifications -> {
                    notificationManager.deleteInstanceNotifications(it.task, it.instance)
                    alarmManager.deleteAlarmTimeout(context, it.task, it.instance)
                    notificationRepository.delete(it.task, it.instance)
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
