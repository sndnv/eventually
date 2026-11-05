package eventually.client.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

object AlarmManagerExtensions {
    fun AlarmManager.putEvaluationAlarm(context: Context, instant: Instant) {
        val triggerAt = instant.toEpochMilli()
        val intent = getAlarmIntent(context)

        try {
            if (canUseExactAlarms()) {
                setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (e: SecurityException) {
            setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    fun AlarmManager.deleteEvaluationAlarm(context: Context) {
        cancel(getAlarmIntent(context))
    }

    fun AlarmManager.canUseExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()

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
