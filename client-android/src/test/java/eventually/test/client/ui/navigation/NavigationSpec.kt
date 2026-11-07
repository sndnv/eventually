package eventually.test.client.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import eventually.client.settings.Settings
import eventually.client.ui.navigation.Route
import eventually.client.ui.navigation.Route.Companion.onTop
import eventually.client.ui.navigation.Route.Companion.openTask
import eventually.client.ui.screens.TaskDetailsActions
import eventually.client.ui.screens.TaskDetailsScreen
import eventually.client.ui.theme.EventuallyTheme
import eventually.core.model.Task
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class NavigationSpec {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openTasksOnce() {
        val backStack = mutableListOf<NavKey>(Route.Main)

        backStack.openTask(1)
        backStack.openTask(1)

        assertThat(backStack.toList(), equalTo(listOf(Route.Main, Route.TaskDetails(1))))

        backStack.openTask(2)
        backStack.openTask(1)

        assertThat(backStack.toList(), equalTo(listOf(Route.Main, Route.TaskDetails(2), Route.TaskDetails(1))))
    }

    @Test
    fun actOnlyWhileRoutesAreOnTop() {
        val backStack = mutableListOf<NavKey>(Route.Main)
        val push = { route: Route -> backStack.onTop(Route.Main) { backStack.add(route) } }

        push(Route.NewTask())
        push(Route.NewTask())
        push(Route.TaskDetails(1))
        push(Route.Settings)

        assertThat(backStack.toList(), equalTo(listOf(Route.Main, Route.NewTask())))

        var calls = 0
        backStack.onTop(Route.NewTask(date = 42L)) { calls++ }
        backStack.onTop(Route.NewTask()) { calls++ }

        assertThat(calls, equalTo(1))
    }

    @Test
    fun ignoreRepeatedBackFromScreensNoLongerOnTop() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var backStack: NavBackStack<NavKey>

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            backStack = rememberNavBackStack(Route.Main, Route.TaskDetails(task.id))
            Display(backStack = backStack, dispatcher = dispatcher)
        }

        composeRule.onNodeWithContentDescription("Edit Task").assertExists()

        val back = { backStack.onTop(Route.TaskDetails(task.id)) { dispatcher.onBackPressed() } }

        composeRule.runOnUiThread { back() }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { back() }

        composeRule.onNodeWithText("main").assertExists()
        composeRule.runOnIdle {
            assertThat(backStack.toList(), equalTo(listOf<NavKey>(Route.Main)))
            assertThat(composeRule.activity.isFinishing, equalTo(false))
        }
    }

    @Test
    fun finishWhenUnguardedBackIsRepeated() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var backStack: NavBackStack<NavKey>

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            backStack = rememberNavBackStack(Route.Main, Route.TaskDetails(task.id))
            Display(backStack = backStack, dispatcher = dispatcher)
        }

        composeRule.onNodeWithContentDescription("Edit Task").assertExists()

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        composeRule.runOnIdle {
            assertThat(backStack.toList(), equalTo(listOf<NavKey>(Route.Main)))
            assertThat(composeRule.activity.isFinishing, equalTo(true))
        }
    }

    @Test
    fun restoreBackStackWhenRecreated() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var backStack: NavBackStack<NavKey>

        restorationTester.setContent {
            backStack = rememberNavBackStack(Route.Main)
        }

        val expected = listOf(
            Route.Main,
            Route.NewTask(date = 42L),
            Route.NewTask(),
            Route.TaskDetails(task = 7),
            Route.Settings,
            Route.Stats,
            Route.About
        )

        composeRule.runOnIdle { backStack.addAll(expected.drop(1)) }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle { assertThat(backStack.toList(), equalTo(expected)) }
    }

    @Test
    fun popEntriesOnBack() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var backStack: NavBackStack<NavKey>

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            backStack = rememberNavBackStack(Route.Main, Route.About)
            Display(backStack = backStack, dispatcher = dispatcher)
        }

        composeRule.onNodeWithText("about").assertExists()

        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        composeRule.onNodeWithText("main").assertExists()
        composeRule.runOnIdle { assertThat(backStack.toList(), equalTo(listOf<NavKey>(Route.Main))) }
    }

    @Test
    fun popEntriesOnTopBarBack() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var backStack: NavBackStack<NavKey>

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            backStack = rememberNavBackStack(Route.Main, Route.TaskDetails(task.id))
            Display(backStack = backStack, dispatcher = dispatcher)
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("main").assertExists()
        composeRule.runOnIdle { assertThat(backStack.toList(), equalTo(listOf<NavKey>(Route.Main))) }
    }

    @Test
    fun letScreensHandleBackFirst() {
        lateinit var dispatcher: OnBackPressedDispatcher
        lateinit var backStack: NavBackStack<NavKey>

        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            backStack = rememberNavBackStack(Route.Main, Route.TaskDetails(task.id))
            Display(backStack = backStack, dispatcher = dispatcher)
        }

        composeRule.onNodeWithContentDescription("Edit Task").performClick()
        composeRule.onNodeWithText("Update").assertExists()

        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        composeRule.onNodeWithText("Update").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Edit Task").assertExists()
        composeRule.runOnIdle { assertThat(backStack.size, equalTo(2)) }

        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        composeRule.onNodeWithText("main").assertExists()
        composeRule.runOnIdle { assertThat(backStack.toList(), equalTo(listOf<NavKey>(Route.Main))) }
    }

    @Composable
    private fun Display(backStack: NavBackStack<NavKey>, dispatcher: OnBackPressedDispatcher) {
        EventuallyTheme {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Route.Main> { Text(text = "main") }
                    entry<Route.About> { Text(text = "about") }
                    entry<Route.TaskDetails> {
                        TaskDetailsScreen(
                            task = task,
                            schedule = null,
                            goals = listOf(task.goal),
                            dateTimeFormat = Settings.DateTimeFormat.Iso,
                            firstDayOfWeek = DayOfWeek.MONDAY,
                            showAllInstances = false,
                            actions = TaskDetailsActions(
                                update = {},
                                delete = {},
                                dismiss = {},
                                postpone = {},
                                undo = {}
                            ),
                            onBack = { dispatcher.onBackPressed() }
                        )
                    }
                }
            )
        }
    }

    private val task = Task(
        id = 1,
        name = "test-task",
        description = "",
        goal = "test-goal",
        schedule = Task.Schedule.Once(Instant.parse("2026-01-02T10:20:00Z")),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true,
        color = null
    )
}
