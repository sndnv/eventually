package eventually.core.model

import java.time.Duration

data class TaskSummaryConfig(
    val summarySize: Duration
) {
    init {
        require(summarySize >= MinimumSummarySize) {
            "Summary size of [$summarySize] is below minimum of [$MinimumSummarySize]"
        }
    }

    companion object {
        val MinimumSummarySize: Duration = Duration.ofMinutes(5)
    }
}
