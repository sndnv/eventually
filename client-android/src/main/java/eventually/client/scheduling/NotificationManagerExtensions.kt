package eventually.client.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import eventually.client.R
import eventually.client.activities.TaskDetailsActivity
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.receivers.DismissReceiver
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putTaskId
import eventually.client.settings.Settings.getPostponeLength
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import java.util.UUID

object NotificationManagerExtensions {
    fun NotificationManager.createInstanceNotificationChannels(context: Context, config: Config = Config.Default) {
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
    }

    fun NotificationManager.putInstanceExecutionNotification(
        context: Context,
        task: Task,
        instance: TaskInstance,
        config: Config = Config.Default
    ) {
        val executionTime = instance.execution().formatAsTime(context)

        val title = context.getString(R.string.notification_instance_execution_title, executionTime, task.name)
        val text = context.getString(R.string.notification_instance_execution_text, task.goal, task.description)

        val intent = Intents.createTaskDetailsIntent(context, task)
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

    fun NotificationManager.putInstanceContextSwitchNotification(
        context: Context,
        task: Task,
        instance: TaskInstance,
        config: Config = Config.Default
    ) {
        val executionTime = instance.execution().formatAsTime(context)

        val title = context.getString(R.string.notification_instance_context_switch_title, executionTime, task.name)
        val text = context.getString(R.string.notification_instance_context_switch_text, task.goal, task.description)

        val intent = Intents.createTaskDetailsIntent(context, task)
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
    }

    private fun getInstanceNotificationId(task: Task, instance: TaskInstance): Int {
        return getInstanceNotificationId(task.id, instance.id)
    }

    private fun getInstanceNotificationId(task: Int, instance: UUID): Int {
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
        fun createTaskDetailsIntent(context: Context, task: Task): PendingIntent {
            val intent = Intent(
                context,
                TaskDetailsActivity::class.java
            ).apply { putTaskId(TaskDetailsActivity.ExtraTask, task.id) }

            return PendingIntent.getActivity(
                context,
                DetailsActivityRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createDismissIntent(context: Context, task: Task, instance: TaskInstance): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                DetailsActivityRequestCode,
                Intent(DismissReceiver.Action).apply {
                    putTaskId(DismissReceiver.ExtraTask, task.id)
                    putInstanceId(DismissReceiver.ExtraInstance, instance.id)
                },
                PendingIntent.FLAG_ONE_SHOT
            )
        }

        fun createPostponeIntent(context: Context, task: Task, instance: TaskInstance): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                DetailsActivityRequestCode,
                Intent(PostponeReceiver.Action).apply {
                    val by = PreferenceManager.getDefaultSharedPreferences(context).getPostponeLength()
                    putTaskId(PostponeReceiver.ExtraTask, task.id)
                    putInstanceId(PostponeReceiver.ExtraInstance, instance.id)
                    putDuration(PostponeReceiver.ExtraBy, by)
                },
                PendingIntent.FLAG_ONE_SHOT
            )
        }

        private const val DetailsActivityRequestCode: Int = 1
    }

    data class Config(
        val instanceExecutionChannel: Channel,
        val instanceContextSwitchChannel: Channel
    ) {
        companion object {
            val Default: Config = Config(
                instanceExecutionChannel = Channel.InstanceExecutionChannel,
                instanceContextSwitchChannel = Channel.InstanceContextSwitchChannel
            )
        }

        data class Channel(
            val id: String,
            val importance: Int,
            val light: Int?,
            val vibrationEnabled: Boolean
        ) {
            fun toNotificationChannel(name: String): NotificationChannel {
                val channel = NotificationChannel(id, name, importance)

                channel.enableLights(light != null)
                channel.lightColor = light ?: Color.TRANSPARENT
                channel.enableVibration(vibrationEnabled)

                return channel
            }

            companion object Defaults {
                val InstanceExecutionChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_instance_execution",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    light = Color.RED,
                    vibrationEnabled = true
                )

                val InstanceContextSwitchChannel: Channel = Channel(
                    id = "eventually.client.scheduling.notification_channel_instance_context_switch",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    light = Color.BLUE,
                    vibrationEnabled = true
                )
            }
        }
    }
}
