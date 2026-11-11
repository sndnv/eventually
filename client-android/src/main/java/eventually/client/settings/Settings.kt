package eventually.client.settings

import android.content.Context
import android.content.SharedPreferences
import eventually.core.model.TaskSummaryConfig
import java.time.DayOfWeek
import java.time.Duration
import java.util.Calendar

object Settings {
    fun Context.defaultPreferences(): SharedPreferences =
        getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

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

    fun SharedPreferences?.getAlarmTimeout(): Duration {
        val minutes = (this?.getString(Keys.AlarmTimeout, Defaults.AlarmTimeout) ?: Defaults.AlarmTimeout).toLong()
        return Duration.ofMinutes(minutes)
    }

    fun SharedPreferences?.getDismissedRetention(): Duration {
        val days = (this?.getString(Keys.DismissedRetention, Defaults.DismissedRetention)
            ?: Defaults.DismissedRetention).toLong()
        return Duration.ofDays(days)
    }

    fun SharedPreferences?.getDismissedLimit(): Int {
        return (this?.getString(Keys.DismissedLimit, Defaults.DismissedLimit) ?: Defaults.DismissedLimit).toInt()
    }

    fun SharedPreferences?.getSummaryConfig(): TaskSummaryConfig = TaskSummaryConfig(
        summarySize = getSummarySize(),
        dismissedRetention = getDismissedRetention(),
        dismissedLimit = getDismissedLimit()
    )

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

    object Keys {
        const val SummarySize: String = "summary_size"
        const val SummaryMaxTasks: String = "summary_max_tasks"
        const val PostponeLength: String = "postpone_length"
        const val DateTimeFormat: String = "date_time_format"
        const val FirstDayOfWeek: String = "first_day_of_week"
        const val StatsEnabled: String = "stats_enabled"
        const val ShowAllInstances: String = "show_all_instances"
        const val DismissedRetention: String = "dismissed_retention"
        const val DismissedLimit: String = "dismissed_limit"
        const val AlarmTimeout: String = "alarm_timeout"

        val SummaryConfig: Set<String> = setOf(SummarySize, DismissedRetention, DismissedLimit)
    }

    object Defaults {
        const val SummarySize: String = "15" // minutes
        const val SummaryMaxTasks: String = "7"
        const val PostponeLength: String = "10" // minutes
        const val DateTimeFormat: String = "system"
        const val FirstDayOfWeek: String = "system"
        const val StatsEnabled: Boolean = false
        const val ShowAllInstances: Boolean = false
        const val DismissedRetention: String = "30"
        const val DismissedLimit: String = "100"
        const val AlarmTimeout: String = "10"
    }

    sealed class DateTimeFormat {
        object System : DateTimeFormat()
        object Iso : DateTimeFormat()
    }
}
