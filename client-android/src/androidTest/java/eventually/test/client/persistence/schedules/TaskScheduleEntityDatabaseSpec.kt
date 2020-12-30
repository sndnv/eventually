package eventually.test.client.persistence.schedules

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.schedules.TaskScheduleEntity
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskScheduleEntityDatabaseSpec {
    @Test
    fun initializeItself() {
        withDatabase { db ->
            val dao = db.dao()

            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

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

    private fun withDatabase(f: (db: TaskScheduleEntityDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = "${UUID.randomUUID()}.db"
        val db = TaskScheduleEntityDatabase.getInstance(context, database)

        try {
            db.clearAllTables()
            f(db)
        } finally {
            db.clearAllTables()
            db.close()
        }
    }
}
