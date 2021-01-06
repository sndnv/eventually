package eventually.client.activities.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eventually.client.R
import eventually.client.activities.TaskDetailsActivity
import eventually.client.activities.helpers.Common.StyledString
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.scheduling.SchedulerService
import eventually.client.settings.Settings.getFirstDayOfWeek
import eventually.client.settings.Settings.toCalendarDay
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import ru.cleverpumpkin.calendar.CalendarDate
import ru.cleverpumpkin.calendar.CalendarView
import java.util.Date

class TaskCalendarFragment : Fragment() {
    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@TaskCalendarFragment.service = binder.service
            this@TaskCalendarFragment.serviceConnected = true

            binder.service.schedules.observe(viewLifecycleOwner, Observer(::updateView))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            this@TaskCalendarFragment.serviceConnected = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_task_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val calendar = view.findViewById<CalendarView>(R.id.tasks_calendar)
        calendar.setupCalendar(firstDayOfWeek = preferences.getFirstDayOfWeek().toCalendarDay())

        calendar.onDateClickListener = { date ->
            val items = calendar.getDateIndicators(date)
                .filterIsInstance<TaskDateIndicator>()
                .sortedBy { it.instance.execution() }

            if (items.isNotEmpty()) {
                context?.let { context ->
                    MaterialAlertDialogBuilder(context)
                        .setTitle(
                            context.getString(
                                R.string.calendar_dialog_title,
                                date.date.toInstant().formatAsDate(context)
                            )
                        )
                        .setItems(
                            items.map {
                                context.getString(R.string.calendar_dialog_item_text)
                                    .renderAsSpannable(
                                        StyledString(
                                            placeholder = "%1\$s",
                                            content = it.instance.execution().formatAsTime(context),
                                            style = StyleSpan(Typeface.BOLD)
                                        ),
                                        StyledString(
                                            placeholder = "%2\$s",
                                            content = it.task.name,
                                            style = StyleSpan(Typeface.NORMAL)
                                        ),
                                        StyledString(
                                            placeholder = "%3\$s",
                                            content = it.task.goal,
                                            style = StyleSpan(Typeface.ITALIC)
                                        )
                                    )
                            }.toTypedArray()
                        ) { _, which ->
                            val intent = Intent(view.context, TaskDetailsActivity::class.java).apply {
                                val taskId = items[which].task.id
                                putExtra(TaskDetailsActivity.ExtraTask, taskId)
                            }

                            context.startActivity(intent)
                        }
                        .show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(context, SchedulerService::class.java)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        activity?.unbindService(serviceConnection)
    }

    private fun updateView(schedules: Map<Int, TaskSchedule>) {
        val context = requireContext()
        val calendar = view?.findViewById<CalendarView>(R.id.tasks_calendar)
        val indicators = schedules.values.flatMap { schedule ->
            schedule.instances.values.map { instance ->
                TaskDateIndicator(
                    context = context,
                    task = schedule.task,
                    instance = instance
                )
            }
        }

        calendar?.datesIndicators = indicators
    }

    private class TaskDateIndicator(
        context: Context,
        val task: Task,
        val instance: TaskInstance
    ) : CalendarView.DateIndicator {
        override val color: Int = context.getColor(R.color.calendar_event)

        override val date: CalendarDate = CalendarDate(Date.from(instance.execution()))
    }
}
