package eventually.client.activities

import android.app.NotificationManager
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import eventually.client.R
import eventually.client.activities.helpers.Common.StyledString
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.activities.helpers.TaskDetails.initTaskDetails
import eventually.client.activities.helpers.TaskManagement
import eventually.client.activities.helpers.TaskPreview
import eventually.client.activities.helpers.TaskPreview.initTaskPreview
import eventually.client.databinding.ActivityTaskDetailsBinding
import eventually.client.persistence.tasks.TaskViewModel
import eventually.client.scheduling.NotificationManagerExtensions.deleteInstanceNotifications
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.requireTaskId
import eventually.client.settings.Settings.getPostponeLength
import eventually.core.model.Task
import eventually.core.model.TaskSchedule

class TaskDetailsActivity : AppCompatActivity() {

    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private lateinit var taskViewModel: TaskViewModel
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var binding: ActivityTaskDetailsBinding
    private lateinit var deleteButton: FloatingActionButton

    private lateinit var notificationManager: NotificationManager

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@TaskDetailsActivity.service = binder.service
            this@TaskDetailsActivity.serviceConnected = true

            updateView()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            this@TaskDetailsActivity.serviceConnected = false
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

        taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_task_details)
        deleteButton = findViewById(R.id.button_delete)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBackPressed() {
        if (isEditingEnabled()) {
            disableEditing()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateView() {
        val taskId = intent.requireTaskId(ExtraTask)

        val tasksWithSchedules: LiveData<Pair<List<Task>, Map<Int, TaskSchedule>>> =
            object : MediatorLiveData<Pair<List<Task>, Map<Int, TaskSchedule>>>() {
                var tasks: List<Task>? = null
                var schedules: Map<Int, TaskSchedule>? = null

                init {
                    addSource(taskViewModel.tasks) { tasks ->
                        this.tasks = tasks
                        this.schedules?.let { value = tasks to it }
                    }
                    addSource(service.schedules) { schedules ->
                        this.schedules = schedules
                        this.tasks?.let { value = it to schedules }
                    }
                }
            }

        tasksWithSchedules.observe(this, { (tasks, schedules) ->
            val task = tasks.firstOrNull { it.id == taskId }
            val schedule = schedules[taskId]

            topAppBar = findViewById(R.id.topAppBar)

            topAppBar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.task_edit -> {
                        enableEditing()
                        true
                    }
                    else -> false
                }
            }

            topAppBar.setNavigationOnClickListener {
                onBackPressed()
            }

            deleteButton.setOnClickListener {
                task?.let {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(
                            getString(R.string.existing_task_delete_message)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = task.name,
                                        style = StyleSpan(Typeface.BOLD_ITALIC)
                                    )
                                )
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            TaskManagement.deleteTask(this, task.id)
                            Toast.makeText(this, getString(R.string.toast_task_deleted), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }

            if (!isEditingEnabled()) {
                disableEditing()
            }

            val handlers = task?.let {
                TaskPreview.Handlers(
                    dismiss = { instance ->
                        View.OnClickListener {
                            it.isEnabled = false
                            notificationManager.deleteInstanceNotifications(task.id, instance)
                            TaskManagement.dismissTaskInstance(this, task.id, instance)
                            Toast.makeText(this, getString(R.string.toast_task_dismissed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    postpone = { instance ->
                        View.OnClickListener {
                            it.isEnabled = false
                            notificationManager.deleteInstanceNotifications(task.id, instance)
                            val by = PreferenceManager.getDefaultSharedPreferences(this).getPostponeLength()
                            TaskManagement.postponeTaskInstance(this, task.id, instance, by)
                            Toast.makeText(this, getString(R.string.toast_task_postponed), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            initTaskPreview(
                binding = binding.preview,
                task = task,
                schedule = schedule,
                handlers = handlers
            )

            initTaskDetails(
                binding = binding.details,
                task = task,
                goals = tasks.map { it.goal },
                operation = getString(R.string.existing_task_update_action)
            ).let { fields ->
                task?.let {
                    topAppBar.subtitle = task.name

                    val button = findViewById<Button>(R.id.execute_operation)
                    button.setOnClickListener {
                        if (fields.validate()) {
                            TaskManagement.putTask(this@TaskDetailsActivity, fields.toUpdatedTask(task = task.id))

                            Toast.makeText(
                                this@TaskDetailsActivity,
                                getString(R.string.toast_task_updated),
                                Toast.LENGTH_SHORT
                            ).show()

                            disableEditing()
                        }
                    }
                }
            }
        })
    }

    private fun isEditingEnabled(): Boolean = binding.previewParent.visibility == View.GONE

    private fun disableEditing() {
        topAppBar.menu.findItem(R.id.task_edit).isVisible = true
        deleteButton.isVisible = true
        binding.previewParent.visibility = View.VISIBLE
        binding.detailsParent.visibility = View.GONE
    }

    private fun enableEditing() {
        topAppBar.menu.findItem(R.id.task_edit).isVisible = false
        deleteButton.isVisible = false
        binding.previewParent.visibility = View.GONE
        binding.detailsParent.visibility = View.VISIBLE
    }

    companion object {
        const val ExtraTask: String = "eventually.client.activities.TaskDetailsActivity.extra_task"
    }
}
