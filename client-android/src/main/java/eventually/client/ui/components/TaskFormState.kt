package eventually.client.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import eventually.client.activities.helpers.Common.toFields
import eventually.core.model.Task
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Stable
class TaskFormState(
    name: String,
    description: String,
    goal: String,
    scheduleType: ScheduleType,
    onceDate: LocalDate,
    onceTime: LocalTime,
    startDate: LocalDate,
    startTime: LocalTime,
    everyAmount: String,
    everyUnit: ChronoUnit,
    days: Set<DayOfWeek>,
    contextSwitchAmount: String,
    contextSwitchUnit: ChronoUnit,
    isActive: Boolean,
    color: Int,
    val expandable: Boolean,
    expanded: Boolean,
    errors: Set<TaskFormError>
) {
    var name: String by mutableStateOf(name)
    var description: String by mutableStateOf(description)
    var goal: String by mutableStateOf(goal)
    var scheduleType: ScheduleType by mutableStateOf(scheduleType)
    var onceDate: LocalDate by mutableStateOf(onceDate)
    var onceTime: LocalTime by mutableStateOf(onceTime)
    var startDate: LocalDate by mutableStateOf(startDate)
    var startTime: LocalTime by mutableStateOf(startTime)
    var everyAmount: String by mutableStateOf(everyAmount)
    var everyUnit: ChronoUnit by mutableStateOf(everyUnit)
    var days: Set<DayOfWeek> by mutableStateOf(days)
    var contextSwitchAmount: String by mutableStateOf(contextSwitchAmount)
    var contextSwitchUnit: ChronoUnit by mutableStateOf(contextSwitchUnit)
    var isActive: Boolean by mutableStateOf(isActive)
    var color: Int by mutableIntStateOf(color)
    var expanded: Boolean by mutableStateOf(expanded)

    var errors: Set<TaskFormError> by mutableStateOf(errors)
        private set

    fun validate(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        errors = buildSet {
            if (name.isEmpty()) add(TaskFormError.Name)
            if (goal.isEmpty()) add(TaskFormError.Goal)
            contextSwitchAmount
                .durationError(contextSwitchUnit, TaskFormError.ContextSwitch, TaskFormError.ContextSwitchTooLong)
                ?.let(::add)

            if (scheduleType == ScheduleType.Repeating) {
                val everyError = everyAmount.durationError(everyUnit, TaskFormError.Every, TaskFormError.EveryTooLong)

                when {
                    everyError != null -> add(everyError)
                    repeatingSchedule(zone).next(after = Instant.now(), within = Duration.ZERO).isEmpty() ->
                        add(TaskFormError.DaysUnreachable)
                }
            }
        }

        if (errors.any { it in CollapsedErrors }) expanded = true

        return errors.isEmpty()
    }

    fun toTask(id: Int, defaultColor: Int, zone: ZoneId = ZoneId.systemDefault()): Task = Task(
        id = id,
        name = name.trim(),
        description = description.trim(),
        goal = goal.trim(),
        schedule = when (scheduleType) {
            ScheduleType.Once -> Task.Schedule.Once(instant = onceDate.atTime(onceTime).atZone(zone).toInstant())
            ScheduleType.Repeating -> repeatingSchedule(zone)
        },
        contextSwitch = Duration.of(contextSwitchAmount.toLong(), contextSwitchUnit),
        isActive = isActive,
        color = color.takeIf { it != 0 && it != defaultColor }
    )

    private fun repeatingSchedule(zone: ZoneId): Task.Schedule.Repeating = Task.Schedule.Repeating(
        start = startDate.atTime(startTime).atZone(zone).toInstant(),
        every = Task.Schedule.Repeating.Interval.of(everyAmount.toLong(), everyUnit),
        days = days.ifEmpty { Task.Schedule.Repeating.DefaultDays }
    )

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

        val ContextSwitchTypes: List<ChronoUnit> = listOf(
            ChronoUnit.SECONDS,
            ChronoUnit.MINUTES,
            ChronoUnit.HOURS,
            ChronoUnit.DAYS
        )
    }

    companion object {
        fun of(task: Task?, default: Instant, defaultColor: Int, zone: ZoneId = ZoneId.systemDefault()): TaskFormState {
            val once = (task?.schedule as? Task.Schedule.Once)?.instant ?: default.plus(Defaults.ScheduleTimeDuration)
            val repeating = task?.schedule as? Task.Schedule.Repeating
            val start = repeating?.start ?: default.plus(Defaults.ScheduleTimeDuration)
            val (everyAmount, everyUnit) = repeating?.every?.toFields() ?: Defaults.ScheduleRepeatingInterval.toFields()
            val (contextSwitchAmount, contextSwitchUnit) = (task?.contextSwitch ?: Defaults.ContextSwitchDuration).toFields()

            return TaskFormState(
                name = task?.name ?: "",
                description = task?.description ?: "",
                goal = task?.goal ?: "",
                scheduleType = if (repeating != null) ScheduleType.Repeating else ScheduleType.Once,
                onceDate = once.atZone(zone).toLocalDate(),
                onceTime = once.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                startDate = start.atZone(zone).toLocalDate(),
                startTime = start.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                everyAmount = everyAmount.toString(),
                everyUnit = everyUnit,
                days = repeating?.days ?: Task.Schedule.Repeating.DefaultDays,
                contextSwitchAmount = contextSwitchAmount.toString(),
                contextSwitchUnit = contextSwitchUnit,
                isActive = task?.isActive ?: true,
                color = task?.color ?: defaultColor,
                expandable = task == null,
                expanded = task != null,
                errors = emptySet()
            )
        }

        val Saver: Saver<TaskFormState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.name,
                    state.description,
                    state.goal,
                    state.scheduleType,
                    state.onceDate,
                    state.onceTime,
                    state.startDate,
                    state.startTime,
                    state.everyAmount,
                    state.everyUnit,
                    ArrayList(state.days),
                    state.contextSwitchAmount,
                    state.contextSwitchUnit,
                    state.isActive,
                    state.color,
                    state.expandable,
                    state.expanded,
                    ArrayList(state.errors)
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                TaskFormState(
                    name = saved[0] as String,
                    description = saved[1] as String,
                    goal = saved[2] as String,
                    scheduleType = saved[3] as ScheduleType,
                    onceDate = saved[4] as LocalDate,
                    onceTime = saved[5] as LocalTime,
                    startDate = saved[6] as LocalDate,
                    startTime = saved[7] as LocalTime,
                    everyAmount = saved[8] as String,
                    everyUnit = saved[9] as ChronoUnit,
                    days = (saved[10] as List<DayOfWeek>).toSet(),
                    contextSwitchAmount = saved[11] as String,
                    contextSwitchUnit = saved[12] as ChronoUnit,
                    isActive = saved[13] as Boolean,
                    color = saved[14] as Int,
                    expandable = saved[15] as Boolean,
                    expanded = saved[16] as Boolean,
                    errors = (saved[17] as List<TaskFormError>).toSet()
                )
            }
        )

        private val CollapsedErrors: Set<TaskFormError> = setOf(
            TaskFormError.ContextSwitch,
            TaskFormError.ContextSwitchTooLong,
            TaskFormError.DaysUnreachable
        )

        private fun String.isPositiveAmount(): Boolean = (toIntOrNull() ?: 0) > 0

        private fun String.durationError(
            unit: ChronoUnit,
            invalid: TaskFormError,
            tooLong: TaskFormError
        ): TaskFormError? =
            when {
                isPositiveAmount() -> tooLong.takeIf { unit.duration.multipliedBy(toLong()) > Task.MaxDuration }
                (toBigIntegerOrNull()?.signum() ?: 0) > 0 -> tooLong
                else -> invalid
            }
    }
}

enum class ScheduleType {
    Once,
    Repeating
}

enum class TaskFormError {
    Name,
    Goal,
    ContextSwitch,
    ContextSwitchTooLong,
    Every,
    EveryTooLong,
    DaysUnreachable
}

@Composable
fun rememberTaskFormState(task: Task?, default: Instant, defaultColor: Int): TaskFormState =
    rememberSaveable(saver = TaskFormState.Saver) {
        TaskFormState.of(task = task, default = default, defaultColor = defaultColor)
    }
