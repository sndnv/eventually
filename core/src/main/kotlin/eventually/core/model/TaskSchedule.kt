package eventually.core.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class TaskSchedule(
    val task: Task,
    val instances: Map<UUID, TaskInstance>,
    val dismissed: List<Instant>
) {
    fun update(
        after: Instant,
        within: Duration,
        retention: Duration = TaskSummaryConfig.DefaultDismissedRetention,
        limit: Int = TaskSummaryConfig.DefaultDismissedLimit
    ): TaskSchedule {
        return if (task.isActive) {
            val latestInstance = instances.values.maxByOrNull { it.instant }
            var nextInstants = task.schedule.next(after, within)
            val repeating = task.schedule is Task.Schedule.Repeating

            val collected: List<Instant>
            while (true) {
                val last = nextInstants.lastOrNull()

                val filtered = nextInstants.filter { next ->
                    val wasDismissed = if (repeating) {
                        dismissed.contains(next)
                    } else {
                        dismissed.any { !it.isBefore(next) }
                    }

                    !wasDismissed
                            && instances.values.none { it.instant == next }
                            && latestInstance?.let { next.isAfter(it.instant) } ?: true
                }

                val searchFurther = repeating && instances.isEmpty() && filtered.isEmpty()

                if (searchFurther && last != null) {
                    nextInstants = task.schedule.next(after = last, within = within).take(1)
                } else {
                    collected = filtered
                    break
                }
            }

            val nextInstances = collected
                .map { TaskInstance(instant = it) }
                .associateBy { it.id }

            copy(
                instances = instances + nextInstances,
                dismissed = if (task.schedule is Task.Schedule.Repeating) {
                    trimmedDismissed(after, retention, limit)
                } else {
                    dismissed
                }
            )
        } else {
            this
        }
    }

    private fun trimmedDismissed(after: Instant, retention: Duration, limit: Int): List<Instant> {
        val oldest = runCatching { after.minus(retention) }.getOrDefault(Instant.MIN)

        return dismissed
            .withIndex()
            .filterNot { it.value.isBefore(oldest) }
            .sortedByDescending { it.value }
            .filterIndexed { rank, entry -> rank < limit || entry.value.isAfter(after) }
            .sortedBy { it.index }
            .map { it.value }
    }

    fun next(after: Instant): List<Pair<TaskInstance, Instant>> {
        return instances.values
            .map { it to it.execution() }
            .filter { it.second.isAfter(after) }
            .sortedBy { it.second }
    }

    fun dismiss(instance: UUID): TaskSchedule {
        require(instances.contains(instance)) {
            "Cannot dismiss instance [$instance] for task [${task.id} / ${task.name}]; instance does not exist"
        }

        val instant = instances[instance]?.instant!!

        return copy(
            instances = instances - instance,
            dismissed = dismissed + instant
        )
    }

    fun canUndoDismiss(instant: Instant): Boolean =
        dismissed.contains(instant) || isRestorable(instant)

    private fun isRestorable(instant: Instant): Boolean =
        task.schedule is Task.Schedule.Repeating &&
                !instant.isAfter(Instant.now()) &&
                instances.values.none { it.instant == instant }

    fun undoDismiss(instant: Instant): TaskSchedule {
        require(canUndoDismiss(instant)) {
            "Cannot undo dismissal with [$instant] for task [${task.id} / ${task.name}]; " +
                    "instant is not dismissed or already has an instance"
        }

        val instance = TaskInstance(instant)

        return copy(
            instances = instances + (instance.id to instance),
            dismissed = dismissed - instant
        )
    }

    fun postpone(instance: UUID, by: Duration): TaskSchedule {
        require(instances.contains(instance)) {
            "Cannot postpone instance [$instance] for task [${task.id} / ${task.name}]; instance does not exist"
        }

        return copy(instances = instances + (instance to instances[instance]?.postponed(by)!!))
    }

    fun match(instant: Instant, withTolerance: Duration): List<Matched> =
        next(after = instant.minus(withTolerance)).map { next ->
            val nextExecution = next.second

            val instantMin = instant.minus(withTolerance)
            val instantMax = instant.plus(withTolerance)
            val matchesInstant = instantMin.isBefore(nextExecution) && instantMax.isAfter(nextExecution)

            val matchesContextSwitch by lazy {
                instant.isBefore(nextExecution) && instant.plus(task.contextSwitch).isAfter(nextExecution)
            }

            when {
                matchesInstant -> Matched.Instant(instance = next.first)
                matchesContextSwitch -> Matched.ContextSwitch(instance = next.first)
                else -> Matched.None
            }
        }

    fun withTask(newTask: Task): TaskSchedule {
        val now = Instant.now()

        val scheduleChanged = task.schedule != newTask.schedule
        val stateChanged = task.isActive != newTask.isActive

        return if (scheduleChanged && newTask.schedule is Task.Schedule.Once) {
            copy(
                task = newTask,
                instances = emptyMap(),
                dismissed = emptyList()
            )
        } else if (scheduleChanged || stateChanged) {
            val pastInstances = instances.filterValues { instance -> instance.execution().isBefore(now) }
            copy(
                task = newTask,
                instances = pastInstances
            )
        } else {
            copy(task = newTask)
        }
    }

    companion object {
        operator fun invoke(task: Task): TaskSchedule {
            return TaskSchedule(
                task = task,
                instances = emptyMap(),
                dismissed = emptyList()
            )
        }
    }

    sealed class Matched {
        data class Instant(val instance: TaskInstance) : Matched()
        data class ContextSwitch(val instance: TaskInstance) : Matched()
        object None : Matched()
    }
}
