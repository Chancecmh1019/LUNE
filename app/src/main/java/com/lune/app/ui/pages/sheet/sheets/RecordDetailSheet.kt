package com.lune.app.ui.pages.sheet.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lune.app.domain.menstrual.MenstrualRecord
import com.lune.app.domain.menstrual.MenstrualService
import com.lune.app.ui.components.SmallSpacer
import com.lune.app.ui.pages.record.PeriodDetailCalendar
import kotlinx.datetime.*
import androidx.compose.ui.res.stringResource
import com.lune.app.R
import kotlin.time.Clock

/**
 * Full-screen record detail ¡X shows record info as cards and a read-only calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailSheet(
    record: MenstrualRecord,
    allRecords: List<MenstrualRecord>,
    defaultCycleLength: Int,
    service: MenstrualService,
    onDismiss: () -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    
    val cycleContext = remember(record, allRecords) {
        service.getMenstrualCycle(record, allRecords, defaultCycleLength = defaultCycleLength)
    }

    val endDate = record.endDate ?: today
    val periodDays = record.startDate.until(endDate, DateTimeUnit.DAY).toInt() + 1
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val cycleDays = cycleContext.cycleLength
    val cycleEndDate = cycleContext.cycleEndDate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ¢w¢w Calendar ¢w¢w
            PeriodDetailCalendar(
                record = record,
                cycleEndDate = cycleEndDate,
                ovulationDates = cycleContext.ovulationDates,
                ovulationPeakDate = cycleContext.ovulationPeakDate,
                modifier = Modifier.padding(top = 16.dp)
            )

            SmallSpacer(16)

            // ¢w¢w Info Cards ¢w¢w
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cycleContext.isAnomaly) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.anomaly_short_cycle_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            SmallSpacer(4)
                            Text(
                                stringResource(R.string.anomaly_short_cycle_body),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Start Date
                DetailListItem(
                    label = stringResource(R.string.record_start_date),
                    value = "${record.startDate.month.number}/${record.startDate.day}",
                    onClick = onEditStart,
                    isEditable = true
                )

                // End Date
                DetailListItem(
                    label = stringResource(R.string.record_end_date),
                    value = if (record.endDate != null) "${record.endDate.month.number}/${record.endDate.day}" else stringResource(R.string.history_in_progress),
                    onClick = onEditEnd,
                    isEditable = true
                )

                // Period Length
                DetailListItem(
                    label = stringResource(R.string.stats_period_days),
                    value = "$periodDays ${stringResource(R.string.unit_days)}",
                )

                // Cycle Length
                DetailListItem(
                    label = stringResource(R.string.stats_cycle_days),
                    value = "$cycleDays ${stringResource(R.string.unit_days)}",
                )
            }

            SmallSpacer(24)

            // ¢w¢w Delete Button ¢w¢w
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.detail_delete))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.detail_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailListItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (isEditable) {
                    SmallSpacer(4)
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    )
}