package eventually.core.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class TaskSchedule(
    val task: Task,
    val instances: Map<UUID, TaskInstance>,
    val dismissed: List<Instant>
) {
    fun update(after: Instant, within: Duration): TaskSchedule {
        fun requireNextInstants(after: Instant): List<Instant> {
            val nextInstants = task.schedule.next(after, within)
            require(nextInstants.isNotEmpty()) {
                "At least one next instant expected for task [${task.id} / ${task.name}]"
            }
            return nextInstants
        }

        var nextInstants = requireNextInstants(after)

        return if (task.isActive) {
            val collected: List<Instant>
            while (true) {
                val last = nextInstants.last()

                val filtered = nextInstants.filter { next ->
                    !dismissed.contains(next) && instances.values.none { it.instant == next }
                }

                if (task.schedule is Task.Schedule.Repeating && instances.isEmpty() && filtered.isEmpty()) {
                    nextInstants = requireNextInstants(after = last).take(1)
                } else {
                    collected = filtered
                    break
                }
            }

            val nextInstances = collected
                .map { TaskInstance(instant = it) }
                .map { it.id to it }
                .toMap()

            copy(
                instances = instances + nextInstances
            )
        } else {
            this
        }
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

        return if (scheduleChanged || stateChanged) {
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
