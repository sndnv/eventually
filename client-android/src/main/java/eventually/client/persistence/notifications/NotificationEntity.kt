package eventually.client.persistence.notifications

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val task: Int,
    val instance: UUID,
    val type: String,
    val hash: Int
)
