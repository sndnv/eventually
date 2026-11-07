package eventually.test.client.activities.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eventually.client.activities.receivers.SystemStartedReceiver
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
@Config(sdk = [Config.OLDEST_SDK])
class SystemStartedReceiverSpec {
    @Test
    fun startSchedulerServiceOnBoot() {
        verifySchedulerServiceStarted(Intent.ACTION_BOOT_COMPLETED)
    }

    @Test
    fun startSchedulerServiceOnUpdate() {
        verifySchedulerServiceStarted(Intent.ACTION_MY_PACKAGE_REPLACED)
    }

    @Test
    fun ignoreOtherActions() {
        val context = mockk<Context>(relaxed = true)

        SystemStartedReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun registerForBootAndUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED).forEach { action ->
            val receivers = context.packageManager
                .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
                .map { it.activityInfo.name }

            assertThat(receivers, equalTo(listOf(SystemStartedReceiver::class.java.name)))
        }
    }

    private fun verifySchedulerServiceStarted(action: String) {
        val intent = slot<Intent>()

        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"
        every { context.startForegroundService(capture(intent)) } returns null

        val receiver = SystemStartedReceiver()
        receiver.onReceive(
            context,
            Intent(context, SystemStartedReceiver::class.java).apply {
                this.action = action
            }
        )

        assertThat(intent.captured.component?.className, equalTo(SchedulerService::class.java.name))
        assertThat(intent.captured.action, equalTo(SchedulerService.ActionPruneNotifications))
    }
}
