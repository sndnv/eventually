package eventually.client.settings

import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.Duration
import java.util.Calendar

object Settings {
    fun SharedPreferences?.getSummarySize(): Duration {
        val size = (this?.getString(Keys.SummarySize, Defaults.SummarySize) ?: Defaults.SummarySize).toLong()
        return Duration.ofMinutes(size)
    }

    fun SharedPreferences?.getSummaryMaxTasks(): Int {
        return (this?.getString(Keys.SummaryMaxTasks, Defaults.SummaryMaxTasks) ?: Defaults.SummaryMaxTasks).toInt()
    }

    fun SharedPreferences?.getPostponeLength(): Duration {
        val size = (this?.getString(Keys.PostponeLength, Defaults.PostponeLength) ?: Defaults.PostponeLength).toLong()
        return Duration.ofMinutes(size)
    }

    fun SharedPreferences?.getDateTimeFormat(): DateTimeFormat {
        return parseDateTimeFormat(this?.getString(Keys.DateTimeFormat, Defaults.DateTimeFormat) ?: Defaults.DateTimeFormat)
    }

    fun SharedPreferences?.getFirstDayOfWeek(): DayOfWeek {
        return parseDay(this?.getString(Keys.FirstDayOfWeek, Defaults.FirstDayOfWeek) ?: Defaults.FirstDayOfWeek)
    }

    fun SharedPreferences.getStatsEnabled(): Boolean {
        return getBoolean(Keys.StatsEnabled, Defaults.StatsEnabled)
    }

    fun SharedPreferences.getShowAllInstances(): Boolean {
        return getBoolean(Keys.ShowAllInstances, Defaults.ShowAllInstances)
    }

    fun parseDateTimeFormat(format: String): DateTimeFormat =
        when (format) {
            "system" -> DateTimeFormat.System
            "iso" -> DateTimeFormat.Iso
            else -> throw IllegalArgumentException("Unexpected format found: [$format]")
        }

    fun parseDay(day: String): DayOfWeek {
        return when (day) {
            "system" -> Calendar.getInstance().firstDayOfWeek.toDayOfWeek()
            else -> DayOfWeek.valueOf(day.uppercase())
        }
    }

    fun Int.toDayOfWeek(): DayOfWeek = when (this) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        else -> throw IllegalArgumentException("Unexpected day of the week found: [$this]")
    }

    fun DayOfWeek.toCalendarDay(): Int = when (this) {
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
    }

    object Keys {
        const val SummarySize: String = "summary_size"
        const val SummaryMaxTasks: String = "summary_max_tasks"
        const val PostponeLength: String = "postpone_length"
        const val DateTimeFormat: String = "date_time_format"
        const val FirstDayOfWeek: String = "first_day_of_week"
        const val StatsEnabled: String = "stats_enabled"
        const val ShowAllInstances: String = "show_all_instances"
    }

    object Defaults {
        const val SummarySize: String = "15" // minutes
        const val SummaryMaxTasks: String = "7"
        const val PostponeLength: String = "10" // minutes
        const val DateTimeFormat: String = "system"
        const val FirstDayOfWeek: String = "system"
        const val StatsEnabled: Boolean = false
        const val ShowAllInstances: Boolean = false
    }

    sealed class DateTimeFormat {
        object System : DateTimeFormat()
        object Iso : DateTimeFormat()
    }
}
