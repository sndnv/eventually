package eventually.test.client.persistence.notifications

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.notifications.NotificationEntityDao
import eventually.client.persistence.notifications.NotificationEntityDatabase
import eventually.test.client.await
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationEntityDaoSpec {
    @Test
    fun createNotificationEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun updateNotificationEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))

            val updatedEntity = entity.copy(type = "execution")
            runBlocking { dao.put(updatedEntity) }
            assertThat(dao.get().await(), equalTo(listOf(updatedEntity)))
        }
    }

    @Test
    fun retrieveNotificationEntitiesBasedOnQuery() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))

            val retrieved = runBlocking { dao.get(entity.task, entity.instance, entity.type) }
            assertThat(retrieved, equalTo(entity))

            val missing = runBlocking { dao.get(entity.task, entity.instance, "execution") }
            assertThat(missing, equalTo(null))
        }
    }

    @Test
    fun deleteNotificationEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))

            runBlocking { dao.delete(entity.id) }
            assertThat(dao.get().await(), equalTo(emptyList()))
        }
    }

    @Test
    fun deleteNotificationEntitiesBasedOnQuery() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            val entity2 = entity.copy(id=2, instance = UUID.randomUUID())
            val entity3 = entity.copy(id=3, instance = UUID.randomUUID())
            val entity4 = entity.copy(id=4, instance = UUID.randomUUID())

            runBlocking {
                dao.put(entity)
                dao.put(entity2)
                dao.put(entity3)
                dao.put(entity4)
            }
            assertThat(dao.get().await(), equalTo(listOf(entity, entity2, entity3, entity4)))

            runBlocking { dao.delete(entity.task, entity.instance) }
            assertThat(dao.get().await(), equalTo(listOf(entity2, entity3, entity4)))
        }
    }

    private val entity: NotificationEntity = NotificationEntity(
        id = 1,
        task = 42,
        instance = UUID.randomUUID(),
        type = "context",
        hash = 2
    )

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun withDao(f: (dao: NotificationEntityDao) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NotificationEntityDatabase::class.java).build()

        try {
            f(db.dao())
        } finally {
            db.close()
        }
    }
}