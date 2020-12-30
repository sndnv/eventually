package eventually.test.client

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun <T> LiveData<T>.await(): T = await(duration = Duration.ofSeconds(3))

fun <T> LiveData<T>.await(duration: Duration): T {
    var result: T? = null

    val count = CountDownLatch(1)

    (object : Observer<T> {
        override fun onChanged(t: T) {
            result = t
            count.countDown()
            this@await.removeObserver(this)
        }
    }).apply(::observeForever)

    count.await(duration.toMillis(), TimeUnit.MILLISECONDS)

    return result ?: throw TimeoutException("Failed to retrieve data")
}
