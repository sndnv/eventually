package eventually.test.client.serialization

import android.content.Intent
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putTask
import eventually.client.serialization.Extras.putTaskId
import eventually.client.serialization.Extras.requireDuration
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireTask
import eventually.client.serialization.Extras.requireTaskId
import eventually.core.model.Task
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ExtrasSpec {
    @Test
    fun putAndRetrieveTasks() {
        val task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                every = Duration.ofMinutes(20)
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        val intent = createIntent().putTask(extra, task)
        assertThat(intent.getStringExtra(extra), not(equalTo(null)))

        val extractedTask = intent.requireTask(extra)
        assertThat(extractedTask, equalTo(task))
    }

    @Test
    fun failToRetrieveTaskOnMissingExtra() {
        val intent = createIntent()

        try {
            intent.requireTask(extra)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected task [$extra] but none was provided"))
        }
    }

    @Test
    fun putAndRetrieveTaskIDs() {
        val taskId = 42
        val intent = createIntent().putTaskId(extra, taskId)
        assertThat(intent.getIntExtra(extra, 0), equalTo(taskId))

        val extractedTaskId = intent.requireTaskId(extra)
        assertThat(extractedTaskId, equalTo(taskId))
    }

    @Test
    fun failToPutInvalidTaskIdAsExtra() {
        val intent = createIntent()

        try {
            intent.putTaskId(extra, taskId = Int.MIN_VALUE)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Invalid task ID [$extra] provided"))
        }
    }

    @Test
    fun failToRetrieveTaskIdOnMissingExtra() {
        val intent = createIntent()

        try {
            intent.requireTaskId(extra)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected task ID [$extra] but none was provided"))
        }
    }

    @Test
    fun putAndRetrieveInstanceIDs() {
        val instanceId = UUID.randomUUID()
        val intent = createIntent().putInstanceId(extra, instanceId)
        assertThat(intent.getStringExtra(extra), equalTo(instanceId.toString()))

        val extractedInstanceId = intent.requireInstanceId(extra)
        assertThat(extractedInstanceId, equalTo(instanceId))
    }

    @Test
    fun failToRetrieveInstanceIdOnMissingExtra() {
        val intent = createIntent()

        try {
            intent.requireInstanceId(extra)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected instance ID [$extra] but none was provided"))
        }
    }

    @Test
    fun putAndRetrieveDurations() {
        val duration = Duration.ofSeconds(1)
        val intent = createIntent().putDuration(extra, duration)
        assertThat(intent.getLongExtra(extra, 0), equalTo(duration.toMillis()))

        val extractedDuration = intent.requireDuration(extra)
        assertThat(extractedDuration, equalTo(duration))
    }

    @Test
    fun failToRetrieveDurationOnMissingExtra() {
        val intent = createIntent()

        try {
            intent.requireDuration(extra)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected duration [$extra] but none was provided"))
        }
    }

    private val extra = "test-extra"

    private fun createIntent(): Intent {
        return object : Intent() {
            val extras = AtomicReference<Map<String, Any>>(emptyMap())

            override fun putExtra(name: String?, value: Int): Intent = update(name!!, value)
            override fun putExtra(name: String?, value: Long): Intent = update(name!!, value)
            override fun putExtra(name: String?, value: String?): Intent = update(name!!, value!!)

            override fun getIntExtra(name: String?, defaultValue: Int): Int = get(name!!) ?: defaultValue
            override fun getLongExtra(name: String?, defaultValue: Long): Long = get(name!!) ?: defaultValue
            override fun getStringExtra(name: String?): String? = get(name!!)

            private fun update(extra: String, value: Any): Intent {
                val entry = mapOf(Pair(extra, value))
                extras.accumulateAndGet(entry) { a, b -> a + b }
                return this
            }

            @Suppress("UNCHECKED_CAST")
            private fun <T> get(extra: String): T? = extras.get()[extra] as T?
        }
    }
}
