package com.luneapp.official.ui.pages.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.luneapp.official.domain.menstrual.CyclePhase
import com.luneapp.official.domain.menstrual.CyclePhaseInfo
import com.luneapp.official.ui.theme.expressiveShapes
import androidx.compose.ui.res.stringResource
import com.luneapp.official.R

@Composable
internal fun CyclePhase.displayName(): String = when (this) {
    CyclePhase.MENSTRUAL -> stringResource(R.string.phase_menstrual)
    CyclePhase.FOLLICULAR -> stringResource(R.string.phase_follicular)
    CyclePhase.OVULATION -> stringResource(R.string.phase_ovulation)
    CyclePhase.LUTEAL -> stringResource(R.string.phase_luteal)
}

/**
  * Day-aware label with phase-specific day count.
  * Shows "排卵日" (Ovulation day) on the peak day,
  * otherwise shows phase name with day count (e.g. "濾泡期 第3天").
 */
@Composable
internal fun CyclePhaseInfo.dayLabel(): String {
    if (isOvulationPeakDay) return stringResource(R.string.detail_ovulation_day)
    
    // Calculate day within current phase
    val phaseStart = phaseStartDay(phase)
    val dayInPhase = (dayInCycle - phaseStart + 1).coerceAtLeast(1)
    
    // Calculate phase length
    val nextPhase = when (phase) {
        CyclePhase.MENSTRUAL -> CyclePhase.FOLLICULAR
        CyclePhase.FOLLICULAR -> CyclePhase.OVULATION
        CyclePhase.OVULATION -> CyclePhase.LUTEAL
        CyclePhase.LUTEAL -> null  // Last phase in cycle
    }
    val phaseEnd = nextPhase?.let { phaseStartDay(it) - 1 } ?: cycleLength
    val phaseLength = (phaseEnd - phaseStart + 1).coerceAtLeast(1)
    
    val phaseName = phase.displayName()
    return stringResource(R.string.phase_day_label, phaseName, dayInPhase, phaseLength)
}

@Composable
internal fun CyclePhase.description(): String = when (this) {
    CyclePhase.MENSTRUAL -> stringResource(R.string.phase_menstrual_desc)
    CyclePhase.FOLLICULAR -> stringResource(R.string.phase_follicular_desc)
    CyclePhase.OVULATION -> stringResource(R.string.phase_ovulation_desc)
    CyclePhase.LUTEAL -> stringResource(R.string.phase_luteal_desc)
}

@Composable
internal fun CyclePhase.description(info: CyclePhaseInfo): String {
    if (this == CyclePhase.MENSTRUAL) {
        return when (info.dayInCycle) {
            1 -> stringResource(R.string.phase_menstrual_desc_day1)
            2 -> stringResource(R.string.phase_menstrual_desc_day2)
            3 -> stringResource(R.string.phase_menstrual_desc_day3)
            4 -> stringResource(R.string.phase_menstrual_desc_day4)
            else -> stringResource(R.string.phase_menstrual_desc_day5_plus)
        }
    }

    val phaseStart = info.phaseStartDay(this)
    val nextPhase = when (this) {
        CyclePhase.FOLLICULAR -> CyclePhase.OVULATION
        CyclePhase.OVULATION -> CyclePhase.LUTEAL
        else -> null
    }
    val phaseEnd = nextPhase?.let { info.phaseStartDay(it) - 1 } ?: info.cycleLength
    val phaseLen = (phaseEnd - phaseStart + 1).coerceAtLeast(1)
    val dayInPhase = (info.dayInCycle - phaseStart + 1).coerceIn(1, phaseLen)
    val progress = (dayInPhase - 1).toFloat() / phaseLen.toFloat()

    return when (this) {
        CyclePhase.FOLLICULAR -> when {
            progress < 0.34f -> stringResource(R.string.phase_follicular_desc_early)
            progress < 0.67f -> stringResource(R.string.phase_follicular_desc_mid)
            else -> stringResource(R.string.phase_follicular_desc_late)
        }
        CyclePhase.OVULATION -> when {
            info.isOvulationPeakDay -> stringResource(R.string.phase_ovulation_desc_mid)
            info.dayInCycle < info.peakDayInCycle -> stringResource(R.string.phase_ovulation_desc_early)
            else -> stringResource(R.string.phase_ovulation_desc_late)
        }
        CyclePhase.LUTEAL -> when {
            progress < 0.34f -> stringResource(R.string.phase_luteal_desc_early)
            progress < 0.67f -> stringResource(R.string.phase_luteal_desc_mid)
            else -> stringResource(R.string.phase_luteal_desc_late)
        }
        CyclePhase.MENSTRUAL -> description()
    }
}

@Composable
internal fun CyclePhase.shape(): Shape = when (this) {
    CyclePhase.MENSTRUAL -> MaterialTheme.expressiveShapes.arrow
    CyclePhase.FOLLICULAR -> MaterialTheme.expressiveShapes.flower
    CyclePhase.OVULATION -> MaterialTheme.expressiveShapes.sunny
    CyclePhase.LUTEAL -> MaterialTheme.expressiveShapes.bun
}

@Composable
internal fun CyclePhase.color(): Color = when (this) {
    CyclePhase.MENSTRUAL -> MaterialTheme.colorScheme.error
    CyclePhase.FOLLICULAR -> MaterialTheme.colorScheme.primary
    CyclePhase.OVULATION -> MaterialTheme.colorScheme.tertiary
    CyclePhase.LUTEAL -> MaterialTheme.colorScheme.secondary
}
