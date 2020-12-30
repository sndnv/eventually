package eventually.client.persistence.tasks

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TaskEntityDao {
    @Query("SELECT * FROM tasks")
    fun get(): LiveData<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TaskEntity): Long

    @Query("DELETE FROM tasks WHERE id == :entity")
    suspend fun delete(entity: Int)
}
