package eventually.test.client.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import eventually.client.scheduling.AlarmManagerExtensions.deleteEvaluationAlarm
import eventually.client.scheduling.AlarmManagerExtensions.putEvaluationAlarm
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class AlarmManagerExtensionsSpec {
    @Test
    fun createEvaluationAlarms() {
        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        justRun { manager.setExactAndAllowWhileIdle(any(), any(), any()) }

        manager.putEvaluationAlarm(context, instant = Instant.now().plusSeconds(42))

        verify(exactly = 1) { manager.setExactAndAllowWhileIdle(any(), any(), any()) }

        confirmVerified(manager)
    }

    @Test
    fun removeEvaluationAlarms() {
        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        justRun { manager.cancel(any<PendingIntent>()) }

        manager.deleteEvaluationAlarm(context)

        verify(exactly = 1) { manager.cancel(any<PendingIntent>()) }

        confirmVerified(manager)
    }
}
