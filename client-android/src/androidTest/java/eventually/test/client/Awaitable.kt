package eventually.test.client

import java.time.Duration

fun <T> eventually(f: () -> T): T =
    eventually(
        totalTimeout = Duration.ofSeconds(3),
        retryWait = Duration.ofMillis(50),
        f = f
    )

fun <T> eventually(
    totalTimeout: Duration,
    retryWait: Duration,
    f: () -> T
): T {
    require(totalTimeout >= retryWait) { "[retryWait] cannot be larger than [totalTimeout]" }

    val maxRetries = totalTimeout.toMillis() / retryWait.toMillis()

    tailrec fun retry(f: () -> T, retries: Long): T {
        val outcome = try {
            Awaitable.Try.Success(f())
        } catch (e: Throwable) {
            Awaitable.Try.Failure(e)
        }

        return when (outcome) {
            is Awaitable.Try.Success ->
                outcome.result

            is Awaitable.Try.Failure -> {
                if (retries > 0) {
                    Thread.sleep(retryWait.toMillis())
                    retry(f, retries - 1)
                } else {
                    throw outcome.e
                }
            }
        }
    }

    return retry(f, retries = maxRetries)
}

object Awaitable {
    sealed class Try<T> {
        data class Success<T>(val result: T) : Try<T>()
        data class Failure<T>(val e: Throwable) : Try<T>()
    }
}
