package com.luneapp.official

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luneapp.official.di.AppComponent
import com.luneapp.official.domain.export.JsonBackupService
import com.luneapp.official.domain.health.HealthAuthStatus
import com.luneapp.official.ui.locale.LocalAppLocale
import com.luneapp.official.ui.navigation.DisclaimerRoute
import com.luneapp.official.ui.navigation.HomeRoute
import com.luneapp.official.ui.navigation.NotificationSettingsRoute
import com.luneapp.official.ui.navigation.OnboardingRoute
import com.luneapp.official.ui.navigation.SettingsRoute
import com.luneapp.official.ui.pages.disclaimer.DisclaimerScreen
import com.luneapp.official.ui.pages.home.HomeScreen
import com.luneapp.official.ui.pages.onboarding.OnboardingScreen
import com.luneapp.official.ui.pages.settings.NotificationSettingsScreen
import com.luneapp.official.ui.pages.settings.SettingsScreen
import com.luneapp.official.ui.pages.sheet.LocalSheetViewModel
import com.luneapp.official.ui.pages.sheet.SheetHost
import com.luneapp.official.ui.pages.sheet.SheetViewModel
import com.luneapp.official.ui.pages.splash.SplashScreen
import com.luneapp.official.ui.theme.AppTheme

@Composable
fun App(component: AppComponent, context: Context) {
    val service = component.menstrualService
    val settings = component.settingsRepository
    val notificationService = component.notificationService
    val reportExportService = component.reportExportService
    val healthSyncManager = component.healthSyncManager
    val jsonBackupService = remember { JsonBackupService(context, component.recordsRepository) }

    val viewModel = remember {
        AppViewModel(context, service, settings, notificationService, reportExportService, jsonBackupService, healthSyncManager)
    }
    val sheetViewModel = remember { SheetViewModel(service) }

    val darkMode = viewModel.darkMode
    val language = viewModel.language
    val cycleLength = viewModel.cycleLength
    val periodDuration = viewModel.periodDuration
    val startRoute = viewModel.startRoute

    var splashDone by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalAppLocale provides language,
        LocalSheetViewModel provides sheetViewModel,
    ) {
        AppTheme(darkMode = darkMode) {
            Box(Modifier.fillMaxSize()) {
                if (startRoute != null) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = startRoute) {
                        composable<DisclaimerRoute> {
                            DisclaimerScreen(onAccept = {
                                viewModel.setDisclaimerAccepted(true)
                                navController.navigate(OnboardingRoute) {
                                    popUpTo(DisclaimerRoute) { inclusive = true }
                                }
                            })
                        }

                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                service = service,
                                settings = settings,
                                healthSyncManager = healthSyncManager
                                    .takeIf { viewModel.healthAuthStatus != HealthAuthStatus.NOT_AVAILABLE },
                                notificationPrefs = viewModel.notificationPrefs,
                                notificationPermissionGranted = viewModel.notificationPermissionGranted,
                                onUpdateNotificationPrefs = { viewModel.updateNotificationPrefs(it) },
                                onRequestNotificationPermission = { viewModel.requestNotificationPermission() },
                                onComplete = {
                                    viewModel.rescheduleNotifications()
                                    navController.navigate(HomeRoute) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                    }
                                },
                                onRestoreFromJson = { uri ->
                                    viewModel.importJsonBackup(uri)
                                    // Normally you might want to show a toast or wait for it to finish,
                                    // but for brevity we let the ViewModel update state silently.
                                },
                            )
                        }

                        composable<HomeRoute> {
                            HomeScreen(
                                service = service,
                                sheetViewModel = sheetViewModel,
                                settings = settings,
                                onNavigateSettings = { navController.navigate(SettingsRoute) },
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                currentDarkMode = darkMode,
                                currentLanguage = language,
                                currentCycleLength = cycleLength,
                                currentPeriodDuration = periodDuration,
                                userStatus = viewModel.userStatus,
                                onDarkModeChange = { newMode -> viewModel.updateDarkMode(newMode) },
                                onLanguageChange = { newLang -> viewModel.updateLanguage(newLang) },
                                onCycleLengthChange = { newLen -> viewModel.updateCycleLength(newLen) },
                                onPeriodDurationChange = { newDur -> viewModel.updatePeriodDuration(newDur) },
                                onBack = { navController.popBackStack() },
                                onClearData = {
                                    viewModel.clearAllData()
                                    navController.navigate(DisclaimerRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onNavigateNotifications = { navController.navigate(NotificationSettingsRoute) },
                                exportStatus = viewModel.exportStatus,
                                onExport = { lang, from, to -> viewModel.exportReport(lang, from, to) },
                                onResetExportStatus = { viewModel.resetExportStatus() },
                                jsonBackupStatus = viewModel.jsonBackupStatus,
                                onResetJsonBackupStatus = { viewModel.resetJsonBackupStatus() },
                                healthSyncEnabled = viewModel.healthSyncEnabled,
                                healthAuthStatus = viewModel.healthAuthStatus,
                                healthLastSync = viewModel.healthLastSync,
                                healthSyncInProgress = viewModel.healthSyncInProgress,
                                onToggleHealthSync = { viewModel.toggleHealthSync(it) },
                                onSyncHealthNow = { viewModel.syncHealth() },
                                onExportJson = { viewModel.exportJsonBackup() },
                                onImportJson = { uri -> viewModel.importJsonBackup(uri) },
                            )
                        }

                        composable<NotificationSettingsRoute> {
                            NotificationSettingsScreen(
                                prefs = viewModel.notificationPrefs,
                                hasPermission = viewModel.notificationPermissionGranted,
                                onRequestPermission = { viewModel.requestNotificationPermission() },
                                onPrefsChange = { viewModel.updateNotificationPrefs(it) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }

                    // Global sheet host ??renders active sheet from SheetViewModel
                    SheetHost(sheetViewModel)
                }

                // Brand splash overlays everything until upstream state is
                // loaded and the entrance animation has settled. Fades out
                // smoothly so the underlying NavHost is already mounted.
                AnimatedVisibility(
                    visible = !splashDone,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(450)),
                ) {
                    SplashScreen(
                        ready = startRoute != null,
                        onFinish = { splashDone = true },
                    )
                }
            }
        }
    }
}
