package eventually.client.activities.helpers

import android.content.Context
import android.content.Intent
import eventually.client.persistence.tasks.TaskViewModel
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putInstant
import eventually.client.serialization.Extras.putTask
import eventually.client.serialization.Extras.putTaskId
import eventually.core.model.Task
import java.time.Duration
import java.time.Instant
import java.util.UUID

object TaskManagement {
    fun putTask(context: Context, task: Task) {
        val intent = Intent(context, SchedulerService::class.java)
        intent.action = SchedulerService.ActionPut
        intent.putTask(SchedulerService.ActionPutExtraTask, task)
        context.startService(intent)
    }

    fun deleteTask(context: Context, task: Int) {
        val intent = Intent(context, SchedulerService::class.java)
        intent.action = SchedulerService.ActionDelete
        intent.putTaskId(SchedulerService.ActionDeleteExtraTask, task)
        context.startService(intent)
    }

    fun dismissTaskInstance(context: Context, task: Int, instance: UUID) {
        val intent = Intent(context, SchedulerService::class.java)
        intent.action = SchedulerService.ActionDismiss
        intent.putTaskId(SchedulerService.ActionDismissExtraTask, task)
        intent.putInstanceId(SchedulerService.ActionDismissExtraInstance, instance)

        context.startService(intent)
    }

    fun undoDismissTaskInstance(context: Context, task: Int, instant: Instant) {
        val intent = Intent(context, SchedulerService::class.java)
        intent.action = SchedulerService.ActionUndoDismiss
        intent.putTaskId(SchedulerService.ActionUndoDismissExtraTask, task)
        intent.putInstant(SchedulerService.ActionUndoDismissExtraInstant, instant)

        context.startService(intent)    }

    fun postponeTaskInstance(context: Context, task: Int, instance: UUID, by: Duration) {
        val intent = Intent(context, SchedulerService::class.java)
        intent.action = SchedulerService.ActionPostpone
        intent.putTaskId(SchedulerService.ActionPostponeExtraTask, task)
        intent.putInstanceId(SchedulerService.ActionPostponeExtraInstance, instance)
        intent.putDuration(SchedulerService.ActionPostponeExtraBy, by)

        context.startService(intent)
    }
}
