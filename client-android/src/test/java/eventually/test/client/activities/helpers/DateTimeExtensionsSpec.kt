package eventually.test.client.activities.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.helpers.DateTimeExtensions.isToday
import eventually.client.activities.helpers.DateTimeExtensions.parseAsDate
import eventually.client.activities.helpers.DateTimeExtensions.parseAsDateTime
import eventually.client.activities.helpers.DateTimeExtensions.parseAsFullDateTime
import eventually.client.activities.helpers.DateTimeExtensions.parseAsLocalDate
import eventually.client.activities.helpers.DateTimeExtensions.parseAsLocalTime
import eventually.client.activities.helpers.DateTimeExtensions.parseAsTime
import eventually.client.settings.Settings
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DateTimeExtensionsSpec {
    @Test
    fun formatInstantAsTimeWithContext() {
        assertThat(instant.formatAsTime(context), equalTo("9:42 PM"))
    }

    @Test
    fun formatInstantAsTimeWithFormat() {
        assertThat(instant.formatAsTime(Settings.DateTimeFormat.System), equalTo("9:42 PM"))
        assertThat(instant.formatAsTime(Settings.DateTimeFormat.Iso), equalTo("21:42"))
    }

    @Test
    fun formatInstantAsDateWithContext() {
        assertThat(instant.formatAsDate(context), equalTo("Dec 21, 2000"))
    }

    @Test
    fun formatInstantAsDateWithFormat() {
        assertThat(instant.formatAsDate(Settings.DateTimeFormat.System), equalTo("Dec 21, 2000"))
        assertThat(instant.formatAsDate(Settings.DateTimeFormat.Iso), equalTo("2000-12-21"))
    }

    @Test
    fun formatInstantAsDateTime() {
        val (date, time) = instant.formatAsDateTime(context)

        assertThat(date, equalTo("Dec 21, 2000"))
        assertThat(time, equalTo("9:42 PM"))

        val today = ZonedDateTime.now().withZoneSameLocal(ZoneId.systemDefault()).toInstant()
        val yesterday = today.minus(1, ChronoUnit.DAYS)
        val tomorrow = today.plus(1, ChronoUnit.DAYS)

        assertThat(today.formatAsDateTime(context).first, equalTo("Today"))
        assertThat(yesterday.formatAsDateTime(context).first, equalTo("Yesterday"))
        assertThat(tomorrow.formatAsDateTime(context).first, equalTo("Tomorrow"))
    }

    @Test
    fun formatLocalTimeAsTime() {
        assertThat(LocalTime.of(21, 42).formatAsTime(context), equalTo("9:42 PM"))
    }

    @Test
    fun formatInstantAsFullDateTime() {
        assertThat(instant.formatAsFullDateTime(context), equalTo("Dec 21, 2000, 9:42 PM"))
    }

    @Test
    fun parseStringAsTime() {
        assertThat(
            "9:42 PM".parseAsTime(context),
            equalTo(
                ZonedDateTime
                    .now()
                    .withHour(21)
                    .withMinute(42)
                    .withSecond(0)
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toInstant()
            )
        )
    }

    @Test
    fun parseStringAsLocalTime() {
        assertThat(
            "9:42 PM".parseAsLocalTime(context),
            equalTo(LocalTime.of(21, 42))
        )
    }

    @Test
    fun parseStringAsDate() {
        assertThat(
            "Dec 21, 2000".parseAsDate(context),
            equalTo(
                LocalDateTime
                    .now()
                    .withYear(2000)
                    .withMonth(12)
                    .withDayOfMonth(21)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toInstant(ZoneOffset.UTC)
            )
        )
    }

    @Test
    fun parseStringAsLocalDate() {
        assertThat(
            "Dec 21, 2000".parseAsLocalDate(context),
            equalTo(
                LocalDate.of(2000, 12, 21)
            )
        )
    }

    @Test
    fun parseStringAsFullDateTime() {
        assertThat(
            "Dec 21, 2000, 9:42 PM".parseAsFullDateTime(context),
            equalTo(instant.truncatedTo(ChronoUnit.MINUTES))
        )
    }

    @Test
    fun parseStringPairAsDateTime() {
        assertThat(
            Pair("Dec 21, 2000", "9:42 PM").parseAsDateTime(context),
            equalTo(instant.truncatedTo(ChronoUnit.MINUTES))
        )
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

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val instant: Instant = ZonedDateTime
        .parse("2000-12-21T21:42:59.00Z")
        .withZoneSameLocal(ZoneId.systemDefault())
        .toInstant()
}