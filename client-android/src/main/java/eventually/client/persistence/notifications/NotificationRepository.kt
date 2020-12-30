package eventually.client.persistence.notifications

import android.content.Context
import androidx.lifecycle.LiveData
import eventually.client.persistence.Converters.Companion.asNotificationEntity
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import java.util.UUID

class NotificationRepository(private val dao: NotificationEntityDao) {
    val notifications: LiveData<List<NotificationEntity>> = dao.get()

    suspend fun put(task: Task, instance: TaskInstance, type: String): Long =
        dao.put((task to instance).asNotificationEntity(type))

    suspend fun get(task: Int, instance: UUID, type: String): NotificationEntity? =
        dao.get(task, instance, type)

    suspend fun delete(id: Int) =
        dao.delete(id)

    suspend fun delete(task: Int, instance: UUID) =
        dao.delete(task, instance)

    companion object {
        operator fun invoke(context: Context): NotificationRepository {
            val dao = NotificationEntityDatabase.getInstance(context).dao()
            return NotificationRepository(dao)
        }
    }
}
