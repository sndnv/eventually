package eventually.test.client.persistence.notifications

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.notifications.NotificationEntity
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
class NotificationEntityDatabaseSpec {
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

    private val entity: NotificationEntity = NotificationEntity(
        id = 1,
        task = 42,
        instance = UUID.randomUUID(),
        type = "context",
        hash = 2
    )

    private fun withDatabase(f: (db: NotificationEntityDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = "${UUID.randomUUID()}.db"
        val db = NotificationEntityDatabase.getInstance(context, database)

        try {
            db.clearAllTables()
            f(db)
        } finally {
            db.clearAllTables()
            db.close()
        }
    }
}