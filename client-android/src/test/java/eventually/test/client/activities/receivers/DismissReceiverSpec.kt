package eventually.test.client.activities.receivers

import android.content.Context
import android.content.Intent
import eventually.client.activities.receivers.DismissReceiver
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putTaskId
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireTaskId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DismissReceiverSpec {
    @Test
    fun dismissTaskInstances() {
        val task = 42
        val instance = UUID.randomUUID()

        val intent = slot<Intent>()

        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        val receiver = DismissReceiver()
        receiver.onReceive(
            context,
            Intent(context, DismissReceiver::class.java).apply {
                putTaskId(DismissReceiver.ExtraTask, task)
                putInstanceId(DismissReceiver.ExtraInstance, instance)
            }
        )

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionDismiss))
        assertThat(intent.captured.requireTaskId(SchedulerService.ActionDismissExtraTask), equalTo(task))
        assertThat(intent.captured.requireInstanceId(SchedulerService.ActionDismissExtraInstance), equalTo(instance))
    }
}
