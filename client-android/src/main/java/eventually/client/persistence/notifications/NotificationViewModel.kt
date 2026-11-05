package eventually.client.persistence.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repo: NotificationRepository,
) : ViewModel() {
    val notifications: LiveData<List<NotificationEntity>> = repo.notifications

    suspend fun put(task: Task, instance: TaskInstance, type: String): Long =
        repo.put(task, instance, type)

    suspend fun put(notification: NotificationEntity): Long = repo.put(notification)

    suspend fun get(task: Int, instance: UUID, type: String): NotificationEntity? =
        repo.get(task, instance, type)

    suspend fun delete(id: Int) = repo.delete(id)

    suspend fun delete(task: Int, instance: UUID) = repo.delete(task, instance)
}
