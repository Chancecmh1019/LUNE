package com.luneapp.official.ui.pages.sheet.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luneapp.official.domain.menstrual.CyclePhase
import com.luneapp.official.domain.menstrual.CyclePhaseInfo
import com.luneapp.official.ui.components.SmallSpacer
import com.luneapp.official.ui.pages.home.color
import com.luneapp.official.ui.pages.home.description
import com.luneapp.official.ui.pages.home.displayName
import com.luneapp.official.ui.pages.home.shape
import androidx.compose.ui.res.stringResource
import com.luneapp.official.R

/**
 * Bottom sheet showing all cycle phases with timing info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhaseExplanationSheet(
    phaseInfo: CyclePhaseInfo,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.home_current_phase),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.home_day_in_cycle, phaseInfo.dayInCycle, phaseInfo.cycleLength),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SmallSpacer(8)

            CyclePhase.entries.forEach { phase ->
                val isCurrent = phase == phaseInfo.phase
                val daysUntil = phaseInfo.daysUntilPhase(phase)
                val timingLabel = if (isCurrent) stringResource(R.string.phase_now)
                                  else stringResource(R.string.phase_in_days, daysUntil)

                Surface(
                    tonalElevation = if (isCurrent) 3.dp else 0.dp,
                    shape = MaterialTheme.shapes.large,
                    border = if (isCurrent) BorderStroke(2.dp, phase.color()) else null,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            color = phase.color(),
                            shape = phase.shape(),
                        ) { }
                        SmallSpacer(12)
                        Column(Modifier.weight(1f)) {
                            Text(
                                phase.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            )
                            SmallSpacer(2)
                            Text(
                                phase.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SmallSpacer(8)
                        Text(
                            timingLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) phase.color()
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
