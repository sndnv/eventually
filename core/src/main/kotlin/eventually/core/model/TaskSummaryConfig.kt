package eventually.core.model

import java.time.Duration

data class TaskSummaryConfig(
    val summarySize: Duration,
    val dismissedRetention: Duration = DefaultDismissedRetention,
    val dismissedLimit: Int = DefaultDismissedLimit
) {
    init {
        require(summarySize >= MinimumSummarySize) {
            "Summary size of [$summarySize] is below minimum of [$MinimumSummarySize]"
        }

        require(!dismissedRetention.isZero && !dismissedRetention.isNegative) {
            "Dismissed retention of [$dismissedRetention] must be positive"
        }

        require(dismissedLimit > 0) {
            "Dismissed limit of [$dismissedLimit] must be positive"
        }
    }

    companion object {
        val MinimumSummarySize: Duration = Duration.ofMinutes(5)

        val DefaultDismissedRetention: Duration = Duration.ofDays(30)

        const val DefaultDismissedLimit: Int = 100
    }
}
