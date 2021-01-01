package eventually.client.activities.helpers

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import eventually.client.R
import eventually.client.activities.helpers.Common.StyledString
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDate
import eventually.client.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.databinding.LayoutTaskPreviewBinding
import eventually.client.settings.Settings.getShowAllInstances
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import kotlinx.android.synthetic.main.list_item_task_instance.view.button_dismiss
import kotlinx.android.synthetic.main.list_item_task_instance.view.button_postpone
import kotlinx.android.synthetic.main.list_item_task_instance.view.task_instance_content
import java.util.UUID

object TaskPreview {
    fun AppCompatActivity.initTaskPreview(
        binding: LayoutTaskPreviewBinding,
        task: Task?,
        schedule: TaskSchedule?,
        handlers: Handlers?
    ) {
        binding.task = task

        val contextSwitch = task?.contextSwitch?.toFields()
        binding.previewContextSwitch.text = getString(
            R.string.task_preview_field_content_context_switch,
            contextSwitch?.first,
            contextSwitch?.second?.asQuantityString(contextSwitch.first, this)
        )

        val taskSchedule = when (val taskSchedule = task?.schedule) {
            is Task.Schedule.Once -> {
                val date = taskSchedule.instant.formatAsDate(this)
                val time = taskSchedule.instant.formatAsTime(this)

                getString(R.string.task_preview_field_content_schedule_once)
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
            is Task.Schedule.Repeating -> {
                val start = taskSchedule.start.formatAsTime(this)
                val every = taskSchedule.every.toFields()

                getString(R.string.task_preview_field_content_schedule_repeating)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = every.first.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = every.second.asQuantityString(every.first, this),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%3\$s",
                            content = start,
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            }
            else -> SpannableString("")
        }

        binding.previewSchedule.text = if (task?.isActive == false) {
            taskSchedule.setSpan(
                StrikethroughSpan(),
                0,
                taskSchedule.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val builder = SpannableStringBuilder()
            builder.append(taskSchedule)
            builder.append(" ")
            builder.append(getString(R.string.task_preview_field_content_inactive))
            builder
        } else {
            taskSchedule
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val showAllInstances = preferences.getShowAllInstances()

        val instances = schedule?.instances?.values?.toList() ?: emptyList()
        val instancesView = findViewById<ListView>(R.id.preview_instances)
        instancesView.adapter = TaskInstanceListItemAdapter(
            context = this,
            resource = R.layout.list_item_task_instance,
            taskActive = schedule?.task?.isActive ?: false,
            instances = if (showAllInstances) instances else instances.take(MaxShownInstances),
            handlers = handlers
        )
        instancesView.emptyView = findViewById<TextView>(R.id.preview_instances_empty)

        val tooManyInstancesView = findViewById<TextView>(R.id.preview_instances_too_many)
        if (showAllInstances || instances.size <= MaxShownInstances) {
            tooManyInstancesView.visibility = View.GONE
        } else {
            tooManyInstancesView.text = getString(R.string.task_preview_instances_too_many_text)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = MaxShownInstances.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = instances.size.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )
            tooManyInstancesView.visibility = View.VISIBLE
        }
    }

    data class Handlers(
        val dismiss: (instance: UUID) -> View.OnClickListener,
        val postpone: (instance: UUID) -> View.OnClickListener
    )

    class TaskInstanceListItemAdapter(
        context: Context,
        private val resource: Int,
        private val taskActive: Boolean,
        private val instances: List<TaskInstance>,
        private val handlers: Handlers?
    ) :
        ArrayAdapter<TaskInstance>(context, resource, instances) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false)) as ConstraintLayout
            val instance = instances[position]

            val execution = instance.execution().formatAsFullDateTime(context)

            val content = context.getString(
                if (instance.postponed != null) {
                    R.string.task_preview_field_content_instance_execution_postponed
                } else {
                    R.string.task_preview_field_content_instance_execution
                },
                execution
            )

            layout.task_instance_content.text = content

            layout.button_dismiss.setOnClickListener(handlers?.let {
                it.dismiss(instance.id)
            })

            layout.button_postpone.setOnClickListener(handlers?.let {
                it.postpone(instance.id)
            })

            layout.button_postpone.isEnabled = taskActive

            return layout
        }
    }

    private const val MaxShownInstances: Int = 100
}
