package eventually.client.activities

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import eventually.client.R
import eventually.client.activities.helpers.TaskDetails.initTaskDetails
import eventually.client.activities.helpers.TaskManagement
import eventually.client.databinding.ActivityNewTaskBinding
import eventually.client.persistence.tasks.TaskViewModel
import kotlinx.coroutines.launch
import java.time.Instant

@AndroidEntryPoint
class NewTaskActivity : AppCompatActivity() {
    private val taskViewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val date = when (val date = intent.extras?.getLong(ExtraDate)) {
            null -> null
            else  -> when {
                date > 0 -> Instant.ofEpochMilli(date)
                else -> null
            }
        }

        lifecycleScope.launch {
            val goals = taskViewModel.goals()

            val binding: ActivityNewTaskBinding = DataBindingUtil.setContentView(this@NewTaskActivity, R.layout.activity_new_task)

            findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            val fields = initTaskDetails(
                binding = binding.details,
                task = null,
                default = date ?: Instant.now(),
                operation = getString(R.string.new_task_action),
                goals = goals
            )

            val button = findViewById<Button>(R.id.execute_operation)
            button.setOnClickListener {
                if (fields.validate()) {
                    TaskManagement.putTask(this@NewTaskActivity, fields.toNewTask())

                    Toast.makeText(
                        this@NewTaskActivity,
                        getString(R.string.toast_task_created),
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
            }
        }
    }

    companion object {
        const val ExtraDate: String = "eventually.client.activities.NewTaskActivity.extra_date"
    }
}
