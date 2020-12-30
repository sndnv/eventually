package eventually.client.activities.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eventually.client.R
import eventually.client.activities.TaskDetailsActivity
import eventually.client.activities.helpers.Common.StyledString
import eventually.client.activities.helpers.Common.asQuantityString
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.activities.helpers.Common.toFields
import eventually.client.activities.helpers.DateTimeExtensions.formatAsDateTime
import eventually.client.activities.helpers.DateTimeExtensions.formatAsTime
import eventually.client.activities.helpers.DateTimeExtensions.isToday
import eventually.client.scheduling.SchedulerService
import eventually.client.settings.Settings.getSummaryMaxTasks
import eventually.client.settings.Settings.getSummarySize
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSummary
import java.time.Instant

class TaskSummaryFragment : Fragment() {
    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@TaskSummaryFragment.service = binder.service
            this@TaskSummaryFragment.serviceConnected = true

            binder.service.summary.observe(viewLifecycleOwner, Observer(::updateView))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            this@TaskSummaryFragment.serviceConnected = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_task_summary, container, false)

    override fun onStart() {
        super.onStart()
        val intent = Intent(context, SchedulerService::class.java)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        activity?.unbindService(serviceConnection)
    }

    private fun updateView(summary: TaskSummary) {
        val context = requireContext()

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        val summarySize = preferences.getSummarySize()
        val maxTasks = preferences.getSummaryMaxTasks()
        val topTasks = maxTasks / 2
        val bottomTasks = maxTasks / 2

        fun renderInstant(instant: Instant): String {
            return if (instant.isToday()) {
                val time = instant.formatAsTime(context)
                getString(R.string.summary_item_instant_today, time)
            } else {
                val (date, time) = instant.formatAsDateTime(context)
                getString(R.string.summary_item_instant, date, time)
            }
        }

        fun renderTask(pair: Pair<Task, TaskInstance>?): SpannableString =
            pair?.let {
                getString(R.string.summary_item_content)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = renderInstant(it.second.execution()),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = it.first.name,
                            style = StyleSpan(Typeface.NORMAL)
                        ),
                        StyledString(
                            placeholder = "%3\$s",
                            content = it.first.goal,
                            style = StyleSpan(Typeface.ITALIC)
                        )
                    )
            } ?: SpannableString("...")

        fun renderExpiredFooter(expired: List<Pair<Task, TaskInstance>>): SpannableString {
            val firstExpiredTask = expired.firstOrNull()?.second?.execution()

            return if (expired.isEmpty()) {
                SpannableString(getString(R.string.summary_expired_footer_empty))
            } else {
                resources.getQuantityString(R.plurals.summary_expired_footer_non_empty, expired.size)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = expired.size.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = firstExpiredTask?.let(::renderInstant) ?: "",
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            }
        }

        fun renderUpcomingFooter(upcoming: List<Pair<Task, TaskInstance>>): SpannableStringBuilder {
            val (amount, unit) = summarySize.toFields()

            return if (upcoming.isEmpty()) {
                SpannableStringBuilder(
                    resources.getQuantityString(R.plurals.summary_upcoming_footer_empty_period, amount)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = amount.toString(),
                                style = StyleSpan(Typeface.BOLD)
                            ),
                            StyledString(
                                placeholder = "%2\$s",
                                content = unit.asQuantityString(amount, context),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                )
            } else {
                val tasks = resources.getQuantityString(R.plurals.summary_upcoming_footer_non_empty_tasks, upcoming.size)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = upcoming.size.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

                val period = resources.getQuantityString(R.plurals.summary_upcoming_footer_non_empty_period, amount)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = amount.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = unit.asQuantityString(amount, context),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

                val builder = SpannableStringBuilder()
                builder.append(tasks)
                builder.append(" ")
                builder.append(period)

                builder
            }
        }


        fun renderContent(tasks: List<Pair<Task, TaskInstance>>): SpannableStringBuilder {
            val summaryTasks = if (tasks.size > maxTasks) {
                tasks.take(topTasks) + listOf<Pair<Task, TaskInstance>?>(null) + tasks.takeLast(bottomTasks)
            } else {
                tasks
            }

            val lastIndex = summaryTasks.size - 1

            val builder = SpannableStringBuilder()
            summaryTasks.withIndex().forEach { task ->
                builder.append(renderTask(task.value))

                if (task.index != lastIndex)
                    builder.append("\n")
            }

            return builder
        }

        val expired = summary.expired.sortedBy { it.second.execution() }
        val upcoming = summary.upcoming.sortedBy { it.second.execution() }

        val expiredContent = view?.findViewById<TextView>(R.id.summary_expired_content)
        if (expired.isNotEmpty()) {
            expiredContent?.visibility = View.VISIBLE
            expiredContent?.text = renderContent(expired)
        } else {
            expiredContent?.visibility = View.GONE
        }

        view?.findViewById<TextView>(R.id.summary_expired_footer)?.text = renderExpiredFooter(expired)

        val upcomingContent = view?.findViewById<TextView>(R.id.summary_upcoming_content)
        if (upcoming.isNotEmpty()) {
            upcomingContent?.visibility = View.VISIBLE
            upcomingContent?.text = renderContent(upcoming)
        } else {
            upcomingContent?.visibility = View.GONE
        }

        view?.findViewById<TextView>(R.id.summary_upcoming_footer)?.text = renderUpcomingFooter(upcoming)

        view?.findViewById<MaterialCardView>(R.id.summary_container_expired)?.setOnClickListener {
            if (expired.isNotEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(getString(R.string.summary_expired_dialog_title))
                    .setItems(
                        expired.map {
                            getString(R.string.summary_expired_dialog_item_text)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = renderInstant(it.second.execution()),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                    StyledString(
                                        placeholder = "%2\$s",
                                        content = it.first.name,
                                        style = StyleSpan(Typeface.NORMAL)
                                    ),
                                    StyledString(
                                        placeholder = "%3\$s",
                                        content = it.first.goal,
                                        style = StyleSpan(Typeface.ITALIC)
                                    )
                                )
                        }.toTypedArray()
                    ) { _, which ->
                        val intent = Intent(context, TaskDetailsActivity::class.java).apply {
                            val taskId = expired[which].first.id
                            putExtra(TaskDetailsActivity.ExtraTask, taskId)
                        }

                        startActivity(intent)
                    }
                    .show()
            }
        }

        view?.findViewById<MaterialCardView>(R.id.summary_container_upcoming)?.setOnClickListener {
            if (upcoming.isNotEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(getString(R.string.summary_upcoming_dialog_title))
                    .setItems(
                        summary.upcoming.map {
                            getString(R.string.summary_upcoming_dialog_item_text)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = renderInstant(it.second.execution()),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                    StyledString(
                                        placeholder = "%2\$s",
                                        content = it.first.name,
                                        style = StyleSpan(Typeface.NORMAL)
                                    ),
                                    StyledString(
                                        placeholder = "%3\$s",
                                        content = it.first.goal,
                                        style = StyleSpan(Typeface.ITALIC)
                                    )
                                )
                        }.toTypedArray()
                    ) { _, which ->
                        val intent = Intent(context, TaskDetailsActivity::class.java).apply {
                            val taskId = summary.upcoming[which].first.id
                            putExtra(TaskDetailsActivity.ExtraTask, taskId)
                        }

                        startActivity(intent)
                    }
                    .show()
            }
        }
    }
}
