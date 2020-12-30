package eventually.test.client.activities

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eventually.client.persistence.notifications.NotificationEntityDatabase
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.client.persistence.schedules.TaskScheduleEntityDatabase
import eventually.client.persistence.schedules.TaskScheduleViewModel
import eventually.client.persistence.tasks.TaskEntityDatabase
import eventually.client.persistence.tasks.TaskViewModel

object ActivityModels {
    fun withTaskViewModel(f: (tasks: TaskViewModel) -> Unit) =
        withModels { tasks, _, _ -> f(tasks) }

    fun withTaskScheduleViewModel(f: (tasks: TaskScheduleViewModel) -> Unit) =
        withModels { _, schedules, _ -> f(schedules) }

    fun withNotificationViewModel(f: (tasks: NotificationViewModel) -> Unit) =
        withModels { _, _, notifications -> f(notifications) }

    fun withModels(f: (tasks: TaskViewModel, schedules: TaskScheduleViewModel, notifications: NotificationViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()

        val tasks = Room.inMemoryDatabaseBuilder(application, TaskEntityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .apply(TaskEntityDatabase::setInstance)

        val schedules = Room.inMemoryDatabaseBuilder(application, TaskScheduleEntityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .apply(TaskScheduleEntityDatabase::setInstance)

        val notifications = Room.inMemoryDatabaseBuilder(application, NotificationEntityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .apply(NotificationEntityDatabase::setInstance)

        try {
            tasks.clearAllTables()
            schedules.clearAllTables()
            notifications.clearAllTables()
            f(TaskViewModel(application), TaskScheduleViewModel(application), NotificationViewModel(application))
        } finally {
            tasks.clearAllTables()
            schedules.clearAllTables()
            notifications.clearAllTables()
        }
    }
}
