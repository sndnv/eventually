package eventually.client.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.app.NotificationCompat
import eventually.client.R
import eventually.client.activities.AlarmActivity
import eventually.client.activities.MainActivity
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.receivers.DismissReceiver
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.persistence.notifications.NotificationRepository
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putInstant
import eventually.client.serialization.Extras.putTask
import eventually.client.serialization.Extras.putTaskId
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getPostponeLength
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import java.time.Duration
import java.util.UUID

object NotificationManagerExtensions {
    fun NotificationManager.createInstanceNotificationChannels(context: Context, config: Config = Config.Default) {
        createNotificationChannel(
            config.foregroundServiceChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_foreground_service_name)
            )
        )

        createNotificationChannel(
            config.instanceExecutionChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_instance_execution_name)
            )
        )

        createNotificationChannel(
            config.instanceContextSwitchChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_instance_context_switch_name)
            )
        )

        createNotificationChannel(
            config.instanceExecutionAlarmChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_instance_execution_alarm_name)
            )
        )

        createNotificationChannel(
            config.instanceMissedAlarmChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_instance_missed_alarm_name)
            )
        )
    }

    fun NotificationManager.putInstanceExecutionNotification(
        context: Context,
        task: Task,
        instance: TaskInstance,
        config: Config = Config.Default
    ) {
        val executionTime = instance.execution().formatAsTime(context)

        val title = context.getString(R.string.notification_instance_execution_title, executionTime, task.name)
        val text = executionText(context, task)

        val intent = Intents.createTaskDetailsIntent(context, task, instance)
        val (dismissAction, postponeAction) = Actions.createInstanceActions(context, task, instance)

        val notification = NotificationCompat.Builder(context, config.instanceExecutionChannel.id)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .addAction(dismissAction)
            .addAction(postponeAction)
            .build()

        notify(getInstanceNotificationId(task, instance), notification)
    }

    fun NotificationManager.putInstanceExecutionAlarm(
        context: Context,
        task: Task,
        instance: TaskInstance,
        timeout: Duration,
        config: Config = Config.Default
    ) {
        val executionTime = instance.execution().formatAsTime(context)

        val title = context.getString(R.string.notification_instance_execution_title, executionTime, task.name)
        val text = executionText(context, task)

        val intent = Intents.createAlarmIntent(context, task, instance)
        val (dismissAction, postponeAction) = Actions.createInstanceActions(context, task, instance)

        val notification = NotificationCompat.Builder(context, config.instanceExecutionAlarmChannel.id)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(intent)
            .setFullScreenIntent(intent, true)
            .setTimeoutAfter(timeout.plus(AlarmTimeoutGrace).toMillis())
            .addAction(dismissAction)
            .addAction(postponeAction)
            .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        notify(getInstanceNotificationId(task, instance), notification)
    }

    fun NotificationManager.putInstanceMissedAlarmNotification(
        context: Context,
        task: Task,
        instance: TaskInstance,
        config: Config = Config.Default
    ) {
        val executionTime = instance.execution().formatAsTime(context)

        val title = context.getString(R.string.notification_instance_missed_alarm_title, executionTime, task.name)
        val text = executionText(context, task)

        val intent = Intents.createTaskDetailsIntent(context, task, instance)
        val (dismissAction, postponeAction) = Actions.createInstanceActions(context, task, instance)

        val notification = NotificationCompat.Builder(context, config.instanceMissedAlarmChannel.id)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .addAction(dismissAction)
            .addAction(postponeAction)
            .build()

        notify(getInstanceNotificationId(task, instance), notification)
    }

    fun NotificationManager.replaceInstanceExecutionAlarm(
        context: Context,
        task: Task,
        instance: TaskInstance,
        config: Config = Config.Default
    ) {
        if (isInstanceExecutionAlarmActive(task.id, instance.id, config)) {
            cancel(getInstanceNotificationId(task, instance))
            putInstanceMissedAlarmNotification(context, task, instance, config)
        }
    }

    fun NotificationManager.isInstanceExecutionAlarmActive(
        task: Int,
        instance: UUID,
        config: Config = Config.Default
    ): Boolean {
        val id = getInstanceNotificationId(task, instance)

        return activeNotifications.any { it.id == id && it.notification.channelId == config.instanceExecutionAlarmChannel.id }
    }

    fun NotificationManager.canUseFullScreenAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || canUseFullScreenIntent()

    fun NotificationManager.putInstanceContextSwitchNotification(
        context: Context,
        task: Task,
        instance: TaskInstance,
        config: Config = Config.Default
    ) {
        val executionTime = instance.execution().formatAsTime(context)

        val title = context.getString(R.string.notification_instance_context_switch_title, executionTime, task.name)
        val text = if (task.description.isEmpty()) {
            context.getString(R.string.notification_instance_context_switch_no_description_text, task.goal)
        } else {
            context.getString(R.string.notification_instance_context_switch_text, task.goal, task.description)
        }

        val intent = Intents.createTaskDetailsIntent(context, task, instance)
        val (dismissAction, postponeAction) = Actions.createInstanceActions(context, task, instance)

        val notification = NotificationCompat.Builder(context, config.instanceContextSwitchChannel.id)
            .setSmallIcon(R.drawable.ic_context)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .addAction(dismissAction)
            .addAction(postponeAction)
            .build()

        notify(getInstanceNotificationId(task, instance), notification)
    }

    fun NotificationManager.deleteInstanceNotifications(task: Int, instance: UUID) {
        cancel(getInstanceNotificationId(task, instance))
        cancel(getLegacyInstanceNotificationId(task, instance))
    }

    suspend fun NotificationManager.pruneInstanceNotifications(notificationRepository: NotificationRepository) {
        val active = activeNotifications.map { it.id }.toSet()

        notificationRepository.all()
            .filterNot { notification ->
                getInstanceNotificationId(notification.task, notification.instance) in active ||
                    getLegacyInstanceNotificationId(notification.task, notification.instance) in active
            }
            .forEach { notificationRepository.delete(it.id) }
    }

    fun createForegroundServiceNotification(
        context: Context,
        config: Config = Config.Default
    ): Pair<Int, Notification> {
        val pendingIntent: PendingIntent = Intents.createMainIntent(context).let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val notificationId = -1

        val notification = Notification.Builder(context, config.foregroundServiceChannel.id)
            .setContentTitle(context.getString(R.string.notification_foreground_service_title))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setGroup("eventually.client.scheduling.foreground_service_notification")
            .build()

        return notificationId to notification
    }

    private fun executionText(context: Context, task: Task): String =
        if (task.description.isEmpty()) {
            context.getString(R.string.notification_instance_execution_no_description_text, task.goal)
        } else {
            context.getString(R.string.notification_instance_execution_text, task.goal, task.description)
        }

    private fun getInstanceNotificationId(task: Task, instance: TaskInstance): Int {
        return getInstanceNotificationId(task.id, instance.id)
    }

    fun getInstanceNotificationId(task: Int, instance: UUID): Int {
        return java.util.Objects.hash(task, instance) and Int.MAX_VALUE
    }

    private fun getLegacyInstanceNotificationId(task: Int, instance: UUID): Int {
        return task + instance.hashCode()
    }

    object Actions {
        fun createInstanceActions(
            context: Context,
            task: Task,
            instance: TaskInstance
        ): Pair<NotificationCompat.Action, NotificationCompat.Action> {
            val dismissAction = NotificationCompat.Action(
                R.drawable.ic_dismiss,
                context.getString(R.string.notification_instance_action_dismiss_title),
                Intents.createDismissIntent(context, task, instance)
            )

            val postponeAction = NotificationCompat.Action(
                R.drawable.ic_postpone,
                context.getString(R.string.notification_instance_action_postpone_title),
                Intents.createPostponeIntent(context, task, instance)
            )

            return dismissAction to postponeAction
        }
    }

    object Intents {
        fun createMainIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

        fun createTaskDetailsIntent(context: Context, task: Task, instance: TaskInstance): PendingIntent {
            val intent = createMainIntent(context).apply { putTaskId(MainActivity.ExtraTask, task.id) }

            return PendingIntent.getActivity(
                context,
                getInstanceNotificationId(task, instance) + DetailsActivityRequestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createAlarmIntent(context: Context, task: Task, instance: TaskInstance): PendingIntent {
            val intent = Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putTask(AlarmActivity.ExtraTask, task)
                .putInstanceId(AlarmActivity.ExtraInstance, instance.id)
                .putInstant(AlarmActivity.ExtraExecution, instance.execution())

            return PendingIntent.getActivity(
                context,
                getInstanceNotificationId(task, instance) + AlarmActivityRequestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createFullScreenAlarmSettingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.fromParts("package", context.packageName, null))
            } else {
                null
            }

        fun createDismissIntent(context: Context, task: Task, instance: TaskInstance): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                getInstanceNotificationId(task, instance) + DismissInstanceRequestCode,
                Intent(DismissReceiver.Action).apply {
                    setPackage(context.packageName)
                    putTaskId(DismissReceiver.ExtraTask, task.id)
                    putInstanceId(DismissReceiver.ExtraInstance, instance.id)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createPostponeIntent(context: Context, task: Task, instance: TaskInstance): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                getInstanceNotificationId(task, instance) + PostponeInstanceRequestCode,
                Intent(PostponeReceiver.Action).apply {
                    val by = context.defaultPreferences().getPostponeLength()
                    setPackage(context.packageName)
                    putTaskId(PostponeReceiver.ExtraTask, task.id)
                    putInstanceId(PostponeReceiver.ExtraInstance, instance.id)
                    putDuration(PostponeReceiver.ExtraBy, by)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private const val DetailsActivityRequestCode: Int = 1
        private const val DismissInstanceRequestCode: Int = 2
        private const val PostponeInstanceRequestCode: Int = 3
        private const val AlarmActivityRequestCode: Int = 4
    }

    private val AlarmTimeoutGrace: Duration = Duration.ofMinutes(1)

    data class Config(
        val foregroundServiceChannel: Channel,
        val instanceExecutionChannel: Channel,
        val instanceContextSwitchChannel: Channel,
        val instanceExecutionAlarmChannel: Channel,
        val instanceMissedAlarmChannel: Channel
    ) {
        companion object {
            val Default: Config = Config(
                foregroundServiceChannel = Channel.ForegroundServiceChannel,
                instanceExecutionChannel = Channel.InstanceExecutionChannel,
                instanceContextSwitchChannel = Channel.InstanceContextSwitchChannel,
                instanceExecutionAlarmChannel = Channel.InstanceExecutionAlarmChannel,
                instanceMissedAlarmChannel = Channel.InstanceMissedAlarmChannel
            )
        }

        data class Channel(
            val id: String,
            val importance: Int,
            val light: Int?,
            val vibrationEnabled: Boolean,
            val alarm: Boolean = false
        ) {
            fun toNotificationChannel(name: String): NotificationChannel {
                val channel = NotificationChannel(id, name, importance)

                channel.enableLights(light != null)
                channel.lightColor = light ?: Color.TRANSPARENT
                channel.enableVibration(vibrationEnabled)

                if (alarm) {
                    channel.setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }

                return channel
            }

            companion object Defaults {
                val ForegroundServiceChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_foreground_service",
                    importance = NotificationManager.IMPORTANCE_LOW,
                    light = null,
                    vibrationEnabled = false
                )

                val InstanceExecutionChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_instance_execution",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    light = Color.RED,
                    vibrationEnabled = true
                )

                val InstanceContextSwitchChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_instance_context_switch",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    light = Color.YELLOW,
                    vibrationEnabled = true
                )

                val InstanceExecutionAlarmChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_instance_execution_alarm",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    light = Color.RED,
                    vibrationEnabled = true,
                    alarm = true
                )

                val InstanceMissedAlarmChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_instance_missed_alarm",
                    importance = NotificationManager.IMPORTANCE_DEFAULT,
                    light = Color.RED,
                    vibrationEnabled = false
                )
            }
        }
    }
}
