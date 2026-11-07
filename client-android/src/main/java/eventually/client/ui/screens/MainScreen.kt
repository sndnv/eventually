package eventually.client.ui.screens

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eventually.client.R
import eventually.client.settings.Settings
import eventually.client.settings.Settings.getDateTimeFormat
import eventually.client.settings.Settings.getFirstDayOfWeek
import eventually.client.settings.Settings.getStatsEnabled
import eventually.client.settings.Settings.getSummaryMaxTasks
import eventually.client.settings.Settings.getSummarySize
import eventually.client.ui.LayoutExtensions.isLandscape
import eventually.client.ui.components.AppTitle
import eventually.client.ui.components.AppTopBar
import eventually.client.ui.screens.MainSettings.Companion.toMainSettings
import eventually.client.ui.screens.main.TaskCalendarPage
import eventually.client.ui.screens.main.TaskListPage
import eventually.client.ui.screens.main.TaskSummaryPage
import eventually.core.model.Task
import eventually.core.model.TaskSchedule
import eventually.core.model.TaskSummary
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant

data class MainSettings(
    val summarySize: Duration,
    val summaryMaxTasks: Int,
    val dateTimeFormat: Settings.DateTimeFormat,
    val firstDayOfWeek: DayOfWeek,
    val statsEnabled: Boolean
) {
    companion object {
        fun SharedPreferences.toMainSettings(): MainSettings = MainSettings(
            summarySize = getSummarySize(),
            summaryMaxTasks = getSummaryMaxTasks(),
            dateTimeFormat = getDateTimeFormat(),
            firstDayOfWeek = getFirstDayOfWeek(),
            statsEnabled = getStatsEnabled()
        )
    }
}

class MainActions(
    val createTask: () -> Unit,
    val createTaskOn: (Instant) -> Unit,
    val openTask: (Int) -> Unit,
    val openSettings: () -> Unit,
    val openStats: () -> Unit,
    val openAbout: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
    val evaluate: () -> Unit
)

enum class MainPage(@DrawableRes val icon: Int, @StringRes val title: Int) {
    Summary(R.drawable.ic_summary, R.string.main_top_app_bar_subtitle_summary),
    Tasks(R.drawable.ic_list, R.string.main_top_app_bar_subtitle_list),
    Calendar(R.drawable.ic_calendar, R.string.main_top_app_bar_subtitle_calendar)
}

@Composable
fun MainScreen(
    tasks: List<Task>?,
    summary: TaskSummary,
    schedules: Map<Int, TaskSchedule>,
    settings: MainSettings,
    exactAlarmPrompt: Boolean,
    onExactAlarmPromptShown: () -> Unit,
    actions: MainActions
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { MainPage.entries.size })
    val page = MainPage.entries[pagerState.currentPage]
    val snackbarHostState = remember { SnackbarHostState() }
    val currentActions by rememberUpdatedState(actions)
    val currentOnExactAlarmPromptShown by rememberUpdatedState(onExactAlarmPromptShown)

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val closeSearch = {
        searching = false
        query = ""
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { MainPage.entries[pagerState.settledPage] }.drop(1).collect { current ->
            if (current != MainPage.Tasks) closeSearch()
            if (current == MainPage.Summary) currentActions.evaluate()
        }
    }

    val promptMessage = stringResource(R.string.exact_alarm_prompt_message)
    val promptAction = stringResource(R.string.exact_alarm_prompt_action)

    val currentExactAlarmPrompt by rememberUpdatedState(exactAlarmPrompt)

    LaunchedEffect(snackbarHostState) {
        snapshotFlow { currentExactAlarmPrompt }.filter { it }.collect {
            currentOnExactAlarmPromptShown()

            val result = snackbarHostState.showSnackbar(
                message = promptMessage,
                actionLabel = promptAction,
                duration = SnackbarDuration.Long
            )

            if (result == SnackbarResult.ActionPerformed) currentActions.openExactAlarmSettings()
        }
    }

    BackHandler(enabled = searching) { closeSearch() }

    val select: (MainPage) -> Unit = { scope.launch { pagerState.animateScrollToPage(it.ordinal) } }

    BoxWithConstraints {
        val landscape = isLandscape

        Scaffold(
            topBar = {
                if (searching) {
                    SearchTopBar(query = query, onQueryChange = { query = it }, onClose = closeSearch)
                } else {
                    MainTopBar(
                        page = page,
                        statsEnabled = settings.statsEnabled,
                        onSearch = { searching = true },
                        actions = actions
                    )
                }
            },
            bottomBar = {
                if (!landscape) MainTabs(selected = page, onSelect = select)
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (landscape) 0.dp else padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding()
                    )
            ) {
                MainRail(visible = landscape, selected = page, onSelect = select)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = padding.calculateEndPadding(layoutDirection), bottom = padding.calculateBottomPadding())
                ) { index ->
                    when (MainPage.entries[index]) {
                        MainPage.Summary -> TaskSummaryPage(
                            summary = summary,
                            schedules = schedules,
                            summarySize = settings.summarySize,
                            maxTasks = settings.summaryMaxTasks,
                            dateTimeFormat = settings.dateTimeFormat,
                            onOpenTask = actions.openTask
                        )

                        MainPage.Tasks -> TaskListPage(tasks = tasks, query = query, onOpenTask = actions.openTask)

                        MainPage.Calendar -> TaskCalendarPage(
                            schedules = schedules,
                            firstDayOfWeek = settings.firstDayOfWeek,
                            dateTimeFormat = settings.dateTimeFormat,
                            landscape = landscape,
                            onOpenTask = actions.openTask,
                            onCreateTask = actions.createTaskOn
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun rememberMainSettings(preferences: SharedPreferences): MainSettings {
    var settings by remember(preferences) { mutableStateOf(preferences.toMainSettings()) }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { updated, _ ->
            settings = updated.toMainSettings()
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return settings
}

@Composable
private fun MainTopBar(page: MainPage, statsEnabled: Boolean, onSearch: () -> Unit, actions: MainActions) {
    var menuExpanded by remember { mutableStateOf(false) }

    AppTopBar(
        title = { AppTitle(subtitle = stringResource(page.title)) },
        actions = {
            if (page == MainPage.Tasks) {
                IconButton(onClick = onSearch) {
                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_hint))
                }
            }

            IconButton(onClick = actions.createTask) {
                Icon(
                    painter = painterResource(R.drawable.ic_list_add),
                    contentDescription = stringResource(R.string.create_task_hint)
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(painter = painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.main_menu_hint))
                }

                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    val select = { action: () -> Unit ->
                        menuExpanded = false
                        action()
                    }

                    MenuItem(title = R.string.settings_hint, onClick = { select(actions.openSettings) })

                    if (statsEnabled) {
                        MenuItem(title = R.string.stats_hint, onClick = { select(actions.openStats) })
                    }

                    MenuItem(title = R.string.about_hint, onClick = { select(actions.openAbout) })
                }
            }
        }
    )
}

@Composable
private fun MenuItem(@StringRes title: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text = stringResource(title)) }, onClick = onClick)
}

@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val contentColor = MaterialTheme.colorScheme.onPrimary

    AppTopBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor,
                    cursorColor = contentColor,
                    focusedPlaceholderColor = contentColor.copy(alpha = PlaceholderAlpha),
                    unfocusedPlaceholderColor = contentColor.copy(alpha = PlaceholderAlpha)
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.search_close_hint))
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painter = painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear_hint))
                }
            }
        }
    )

    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

@Composable
private fun MainTabs(selected: MainPage, onSelect: (MainPage) -> Unit) {
    val containerColor = colorResource(R.color.primary_dark)

    Box(
        modifier = Modifier
            .background(containerColor)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        PrimaryTabRow(
            selectedTabIndex = selected.ordinal,
            containerColor = containerColor,
            contentColor = Color.White,
            divider = {}
        ) {
            MainPage.entries.forEach { page ->
                Tab(
                    selected = page == selected,
                    onClick = { onSelect(page) },
                    icon = { Icon(painter = painterResource(page.icon), contentDescription = stringResource(page.title)) },
                    unselectedContentColor = Color.White.copy(alpha = UnselectedTabAlpha)
                )
            }
        }
    }
}

@Composable
private fun MainRail(visible: Boolean, selected: MainPage, onSelect: (MainPage) -> Unit) {
    if (!visible) return

    NavigationRail(
        containerColor = colorResource(R.color.primary_dark),
        contentColor = Color.White,
        windowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime).only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)
    ) {
        val colors = NavigationRailItemDefaults.colors(
            selectedIconColor = Color.White,
            indicatorColor = colorResource(R.color.primary),
            unselectedIconColor = Color.White.copy(alpha = UnselectedTabAlpha)
        )

        MainPage.entries.forEach { page ->
            NavigationRailItem(
                selected = page == selected,
                onClick = { onSelect(page) },
                icon = { Icon(painter = painterResource(page.icon), contentDescription = stringResource(page.title)) },
                colors = colors
            )
        }
    }
}

private const val PlaceholderAlpha: Float = 0.7f
private const val UnselectedTabAlpha: Float = 0.6f
