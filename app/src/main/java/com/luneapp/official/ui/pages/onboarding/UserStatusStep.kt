package com.luneapp.official.ui.pages.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luneapp.official.R
import com.luneapp.official.domain.settings.UserStatus
import com.luneapp.official.ui.components.PrimaryCta
import com.luneapp.official.ui.components.SmallSpacer

/**
 * Onboarding step that asks the user to select their current health status.
 * The choice is persisted and used to adjust prediction reliability and
 * the clinical symptom fields shown in the daily log.
 *
 * Multiple selections are not supported; users may change their status at
 * any time from Settings.
 */
@Composable
internal fun UserStatusStep(
    selected: UserStatus,
    onSelect: (UserStatus) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.onboarding_status_health_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(8)
        Text(
            stringResource(R.string.onboarding_status_health_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        SmallSpacer(20)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusOption(
                label = stringResource(R.string.user_status_regular),
                description = stringResource(R.string.user_status_regular_desc),
                selected = selected == UserStatus.REGULAR,
                onClick = { onSelect(UserStatus.REGULAR) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_postpartum),
                description = stringResource(R.string.user_status_postpartum_desc),
                selected = selected == UserStatus.POSTPARTUM,
                onClick = { onSelect(UserStatus.POSTPARTUM) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_oncology_tamoxifen),
                description = stringResource(R.string.user_status_oncology_tamoxifen_desc),
                selected = selected == UserStatus.ONCOLOGY_TAMOXIFEN,
                onClick = { onSelect(UserStatus.ONCOLOGY_TAMOXIFEN) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_oncology_other),
                description = stringResource(R.string.user_status_oncology_other_desc),
                selected = selected == UserStatus.ONCOLOGY_OTHER,
                onClick = { onSelect(UserStatus.ONCOLOGY_OTHER) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_pcos),
                description = stringResource(R.string.user_status_pcos_desc),
                selected = selected == UserStatus.PCOS,
                onClick = { onSelect(UserStatus.PCOS) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_endometriosis),
                description = stringResource(R.string.user_status_endometriosis_desc),
                selected = selected == UserStatus.ENDOMETRIOSIS,
                onClick = { onSelect(UserStatus.ENDOMETRIOSIS) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_hormonal_contraception),
                description = stringResource(R.string.user_status_hormonal_contraception_desc),
                selected = selected == UserStatus.HORMONAL_CONTRACEPTION,
                onClick = { onSelect(UserStatus.HORMONAL_CONTRACEPTION) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_thyroid),
                description = stringResource(R.string.user_status_thyroid_desc),
                selected = selected == UserStatus.THYROID_DISORDER,
                onClick = { onSelect(UserStatus.THYROID_DISORDER) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_perimenopause),
                description = stringResource(R.string.user_status_perimenopause_desc),
                selected = selected == UserStatus.PERIMENOPAUSE,
                onClick = { onSelect(UserStatus.PERIMENOPAUSE) },
            )
            StatusOption(
                label = stringResource(R.string.user_status_other),
                description = stringResource(R.string.user_status_other_desc),
                selected = selected == UserStatus.OTHER_IRREGULAR,
                onClick = { onSelect(UserStatus.OTHER_IRREGULAR) },
            )
        }

        SmallSpacer(16)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(stringResource(R.string.onboarding_back)) }
            PrimaryCta(
                text = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatusOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description.isNotBlank()) {
                    SmallSpacer(2)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                RadioButton(selected = true, onClick = null)
            }
        }
    }
}
