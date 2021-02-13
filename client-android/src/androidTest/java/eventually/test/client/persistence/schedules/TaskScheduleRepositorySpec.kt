package eventually.test.client.persistence.schedules

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.schedules.TaskScheduleEntityDao
import eventually.client.persistence.schedules.TaskScheduleRepository
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.test.client.await
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
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class TaskScheduleRepositorySpec {
    @Test
    fun createSchedules() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(schedule) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity)))
    }

    @Test
    fun createSchedulesFromExistingEntity() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity)))
    }

    @Test
    fun updateSchedules() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(schedule) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity)))

        val updatedSchedule = schedule.copy(dismissed = emptyList())
        runBlocking { repo.put(updatedSchedule) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.copy(dismissed = emptyList()))))
    }

    @Test
    fun deleteSchedules() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(schedule) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity)))

        runBlocking { repo.delete(schedule.task.id) }
        assertThat(repo.schedules.await(), equalTo(emptyList()))
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

    private fun createRepo(): TaskScheduleRepository {
        val dao = object : TaskScheduleEntityDao {
            val entities = ConcurrentHashMap<Int, TaskScheduleEntity>()
            val data = MutableLiveData<List<TaskScheduleEntity>>(emptyList())

            override fun get(): LiveData<List<TaskScheduleEntity>> = data

            override suspend fun put(entity: TaskScheduleEntity) {
                entities[entity.task] = entity
                data.value = entities.toList().map { it.second }
            }

            override suspend fun delete(task: Int) {
                entities -= task
                data.value = entities.toList().map { it.second }
            }
        }

        return TaskScheduleRepository(dao)
    }
}
