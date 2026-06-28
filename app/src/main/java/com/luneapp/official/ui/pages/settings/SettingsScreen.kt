package com.luneapp.official.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luneapp.official.BuildConfig
import com.luneapp.official.ExportStatus
import com.luneapp.official.domain.health.HealthAuthStatus
import com.luneapp.official.domain.settings.AppDarkMode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import androidx.compose.ui.res.stringResource
import com.luneapp.official.R
import com.luneapp.official.domain.menstrual.toConditionProfile

@Composable
fun SettingsScreen(
    currentDarkMode: AppDarkMode,
    currentLanguage: String?,
    currentCycleLength: Int,
    currentPeriodDuration: Int,
    userStatus: com.luneapp.official.domain.settings.UserStatus = com.luneapp.official.domain.settings.UserStatus.REGULAR,
    onDarkModeChange: (AppDarkMode) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onCycleLengthChange: (Int) -> Unit,
    onPeriodDurationChange: (Int) -> Unit,
    onBack: () -> Unit,
    onClearData: () -> Unit,
    onNavigateNotifications: () -> Unit = {},
    exportStatus: ExportStatus = ExportStatus.Idle,
    onExport: (language: String, fromDate: kotlinx.datetime.LocalDate?, toDate: kotlinx.datetime.LocalDate?) -> Unit = { _, _, _ -> },
    onResetExportStatus: () -> Unit = {},
    jsonBackupStatus: ExportStatus = ExportStatus.Idle,
    onResetJsonBackupStatus: () -> Unit = {},
    healthSyncEnabled: Boolean = false,
    healthAuthStatus: HealthAuthStatus = HealthAuthStatus.NOT_AVAILABLE,
    healthLastSync: Long = 0L,
    healthSyncInProgress: Boolean = false,
    onToggleHealthSync: (Boolean) -> Unit = {},
    onSyncHealthNow: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onImportJson: (android.net.Uri) -> Unit = {},
) {
    val jsonPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { onImportJson(it) } }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showPeriodDurationDialog by remember { mutableStateOf(false) }
    var showCycleLengthDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val exportSuccessMsg = stringResource(R.string.settings_backup_export_success)
    val importSuccessMsg = stringResource(R.string.settings_backup_import_success)
    
    // Handle JSON backup status (both export and import)
    LaunchedEffect(jsonBackupStatus) {
        when (jsonBackupStatus) {
            is ExportStatus.Success -> {
                // Check if it's export or import based on message content
                val msg = if (jsonBackupStatus.location.contains("Imported")) {
                    importSuccessMsg
                } else {
                    exportSuccessMsg
                }
                snackbarHostState.showSnackbar(msg)
                onResetJsonBackupStatus()
            }
            is ExportStatus.Failure -> {
                snackbarHostState.showSnackbar(jsonBackupStatus.message)
                onResetJsonBackupStatus()
            }
            else -> {}
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 24.dp),
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(32.dp))

        // Display Mode ??icon toggle
        SectionLabel(stringResource(R.string.settings_display_mode))
        Spacer(Modifier.height(12.dp))
        InlineIconToggle(
            items = listOf(
                ToggleItem(AppDarkMode.SYSTEM, stringResource(R.string.settings_mode_system), Icons.Outlined.Smartphone),
                ToggleItem(AppDarkMode.LIGHT, stringResource(R.string.settings_mode_light), Icons.Outlined.LightMode),
                ToggleItem(AppDarkMode.DARK, stringResource(R.string.settings_mode_dark), Icons.Outlined.DarkMode),
            ),
            selected = currentDarkMode,
            onSelect = onDarkModeChange,
        )

        Spacer(Modifier.height(24.dp))

        // Language ??text toggle
        SectionLabel(stringResource(R.string.settings_language))
        Spacer(Modifier.height(12.dp))
        InlineTextToggle(
            items = listOf(
                null to "Auto",
                "en" to "EN",
                "zh-TW" to "繁體中文",
            ),
            selected = currentLanguage,
            onSelect = onLanguageChange,
        )

        Spacer(Modifier.height(24.dp))

        // Only show cycle/period settings if predictions are enabled
        // Users with predictionsEnabled=false (postpartum, oncology) don't have predictable cycles
        val profile = userStatus.toConditionProfile()
        if (profile.predictionsEnabled) {
            // ?? Period Duration ??display only, tap to edit ??
            SettingsItem(
                label = stringResource(R.string.settings_period_duration),
                value = "${currentPeriodDuration} ${stringResource(R.string.unit_days)}",
                onClick = { showPeriodDurationDialog = true },
            )

            Spacer(Modifier.height(12.dp))

            // ?? Cycle Length ??display only, tap to edit ??
            SettingsItem(
                label = stringResource(R.string.settings_cycle_length),
                value = "${currentCycleLength} ${stringResource(R.string.unit_days)}",
                onClick = { showCycleLengthDialog = true },
            )

            Spacer(Modifier.height(12.dp))
        }

        // ?? Notifications ??
        SettingsItem(
            label = stringResource(R.string.settings_notifications),
            value = stringResource(R.string.settings_notifications_value),
            onClick = onNavigateNotifications,
        )

        Spacer(Modifier.height(12.dp))

        // ?? Export report ??
        SettingsItem(
            label = stringResource(R.string.settings_export),
            value = stringResource(R.string.settings_export_value),
            onClick = {
                onResetExportStatus()
                showExportDialog = true
            },
        )

        Spacer(Modifier.height(24.dp))

        // Backup Data
        SectionLabel(stringResource(R.string.settings_backup_title))
        Spacer(Modifier.height(12.dp))
        
        Surface(
            onClick = { onExportJson() },
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.settings_backup_export),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.settings_backup_export_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Surface(
            onClick = { jsonPicker.launch("application/json") },
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.settings_backup_import),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.settings_backup_import_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ?? Health Data ??
        SectionLabel(stringResource(R.string.settings_health_data))
        Spacer(Modifier.height(12.dp))
        Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_health_sync),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.settings_health_sync_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = healthSyncEnabled,
                        onCheckedChange = onToggleHealthSync,
                        enabled = healthAuthStatus != HealthAuthStatus.NOT_AVAILABLE,
                    )
                }
                if (healthAuthStatus == HealthAuthStatus.NOT_AVAILABLE) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_health_not_available),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (healthSyncEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (healthLastSync > 0L) {
                                stringResource(
                                    R.string.settings_health_last_sync,
                                    formatSyncTime(healthLastSync),
                                )
                            } else {
                                stringResource(R.string.settings_health_never_synced)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = onSyncHealthNow,
                            enabled = !healthSyncInProgress,
                        ) {
                            if (healthSyncInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_health_syncing))
                            } else {
                                Text(stringResource(R.string.settings_health_sync_now))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // About & version
        Surface(
            onClick = { showAboutSheet = true },
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            stringResource(R.string.settings_made_by),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Clear data
        Spacer(Modifier.height(12.dp))
        SettingsItem(
            label = stringResource(R.string.settings_clear_data),
            value = stringResource(R.string.settings_clear_data_value),
            onClick = { showClearConfirm = true },
            destructive = true,
        )

        Spacer(Modifier.height(16.dp))
    }

        // SnackbarHost positioned at the bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp)
        )
    }

    // Dialogs

    if (showClearConfirm) {
        ClearDataDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = onClearData,
        )
    }

    if (showAboutSheet) {
        AboutSheet(onDismiss = { showAboutSheet = false })
    }

    if (showPeriodDurationDialog) {
        SliderDialog(
            title = stringResource(R.string.settings_period_duration),
            currentValue = currentPeriodDuration,
            valueRange = 2f..10f,
            steps = 7,
            minLabel = "2",
            maxLabel = "10",
            onConfirm = { onPeriodDurationChange(it); showPeriodDurationDialog = false },
            onDismiss = { showPeriodDurationDialog = false },
        )
    }

    if (showCycleLengthDialog) {
        SliderDialog(
            title = stringResource(R.string.settings_cycle_length),
            currentValue = currentCycleLength,
            valueRange = 20f..45f,
            steps = 24,
            minLabel = "20",
            maxLabel = "45",
            onConfirm = { onCycleLengthChange(it); showCycleLengthDialog = false },
            onDismiss = { showCycleLengthDialog = false },
        )
    }

    if (showExportDialog) {
        ExportDialog(
            status = exportStatus,
            onExport = { language, fromDate, toDate ->
                onExport(language, fromDate, toDate)
            },
            onDismiss = {
                showExportDialog = false
                onResetExportStatus()
            },
        )
    }
}

private fun formatSyncTime(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val h = dt.hour.toString().padStart(2, '0')
    val m = dt.minute.toString().padStart(2, '0')
    return "${dt.date} $h:$m"
}

// ?????????????????????????????????????????????????
// Slider dialog for editing numeric values
// ?????????????????????????????????????????????????
