package eventually.test.client.persistence.tasks

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.tasks.TaskEntityDatabase
import eventually.client.persistence.tasks.TaskViewModel
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.test.client.await
import eventually.test.client.eventually
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
class TaskViewModelSpec {
    @Test
    fun createTasks() {
        withModel { model ->
            assertThat(model.tasks.await(), equalTo(emptyList()))

            val taskId = runBlocking { model.put(task).await() }

            eventually {
                assertThat(model.tasks.await(), equalTo(listOf(task.copy(id = taskId.toInt()))))
            }
        }
    }

    @Test
    fun updateTasks() {
        withModel { model ->
            assertThat(model.tasks.await(), equalTo(emptyList()))

            val taskId = runBlocking { model.put(task).await() }

            eventually {
                assertThat(model.tasks.await(), equalTo(listOf(task.copy(id = taskId.toInt()))))
            }

            val updatedTask = task.copy(id = taskId.toInt(), name = "updated-name")
            runBlocking { model.put(updatedTask) }

            eventually {
                assertThat(model.tasks.await(), equalTo(listOf(updatedTask)))
            }
        }
    }

    @Test
    fun deleteTasks() {
        withModel { model ->
            assertThat(model.tasks.await(), equalTo(emptyList()))

            val taskId = runBlocking { model.put(task).await() }

            eventually {
                assertThat(model.tasks.await(), equalTo(listOf(task.copy(id = taskId.toInt()))))
            }

            runBlocking { model.delete(taskId.toInt()) }

            eventually {
                assertThat(model.tasks.await(), equalTo(emptyList()))
            }
        }
    }

    @Test
    fun retrieveUniqueTaskGoals() {
        withModel { model ->
            assertThat(model.tasks.await(), equalTo(emptyList()))

            runBlocking {
                model.put(task.copy(goal = "goal-1"))
                model.put(task.copy(goal = "goal-2"))
                model.put(task.copy(goal = "goal-3"))
                model.put(task.copy(goal = "goal-2"))
                model.put(task.copy(goal = "goal-3"))
            }

            assertThat(model.tasks.await().size, equalTo(5))
            assertThat(model.goals.await().sorted(), equalTo(listOf("goal-1", "goal-2", "goal-3")))
        }
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
            every = Duration.ofMinutes(20).toInterval()
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true
    )

    private fun withModel(f: (model: TaskViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = TaskViewModel(application)
        val db = TaskEntityDatabase.getInstance(application)

        try {
            db.clearAllTables()
            f(model)
        } finally {
            db.clearAllTables()
        }
    }
}
