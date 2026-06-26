package com.luneapp.official

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.luneapp.official.di.AppComponent
import com.luneapp.official.di.create
import com.luneapp.official.domain.export.AndroidReportExportService
import com.luneapp.official.notifications.AndroidNotificationScheduler
import com.viktormykhailiv.kmp.health.HealthManagerFactory
import com.viktormykhailiv.kmp.health.HealthManagerFactoryOptions
import okio.Path.Companion.toPath

private const val DATA_STORE_FILE_NAME = "lune.preferences_pb"

// DataStore must be a process-wide singleton — creating two against the same
// file (which happens on every Activity re-creation) throws IllegalStateException.
@Volatile private var sharedDataStore: DataStore<Preferences>? = null
private val dataStoreLock = Any()

private fun appDataStore(context: Context): DataStore<Preferences> {
    sharedDataStore?.let { return it }
    return synchronized(dataStoreLock) {
        sharedDataStore ?: PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.applicationContext.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath.toPath()
            },
        ).also { sharedDataStore = it }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidNotificationScheduler.currentActivityHolder.set(this)

        val dataStore = appDataStore(this)
        val scheduler = AndroidNotificationScheduler(applicationContext)
        val reportExportService = AndroidReportExportService(applicationContext)
        val healthManager = HealthManagerFactory()
            .createManager(options = HealthManagerFactoryOptions.default())
        val component = AppComponent::class.create(
            dataStore = dataStore,
            notificationScheduler = scheduler,
            reportExportService = reportExportService,
            healthManager = healthManager
        )

        setContent {
            App(component, applicationContext)
        }
    }

    override fun onDestroy() {
        AndroidNotificationScheduler.currentActivityHolder.compareAndSet(this, null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Using legacy permission API bridged to NotificationScheduler")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AndroidNotificationScheduler.onPermissionResult(requestCode, grantResults)
    }
}
