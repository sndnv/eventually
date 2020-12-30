package eventually.client.persistence.tasks

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import eventually.client.persistence.Converters.Companion.asEntity
import eventually.client.persistence.Converters.Companion.asTask
import eventually.core.model.Task

class TaskRepository(private val dao: TaskEntityDao) {
    val tasks: LiveData<List<Task>> = dao.get().map { entities -> entities.map { it.asTask() } }

    suspend fun put(task: Task): Long = dao.put(task.asEntity())

    suspend fun delete(task: Int) = dao.delete(task)

    companion object {
        operator fun invoke(context: Context): TaskRepository {
            val dao = TaskEntityDatabase.getInstance(context).dao()
            return TaskRepository(dao)
        }
    }
}
