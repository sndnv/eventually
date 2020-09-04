package eventually.test.core.model

import eventually.core.model.TaskSummaryConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

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
    }
})
