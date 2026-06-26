package com.lune.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.lune.app.domain.export.ReportExportService
import com.lune.app.domain.health.HealthSyncManager
import com.lune.app.domain.menstrual.DailyNoteRepository
import com.lune.app.domain.menstrual.MenstrualService
import com.lune.app.domain.menstrual.RecordsRepository
import com.lune.app.domain.notifications.NotificationScheduler
import com.lune.app.domain.notifications.NotificationService
import com.lune.app.domain.settings.SettingsRepository
import com.lune.app.infrastructure.persistence.DataStoreDailyNoteRepository
import com.lune.app.infrastructure.persistence.DataStoreRecordsRepository
import com.viktormykhailiv.kmp.health.HealthManager
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Component
abstract class AppComponent(
    @get:Provides protected val dataStore: DataStore<Preferences>,
    @get:Provides protected val notificationScheduler: NotificationScheduler,
    @get:Provides val reportExportService: ReportExportService,
    @get:Provides protected val healthManager: HealthManager,
) {
    abstract val menstrualService: MenstrualService
    abstract val settingsRepository: SettingsRepository
    abstract val recordsRepository: RecordsRepository
    abstract val dailyNoteRepository: DailyNoteRepository
    abstract val healthSyncManager: HealthSyncManager

    val notificationService: NotificationService by lazy {
        NotificationService(menstrualService, notificationScheduler)
    }

    @Provides
    fun DataStoreRecordsRepository.bind(): RecordsRepository = this

    @Provides
    fun DataStoreDailyNoteRepository.bind(): DailyNoteRepository = this
}
