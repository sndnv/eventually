package eventually.client.activities

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import eventually.client.BuildConfig
import eventually.client.R
import eventually.client.activities.helpers.TaskManagement
import eventually.client.persistence.DataExport
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.client.persistence.schedules.TaskScheduleViewModel
import eventually.client.persistence.tasks.TaskViewModel
import eventually.client.scheduling.AlarmManagerExtensions.canUseExactAlarms
import eventually.client.scheduling.NotificationManagerExtensions.createInstanceNotificationChannels
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.requireTaskId
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getDateTimeFormat
import eventually.client.settings.Settings.getFirstDayOfWeek
import eventually.client.settings.Settings.getPostponeLength
import eventually.client.settings.Settings.getShowAllInstances
import eventually.client.ui.navigation.Route
import eventually.client.ui.navigation.Route.Companion.onTop
import eventually.client.ui.navigation.Route.Companion.openTask
import eventually.client.ui.screens.AboutScreen
import eventually.client.ui.screens.MainActions
import eventually.client.ui.screens.MainScreen
import eventually.client.ui.screens.NewTaskScreen
import eventually.client.ui.screens.SettingsScreen
import eventually.client.ui.screens.Stats
import eventually.client.ui.screens.StatsScreen
import eventually.client.ui.screens.TaskDetailsActions
import eventually.client.ui.screens.TaskDetailsScreen
import eventually.client.ui.screens.rememberMainSettings
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val taskViewModel: TaskViewModel by viewModels()
    private val taskScheduleViewModel: TaskScheduleViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

    private val preferences by lazy { defaultPreferences() }

    private var service: SchedulerService? by mutableStateOf(null)

    private var exactAlarmPrompt: Boolean by mutableStateOf(false)

    private var exactAlarmPromptShown: Boolean = false

    private var requestedTask: Int? by mutableStateOf(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            this@MainActivity.service = (service as SchedulerService.SchedulerBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            this@MainActivity.service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exactAlarmPromptShown = savedInstanceState?.getBoolean(StateExactAlarmPromptShown) ?: false

        if (savedInstanceState == null) {
            requestedTask = intent.requestedTask()
        }

        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        setContent {
            EventuallyTheme {
                val backStack = rememberNavBackStack(Route.Main)

                LaunchedEffect(requestedTask) {
                    requestedTask?.let { task ->
                        backStack.openTask(task)
                        requestedTask = null
                    }
                }

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = { ScreenTransition },
                    popTransitionSpec = { ScreenTransition },
                    entryProvider = entryProvider {
                        entry<Route.Main> { MainEntry(backStack = backStack) }
                        entry<Route.NewTask> { route -> NewTaskEntry(route = route, backStack = backStack) }
                        entry<Route.TaskDetails> { route -> TaskDetailsEntry(route = route, backStack = backStack) }
                        entry<Route.Settings> { route ->
                            SettingsScreen(preferences = preferences, onBack = { backStack.onTop(route, ::back) })
                        }
                        entry<Route.Stats> { route -> StatsEntry(route = route, backStack = backStack) }
                        entry<Route.About> { AboutScreen(version = BuildConfig.VERSION_NAME) }
                    }
                )
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createInstanceNotificationChannels(applicationContext)

        if (savedInstanceState == null) {
            requestNotificationPermissionIfNeeded()
        }

        startForegroundService(Intent(this, SchedulerService::class.java))
        evaluate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.requestedTask()?.let { requestedTask = it }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SchedulerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()
        promptForExactAlarmsIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(StateExactAlarmPromptShown, exactAlarmPromptShown)
    }

    @Composable
    private fun MainEntry(backStack: MutableList<NavKey>) {
        val tasks by taskViewModel.tasks.observeAsState()
        val summary by (service?.summary ?: NoSummary).observeAsState()
        val schedules by (service?.schedules ?: NoSchedules).observeAsState()

        val actions = remember(backStack) {
            val push = { route: Route -> backStack.onTop(Route.Main) { backStack.add(route) } }

            MainActions(
                createTask = { push(Route.NewTask()) },
                createTaskOn = { date -> push(Route.NewTask(date = date.toEpochMilli())) },
                openTask = { task -> push(Route.TaskDetails(task)) },
                openSettings = { push(Route.Settings) },
                openStats = { push(Route.Stats) },
                openAbout = { push(Route.About) },
                openExactAlarmSettings = ::openExactAlarmSettings,
                evaluate = ::evaluate
            )
        }

        MainScreen(
            tasks = tasks,
            summary = summary ?: TaskSummary.empty(),
            schedules = schedules ?: emptyMap(),
            settings = rememberMainSettings(preferences),
            exactAlarmPrompt = exactAlarmPrompt,
            onExactAlarmPromptShown = {
                exactAlarmPromptShown = true
                exactAlarmPrompt = false
            },
            actions = actions
        )
    }

    @Composable
    private fun NewTaskEntry(route: Route.NewTask, backStack: MutableList<NavKey>) {
        val goals by produceState(initialValue = emptyList<String>()) { value = taskViewModel.goals() }
        val default = remember(route) { route.date?.takeIf { it > 0 }?.let(Instant::ofEpochMilli) ?: Instant.now() }

        NewTaskScreen(
            goals = goals,
            default = default,
            dateTimeFormat = preferences.getDateTimeFormat(),
            firstDayOfWeek = preferences.getFirstDayOfWeek(),
            onCreate = { task ->
                if (backStack.remove(route)) createTask(task)
            },
            onBack = { backStack.onTop(route, ::back) }
        )
    }

    @Composable
    private fun TaskDetailsEntry(route: Route.TaskDetails, backStack: MutableList<NavKey>) {
        val tasks by taskViewModel.tasks.observeAsState()
        val schedules by (service?.schedules ?: NoSchedules).observeAsState()

        val loaded = tasks != null && schedules != null

        val actions = remember(route, backStack) {
            TaskDetailsActions(
                update = ::updateTask,
                delete = { task ->
                    if (backStack.removeAll { it is Route.TaskDetails && it.task == task.id }) deleteTask(task)
                },
                dismiss = { instance -> dismissInstance(route.task, instance) },
                postpone = { instance -> postponeInstance(route.task, instance) },
                undo = { instant -> TaskManagement.undoDismissTaskInstance(this, route.task, instant) }
            )
        }

        TaskDetailsScreen(
            task = if (loaded) tasks?.firstOrNull { it.id == route.task } else null,
            schedule = schedules?.get(route.task),
            goals = tasks.orEmpty().map { it.goal },
            dateTimeFormat = preferences.getDateTimeFormat(),
            firstDayOfWeek = preferences.getFirstDayOfWeek(),
            showAllInstances = preferences.getShowAllInstances(),
            actions = actions,
            onBack = { backStack.onTop(route, ::back) }
        )
    }

    @Composable
    private fun StatsEntry(route: Route.Stats, backStack: MutableList<NavKey>) {
        val tasks by taskViewModel.tasks.observeAsState()
        val schedules by taskScheduleViewModel.schedules.observeAsState()
        val notifications by notificationViewModel.notifications.observeAsState()
        val lastEvaluation by (service?.lastEvaluation ?: NoEvaluation).observeAsState()
        val nextEvaluation by (service?.nextEvaluation ?: NoEvaluation).observeAsState()
        val evaluations by (service?.evaluations ?: NoEvaluations).observeAsState()

        StatsScreen(
            stats = Stats(
                tasks = tasks,
                schedules = schedules,
                notifications = notifications,
                lastEvaluation = lastEvaluation,
                nextEvaluation = nextEvaluation,
                evaluations = evaluations
            ),
            onImport = ::importData,
            onForceEvaluation = ::evaluate,
            onBack = { backStack.onTop(route, ::back) }
        )
    }

    private fun back() {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun createTask(task: Task) {
        TaskManagement.putTask(this, task)
        Toast.makeText(this, getString(R.string.toast_task_created), Toast.LENGTH_SHORT).show()
    }

    private fun updateTask(task: Task) {
        TaskManagement.putTask(this, task)
        Toast.makeText(this, getString(R.string.toast_task_updated), Toast.LENGTH_SHORT).show()
    }

    private fun deleteTask(task: Task) {
        TaskManagement.deleteTask(this, task.id)
        Toast.makeText(this, getString(R.string.toast_task_deleted), Toast.LENGTH_SHORT).show()
    }

    private fun dismissInstance(task: Int, instance: TaskInstance) {
        getSystemService(NotificationManager::class.java).deleteInstanceNotifications(task, instance.id)
        TaskManagement.dismissTaskInstance(this, task, instance.id)
    }

    private fun postponeInstance(task: Int, instance: TaskInstance) {
        getSystemService(NotificationManager::class.java).deleteInstanceNotifications(task, instance.id)
        TaskManagement.postponeTaskInstance(this, task, instance.id, preferences.getPostponeLength())
    }

    private fun importData(data: DataExport) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                data.tasks.forEach { taskViewModel.put(it) }
                data.schedules.forEach { taskScheduleViewModel.put(it) }

                val intent = Intent(applicationContext, SchedulerService::class.java)
                intent.action = SchedulerService.ActionReload

                applicationContext.startService(intent)
            }
        }
    }

    private fun evaluate() {
        val intent = Intent(this@MainActivity, SchedulerService::class.java)
        intent.action = SchedulerService.ActionEvaluate

        startService(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun promptForExactAlarmsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (exactAlarmPromptShown) return

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canUseExactAlarms()) return

        exactAlarmPrompt = true
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        const val ExtraTask: String = "eventually.client.activities.MainActivity.extra_task"

        fun Intent.requestedTask(): Int? =
            runCatching {
                val fromHistory = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
                if (!fromHistory && hasExtra(ExtraTask)) requireTaskId(ExtraTask) else null
            }.getOrNull()

        private const val StateExactAlarmPromptShown: String =
            "eventually.client.activities.MainActivity.state_exact_alarm_prompt_shown"

        private val ScreenTransition: ContentTransform
            get() = fadeIn(tween()) togetherWith fadeOut(tween())

        private val NoSummary: LiveData<TaskSummary> = MutableLiveData()
        private val NoSchedules: LiveData<Map<Int, TaskSchedule>> = MutableLiveData()
        private val NoEvaluation: LiveData<Instant> = MutableLiveData()
        private val NoEvaluations: LiveData<Long> = MutableLiveData()
    }
}
