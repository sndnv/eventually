package eventually.client.activities

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import eventually.client.R
import eventually.client.activities.helpers.TaskDetails.initTaskDetails
import eventually.client.activities.helpers.TaskManagement
import eventually.client.databinding.ActivityNewTaskBinding

class NewTaskActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityNewTaskBinding = DataBindingUtil.setContentView(this, R.layout.activity_new_task)
        val fields = initTaskDetails(binding = binding.details, task = null, operation = getString(R.string.new_task_action))

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
