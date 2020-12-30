package eventually.client.activities.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import eventually.client.R
import eventually.client.activities.helpers.TaskManagement
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireTaskId

class DismissReceiver : BroadcastReceiver() {
    val intentFilter: IntentFilter = IntentFilter(Action)

    override fun onReceive(context: Context, intent: Intent) {
        val task = intent.requireTaskId(ExtraTask)
        val instance = intent.requireInstanceId(ExtraInstance)

        TaskManagement.dismissTaskInstance(context, task, instance)

        Toast.makeText(context, context.getString(R.string.toast_task_dismissed), Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val Action: String = "eventually.client.activities.receivers.Dismiss"
        const val ExtraTask: String = "eventually.client.activities.receivers.DismissReceiver.extra_task"
        const val ExtraInstance: String = "eventually.client.activities.receivers.DismissReceiver.extra_instance"
    }
}
