package eventually.test.client.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.settings.Settings
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.ui.screens.SettingsScreen
import eventually.client.ui.theme.EventuallyTheme
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SettingsScreenSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val preferences: SharedPreferences =
        ApplicationProvider.getApplicationContext<Context>().defaultPreferences()

    @Test
    fun showDefaultsWhenNothingIsStored() {
        showSettings()

        composeRule.onNodeWithText("Eventually").assertExists()
        composeRule.onNodeWithText("Settings").assertExists()
        showsText("Show expected tasks in the next 15 minutes")
        showsText("Show at most 7 tasks")
        showsText("Sunday")
        showsText("Postpone a task by 10 minutes")
        showsText("Stop ringing alarms after 10 minutes")
        showsText("Dismissed tasks")
        showsText("Keep dismissed instances from the last 30 days")
        showsText("Keep at most 100 dismissed instances per task")
        showsText("Show statistics menu option")
        composeRule.onNodeWithText("Show statistics menu option").assertIsOff()
        showsText("Show all task instances")
        composeRule.onNodeWithText("Show all task instances").assertIsOff()

        assertThat(preferences.all, equalTo(emptyMap()))
    }

    @Test
    fun showValuesStoredByPreviousVersions() {
        preferences.edit {
            putString(Settings.Keys.SummarySize, "120")
            putString(Settings.Keys.SummaryMaxTasks, "11")
            putString(Settings.Keys.PostponeLength, "25")
            putString(Settings.Keys.DateTimeFormat, "iso")
            putString(Settings.Keys.FirstDayOfWeek, "monday")
            putBoolean(Settings.Keys.StatsEnabled, true)
            putBoolean(Settings.Keys.ShowAllInstances, true)
        }

        showSettings()

        showsText("Show expected tasks in the next 2 hours")
        showsText("Show at most 11 tasks")
        showsText("Show dates as ${Instant.now().formatAsDate(Settings.DateTimeFormat.Iso)}", substring = true)
        showsText("Monday")
        showsText("Postpone a task by 25 minutes")
        showsText("Show statistics menu option")
        composeRule.onNodeWithText("Show statistics menu option").assertIsOn()
        showsText("Show all task instances")
        composeRule.onNodeWithText("Show all task instances").assertIsOn()
    }

    @Test
    fun updateDropdownPreferences() {
        showSettings()

        composeRule.onNodeWithText("Size").performClick()
        composeRule.onNodeWithText("Show a 1-hour summary").performClick()

        showsText("Show expected tasks in the next hour")

        composeRule.onNodeWithText("Start week on").performClick()
        composeRule.onNodeWithText("Monday").performClick()

        showsText("Monday")

        assertThat(
            preferences.all,
            equalTo(mapOf(Settings.Keys.SummarySize to "60", Settings.Keys.FirstDayOfWeek to "monday"))
        )
    }

    @Test
    fun updatePostponeLength() {
        showSettings()

        scrollTo("Postpone length")
        composeRule.onNodeWithText("Postpone length").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("25")
        composeRule.onNodeWithText("OK").performClick()

        showsText("Postpone a task by 25 minutes")

        assertThat(preferences.all, equalTo(mapOf(Settings.Keys.PostponeLength to "25")))
    }

    @Test
    fun rejectInvalidPostponeLength() {
        showSettings()

        scrollTo("Postpone length")
        composeRule.onNodeWithText("Postpone length").performClick()

        listOf("0", "-5", "abc", "").forEach { invalid ->
            composeRule.onNode(hasSetTextAction()).performTextReplacement(invalid)
            composeRule.onNodeWithText("OK").performClick()
            composeRule.onNode(hasSetTextAction()).assertExists()
        }

        composeRule.onNodeWithText("Cancel").performClick()

        showsText("Postpone a task by 10 minutes")

        assertThat(preferences.all, equalTo(emptyMap()))
    }

    @Test
    fun updateAlarmTimeout() {
        showSettings()

        updateNumber("Alarm timeout", "1")
        showsText("Stop ringing alarms after 1 minute")

        assertThat(preferences.all, equalTo(mapOf(Settings.Keys.AlarmTimeout to "1")))
    }

    @Test
    fun showStoredAlarmTimeout() {
        preferences.edit { putString(Settings.Keys.AlarmTimeout, "15") }

        showSettings()

        showsText("Stop ringing alarms after 15 minutes")
    }

    @Test
    fun rejectInvalidAlarmTimeout() {
        showSettings()

        scrollTo("Alarm timeout")
        composeRule.onNodeWithText("Alarm timeout").performClick()

        listOf("0", "-5", "abc", "").forEach { invalid ->
            composeRule.onNode(hasSetTextAction()).performTextReplacement(invalid)
            composeRule.onNodeWithText("OK").performClick()
            composeRule.onNode(hasSetTextAction()).assertExists()
        }

        composeRule.onNodeWithText("Cancel").performClick()

        showsText("Stop ringing alarms after 10 minutes")

        assertThat(preferences.all, equalTo(emptyMap()))
    }

    @Test
    fun updateDismissedPreferences() {
        showSettings()

        updateNumber("Retention", "1")
        showsText("Keep dismissed instances from the last day")

        updateNumber("Limit", "250")
        showsText("Keep at most 250 dismissed instances per task")

        assertThat(
            preferences.all,
            equalTo(mapOf(Settings.Keys.DismissedRetention to "1", Settings.Keys.DismissedLimit to "250"))
        )
    }

    @Test
    fun showStoredDismissedPreferences() {
        preferences.edit {
            putString(Settings.Keys.DismissedRetention, "7")
            putString(Settings.Keys.DismissedLimit, "1")
        }

        showSettings()

        showsText("Keep dismissed instances from the last 7 days")
        showsText("Keep at most 1 dismissed instance per task")
    }

    @Test
    fun rejectInvalidDismissedPreferences() {
        showSettings()

        listOf("Retention", "Limit").forEach { title ->
            scrollTo(title)
            composeRule.onNodeWithText(title).performClick()

            listOf("0", "-5", "abc", "").forEach { invalid ->
                composeRule.onNode(hasSetTextAction()).performTextReplacement(invalid)
                composeRule.onNodeWithText("OK").performClick()
                composeRule.onNode(hasSetTextAction()).assertExists()
            }

            composeRule.onNodeWithText("Cancel").performClick()
        }

        showsText("Keep dismissed instances from the last 30 days")
        showsText("Keep at most 100 dismissed instances per task")

        assertThat(preferences.all, equalTo(emptyMap()))
    }

    @Test
    fun updateSwitchPreferences() {
        showSettings()

        scrollTo("Show statistics menu option")
        composeRule.onNodeWithText("Show statistics menu option").performClick().assertIsOn()

        scrollTo("Show all task instances")
        composeRule.onNodeWithText("Show all task instances").performClick().assertIsOn()
        composeRule.onNodeWithText("Show all task instances").performClick().assertIsOff()

        assertThat(
            preferences.all,
            equalTo(mapOf(Settings.Keys.StatsEnabled to true, Settings.Keys.ShowAllInstances to false))
        )
    }

    @Test
    fun notifyListenersOnlyAboutUpdatedPreferences() {
        preferences.edit {
            putString(Settings.Keys.SummaryMaxTasks, "5")
            putBoolean(Settings.Keys.StatsEnabled, true)
        }

        val updated = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> updated.add(key) }
        preferences.registerOnSharedPreferenceChangeListener(listener)

        showSettings()

        composeRule.onNodeWithText("Size").performClick()
        composeRule.onNodeWithText("Show a 30-minute summary").performClick()
        composeRule.waitForIdle()

        preferences.unregisterOnSharedPreferenceChangeListener(listener)

        assertThat(updated.toList(), equalTo(listOf<String?>(Settings.Keys.SummarySize)))
        assertThat(
            preferences.all,
            equalTo(
                mapOf(
                    Settings.Keys.SummarySize to "30",
                    Settings.Keys.SummaryMaxTasks to "5",
                    Settings.Keys.StatsEnabled to true
                )
            )
        )
    }

    private fun showSettings() {
        composeRule.setContent {
            EventuallyTheme {
                SettingsScreen(preferences = preferences, onBack = {})
            }
        }
    }

    private fun updateNumber(title: String, value: String) {
        scrollTo(title)
        composeRule.onNodeWithText(title).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(value)
        composeRule.onNodeWithText("OK").performClick()
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }

    private fun showsText(text: String, substring: Boolean = false) {
        scrollTo(text, substring)
        composeRule.onNode(hasText(text, substring = substring)).assertExists()
    }
}
