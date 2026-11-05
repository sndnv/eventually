package eventually.client.activities.receivers

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import eventually.client.scheduling.SchedulerService

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            context.startService(
                Intent(context, SchedulerService::class.java).apply {
                    action = SchedulerService.ActionEvaluate
                }
            )
        }
    }
}
