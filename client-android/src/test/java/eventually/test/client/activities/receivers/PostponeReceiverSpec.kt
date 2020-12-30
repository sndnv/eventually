package eventually.test.client.activities.receivers

import android.content.Context
import android.content.Intent
import eventually.client.activities.receivers.PostponeReceiver
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.putDuration
import eventually.client.serialization.Extras.putInstanceId
import eventually.client.serialization.Extras.putTaskId
import eventually.client.serialization.Extras.requireDuration
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
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class PostponeReceiverSpec {
    @Test
    fun postponeTaskInstances() {
        val task = 42
        val instance = UUID.randomUUID()
        val by = Duration.ofMinutes(42)

        val intent = slot<Intent>()

        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"
        every { context.startService(capture(intent)) } returns null

        val receiver = PostponeReceiver()
        receiver.onReceive(
            context,
            Intent(context, PostponeReceiver::class.java).apply {
                putTaskId(PostponeReceiver.ExtraTask, task)
                putInstanceId(PostponeReceiver.ExtraInstance, instance)
                putDuration(PostponeReceiver.ExtraBy, by)
            }
        )

        assertThat(intent.captured.action, equalTo(SchedulerService.ActionPostpone))
        assertThat(intent.captured.requireTaskId(SchedulerService.ActionPostponeExtraTask), equalTo(task))
        assertThat(intent.captured.requireInstanceId(SchedulerService.ActionPostponeExtraInstance), equalTo(instance))
        assertThat(intent.captured.requireDuration(SchedulerService.ActionPostponeExtraBy), equalTo(by))
    }
}