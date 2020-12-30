package eventually.test.client.scheduling

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eventually.client.scheduling.WorkManagerExtensions
import eventually.client.scheduling.WorkManagerExtensions.deleteEvaluationAlarm
import eventually.client.scheduling.WorkManagerExtensions.putEvaluationAlarm
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class WorkManagerExtensionsSpec {
    @Test
    fun createEvaluationAlarms() {
        val workRequest = slot<OneTimeWorkRequest>()

        val manager = mockk<WorkManager>()
        every { manager.enqueueUniqueWork(any(), any(), capture(workRequest)) } returns mockk()

        manager.putEvaluationAlarm(instant = Instant.now().plusSeconds(42))

        assertThat(
            workRequest.captured.workSpec.workerClassName,
            containsString(WorkManagerExtensions.EvaluationAlarmWorker::class.simpleName)
        )
    }

    @Test
    fun removeEvaluationAlarms() {
        val manager = mockk<WorkManager>(relaxed = true)

        manager.deleteEvaluationAlarm()

        verify(exactly = 1) { manager.cancelUniqueWork(any()) }

        confirmVerified(manager)
    }

    @Test
    fun provideEvaluationAlarmWorker() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"

        val params = mockk<WorkerParameters>()

        val worker = WorkManagerExtensions.EvaluationAlarmWorker(context, params)

        worker.doWork()

        verify(exactly = 1) { context.packageName }
        verify(exactly = 1) { context.startService(any()) }

        confirmVerified(context)
    }
}
