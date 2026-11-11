package eventually.test.client.persistence.tasks

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eventually.client.persistence.tasks.TaskEntityDatabase
import eventually.core.model.Task
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TaskEntityDatabaseMigrationSpec {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TaskEntityDatabase::class.java
    )

    @Test
    fun migrateFromVersion2To3() {
        helper.createDatabase(DatabaseName, 2).use { db ->
            db.execSQL(
                "INSERT INTO tasks (id, name, description, goal, schedule, contextSwitch, isActive, color) " +
                    "VALUES (42, 'test-task', 'test-description', 'test-goal', '{\"type\":\"once\",\"instant\":42}', 300, 1, 7)"
            )
        }

        helper.runMigrationsAndValidate(DatabaseName, 3, true, TaskEntityDatabase.MIGRATION_2_3).close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room
            .databaseBuilder(context, TaskEntityDatabase::class.java, DatabaseName)
            .addMigrations(TaskEntityDatabase.MIGRATION_1_2, TaskEntityDatabase.MIGRATION_2_3)
            .build()

        try {
            val tasks = runBlocking { database.dao().getAll() }

            assertThat(tasks.size, equalTo(1))
            assertThat(tasks.single().id, equalTo(42))
            assertThat(tasks.single().name, equalTo("test-task"))
            assertThat(tasks.single().schedule, equalTo(Task.Schedule.Once(instant = Instant.ofEpochSecond(42))))
            assertThat(tasks.single().contextSwitch, equalTo(Duration.ofMinutes(5)))
            assertThat(tasks.single().isActive, equalTo(true))
            assertThat(tasks.single().color, equalTo(7))
            assertThat(tasks.single().executionAlarm, equalTo(false))
        } finally {
            database.close()
        }
    }

    companion object {
        private const val DatabaseName: String = "migration-test.db"
    }
}
