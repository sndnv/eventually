package eventually.core.model

import java.time.Instant

data class TaskSummary(
    val expired: List<Pair<Task, TaskInstance>>,
    val upcoming: List<Pair<Task, TaskInstance>>
) {
    fun isEmpty(): Boolean = expired.isEmpty() && upcoming.isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun goals(): List<String> = expired.map { it.first.goal } + upcoming.map { it.first.goal }

    fun nextEvent(after: Instant): Instant? {
        return upcoming
            .flatMap {
                val execution = it.second.execution()
                val contextSwitch = execution.minus(it.first.contextSwitch)
                listOf(execution, contextSwitch)
            }
            .filter { it.isAfter(after) }
            .minByOrNull { it }
    }

    companion object {
        operator fun invoke(
            instant: Instant,
            schedules: List<TaskSchedule>,
            config: TaskSummaryConfig
        ): TaskSummary {
            val (expired, upcoming) = schedules
                .flatMap { schedule ->
                    schedule.instances.values.map { instance -> schedule.task to instance }
                }
                .filter { it.second.execution().isBefore(instant.plusMillis(config.summarySize.toMillis())) }
                .partition { (_, instance) ->
                    instance.execution().isBefore(instant)
                }

            return TaskSummary(
                expired = expired.sortedWith(
                    compareByDescending<Pair<Task, TaskInstance>> { it.first.priority }.thenBy { it.second.execution() }
                ),
                upcoming = upcoming.sortedWith(
                    compareBy<Pair<Task, TaskInstance>> { it.second.execution() }.thenByDescending { it.first.priority }
                )
            )
        }
    }
}
