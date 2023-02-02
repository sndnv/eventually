package eventually.test.client.persistence.notifications

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.notifications.NotificationEntityDao
import eventually.client.persistence.notifications.NotificationRepository
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class NotificationRepositorySpec {
    @Test
    fun createNotificationEntities() {
        val repo = createRepo()

        assertThat(repo.notifications.await(), equalTo(emptyList()))

        runBlocking { repo.put(task, instance, entity.type) }
        assertThat(repo.notifications.await(), equalTo(listOf(entity)))
    }

    @Test
    fun createNotificationEntitiesFromExistingEntity() {
        val repo = createRepo()

        assertThat(repo.notifications.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.notifications.await(), equalTo(listOf(entity)))
    }

    @Test
    fun retrieveNotificationEntitiesBasedOnQuery() {
        val repo = createRepo()

        assertThat(repo.notifications.await(), equalTo(emptyList()))

        runBlocking { repo.put(task, instance, entity.type) }
        assertThat(repo.notifications.await(), equalTo(listOf(entity)))

        val retrieved = runBlocking { repo.get(entity.task, entity.instance, entity.type) }
        assertThat(retrieved, equalTo(entity))

        val missing = runBlocking { repo.get(entity.task, entity.instance, "execution") }
        assertThat(missing, equalTo(null))
    }

    @Test
    fun deleteNotificationEntities() {
        val repo = createRepo()

        assertThat(repo.notifications.await(), equalTo(emptyList()))

        runBlocking { repo.put(task, instance, entity.type) }
        assertThat(repo.notifications.await(), equalTo(listOf(entity)))

        runBlocking { repo.delete(entity.id) }
        assertThat(repo.notifications.await(), equalTo(emptyList()))
    }

    @Test
    fun deleteNotificationEntitiesBasedOnQuery() {
        val repo = createRepo()

        assertThat(repo.notifications.await(), equalTo(emptyList()))

        val instance2 = instance.copy(id = UUID.randomUUID())
        val instance3 = instance.copy(id = UUID.randomUUID())
        val instance4 = instance.copy(id = UUID.randomUUID())

        val entity2 = entity.copy(id = 2, instance = instance2.id, hash = instance2.hashCode())
        val entity3 = entity.copy(id = 3, instance = instance3.id, hash = instance3.hashCode())
        val entity4 = entity.copy(id = 4, instance = instance4.id, hash = instance4.hashCode())

        runBlocking {
            repo.put(task, instance, entity.type)
            repo.put(task, instance2, entity.type)
            repo.put(task, instance3, entity.type)
            repo.put(task, instance4, entity.type)
        }
        assertThat(repo.notifications.await(), equalTo(listOf(entity, entity2, entity3, entity4)))

        runBlocking { repo.delete(entity.task, entity.instance) }
        assertThat(repo.notifications.await(), equalTo(listOf(entity2, entity3, entity4)))
    }

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Repeating(
            start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
            every = Duration.ofMinutes(20).toInterval()
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = 1
    )

    private val instance = TaskInstance(instant = Instant.now())

    private val entity = NotificationEntity(
        id = 1,
        task = task.id,
        instance = instance.id,
        type = "context",
        hash = instance.hashCode()
    )

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun createRepo(): NotificationRepository {
        val dao = object : NotificationEntityDao {
            val entities = ConcurrentHashMap<Int, NotificationEntity>()
            val data = MutableLiveData<List<NotificationEntity>>(emptyList())

            override fun get(): LiveData<List<NotificationEntity>> = data

            override suspend fun get(task: Int, instance: UUID, type: String): NotificationEntity? {
                return entities.values.find { it.task == task && it.instance == instance && it.type == type }
            }

            override suspend fun put(entity: NotificationEntity): Long {
                val id = if (entity.id == 0) entities.size + 1 else entities.size + 1
                entities[id] = entity.copy(id = id)
                data.value = entities.toList().map { it.second }
                return id.toLong()
            }

            override suspend fun delete(id: Int) {
                entities -= id
                data.value = entities.toList().map { it.second }
            }

            override suspend fun delete(task: Int, instance: UUID) {
                entities.values.find { it.task == task && it.instance == instance }?.let { notification ->
                    entities -= notification.id
                    data.value = entities.toList().map { it.second }
                }
            }
        }

        return NotificationRepository(dao)
    }
}
