package eventually.test.core.model

import eventually.core.model.Task
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
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
                val now = Instant.now()
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
                    every = repetitionDuration
                )

                val expected = (within.toMinutes() / duration).coerceAtLeast(1L).toInt()

                if (originalSchedule.isAfter(now)) {
                    val next = futureSchedule.next(after = now, within = within)

                    next.size shouldBe (expected)
                    next.first() shouldBe (originalSchedule)
                    next.withIndex().forEach { (i, instant) ->
                        instant shouldBe originalSchedule.plusSeconds(repetitionDuration.seconds * i)
                    }
                } else {
                    val next = futureSchedule.next(after = now, within = within)

                    next.size shouldBe (expected)
                    next.first() shouldBe (expectedNext)
                    next.withIndex().forEach { (i, instant) ->
                        instant shouldBe expectedNext.plusSeconds(repetitionDuration.seconds * i)
                    }
                }
            }

            for (hour in 0..23) {
                for (minute in 0..59) {
                    for (duration in 1..120L) {
                        withClue("Initial schedule: [$hour:$minute]; Duration: [$duration]") {
                            verify(hour = hour, minute = minute, duration = duration)
                        }
                    }
                }
            }
        }
    }
})
