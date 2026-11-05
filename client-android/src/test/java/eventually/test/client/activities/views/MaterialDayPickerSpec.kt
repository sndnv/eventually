package eventually.test.client.activities.views

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.chip.Chip
import eventually.client.R
import eventually.client.activities.views.MaterialDayPicker
import eventually.client.activities.views.MaterialDayPicker.Weekday
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class MaterialDayPickerSpec {
    @Test
    fun orderDaysByFirstDayOfWeek() {
        val picker = createPicker()

        assertThat(picker.labels(), equalTo(listOf("M", "T", "W", "T", "F", "S", "S")))

        picker.firstDayOfWeek = Weekday.SUNDAY

        assertThat(picker.labels(), equalTo(listOf("S", "M", "T", "W", "T", "F", "S")))
    }

    @Test
    fun setAndRetrieveSelectedDays() {
        val picker = createPicker()

        picker.setSelectedDays(listOf(Weekday.FRIDAY, Weekday.MONDAY))
        assertThat(picker.selectedDays, equalTo(listOf(Weekday.MONDAY, Weekday.FRIDAY)))

        picker.setSelectedDays(emptyList())
        assertThat(picker.selectedDays, equalTo(emptyList()))
    }

    @Test
    fun selectDaysByPosition() {
        val picker = createPicker()
        picker.firstDayOfWeek = Weekday.SUNDAY

        picker.chipAt(0).performClick()
        picker.chipAt(1).performClick()

        assertThat(picker.selectedDays, equalTo(listOf(Weekday.MONDAY, Weekday.SUNDAY)))

        picker.chipAt(0).performClick()

        assertThat(picker.selectedDays, equalTo(listOf(Weekday.MONDAY)))
    }

    @Test
    fun keepSelectedDaysWhenChangingFirstDayOfWeek() {
        val picker = createPicker()

        picker.setSelectedDays(listOf(Weekday.TUESDAY, Weekday.SATURDAY))
        picker.firstDayOfWeek = Weekday.SUNDAY

        assertThat(picker.selectedDays, equalTo(listOf(Weekday.TUESDAY, Weekday.SATURDAY)))
    }

    @Test
    fun showDaysWithoutCheckedIconsOrTouchTargetInsets() {
        val picker = createPicker()

        (0 until picker.childCount).map { picker.chipAt(it) }.forEach { chip ->
            assertThat(chip.isCheckedIconVisible, equalTo(false))
            assertThat(chip.shouldEnsureMinTouchTargetSize(), equalTo(false))
        }
    }

    private fun createPicker(): MaterialDayPicker =
        MaterialDayPicker(ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme))

    private fun MaterialDayPicker.chipAt(index: Int): Chip =
        (getChildAt(index) as FrameLayout).getChildAt(0) as Chip

    private fun MaterialDayPicker.labels(): List<String> =
        (0 until childCount).map { chipAt(it).text.toString() }
}
