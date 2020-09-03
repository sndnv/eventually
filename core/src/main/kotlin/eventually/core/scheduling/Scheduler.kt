package eventually.core.scheduling

import eventually.core.model.Task
import java.time.Duration
import java.util.UUID

interface Scheduler {
    fun put(task: Task)
    fun delete(task: UUID)
    fun dismiss(task: UUID, instance: UUID)
    fun postpone(task: UUID, instance: UUID, by: Duration)
}
