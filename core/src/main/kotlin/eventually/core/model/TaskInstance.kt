package eventually.core.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class TaskInstance(
    val id: UUID,
    val instant: Instant,
    val postponed: Duration?
) {
    fun execution(): Instant = instant.plus(postponed ?: Duration.ZERO)

    fun postponed(by: Duration): TaskInstance {
        val now = Instant.now()

        return if (execution().isBefore(now)) {
            copy(
                instant = Instant.now(),
                postponed = by
            )
        } else {
            copy(postponed = (postponed ?: Duration.ZERO) + by)
        }
    }

    companion object {
        operator fun invoke(instant: Instant): TaskInstance = TaskInstance(
            id = UUID.randomUUID(),
            instant = instant,
            postponed = null
        )
    }
}
