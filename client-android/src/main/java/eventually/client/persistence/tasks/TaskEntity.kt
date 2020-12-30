package eventually.client.persistence.tasks

import androidx.room.Entity
import androidx.room.PrimaryKey
import eventually.core.model.Task
import java.time.Duration

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val goal: String,
    val schedule: Task.Schedule,
    val contextSwitch: Duration,
    val isActive: Boolean
)
