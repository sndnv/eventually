package eventually.client.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

object AlarmManagerExtensions {
    fun AlarmManager.putEvaluationAlarm(context: Context, instant: Instant) {
        setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            instant.toEpochMilli(),
            getAlarmIntent(context)
        )
    }

    fun AlarmManager.deleteEvaluationAlarm(context: Context) {
        cancel(getAlarmIntent(context))
    }

    private fun getAlarmIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            SchedulerServiceEvaluationRequestCode,
            Intent(context, SchedulerService::class.java).apply { action = SchedulerService.ActionEvaluate },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private const val SchedulerServiceEvaluationRequestCode: Int = 1
}
