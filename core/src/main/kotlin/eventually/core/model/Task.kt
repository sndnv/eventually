package eventually.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.time.temporal.TemporalUnit

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
            val every: Interval,
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

            private fun scheduleInstants(step: Interval): Sequence<Instant> = sequence {
                val zone = ZoneId.systemDefault()
                var lastInstant = start

                while (true) {
                    yield(lastInstant)
                    lastInstant = (lastInstant.atZone(zone).plus(step.amount())).toInstant()
                }
            }

            sealed class Interval {
                abstract fun amount(): TemporalAmount

                data class DurationInterval(val value: Duration) : Interval() {
                    override fun amount(): TemporalAmount = value
                }

                data class PeriodInterval(val value: Period) : Interval() {
                    init {
                        val daysSet = value.days > 0 && value.months == 0 && value.years == 0
                        val monthsSet = value.months > 0 && value.days == 0 && value.years == 0
                        val yearsSet = value.years > 0 && value.days == 0 && value.months == 0

                        require(daysSet || monthsSet || yearsSet) {
                            "Unexpected period found - " +
                                    "with days: [${value.days > 0}], " +
                                    "with months: [${value.months > 0}], " +
                                    "with years: [${value.years > 0}]; " +
                                    "setting only either days, months or years is allowed"
                        }
                    }

                    override fun amount(): TemporalAmount = value
                }

                companion object {
                    fun of(amount: Long, unit: TemporalUnit): Interval =
                        when (unit) {
                            ChronoUnit.YEARS -> PeriodInterval(Period.ofYears(amount.toInt()))
                            ChronoUnit.MONTHS -> PeriodInterval(Period.ofMonths(amount.toInt()))
                            ChronoUnit.DAYS -> PeriodInterval(Period.ofDays(amount.toInt()))
                            else -> DurationInterval(Duration.of(amount, unit))
                        }

                    fun Duration.toInterval(): Interval = DurationInterval(value = this)

                    fun Period.toInterval(): Interval = PeriodInterval(value = this)
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

                operator fun invoke(start: Instant, every: Interval): Repeating =
                    Repeating(
                        start = start,
                        every = every,
                        days = DefaultDays
                    )
            }
        }
    }
}
