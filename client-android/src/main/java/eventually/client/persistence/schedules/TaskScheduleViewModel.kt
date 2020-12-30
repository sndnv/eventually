package eventually.client.persistence.schedules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import eventually.core.model.TaskSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: TaskScheduleRepository = TaskScheduleRepository(application)

    val schedules: LiveData<List<TaskScheduleEntity>> = repo.schedules

    fun put(schedule: TaskSchedule): CompletableDeferred<Unit> {
        val response = CompletableDeferred(Unit)
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(schedule))
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
