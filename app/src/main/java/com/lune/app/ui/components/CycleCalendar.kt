package com.lune.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lune.app.domain.menstrual.CyclePhase
import com.lune.app.domain.menstrual.CyclePhaseInfo
import com.lune.app.domain.menstrual.CycleState
import kotlinx.datetime.*
import androidx.compose.ui.res.stringResource
import com.lune.app.R
import kotlin.time.Clock

// ============================================================
// Day type for calendar coloring
// ============================================================

enum class DayType {
    NONE, PERIOD, PREDICTED_PERIOD, OVULATION, PREDICTED_OVULATION,
}

// ============================================================
// Build date → type mapping from cycle data
// ============================================================

fun buildDateMap(
    state: CycleState,
    phaseInfo: CyclePhaseInfo?,
    today: LocalDate,
): Map<LocalDate, DayType> {
    val map = mutableMapOf<LocalDate, DayType>()
    val avgCycle = phaseInfo?.cycleLength ?: 28
    val avgPeriod = phaseInfo?.periodLength ?: 5

    state.records.forEach { record ->
        val end = record.endDate ?: today
        var d = record.startDate
        while (d <= end) {
            // For unconfirmed records, future days are predicted
            val type = if (!record.endConfirmed && d > today) DayType.PREDICTED_PERIOD else DayType.PERIOD
            map[d] = type
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    state.predictions.forEach { pred ->
        val pEnd = pred.predictedEnd ?: pred.predictedStart.plus(avgPeriod - 1, DateTimeUnit.DAY)
        var d = pred.predictedStart
        while (d <= pEnd) { if (d !in map) map[d] = DayType.PREDICTED_PERIOD; d = d.plus(1, DateTimeUnit.DAY) }
    }

    // Add ovulation phase based on unified logic
    // We iterate through recent cycles to mark ovulation
    val dates = map.keys.toList()
    if (dates.isNotEmpty()) {
        val minDate = dates.minOrNull()!!
        val maxDate = dates.maxOrNull()!!
        var d = minDate
        while (d <= maxDate) {
            if (map[d] == null || map[d] == DayType.NONE) {
                val phaseInfoForDate = CyclePhaseInfo.getPhaseInfo(d, state, avgCycle)
                if (phaseInfoForDate?.phase == CyclePhase.OVULATION) {
                    map[d] = DayType.OVULATION
                }
            }
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }

    return map
}

// ============================================================
// Reusable Cycle Calendar Grid — simple square cells
// ============================================================

private data class YearMonth(val year: Int, val month: Month)

private val TODAY_COLOR = Color(0xFF7C4DFF) // purple

/**
 * Reusable cycle-aware calendar grid with simple square cells.
 * No ovulation display, no complex shapes — just rounded-rect cells like HomeCalendar.
 */
@Composable
fun CycleCalendarGrid(
    state: CycleState,
    phaseInfo: CyclePhaseInfo?,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null,
    selectedStart: LocalDate? = null,
    selectedEnd: LocalDate? = null,
    onDateClick: ((LocalDate) -> Unit)? = null,
    isDateEnabled: (LocalDate) -> Boolean = { true },
    monthRange: IntRange = -3..2,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val dateMap = buildDateMap(state, phaseInfo, today)

    val months = monthRange.map { offset ->
        val m = today.plus(offset, DateTimeUnit.MONTH)
        YearMonth(m.year, m.month)
    }
    val initialIndex = (-monthRange.first).coerceIn(0, months.size - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(Unit) { listState.scrollToItem(initialIndex) }

    Column(modifier) {
        // Weekday header
        Row(Modifier.fillMaxWidth()) {
            val weekdays = listOf(
                R.string.weekday_short_mon, R.string.weekday_short_tue, R.string.weekday_short_wed,
                R.string.weekday_short_thu, R.string.weekday_short_fri, R.string.weekday_short_sat, R.string.weekday_short_sun
            )
            weekdays.forEach { dayRes ->
                Text(
                    stringResource(dayRes),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        SmallSpacer(4)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(months, key = { "${it.year}-${it.month}" }) { ym ->
            CalendarMonth(
                yearMonth = ym,
                today = today,
                dateMap = dateMap,
                selectedDate = selectedDate,
                selectedStart = selectedStart,
                selectedEnd = selectedEnd,
                onDateClick = onDateClick,
                isDateEnabled = isDateEnabled,
            )
        }
    }
    } // Column
}

// ============================================================
// Legend row — simplified (no ovulation)
// ============================================================

@Composable
fun CycleCalendarLegend(modifier: Modifier = Modifier) {
    val periodColor = MaterialTheme.colorScheme.error
    val cellShape = MaterialTheme.shapes.extraSmall

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(color = periodColor, text = stringResource(R.string.legend_period))
        LegendItem(color = periodColor.copy(alpha = 0.4f), text = stringResource(R.string.legend_predicted))
        LegendItem(color = TODAY_COLOR, text = stringResource(R.string.legend_today))
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================
// Month grid — simple square cells
// ============================================================

@Composable
private fun CalendarMonth(
    yearMonth: YearMonth,
    today: LocalDate,
    dateMap: Map<LocalDate, DayType>,
    selectedDate: LocalDate?,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onDateClick: ((LocalDate) -> Unit)?,
    isDateEnabled: (LocalDate) -> Boolean,
) {
    val periodColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val cellShape = MaterialTheme.shapes.extraSmall

    val firstDay = LocalDate(yearMonth.year, yearMonth.month, 1)
    val startOffset = firstDay.dayOfWeek.ordinal
    val daysInMonth = when (yearMonth.month) {
        Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY,
        Month.AUGUST, Month.OCTOBER, Month.DECEMBER -> 31
        Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
        Month.FEBRUARY -> if (yearMonth.year % 4 == 0 && (yearMonth.year % 100 != 0 || yearMonth.year % 400 == 0)) 29 else 28
    }
    val rows = (startOffset + daysInMonth + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        Text(
            "${monthName(yearMonth.month)} ${yearMonth.year}",
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = onSurface.copy(alpha = 0.5f),
        )

        for (row in 0 until rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - startOffset + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        Spacer(Modifier.weight(1f).height(32.dp))
                    } else {
                        val date = LocalDate(yearMonth.year, yearMonth.month, dayNum)
                        val type = dateMap[date] ?: DayType.NONE
                        val isToday = date == today
                        val isFuture = date > today
                        val enabled = isDateEnabled(date)
                        val isSelected = date == selectedDate ||
                                date == selectedStart || date == selectedEnd
                        val isInRange = selectedStart != null && selectedEnd != null &&
                                date > selectedStart && date < selectedEnd

                        val isPeriod = type == DayType.PERIOD
                        val isPredictedPeriod = type == DayType.PREDICTED_PERIOD

                        val bgColor = when {
                            isSelected -> primary
                            isInRange -> primary.copy(alpha = 0.12f)
                            isPeriod -> periodColor
                            isPredictedPeriod -> periodColor.copy(alpha = 0.35f)
                            isToday -> TODAY_COLOR
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            isSelected -> Color.White
                            isPeriod -> Color.White
                            isPredictedPeriod -> Color.White
                            isToday -> Color.White
                            isInRange -> primary
                            !enabled -> onSurface.copy(alpha = 0.2f)
                            isFuture -> onSurface.copy(alpha = 0.3f)
                            else -> onSurface
                        }
                        Surface(
                            modifier = Modifier.weight(1f).height(32.dp)
                                .then(if (enabled && onDateClick != null) Modifier.clickable { onDateClick(date) } else Modifier),
                            shape = cellShape,
                            color = bgColor,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "$dayNum",
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun monthName(month: Month): String = when (month) {
    Month.JANUARY -> stringResource(R.string.month_short_jan)
    Month.FEBRUARY -> stringResource(R.string.month_short_feb)
    Month.MARCH -> stringResource(R.string.month_short_mar)
    Month.APRIL -> stringResource(R.string.month_short_apr)
    Month.MAY -> stringResource(R.string.month_short_may)
    Month.JUNE -> stringResource(R.string.month_short_jun)
    Month.JULY -> stringResource(R.string.month_short_jul)
    Month.AUGUST -> stringResource(R.string.month_short_aug)
    Month.SEPTEMBER -> stringResource(R.string.month_short_sep)
    Month.OCTOBER -> stringResource(R.string.month_short_oct)
    Month.NOVEMBER -> stringResource(R.string.month_short_nov)
    Month.DECEMBER -> stringResource(R.string.month_short_dec)
}
