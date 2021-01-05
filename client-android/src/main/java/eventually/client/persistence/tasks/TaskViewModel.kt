package eventually.client.persistence.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import eventually.core.model.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: TaskRepository = TaskRepository(application)

    val tasks: LiveData<List<Task>> = repo.tasks

    val goals: LiveData<List<String>> = repo.goals

    fun put(task: Task): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(task))
        }
        return response
    }

    fun delete(task: Int): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(task)
            response.complete(Unit)
        }
        return response
    }
}
