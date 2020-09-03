package eventually.core.scheduling

import eventually.core.model.Task
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import eventually.core.model.TaskSummaryConfig
import eventually.core.notifications.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DefaultScheduler private constructor(private val ref: SendChannel<Message>) : Scheduler {
    override fun put(task: Task) {
        ref.offer(Message.Put(task))
    }

    override fun delete(task: UUID) {
        ref.offer(Message.Delete(task))
    }

    override fun dismiss(task: UUID, instance: UUID) {
        ref.offer(Message.Dismiss(task, instance))
    }

    override fun postpone(task: UUID, instance: UUID, by: Duration) {
        ref.offer(Message.Postpone(task, instance, by))
    }

    suspend fun getAsync(): Deferred<List<TaskSchedule>> {
        val response = CompletableDeferred<List<TaskSchedule>>()
        ref.send(Message.Get(response))
        return response
    }

    private sealed class Message {
        data class Put(val task: Task) : Message()
        data class Delete(val task: UUID) : Message()
        data class Dismiss(val task: UUID, val instance: UUID) : Message()
        data class Postpone(val task: UUID, val instance: UUID, val by: Duration) : Message()
        data class Get(val response: CompletableDeferred<List<TaskSchedule>>) : Message()

        object Evaluate : Message()
    }

    companion object {
        private val SchedulingTolerance: Duration = Duration.ofSeconds(3)

        operator fun invoke(
            notifier: Notifier,
            config: TaskSummaryConfig,
            scope: CoroutineScope
        ): DefaultScheduler {
            return DefaultScheduler(ref = scope.collector(notifier, config))
        }

        @SuppressWarnings("ComplexMethod")
        @OptIn(ObsoleteCoroutinesApi::class)
        private fun CoroutineScope.collector(
            notifier: Notifier,
            config: TaskSummaryConfig
        ): SendChannel<Message> = actor {
            var schedules = mutableMapOf<UUID, TaskSchedule>()

            fun evaluate() {
                notifier.deleteInternalAlarm()
                notifier.deleteSummaryNotification()

                val now = Instant.now()

                schedules = schedules.mapValues {
                    it.value.update(after = now)
                }.toMutableMap()

                schedules.forEach { (_, schedule) ->
                    schedule.notify(
                        instant = now,
                        withTolerance = SchedulingTolerance,
                        notifier = notifier
                    )
                }

                val summary = TaskSummary(
                    instant = now,
                    schedules = schedules.values.toList(),
                    config = config
                )

                if (summary.isNotEmpty()) {
                    notifier.putSummaryNotification(summary)
                }

                val nextEvaluation = summary.nextEvent(after = now)
                if (nextEvaluation != null) {
                    notifier.putInternalAlarm(instant = nextEvaluation)
                }
            }

            fun put(msg: Message.Put) {
                schedules[msg.task.id] = TaskSchedule(msg.task)
                evaluate()
            }

            fun delete(msg: Message.Delete) {
                val schedule = schedules[msg.task]
                if (schedule != null) {
                    schedules.remove(msg.task)

                    schedule.instances.keys.forEach {
                        notifier.deleteInstanceNotifications(msg.task, it)
                    }

                    evaluate()
                }
            }

            fun dismiss(msg: Message.Dismiss) {
                val schedule = schedules[msg.task]
                if (schedule != null) {
                    schedules[msg.task] = schedule.dismiss(msg.instance)

                    notifier.deleteInstanceNotifications(msg.task, msg.instance)

                    evaluate()
                }
            }

            fun postpone(msg: Message.Postpone) {
                val schedule = schedules[msg.task]
                if (schedule != null) {
                    schedules[msg.task] = schedule.postpone(msg.instance, msg.by)

                    notifier.deleteInstanceNotifications(msg.task, msg.instance)

                    evaluate()
                }
            }

            fun get(msg: Message.Get) {
                msg.response.complete(schedules.values.toList())
            }

            for (msg in channel) {
                when (msg) {
                    is Message.Put -> put(msg)
                    is Message.Delete -> delete(msg)
                    is Message.Dismiss -> dismiss(msg)
                    is Message.Postpone -> postpone(msg)
                    is Message.Evaluate -> evaluate()
                    is Message.Get -> get(msg)
                }
            }
        }
    }
}
