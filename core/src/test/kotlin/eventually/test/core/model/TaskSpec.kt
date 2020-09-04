package eventually.test.core.model

import eventually.core.model.Task
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class TaskSpec : WordSpec({
    "A Task" should {
        "support scheduling single-execution events" {
            val now = Instant.now()

            val expected = now.plus(Duration.ofSeconds(42))
            val schedule = Task.Schedule.Once(instant = expected)

            schedule.next(after = now) shouldBe(expected)
        }

        "support scheduling repeating events" {
            fun verify(hour: Int, minute: Int, duration: Long) {
                val now = Instant.now()

                val originalTime = LocalTime.of(hour, minute)
                val originalSchedule = now.truncatedTo(ChronoUnit.DAYS).plusSeconds(
                    originalTime.toSecondOfDay().toLong()
                )
                val repetitionDuration = Duration.ofMinutes(duration)

                val events = abs(Duration.between(now, originalSchedule).dividedBy(repetitionDuration))

                val expectedNext = originalSchedule.plusSeconds(repetitionDuration.toSeconds() * (events + 1))

                val futureSchedule = Task.Schedule.Repeating(
                    time = originalTime,
                    every = repetitionDuration
                )

                if (originalSchedule.isAfter(now)) {
                    futureSchedule.next(after = now) shouldBe(originalSchedule)
                } else {
                    futureSchedule.next(after = now) shouldBe(expectedNext)
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
