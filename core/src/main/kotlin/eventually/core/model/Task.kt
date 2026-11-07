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
    val isActive: Boolean,
    val color: Int?
) {
    companion object {
        val MaxDuration: Duration = ChronoUnit.YEARS.duration.multipliedBy(100)
    }

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
            init {
                require(days.isNotEmpty()) {
                    "At least one day of the week is required for a repeating schedule"
                }
            }

            override fun next(after: Instant, within: Duration): List<Instant> {
                val zone = ZoneId.systemDefault()
                val withinEnd = after.plus(within)

                val first = firstInstantAfter(after, zone) ?: return emptyList()
                val horizon = runCatching { first.atZone(zone).plus(SearchHorizon).toInstant() }.getOrNull()

                val matching = generateSequence(first) { it.nextInstant(zone) }
                    .withIndex()
                    .takeWhile { (index, instant) ->
                        index < MinSearchCandidates || (horizon != null && !instant.isAfter(horizon))
                    }
                    .map { it.value }
                    .filter { days.contains(it.atZone(zone).dayOfWeek) }
                    .iterator()

                val found = if (matching.hasNext()) matching.next() else null

                return when {
                    found == null -> emptyList()
                    found.isAfter(withinEnd) -> listOf(found)
                    else -> listOf(found) + matching.asSequence().takeWhile { !it.isAfter(withinEnd) }
                }
            }

            private fun firstInstantAfter(after: Instant, zone: ZoneId): Instant? {
                val step = every

                val skipped = when {
                    step is Interval.DurationInterval && (step.value.isZero || step.value.isNegative) -> return null
                    step is Interval.DurationInterval && step.value.nano == 0 && !start.isAfter(after) ->
                        (after.epochSecond - start.epochSecond) / step.value.seconds
                    else -> 0L
                }

                val skippedStart = when (step) {
                    is Interval.DurationInterval -> start.plus(step.value.multipliedBy(skipped))
                    is Interval.PeriodInterval -> start
                }

                return generateSequence(skippedStart) { it.nextInstant(zone) }.firstOrNull { it.isAfter(after) }
            }

            private fun Instant.nextInstant(zone: ZoneId): Instant? =
                runCatching { atZone(zone).plus(every.amount()).toInstant() }.getOrNull()

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
                private val SearchHorizon: Period = Period.ofYears(10)

                private const val MinSearchCandidates: Int = 4800

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
