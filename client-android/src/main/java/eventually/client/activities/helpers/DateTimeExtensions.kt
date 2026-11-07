package eventually.client.activities.helpers

import android.content.Context
import eventually.client.R
import eventually.client.settings.Settings
import eventually.client.settings.Settings.defaultPreferences
import eventually.client.settings.Settings.getDateTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

object DateTimeExtensions {
    fun Instant.formatAsTime(context: Context): String =
        timeOnlyFormatter(context).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsTime(format: Settings.DateTimeFormat): String =
        timeOnlyFormatter(format).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsDate(format: Settings.DateTimeFormat): String =
        dateOnlyFormatter(format).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsDateTime(context: Context, format: Settings.DateTimeFormat): Pair<String, String> {
        val zoned = this.atZone(ZoneId.systemDefault())

        val today = ZonedDateTime.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        val date = when {
            sameDay(zoned, yesterday) -> context.getString(R.string.date_time_format_yesterday)
            sameDay(zoned, today) -> context.getString(R.string.date_time_format_today)
            sameDay(zoned, tomorrow) -> context.getString(R.string.date_time_format_tomorrow)
            else -> dateOnlyFormatter(format).format(zoned)
        }

        val time = timeOnlyFormatter(format).format(zoned)

        return date to time
    }

    fun LocalTime.formatAsTime(format: Settings.DateTimeFormat): String =
        timeOnlyFormatter(format).format(this)

    fun LocalDate.formatAsDate(format: Settings.DateTimeFormat): String =
        dateOnlyFormatter(format).format(this)

    fun Instant.formatAsFullDateTime(format: Settings.DateTimeFormat): String =
        dateTimeFormatter(format).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.isToday(): Boolean =
        sameDay(this.atZone(ZoneId.systemDefault()), ZonedDateTime.now())

    fun Instant.isTomorrow(): Boolean =
        sameDay(this.atZone(ZoneId.systemDefault()), ZonedDateTime.now().plusDays(1))

    private const val IsoDateOnlyPattern: String = "yyyy-MM-dd"
    private const val IsoTimeOnlyPattern: String = "HH:mm"
    private const val IsoDateTimePattern: String = "yyyy-MM-dd HH:mm"

    private fun timeOnlyFormatter(context: Context): DateTimeFormatter =
        timeOnlyFormatter(context.defaultPreferences().getDateTimeFormat())

    private fun dateOnlyFormatter(format: Settings.DateTimeFormat): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            when (format) {
                Settings.DateTimeFormat.System -> DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    FormatStyle.MEDIUM,
                    null,
                    IsoChronology.INSTANCE,
                    Locale.getDefault()
                )
                Settings.DateTimeFormat.Iso -> IsoDateOnlyPattern
            }
        )

    private fun timeOnlyFormatter(format: Settings.DateTimeFormat): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            when (format) {
                Settings.DateTimeFormat.System -> DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    null,
                    FormatStyle.SHORT,
                    IsoChronology.INSTANCE,
                    Locale.getDefault()
                )
                Settings.DateTimeFormat.Iso -> IsoTimeOnlyPattern
            }
        )

    private fun dateTimeFormatter(format: Settings.DateTimeFormat): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            when (format) {
                Settings.DateTimeFormat.System -> DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    FormatStyle.MEDIUM,
                    FormatStyle.SHORT,
                    IsoChronology.INSTANCE,
                    Locale.getDefault()
                )
                Settings.DateTimeFormat.Iso -> IsoDateTimePattern
            }
        )

    private fun sameDay(a: ZonedDateTime, b: ZonedDateTime): Boolean =
        a.year == b.year && a.monthValue == b.monthValue && a.dayOfMonth == b.dayOfMonth
}
