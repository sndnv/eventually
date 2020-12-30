package eventually.client.persistence.schedules

import androidx.room.Entity
import androidx.room.PrimaryKey
import eventually.core.model.TaskInstance
import java.time.Instant
import java.util.UUID

@Entity(tableName = "schedules")
data class TaskScheduleEntity(
    @PrimaryKey()
    val task: Int,
    val instances: Map<UUID, TaskInstance>,
    val dismissed: List<Instant>
)
