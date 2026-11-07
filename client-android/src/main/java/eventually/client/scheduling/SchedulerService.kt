package eventually.client.scheduling

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.AndroidEntryPoint
import eventually.client.activities.receivers.DismissReceiver
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.persistence.Converters.Companion.asSchedule
import eventually.client.persistence.notifications.NotificationRepository
import eventually.client.persistence.schedules.TaskScheduleRepository
import eventually.client.persistence.tasks.TaskRepository
import eventually.client.scheduling.NotificationManagerExtensions.pruneInstanceNotifications
import eventually.client.serialization.Extras.requireDuration
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireInstant
import eventually.client.serialization.Extras.requireTask
import eventually.client.serialization.Extras.requireTaskId
import eventually.client.settings.Settings
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getSummaryConfig
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import eventually.core.model.TaskSummaryConfig
import eventually.core.scheduling.SchedulerOps
import kotlinx.coroutines.runBlocking
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SchedulerService : LifecycleService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val binder: SchedulerBinder = SchedulerBinder()

    private lateinit var handler: ServiceHandler
    private lateinit var config: TaskSummaryConfig

    private lateinit var dismissReceiver: DismissReceiver
    private lateinit var postponeReceiver: PostponeReceiver

    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var taskScheduleRepository: TaskScheduleRepository
    @Inject lateinit var notificationRepository: NotificationRepository

    private val providedSchedules: MutableLiveData<Map<Int, TaskSchedule>> = MutableLiveData(emptyMap())
    private val providedSummary: MutableLiveData<TaskSummary> = MutableLiveData(TaskSummary.empty())
    private val providedLastEvaluation: MutableLiveData<Instant> = MutableLiveData(Instant.EPOCH)
    private val providedNextEvaluation: MutableLiveData<Instant> = MutableLiveData(Instant.EPOCH)
    private val providedEvaluations: MutableLiveData<Long> = MutableLiveData(0)

    val schedules: LiveData<Map<Int, TaskSchedule>> = providedSchedules
    val summary: LiveData<TaskSummary> = providedSummary
    val lastEvaluation: LiveData<Instant> = providedLastEvaluation
    val nextEvaluation: LiveData<Instant> = providedNextEvaluation
    val evaluations: LiveData<Long> = providedEvaluations

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        private val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        private val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        private var schedules: Map<Int, TaskSchedule> = emptyMap()
        private var summary: TaskSummary = TaskSummary.empty()

        override fun handleMessage(msg: Message) {
            val notifications = NotificationQueue()

            runBlocking {
                if (msg.obj == PruneNotifications) notificationManager.pruneInstanceNotifications(notificationRepository)

                msg
                    .handle(schedules, taskRepository, taskScheduleRepository)
                    .applyTo(
                        notifications = notifications,
                        taskScheduleRepository = taskScheduleRepository,
                        updateSchedules = { schedules = it },
                        updateSummary = { summary = it }
                    )

                SchedulerOps
                    .evaluate(schedules, SchedulerOps.Message.Evaluate(config))
                    .applyTo(
                        notifications = notifications,
                        taskScheduleRepository = taskScheduleRepository,
                        updateSchedules = { schedules = it },
                        updateSummary = { summary = it }
                    )

                notifications
                    .distinct(notificationRepository)
                    .release(this@SchedulerService, alarmManager, notificationManager, notificationRepository)
            }

            val nextEvaluation =
                notifications.notifications
                    .filterIsInstance<SchedulerOps.Notification.PutEvaluationAlarm>()
                    .minOfOrNull { it.instant }

            this@SchedulerService.providedSchedules.postValue(schedules)
            this@SchedulerService.providedSummary.postValue(summary)
            this@SchedulerService.providedLastEvaluation.postValue(Instant.now())
            this@SchedulerService.providedNextEvaluation.postValue(nextEvaluation ?: Instant.EPOCH)
            this@SchedulerService.providedEvaluations.postValue((providedEvaluations.value ?: 0) + 1)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startResult = super.onStartCommand(intent, flags, startId)

        handler.obtainMessage().let { msg ->
            val obj = intent.toSchedulerMessage(
                requireConfig = ::requireConfig,
                reloadData = ::load,
                pruneNotifications = ::prune
            )

            obj?.let {
                msg.obj = it
                handler.sendMessage(msg)
            }
        }

        return startResult
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent) // result discarded; always null
        return binder
    }

    override fun onSharedPreferenceChanged(updated: SharedPreferences, key: String?) {
        if (key in Settings.Keys.SummaryConfig) {
            config = updated.getSummaryConfig()
            handler.obtainMessage().let { msg ->
                msg.obj = SchedulerOps.Message.Evaluate(config)
                handler.sendMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val (id, notification) = NotificationManagerExtensions.createForegroundServiceNotification(this)
        startForeground(id, notification)

        HandlerThread("SchedulerService", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            handler = ServiceHandler(looper)

            if (!::config.isInitialized) {
                val preferences = this@SchedulerService.defaultPreferences()
                config = preferences.getSummaryConfig()

                preferences.registerOnSharedPreferenceChangeListener(this@SchedulerService)

                load()
            }
        }

        dismissReceiver = DismissReceiver()
        postponeReceiver = PostponeReceiver()

        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            dismissReceiver.intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            postponeReceiver,
            postponeReceiver.intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(dismissReceiver)
        unregisterReceiver(postponeReceiver)
    }

    private fun requireConfig(): TaskSummaryConfig {
        require(::config.isInitialized) { "Scheduler service must be initialized first" }
        return config
    }

    private fun load() {
        handler.obtainMessage().let { msg ->
            msg.obj = Load
            handler.sendMessage(msg)
        }
    }

    private fun prune() {
        handler.obtainMessage().let { msg ->
            msg.obj = PruneNotifications
            handler.sendMessage(msg)
        }
    }

    inner class SchedulerBinder : Binder() {
        val service: SchedulerService = this@SchedulerService
    }

    data object Load

    data object PruneNotifications

    companion object {
        const val ActionPut: String = "eventually.client.scheduling.SchedulerService.Put"
        const val ActionPutExtraTask: String = "eventually.client.scheduling.SchedulerService.Put.extra_task"

        const val ActionDelete: String = "eventually.client.scheduling.SchedulerService.Delete"
        const val ActionDeleteExtraTask: String = "eventually.client.scheduling.SchedulerService.Delete.extra_task"

        const val ActionDismiss: String = "eventually.client.scheduling.SchedulerService.Dismiss"
        const val ActionDismissExtraTask: String = "eventually.client.scheduling.SchedulerService.Dismiss.extra_task"
        const val ActionDismissExtraInstance: String =
            "eventually.client.scheduling.SchedulerService.Dismiss.extra_instance"

        const val ActionUndoDismiss: String = "eventually.client.scheduling.SchedulerService.UndoDismiss"
        const val ActionUndoDismissExtraTask: String =
            "eventually.client.scheduling.SchedulerService.UndoDismiss.extra_task"
        const val ActionUndoDismissExtraInstant: String =
            "eventually.client.scheduling.SchedulerService.UndoDismiss.extra_instant"

        const val ActionPostpone: String = "eventually.client.scheduling.SchedulerService.Postpone"
        const val ActionPostponeExtraTask: String = "eventually.client.scheduling.SchedulerService.Postpone.extra_task"
        const val ActionPostponeExtraInstance: String =
            "eventually.client.scheduling.SchedulerService.Postpone.extra_instance"
        const val ActionPostponeExtraBy: String = "eventually.client.scheduling.SchedulerService.Postpone.extra_by"

        const val ActionEvaluate: String = "eventually.client.scheduling.SchedulerService.Evaluate"
        const val ActionReload: String = "eventually.client.scheduling.SchedulerService.Reload"
        const val ActionPruneNotifications: String = "eventually.client.scheduling.SchedulerService.PruneNotifications"

        fun Intent?.toSchedulerMessage(
            requireConfig: () -> TaskSummaryConfig,
            reloadData: () -> Unit,
            pruneNotifications: () -> Unit
        ): SchedulerOps.Message? =
            when (this?.action) {
                ActionPut -> {
                    requireConfig()

                    val task = requireTask(ActionPutExtraTask)
                    SchedulerOps.Message.Put(task)
                }

                ActionDelete -> {
                    requireConfig()

                    val task = requireTaskId(ActionDeleteExtraTask)
                    SchedulerOps.Message.Delete(task)
                }

                ActionDismiss -> {
                    requireConfig()

                    val task = requireTaskId(ActionDismissExtraTask)
                    val instance = requireInstanceId(ActionDismissExtraInstance)
                    SchedulerOps.Message.Dismiss(task, instance)
                }

                ActionUndoDismiss -> {
                    requireConfig()

                    val task = requireTaskId(ActionUndoDismissExtraTask)
                    val instant = requireInstant(ActionUndoDismissExtraInstant)
                    SchedulerOps.Message.UndoDismiss(task, instant)
                }

                ActionPostpone -> {
                    requireConfig()

                    val task = requireTaskId(ActionPostponeExtraTask)
                    val instance = requireInstanceId(ActionPostponeExtraInstance)
                    val by = requireDuration(ActionPostponeExtraBy)
                    SchedulerOps.Message.Postpone(task, instance, by)
                }

                ActionEvaluate -> {
                    val config = requireConfig()

                    SchedulerOps.Message.Evaluate(config)
                }

                ActionReload -> {
                    requireConfig()

                    reloadData()
                    null
                }

                ActionPruneNotifications -> {
                    requireConfig()

                    pruneNotifications()
                    null
                }

                null -> null

                else -> throw IllegalArgumentException("Unexpected action encountered: [$action]")
            }

        suspend fun loadInit(
            taskRepository: TaskRepository,
            taskScheduleRepository: TaskScheduleRepository
        ): SchedulerOps.Message.Init {
            val tasks = taskRepository.all()
            val schedulesWithTasks = taskScheduleRepository.all().mapNotNull { schedule ->
                tasks.find { it.id == schedule.task }?.let { schedule.asSchedule(it) }
            }

            return SchedulerOps.Message.Init(tasks, schedulesWithTasks)
        }

        suspend fun Message.handle(
            schedules: Map<Int, TaskSchedule>,
            taskRepository: TaskRepository,
            taskScheduleRepository: TaskScheduleRepository
        ): SchedulerOps.SchedulerResult? =
            when (val message = obj) {
                Load -> SchedulerOps.init(loadInit(taskRepository, taskScheduleRepository))
                PruneNotifications -> null

                is SchedulerOps.Message.Put -> {
                    val taskId = taskRepository.put(message.task)
                    SchedulerOps.put(schedules, message.copy(task = message.task.copy(id = taskId.toInt())))
                }

                is SchedulerOps.Message.Delete -> {
                    taskRepository.delete(message.task)
                    SchedulerOps.delete(schedules, message)
                }

                is SchedulerOps.Message.Dismiss -> SchedulerOps.dismiss(schedules, message)
                is SchedulerOps.Message.UndoDismiss -> SchedulerOps.undoDismiss(schedules, message)
                is SchedulerOps.Message.Postpone -> SchedulerOps.postpone(schedules, message)
                is SchedulerOps.Message.Evaluate -> null
                is SchedulerOps.Message.Init -> SchedulerOps.init(message)
                else -> throw IllegalArgumentException("Unexpected message encountered: [$message]")
            }

        suspend fun SchedulerOps.SchedulerResult?.applyTo(
            notifications: NotificationQueue,
            taskScheduleRepository: TaskScheduleRepository,
            updateSchedules: (Map<Int, TaskSchedule>) -> Unit,
            updateSummary: (TaskSummary) -> Unit
        ) {
            this?.let {
                updateSchedules(schedules)

                summary?.let { updateSummary(it) }

                notifications.enqueue(this.notifications)

                affectedSchedules.forEach {
                    when (val schedule = schedules[it]) {
                        null -> taskScheduleRepository.delete(it)
                        else -> taskScheduleRepository.put(schedule)
                    }
                }
            }
        }
    }
}
