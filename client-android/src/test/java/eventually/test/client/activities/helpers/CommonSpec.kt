package eventually.test.client.activities.helpers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.asString
import eventually.client.activities.helpers.Common.toFields
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.Period
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
    fun convertPeriodsToFields() {
        assertThat(Period.ofDays(0).toFields(), equalTo(Pair(0, ChronoUnit.DAYS)))
        assertThat(Period.ofDays(1).toFields(), equalTo(Pair(1, ChronoUnit.DAYS)))
        assertThat(Period.ofDays(60).toFields(), equalTo(Pair(60, ChronoUnit.DAYS)))

        assertThat(Period.ofMonths(1).toFields(), equalTo(Pair(1, ChronoUnit.MONTHS)))
        assertThat(Period.ofMonths(60).toFields(), equalTo(Pair(60, ChronoUnit.MONTHS)))

        assertThat(Period.ofYears(1).toFields(), equalTo(Pair(1, ChronoUnit.YEARS)))
        assertThat(Period.ofYears(60).toFields(), equalTo(Pair(60, ChronoUnit.YEARS)))
    }

    @Test
    fun convertIntervalsToFields() {
        assertThat(Duration.ofSeconds(21).toInterval().toFields(), equalTo(Pair(21, ChronoUnit.SECONDS)))
        assertThat(Period.ofMonths(42).toInterval().toFields(), equalTo(Pair(42, ChronoUnit.MONTHS)))
        assertThat(Period.ofDays(1).toInterval().toFields(), equalTo(Pair(1, ChronoUnit.DAYS)))
        assertThat(Duration.ofHours(24).toInterval().toFields(), equalTo(Pair(24, ChronoUnit.HOURS)))
        assertThat(Duration.ofDays(3).toInterval().toFields(), equalTo(Pair(72, ChronoUnit.HOURS)))
        assertThat(Duration.ofMinutes(90).toInterval().toFields(), equalTo(Pair(90, ChronoUnit.MINUTES)))
    }

    @Test
    fun convertDurationsToFieldsWithoutDays() {
        assertThat(Duration.ofHours(24).toFields(withDays = false), equalTo(Pair(24, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(25).toFields(withDays = false), equalTo(Pair(25, ChronoUnit.HOURS)))
        assertThat(Duration.ofMinutes(60).toFields(withDays = false), equalTo(Pair(1, ChronoUnit.HOURS)))
        assertThat(Duration.ofSeconds(61).toFields(withDays = false), equalTo(Pair(61, ChronoUnit.SECONDS)))
    }

    @Test
    fun convertChronoUnitsToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(ChronoUnit.YEARS.asString(context), equalTo("years"))
        assertThat(ChronoUnit.MONTHS.asString(context), equalTo("months"))
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

        assertThat(ChronoUnit.YEARS.asQuantityString(amount = 1, context), equalTo("year"))
        assertThat(ChronoUnit.YEARS.asQuantityString(amount = 10, context), equalTo("years"))
        assertThat(ChronoUnit.MONTHS.asQuantityString(amount = 1, context), equalTo("month"))
        assertThat(ChronoUnit.MONTHS.asQuantityString(amount = 10, context), equalTo("months"))
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
    fun convertDaysOfTheWeekToStrings() {
        val someDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        val allDays = someDays + setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

        assertThat(someDays.asString(DayOfWeek.MONDAY), equalTo("Tue, Fri, Sat, Sun"))
        assertThat(allDays.asString(DayOfWeek.MONDAY), equalTo("Mon, Tue, Wed, Thu, Fri, Sat, Sun"))
        assertThat(emptySet<DayOfWeek>().asString(DayOfWeek.MONDAY), equalTo(""))

        assertThat(someDays.asString(DayOfWeek.SATURDAY), equalTo("Sat, Sun, Tue, Fri"))
        assertThat(allDays.asString(DayOfWeek.SATURDAY), equalTo("Sat, Sun, Mon, Tue, Wed, Thu, Fri"))
        assertThat(emptySet<DayOfWeek>().asString(DayOfWeek.SATURDAY), equalTo(""))

        assertThat(someDays.asString(DayOfWeek.SUNDAY), equalTo("Sun, Tue, Fri, Sat"))
        assertThat(allDays.asString(DayOfWeek.SUNDAY), equalTo("Sun, Mon, Tue, Wed, Thu, Fri, Sat"))
        assertThat(emptySet<DayOfWeek>().asString(DayOfWeek.SUNDAY), equalTo(""))
    }
}