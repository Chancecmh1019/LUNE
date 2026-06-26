package com.luneapp.official.ui.pages.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luneapp.official.ui.components.PrimaryCta
import com.luneapp.official.ui.components.SmallSpacer
import com.luneapp.official.ui.pages.sheet.sheets.GenericDatePickerSheet
import io.github.adrcotfas.datetime.names.TextStyle
import io.github.adrcotfas.datetime.names.getDisplayName
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import androidx.compose.ui.res.stringResource
import com.luneapp.official.R
import kotlin.time.Clock

/** Layout variants for the period-status step. */
internal enum class PeriodStatusMode {
    /** New user with no history — three options including "I'm not sure". */
    Full,

    /** User already imported records via HealthSync — only ask if currently bleeding. */
    SimpleYesNo,
}

/** Which option the user has tapped. */
internal enum class PeriodStatusOption { Active, Ended, Unsure }

/**
 * Asks the user what state they're in: currently on their period, just finished,
 * or unsure. Adapts to [mode] — the SimpleYesNo variant hides the "ended" option
 * since we already have history from HealthSync.
 */
@Composable
internal fun PeriodStatusStep(
    mode: PeriodStatusMode,
    importedCount: Int,
    selected: PeriodStatusOption?,
    activeStart: LocalDate?,
    endedStart: LocalDate?,
    endedEnd: LocalDate?,
    onSelect: (PeriodStatusOption) -> Unit,
    onActiveStartChange: (LocalDate) -> Unit,
    onEndedStartChange: (LocalDate) -> Unit,
    onEndedEndChange: (LocalDate) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val earliest = remember(today) { today.minus(3, DateTimeUnit.MONTH) }

    var openPicker by remember { mutableStateOf<PickerTarget?>(null) }

    val canContinue = when (selected) {
        PeriodStatusOption.Active -> activeStart != null
        PeriodStatusOption.Ended -> endedStart != null && endedEnd != null && endedEnd >= endedStart
        PeriodStatusOption.Unsure -> true
        null -> false
    }

    val titleRes = when (mode) {
        PeriodStatusMode.Full -> R.string.onboarding_status_title
        PeriodStatusMode.SimpleYesNo -> R.string.onboarding_status_simple_title
    }
    val subtitleRes = when (mode) {
        PeriodStatusMode.Full -> R.string.onboarding_status_subtitle
        PeriodStatusMode.SimpleYesNo -> R.string.onboarding_status_simple_subtitle
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(8)
        Text(
            stringResource(subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode == PeriodStatusMode.SimpleYesNo && importedCount > 0) {
            SmallSpacer(12)
            ImportedBadge(count = importedCount)
        }

        SmallSpacer(24)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                icon = Icons.Outlined.WaterDrop,
                accent = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.onboarding_status_active_title),
                subtitle = stringResource(R.string.onboarding_status_active_subtitle),
                selected = selected == PeriodStatusOption.Active,
                onClick = { onSelect(PeriodStatusOption.Active) },
            ) {
                DateRow(
                    label = stringResource(R.string.onboarding_status_active_start_label),
                    value = activeStart,
                    placeholder = stringResource(R.string.onboarding_status_pick_date),
                    onClick = { openPicker = PickerTarget.ActiveStart },
                )
            }

            if (mode == PeriodStatusMode.Full) {
                StatusCard(
                    icon = Icons.Outlined.CalendarMonth,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.onboarding_status_ended_title),
                    subtitle = stringResource(R.string.onboarding_status_ended_subtitle),
                    selected = selected == PeriodStatusOption.Ended,
                    onClick = { onSelect(PeriodStatusOption.Ended) },
                ) {
                    DateRow(
                        label = stringResource(R.string.onboarding_status_ended_start_label),
                        value = endedStart,
                        placeholder = stringResource(R.string.onboarding_status_pick_start),
                        onClick = { openPicker = PickerTarget.EndedStart },
                    )
                    SmallSpacer(8)
                    DateRow(
                        label = stringResource(R.string.onboarding_status_ended_end_label),
                        value = endedEnd,
                        placeholder = stringResource(R.string.onboarding_status_pick_end),
                        onClick = { openPicker = PickerTarget.EndedEnd },
                    )
                }
            }

            val unsureTitle = if (mode == PeriodStatusMode.Full)
                R.string.onboarding_status_unsure_title
            else
                R.string.onboarding_status_simple_no_title
            val unsureSubtitle = if (mode == PeriodStatusMode.Full)
                R.string.onboarding_status_unsure_subtitle
            else
                R.string.onboarding_status_simple_no_subtitle

            StatusCard(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                accent = MaterialTheme.colorScheme.secondary,
                title = stringResource(unsureTitle),
                subtitle = stringResource(unsureSubtitle),
                selected = selected == PeriodStatusOption.Unsure,
                onClick = { onSelect(PeriodStatusOption.Unsure) },
            ) {
                if (mode == PeriodStatusMode.Full) {
                    Text(
                        stringResource(R.string.onboarding_status_unsure_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SmallSpacer(16)
        PrimaryCta(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = canContinue,
        )
        SmallSpacer(4)
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
        }
    }

    when (openPicker) {
        PickerTarget.ActiveStart -> GenericDatePickerSheet(
            titleRes = R.string.onboarding_status_active_start_label,
            minDate = earliest,
            maxDate = today,
            defaultDate = activeStart ?: today,
            onDismiss = { openPicker = null },
            onConfirm = {
                onActiveStartChange(it)
                openPicker = null
            },
        )
        PickerTarget.EndedStart -> GenericDatePickerSheet(
            titleRes = R.string.onboarding_status_ended_start_label,
            minDate = earliest,
            maxDate = endedEnd ?: today,
            defaultDate = endedStart ?: today.minus(7, DateTimeUnit.DAY),
            onDismiss = { openPicker = null },
            onConfirm = {
                onEndedStartChange(it)
                openPicker = null
            },
        )
        PickerTarget.EndedEnd -> GenericDatePickerSheet(
            titleRes = R.string.onboarding_status_ended_end_label,
            minDate = endedStart ?: earliest,
            maxDate = today,
            defaultDate = endedEnd ?: endedStart ?: today,
            onDismiss = { openPicker = null },
            onConfirm = {
                onEndedEndChange(it)
                openPicker = null
            },
        )
        null -> Unit
    }
}

private enum class PickerTarget { ActiveStart, EndedStart, EndedEnd }

@Composable
private fun ImportedBadge(count: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.onboarding_status_imported_chip, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    val container = if (selected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val border = if (selected) BorderStroke(2.dp, accent.copy(alpha = 0.75f)) else null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SmallSpacer(2)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(Modifier.padding(top = 16.dp)) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
private fun DateRow(
    label: String,
    value: LocalDate?,
    placeholder: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value?.let { formatPickedDate(it) } ?: placeholder,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (value != null)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatPickedDate(date: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT)
    return "${date.month.number}/${date.day} $weekday"
}
