package eventually.core.model

import java.time.Duration
import java.time.Instant

data class Task(
    val id: Int,
    val name: String,
    val description: String,
    val goal: String,
    val schedule: Schedule,
    val contextSwitch: Duration,
    val isActive: Boolean
) {
    sealed class Schedule {
        abstract fun next(after: Instant, within: Duration): List<Instant>

        data class Once(val instant: Instant) : Schedule() {
            override fun next(after: Instant, within: Duration): List<Instant> = listOf(instant)
        }

        data class Repeating(val start: Instant, val every: Duration) : Schedule() {
            override fun next(after: Instant, within: Duration): List<Instant> {
                return scheduleInstants(step = every)
                    .filter { it.isAfter(after) }
                    .take((within.seconds / every.seconds).coerceAtLeast(1L).toInt())
                    .toList()
            }

            private fun scheduleInstants(step: Duration): Sequence<Instant> = sequence {
                var lastInstant = start

                while (true) {
                    yield(lastInstant)
                    lastInstant += step
                }
            }
        }
    }
}
