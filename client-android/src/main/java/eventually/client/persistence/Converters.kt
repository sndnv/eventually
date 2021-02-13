package eventually.client.persistence

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.schedules.TaskScheduleEntity
import eventually.client.persistence.tasks.TaskEntity
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.core.model.TaskSchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.Period
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
    fun intervalToString(interval: Task.Schedule.Repeating.Interval?): String? = interval?.let {
        Gson().toJson(intervalToJson(interval))
    }

    @TypeConverter
    fun stringToInterval(interval: String?): Task.Schedule.Repeating.Interval? = interval?.let {
        jsonToInterval(Gson().fromJson(interval, JsonObject::class.java))
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

        fun DataExport.toJson(): String = dataExportToString(this)

        fun String.toDataExport(): DataExport = stringToDataExport(this)

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

        fun taskToString(task: Task): String =
            Gson().toJson(taskToJson(task))

        fun stringToTask(task: String): Task =
            jsonToTask(Gson().fromJson(task, JsonObject::class.java))

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

        fun dataExportToString(export: DataExport): String {
            val json = JsonObject()
            json.add("tasks", JsonArray().apply { export.tasks.forEach { add(taskToJson(it)) } })
            json.add("schedules", JsonArray().apply { export.schedules.forEach { add(taskScheduleEntityToJson(it)) } })
            json.add("notifications", JsonArray().apply { export.notifications.forEach { add(notificationEntityToJson(it)) } })

            return Gson().toJson(json)
        }

        fun stringToDataExport(export: String): DataExport {
            val json = Gson().fromJson(export, JsonObject::class.java)

            return DataExport(
                tasks = json.get("tasks").asJsonArray.map { jsonToTask(it.asJsonObject) },
                schedules = json.get("schedules").asJsonArray.map { jsonToTaskScheduleEntity(it.asJsonObject) },
                notifications = json.get("notifications").asJsonArray.map { jsonToNotificationEntity(it.asJsonObject) }
            )
        }

        private fun taskScheduleEntityToJson(schedule: TaskScheduleEntity): JsonObject {
            val json = JsonObject()
            json.addProperty("task", schedule.task)
            json.add("instances", instancesToJson(schedule.instances))
            json.add("dismissed", dismissedToJson(schedule.dismissed))

            return json
        }

        private fun jsonToTaskScheduleEntity(schedule: JsonObject): TaskScheduleEntity =
            TaskScheduleEntity(
                task = schedule.get("task").asInt,
                instances = jsonToInstances(schedule.get("instances").asJsonObject),
                dismissed = jsonToDismissed(schedule.get("dismissed").asJsonArray)
            )

        private fun notificationEntityToJson(notification: NotificationEntity): JsonObject {
            val json = JsonObject()
            json.addProperty("id", notification.id)
            json.addProperty("task", notification.task)
            json.addProperty("instance", notification.instance.toString())
            json.addProperty("type", notification.type)
            json.addProperty("hash", notification.hash)

            return json
        }

        private fun jsonToNotificationEntity(notification: JsonObject): NotificationEntity =
            NotificationEntity(
                id = notification.get("id").asInt,
                task = notification.get("task").asInt,
                instance = UUID.fromString(notification.get("instance").asString),
                type = notification.get("type").asString,
                hash = notification.get("hash").asInt,
            )

        private fun taskToJson(task: Task): JsonObject {
            val json = JsonObject()
            json.addProperty("id", task.id)
            json.addProperty("name", task.name)
            json.addProperty("description", task.description)
            json.addProperty("goal", task.goal)
            json.add("schedule", scheduleToJson(task.schedule))
            json.addProperty("context_switch", task.contextSwitch.seconds)
            json.addProperty("is_active", task.isActive)

            return json
        }

        private fun jsonToTask(task: JsonObject): Task {
            return Task(
                id = task.get("id").asInt,
                name = task.get("name").asString,
                description = task.get("description").asString,
                goal = task.get("goal").asString,
                schedule = jsonToSchedule(task.get("schedule").asJsonObject),
                contextSwitch = Duration.ofSeconds(task.get("context_switch").asLong),
                isActive = task.get("is_active").asBoolean
            )
        }

        private fun intervalToJson(interval: Task.Schedule.Repeating.Interval): JsonObject {
            val json = JsonObject()

            when (interval) {
                is Task.Schedule.Repeating.Interval.DurationInterval -> {
                    json.addProperty("unit", "seconds")
                    json.addProperty("amount", interval.value.seconds)
                }

                is Task.Schedule.Repeating.Interval.PeriodInterval -> {
                    val (amount, unit) = when {
                        interval.value.days > 0 -> interval.value.days to "days"
                        interval.value.months > 0 -> interval.value.months to "months"
                        else -> interval.value.years to "years"
                    }

                    json.addProperty("unit", unit)
                    json.addProperty("amount", amount)
                }
            }

            return json
        }

        private fun jsonToInterval(interval: JsonObject): Task.Schedule.Repeating.Interval {
            val amount = interval.get("amount")?.asLong
            require(amount != null) { "Expected 'amount' field but none was found" }

            return when (val unit = interval.get("unit")?.asString) {
                "seconds" -> {
                    Duration.ofSeconds(amount).toInterval()
                }

                is String -> {
                    when (unit) {
                        "days" -> Period.ofDays(amount.toInt())
                        "months" -> Period.ofMonths(amount.toInt())
                        "years" -> Period.ofYears(amount.toInt())
                        else -> throw IllegalArgumentException("Unexpected period unit provided: [$unit]")
                    }.toInterval()
                }

                else -> throw IllegalArgumentException("Expected 'unit' field but none was found")
            }
        }

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
                json.add("every", intervalToJson(schedule.every))
                json.add("days", JsonArray().apply { schedule.days.forEach { day -> add(day.value) } })
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

                    val every = schedule.get("every")?.asJsonObject
                    require(every != null) { "Expected 'every' field but none was found" }

                    val days = schedule.get("days")?.asJsonArray
                    require(days != null) { "Expected 'days' field but none was found" }

                    Task.Schedule.Repeating(
                        start = Instant.ofEpochSecond(start),
                        every = jsonToInterval(every),
                        days = days.map { day -> DayOfWeek.of(day.asInt) }.toSet()
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
