package eventually.test.core.model

import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.DayOfWeek
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
import kotlin.time.Duration.Companion.seconds

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

        "return no instants when the selected days are never reached".config(timeout = 5.seconds, blockingTest = true) {
            val monday = LocalDate.of(2026, 11, 9).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()

            listOf(
                Period.ofDays(7).toInterval(),
                Period.ofDays(14).toInterval(),
                Duration.ofDays(7).toInterval()
            ).forEach { every ->
                val schedule = Task.Schedule.Repeating(
                    start = monday,
                    every = every,
                    days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
                )

                schedule.next(after = monday, within = Duration.ofDays(30)) shouldBe (emptyList())
            }
        }

        "find selected days beyond ten years for multi-year intervals" {
            val start = LocalDate.of(2026, 11, 10).atTime(10, 0).atZone(ZoneId.systemDefault())
            val expected = LocalDate.of(2040, 11, 10).atTime(10, 0).atZone(ZoneId.systemDefault())

            expected.dayOfWeek shouldBe (DayOfWeek.SATURDAY)

            val schedule = Task.Schedule.Repeating(
                start = start.toInstant(),
                every = Period.ofYears(2).toInterval(),
                days = setOf(DayOfWeek.SATURDAY)
            )

            val next = schedule.next(after = start.toInstant(), within = Duration.ofDays(1))

            next shouldBe (listOf(expected.toInstant()))
        }

        "return no instants once enough candidates were tried".config(timeout = 5.seconds, blockingTest = true) {
            val start = LocalDate.of(2026, 11, 10).atTime(10, 0).atZone(ZoneId.systemDefault())
            val reached = (1L..4L).map { start.plusYears(100 * it).dayOfWeek }.toSet()

            val schedule = Task.Schedule.Repeating(
                start = start.toInstant(),
                every = Period.ofYears(100).toInterval(),
                days = DayOfWeek.entries.toSet() - reached
            )

            schedule.next(after = start.toInstant(), within = Duration.ofDays(1)) shouldBe (emptyList())
        }

        "stop searching at the end of the supported date range".config(timeout = 5.seconds, blockingTest = true) {
            val start = LocalDate.of(2026, 11, 10).atTime(10, 0).atZone(ZoneId.systemDefault())

            start.dayOfWeek shouldBe (DayOfWeek.TUESDAY)

            listOf(Period.ofYears(1_000_000), Period.ofYears(999_999_999)).forEach { period ->
                val schedule = Task.Schedule.Repeating(
                    start = start.toInstant(),
                    every = period.toInterval(),
                    days = setOf(DayOfWeek.MONDAY)
                )

                schedule.next(after = start.toInstant(), within = Duration.ofDays(1)) shouldBe (emptyList())
            }

            val nearEnd = Period.ofYears(999_997_970)

            val schedule = Task.Schedule.Repeating(
                start = start.toInstant(),
                every = nearEnd.toInterval(),
                days = DayOfWeek.entries.toSet() - start.plus(nearEnd).dayOfWeek
            )

            schedule.next(after = start.toInstant(), within = Duration.ofDays(1)) shouldBe (emptyList())
        }

        "support sub-second and fractional duration intervals" {
            val start = Instant.now().truncatedTo(ChronoUnit.SECONDS)

            val subSecond = Task.Schedule.Repeating(start = start, every = Duration.ofMillis(500).toInterval())
            subSecond.next(after = start, within = Duration.ZERO) shouldBe (listOf(start.plusMillis(500)))

            val fractional = Task.Schedule.Repeating(start = start, every = Duration.ofMillis(1500).toInterval())
            fractional.next(after = start.plusSeconds(100), within = Duration.ZERO) shouldBe (
                listOf(start.plusMillis(100_500))
            )
        }

        "skip ahead when instants have sub-second parts".config(timeout = 5.seconds, blockingTest = true) {
            val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            val past = now.minus(Duration.ofDays(5 * 365))
            val every = Duration.ofSeconds(1).toInterval()

            val later = Task.Schedule.Repeating(start = past.plusMillis(900), every = every)
            later.next(after = now.plusMillis(100), within = Duration.ZERO) shouldBe (listOf(now.plusMillis(900)))

            val earlier = Task.Schedule.Repeating(start = past.plusMillis(100), every = every)
            earlier.next(after = now.plusMillis(900), within = Duration.ZERO) shouldBe (listOf(now.plusMillis(1100)))
        }

        "find selected days within the search limit" {
            val start = LocalDate.of(2026, 11, 10).atTime(10, 0).atZone(ZoneId.systemDefault())
            val expected = LocalDate.of(2031, 11, 10).atTime(10, 0).atZone(ZoneId.systemDefault())

            expected.dayOfWeek shouldBe (DayOfWeek.MONDAY)

            val schedule = Task.Schedule.Repeating(
                start = start.toInstant(),
                every = Period.ofYears(1).toInterval(),
                days = setOf(DayOfWeek.MONDAY)
            )

            val next = schedule.next(after = start.toInstant(), within = Duration.ofDays(1))

            next shouldBe (listOf(expected.toInstant()))
        }

        "skip ahead for long-running duration intervals".config(timeout = 5.seconds, blockingTest = true) {
            val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

            val schedule = Task.Schedule.Repeating(
                start = now.minus(Duration.ofDays(5 * 365)),
                every = Duration.ofSeconds(1).toInterval()
            )

            schedule.next(after = now, within = Duration.ZERO) shouldBe (listOf(now.plusSeconds(1)))
        }

        "return no instants for non-positive duration intervals".config(timeout = 5.seconds, blockingTest = true) {
            val now = Instant.now()

            listOf(Duration.ZERO, Duration.ofSeconds(-5)).forEach { duration ->
                val schedule = Task.Schedule.Repeating(start = now, every = duration.toInterval())

                schedule.next(after = now, within = Duration.ofMinutes(5)) shouldBe (emptyList())
            }
        }

        "step month periods from the previous instant" {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.of(2026, 1, 31).atTime(10, 0).atZone(zone).toInstant()

            val schedule = Task.Schedule.Repeating(start = start, every = Period.ofMonths(1).toInterval())

            schedule.next(after = start, within = Duration.ofDays(60)) shouldBe (
                listOf(
                    LocalDate.of(2026, 2, 28).atTime(10, 0).atZone(zone).toInstant(),
                    LocalDate.of(2026, 3, 28).atTime(10, 0).atZone(zone).toInstant()
                )
            )
        }

        "fail to create repeating schedules without any days" {
            val e = shouldThrow<IllegalArgumentException> {
                Task.Schedule.Repeating(
                    start = Instant.now(),
                    every = Duration.ofMinutes(20).toInterval(),
                    days = emptySet()
                )
            }

            e.message shouldBe ("At least one day of the week is required for a repeating schedule")
        }

        "not use an execution alarm by default" {
            val task = Task(
                id = 0,
                name = "test-task",
                description = "test-description",
                goal = "test-goal",
                schedule = Task.Schedule.Once(instant = Instant.now()),
                contextSwitch = Duration.ofMinutes(5),
                isActive = true,
                color = null
            )

            task.executionAlarm shouldBe (false)
        }
    }
})
