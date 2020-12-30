package eventually.client.persistence.schedules

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eventually.client.persistence.Converters
import org.jetbrains.annotations.TestOnly

@Database(entities = [TaskScheduleEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TaskScheduleEntityDatabase : RoomDatabase() {
    abstract fun dao(): TaskScheduleEntityDao

    companion object {
        private const val DefaultDatabase: String = "schedules.db"

        @Volatile
        private var INSTANCE: TaskScheduleEntityDatabase? = null

        fun getInstance(context: Context) =
            getInstance(context, DefaultDatabase)

        fun getInstance(context: Context, database: String): TaskScheduleEntityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, database).also { INSTANCE = it }
            }

        @TestOnly
        fun setInstance(instance: TaskScheduleEntityDatabase): Unit =
            synchronized(this) {
                require(INSTANCE == null) { "TaskScheduleEntityDatabase instance already exists" }
                INSTANCE = instance
            }

        private fun build(context: Context, database: String) =
            Room.databaseBuilder(
                context.applicationContext,
                TaskScheduleEntityDatabase::class.java,
                database
            ).build()
    }
}
