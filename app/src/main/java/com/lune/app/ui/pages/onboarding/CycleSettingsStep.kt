package com.lune.app.ui.pages.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lune.app.ui.components.PrimaryCta
import com.lune.app.ui.components.SmallSpacer
import androidx.compose.ui.res.stringResource
import com.lune.app.R

@Composable
internal fun CycleSettingsStep(
    cycleLength: Int,
    periodDuration: Int,
    onCycleLengthChange: (Int) -> Unit,
    onPeriodDurationChange: (Int) -> Unit,
    showSyntheticHint: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_cycle_settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(8)
        Text(
            stringResource(R.string.onboarding_cycle_settings_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(0.3f))

        ValueSlider(
            label = stringResource(R.string.onboarding_period_duration_label),
            value = periodDuration,
            valueRange = 2f..10f,
            unit = stringResource(R.string.unit_days),
            onValueChange = onPeriodDurationChange,
            minLabel = "2",
            maxLabel = "10",
        )
        SmallSpacer(32)
        ValueSlider(
            label = stringResource(R.string.onboarding_cycle_length_label),
            value = cycleLength,
            valueRange = 20f..45f,
            unit = stringResource(R.string.unit_days),
            onValueChange = onCycleLengthChange,
            minLabel = "20",
            maxLabel = "45",
        )

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(visible = showSyntheticHint) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.onboarding_synthetic_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallSpacer(16)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(stringResource(R.string.onboarding_back)) }
            PrimaryCta(
                text = stringResource(R.string.onboarding_continue),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
