package eventually.test.client.activities.receivers

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import eventually.client.activities.receivers.ExactAlarmPermissionReceiver
import eventually.client.scheduling.SchedulerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ExactAlarmPermissionReceiverSpec {
    @Test
    fun forceScheduleEvaluationOnExactAlarmPermissionChange() {
        val intent = slot<Intent>()

        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        val receiver = ExactAlarmPermissionReceiver()
        receiver.onReceive(
            context,
            Intent(context, ExactAlarmPermissionReceiver::class.java).apply {
                action = AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            }
        )

        assertThat(intent.captured.component?.className, equalTo(SchedulerService::class.java.name))
        assertThat(intent.captured.action, equalTo(SchedulerService.ActionEvaluate))
    }

    @Test
    fun ignoreUnexpectedActions() {
        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"

        val receiver = ExactAlarmPermissionReceiver()
        receiver.onReceive(
            context,
            Intent(context, ExactAlarmPermissionReceiver::class.java).apply {
                action = Intent.ACTION_TIMEZONE_CHANGED
            }
        )

        verify(exactly = 0) { context.startService(any()) }
    }
}
