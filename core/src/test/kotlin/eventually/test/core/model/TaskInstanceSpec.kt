package eventually.test.core.model

import eventually.core.model.TaskInstance
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.Instant

class TaskInstanceSpec : WordSpec({
    "A TaskInstance" should {
        "provide its next execution time" {
            val postponedDuration = Duration.ofSeconds(42)

            val instant = Instant.now().plusSeconds(30)

            val nonPostponedInstance = TaskInstance(instant = instant)
            val postponedInstance = TaskInstance(instant = instant).postponed(by = postponedDuration)
            val postponedFurtherInstance = postponedInstance.postponed(by = postponedDuration)

            nonPostponedInstance.execution() shouldBe (instant)
            postponedInstance.execution() shouldBe (instant.plus(postponedDuration))
            postponedFurtherInstance.execution() shouldBe (instant.plus(postponedDuration.multipliedBy(2)))
        }

        "support postponing its execution time (execution time has passed)" {
            val postponedDuration = Duration.ofSeconds(1)

            val instant = Instant.now().minusSeconds(30)

            val instance = TaskInstance(instant = instant)

            instance.instant shouldBe (instant)
            instance.postponed shouldBe (null)

            val updated = instance.postponed(by = postponedDuration)
            updated.instant shouldNotBe (instant)
            updated.postponed shouldBe (postponedDuration)
        }

        "support postponing its execution time (execution time has not passed)" {
            val postponedDuration = Duration.ofSeconds(1)

            val instant = Instant.now().plusSeconds(10)

            val instance = TaskInstance(instant = instant)

            instance.instant shouldBe (instant)
            instance.postponed shouldBe (null)

            val updated = instance.postponed(by = postponedDuration)
            updated.instant shouldBe (instant)
            updated.postponed shouldBe (postponedDuration)

            updated.postponed(by = postponedDuration).postponed shouldBe (postponedDuration.multipliedBy(2))
        }
    }
})
