package eventually.client.persistence.schedules

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import eventually.core.model.TaskSchedule
import javax.inject.Inject

@HiltViewModel
class TaskScheduleViewModel @Inject constructor(
    private val repo: TaskScheduleRepository,
) : ViewModel() {
    val schedules: LiveData<List<TaskScheduleEntity>> = repo.schedules

    suspend fun put(schedule: TaskSchedule) = repo.put(schedule)

    suspend fun put(schedule: TaskScheduleEntity) = repo.put(schedule)

    suspend fun delete(task: Int) = repo.delete(task)
}
