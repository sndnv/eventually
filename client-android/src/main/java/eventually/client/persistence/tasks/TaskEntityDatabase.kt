package eventually.client.persistence.tasks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eventually.client.persistence.Converters
import org.jetbrains.annotations.TestOnly

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TaskEntityDatabase : RoomDatabase() {
    abstract fun dao(): TaskEntityDao

    companion object {
        private const val DefaultDatabase: String = "tasks.db"

        @Volatile
        private var INSTANCE: TaskEntityDatabase? = null

        fun getInstance(context: Context): TaskEntityDatabase =
            getInstance(context, DefaultDatabase)

        fun getInstance(context: Context, database: String): TaskEntityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, database).also { INSTANCE = it }
            }

        @TestOnly
        fun setInstance(instance: TaskEntityDatabase): Unit =
            synchronized(this) {
                require(INSTANCE == null) { "TaskEntityDatabase instance already exists" }
                INSTANCE = instance
            }

        private fun build(context: Context, database: String) =
            Room.databaseBuilder(
                context.applicationContext,
                TaskEntityDatabase::class.java,
                database
            ).build()
    }
}
