package eventually.test.client.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import eventually.client.scheduling.AlarmManagerExtensions.deleteAlarmTimeout
import eventually.client.scheduling.AlarmManagerExtensions.deleteEvaluationAlarm
import eventually.client.scheduling.AlarmManagerExtensions.putAlarmTimeout
import eventually.client.scheduling.AlarmManagerExtensions.putEvaluationAlarm
import eventually.client.scheduling.SchedulerService
import eventually.client.serialization.Extras.requireInstanceId
import eventually.client.serialization.Extras.requireTaskId
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

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
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun createExactEvaluationAlarmsWhenAllowed() {
        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        every { manager.canScheduleExactAlarms() } returns true
        justRun { manager.setExactAndAllowWhileIdle(any(), any(), any()) }

        manager.putEvaluationAlarm(context, instant = Instant.now().plusSeconds(42))

        verify(exactly = 1) { manager.canScheduleExactAlarms() }
        verify(exactly = 1) { manager.setExactAndAllowWhileIdle(any(), any(), any()) }

        confirmVerified(manager)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun createInexactEvaluationAlarmsWhenExactAlarmsAreNotAllowed() {
        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        every { manager.canScheduleExactAlarms() } returns false
        justRun { manager.setAndAllowWhileIdle(any(), any(), any()) }

        manager.putEvaluationAlarm(context, instant = Instant.now().plusSeconds(42))

        verify(exactly = 1) { manager.canScheduleExactAlarms() }
        verify(exactly = 1) { manager.setAndAllowWhileIdle(any(), any(), any()) }

        confirmVerified(manager)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun createInexactEvaluationAlarmsWhenExactAlarmsAreRejected() {
        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        every { manager.canScheduleExactAlarms() } returns true
        every { manager.setExactAndAllowWhileIdle(any(), any(), any()) } throws SecurityException()
        justRun { manager.setAndAllowWhileIdle(any(), any(), any()) }

        manager.putEvaluationAlarm(context, instant = Instant.now().plusSeconds(42))

        verify(exactly = 1) { manager.canScheduleExactAlarms() }
        verify(exactly = 1) { manager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 1) { manager.setAndAllowWhileIdle(any(), any(), any()) }

        confirmVerified(manager)
    }

    @Test
    fun createAlarmTimeouts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val task = 42
        val instance = UUID.randomUUID()
        val instant = Instant.now().plusSeconds(600)

        val intent = slot<PendingIntent>()

        val manager = mockk<AlarmManager>()
        justRun { manager.setExactAndAllowWhileIdle(any(), any(), capture(intent)) }

        manager.putAlarmTimeout(context, task, instance, instant)

        verify(exactly = 1) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant.toEpochMilli(), any())
        }
        confirmVerified(manager)

        val pending = shadowOf(intent.captured)
        assertThat(pending.isServiceIntent, equalTo(true))
        assertThat(pending.savedIntent.component?.className, equalTo(SchedulerService::class.java.name))
        assertThat(pending.savedIntent.action, equalTo(SchedulerService.ActionAlarmTimeout))
        assertThat(pending.savedIntent.requireTaskId(SchedulerService.ActionAlarmTimeoutExtraTask), equalTo(task))
        assertThat(pending.savedIntent.requireInstanceId(SchedulerService.ActionAlarmTimeoutExtraInstance), equalTo(instance))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun createInexactAlarmTimeoutsWhenExactAlarmsAreNotAllowed() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<AlarmManager>()
        every { manager.canScheduleExactAlarms() } returns false
        justRun { manager.setAndAllowWhileIdle(any(), any(), any()) }

        manager.putAlarmTimeout(context, 42, UUID.randomUUID(), Instant.now().plusSeconds(600))

        verify(exactly = 1) { manager.canScheduleExactAlarms() }
        verify(exactly = 1) { manager.setAndAllowWhileIdle(any(), any(), any()) }

        confirmVerified(manager)
    }

    @Test
    fun removeAlarmTimeouts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val task = 42
        val instance = UUID.randomUUID()

        val created = slot<PendingIntent>()
        val removed = slot<PendingIntent>()

        val manager = mockk<AlarmManager>()
        justRun { manager.setExactAndAllowWhileIdle(any(), any(), capture(created)) }
        justRun { manager.cancel(capture(removed)) }

        manager.putAlarmTimeout(context, task, instance, Instant.now().plusSeconds(600))
        manager.deleteAlarmTimeout(context, task, instance)

        verify(exactly = 1) { manager.setExactAndAllowWhileIdle(any(), any(), any()) }
        verify(exactly = 1) { manager.cancel(any<PendingIntent>()) }
        confirmVerified(manager)

        assertThat(removed.captured, equalTo(created.captured))
        assertThat(shadowOf(created.captured).isCanceled, equalTo(true))
    }

    @Test
    fun ignoreMissingAlarmTimeouts() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<AlarmManager>()

        manager.deleteAlarmTimeout(context, 42, UUID.randomUUID())

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
