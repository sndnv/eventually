package eventually.client.persistence

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.tasks.TaskEntity
import eventually.core.model.Task
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import java.time.Duration
import java.time.Instant
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuidToString(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun stringToUuid(uuid: String?): UUID? = uuid?.let(UUID::fromString)

    @TypeConverter
    fun scheduleToString(schedule: Task.Schedule?): String? = schedule?.let {
        Gson().toJson(scheduleToJson(schedule))
    }

    @TypeConverter
    fun stringToSchedule(schedule: String?): Task.Schedule? = schedule?.let {
        jsonToSchedule(Gson().fromJson(schedule, JsonObject::class.java))
    }

    @TypeConverter
    fun durationToLong(duration: Duration?): Long? = duration?.seconds

    @TypeConverter
    fun longToDuration(duration: Long?): Duration? = duration?.let(Duration::ofSeconds)

    @TypeConverter
    fun scheduleInstancesToString(instances: Map<UUID, TaskInstance>?): String? = instances?.let {
        Gson().toJson(instancesToJson(instances))
    }

    @TypeConverter
    fun stringToScheduleInstances(instances: String?): Map<UUID, TaskInstance>? = instances?.let {
        jsonToInstances(Gson().fromJson(instances, JsonObject::class.java))
    }

    @TypeConverter
    fun scheduleDismissedToString(dismissed: List<Instant>?): String? = dismissed?.let {
        Gson().toJson(dismissedToJson(dismissed))
    }

    @TypeConverter
    fun stringToScheduleDismissed(dismissed: String?): List<Instant>? = dismissed?.let {
        jsonToDismissed(Gson().fromJson(dismissed, JsonArray::class.java))
    }

    companion object {
        fun Task.asEntity(): TaskEntity = taskToEntity(this)
        fun TaskEntity.asTask(): Task = entityToTask(this)

        fun Task.toJson(): String = taskToString(this)
        fun String.toTask(): Task = stringToTask(this)

        fun TaskSchedule.asEntity(): TaskScheduleEntity = scheduleToEntity(this)
        fun TaskScheduleEntity.asSchedule(task: Task): TaskSchedule = entityToSchedule(this, task)

        fun Pair<Task, TaskInstance>.asNotificationEntity(type: String): NotificationEntity =
            notificationToEntity(first, second, type)

        fun taskToEntity(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            name = task.name,
            description = task.description,
            goal = task.goal,
            schedule = task.schedule,
            contextSwitch = task.contextSwitch,
            isActive = task.isActive
        )

        fun entityToTask(entity: TaskEntity) = Task(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            goal = entity.goal,
            schedule = entity.schedule,
            contextSwitch = entity.contextSwitch,
            isActive = entity.isActive
        )

        fun taskToString(task: Task): String {
            val json = JsonObject()
            json.addProperty("id", task.id)
            json.addProperty("name", task.name)
            json.addProperty("description", task.description)
            json.addProperty("goal", task.goal)
            json.add("schedule", scheduleToJson(task.schedule))
            json.addProperty("context_switch", task.contextSwitch.seconds)
            json.addProperty("is_active", task.isActive)

            return Gson().toJson(json)
        }

        fun stringToTask(task: String): Task {
            val json = Gson().fromJson(task, JsonObject::class.java)

            return Task(
                id = json.get("id").asInt,
                name = json.get("name").asString,
                description = json.get("description").asString,
                goal = json.get("goal").asString,
                schedule = jsonToSchedule(json.get("schedule").asJsonObject),
                contextSwitch = Duration.ofSeconds(json.get("context_switch").asLong),
                isActive = json.get("is_active").asBoolean
            )
        }

        fun scheduleToEntity(schedule: TaskSchedule): TaskScheduleEntity = TaskScheduleEntity(
            task = schedule.task.id,
            instances = schedule.instances,
            dismissed = schedule.dismissed
        )

        fun entityToSchedule(entity: TaskScheduleEntity, task: Task): TaskSchedule = TaskSchedule(
            task = task,
            instances = entity.instances,
            dismissed = entity.dismissed
        )

        fun notificationToEntity(task: Task, instance: TaskInstance, type: String): NotificationEntity = NotificationEntity(
            id = 0,
            task = task.id,
            instance = instance.id,
            type = type,
            hash = instance.hashCode()
        )

        private fun scheduleToJson(schedule: Task.Schedule): JsonObject = when (schedule) {
            is Task.Schedule.Once -> {
                val json = JsonObject()
                json.addProperty("type", "once")
                json.addProperty("instant", schedule.instant.epochSecond)
                json
            }

            is Task.Schedule.Repeating -> {
                val json = JsonObject()
                json.addProperty("type", "repeating")
                json.addProperty("start", schedule.start.epochSecond)
                json.addProperty("every", schedule.every.seconds)
                json
            }
        }

        private fun jsonToSchedule(schedule: JsonObject): Task.Schedule {
            return when (val type = schedule.get("type")?.asString) {
                "once" -> {
                    val instant = schedule.get("instant")?.asLong
                    require(instant != null) { "Expected 'instant' field but none was found" }
                    Task.Schedule.Once(instant = Instant.ofEpochSecond(instant))
                }

                "repeating" -> {
                    val start = schedule.get("start")?.asLong
                    require(start != null) { "Expected 'start' field but none was found" }

                    val every = schedule.get("every")?.asLong
                    require(every != null) { "Expected 'every' field but none was found" }

                    Task.Schedule.Repeating(
                        start = Instant.ofEpochSecond(start),
                        every = Duration.ofSeconds(every)
                    )
                }

                else -> throw IllegalArgumentException("Unexpected task schedule type provided: [$type]")
            }
        }

        private fun instancesToJson(instances: Map<UUID, TaskInstance>): JsonObject {
            val json = JsonObject()

            instances.forEach { (key, value) ->
                val instance = JsonObject()
                instance.addProperty("id", value.id.toString())
                instance.addProperty("instant", value.instant.epochSecond)
                instance.addProperty("postponed", value.postponed?.seconds)

                json.add(key.toString(), instance)
            }

            return json
        }

        private fun jsonToInstances(instances: JsonObject): Map<UUID, TaskInstance> {
            val gson = Gson()

            return instances.entrySet().map { (key, value) ->
                val json = gson.fromJson(value, JsonObject::class.java)

                val instance = TaskInstance(
                    id = UUID.fromString(json.get("id").asString),
                    instant = Instant.ofEpochSecond(json.get("instant").asLong),
                    postponed = json.get("postponed")?.asLong?.let(Duration::ofSeconds)
                )

                UUID.fromString(key) to instance
            }.toMap()
        }

        private fun dismissedToJson(dismissed: List<Instant>): JsonArray {
            val json = JsonArray()
            dismissed.map { json.add(it.epochSecond) }
            return json
        }

        private fun jsonToDismissed(dismissed: JsonArray): List<Instant> {
            return dismissed.map { Instant.ofEpochSecond(it.asLong) }
        }
    }
}
