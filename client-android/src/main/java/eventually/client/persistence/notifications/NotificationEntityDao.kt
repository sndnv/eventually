package eventually.client.persistence.notifications

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.UUID

@Dao
interface NotificationEntityDao {
    @Query("SELECT * FROM notifications")
    fun get(): LiveData<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE task == :task AND instance == :instance AND type == :type LIMIT 1")
    suspend fun get(task: Int, instance: UUID, type: String): NotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: NotificationEntity): Long

    @Query("DELETE FROM notifications WHERE id == :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM notifications WHERE task == :task AND instance == :instance")
    suspend fun delete(task: Int, instance: UUID)
}
