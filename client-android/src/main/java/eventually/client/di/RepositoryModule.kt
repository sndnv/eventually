package eventually.client.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eventually.client.persistence.notifications.NotificationRepository
import eventually.client.persistence.schedules.TaskScheduleRepository
import eventually.client.persistence.tasks.TaskRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideTaskRepository(@ApplicationContext context: Context): TaskRepository =
        TaskRepository(context)

    @Provides
    @Singleton
    fun provideTaskScheduleRepository(@ApplicationContext context: Context): TaskScheduleRepository =
        TaskScheduleRepository(context)

    @Provides
    @Singleton
    fun provideNotificationRepository(@ApplicationContext context: Context): NotificationRepository =
        NotificationRepository(context)
}
