package eventually.client.persistence.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: NotificationRepository = NotificationRepository(application)

    val notifications: LiveData<List<NotificationEntity>> = repo.notifications

    fun put(task: Task, instance: TaskInstance, type: String): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(task, instance, type))
        }
        return response
    }

    fun put(notification: NotificationEntity): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(notification))
        }
        return response
    }

    fun get(task: Int, instance: UUID, type: String): CompletableDeferred<NotificationEntity?> {
        val response = CompletableDeferred<NotificationEntity?>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.get(task, instance, type))
        }
        return response
    }

    fun delete(id: Int): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            response.complete(Unit)
        }
        return response
    }

    fun delete(task: Int, instance: UUID): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(task, instance)
            response.complete(Unit)
        }
        return response
    }
}
