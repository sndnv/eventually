package eventually.client.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import eventually.client.scheduling.NotificationManagerExtensions.getInstanceNotificationId
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putTaskId
import java.time.Instant
import java.util.UUID

object AlarmManagerExtensions {
    fun AlarmManager.putEvaluationAlarm(context: Context, instant: Instant) {
        putAlarm(instant, getAlarmIntent(context))
    }

    fun AlarmManager.deleteEvaluationAlarm(context: Context) {
        cancel(getAlarmIntent(context))
    }

    fun AlarmManager.putAlarmTimeout(context: Context, task: Int, instance: UUID, instant: Instant) {
        val intent: PendingIntent = PendingIntent.getService(
            context,
            getAlarmTimeoutRequestCode(task, instance),
            createAlarmTimeoutIntent(context, task, instance),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
        )

        putAlarm(instant, intent)
    }

    fun AlarmManager.deleteAlarmTimeout(context: Context, task: Int, instance: UUID) {
        val intent: PendingIntent? = PendingIntent.getService(
            context,
            getAlarmTimeoutRequestCode(task, instance),
            createAlarmTimeoutIntent(context, task, instance),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE
        )

        intent?.let {
            cancel(it)
            it.cancel()
        }
    }

    fun AlarmManager.canUseExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()

    private fun AlarmManager.putAlarm(instant: Instant, intent: PendingIntent) {
        val triggerAt = instant.toEpochMilli()

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

    private fun getAlarmIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            SchedulerServiceEvaluationRequestCode,
            Intent(context, SchedulerService::class.java).apply { action = SchedulerService.ActionEvaluate },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getAlarmTimeoutRequestCode(task: Int, instance: UUID): Int =
        getInstanceNotificationId(task, instance) + SchedulerServiceAlarmTimeoutRequestCode

    private fun createAlarmTimeoutIntent(context: Context, task: Int, instance: UUID): Intent =
        Intent(context, SchedulerService::class.java).apply {
            action = SchedulerService.ActionAlarmTimeout
            putTaskId(SchedulerService.ActionAlarmTimeoutExtraTask, task)
            putInstanceId(SchedulerService.ActionAlarmTimeoutExtraInstance, instance)
        }

    private const val SchedulerServiceEvaluationRequestCode: Int = 1
    private const val SchedulerServiceAlarmTimeoutRequestCode: Int = 5
}
