package eventually.client.settings

import android.content.SharedPreferences
import java.security.Key
import java.time.Duration

object Settings {
    fun SharedPreferences.getSummarySize(): Duration {
        val size = (getString(Keys.SummarySize, Defaults.SummarySize) ?: Defaults.SummarySize).toLong()
        return Duration.ofMinutes(size)
    }

    fun SharedPreferences.getSummaryMaxTasks(): Int {
        return (getString(Keys.SummaryMaxTasks, Defaults.SummaryMaxTasks) ?: Defaults.SummaryMaxTasks).toInt()
    }

    fun SharedPreferences.getPostponeLength(): Duration {
        val size = (getString(Keys.PostponeLength, Defaults.PostponeLength) ?: Defaults.PostponeLength).toLong()
        return Duration.ofMinutes(size)
    }

    fun SharedPreferences.getDateTimeFormat(): DateTimeFormat {
        return parseDateTimeFormat(getString(Keys.DateTimeFormat, Defaults.DateTimeFormat) ?: Defaults.DateTimeFormat)
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

    object Keys {
        const val SummarySize: String = "summary_size"
        const val SummaryMaxTasks: String = "summary_max_tasks"
        const val PostponeLength: String = "postpone_length"
        const val DateTimeFormat: String = "date_time_format"
        const val StatsEnabled: String = "stats_enabled"
        const val ShowAllInstances: String = "show_all_instances"
    }

    object Defaults {
        const val SummarySize: String = "15" // minutes
        const val SummaryMaxTasks: String = "7"
        const val PostponeLength: String = "10" // minutes
        const val DateTimeFormat: String = "system"
        const val StatsEnabled: Boolean = false
        const val ShowAllInstances: Boolean = false
    }

    sealed class DateTimeFormat {
        object System : DateTimeFormat()
        object Iso : DateTimeFormat()
    }
}
