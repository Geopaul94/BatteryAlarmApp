package com.example.batteryalarm.di

import android.content.Context
import com.example.batteryalarm.data.datasource.BatteryDataSource
import com.example.batteryalarm.data.repository.BatteryRepositoryImpl
import com.example.batteryalarm.domain.repository.BatteryRepository
import com.example.batteryalarm.notifications.AlarmNotificationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module
 * Responsibility: Provide singleton instances of repositories and services
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideBatteryDataSource(
        @ApplicationContext context: Context
    ): BatteryDataSource {
        return BatteryDataSource(context)
    }

    @Singleton
    @Provides
    fun provideBatteryRepository(
        @ApplicationContext context: Context,
        batteryDataSource: BatteryDataSource
    ): BatteryRepository {
        return BatteryRepositoryImpl(context, batteryDataSource)
    }

    @Singleton
    @Provides
    fun provideAlarmNotificationManager(
        @ApplicationContext context: Context
    ): AlarmNotificationManager {
        return AlarmNotificationManager(context)
    }
}
