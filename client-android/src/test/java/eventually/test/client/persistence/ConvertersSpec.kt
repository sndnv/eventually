package eventually.test.client.persistence

import eventually.client.persistence.Converters
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.tasks.TaskEntity
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ConvertersSpec {
    @Test
    fun convertUUIDs() {
        val converters = Converters()

        val original = UUID.randomUUID()
        val converted = converters.uuidToString(original)

        assertThat(converted, equalTo(original.toString()))
        assertThat(converters.stringToUuid(converted), equalTo(original))
    }

    @Test
    fun convertTaskSchedules() {
        val converters = Converters()

        val instantSeconds = 16L
        val startSeconds = 23L
        val durationSeconds = 42L

        val originalOnce = Task.Schedule.Once(
            instant = Instant.ofEpochSecond(instantSeconds)
        )

        val originalRepeating = Task.Schedule.Repeating(
            start = Instant.ofEpochSecond(startSeconds),
            every = Duration.ofSeconds(durationSeconds)
        )

        val convertedOnce = converters.scheduleToString(originalOnce)
        val convertedRepeating = converters.scheduleToString(originalRepeating)

        assertThat(
            convertedOnce,
            equalTo("""{"type":"once","instant":$instantSeconds}""")
        )

        assertThat(
            convertedRepeating,
            equalTo("""{"type":"repeating","start":$startSeconds,"every":$durationSeconds,"days":[1,2,3,4,5,6,7]}""")
        )

        assertThat(converters.stringToSchedule(convertedOnce), equalTo(originalOnce))
        assertThat(converters.stringToSchedule(convertedRepeating), equalTo(originalRepeating))

        assertThat(converters.scheduleToString(schedule = null), equalTo(null))
        assertThat(converters.stringToSchedule(schedule = null), equalTo(null))
    }

    @Test
    fun failToConvertTaskSchedulesFromStringOnInvalidScheduleType() {
        val converters = Converters()

        try {
            converters.stringToSchedule("{}")
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected task schedule type provided: [null]"))
        }
    }

    @Test
    fun failToConvertTaskSchedulesFromStringOnNoInstant() {
        val converters = Converters()

        try {
            converters.stringToSchedule("""{"type":"once"}""")
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected 'instant' field but none was found"))
        }

    }

    @Test
    fun failToConvertTaskSchedulesFromStringOnNoStart() {
        val converters = Converters()

        try {
            converters.stringToSchedule("""{"type":"repeating","every":42}""")
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected 'start' field but none was found"))
        }
    }

    @Test
    fun failToConvertTaskSchedulesFromStringOnNoRepetitionDuration() {
        val converters = Converters()

        try {
            converters.stringToSchedule("""{"type":"repeating","start":42}""")
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected 'every' field but none was found"))
        }
    }

    @Test
    fun convertDurations() {
        val converters = Converters()

        val durationSeconds = 42L

        val original = Duration.ofSeconds(durationSeconds)
        val converted = converters.durationToLong(original)

        assertThat(converted, equalTo(durationSeconds))
        assertThat(converters.longToDuration(converted), equalTo(original))
    }

    @Test
    fun convertScheduleInstances() {
        val converters = Converters()

        val instant = Instant.parse("2000-12-21T21:42:59.00Z")

        val instance1 = TaskInstance(instant = instant)
        val instance2 = TaskInstance(instant = instant.plusSeconds(42)).copy(postponed = Duration.ofMinutes(5))
        val instance3 = TaskInstance(instant = instant.minusSeconds(42))

        val original = mapOf(
            instance1.id to instance1,
            instance2.id to instance2,
            instance3.id to instance3
        )
        val converted = converters.scheduleInstancesToString(original)

        val convertedInstances = listOf(
            """"${instance1.id}":{"id":"${instance1.id}","instant":${instance1.instant.epochSecond}}""",
            """"${instance2.id}":{"id":"${instance2.id}","instant":${instance2.instant.epochSecond},"postponed":${instance2.postponed?.seconds}}""",
            """"${instance3.id}":{"id":"${instance3.id}","instant":${instance3.instant.epochSecond}}"""
        ).joinToString(separator = ",")

        assertThat(converted, equalTo("{${convertedInstances}}"))
        assertThat(converters.stringToScheduleInstances(converted), equalTo(original))
    }

    @Test
    fun convertDismissedScheduleInstances() {
        val converters = Converters()

        val instant1 = Instant.parse("2000-12-21T21:42:59.00Z")
        val instant2 = instant1.plusSeconds(21)
        val instant3 = instant1.minusSeconds(21)

        val original = listOf(instant1, instant2, instant3)
        val converted = converters.scheduleDismissedToString(original)

        assertThat(converted, equalTo("[${instant1.epochSecond},${instant2.epochSecond},${instant3.epochSecond}]"))
        assertThat(converters.stringToScheduleDismissed(converted), equalTo(original))
    }

    @Test
    fun convertTasksToJson() {
        val start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC)

        val original = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = start,
                every = Duration.ofMinutes(20),
                days = setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY)
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        val converted = Converters.taskToString(original)

        assertThat(
            converted, equalTo(
                """{
                "id":42,
                "name":"test-task",
                "description":"test-description",
                "goal":"test-goal",
                "schedule":{"type":"repeating","start":${start.epochSecond},"every":1200,"days":[4,6,3]},
                "context_switch":300,
                "is_active":true
            }""".replace("\n", "").replace(" ", "")
            )
        )

        assertThat(Converters.stringToTask(converted), equalTo(original))
    }

    @Test
    fun convertTasksToEntities() {
        val task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                every = Duration.ofMinutes(20)
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        val entity = TaskEntity(
            id = task.id,
            name = task.name,
            description = task.description,
            goal = task.goal,
            schedule = task.schedule,
            contextSwitch = task.contextSwitch,
            isActive = task.isActive
        )

        assertThat(Converters.taskToEntity(task), equalTo(entity))
        assertThat(Converters.entityToTask(entity), equalTo(task))
    }

    @Test
    fun convertTaskSchedulesToEntities() {
        val task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                every = Duration.ofMinutes(20)
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        val instant = Instant.now()
        val instance1 = TaskInstance(instant = instant)
        val instance2 = TaskInstance(instant = instant.plusSeconds(42)).copy(postponed = Duration.ofMinutes(5))
        val instance3 = TaskInstance(instant = instant.minusSeconds(42))

        val instances = mapOf(
            instance1.id to instance1,
            instance2.id to instance2,
            instance3.id to instance3
        )

        val dismissed = listOf(instant, instant.plusSeconds(21), instant.minusSeconds(21))

        val schedule = TaskSchedule(
            task = task,
            instances = instances,
            dismissed = dismissed
        )

        val entity = TaskScheduleEntity(
            task = task.id,
            instances = instances,
            dismissed = dismissed
        )

        assertThat(Converters.scheduleToEntity(schedule), equalTo(entity))
        assertThat(Converters.entityToSchedule(entity, task), equalTo(schedule))
    }

    @Test
    fun convertTaskAndInstancesToNotificationEntities() {
        val task = Task(
            id = 42,
            name = "test-task",
            description = "test-description",
            goal = "test-goal",
            schedule = Task.Schedule.Repeating(
                start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
                every = Duration.ofMinutes(20)
            ),
            contextSwitch = Duration.ofMinutes(5),
            isActive = true
        )

        val instance = TaskInstance(instant = Instant.now())

        val entity = NotificationEntity(
            id = 0,
            task = task.id,
            instance = instance.id,
            type = "execution",
            hash = instance.hashCode()
        )

        assertThat(Converters.notificationToEntity(task, instance, "execution"), equalTo(entity))
    }
}
