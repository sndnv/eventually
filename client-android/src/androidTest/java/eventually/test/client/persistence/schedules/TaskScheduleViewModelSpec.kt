package eventually.test.client.persistence.schedules

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.schedules.TaskScheduleEntityDatabase
import eventually.client.persistence.schedules.TaskScheduleViewModel
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.test.client.await
import eventually.test.client.eventually
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class TaskScheduleViewModelSpec {
    @Test
    fun createSchedules() {
        withModel { model ->
            assertThat(model.schedules.await(), equalTo(emptyList()))

            runBlocking { model.put(schedule).await() }

            eventually {
                assertThat(model.schedules.await(), equalTo(listOf(entity)))
            }
        }
    }

    @Test
    fun updateSchedules() {
        withModel { model ->
            assertThat(model.schedules.await(), equalTo(emptyList()))

            runBlocking { model.put(schedule).await() }

            eventually {
                assertThat(model.schedules.await(), equalTo(listOf(entity)))
            }

            val updatedSchedule = schedule.copy(dismissed = emptyList())
            runBlocking { model.put(updatedSchedule) }

            eventually {
                assertThat(model.schedules.await(), equalTo(listOf(entity.copy(dismissed = emptyList()))))
            }
        }
    }

    @Test
    fun deleteSchedules() {
        withModel { model ->
            assertThat(model.schedules.await(), equalTo(emptyList()))

            runBlocking { model.put(schedule).await() }

            eventually {
                assertThat(model.schedules.await(), equalTo(listOf(entity)))
            }

            runBlocking { model.delete(schedule.task.id) }

            eventually {
                assertThat(model.schedules.await(), equalTo(emptyList()))
            }
        }
    }

    private val instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val instance1 = TaskInstance(instant = instant)
    private val instance2 = TaskInstance(instant = instant.plusSeconds(42)).copy(postponed = Duration.ofMinutes(5))
    private val instance3 = TaskInstance(instant = instant.minusSeconds(42))

    private val schedule = TaskSchedule(
        task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                every = Duration.ofMinutes(20).toInterval()
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        ),
        instances = mapOf(
            instance1.id to instance1,
            instance2.id to instance2,
            instance3.id to instance3
        ),
        dismissed = listOf(
            instant,
            instant.plusSeconds(21),
            instant.minusSeconds(21)
        )
    )

    private val entity = TaskScheduleEntity(
        task = 42,
        instances = mapOf(
            instance1.id to instance1,
            instance2.id to instance2,
            instance3.id to instance3
        ),
        dismissed = listOf(
            instant,
            instant.plusSeconds(21),
            instant.minusSeconds(21)
        )
    )

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun withModel(f: (model: TaskScheduleViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = TaskScheduleViewModel(application)
        val db = TaskScheduleEntityDatabase.getInstance(application)

        try {
            db.clearAllTables()
            f(model)
        } finally {
            db.clearAllTables()
        }
    }
}
