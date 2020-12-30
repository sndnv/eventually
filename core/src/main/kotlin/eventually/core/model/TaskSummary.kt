package eventually.core.model

import java.time.Instant

data class TaskSummary(
    val expired: List<Pair<Task, TaskInstance>>,
    val upcoming: List<Pair<Task, TaskInstance>>,
    val nextEvent: Instant?
) {
    fun isEmpty(): Boolean = expired.isEmpty() && upcoming.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    companion object {
        operator fun invoke(
            instant: Instant,
            schedules: List<TaskSchedule>,
            config: TaskSummaryConfig
        ): TaskSummary {
            val instances = schedules
                .flatMap { schedule ->
                    schedule.instances.values.map { instance -> schedule.task to instance }
                }

            val (expired, upcoming) = instances
                .filter { it.second.execution().isBefore(instant.plusMillis(config.summarySize.toMillis())) }
                .partition { (_, instance) ->
                    instance.execution().isBefore(instant)
                }

            val next = instances
                .flatMap {
                    val execution = it.second.execution()
                    val contextSwitch = execution.minus(it.first.contextSwitch)
                    listOf(execution, contextSwitch)
                }
                .filter { it.isAfter(instant) }
                .sortedBy { it }
                .firstOrNull()

            return TaskSummary(
                expired = expired.sortedWith(compareBy { it.second.execution() }),
                upcoming = upcoming.sortedWith(compareBy { it.second.execution() }),
                nextEvent = next
            )
        }

        fun empty(): TaskSummary = TaskSummary(expired = emptyList(), upcoming = emptyList(), nextEvent = null)
    }
}
