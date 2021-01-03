package eventually.test.client.activities.helpers

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.helpers.Common.StyledString
import eventually.client.activities.helpers.Common.asChronoUnit
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.asString
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.activities.helpers.Common.toFields
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class CommonSpec {
    @Test
    fun convertDurationsToFields() {
        assertThat(Duration.ofSeconds(0).toFields(), equalTo(Pair(0, ChronoUnit.SECONDS)))
        assertThat(Duration.ofSeconds(1).toFields(), equalTo(Pair(1, ChronoUnit.SECONDS)))
        assertThat(Duration.ofSeconds(59).toFields(), equalTo(Pair(59, ChronoUnit.SECONDS)))
        assertThat(Duration.ofSeconds(61).toFields(), equalTo(Pair(61, ChronoUnit.SECONDS)))

        assertThat(Duration.ofSeconds(60).toFields(), equalTo(Pair(1, ChronoUnit.MINUTES)))
        assertThat(Duration.ofMinutes(1).toFields(), equalTo(Pair(1, ChronoUnit.MINUTES)))
        assertThat(Duration.ofMinutes(59).toFields(), equalTo(Pair(59, ChronoUnit.MINUTES)))
        assertThat(Duration.ofMinutes(61).toFields(), equalTo(Pair(61, ChronoUnit.MINUTES)))

        assertThat(Duration.ofMinutes(60).toFields(), equalTo(Pair(1, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(1).toFields(), equalTo(Pair(1, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(23).toFields(), equalTo(Pair(23, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(25).toFields(), equalTo(Pair(25, ChronoUnit.HOURS)))

        assertThat(Duration.ofHours(24).toFields(), equalTo(Pair(1, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(1).toFields(), equalTo(Pair(1, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(2).toFields(), equalTo(Pair(2, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(3).toFields(), equalTo(Pair(3, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(99).toFields(), equalTo(Pair(99, ChronoUnit.DAYS)))
    }

    @Test
    fun convertChronoUnitsToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(ChronoUnit.DAYS.asString(context), equalTo("days"))
        assertThat(ChronoUnit.HOURS.asString(context), equalTo("hours"))
        assertThat(ChronoUnit.MINUTES.asString(context), equalTo("minutes"))
        assertThat(ChronoUnit.SECONDS.asString(context), equalTo("seconds"))

        try {
            ChronoUnit.WEEKS.asString(context)
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected ChronoUnit provided: [WEEKS]"))
        }
    }

    @Test
    fun convertChronoUnitsToQualifiedStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(ChronoUnit.DAYS.asQuantityString(amount = 1, context), equalTo("day"))
        assertThat(ChronoUnit.DAYS.asQuantityString(amount = 10, context), equalTo("days"))
        assertThat(ChronoUnit.HOURS.asQuantityString(amount = 1, context), equalTo("hour"))
        assertThat(ChronoUnit.HOURS.asQuantityString(amount = 10, context), equalTo("hours"))
        assertThat(ChronoUnit.MINUTES.asQuantityString(amount = 1, context), equalTo("minute"))
        assertThat(ChronoUnit.MINUTES.asQuantityString(amount = 10, context), equalTo("minutes"))
        assertThat(ChronoUnit.SECONDS.asQuantityString(amount = 1, context), equalTo("second"))
        assertThat(ChronoUnit.SECONDS.asQuantityString(amount = 10, context), equalTo("seconds"))

        try {
            ChronoUnit.WEEKS.asQuantityString(amount = 1, context)
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected ChronoUnit provided: [WEEKS]"))
        }
    }

    @Test
    fun convertStringsToChronoUnits() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("days".asChronoUnit(context), equalTo(ChronoUnit.DAYS))
        assertThat("hours".asChronoUnit(context), equalTo(ChronoUnit.HOURS))
        assertThat("minutes".asChronoUnit(context), equalTo(ChronoUnit.MINUTES))
        assertThat("seconds".asChronoUnit(context), equalTo(ChronoUnit.SECONDS))

        try {
            "other".asChronoUnit(context)
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected ChronoUnit string provided: [other]"))
        }
    }

    @Test
    fun convertDaysOfTheWeekToStrings() {
        val someDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        val allDays = someDays + setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

        assertThat(someDays.asString(), equalTo("Tue, Fri, Sat, Sun"))
        assertThat(allDays.asString(), equalTo("Mon, Tue, Wed, Thu, Fri, Sat, Sun"))
        assertThat(emptySet<DayOfWeek>().asString(), equalTo(""))
    }

    @Test
    fun renderSpannableStrings() {
        val rendered = "%1\$s | %2\$s | %3\$s".renderAsSpannable(
            StyledString(
                placeholder = "%1\$s",
                content = "test-1",
                style = StyleSpan(Typeface.BOLD_ITALIC)
            ),
            StyledString(
                placeholder = "%2\$s",
                content = "test-2",
                style = StyleSpan(Typeface.ITALIC)
            ),
            StyledString(
                placeholder = "%3\$s",
                content = "test-3",
                style = StyleSpan(Typeface.BOLD)
            )
        )

        val spans = rendered.getSpans(1, rendered.length, StyleSpan::class.java)

        assertThat(rendered.toString(), equalTo("test-1 | test-2 | test-3"))
        assertThat(spans.size, equalTo(3))
        assertThat(spans[0].style, equalTo(Typeface.BOLD_ITALIC))
        assertThat(spans[1].style, equalTo(Typeface.ITALIC))
        assertThat(spans[2].style, equalTo(Typeface.BOLD))
    }
}