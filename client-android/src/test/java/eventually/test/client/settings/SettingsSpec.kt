package eventually.test.client.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import eventually.client.settings.Settings
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getAlarmTimeout
import eventually.client.settings.Settings.getDateTimeFormat
import eventually.client.settings.Settings.getDismissedLimit
import eventually.client.settings.Settings.getDismissedRetention
import eventually.client.settings.Settings.getFirstDayOfWeek
import eventually.client.settings.Settings.getPostponeLength
import eventually.client.settings.Settings.getShowAllInstances
import eventually.client.settings.Settings.getStatsEnabled
import eventually.client.settings.Settings.getSummaryConfig
import eventually.client.settings.Settings.getSummaryMaxTasks
import eventually.client.settings.Settings.getSummarySize
import eventually.client.settings.Settings.toDayOfWeek
import eventually.core.model.TaskSummaryConfig
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SettingsSpec {
    @Test
    fun useDefaultPreferencesFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        @Suppress("DEPRECATION")
        val platformDefault = android.preference.PreferenceManager.getDefaultSharedPreferences(context)

        assertThat(context.defaultPreferences(), sameInstance(platformDefault))

        context.defaultPreferences().edit().putString(Settings.Keys.SummaryMaxTasks, "9").commit()

        assertThat(
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
                .getString(Settings.Keys.SummaryMaxTasks, null),
            equalTo("9")
        )
    }

    @Test
    fun supportParsingDateTimeFormats() {
        assertThat(Settings.parseDateTimeFormat(format = "system"), equalTo((Settings.DateTimeFormat.System)))
        assertThat(Settings.parseDateTimeFormat(format = "iso"), equalTo((Settings.DateTimeFormat.Iso)))

        try {
            Settings.parseDateTimeFormat(format = "other")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected format found: [other]"))
        }
    }

    @Test
    fun supportParsingDays() {
        assertThat(
            Settings.parseDay(day = "system"),
            anyOf(equalTo(DayOfWeek.SATURDAY), equalTo(DayOfWeek.SUNDAY), equalTo(DayOfWeek.MONDAY))
        )

        assertThat(Settings.parseDay(day = "monday"), equalTo(DayOfWeek.MONDAY))
        assertThat(Settings.parseDay(day = "tuesday"), equalTo(DayOfWeek.TUESDAY))
        assertThat(Settings.parseDay(day = "wednesday"), equalTo(DayOfWeek.WEDNESDAY))
        assertThat(Settings.parseDay(day = "thursday"), equalTo(DayOfWeek.THURSDAY))
        assertThat(Settings.parseDay(day = "friday"), equalTo(DayOfWeek.FRIDAY))
        assertThat(Settings.parseDay(day = "saturday"), equalTo(DayOfWeek.SATURDAY))
        assertThat(Settings.parseDay(day = "sunday"), equalTo(DayOfWeek.SUNDAY))
    }

    @Test
    fun supportConvertingCalendarDaysToDayOfWeek() {
        assertThat(Calendar.MONDAY.toDayOfWeek(), equalTo(DayOfWeek.MONDAY))
        assertThat(Calendar.TUESDAY.toDayOfWeek(), equalTo(DayOfWeek.TUESDAY))
        assertThat(Calendar.WEDNESDAY.toDayOfWeek(), equalTo(DayOfWeek.WEDNESDAY))
        assertThat(Calendar.THURSDAY.toDayOfWeek(), equalTo(DayOfWeek.THURSDAY))
        assertThat(Calendar.FRIDAY.toDayOfWeek(), equalTo(DayOfWeek.FRIDAY))
        assertThat(Calendar.SATURDAY.toDayOfWeek(), equalTo(DayOfWeek.SATURDAY))
        assertThat(Calendar.SUNDAY.toDayOfWeek(), equalTo(DayOfWeek.SUNDAY))

        try {
            42.toDayOfWeek()
            Assert.fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected day of the week found: [42]"))
        }
    }

    @Test
    fun supportRetrievingDefaultSummarySize() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.SummarySize, Settings.Defaults.SummarySize) } returns null

        val expectedSummarySize = Duration.ofMinutes(Settings.Defaults.SummarySize.toLong())
        assertThat(preferences.getSummarySize(), equalTo((expectedSummarySize)))
    }

    @Test
    fun supportRetrievingUserDefinedSummarySize() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.SummarySize, Settings.Defaults.SummarySize) } returns "42"

        val expectedSummarySize = Duration.ofMinutes(42)
        assertThat(preferences.getSummarySize(), equalTo((expectedSummarySize)))
    }

    @Test
    fun supportRetrievingTheDefaultMaxNumberOfSummaryTasks() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.SummaryMaxTasks, Settings.Defaults.SummaryMaxTasks) } returns null

        val expectedSummaryTasks = Settings.Defaults.SummaryMaxTasks.toInt()
        assertThat(preferences.getSummaryMaxTasks(), equalTo((expectedSummaryTasks)))
    }

    @Test
    fun supportRetrievingUserDefinedMaxNumberSummaryTasks() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.SummaryMaxTasks, Settings.Defaults.SummaryMaxTasks) } returns "42"

        val expectedSummaryTasks = 42
        assertThat(preferences.getSummaryMaxTasks(), equalTo((expectedSummaryTasks)))
    }

    @Test
    fun supportRetrievingDefaultPostponeLength() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.PostponeLength, Settings.Defaults.PostponeLength) } returns null

        val expectedPostponeLength = Duration.ofMinutes(Settings.Defaults.PostponeLength.toLong())
        assertThat(preferences.getPostponeLength(), equalTo((expectedPostponeLength)))
    }

    @Test
    fun supportRetrievingUserDefinedPostponeLength() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.PostponeLength, Settings.Defaults.PostponeLength) } returns "42"

        val expectedPostponeLength = Duration.ofMinutes(42)
        assertThat(preferences.getPostponeLength(), equalTo((expectedPostponeLength)))
    }

    @Test
    fun supportRetrievingDefaultAlarmTimeout() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.AlarmTimeout, Settings.Defaults.AlarmTimeout) } returns null

        assertThat(preferences.getAlarmTimeout(), equalTo(Duration.ofMinutes(10)))
        assertThat(null.getAlarmTimeout(), equalTo(Duration.ofMinutes(10)))
    }

    @Test
    fun supportRetrievingUserDefinedAlarmTimeout() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.AlarmTimeout, Settings.Defaults.AlarmTimeout) } returns "3"

        assertThat(preferences.getAlarmTimeout(), equalTo(Duration.ofMinutes(3)))
    }

    @Test
    fun supportRetrievingDefaultDismissedRetention() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(Settings.Keys.DismissedRetention, Settings.Defaults.DismissedRetention)
        } returns null

        assertThat(preferences.getDismissedRetention(), equalTo(Duration.ofDays(30)))
    }

    @Test
    fun supportRetrievingUserDefinedDismissedRetention() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(Settings.Keys.DismissedRetention, Settings.Defaults.DismissedRetention)
        } returns "42"

        assertThat(preferences.getDismissedRetention(), equalTo(Duration.ofDays(42)))
    }

    @Test
    fun supportRetrievingDefaultDismissedLimit() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.DismissedLimit, Settings.Defaults.DismissedLimit) } returns null

        assertThat(preferences.getDismissedLimit(), equalTo(100))
    }

    @Test
    fun supportRetrievingUserDefinedDismissedLimit() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.DismissedLimit, Settings.Defaults.DismissedLimit) } returns "42"

        assertThat(preferences.getDismissedLimit(), equalTo(42))
    }

    @Test
    fun supportRetrievingTheSummaryConfig() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultPreferences()

        assertThat(
            preferences.getSummaryConfig(),
            equalTo(
                TaskSummaryConfig(
                    summarySize = Duration.ofMinutes(15),
                    dismissedRetention = Duration.ofDays(30),
                    dismissedLimit = 100
                )
            )
        )

        preferences.edit()
            .putString(Settings.Keys.SummarySize, "60")
            .putString(Settings.Keys.DismissedRetention, "7")
            .putString(Settings.Keys.DismissedLimit, "5")
            .commit()

        assertThat(
            preferences.getSummaryConfig(),
            equalTo(
                TaskSummaryConfig(
                    summarySize = Duration.ofMinutes(60),
                    dismissedRetention = Duration.ofDays(7),
                    dismissedLimit = 5
                )
            )
        )
    }

    @Test
    fun supportRetrievingDefaultDateTimeFormat() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.DateTimeFormat, Settings.Defaults.DateTimeFormat) } returns null

        val expectedFormat = Settings.Defaults.DateTimeFormat
        assertThat(preferences.getDateTimeFormat(), equalTo((Settings.parseDateTimeFormat(expectedFormat))))
    }

    @Test
    fun supportRetrievingUserDefinedDateTimeFormat() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.DateTimeFormat, Settings.Defaults.DateTimeFormat) } returns "iso"

        val expectedFormat = "iso"
        assertThat(preferences.getDateTimeFormat(), equalTo((Settings.parseDateTimeFormat(expectedFormat))))
    }

    @Test
    fun supportRetrievingDefaultFirstDayOfWeek() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.FirstDayOfWeek, Settings.Defaults.FirstDayOfWeek) } returns null

        val expectedDay = Settings.Defaults.FirstDayOfWeek
        assertThat(preferences.getFirstDayOfWeek(), equalTo((Settings.parseDay(expectedDay))))
    }

    @Test
    fun supportRetrievingUserDefinedFirstDayOfWeek() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Settings.Keys.FirstDayOfWeek, Settings.Defaults.FirstDayOfWeek) } returns "monday"

        val expectedDay = "monday"
        assertThat(preferences.getFirstDayOfWeek(), equalTo((Settings.parseDay(expectedDay))))
    }

    @Test
    fun supportRetrievingDefaultStatsEnabledState() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getBoolean(Settings.Keys.StatsEnabled, Settings.Defaults.StatsEnabled) } returns false

        val expectedState = Settings.Defaults.StatsEnabled
        assertThat(preferences.getStatsEnabled(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedStatsEnabledState() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getBoolean(Settings.Keys.StatsEnabled, Settings.Defaults.StatsEnabled) } returns true

        val expectedState = true
        assertThat(preferences.getStatsEnabled(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultShowAllInstancesState() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getBoolean(Settings.Keys.ShowAllInstances, Settings.Defaults.ShowAllInstances) } returns false

        val expectedState = Settings.Defaults.ShowAllInstances
        assertThat(preferences.getShowAllInstances(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedShowAllInstancesState() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getBoolean(Settings.Keys.ShowAllInstances, Settings.Defaults.ShowAllInstances) } returns true

        val expectedState = true
        assertThat(preferences.getShowAllInstances(), equalTo(expectedState))
    }
}
