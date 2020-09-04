package eventually.test.core.model

import eventually.core.model.TaskInstance
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class TaskInstanceSpec : WordSpec({
    "A TaskInstance" should {
        "support postponing its execution time" {
            val postponedDuration = Duration.ofSeconds(1)

            val now = Instant.now()

            val instance = TaskInstance(instant = now)

            instance.postponed shouldBe(null)
            instance.postponed(by = postponedDuration).postponed shouldBe(postponedDuration)
        }

        "provide its next execution time" {
            val postponedDuration = Duration.ofSeconds(42)

            val now = Instant.now()

            val nonPostponedInstance = TaskInstance(instant = now)
            val postponedInstance = TaskInstance(instant = now).postponed(by = postponedDuration)
            val postponedFurtherInstance = postponedInstance.postponed(by = postponedDuration)

            nonPostponedInstance.execution() shouldBe(now)
            postponedInstance.execution() shouldBe(now.plus(postponedDuration))
            postponedFurtherInstance.execution() shouldBe(now.plus(postponedDuration.multipliedBy(2)))
        }
    }
})
