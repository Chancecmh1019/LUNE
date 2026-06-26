package com.luneapp.official.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.luneapp.official.domain.export.ReportExportService
import com.luneapp.official.domain.health.HealthSyncManager
import com.luneapp.official.domain.menstrual.DailyNoteRepository
import com.luneapp.official.domain.menstrual.MenstrualService
import com.luneapp.official.domain.menstrual.RecordsRepository
import com.luneapp.official.domain.notifications.NotificationScheduler
import com.luneapp.official.domain.notifications.NotificationService
import com.luneapp.official.domain.settings.SettingsRepository
import com.luneapp.official.infrastructure.persistence.DataStoreDailyNoteRepository
import com.luneapp.official.infrastructure.persistence.DataStoreRecordsRepository
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
