package eventually.test.client.persistence.tasks

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.tasks.TaskEntity
import eventually.client.persistence.tasks.TaskEntityDao
import eventually.client.persistence.tasks.TaskEntityDatabase
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
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

@RunWith(AndroidJUnit4::class)
class TaskEntityDaoSpec {
    @Test
    fun createTaskEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            val taskId = runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity.copy(id = taskId.toInt()))))
        }
    }

    @Test
    fun updateTaskEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            val taskId = runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity.copy(id = taskId.toInt()))))

            val updatedEntity = entity.copy(id = taskId.toInt(), name = "updated-name")
            runBlocking { dao.put(updatedEntity) }
            assertThat(dao.get().await(), equalTo(listOf(updatedEntity)))
        }
    }

    @Test
    fun deleteTaskEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            val taskId = runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity.copy(id = taskId.toInt()))))

            runBlocking { dao.delete(taskId.toInt()) }
            assertThat(dao.get().await(), equalTo(emptyList()))
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val entity = TaskEntity(
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Repeating(
            start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
            every = Duration.ofMinutes(20).toInterval()
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true
    )

    private fun withDao(f: (dao: TaskEntityDao) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TaskEntityDatabase::class.java).build()

        try {
            f(db.dao())
        } finally {
            db.close()
        }
    }
}
