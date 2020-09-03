package eventually.core.model

import eventually.core.notifications.Notifier
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class TaskSchedule(
    val task: Task,
    val instances: Map<UUID, TaskInstance>,
    val dismissed: List<Instant>
) {
    fun update(after: Instant): TaskSchedule {
        val next = task.schedule.next(after)

        val updatedDismissed = dismissed.filter { it.isAfter(next) }

        return if (!dismissed.contains(next) && instances.values.none { it.instant == next }) {
            val instance = TaskInstance(instant = next)
            copy(
                instances = instances + (instance.id to instance),
                dismissed = updatedDismissed
            )
        } else {
            copy(
                dismissed = updatedDismissed
            )
        }
    }

    fun next(after: Instant): Pair<TaskInstance, Instant>? {
        return instances.values
            .map { Pair(it, it.execution()) }
            .sortedBy { it.second }
            .find { it.second.isAfter(after) }
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

    fun notify(instant: Instant, withTolerance: Duration, notifier: Notifier) {
        when (val match = match(instant = instant, withTolerance = withTolerance)) {
            is Matched.Instant -> notifier.putInstanceExecutionNotification(
                task = task,
                instance = match.instance
            )

            is Matched.ContextSwitch -> notifier.putInstanceContextSwitchNotification(
                task = task,
                instance = match.instance
            )

            is Matched.None -> Unit // do nothing
        }
    }

    fun match(instant: Instant, withTolerance: Duration): Matched {
        return when (val next = next(after = instant.minus(withTolerance))) {
            null -> Matched.None
            else -> {
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
