package eventually.client.persistence.schedules

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TaskScheduleEntityDao {
    @Query("SELECT * FROM schedules")
    fun get(): LiveData<List<TaskScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TaskScheduleEntity)

    @Query("DELETE FROM schedules WHERE task == :task")
    suspend fun delete(task: Int)
}
