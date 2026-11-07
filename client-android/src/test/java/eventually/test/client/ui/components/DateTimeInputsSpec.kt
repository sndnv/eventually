package eventually.test.client.ui.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eventually.client.settings.Settings
import eventually.client.ui.components.DateInput
import eventually.client.ui.components.TimeInput
import eventually.client.ui.components.asPickerDate
import eventually.client.ui.components.asPickerMillis
import eventually.client.ui.components.orderedDays
import eventually.client.ui.theme.EventuallyTheme
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DateTimeInputsSpec {
    @get:Rule
    val composeRule = createComposeRule()

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun convertPickerDatesIndependentlyOfTimeZone() {
        val date = LocalDate.of(2026, 3, 15)

        Zones.forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))

            assertThat(date.asPickerMillis(), equalTo(1773532800000L))
            assertThat(1773532800000L.asPickerDate(), equalTo(date))
            assertThat(date.asPickerMillis().asPickerDate(), equalTo(date))
        }
    }

    @Test
    fun keepSelectedDateWhenConfirmedWestOfUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

        val selected = mutableListOf<LocalDate>()

        composeRule.setContent {
            EventuallyTheme {
                DateInput(
                    date = LocalDate.of(2026, 3, 15),
                    onDateChange = { selected.add(it) },
                    format = Settings.DateTimeFormat.Iso
                )
            }
        }

        composeRule.onNodeWithText("2026-03-15").performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(selected.toList(), equalTo(listOf(LocalDate.of(2026, 3, 15))))
    }

    @Test
    fun keepSelectedTimeWhenConfirmed() {
        val selected = mutableListOf<LocalTime>()

        composeRule.setContent {
            EventuallyTheme {
                TimeInput(
                    time = LocalTime.of(10, 20),
                    onTimeChange = { selected.add(it) },
                    format = Settings.DateTimeFormat.Iso
                )
            }
        }

        composeRule.onNodeWithText("10:20").performClick()
        composeRule.onNodeWithText("Select time").assertExists()
        composeRule.onNodeWithText("OK").performClick()

        assertThat(selected.toList(), equalTo(listOf(LocalTime.of(10, 20))))
    }

    @Test
    fun orderDaysByFirstDayOfWeek() {
        assertThat(
            orderedDays(DayOfWeek.MONDAY),
            equalTo(DayOfWeek.entries.toList())
        )

        assertThat(
            orderedDays(DayOfWeek.SUNDAY),
            equalTo(
                listOf(
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY
                )
            )
        )
    }

    private companion object {
        val Zones: List<String> = listOf(
            "UTC",
            "America/Los_Angeles",
            "Pacific/Pago_Pago",
            "Europe/Sofia",
            "Pacific/Auckland",
            "Pacific/Kiritimati"
        )
    }
}
