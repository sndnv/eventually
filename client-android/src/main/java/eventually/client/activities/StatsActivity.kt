package eventually.client.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import eventually.client.R
import eventually.client.activities.helpers.Common
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.client.persistence.schedules.TaskScheduleViewModel
import eventually.client.persistence.tasks.TaskViewModel
import eventually.client.scheduling.SchedulerService
import kotlinx.android.synthetic.main.activity_stats.stat_evaluations
import kotlinx.android.synthetic.main.activity_stats.stat_last_evaluation
import kotlinx.android.synthetic.main.activity_stats.stat_next_evaluation
import kotlinx.android.synthetic.main.activity_stats.stat_notifications
import kotlinx.android.synthetic.main.activity_stats.stat_schedules
import kotlinx.android.synthetic.main.activity_stats.stat_tasks
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StatsActivity : AppCompatActivity() {
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskScheduleViewModel: TaskScheduleViewModel
    private lateinit var notificationViewModel: NotificationViewModel

    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@StatsActivity.service = binder.service
            this@StatsActivity.serviceConnected = true

            binder.service.lastEvaluation.observe(this@StatsActivity, { lastEvaluation ->
                val evaluation = lastEvaluation.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                stat_last_evaluation.update(
                    title = getString(R.string.stats_title_last_evaluation),
                    content = getString(R.string.stats_content_last_evaluation)
                        .renderAsSpannable(
                            Common.StyledString(
                                placeholder = "%1\$s",
                                content = evaluation,
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                )
            })

            binder.service.nextEvaluation.observe(this@StatsActivity, { nextEvaluation ->
                val evaluation = nextEvaluation.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                stat_next_evaluation.update(
                    title = getString(R.string.stats_title_next_evaluation),
                    content = getString(R.string.stats_content_next_evaluation)
                        .renderAsSpannable(
                            Common.StyledString(
                                placeholder = "%1\$s",
                                content = evaluation,
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                )
            })

            binder.service.evaluations.observe(this@StatsActivity, { evaluations ->
                stat_evaluations.update(
                    title = getString(R.string.stats_title_evaluations),
                    content = getString(R.string.stats_content_evaluations)
                        .renderAsSpannable(
                            Common.StyledString(
                                placeholder = "%1\$s",
                                content = evaluations.toString(),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                )
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            this@StatsActivity.serviceConnected = false
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SchedulerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)
        taskScheduleViewModel = ViewModelProvider(this).get(TaskScheduleViewModel::class.java)
        notificationViewModel = ViewModelProvider(this).get(NotificationViewModel::class.java)

        taskViewModel.tasks.observe(this, { tasks ->
            val total = tasks.size
            val enabled = tasks.count { it.isActive }
            val disabled = total - enabled

            stat_tasks.update(
                title = getString(R.string.stats_title_tasks),
                content = getString(R.string.stats_content_tasks)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = total.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        Common.StyledString(
                            placeholder = "%2\$s",
                            content = enabled.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        Common.StyledString(
                            placeholder = "%3\$s",
                            content = disabled.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            )
        })

        taskScheduleViewModel.schedules.observe(this, { schedules ->
            val total = schedules.size
            val instances = schedules.flatMap { it.instances.values }.size

            stat_schedules.update(
                title = getString(R.string.stats_title_schedules),
                content = getString(R.string.stats_content_schedules)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = total.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        Common.StyledString(
                            placeholder = "%2\$s",
                            content = instances.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            )
        })

        notificationViewModel.notifications.observe(this, { notifications ->
            val total = notifications.size
            val context = notifications.count { it.type == "context" }
            val execution = notifications.count { it.type == "execution" }

            stat_notifications.update(
                title = getString(R.string.stats_title_notifications),
                content = getString(R.string.stats_content_notifications)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = total.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        Common.StyledString(
                            placeholder = "%2\$s",
                            content = context.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        Common.StyledString(
                            placeholder = "%3\$s",
                            content = execution.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            )
        })

        val button = findViewById<Button>(R.id.force_evaluation)
        button.setOnClickListener {
            val intent = Intent(this, SchedulerService::class.java)
            intent.action = SchedulerService.ActionEvaluate

            startService(intent)
        }
    }

    companion object {
        fun View.update(title: CharSequence, content: CharSequence) {
            findViewById<TextView>(R.id.stat_list_entry_title).text = title
            findViewById<TextView>(R.id.stat_list_entry_content).text = content
        }
    }
}
