package eventually.client.serialization

import android.content.Intent
import eventually.client.persistence.Converters.Companion.toJson
import eventually.client.persistence.Converters.Companion.toTask
import eventually.core.model.Task
import java.time.Duration
import java.util.UUID

object Extras {
    fun Intent.putTask(extra: String, task: Task): Intent {
        return putExtra(extra, task.toJson())
    }

    fun Intent.requireTask(extra: String): Task {
        val task = getStringExtra(extra)?.toTask()
        require(task != null) { "Expected task [$extra] but none was provided" }

        return task
    }

    fun Intent.putTaskId(extra: String, taskId: Int): Intent {
        require(taskId != MissingTaskId) { "Invalid task ID [$extra] provided" }

        return putExtra(extra, taskId)
    }

    fun Intent.requireTaskId(extra: String): Int {
        val taskId = getIntExtra(extra, MissingTaskId)
        require(taskId != MissingTaskId) { "Expected task ID [$extra] but none was provided" }

        return taskId
    }

    fun Intent.putInstanceId(extra: String, instanceId: UUID): Intent {
        return putExtra(extra, instanceId.toString())
    }

    fun Intent.requireInstanceId(extra: String): UUID {
        val instanceId = getStringExtra(extra)?.let(UUID::fromString)
        require(instanceId != null) { "Expected instance ID [$extra] but none was provided" }

        return instanceId
    }

    fun Intent.putDuration(extra: String, duration: Duration): Intent {
        val durationInMillis = duration.toMillis()
        require(durationInMillis != MissingDuration) { "Invalid duration [$extra] provided" }

        return putExtra(extra, durationInMillis)
    }

    fun Intent.requireDuration(extra: String): Duration {
        val duration = getLongExtra(extra, MissingDuration)
        require(duration != MissingDuration) { "Expected duration [$extra] but none was provided" }

        return Duration.ofMillis(duration)
    }

    private const val MissingTaskId: Int = Int.MIN_VALUE
    private const val MissingDuration: Long = Long.MIN_VALUE
}
