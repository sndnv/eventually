package eventually.test.core.model

import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import eventually.core.model.TaskSummaryConfig
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.Instant

class TaskSummarySpec : WordSpec({
    "A TaskSummary" should {
        val task = Task(
            id = 0,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Once(instant = Instant.now()),
            contextSwitch = Duration.ofMinutes(15),
            isActive = true,
            color = 1
        )

        "support creating empty summary" {
            TaskSummary.empty() shouldBe (TaskSummary(expired = emptyList(), upcoming = emptyList(), nextEvent = null))
        }

        "support creation from task schedules" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)

            val config = TaskSummaryConfig(summarySize = TaskSummaryConfig.MinimumSummarySize)

            val expiredSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.minus(config.summarySize)))
                ).update(after = now, within = within)
            val expiredInstance = expiredSchedule.instances.values.firstOrNull()

            val earlierExpiredSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.minus(config.summarySize.multipliedBy(2))))
                ).update(after = now, within = within)
            val earlierExpiredInstance = earlierExpiredSchedule.instances.values.firstOrNull()

            val upcomingSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(10))))
                ).update(after = now, within = within)
            val upcomingInstance = upcomingSchedule.instances.values.firstOrNull()

            val laterUpcomingSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(5))))
                ).update(after = now, within = within)
            val laterUpcomingInstance = laterUpcomingSchedule.instances.values.firstOrNull()

            val futureSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.plusSeconds(1))))
                ).update(after = now, within = within)

            val summary = TaskSummary(
                instant = now,
                schedules = listOf(
                    expiredSchedule,
                    laterUpcomingSchedule,
                    upcomingSchedule,
                    futureSchedule,
                    earlierExpiredSchedule
                ),
                config = config
            )

            summary.isNotEmpty() shouldBe (true)

            withClue("Expired tasks sorted by execution time") {
                summary.expired shouldBe listOf(
                    Pair(earlierExpiredSchedule.task, earlierExpiredInstance),
                    Pair(expiredSchedule.task, expiredInstance)
                )
            }

            withClue("Upcoming tasks sorted by execution time") {
                summary.upcoming shouldBe listOf(
                    Pair(upcomingSchedule.task, upcomingInstance),
                    Pair(laterUpcomingSchedule.task, laterUpcomingInstance)
                )

            }
        }

        "support checking for emptiness" {
            val instance = TaskInstance(instant = Instant.now())

            val entries = listOf(Pair(task, instance))

            val emptySummary = TaskSummary(expired = emptyList(), upcoming = emptyList(), nextEvent = null)
            emptySummary.isEmpty() shouldBe (true)
            emptySummary.isNotEmpty() shouldBe (false)

            val summaryWithExpired = TaskSummary(expired = entries, upcoming = emptyList(), nextEvent = null)
            summaryWithExpired.isEmpty() shouldBe (false)
            summaryWithExpired.isNotEmpty() shouldBe (true)

            val summaryWithUpcoming = TaskSummary(expired = emptyList(), upcoming = entries, nextEvent = null)
            summaryWithUpcoming.isEmpty() shouldBe (false)
            summaryWithUpcoming.isNotEmpty() shouldBe (true)

            val summaryWithBoth = TaskSummary(expired = entries, upcoming = entries, nextEvent = null)
            summaryWithBoth.isEmpty() shouldBe (false)
            summaryWithBoth.isNotEmpty() shouldBe (true)
        }

        "support retrieving the next upcoming event" {
            val now = Instant.now()
            val within = Duration.ofMinutes(5)

            val config = TaskSummaryConfig(summarySize = TaskSummaryConfig.MinimumSummarySize)

            val upcomingSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(1))))
                ).update(after = now, within = within)

            val upcomingInstance = upcomingSchedule.instances.values.firstOrNull()
            upcomingInstance shouldNotBe (null)

            val upcomingInstanceExecution = upcomingInstance!!.execution()

            val summary = TaskSummary(
                instant = now,
                schedules = listOf(upcomingSchedule),
                config = config
            )

            summary.isNotEmpty() shouldBe (true)

            summary.nextEvent shouldBe (upcomingInstanceExecution)
        }
    }
})
