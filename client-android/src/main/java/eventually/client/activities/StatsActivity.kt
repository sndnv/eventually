package eventually.client.activities

import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import eventually.client.R
import eventually.client.activities.helpers.Common
import eventually.client.activities.helpers.Common.renderAsSpannable
import eventually.client.databinding.ActivityStatsBinding
import eventually.client.persistence.Converters.Companion.toDataExport
import eventually.client.persistence.Converters.Companion.toJson
import eventually.client.persistence.DataExport
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.client.persistence.schedules.TaskScheduleViewModel
import eventually.client.persistence.tasks.TaskViewModel
import eventually.client.scheduling.SchedulerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StatsActivity : AppCompatActivity() {
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskScheduleViewModel: TaskScheduleViewModel
    private lateinit var notificationViewModel: NotificationViewModel

    private lateinit var binding: ActivityStatsBinding

    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@StatsActivity.service = binder.service
            this@StatsActivity.serviceConnected = true

            binder.service.lastEvaluation.observe(this@StatsActivity) { lastEvaluation ->
                val evaluation = lastEvaluation.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                binding.statLastEvaluation.statListEntryTitle.text = getString(R.string.stats_title_last_evaluation)
                binding.statLastEvaluation.statListEntryContent.text = getString(R.string.stats_content_last_evaluation)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = evaluation,
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            }

            binder.service.nextEvaluation.observe(this@StatsActivity) { nextEvaluation ->
                val evaluation = nextEvaluation.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                binding.statNextEvaluation.statListEntryTitle.text = getString(R.string.stats_title_next_evaluation)
                binding.statNextEvaluation.statListEntryContent.text = getString(R.string.stats_content_next_evaluation)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = evaluation,
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            }

            binder.service.evaluations.observe(this@StatsActivity) { evaluations ->
                binding.statEvaluations.statListEntryTitle.text = getString(R.string.stats_title_evaluations)
                binding.statEvaluations.statListEntryContent.text = getString(R.string.stats_content_evaluations)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = evaluations.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
            }
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

        taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        taskScheduleViewModel = ViewModelProvider(this)[TaskScheduleViewModel::class.java]
        notificationViewModel = ViewModelProvider(this)[NotificationViewModel::class.java]

        binding = DataBindingUtil.setContentView(this, R.layout.activity_stats)

        taskViewModel.tasks.observe(this) { tasks ->
            val total = tasks.size
            val enabled = tasks.count { it.isActive }
            val disabled = total - enabled

            binding.statTasks.statListEntryTitle.text = getString(R.string.stats_title_tasks)
            binding.statTasks.statListEntryContent.text = getString(R.string.stats_content_tasks)
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
        }

        taskScheduleViewModel.schedules.observe(this) { schedules ->
            val total = schedules.size
            val instances = schedules.flatMap { it.instances.values }.size

            binding.statSchedules.statListEntryTitle.text = getString(R.string.stats_title_schedules)
            binding.statSchedules.statListEntryContent.text = getString(R.string.stats_content_schedules)
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
        }

        notificationViewModel.notifications.observe(this) { notifications ->
            val total = notifications.size
            val context = notifications.count { it.type == "context" }
            val execution = notifications.count { it.type == "execution" }

            binding.statNotifications.statListEntryTitle.text = getString(R.string.stats_title_notifications)
            binding.statNotifications.statListEntryContent.text = getString(R.string.stats_content_notifications)
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
        }

        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<Button>(R.id.export_data).setOnClickListener {
            val export = DataExport(
                tasks = taskViewModel.tasks.value ?: emptyList(),
                schedules = taskScheduleViewModel.schedules.value ?: emptyList(),
                notifications = notificationViewModel.notifications.value ?: emptyList()
            )

            ExportDialogFragment(data = export.toJson()).show(supportFragmentManager, ExportDialogTag)
        }

        findViewById<Button>(R.id.import_data).setOnClickListener {
            val dialog = ImportDialogFragment(::importData)
            dialog.show(supportFragmentManager, ImportDialogTag)
        }

        findViewById<Button>(R.id.force_evaluation).setOnClickListener {
            val intent = Intent(this, SchedulerService::class.java)
            intent.action = SchedulerService.ActionEvaluate

            startService(intent)
        }
    }

    private fun importData(data: DataExport) {
        lifecycleScope.launch(Dispatchers.IO) {
            data.tasks.forEach { taskViewModel.put(it).join() }
            data.schedules.forEach { taskScheduleViewModel.put(it).join() }
            data.notifications.forEach { notificationViewModel.put(it).join() }

            val intent = Intent(this@StatsActivity, SchedulerService::class.java)
            intent.action = SchedulerService.ActionReload

            startService(intent)
        }
    }

    class ExportDialogFragment(private val data: String) : DialogFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val view = inflater.inflate(R.layout.dialog_export, container, false)

            val exportedDataView = view.findViewById<TextInputLayout>(R.id.exported_data)
            exportedDataView.editText?.setText(data)

            view.findViewById<Button>(R.id.copy_data).setOnClickListener {
                val context = requireContext()

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.stats_export_data_clip_label),
                        exportedDataView.editText?.text
                    )
                )

                dialog?.dismiss()

                Toast.makeText(context, getString(R.string.stats_export_data_clip_created), Toast.LENGTH_SHORT).show()
            }

            return view
        }
    }

    class ImportDialogFragment(
        private val importData: (DataExport) -> Unit
    ) : DialogFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val view = inflater.inflate(R.layout.dialog_import, container, false)

            view.findViewById<Button>(R.id.load_data).setOnClickListener {
                val importedDataView = view.findViewById<TextInputLayout>(R.id.imported_data)
                importedDataView.isErrorEnabled = false
                importedDataView.error = null

                val json = importedDataView.editText?.text?.toString() ?: ""

                try {
                    importData(json.toDataExport())
                    dialog?.dismiss()
                } catch (e: Throwable) {
                    importedDataView.isErrorEnabled = true
                    importedDataView.error = getString(R.string.stats_import_data_invalid)
                }
            }

            return view
        }
    }

    companion object {
        const val ExportDialogTag: String = "eventually.client.activities.StatsActivity.ExportDialogFragment"
        const val ImportDialogTag: String = "eventually.client.activities.StatsActivity.ImportDialogFragment"
    }
}
