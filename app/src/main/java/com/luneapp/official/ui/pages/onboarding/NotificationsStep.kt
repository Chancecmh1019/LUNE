package com.luneapp.official.ui.pages.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luneapp.official.domain.notifications.NotificationPrefs
import com.luneapp.official.ui.components.PrimaryCta
import com.luneapp.official.ui.components.SmallSpacer
import androidx.compose.ui.res.stringResource
import com.luneapp.official.R

@Composable
internal fun NotificationsStep(
    prefs: NotificationPrefs,
    hasPermission: Boolean,
    onPrefsChange: (NotificationPrefs) -> Unit,
    onRequestPermission: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val anyEnabled = prefs.periodReminderEnabled ||
        prefs.ovulationReminderEnabled ||
        prefs.dailyReportEnabled

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        FeatureIcon(icon = Icons.Outlined.Notifications, tint = MaterialTheme.colorScheme.primary)
        SmallSpacer(20)
        Text(
            stringResource(R.string.onboarding_notifications_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        SmallSpacer(12)
        Text(
            stringResource(R.string.onboarding_notifications_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        SmallSpacer(28)

        ReminderToggle(
            icon = Icons.Outlined.WaterDrop,
            accent = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.notifications_period_title),
            valueLabel = if (prefs.periodReminderEnabled)
                stringResource(R.string.notifications_days_before, prefs.periodReminderDaysBefore)
            else stringResource(R.string.notifications_off),
            checked = prefs.periodReminderEnabled,
            onCheckedChange = { onPrefsChange(prefs.copy(periodReminderEnabled = it)) },
        )
        SmallSpacer(10)
        ReminderToggle(
            icon = Icons.Outlined.AutoAwesome,
            accent = MaterialTheme.colorScheme.tertiary,
            title = stringResource(R.string.notifications_ovulation_title),
            valueLabel = if (prefs.ovulationReminderEnabled)
                stringResource(R.string.notifications_days_before, prefs.ovulationReminderDaysBefore)
            else stringResource(R.string.notifications_off),
            checked = prefs.ovulationReminderEnabled,
            onCheckedChange = { onPrefsChange(prefs.copy(ovulationReminderEnabled = it)) },
        )
        SmallSpacer(10)
        ReminderToggle(
            icon = Icons.Outlined.WbSunny,
            accent = MaterialTheme.colorScheme.secondary,
            title = stringResource(R.string.notifications_daily_title),
            valueLabel = if (prefs.dailyReportEnabled)
                formatHourMinute(prefs.dailyReportHour, prefs.dailyReportMinute)
            else stringResource(R.string.notifications_off),
            checked = prefs.dailyReportEnabled,
            onCheckedChange = { onPrefsChange(prefs.copy(dailyReportEnabled = it)) },
        )

        AnimatedVisibility(visible = anyEnabled && !hasPermission) {
            Column {
                SmallSpacer(16)
                Surface(
                    onClick = onRequestPermission,
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.notifications_permission_required),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        SmallSpacer(2)
                        Text(
                            stringResource(R.string.notifications_permission_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryCta(
            text = stringResource(
                if (anyEnabled) R.string.onboarding_continue else R.string.onboarding_skip
            ),
            onClick = if (anyEnabled) onContinue else onSkip,
        )
        SmallSpacer(8)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
        }
    }
}

private fun formatHourMinute(hour: Int, minute: Int): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m"
}
