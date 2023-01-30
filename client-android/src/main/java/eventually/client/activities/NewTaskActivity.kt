package eventually.client.activities

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import eventually.client.R
import eventually.client.activities.helpers.TaskDetails.initTaskDetails
import eventually.client.activities.helpers.TaskManagement
import eventually.client.databinding.ActivityNewTaskBinding
import eventually.client.persistence.tasks.TaskViewModel
import java.time.Instant

class NewTaskActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)

        val date = when (val date = intent.extras?.getLong(ExtraDate)) {
            null -> null
            else  -> when {
                date > 0 -> Instant.ofEpochMilli(date)
                else -> null
            }
        }

        taskViewModel.goals.observe(this) { goals ->
            taskViewModel.goals.removeObservers(this)

            val binding: ActivityNewTaskBinding = DataBindingUtil.setContentView(this, R.layout.activity_new_task)

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
