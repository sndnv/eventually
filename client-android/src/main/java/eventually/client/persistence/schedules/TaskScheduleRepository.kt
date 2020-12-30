package eventually.client.persistence.schedules

import android.content.Context
import androidx.lifecycle.LiveData
import eventually.client.persistence.Converters.Companion.asEntity
import eventually.core.model.TaskSchedule

class TaskScheduleRepository(private val dao: TaskScheduleEntityDao) {
    val schedules: LiveData<List<TaskScheduleEntity>> = dao.get()

    suspend fun put(schedule: TaskSchedule) = dao.put(schedule.asEntity())

    suspend fun delete(task: Int) = dao.delete(task)

    companion object {
        operator fun invoke(context: Context): TaskScheduleRepository {
            val dao = TaskScheduleEntityDatabase.getInstance(context).dao()
            return TaskScheduleRepository(dao)
        }
    }
}
