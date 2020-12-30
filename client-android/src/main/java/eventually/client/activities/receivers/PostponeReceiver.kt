package eventually.client.activities.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import eventually.client.R
import eventually.client.activities.helpers.TaskManagement
import eventually.client.serialization.Extras.requireDuration
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireTaskId

class PostponeReceiver : BroadcastReceiver() {
    val intentFilter: IntentFilter = IntentFilter(Action)

    override fun onReceive(context: Context, intent: Intent) {
        val task = intent.requireTaskId(ExtraTask)
        val instance = intent.requireInstanceId(ExtraInstance)
        val by = intent.requireDuration(ExtraBy)

        TaskManagement.postponeTaskInstance(context, task, instance, by)

        Toast.makeText(context, context.getString(R.string.toast_task_postponed), Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val Action: String = "eventually.client.activities.receivers.Postpone"
        const val ExtraTask: String = "eventually.client.activities.receivers.PostponeReceiver.extra_task"
        const val ExtraInstance: String = "eventually.client.activities.receivers.PostponeReceiver.extra_instance"
        const val ExtraBy: String = "eventually.client.activities.receivers.PostponeReceiver.extra_by"
    }
}
