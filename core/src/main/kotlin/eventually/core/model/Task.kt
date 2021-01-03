package eventually.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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

        data class Repeating(
            val start: Instant,
            val every: Duration,
            val days: Set<DayOfWeek>
        ) : Schedule() {
            override fun next(after: Instant, within: Duration): List<Instant> {
                val withinEnd = after.plus(within)

                val instants = scheduleInstants(step = every)
                    .filter { it.isAfter(after) && days.contains(it.atZone(ZoneId.systemDefault()).dayOfWeek) }

                tailrec fun collect(from: Iterator<Instant>, collected: List<Instant>): List<Instant> {
                    val current = from.next()
                    return when {
                        collected.isEmpty() -> {
                            collect(from = from, collected = collected + current)
                        }
                        withinEnd.isBefore(current) -> {
                            collected
                        }
                        else -> {
                            collect(from = from, collected = collected + current)
                        }
                    }
                }

                return collect(from = instants.iterator(), collected = emptyList())
            }

            private fun scheduleInstants(step: Duration): Sequence<Instant> = sequence {
                var lastInstant = start

                while (true) {
                    yield(lastInstant)
                    lastInstant += step
                }
            }

            companion object {
                val DefaultDays: Set<DayOfWeek> = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY
                )

                operator fun invoke(start: Instant, every: Duration): Repeating =
                    Repeating(
                        start = start,
                        every = every,
                        days = DefaultDays
                    )
            }
        }
    }
}
