package eventually.client.ui.screens

import android.content.SharedPreferences
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.edit
import eventually.client.R
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.settings.Settings
import eventually.client.ui.TextExtensions.withBold
import eventually.client.ui.components.ChildTopBar
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceTheme
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.preferenceCategory
import java.time.Duration
import java.time.Instant
import java.time.format.TextStyle

@Composable
fun SettingsScreen(preferences: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now = remember { Instant.now() }

    val summarySize = rememberStringPreference(
        preferences,
        Settings.Keys.SummarySize,
        Settings.Defaults.SummarySize
    )
    val summaryMaxTasks = rememberStringPreference(
        preferences,
        Settings.Keys.SummaryMaxTasks,
        Settings.Defaults.SummaryMaxTasks
    )
    val dateTimeFormat = rememberStringPreference(
        preferences,
        Settings.Keys.DateTimeFormat,
        Settings.Defaults.DateTimeFormat
    )
    val firstDayOfWeek = rememberStringPreference(
        preferences,
        Settings.Keys.FirstDayOfWeek,
        Settings.Defaults.FirstDayOfWeek
    )
    val postponeLength = rememberStringPreference(
        preferences,
        Settings.Keys.PostponeLength,
        Settings.Defaults.PostponeLength
    )
    val alarmTimeout = rememberStringPreference(
        preferences,
        Settings.Keys.AlarmTimeout,
        Settings.Defaults.AlarmTimeout
    )
    val dismissedRetention = rememberStringPreference(
        preferences,
        Settings.Keys.DismissedRetention,
        Settings.Defaults.DismissedRetention
    )
    val dismissedLimit = rememberStringPreference(
        preferences,
        Settings.Keys.DismissedLimit,
        Settings.Defaults.DismissedLimit
    )
    val statsEnabled = rememberBooleanPreference(
        preferences,
        Settings.Keys.StatsEnabled,
        Settings.Defaults.StatsEnabled
    )
    val showAllInstances = rememberBooleanPreference(
        preferences,
        Settings.Keys.ShowAllInstances,
        Settings.Defaults.ShowAllInstances
    )

    val summarySizeOptions = rememberOptions(
        values = R.array.settings_summary_size_values,
        entries = R.array.settings_summary_size_entries
    )
    val summaryMaxTasksOptions = rememberOptions(
        values = R.array.settings_summary_max_tasks_values,
        entries = R.array.settings_summary_max_tasks_entries
    )
    val dateTimeFormatOptions = rememberOptions(
        values = R.array.settings_date_time_format_values,
        entries = R.array.settings_date_time_format_entries
    )
    val firstDayOfWeekOptions = rememberOptions(
        values = R.array.settings_first_day_of_week_values,
        entries = R.array.settings_first_day_of_week_entries
    )

    Scaffold(
        topBar = { ChildTopBar(subtitle = stringResource(R.string.settings_subtitle), onBack = onBack) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        ProvidePreferenceTheme {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                category(R.string.settings_category_summary)

                dropdown(summarySize, summarySizeOptions, R.string.settings_summary_size_title) { value ->
                    val (amount, unit) = Duration.ofMinutes(value.toLong()).toFields()
                    pluralStringResource(R.plurals.settings_summary_size_hint, amount)
                        .withBold(amount.toString(), unit.asQuantityString(amount, context))
                }

                dropdown(summaryMaxTasks, summaryMaxTasksOptions, R.string.settings_summary_max_tasks_title) { value ->
                    stringResource(R.string.settings_summary_max_tasks_hint).withBold(value)
                }

                category(R.string.settings_category_date_time)

                dropdown(dateTimeFormat, dateTimeFormatOptions, R.string.settings_date_time_format_title) { value ->
                    val format = Settings.parseDateTimeFormat(value)
                    stringResource(R.string.settings_date_time_format_hint)
                        .withBold(now.formatAsDate(format), now.formatAsTime(format))
                }

                dropdown(firstDayOfWeek, firstDayOfWeekOptions, R.string.settings_first_day_of_week_title) { value ->
                    stringResource(R.string.settings_first_day_of_week_hint)
                        .withBold(Settings.parseDay(value).getDisplayName(TextStyle.FULL, locale))
                }

                category(R.string.settings_category_other)

                number(postponeLength, R.string.settings_postpone_length_title, R.plurals.settings_postpone_length_hint)

                number(alarmTimeout, R.string.settings_alarm_timeout_title, R.plurals.settings_alarm_timeout_hint)

                category(R.string.settings_category_dismissed)

                number(
                    dismissedRetention,
                    R.string.settings_dismissed_retention_title,
                    R.plurals.settings_dismissed_retention_hint
                )

                number(dismissedLimit, R.string.settings_dismissed_limit_title, R.plurals.settings_dismissed_limit_hint)

                category(R.string.settings_category_debug)

                switch(statsEnabled, R.string.settings_stats_enabled_title)

                switch(showAllInstances, R.string.settings_show_all_instances_title)
            }
        }
    }
}

private fun LazyListScope.category(@StringRes title: Int) =
    preferenceCategory(key = "category_$title", title = { Text(text = stringResource(title)) })

private fun LazyListScope.dropdown(
    preference: PreferenceValue<String>,
    options: Options,
    @StringRes title: Int,
    summary: @Composable (String) -> AnnotatedString
) = item(key = preference.key) {
    ListPreference(
        value = preference.current,
        onValueChange = preference.update,
        values = options.values,
        title = { Text(text = stringResource(title)) },
        modifier = Modifier.fillMaxWidth(),
        summary = { Text(text = summary(preference.current)) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(options.entryFor(it)) }
    )
}

private fun LazyListScope.number(
    preference: PreferenceValue<String>,
    @StringRes title: Int,
    @PluralsRes summary: Int
) = item(key = preference.key) {
    TextFieldPreference(
        value = preference.current,
        onValueChange = preference.update,
        title = { Text(text = stringResource(title)) },
        textToValue = { text -> text.takeIf { (it.toIntOrNull() ?: 0) > 0 } },
        modifier = Modifier.fillMaxWidth(),
        summary = {
            val amount = preference.current.toInt()
            Text(text = pluralStringResource(summary, amount).withBold(amount.toString()))
        },
        textField = { value, onValueChange, onOk ->
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions { onOk() },
                singleLine = true
            )
        }
    )
}

private fun LazyListScope.switch(preference: PreferenceValue<Boolean>, @StringRes title: Int) =
    item(key = preference.key) {
        SwitchPreference(
            value = preference.current,
            onValueChange = preference.update,
            title = { Text(text = stringResource(title)) },
            modifier = Modifier.fillMaxWidth()
        )
    }

private class PreferenceValue<T>(val key: String, val current: T, val update: (T) -> Unit)

@Composable
private fun rememberStringPreference(
    preferences: SharedPreferences,
    key: String,
    default: String
): PreferenceValue<String> {
    var current by remember(preferences, key) { mutableStateOf(preferences.getString(key, default) ?: default) }

    return PreferenceValue(key, current) { updated ->
        current = updated
        preferences.edit { putString(key, updated) }
    }
}

@Composable
private fun rememberBooleanPreference(
    preferences: SharedPreferences,
    key: String,
    default: Boolean
): PreferenceValue<Boolean> {
    var current by remember(preferences, key) { mutableStateOf(preferences.getBoolean(key, default)) }

    return PreferenceValue(key, current) { updated ->
        current = updated
        preferences.edit { putBoolean(key, updated) }
    }
}

private class Options(val values: List<String>, val entries: List<String>) {
    fun entryFor(value: String): String = entries.getOrNull(values.indexOf(value)) ?: value
}

@Composable
private fun rememberOptions(@ArrayRes values: Int, @ArrayRes entries: Int): Options =
    Options(values = stringArrayResource(values).toList(), entries = stringArrayResource(entries).toList())
