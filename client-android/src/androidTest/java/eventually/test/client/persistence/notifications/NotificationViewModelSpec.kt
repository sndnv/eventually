package eventually.test.client.persistence.notifications

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eventually.client.persistence.notifications.NotificationEntity
import eventually.client.persistence.notifications.NotificationEntityDatabase
import eventually.client.persistence.notifications.NotificationViewModel
import eventually.core.model.Task
import eventually.core.model.Task.Schedule.Repeating.Interval.Companion.toInterval
import eventually.core.model.TaskInstance
import eventually.test.client.await
import eventually.test.client.eventually
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class NotificationViewModelSpec {
    @Test
    fun createNotifications() {
        withModel { model ->
            assertThat(model.notifications.await(), equalTo(emptyList()))

            runBlocking { model.put(task, instance, entity.type).await() }

            eventually {
                assertThat(
                    model.notifications.await().map { it.copy(id = entity.id) },
                    equalTo(listOf(entity))
                )
            }
        }
    }


    @Test
    fun retrieveNotificationEntitiesBasedOnQuery() {
        withModel { model ->
            assertThat(model.notifications.await(), equalTo(emptyList()))

            runBlocking {
                model.put(task, instance, entity.type).await()

                assertThat(
                    model.get(entity.task, entity.instance, entity.type).await()?.copy(id = entity.id),
                    equalTo(entity)
                )

                assertThat(
                    model.get(entity.task, entity.instance, "execution").await(),
                    equalTo(null)
                )
            }
        }
    }

    @Test
    fun deleteNotifications() {
        withModel { model ->
            assertThat(model.notifications.await(), equalTo(emptyList()))

            val notificationId = runBlocking { model.put(task, instance, entity.type).await() }

            eventually {
                assertThat(
                    model.notifications.await(),
                    equalTo(listOf(entity.copy(id = notificationId.toInt())))
                )
            }

            runBlocking { model.delete(notificationId.toInt()) }

            eventually {
                assertThat(model.notifications.await(), equalTo(emptyList()))
            }
        }
    }

    @Test
    fun notDeleteMissingNotifications() {
        withModel { model ->
            assertThat(model.notifications.await(), equalTo(emptyList()))

            runBlocking { model.delete(42) }

            eventually {
                assertThat(model.notifications.await(), equalTo(emptyList()))
            }
        }
    }

    @Test
    fun deleteNotificationEntitiesBasedOnQuery() {
        withModel { model ->
            assertThat(model.notifications.await(), equalTo(emptyList()))

            val contextNotificationId = runBlocking { model.put(task, instance, "context").await() }
            val executionNotificationId = runBlocking { model.put(task, instance, "execution").await() }

            eventually {
                assertThat(
                    model.notifications.await(),
                    equalTo(
                        listOf(
                            entity.copy(id = contextNotificationId.toInt(), type = "context"),
                            entity.copy(id = executionNotificationId.toInt(), type = "execution")
                        )
                    )
                )
            }

            runBlocking { model.delete(entity.task, entity.instance) }

            eventually {
                assertThat(model.notifications.await(), equalTo(emptyList()))
            }
        }
    }

    @Test
    fun notDeleteMissingNotificationsBasedOnQuery() {
        withModel { model ->
            assertThat(model.notifications.await(), equalTo(emptyList()))

            runBlocking { model.delete(entity.task, entity.instance) }

            eventually {
                assertThat(model.notifications.await(), equalTo(emptyList()))
            }
        }
    }

    private val task = Task(
        id = 42,
        name = "test-task",
        description = "test-description",
        goal = "test-goal",
        schedule = Task.Schedule.Repeating(
            start = LocalTime.of(0, 15).atDate(LocalDate.now()).toInstant(ZoneOffset.UTC),
            every = Duration.ofMinutes(20).toInterval()
        ),
        contextSwitch = Duration.ofMinutes(5),
        isActive = true
    )

    private val instance = TaskInstance(instant = Instant.now())

    private val entity = NotificationEntity(
        id = 1,
        task = task.id,
        instance = instance.id,
        type = "context",
        hash = instance.hashCode()
    )

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun withModel(f: (model: NotificationViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = NotificationViewModel(application)
        val db = NotificationEntityDatabase.getInstance(application)

        try {
            db.clearAllTables()
            f(model)
        } finally {
            db.clearAllTables()
        }
    }
}
