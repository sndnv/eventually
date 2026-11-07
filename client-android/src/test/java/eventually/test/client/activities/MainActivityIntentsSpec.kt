package eventually.test.client.activities

import android.content.Intent
import eventually.client.activities.MainActivity
import eventually.client.activities.MainActivity.Companion.requestedTask
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class MainActivityIntentsSpec {
    @Test
    fun readRequestedTasks() {
        assertThat(Intent().putExtra(MainActivity.ExtraTask, 42).requestedTask(), equalTo(42))
    }

    @Test
    fun ignoreIntentsWithoutTasks() {
        assertThat(Intent().requestedTask(), nullValue())
    }

    @Test
    fun ignoreInvalidTasks() {
        assertThat(Intent().putExtra(MainActivity.ExtraTask, "42").requestedTask(), nullValue())
    }

    @Test
    fun ignoreTasksWhenLaunchedFromHistory() {
        val intent = Intent()
            .putExtra(MainActivity.ExtraTask, 42)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(intent.requestedTask(), nullValue())
    }
}
