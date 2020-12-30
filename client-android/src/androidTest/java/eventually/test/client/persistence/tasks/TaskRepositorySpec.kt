package eventually.test.client.persistence.tasks

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.tasks.TaskEntity
import eventually.client.persistence.tasks.TaskEntityDao
import eventually.client.persistence.tasks.TaskRepository
import eventually.core.model.Task
import eventually.test.client.await
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class TaskRepositorySpec {
    @Test
    fun createTasks() {
        val repo = createRepo()

        assertThat(repo.tasks.await(), equalTo(emptyList()))

        val taskId = runBlocking { repo.put(task) }
        assertThat(taskId, equalTo(1L))
        assertThat(repo.tasks.await(), equalTo(listOf(task.copy(id = taskId.toInt()))))
    }

    @Test
    fun updateTasks() {
        val repo = createRepo()

        assertThat(repo.tasks.await(), equalTo(emptyList()))

        val taskId = runBlocking { repo.put(task) }
        assertThat(repo.tasks.await(), equalTo(listOf(task.copy(id = taskId.toInt()))))

        val updatedTask = task.copy(id = taskId.toInt(), name = "updated-name")
        runBlocking { repo.put(updatedTask) }
        assertThat(repo.tasks.await(), equalTo(listOf(updatedTask)))
    }

    @Test
    fun deleteTasks() {
        val repo = createRepo()

        assertThat(repo.tasks.await(), equalTo(emptyList()))

        val taskId = runBlocking { repo.put(task) }
        assertThat(repo.tasks.await(), equalTo(listOf(task.copy(id = taskId.toInt()))))

        runBlocking { repo.delete(taskId.toInt()) }
        assertThat(repo.tasks.await(), equalTo(emptyList()))
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val task = Task(
        id = 0,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Repeating(
            start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
            every = Duration.ofMinutes(20)
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true
    )

    private fun createRepo(): TaskRepository {
        val dao = object : TaskEntityDao {
            val entities = ConcurrentHashMap<Int, TaskEntity>()
            val data = MutableLiveData<List<TaskEntity>>(emptyList())

            override fun get(): LiveData<List<TaskEntity>> = data

            override suspend fun put(entity: TaskEntity): Long {
                val id = entities[entity.id]?.id ?: entities.size + 1
                entities[id] = entity.copy(id = id)
                data.value = entities.toList().map { it.second }
                return id.toLong()
            }

            override suspend fun delete(entity: Int) {
                entities -= entity
                data.value = entities.toList().map { it.second }
            }
        }

        return TaskRepository(dao)
    }
}
