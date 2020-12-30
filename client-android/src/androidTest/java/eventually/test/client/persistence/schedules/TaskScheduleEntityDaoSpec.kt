package eventually.test.client.persistence.schedules

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.schedules.TaskScheduleEntityDao
import eventually.client.persistence.schedules.TaskScheduleEntityDatabase
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
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class TaskScheduleEntityDaoSpec {
    @Test
    fun createScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun updateScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))

            val updatedEntity = entity.copy(dismissed = emptyList())
            runBlocking { dao.put(updatedEntity) }
            assertThat(dao.get().await(), equalTo(listOf(updatedEntity)))
        }
    }

    @Test
    fun deleteScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))

            runBlocking { dao.delete(entity.task) }
            assertThat(dao.get().await(), equalTo(emptyList()))
        }
    }

    private val instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val instance1 = TaskInstance(instant = instant)
    private val instance2 = TaskInstance(instant = instant.plusSeconds(42)).copy(postponed = Duration.ofMinutes(5))
    private val instance3 = TaskInstance(instant = instant.minusSeconds(42))

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

    private fun withDao(f: (dao: TaskScheduleEntityDao) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TaskScheduleEntityDatabase::class.java).build()

        try {
            f(db.dao())
        } finally {
            db.close()
        }
    }
}
