package eventually.client.activities.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import eventually.client.R
import eventually.client.activities.helpers.Common.StyledString
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.settings.Settings
import eventually.client.settings.Settings.getPostponeLength
import eventually.client.settings.Settings.getSummaryMaxTasks
import eventually.client.settings.Settings.getSummarySize
import java.time.Duration
import java.time.Instant

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val summarySize = findPreference<DropDownPreference>(Settings.Keys.SummarySize)
        val summarySizeValue = preferenceManager.sharedPreferences.getSummarySize()
        summarySize?.summary = renderSummarySize(summarySizeValue)
        summarySize?.setOnPreferenceChangeListener { _, newValue ->
            val updatedSummarySizeValue = Duration.ofMinutes(java.lang.Long.parseLong(newValue.toString()))
            summarySize.summary = renderSummarySize(updatedSummarySizeValue)
            true
        }

        val summaryMaxTasks = findPreference<DropDownPreference>(Settings.Keys.SummaryMaxTasks)
        val summaryMaxTasksValue = preferenceManager.sharedPreferences.getSummaryMaxTasks()
        summaryMaxTasks?.summary = renderSummaryMaxTasks(summaryMaxTasksValue.toString())
        summaryMaxTasks?.setOnPreferenceChangeListener { _, newValue ->
            summaryMaxTasks.summary = renderSummaryMaxTasks(newValue.toString())
            true
        }

        val postponeLength = findPreference<EditTextPreference>(Settings.Keys.PostponeLength)
        val postponeLengthValue = preferenceManager.sharedPreferences.getPostponeLength()
        postponeLength?.summary = renderPostponeLength(
            quantity = postponeLengthValue.toMinutes().toInt(),
            value = postponeLengthValue.toMinutes().toString()
        )
        postponeLength?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
        postponeLength?.setOnPreferenceChangeListener { _, newValue ->
            try {
                val value = Integer.parseInt(newValue.toString())
                if (value > 0) {
                    postponeLength.summary = renderPostponeLength(
                        quantity = value,
                        value = value.toString()
                    )
                    true
                } else {
                    false
                }
            } catch (e: NumberFormatException) {
                false
            }
        }

        val context = requireContext()
        val now = Instant.now()
        val dateTimeFormat = findPreference<DropDownPreference>(Settings.Keys.DateTimeFormat)
        dateTimeFormat?.summary = renderDateTimeFormat(
            date = now.formatAsDate(context),
            time = now.formatAsTime(context)
        )
        dateTimeFormat?.setOnPreferenceChangeListener { _, newValue ->
            val updatedFormat = Settings.parseDateTimeFormat(newValue.toString())

            dateTimeFormat.summary = renderDateTimeFormat(
                date = now.formatAsDate(updatedFormat),
                time = now.formatAsTime(updatedFormat)
            )
            true
        }
    }

    private fun renderSummarySize(value: Duration): SpannableString {
        val (amount, unit) = value.toFields()

        return resources.getQuantityString(
            R.plurals.settings_summary_size_hint,
            amount
        )
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = amount.toString(),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = unit.asQuantityString(amount, requireContext()),
                    style = StyleSpan(Typeface.BOLD)
                )
            )
    }

    private fun renderSummaryMaxTasks(value: String): SpannableString =
        getString(R.string.settings_summary_max_tasks_hint)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = value,
                    style = StyleSpan(Typeface.BOLD)
                )
            )

    private fun renderPostponeLength(quantity: Int, value: String): SpannableString =
        resources.getQuantityString(
            R.plurals.settings_postpone_length_hint,
            quantity
        ).renderAsSpannable(
            StyledString(
                placeholder = "%1\$s",
                content = value,
                style = StyleSpan(Typeface.BOLD)
            )
        )

    private fun renderDateTimeFormat(date: String, time: String): SpannableString =
        getString(R.string.settings_date_time_format_hint)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = date,
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = time,
                    style = StyleSpan(Typeface.BOLD)
                )
            )
}
