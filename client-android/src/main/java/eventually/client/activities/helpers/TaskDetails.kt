package eventually.client.activities.helpers

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import ca.antonious.materialdaypicker.MaterialDayPicker
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import eventually.client.R
import eventually.client.activities.helpers.Common.asChronoUnit
import eventually.client.activities.helpers.Common.asString
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.helpers.DateTimeExtensions.parseAsDate
import eventually.client.activities.helpers.DateTimeExtensions.parseAsDateTime
import eventually.client.activities.helpers.DateTimeExtensions.parseAsLocalTime
import eventually.client.databinding.LayoutTaskDetailsBinding
import eventually.client.settings.Settings
import eventually.client.settings.Settings.getDateTimeFormat
import eventually.core.model.Task
import kotlinx.android.synthetic.main.input_schedule_once.view.date
import kotlinx.android.synthetic.main.input_schedule_once.view.time
import kotlinx.android.synthetic.main.layout_task_details.view.goal_text_input
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

object TaskDetails {
    fun AppCompatActivity.initTaskDetails(
        binding: LayoutTaskDetailsBinding,
        task: Task?,
        goals: List<String>,
        operation: String
    ): Fields {
        binding.task = task
        binding.operation = operation
        binding.active = task?.isActive ?: true

        initGoal(binding, task, goals.distinct().sorted())

        initContextSwitch(binding, task)

        val scheduleType = initSchedule(binding, task)

        initSectionExpansion(binding, task)

        return Fields(
            nameField = binding.name,
            descriptionField = binding.description,
            goalField = binding.goal,
            contextSwitchDurationTypeField = binding.contextSwitch.durationType,
            contextSwitchDurationAmountField = binding.contextSwitch.durationAmount,
            isActiveField = binding.isActive,
            scheduleOnceDateButton = binding.scheduleOnce.date,
            scheduleOnceTimeButton = binding.scheduleOnce.time,
            scheduleRepeatingStartTimeButton = binding.scheduleRepeating.startTime,
            scheduleRepeatingStartDateButton = binding.scheduleRepeating.startDate,
            scheduleRepeatingEveryDurationTypeField = binding.scheduleRepeating.every.durationType,
            scheduleRepeatingEveryDurationAmountField = binding.scheduleRepeating.every.durationAmount,
            scheduleRepeatingDays = binding.scheduleRepeating.days,
            scheduleType = scheduleType,
            context = this
        )
    }

    private fun AppCompatActivity.initSectionExpansion(binding: LayoutTaskDetailsBinding, task: Task?) {
        if (task == null) {
            binding.expandExtraFields.buttonAction.setOnClickListener {
                if (binding.extraFields.visibility == View.GONE) {
                    binding.extraFields.visibility = View.VISIBLE
                    binding.scheduleRepeating.extraDayPicker.visibility = View.VISIBLE
                    binding.expandExtraFields.buttonAction.icon = ContextCompat.getDrawable(this, R.drawable.ic_collapse)
                    binding.expandExtraFields.buttonAction.tooltipText = getString(R.string.section_collapse_tooltip)
                } else {
                    binding.extraFields.visibility = View.GONE
                    binding.scheduleRepeating.extraDayPicker.visibility = View.GONE
                    binding.expandExtraFields.buttonAction.icon = ContextCompat.getDrawable(this, R.drawable.ic_expand)
                    binding.expandExtraFields.buttonAction.tooltipText = getString(R.string.section_expand_tooltip)
                }
            }
        } else {
            binding.extraFields.visibility = View.VISIBLE
            binding.scheduleRepeating.extraDayPicker.visibility = View.VISIBLE
            binding.expandExtraFields.buttonAction.visibility = View.GONE
        }
    }

    private fun AppCompatActivity.initGoal(binding: LayoutTaskDetailsBinding, task: Task?, goals: List<String>) {
        val adapter = ArrayAdapter(this, R.layout.list_item_goal, goals)
        val input = binding.goal.goal_text_input
        input.setAdapter(adapter)
        task?.let { input.setText(it.goal, false) }
    }

    private fun AppCompatActivity.initContextSwitch(binding: LayoutTaskDetailsBinding, task: Task?) {
        val durationTypesAdapter = ArrayAdapter(
            applicationContext,
            R.layout.dropdown_duration_type_item,
            Defaults.DurationTypes.map { it.asString(this) }
        )

        val defaultContextSwitchDuration = Defaults.ContextSwitchDuration.toFields()
        val contextSwitchDuration = task?.contextSwitch?.toFields()

        binding.contextSwitch.durationAmountValue =
            (contextSwitchDuration?.first ?: defaultContextSwitchDuration.first).toString()
        val contextSwitchDurationTypeView = binding.contextSwitch.durationType.editText as? AutoCompleteTextView
        contextSwitchDurationTypeView?.setAdapter(durationTypesAdapter)
        contextSwitchDurationTypeView?.setText(
            (contextSwitchDuration?.second ?: defaultContextSwitchDuration.second).asString(this),
            false
        )
    }

    private fun AppCompatActivity.initSchedule(binding: LayoutTaskDetailsBinding, task: Task?): AtomicReference<String> {
        val durationTypesAdapter = ArrayAdapter(
            applicationContext,
            R.layout.dropdown_duration_type_item,
            Defaults.DurationTypes.map { it.asString(this) }
        )

        val defaultRepeatingScheduleInterval = Defaults.ScheduleRepeatingInterval.toFields()
        val scheduleRepeatingInterval = (task?.schedule as? Task.Schedule.Repeating)?.every?.toFields()

        binding.scheduleRepeating.every.durationAmountValue =
            (scheduleRepeatingInterval?.first ?: defaultRepeatingScheduleInterval.first).toString()
        val scheduleRepeatingEveryDurationTypeView =
            binding.scheduleRepeating.every.durationType.editText as? AutoCompleteTextView
        scheduleRepeatingEveryDurationTypeView?.setAdapter(durationTypesAdapter)
        scheduleRepeatingEveryDurationTypeView?.setText(
            (scheduleRepeatingInterval?.second ?: defaultRepeatingScheduleInterval.second).asString(this),
            false
        )

        val now = Instant.now()

        val scheduleOnce: View = binding.scheduleOnce
        val scheduleRepeating: View = binding.scheduleRepeating.scheduleRepeatingGrid

        val scheduleType: AtomicReference<String> = AtomicReference()

        val scheduleButton = binding.scheduleButton

        fun setScheduleOnce() {
            scheduleButton.check(R.id.schedule_type_once)
            scheduleOnce.visibility = View.VISIBLE
            scheduleRepeating.visibility = View.GONE
            scheduleType.set("once")
        }

        fun setScheduleRepeating() {
            scheduleButton.check(R.id.schedule_type_repeating)
            scheduleOnce.visibility = View.GONE
            scheduleRepeating.visibility = View.VISIBLE
            scheduleType.set("repeating")
        }

        when (task?.schedule) {
            is Task.Schedule.Once -> setScheduleOnce()
            is Task.Schedule.Repeating -> setScheduleRepeating()
        }

        scheduleButton.addOnButtonCheckedListener { _, checkedButton, isChecked ->
            if (isChecked) {
                when (checkedButton) {
                    R.id.schedule_type_once -> setScheduleOnce()
                    R.id.schedule_type_repeating -> setScheduleRepeating()
                }
            }
        }

        initScheduleOnce(scheduleOnce, now, task)
        initScheduleRepeating(scheduleRepeating, now, task)

        if (task == null) {
            setScheduleOnce()
        }

        return scheduleType
    }

    private fun AppCompatActivity.initScheduleOnce(scheduleOnce: View, now: Instant, task: Task?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val scheduleOnceInstant = (task?.schedule as? Task.Schedule.Once)?.instant ?: (now.plus(Defaults.ScheduleTimeDuration))

        val scheduleOnceDateButton = scheduleOnce.findViewById<Button>(R.id.date)
        scheduleOnceDateButton.text = scheduleOnceInstant.formatAsDate(this)

        val scheduleOnceTimeButton = scheduleOnce.findViewById<Button>(R.id.time)
        scheduleOnceTimeButton.text = scheduleOnceInstant.formatAsTime(this)

        scheduleOnceDateButton.setOnClickListener {
            val selected = scheduleOnceDateButton.text.parseAsDate(this)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selected.toEpochMilli())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                scheduleOnceDateButton.text = Instant.ofEpochMilli(selection).formatAsDate(this)
            }

            datePicker.show(supportFragmentManager, datePicker.toString())
        }

        scheduleOnceTimeButton.setOnClickListener {
            val selected = scheduleOnceTimeButton.text.parseAsLocalTime(this)

            val timePickerBuilder = MaterialTimePicker.Builder()
                .setHour(selected.hour)
                .setMinute(selected.minute)

            if (preferences.getDateTimeFormat() == Settings.DateTimeFormat.Iso) {
                timePickerBuilder.setTimeFormat(TimeFormat.CLOCK_24H)
            }

            val timePicker = timePickerBuilder.build()

            timePicker.addOnPositiveButtonClickListener {
                scheduleOnceTimeButton.text = LocalTime.of(timePicker.hour, timePicker.minute).formatAsTime(this)
            }

            timePicker.show(supportFragmentManager, timePicker.toString())
        }
    }

    private fun AppCompatActivity.initScheduleRepeating(scheduleRepeating: View, now: Instant, task: Task?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)

        val schedule = task?.schedule as? Task.Schedule.Repeating

        val scheduleRepeatingInstant = schedule?.start ?: now.plus(Defaults.ScheduleTimeDuration)

        val scheduleRepeatingStartDateButton = scheduleRepeating.findViewById<Button>(R.id.start_date)
        scheduleRepeatingStartDateButton.text = scheduleRepeatingInstant.formatAsDate(this)

        scheduleRepeatingStartDateButton.setOnClickListener {
            val selected = scheduleRepeatingStartDateButton.text.parseAsDate(this)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selected.toEpochMilli())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                scheduleRepeatingStartDateButton.text = Instant.ofEpochMilli(selection).formatAsDate(this)
            }

            datePicker.show(supportFragmentManager, datePicker.toString())
        }

        val scheduleRepeatingStartTimeButton = scheduleRepeating.findViewById<Button>(R.id.start_time)
        scheduleRepeatingStartTimeButton.text = scheduleRepeatingInstant.formatAsTime(this)

        scheduleRepeatingStartTimeButton.setOnClickListener {
            val selected = scheduleRepeatingStartTimeButton.text.parseAsLocalTime(this)

            val timePickerBuilder = MaterialTimePicker.Builder()
                .setHour(selected.hour)
                .setMinute(selected.minute)

            if (preferences.getDateTimeFormat() == Settings.DateTimeFormat.Iso) {
                timePickerBuilder.setTimeFormat(TimeFormat.CLOCK_24H)
            }

            val timePicker = timePickerBuilder.build()

            timePicker.addOnPositiveButtonClickListener {
                scheduleRepeatingStartTimeButton.text = LocalTime.of(timePicker.hour, timePicker.minute).formatAsTime(this)
            }

            timePicker.show(supportFragmentManager, timePicker.toString())
        }

        val days = schedule?.days ?: Task.Schedule.Repeating.DefaultDays
        val daysPicker = scheduleRepeating.findViewById<MaterialDayPicker>(R.id.days)
        daysPicker.setSelectedDays(days.map { MaterialDayPicker.Weekday.valueOf(it.name) })
    }

    object Defaults {
        val ContextSwitchDuration: Duration = Duration.of(15, ChronoUnit.MINUTES)

        val ScheduleTimeDuration: Duration = Duration.of(20, ChronoUnit.MINUTES)

        val ScheduleRepeatingInterval: Duration = Duration.of(1, ChronoUnit.DAYS)

        val DurationTypes: List<ChronoUnit> = listOf(
            ChronoUnit.SECONDS,
            ChronoUnit.MINUTES,
            ChronoUnit.HOURS,
            ChronoUnit.DAYS,
            ChronoUnit.MONTHS,
            ChronoUnit.YEARS
        )
    }

    class Fields(
        private val nameField: TextInputLayout,
        private val descriptionField: TextInputLayout,
        private val goalField: TextInputLayout,
        private val contextSwitchDurationTypeField: TextInputLayout,
        private val contextSwitchDurationAmountField: TextInputLayout,
        private val isActiveField: SwitchMaterial,
        private val scheduleOnceDateButton: Button,
        private val scheduleOnceTimeButton: Button,
        private val scheduleRepeatingStartTimeButton: Button,
        private val scheduleRepeatingStartDateButton: Button,
        private val scheduleRepeatingEveryDurationTypeField: TextInputLayout,
        private val scheduleRepeatingEveryDurationAmountField: TextInputLayout,
        private val scheduleRepeatingDays: MaterialDayPicker,
        private val scheduleType: AtomicReference<String>,
        private val context: Context
    ) {
        fun validate(): Boolean {
            val nameValid = nameField.validateText {
                context.getString(R.string.task_details_field_error_name)
            }

            val goalValid = goalField.validateText {
                context.getString(R.string.task_details_field_error_goal)
            }

            val contextSwitchDurationAmountValid = contextSwitchDurationAmountField.validateDurationAmount {
                context.getString(R.string.task_details_field_error_context_switch_duration)
            }

            contextSwitchDurationTypeField.isErrorEnabled = !contextSwitchDurationAmountValid
            contextSwitchDurationTypeField.error =
                if (contextSwitchDurationAmountValid) null
                else context.getString(R.string.task_details_field_error_context_switch_duration_padding)

            val scheduleValid = if (scheduleType.get() == "repeating") {
                val scheduleRepeatingEveryDurationAmountValid = scheduleRepeatingEveryDurationAmountField.validateDurationAmount {
                    context.getString(R.string.task_details_field_error_schedule_repeating_duration)
                }
                scheduleRepeatingEveryDurationTypeField.isErrorEnabled = !scheduleRepeatingEveryDurationAmountValid
                scheduleRepeatingEveryDurationTypeField.error =
                    if (scheduleRepeatingEveryDurationAmountValid) null
                    else context.getString(R.string.task_details_field_error_schedule_repeating_duration_padding)

                scheduleRepeatingEveryDurationAmountValid
            } else {
                true
            }

            return nameValid && goalValid && contextSwitchDurationAmountValid && scheduleValid
        }

        fun toNewTask(): Task = Task(
            id = 0,
            name = name,
            description = description,
            goal = goal,
            schedule = schedule,
            contextSwitch = contextSwitch,
            isActive = isActive
        )

        fun toUpdatedTask(task: Int): Task = toNewTask().copy(id = task)

        val name: String
            get() = nameField.editText?.text.toString()

        val description: String
            get() = descriptionField.editText?.text.toString()

        val goal: String
            get() = goalField.editText?.text.toString()

        val contextSwitch: Duration
            get() {
                val taskDurationType = contextSwitchDurationTypeField.editText?.text.toString().asChronoUnit(context)
                val taskDurationAmount = contextSwitchDurationAmountField.editText?.text.toString().toLong()
                return Duration.of(taskDurationAmount, taskDurationType)
            }

        val isActive: Boolean
            get() {
                return isActiveField.isChecked
            }

        val schedule: Task.Schedule
            get() = when (val actualScheduleType = scheduleType.get()) {
                "once" -> {
                    val instant = (scheduleOnceDateButton.text to scheduleOnceTimeButton.text).parseAsDateTime(context)
                    Task.Schedule.Once(instant = instant)
                }
                "repeating" -> {
                    val start = (scheduleRepeatingStartDateButton.text to scheduleRepeatingStartTimeButton.text)
                        .parseAsDateTime(context)

                    val durationType = scheduleRepeatingEveryDurationTypeField.editText?.text.toString().asChronoUnit(context)
                    val durationAmount = scheduleRepeatingEveryDurationAmountField.editText?.text.toString().toLong()

                    val every = Task.Schedule.Repeating.Interval.of(durationAmount, durationType)

                    val days = if (scheduleRepeatingDays.selectedDays.isEmpty()) {
                        Task.Schedule.Repeating.DefaultDays
                    } else {
                        scheduleRepeatingDays.selectedDays.map { DayOfWeek.valueOf(it.name) }.toSet()
                    }

                    Task.Schedule.Repeating(start = start, every = every, days = days)
                }
                else -> {
                    throw IllegalArgumentException("Unexpected schedule type encountered: [$actualScheduleType]")
                }
            }
    }

    fun TextInputLayout.validateText(message: () -> String): Boolean =
        validate(isValid = { it?.isNotEmpty() ?: true }, message = message)

    fun TextInputLayout.validateDurationAmount(message: () -> String): Boolean =
        validate(
            isValid = {
                it?.let {
                    try {
                        it.toInt() > 0
                    } catch (e: NumberFormatException) {
                        false
                    }
                } ?: true
            },
            message = message
        )

    private fun TextInputLayout.validate(isValid: (content: String?) -> Boolean, message: () -> String): Boolean {
        val isInvalid = !isValid(editText?.text?.toString())

        isErrorEnabled = isInvalid
        error = if (isInvalid) message() else null

        return !isInvalid
    }
}
