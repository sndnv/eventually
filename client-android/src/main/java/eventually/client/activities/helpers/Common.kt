package eventually.client.activities.helpers

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.CharacterStyle
import eventually.client.R
import eventually.core.model.Task
import java.time.DayOfWeek
import java.time.Duration
import java.time.Period
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Common {
    fun Duration.toFields(): Pair<Int, ChronoUnit> {
        return if (seconds > 0 && seconds % 60 == 0L) {
            val minutes = (seconds / 60).toInt()
            if (minutes % 60 == 0) {
                val hours = minutes / 60
                if (hours % 24 == 0) {
                    val days = (hours / 24)
                    days to ChronoUnit.DAYS
                } else {
                    hours to ChronoUnit.HOURS
                }
            } else {
                minutes to ChronoUnit.MINUTES
            }
        } else {
            seconds.toInt() to ChronoUnit.SECONDS
        }
    }

    fun Period.toFields(): Pair<Int, ChronoUnit> {
        return when {
            years > 0 -> years to ChronoUnit.YEARS
            months > 0 -> months to ChronoUnit.MONTHS
            else -> days to ChronoUnit.DAYS
        }
    }

    fun Task.Schedule.Repeating.Interval.toFields(): Pair<Int, ChronoUnit> {
        return when (this) {
            is Task.Schedule.Repeating.Interval.DurationInterval -> this.value.toFields()
            is Task.Schedule.Repeating.Interval.PeriodInterval -> this.value.toFields()
        }
    }

    fun ChronoUnit.asString(context: Context): String = when {
        this == ChronoUnit.YEARS -> context.getString(R.string.duration_plural_years)
        this == ChronoUnit.MONTHS -> context.getString(R.string.duration_plural_months)
        this == ChronoUnit.DAYS -> context.getString(R.string.duration_plural_days)
        this == ChronoUnit.HOURS -> context.getString(R.string.duration_plural_hours)
        this == ChronoUnit.MINUTES -> context.getString(R.string.duration_plural_minutes)
        this == ChronoUnit.SECONDS -> context.getString(R.string.duration_plural_seconds)
        else -> throw IllegalArgumentException("Unexpected ChronoUnit provided: [${this.name}]")
    }

    fun ChronoUnit.asQuantityString(amount: Int, context: Context): String = when {
        this == ChronoUnit.YEARS -> context.resources.getQuantityString(R.plurals.duration_years, amount)
        this == ChronoUnit.MONTHS -> context.resources.getQuantityString(R.plurals.duration_months, amount)
        this == ChronoUnit.DAYS -> context.resources.getQuantityString(R.plurals.duration_days, amount)
        this == ChronoUnit.HOURS -> context.resources.getQuantityString(R.plurals.duration_hours, amount)
        this == ChronoUnit.MINUTES -> context.resources.getQuantityString(R.plurals.duration_minutes, amount)
        this == ChronoUnit.SECONDS -> context.resources.getQuantityString(R.plurals.duration_seconds, amount)
        else -> throw IllegalArgumentException("Unexpected ChronoUnit provided: [${this.name}]")
    }

    fun String.asChronoUnit(context: Context): ChronoUnit = when {
        this == context.getString(R.string.duration_plural_years) -> ChronoUnit.YEARS
        this == context.getString(R.string.duration_plural_months) -> ChronoUnit.MONTHS
        this == context.getString(R.string.duration_plural_days) -> ChronoUnit.DAYS
        this == context.getString(R.string.duration_plural_hours) -> ChronoUnit.HOURS
        this == context.getString(R.string.duration_plural_minutes) -> ChronoUnit.MINUTES
        this == context.getString(R.string.duration_plural_seconds) -> ChronoUnit.SECONDS
        else -> throw IllegalArgumentException("Unexpected ChronoUnit string provided: [$this]")
    }

    fun Set<DayOfWeek>.asString(): String =
        sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

    data class StyledString(val placeholder: String, val content: String, val style: CharacterStyle)

    fun String.renderAsSpannable(vararg strings: StyledString): SpannableString {
        val (prepared, entries) = strings.fold(
            initial = this to emptyList<Pair<Int, StyledString>>()
        ) { (collected, indexes), value ->
            val index = collected.indexOf(value.placeholder)
            val replaced = collected.replace(value.placeholder, value.content)

            (replaced to indexes + (index to value))
        }

        val string = SpannableString(prepared)

        entries.forEach { (index, value) ->
            if (index >= 0) {
                string.setSpan(
                    value.style,
                    index,
                    index + value.content.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return string
    }
}
