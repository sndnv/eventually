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
import java.util.UUID

class TaskSummarySpec : WordSpec({
    "A TaskSummary" should {
        val task = Task(
            id = UUID.randomUUID(),
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Once(instant = Instant.now()),
            priority = Task.Priority.High,
            contextSwitch = Duration.ofMinutes(15)
        )

        "support creation from task schedules" {
            val now = Instant.now()

            val config = TaskSummaryConfig(summarySize = TaskSummaryConfig.MinimumSummarySize)

            val expiredSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.minus(config.summarySize)))
                ).update(after = now)
            val expiredInstance = expiredSchedule.instances.values.firstOrNull()

            val earlierExpiredSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.minus(config.summarySize.multipliedBy(2))))
                ).update(after = now)
            val earlierExpiredInstance = earlierExpiredSchedule.instances.values.firstOrNull()

            val lowPriorityExpiredSchedule =
                TaskSchedule(
                    task = task.copy(
                        schedule = Task.Schedule.Once(now.minus(config.summarySize)),
                        priority = Task.Priority.Low
                    )
                ).update(after = now)
            val lowPriorityExpiredInstance = lowPriorityExpiredSchedule.instances.values.firstOrNull()

            val upcomingSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(10))))
                ).update(after = now)
            val upcomingInstance = upcomingSchedule.instances.values.firstOrNull()

            val laterUpcomingSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(5))))
                ).update(after = now)
            val laterUpcomingInstance = laterUpcomingSchedule.instances.values.firstOrNull()

            val mediumPriorityUpcomingSchedule =
                TaskSchedule(
                    task = task.copy(
                        schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(10))),
                        priority = Task.Priority.Medium
                    )
                ).update(after = now)
            val mediumPriorityUpcomingInstance = mediumPriorityUpcomingSchedule.instances.values.firstOrNull()

            val futureSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.plusSeconds(1))))
                ).update(after = now)

            val summary = TaskSummary(
                instant = now,
                schedules = listOf(
                    expiredSchedule,
                    lowPriorityExpiredSchedule,
                    laterUpcomingSchedule,
                    upcomingSchedule,
                    mediumPriorityUpcomingSchedule,
                    futureSchedule,
                    earlierExpiredSchedule
                ),
                config = config
            )

            summary.isNotEmpty() shouldBe(true)

            withClue("Expired tasks sorted by priority first and execution time second") {
                summary.expired shouldBe(
                    listOf(
                        Pair(earlierExpiredSchedule.task, earlierExpiredInstance),
                        Pair(expiredSchedule.task, expiredInstance),
                        Pair(lowPriorityExpiredSchedule.task, lowPriorityExpiredInstance)
                    )
                )
            }

            withClue("Upcoming tasks sorted by execution time first and priority second") {
                summary.upcoming shouldBe(
                    listOf(
                        Pair(upcomingSchedule.task, upcomingInstance),
                        Pair(mediumPriorityUpcomingSchedule.task, mediumPriorityUpcomingInstance),
                        Pair(laterUpcomingSchedule.task, laterUpcomingInstance)
                    )
                )
            }
        }

        "support checking for emptiness" {
            val instance = TaskInstance(instant = Instant.now())

            val entries = listOf(Pair(task, instance))

            val emptySummary = TaskSummary(expired = emptyList(), upcoming = emptyList())
            emptySummary.isEmpty() shouldBe(true)
            emptySummary.isNotEmpty() shouldBe(false)

            val summaryWithExpired = TaskSummary(expired = entries, upcoming = emptyList())
            summaryWithExpired.isEmpty() shouldBe(false)
            summaryWithExpired.isNotEmpty() shouldBe(true)

            val summaryWithUpcoming = TaskSummary(expired = emptyList(), upcoming = entries)
            summaryWithUpcoming.isEmpty() shouldBe(false)
            summaryWithUpcoming.isNotEmpty() shouldBe(true)

            val summaryWithBoth = TaskSummary(expired = entries, upcoming = entries)
            summaryWithBoth.isEmpty() shouldBe(false)
            summaryWithBoth.isNotEmpty() shouldBe(true)
        }

        "support retrieving all active goals" {
            val instance = TaskInstance(instant = Instant.now())

            val goal1 = "test-goal-01"
            val goal2 = "test-goal-02"

            val expiredEntries = listOf(Pair(task.copy(goal = goal1), instance))
            val upcomingEntries = listOf(Pair(task.copy(goal = goal2), instance))

            val summary = TaskSummary(expired = expiredEntries, upcoming = upcomingEntries)

            summary.goals() shouldBe(listOf(goal1, goal2))
        }

        "support retrieving the next upcoming event" {
            val now = Instant.now()

            val config = TaskSummaryConfig(summarySize = TaskSummaryConfig.MinimumSummarySize)

            val emptySummary = TaskSummary(expired = emptyList(), upcoming = emptyList())
            emptySummary.nextEvent(after = now) shouldBe(null)

            val upcomingSchedule =
                TaskSchedule(
                    task = task.copy(schedule = Task.Schedule.Once(now.plus(config.summarySize.minusSeconds(1))))
                ).update(after = now)

            val upcomingInstance = upcomingSchedule.instances.values.firstOrNull()
            upcomingInstance shouldNotBe(null)

            val upcomingInstanceExecution = upcomingInstance!!.execution()
            val upcomingInstanceContextSwitch = upcomingInstanceExecution.minus(upcomingSchedule.task.contextSwitch)

            val summary = TaskSummary(
                instant = now,
                schedules = listOf(upcomingSchedule),
                config = config
            )

            summary.isNotEmpty() shouldBe(true)

            summary.nextEvent(
                after = now
            ) shouldBe(upcomingInstanceExecution)

            summary.nextEvent(
                after = now.minus(upcomingSchedule.task.contextSwitch)
            ) shouldBe(upcomingInstanceContextSwitch)
        }
    }
})
