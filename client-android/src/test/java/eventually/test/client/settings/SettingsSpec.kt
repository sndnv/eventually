package eventually.test.client.settings

import android.content.SharedPreferences
import eventually.client.settings.Settings
import eventually.client.settings.Settings.getDateTimeFormat
import eventually.client.settings.Settings.getPostponeLength
import eventually.client.settings.Settings.getShowAllInstances
import eventually.client.settings.Settings.getStatsEnabled
import eventually.client.settings.Settings.getSummaryMaxTasks
import eventually.client.settings.Settings.getSummarySize
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SettingsSpec {
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
