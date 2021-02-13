package eventually.client.persistence

import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.core.model.Task

data class DataExport(
    val tasks: List<Task>,
    val schedules: List<TaskScheduleEntity>,
    val notifications: List<NotificationEntity>
)
