package com.lune.app.ui.pages.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lune.app.ExportStatus
import androidx.compose.ui.res.stringResource
import com.lune.app.R
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** Preset date range options for the report export. */
internal enum class ExportDateRange { MONTHS_3, MONTHS_6, YEAR_1, ALL, CUSTOM }

/**
 * Dialog that asks the user to pick a language AND a date range for the
 * report export. Also surfaces in-progress / success / failure states.
 */
@Composable
internal fun ExportDialog(
    status: ExportStatus,
    onExport: (language: String, fromDate: LocalDate?, toDate: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var selectedLanguage by remember { mutableStateOf("zh") }
    var selectedRange by remember { mutableStateOf(ExportDateRange.ALL) }

    // Custom range state
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo   by remember { mutableStateOf<LocalDate?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }

    val busy = status is ExportStatus.InProgress

    // Compute effective fromDate / toDate based on selection
    val effectiveFrom: LocalDate? = when (selectedRange) {
        ExportDateRange.MONTHS_3 -> today.minus(3, DateTimeUnit.MONTH)
        ExportDateRange.MONTHS_6 -> today.minus(6, DateTimeUnit.MONTH)
        ExportDateRange.YEAR_1   -> today.minus(1, DateTimeUnit.YEAR)
        ExportDateRange.ALL      -> null
        ExportDateRange.CUSTOM   -> customFrom
    }
    val effectiveTo: LocalDate? = when (selectedRange) {
        ExportDateRange.CUSTOM -> customTo
        else -> null // null = up to today (no upper limit)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            val title = when (status) {
                is ExportStatus.Success  -> stringResource(R.string.export_success_title)
                is ExportStatus.Failure  -> stringResource(R.string.export_failure_title)
                else                     -> stringResource(R.string.export_dialog_title)
            }
            Text(title)
        },
        text = {
            when (status) {
                is ExportStatus.Success -> {
                    Text(stringResource(R.string.export_success_body))
                }
                is ExportStatus.Failure -> {
                    Text(stringResource(R.string.export_failure_body, status.message))
                }
                is ExportStatus.InProgress -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.export_in_progress))
                    }
                }
                ExportStatus.Idle -> {
                    Column {
                        Text(stringResource(R.string.export_dialog_body))
                        Spacer(Modifier.height(16.dp))

                        // ── Language picker ────────────────────────────────────
                        LanguageOption(
                            label = stringResource(R.string.export_lang_english),
                            selected = selectedLanguage == "en",
                            onClick = { selectedLanguage = "en" },
                        )
                        Spacer(Modifier.height(8.dp))
                        LanguageOption(
                            label = stringResource(R.string.export_lang_chinese),
                            selected = selectedLanguage == "zh",
                            onClick = { selectedLanguage = "zh" },
                        )

                        Spacer(Modifier.height(20.dp))

                        // ── Date range picker ──────────────────────────────────
                        Text(
                            stringResource(R.string.export_range_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))

                        val rangeOptions = listOf(
                            ExportDateRange.MONTHS_3 to stringResource(R.string.export_range_3m),
                            ExportDateRange.MONTHS_6 to stringResource(R.string.export_range_6m),
                            ExportDateRange.YEAR_1   to stringResource(R.string.export_range_1y),
                            ExportDateRange.ALL      to stringResource(R.string.export_range_all),
                            ExportDateRange.CUSTOM   to stringResource(R.string.export_range_custom),
                        )

                        rangeOptions.forEach { (range, label) ->
                            RangeOption(
                                label = label,
                                selected = selectedRange == range,
                                onClick = { selectedRange = range },
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        // Custom date pickers (only shown when CUSTOM selected)
                        AnimatedVisibility(visible = selectedRange == ExportDateRange.CUSTOM) {
                            Column(Modifier.padding(top = 4.dp)) {
                                // From date
                                OutlinedButton(
                                    onClick = { showFromPicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        if (customFrom != null)
                                            "${stringResource(R.string.export_range_from)}: $customFrom"
                                        else
                                            stringResource(R.string.export_range_from) + "：" +
                                                stringResource(R.string.export_range_pick),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                // To date
                                OutlinedButton(
                                    onClick = { showToPicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        if (customTo != null)
                                            "${stringResource(R.string.export_range_to)}: $customTo"
                                        else
                                            stringResource(R.string.export_range_to) + "：" +
                                                stringResource(R.string.export_range_pick),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (status) {
                ExportStatus.Idle -> {
                    val canExport = selectedRange != ExportDateRange.CUSTOM ||
                        (customFrom != null && customTo != null)
                    TextButton(
                        onClick = { onExport(selectedLanguage, effectiveFrom, effectiveTo) },
                        enabled = canExport,
                    ) {
                        Text(stringResource(R.string.export_button))
                    }
                }
                is ExportStatus.Success, is ExportStatus.Failure -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_confirm))
                    }
                }
                is ExportStatus.InProgress -> { /* no action while busy */ }
            }
        },
        dismissButton = if (status is ExportStatus.Idle) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        } else null,
    )

    // Date picker dialogs
    if (showFromPicker) {
        DatePickerModalDialog(
            initialDate = customFrom ?: today.minus(6, DateTimeUnit.MONTH),
            title = stringResource(R.string.export_range_from),
            onDateSelected = { customFrom = it; showFromPicker = false },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        DatePickerModalDialog(
            initialDate = customTo ?: today,
            title = stringResource(R.string.export_range_to),
            onDateSelected = { customTo = it; showToPicker = false },
            onDismiss = { showToPicker = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date picker modal (Material3 DatePicker wrapped in a Dialog)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModalDialog(
    initialDate: LocalDate,
    title: String,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis: Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, initialDate.year)
        set(java.util.Calendar.MONTH, initialDate.month.ordinal) // Month enum ordinal: JAN=0..DEC=11
        set(java.util.Calendar.DAY_OF_MONTH, initialDate.day)
        set(java.util.Calendar.HOUR_OF_DAY, 12)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val epochDay = millis / 86_400_000L
                    val jDate = java.time.LocalDate.ofEpochDay(epochDay)
                    onDateSelected(LocalDate(jDate.year, jDate.monthValue, jDate.dayOfMonth))
                } else {
                    onDismiss()
                }
            }) { Text(stringResource(R.string.dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    ) {
        DatePicker(state = state, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RangeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
