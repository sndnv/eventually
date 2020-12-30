package eventually.core.scheduling

import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import eventually.core.model.TaskSummaryConfig
import java.time.Duration
import java.time.Instant
import java.util.UUID

object SchedulerOps {
    sealed class Message {
        data class Init(val tasks: List<Task>, val schedules: List<TaskSchedule>) : Message()
        data class Put(val task: Task) : Message()
        data class Delete(val task: Int) : Message()
        data class Dismiss(val task: Int, val instance: UUID) : Message()
        data class Postpone(val task: Int, val instance: UUID, val by: Duration) : Message()
        data class Evaluate(val config: TaskSummaryConfig) : Message()
    }

    sealed class Notification {
        data class PutEvaluationAlarm(val instant: Instant) : Notification()
        object DeleteEvaluationAlarm : Notification()

        data class PutInstanceExecutionNotification(val task: Task, val instance: TaskInstance) : Notification()
        data class PutInstanceContextSwitchNotification(val task: Task, val instance: TaskInstance) : Notification()
        data class DeleteInstanceNotifications(val task: Int, val instance: UUID) : Notification()
    }

    data class SchedulerResult(
        val schedules: Map<Int, TaskSchedule>,
        val notifications: List<Notification>,
        val summary: TaskSummary?,
        val affectedSchedules: List<Int>
    )

    fun init(msg: Message.Init): SchedulerResult {
        val existingSchedules = msg.schedules.map { it.task.id to it }.toMap()

        return SchedulerResult(
            schedules = msg.tasks.map { task ->
                task.id to (existingSchedules[task.id] ?: TaskSchedule(task))
            }.toMap(),
            notifications = emptyList(),
            summary = null,
            affectedSchedules = emptyList()
        )
    }

    fun put(schedules: Map<Int, TaskSchedule>, msg: Message.Put): SchedulerResult {
        val (schedule, notifications) = when (val schedule = schedules[msg.task.id]) {
            null -> {
                TaskSchedule(msg.task) to emptyList()
            }

            else -> {
                val updatedSchedule = schedule.withTask(msg.task)

                val notifications = schedule.instances.keys.filterNot { updatedSchedule.instances.containsKey(it) }
                    .map { discarded ->
                        Notification.DeleteInstanceNotifications(msg.task.id, discarded)
                    }

                updatedSchedule to notifications
            }
        }

        return SchedulerResult(
            schedules = schedules + (msg.task.id to schedule),
            notifications = notifications,
            summary = null,
            affectedSchedules = listOf(msg.task.id)
        )
    }

    fun delete(schedules: Map<Int, TaskSchedule>, msg: Message.Delete): SchedulerResult {
        val schedule = schedules[msg.task]
        return if (schedule != null) {
            val updated = schedules - msg.task

            val notifications = schedule.instances.keys.map {
                Notification.DeleteInstanceNotifications(msg.task, it)
            }

            SchedulerResult(
                schedules = updated,
                notifications = notifications,
                summary = null,
                affectedSchedules = listOf(msg.task)
            )
        } else {
            SchedulerResult(
                schedules = schedules,
                notifications = emptyList(),
                summary = null,
                affectedSchedules = emptyList()
            )
        }
    }

    fun dismiss(schedules: Map<Int, TaskSchedule>, msg: Message.Dismiss): SchedulerResult {
        val schedule = schedules[msg.task]
        return if (schedule != null) {
            val updated = schedules + (msg.task to schedule.dismiss(msg.instance))

            val notifications = listOf(Notification.DeleteInstanceNotifications(msg.task, msg.instance))

            SchedulerResult(
                schedules = updated,
                notifications = notifications,
                summary = null,
                affectedSchedules = listOf(msg.task)
            )
        } else {
            SchedulerResult(
                schedules = schedules,
                notifications = emptyList(),
                summary = null,
                affectedSchedules = emptyList()
            )
        }
    }

    fun postpone(schedules: Map<Int, TaskSchedule>, msg: Message.Postpone): SchedulerResult {
        val schedule = schedules[msg.task]
        return if (schedule != null) {
            val updated = schedules + (msg.task to schedule.postpone(msg.instance, msg.by))

            val notifications = listOf(Notification.DeleteInstanceNotifications(msg.task, msg.instance))

            SchedulerResult(
                schedules = updated,
                notifications = notifications,
                summary = null,
                affectedSchedules = listOf(msg.task)
            )
        } else {
            SchedulerResult(
                schedules = schedules,
                notifications = emptyList(),
                summary = null,
                affectedSchedules = emptyList()
            )
        }
    }

    fun evaluate(schedules: Map<Int, TaskSchedule>, msg: Message.Evaluate): SchedulerResult {
        val notifications: MutableList<Notification> = mutableListOf(
            Notification.DeleteEvaluationAlarm
        )

        val now = Instant.now()

        val updated = schedules.mapValues { it.value.update(after = now, within = msg.config.summarySize) }

        updated.forEach { (_, schedule) ->
            schedule.match(instant = now, withTolerance = SchedulingTolerance).map {
                when (it) {
                    is TaskSchedule.Matched.Instant ->
                        notifications += Notification.PutInstanceExecutionNotification(
                            task = schedule.task,
                            instance = it.instance
                        )

                    is TaskSchedule.Matched.ContextSwitch ->
                        notifications += Notification.PutInstanceContextSwitchNotification(
                            task = schedule.task,
                            instance = it.instance
                        )

                    is TaskSchedule.Matched.None -> Unit // do nothing
                }
            }
        }

        val summary = TaskSummary(
            instant = now,
            schedules = updated.values.toList(),
            config = msg.config
        )

        if (summary.nextEvent != null) {
            notifications += Notification.PutEvaluationAlarm(instant = summary.nextEvent)
        }

        return SchedulerResult(
            schedules = updated,
            notifications = notifications,
            summary = summary,
            affectedSchedules = updated.filterNot { it.value == schedules[it.key] }.keys.toList()
        )
    }

    @SuppressWarnings("MagicNumber")
    private val SchedulingTolerance: Duration = Duration.ofSeconds(3)
}
