package eventually.client.activities.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.chip.Chip
import eventually.client.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class MaterialDayPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

    private val chips: MutableMap<Weekday, Chip> = mutableMapOf()

    var firstDayOfWeek: Weekday = Weekday.MONDAY
        set(value) {
            val previouslySelected = selectedDays
            field = value
            rebuild()
            setSelectedDays(previouslySelected)
        }

    val selectedDays: List<Weekday>
        get() = Weekday.entries.filter { chips[it]?.isChecked == true }

    init {
        orientation = HORIZONTAL
        rebuild()
    }

    fun setSelectedDays(days: List<Weekday>) {
        val set = days.toSet()
        Weekday.entries.forEach { day -> chips[day]?.isChecked = day in set }
    }

    private fun rebuild() {
        removeAllViews()
        chips.clear()

        val inflater = LayoutInflater.from(context)

        orderedDays(firstDayOfWeek).forEach { day ->
            val cell = FrameLayout(context)
            val chip = inflater.inflate(R.layout.view_day_chip, cell, false) as Chip
            chip.text = day.label()
            cell.addView(chip)

            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            chips[day] = chip
        }
    }

    private fun Weekday.label(): String =
        DayOfWeek.valueOf(name).getDisplayName(TextStyle.NARROW, Locale.getDefault())

    private fun orderedDays(start: Weekday): List<Weekday> {
        val all = Weekday.entries
        val idx = all.indexOf(start)
        return all.drop(idx) + all.take(idx)
    }
}
