package eventually.client.persistence.notifications

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import eventually.client.persistence.Converters
import eventually.client.persistence.schedules.TaskScheduleEntityDatabase
import org.jetbrains.annotations.TestOnly

@Database(entities = [NotificationEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NotificationEntityDatabase : RoomDatabase() {
    abstract fun dao(): NotificationEntityDao

    companion object {
        private const val DefaultDatabase: String = "notifications.db"

        @Volatile
        private var INSTANCE: NotificationEntityDatabase? = null

        fun getInstance(context: Context) =
            getInstance(context, DefaultDatabase)

        fun getInstance(context: Context, database: String): NotificationEntityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, database).also { INSTANCE = it }
            }

        @TestOnly
        fun setInstance(instance: NotificationEntityDatabase): Unit =
            synchronized(this) {
                require(INSTANCE == null) { "NotificationEntityDatabase instance already exists" }
                INSTANCE = instance
            }

        private fun build(context: Context, database: String) =
            Room.databaseBuilder(
                context.applicationContext,
                NotificationEntityDatabase::class.java,
                database
            ).build()
    }
}
