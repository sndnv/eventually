package eventually.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.util.UUID

data class Task(
    val id: UUID,
    val name: String,
    val description: String,
    val goal: String,
    val schedule: Schedule,
    val priority: Priority,
    val contextSwitch: Duration
) {
    enum class Priority {
        Low,
        Medium,
        High
    }

    sealed class Schedule {
        abstract fun next(after: Instant): Instant

        data class Once(val instant: Instant) : Schedule() {
            override fun next(after: Instant): Instant = instant
        }

        data class Repeating(val time: LocalTime, val every: Duration) : Schedule() {
            override fun next(after: Instant): Instant {
                return scheduleInstants(init = after, step = every).find { it.isAfter(after) }!!
            }

            private fun scheduleInstants(init: Instant, step: Duration): Sequence<Instant> = sequence {
                var lastInstant = init
                    .atZone(ZoneOffset.UTC)
                    .with(ChronoField.HOUR_OF_DAY, time.hour.toLong())
                    .with(ChronoField.MINUTE_OF_HOUR, time.minute.toLong())
                    .with(ChronoField.SECOND_OF_MINUTE, time.second.toLong())
                    .with(ChronoField.NANO_OF_SECOND, time.nano.toLong())
                    .toInstant()

                while (true) {
                    yield(lastInstant)
                    lastInstant += step
                }
            }
        }
    }
}
