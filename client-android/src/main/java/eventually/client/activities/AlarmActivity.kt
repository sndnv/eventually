package eventually.client.activities

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eventually.client.R
import eventually.client.activities.helpers.TaskManagement
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.NotificationManagerExtensions.isInstanceExecutionAlarmActive
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireInstant
import eventually.client.serialization.Extras.requireTask
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getDateTimeFormat
import eventually.client.settings.Settings.getPostponeLength
import eventually.client.ui.screens.AlarmScreen
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AlarmActivity : ComponentActivity() {
    private val preferences by lazy { defaultPreferences() }

    private var alarm: Alarm? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showOverLockScreen()
        enableEdgeToEdge()

        alarm = intent.toAlarm()

        if (alarm == null) {
            finish()
            return
        }

        setContent {
            val dateTimeFormat = remember { preferences.getDateTimeFormat() }
            val postponeLength = remember { preferences.getPostponeLength() }

            EventuallyTheme {
                alarm?.let { current ->
                    LaunchedEffect(current) {
                        do {
                            delay(RingingCheckInterval)
                        } while (isRinging(current))

                        finish()
                    }

                    AlarmScreen(
                        task = current.task,
                        execution = current.execution,
                        dateTimeFormat = dateTimeFormat,
                        postponeLength = postponeLength,
                        onPostpone = { postpone(current, postponeLength) },
                        onDismiss = { dismiss(current) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toAlarm()?.let {
            setIntent(intent)
            alarm = it
        }
    }

    private fun isRinging(alarm: Alarm): Boolean =
        getSystemService(NotificationManager::class.java).isInstanceExecutionAlarmActive(alarm.task.id, alarm.instance)

    private fun dismiss(alarm: Alarm) {
        if (isFinishing) return

        getSystemService(NotificationManager::class.java).deleteInstanceNotifications(alarm.task.id, alarm.instance)
        TaskManagement.dismissTaskInstance(this, alarm.task.id, alarm.instance)
        Toast.makeText(this, getString(R.string.toast_task_dismissed), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun postpone(alarm: Alarm, by: Duration) {
        if (isFinishing) return

        getSystemService(NotificationManager::class.java).deleteInstanceNotifications(alarm.task.id, alarm.instance)
        TaskManagement.postponeTaskInstance(this, alarm.task.id, alarm.instance, by)
        Toast.makeText(this, getString(R.string.toast_task_postponed), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    data class Alarm(val task: Task, val instance: UUID, val execution: Instant)

    companion object {
        const val ExtraTask: String = "eventually.client.activities.AlarmActivity.extra_task"
        const val ExtraInstance: String = "eventually.client.activities.AlarmActivity.extra_instance"
        const val ExtraExecution: String = "eventually.client.activities.AlarmActivity.extra_execution"

        fun Intent.toAlarm(): Alarm? =
            runCatching {
                Alarm(
                    task = requireTask(ExtraTask),
                    instance = requireInstanceId(ExtraInstance),
                    execution = requireInstant(ExtraExecution)
                )
            }.getOrNull()

        private const val RingingCheckInterval: Long = 1000
    }
}
