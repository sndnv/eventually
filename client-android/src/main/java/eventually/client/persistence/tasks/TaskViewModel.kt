package eventually.client.persistence.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import eventually.core.model.Task
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repo: TaskRepository,
) : ViewModel() {
    val tasks: LiveData<List<Task>> = repo.tasks

    suspend fun goals(): List<String> = repo.goals()

    suspend fun put(task: Task): Long = repo.put(task)

    suspend fun delete(task: Int) = repo.delete(task)
}
