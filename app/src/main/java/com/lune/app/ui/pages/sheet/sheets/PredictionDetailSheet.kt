package com.lune.app.ui.pages.sheet.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lune.app.domain.menstrual.PredictedCycle
import com.lune.app.ui.components.DecorShape
import com.lune.app.ui.components.SmallSpacer
import com.lune.app.ui.theme.expressiveShapes
import kotlinx.datetime.number
import androidx.compose.ui.res.stringResource
import com.lune.app.R

/**
 * Read-only detail sheet for a predicted period.
 * Shows predicted dates, estimated duration, and decorative prediction badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionDetailSheet(
    prediction: PredictedCycle,
    avgPeriodLength: Int,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Prediction badge
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DecorShape(
                        size = 12,
                        shape = MaterialTheme.expressiveShapes.sunny,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    SmallSpacer(6)
                    Text(
                        stringResource(R.string.home_next_period_starts),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            SmallSpacer(24)

            // Predicted duration
            Text(
                "$avgPeriodLength",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.unit_days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SmallSpacer(24)

            // Date range
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.record_start_date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SmallSpacer(4)
                        Text(
                            "${prediction.predictedStart.month.number}/${prediction.predictedStart.day}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (prediction.predictedEnd != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.record_end_date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SmallSpacer(4)
                            Text(
                                "${prediction.predictedEnd.month.number}/${prediction.predictedEnd.day}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            SmallSpacer(16)

            Text(
                stringResource(R.string.home_backfill_to_predict),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
