package eventually.test.core.model

import eventually.core.model.TaskSummaryConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class TaskSummaryConfigSpec : WordSpec({
    "A TaskSummaryConfig" should {
        "reject invalid summary size configuration" {
            val size = TaskSummaryConfig.MinimumSummarySize.minusSeconds(1)

            val e = shouldThrow<IllegalArgumentException> {
                TaskSummaryConfig(summarySize = size)
            }

            e.message shouldBe(
                "Summary size of [$size] is below minimum of [${TaskSummaryConfig.MinimumSummarySize}]"
            )
        }

        "provide default dismissed retention and limit" {
            val config = TaskSummaryConfig(summarySize = TaskSummaryConfig.MinimumSummarySize)

            config.dismissedRetention shouldBe (Duration.ofDays(30))
            config.dismissedLimit shouldBe (100)
        }

        "reject non-positive dismissed retention" {
            listOf(Duration.ZERO, Duration.ofDays(-1)).forEach { retention ->
                val e = shouldThrow<IllegalArgumentException> {
                    TaskSummaryConfig(
                        summarySize = TaskSummaryConfig.MinimumSummarySize,
                        dismissedRetention = retention
                    )
                }

                e.message shouldBe ("Dismissed retention of [$retention] must be positive")
            }
        }

        "reject non-positive dismissed limit" {
            listOf(0, -1).forEach { limit ->
                val e = shouldThrow<IllegalArgumentException> {
                    TaskSummaryConfig(summarySize = TaskSummaryConfig.MinimumSummarySize, dismissedLimit = limit)
                }

                e.message shouldBe ("Dismissed limit of [$limit] must be positive")
            }
        }
    }
})
