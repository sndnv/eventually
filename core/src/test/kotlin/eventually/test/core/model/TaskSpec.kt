package eventually.test.core.model

import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class TaskSpec : WordSpec({
    "A Task" should {
        "support scheduling single-execution events" {
            val now = Instant.now()
            val within = Duration.ofMinutes(15)

            val expected = now.plus(Duration.ofSeconds(42))
            val schedule = Task.Schedule.Once(instant = expected)

            schedule.next(after = now, within = within) shouldBe (listOf(expected))
        }

        "support scheduling repeating events" {
            fun verify(hour: Int, minute: Int, duration: Long) {
                val now = Instant.now().truncatedTo(ChronoUnit.MINUTES)
                val within = Duration.ofMinutes(5)

                val originalTime = LocalTime.of(hour, minute)
                val originalSchedule = now.truncatedTo(ChronoUnit.DAYS).plusSeconds(
                    originalTime.toSecondOfDay().toLong()
                )
                val repetitionDuration = Duration.ofMinutes(duration)

                val events = abs(Duration.between(now, originalSchedule).toMillis() / repetitionDuration.toMillis())

                val expectedNext = originalSchedule.plusSeconds(repetitionDuration.seconds * (events + 1))

                val futureSchedule = Task.Schedule.Repeating(
                    start = originalTime.atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                    every = repetitionDuration.toInterval(),
                    days = Task.Schedule.Repeating.DefaultDays
                )

                if (originalSchedule.isAfter(now)) {
                    val next = futureSchedule.next(after = now, within = within)

                    next.first() shouldBe (originalSchedule)
                    next.withIndex().forEach { (i, instant) ->
                        instant shouldBe originalSchedule.plusSeconds(repetitionDuration.seconds * i)
                    }
                } else {
                    val next = futureSchedule.next(after = now, within = within)

                    next.first() shouldBe (expectedNext)
                    next.withIndex().forEach { (i, instant) ->
                        instant shouldBe expectedNext.plusSeconds(repetitionDuration.seconds * i)
                    }
                }
            }

            for (hour in 0..23) {
                for (minute in 0..59) {
                    for (duration in 1..120L) {
                        withClue("Initial schedule at [$hour:$minute] with duration of [$duration] minutes") {
                            verify(hour = hour, minute = minute, duration = duration)
                        }
                    }
                }
            }
        }

        "support scheduling repeating events on specific days only" {
            val today = ZonedDateTime.now()
            val tomorrow = today.plusDays(1)

            val within = Duration.ofDays(7)

            val schedule = Task.Schedule.Repeating(
                start = today.toInstant(),
                every = Period.ofDays(1).toInterval(),
                days = setOf(
                    today.dayOfWeek,
                    tomorrow.dayOfWeek
                )
            )

            val expected = 2

            val next = schedule.next(after = schedule.start, within = within)

            next.size shouldBe (expected)
            next.first() shouldBe (tomorrow.toInstant())
            next.last() shouldBe (today.plusWeeks(1).toInstant())
        }

        "support scheduling repeating events with multiple interval unit types" {
            val now = Instant.now()

            fun nextInstant(forInterval: Task.Schedule.Repeating.Interval) =
                Task.Schedule.Repeating(start = now, every = forInterval)
                    .next(now, within = Duration.ofMinutes(5)).first()

            nextInstant(forInterval = Duration.ofSeconds(1).toInterval()) shouldBe (now.plus(1, ChronoUnit.SECONDS))
            nextInstant(forInterval = Duration.ofMinutes(2).toInterval()) shouldBe (now.plus(2, ChronoUnit.MINUTES))
            nextInstant(forInterval = Duration.ofHours(3).toInterval()) shouldBe (now.plus(3, ChronoUnit.HOURS))
            nextInstant(forInterval = Period.ofDays(4).toInterval()) shouldBe (now.plus(4, ChronoUnit.DAYS))
            nextInstant(forInterval = Period.ofMonths(5).toInterval()) shouldBe (now.atZone(ZoneId.systemDefault())
                .plus(5, ChronoUnit.MONTHS).toInstant())
            nextInstant(forInterval = Period.ofYears(6).toInterval()) shouldBe (now.atZone(ZoneId.systemDefault())
                .plus(6, ChronoUnit.YEARS).toInstant())
        }

        "support duration-based intervals for repeating schedules" {
            val duration = Duration.ofSeconds(42)
            val scheduleInterval = duration.toInterval()

            (scheduleInterval is Task.Schedule.Repeating.Interval.DurationInterval) shouldBe (true)
            scheduleInterval.amount() shouldBe (duration)
        }

        "support period-based intervals for repeating schedules" {
            val period = Period.ofYears(42)
            val scheduleInterval = period.toInterval()

            (scheduleInterval is Task.Schedule.Repeating.Interval.PeriodInterval) shouldBe (true)
            scheduleInterval.amount() shouldBe (period)

            val yearsWithMonths = shouldThrow<IllegalArgumentException> {
                Period.of(1, 2, 0).toInterval()
            }

            val yearsWithMonthsAndDays = shouldThrow<IllegalArgumentException> {
                Period.of(1, 2, 3).toInterval()
            }

            yearsWithMonths.message shouldContain ("with days: [false], with months: [true], with years: [true]")
            yearsWithMonthsAndDays.message shouldContain ("with days: [true], with months: [true], with years: [true]")
        }

        "support creating intervals for repeating schedules" {
            Task.Schedule.Repeating.Interval.of(1, ChronoUnit.YEARS).amount() shouldBe (Period.ofYears(1))
            Task.Schedule.Repeating.Interval.of(2, ChronoUnit.MONTHS).amount() shouldBe (Period.ofMonths(2))
            Task.Schedule.Repeating.Interval.of(3, ChronoUnit.DAYS).amount() shouldBe (Period.ofDays(3))
            Task.Schedule.Repeating.Interval.of(4, ChronoUnit.HOURS).amount() shouldBe (Duration.ofHours(4))
            Task.Schedule.Repeating.Interval.of(5, ChronoUnit.MINUTES).amount() shouldBe (Duration.ofMinutes(5))
            Task.Schedule.Repeating.Interval.of(6, ChronoUnit.SECONDS).amount() shouldBe (Duration.ofSeconds(6))
            Task.Schedule.Repeating.Interval.of(7, ChronoUnit.MILLIS).amount() shouldBe (Duration.ofMillis(7))
        }
    }
})
