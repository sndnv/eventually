package eventually.client.ui.screens.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.compose.ContentHeightMode
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.VerticalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import eventually.client.R
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.settings.Settings
import eventually.client.ui.TextExtensions.withStyles
import eventually.client.ui.components.ItemListDialog
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TaskCalendarPage(
    schedules: Map<Int, TaskSchedule>,
    firstDayOfWeek: DayOfWeek,
    dateTimeFormat: Settings.DateTimeFormat,
    landscape: Boolean,
    onOpenTask: (Int) -> Unit,
    onCreateTask: (Instant) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val defaultColor = colorResource(R.color.calendar_event)
    val scope = rememberCoroutineScope()

    val instances = remember(schedules) { schedules.byDate(zone) }
    val today = remember { LocalDate.now(zone) }
    val current = remember { YearMonth.now(zone) }

    val state = rememberCalendarState(
        startMonth = current.minusYears(CalendarRangeYears),
        endMonth = current.plusYears(CalendarRangeYears),
        firstVisibleMonth = current,
        firstDayOfWeek = firstDayOfWeek
    )

    var dialogDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var selectedDate by rememberSaveable { mutableStateOf(today) }

    val createOn = { date: LocalDate -> onCreateTask(date.atStartOfDay(zone).toInstant()) }

    var targetMonth by remember { mutableStateOf<YearMonth?>(null) }

    val visibleMonth by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo

            layout.visibleMonthsInfo
                .maxByOrNull { month ->
                    minOf(month.offset + month.size, layout.viewportEndOffset) - maxOf(month.offset, layout.viewportStartOffset)
                }
                ?.month?.yearMonth
                ?: state.firstVisibleMonth.yearMonth
        }
    }

    val scrollByMonths = { months: Long, animated: Boolean ->
        val target = (targetMonth ?: if (landscape) visibleMonth else state.firstVisibleMonth.yearMonth)
            .plusMonths(months)
            .coerceIn(state.startMonth, state.endMonth)

        targetMonth = target

        scope.launch {
            try {
                if (animated) state.animateScrollToMonth(target) else state.scrollToMonth(target)
            } finally {
                if (targetMonth == target) targetMonth = null
            }
        }
    }

    val monthDay = @Composable { day: CalendarDay, style: DayStyle, modifier: Modifier, onSelect: (LocalDate) -> Unit ->
        if (day.position == DayPosition.MonthDate) {
            val dayInstances = instances[day.date].orEmpty()

            Day(
                date = day.date,
                isToday = day.date == today,
                style = style,
                indicators = dayInstances.take(MaxIndicators).map { it.first.indicatorColor(defaultColor) },
                description = day.date.formatAsDate(dateTimeFormat),
                onClick = { if (dayInstances.isEmpty()) createOn(day.date) else onSelect(day.date) },
                modifier = modifier
            )
        }
    }

    if (landscape) {
        LaunchedEffect(state) {
            val pending = dialogDate

            if (pending != null) {
                selectedDate = pending
                dialogDate = null
            }

            state.scrollToMonth(pending?.let(YearMonth::from) ?: state.firstVisibleMonth.yearMonth)
        }

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .semantics { isTraversalGroup = true }
            ) {
                MonthSelector(
                    month = targetMonth ?: visibleMonth,
                    locale = locale,
                    onPreviousYear = { scrollByMonths(-MonthsInYear, false) },
                    onPreviousMonth = { scrollByMonths(-1, true) },
                    onNextMonth = { scrollByMonths(1, true) },
                    onNextYear = { scrollByMonths(MonthsInYear, false) }
                )

                DaysOfWeekHeader(days = daysOfWeek(firstDayOfWeek), locale = locale)

                HorizontalCalendar(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    contentHeightMode = ContentHeightMode.Fill,
                    dayContent = { day ->
                        monthDay(
                            day,
                            DayStyle.Ring(isSelected = day.date == selectedDate),
                            Modifier.fillMaxSize().wrapContentSize()
                        ) { selectedDate = it }
                    }
                )
            }

            val items = instances[selectedDate].orEmpty()

            Agenda(
                title = stringResource(R.string.calendar_dialog_title, selectedDate.formatAsDate(dateTimeFormat)),
                items = items.map { it.agendaItem(dateTimeFormat, defaultColor) },
                onItemClick = { index -> onOpenTask(items[index].first.id) },
                onCreate = { createOn(selectedDate) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .semantics { isTraversalGroup = true }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            YearSelector(
                year = state.firstVisibleMonth.yearMonth.year,
                onPrevious = { scrollByMonths(-MonthsInYear, false) },
                onNext = { scrollByMonths(MonthsInYear, false) }
            )

            DaysOfWeekHeader(days = daysOfWeek(firstDayOfWeek), locale = locale)

            VerticalCalendar(
                modifier = Modifier.fillMaxSize(),
                state = state,
                dayContent = { day -> monthDay(day, DayStyle.Dots, Modifier) { dialogDate = it } },
                monthHeader = { month ->
                    Text(
                        text = month.yearMonth.format(DateTimeFormatter.ofPattern(MonthHeaderPattern, locale)),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }

        dialogDate?.let { date ->
            val items = instances[date].orEmpty()

            ItemListDialog(
                title = stringResource(R.string.calendar_dialog_title, date.formatAsDate(dateTimeFormat)),
                items = items.map { it.agendaItem(dateTimeFormat, defaultColor) },
                onItemClick = { index ->
                    dialogDate = null
                    onOpenTask(items[index].first.id)
                },
                onDismiss = { dialogDate = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialogDate = null
                            createOn(date)
                        }
                    ) {
                        Text(text = stringResource(R.string.new_task_subtitle))
                    }
                }
            )
        }
    }
}

@Composable
private fun YearSelector(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationButton(icon = R.drawable.ic_chevron_left, description = R.string.calendar_previous_year, onClick = onPrevious)
        Text(text = year.toString(), style = MaterialTheme.typography.titleMedium)
        NavigationButton(icon = R.drawable.ic_chevron_right, description = R.string.calendar_next_year, onClick = onNext)
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    locale: Locale,
    onPreviousYear: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onNextYear: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NavigationButton(
            icon = R.drawable.ic_chevron_double_left,
            description = R.string.calendar_previous_year,
            onClick = onPreviousYear
        )

        NavigationButton(
            icon = R.drawable.ic_chevron_left,
            description = R.string.calendar_previous_month,
            onClick = onPreviousMonth
        )

        Text(
            text = month.format(DateTimeFormatter.ofPattern(MonthHeaderPattern, locale)),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.titleMedium.fontSize)
        )

        NavigationButton(
            icon = R.drawable.ic_chevron_right,
            description = R.string.calendar_next_month,
            onClick = onNextMonth
        )

        NavigationButton(
            icon = R.drawable.ic_chevron_double_right,
            description = R.string.calendar_next_year,
            onClick = onNextYear
        )
    }
}

@Composable
private fun NavigationButton(@DrawableRes icon: Int, @StringRes description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(description))
    }
}

@Composable
private fun DaysOfWeekHeader(days: List<DayOfWeek>, locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, locale),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private sealed interface DayStyle {
    data object Dots : DayStyle

    data class Ring(val isSelected: Boolean) : DayStyle
}

@Composable
private fun Day(
    date: LocalDate,
    isToday: Boolean,
    style: DayStyle,
    indicators: List<Color>,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = style is DayStyle.Ring && style.isSelected

    val today = if (isToday && style is DayStyle.Dots) {
        Modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape)
    } else {
        Modifier
    }

    val selection = if (isSelected) Modifier.background(colorResource(R.color.daySelected)) else Modifier
    val ring = if (style is DayStyle.Ring) Modifier.ring(colors = indicators, width = RingWidth) else Modifier

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(today)
            .then(selection)
            .then(ring)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                if (style is DayStyle.Ring) selected = style.isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                isToday && style is DayStyle.Ring -> MaterialTheme.colorScheme.primary
                else -> Color.Unspecified
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else null
        )

        if (style is DayStyle.Dots) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(IndicatorSpacing)
            ) {
                indicators.forEach { color ->
                    Box(modifier = Modifier.size(IndicatorSize).background(color = color, shape = CircleShape))
                }
            }
        }
    }
}

private fun Modifier.ring(colors: List<Color>, width: Dp): Modifier =
    if (colors.isEmpty()) this else drawBehind {
        val stroke = width.toPx()
        val sweep = FullCircleDegrees / colors.size
        val gap = if (colors.size > 1) RingGapDegrees else 0f

        colors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = RingStartDegrees + index * sweep + gap / 2,
                sweepAngle = sweep - gap,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
        }
    }

@Composable
private fun Agenda(
    title: String,
    items: List<AnnotatedString>,
    onItemClick: (Int) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            modifier = Modifier.padding(vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )

        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_agenda_empty_text),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(items) { index, item ->
                    Text(
                        text = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(index) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }

        Button(onClick = onCreate, modifier = Modifier.align(Alignment.End).padding(vertical = 8.dp)) {
            Text(text = stringResource(R.string.new_task_subtitle))
        }
    }
}

@Composable
private fun Pair<Task, TaskInstance>.agendaItem(format: Settings.DateTimeFormat, defaultColor: Color): AnnotatedString {
    val (task, instance) = this

    return stringResource(R.string.calendar_dialog_item_text).withStyles(
        Indicator to SpanStyle(color = task.indicatorColor(defaultColor)),
        instance.execution().formatAsTime(format) to SpanStyle(fontWeight = FontWeight.Bold),
        task.name to SpanStyle(),
        task.goal to SpanStyle(fontStyle = FontStyle.Italic)
    )
}

private fun Map<Int, TaskSchedule>.byDate(zone: ZoneId): Map<LocalDate, List<Pair<Task, TaskInstance>>> =
    values
        .flatMap { schedule -> schedule.instances.values.map { schedule.task to it } }
        .sortedBy { it.second.execution() }
        .groupBy { it.second.execution().atZone(zone).toLocalDate() }

private fun Task.indicatorColor(default: Color): Color = color?.let(::Color) ?: default

private const val CalendarRangeYears: Long = 100
private const val MonthsInYear: Long = 12
private const val MaxIndicators: Int = 3
private const val MonthHeaderPattern: String = "LLLL yyyy"
private const val Indicator: String = "●"
private val IndicatorSize = 6.dp
private val IndicatorSpacing = 2.dp
private val RingWidth = 3.dp
private const val FullCircleDegrees: Float = 360f
private const val RingStartDegrees: Float = -90f
private const val RingGapDegrees: Float = 20f
