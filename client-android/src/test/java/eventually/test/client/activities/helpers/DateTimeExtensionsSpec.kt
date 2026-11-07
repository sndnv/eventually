package eventually.test.client.activities.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.helpers.DateTimeExtensions.isToday
import eventually.client.activities.helpers.DateTimeExtensions.isTomorrow
import eventually.client.settings.Settings
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DateTimeExtensionsSpec {
    @Test
    fun formatInstantAsTimeWithContext() {
        assertThat(instant.formatAsTime(context), equalTo("9:42 PM"))
    }

    @Test
    fun formatInstantAsTimeWithFormat() {
        assertThat(instant.formatAsTime(Settings.DateTimeFormat.System), equalTo("9:42 PM"))
        assertThat(instant.formatAsTime(Settings.DateTimeFormat.Iso), equalTo("21:42"))
    }

    @Test
    fun formatInstantAsDateWithFormat() {
        assertThat(instant.formatAsDate(Settings.DateTimeFormat.System), equalTo("Dec 21, 2000"))
        assertThat(instant.formatAsDate(Settings.DateTimeFormat.Iso), equalTo("2000-12-21"))
    }

    @Test
    fun formatInstantAsDateTimeWithFormat() {
        assertThat(
            instant.formatAsDateTime(context, Settings.DateTimeFormat.System),
            equalTo("Dec 21, 2000" to "9:42 PM")
        )
        assertThat(
            instant.formatAsDateTime(context, Settings.DateTimeFormat.Iso),
            equalTo("2000-12-21" to "21:42")
        )

        val today = ZonedDateTime.now().withZoneSameLocal(ZoneId.systemDefault()).toInstant()
        val yesterday = today.minus(1, ChronoUnit.DAYS)
        val tomorrow = today.plus(1, ChronoUnit.DAYS)

        assertThat(today.formatAsDateTime(context, Settings.DateTimeFormat.Iso).first, equalTo("Today"))
        assertThat(yesterday.formatAsDateTime(context, Settings.DateTimeFormat.Iso).first, equalTo("Yesterday"))
        assertThat(tomorrow.formatAsDateTime(context, Settings.DateTimeFormat.Iso).first, equalTo("Tomorrow"))
    }

    @Test
    fun formatLocalTimeAsTime() {
        assertThat(LocalTime.of(21, 42).formatAsTime(Settings.DateTimeFormat.System), equalTo("9:42 PM"))
        assertThat(LocalTime.of(21, 42).formatAsTime(Settings.DateTimeFormat.Iso), equalTo("21:42"))
    }

    @Test
    fun formatLocalDateAsDate() {
        assertThat(LocalDate.of(2000, 12, 21).formatAsDate(Settings.DateTimeFormat.System), equalTo("Dec 21, 2000"))
        assertThat(LocalDate.of(2000, 12, 21).formatAsDate(Settings.DateTimeFormat.Iso), equalTo("2000-12-21"))
    }

    @Test
    fun formatInstantAsFullDateTime() {
        assertThat(
            instant.formatAsFullDateTime(Settings.DateTimeFormat.System),
            equalTo("Dec 21, 2000, 9:42 PM")
        )
        assertThat(instant.formatAsFullDateTime(Settings.DateTimeFormat.Iso), equalTo("2000-12-21 21:42"))
    }

    @Test
    fun checkIfInstantIsToday() {
        val today = ZonedDateTime.now().withZoneSameLocal(ZoneId.systemDefault()).toInstant()
        val yesterday = today.minus(1, ChronoUnit.DAYS)
        val tomorrow = today.plus(1, ChronoUnit.DAYS)

        assertThat(instant.isToday(), equalTo(false))
        assertThat(today.isToday(), equalTo(true))
        assertThat(yesterday.isToday(), equalTo(false))
        assertThat(tomorrow.isToday(), equalTo(false))
    }

    @Test
    fun checkIfInstantIsTomorrow() {
        val today = ZonedDateTime.now().withZoneSameLocal(ZoneId.systemDefault()).toInstant()
        val yesterday = today.minus(1, ChronoUnit.DAYS)
        val tomorrow = today.plus(1, ChronoUnit.DAYS)

        assertThat(instant.isTomorrow(), equalTo(false))
        assertThat(today.isTomorrow(), equalTo(false))
        assertThat(yesterday.isTomorrow(), equalTo(false))
        assertThat(tomorrow.isTomorrow(), equalTo(true))
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val instant: Instant = ZonedDateTime
        .parse("2000-12-21T21:42:59.00Z")
        .withZoneSameLocal(ZoneId.systemDefault())
        .toInstant()
}
