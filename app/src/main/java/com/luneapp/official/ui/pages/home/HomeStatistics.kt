package com.luneapp.official.ui.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.luneapp.official.R
import com.luneapp.official.domain.menstrual.CycleState
import com.luneapp.official.domain.menstrual.cycleStdDev
import com.luneapp.official.domain.menstrual.calculateConfidenceScore
import com.luneapp.official.domain.menstrual.calculateConfidenceWindowDays
import com.luneapp.official.ui.components.SmallSpacer
import com.luneapp.official.ui.pages.sheet.SheetViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import kotlin.math.roundToInt

/**
 * Inline statistics view shown when user toggles to stats mode on the home screen.
 * Displays bar chart, averages, advanced regularity analysis, and history table.
 */

@Composable
internal fun HomeStatistics(
    cycleState: CycleState,
    sheetViewModel: SheetViewModel,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showLegendDialog by remember { mutableStateOf(false) }

    val allRecords = cycleState.records.filter { !it.isDeleted }
    val sortedAsc = allRecords.sortedBy { it.startDate }

    // Compute stats — always based on last 6 cycles for average (UI convention)
    val completedRecords = sortedAsc.filter { it.endDate != null && it.endConfirmed }
    
    // Exclude anomaly cycles shorter than 14 days (or use proper filtering logic)
    val validCycleGaps = sortedAsc.zipWithNext().mapNotNull { (a, b) ->
        val gap = a.startDate.until(b.startDate, DateTimeUnit.DAY).toInt()
        if (gap >= 14) gap else null
    }
    
    val last6Gaps = validCycleGaps.takeLast(6)
    val last12Gaps = validCycleGaps.takeLast(12) // for better stdDev calculation
    val last6Completed = completedRecords.takeLast(6)
    
    val avgPeriod = if (last6Completed.isNotEmpty()) {
        last6Completed.map { it.startDate.until(it.endDate!!, DateTimeUnit.DAY).toInt() + 1 }.average().roundToInt()
    } else null
    val avgCycle = if (last6Gaps.isNotEmpty()) {
        last6Gaps.average().roundToInt()
    } else null

    val stdDev = if (last12Gaps.size >= 2) cycleStdDev(last12Gaps) else null
    val confidence = if (stdDev != null) calculateConfidenceScore(last12Gaps.size, stdDev) else null
    val windowDays = if (stdDev != null) calculateConfidenceWindowDays(stdDev) else null

    if (allRecords.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.stats_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Bar Chart
        item {
            BarChartSection(
                records = sortedAsc,
                avgCycle = avgCycle,
                predictedCycleLength = avgCycle,
                onHelpClick = { showLegendDialog = true },
            )
        }

        // Averages
        if (avgPeriod != null || avgCycle != null) {
            item {
                AveragesCard(avgPeriod = avgPeriod, avgCycle = avgCycle)
            }
        }

        // Advanced Regularity Analysis Card (New Feature)
        if (stdDev != null && confidence != null && windowDays != null) {
            item {
                RegularityAnalysisCard(
                    stdDevDays = stdDev,
                    confidenceScore = confidence,
                    windowDays = windowDays,
                    cycleCount = last12Gaps.size
                )
            }
        }

        // History Title
        item {
            Text(
                stringResource(R.string.stats_history_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp),
            )
        }

        // History Table
        val defaultCycleLength = avgCycle ?: 28
        sortedAsc.reversed().forEach { record ->
            item(key = record.id) {
                val isCurrent = record == sortedAsc.last() && !record.endConfirmed
                RecordCard(
                    record = record,
                    sortedAsc = sortedAsc,
                    isCurrent = isCurrent,
                    defaultCycleLength = defaultCycleLength,
                    onClick = {
                        scope.launch {
                            sheetViewModel.showAndHandleRecordDetail(record, defaultCycleLength)
                            onRefresh()
                        }
                    },
                )
            }
        }

        // Bottom padding so content doesn't hide behind floating toolbar
        item {
            SmallSpacer(64)
        }
    }

    if (showLegendDialog) {
        ChartLegendDialog(onDismiss = { showLegendDialog = false })
    }
}

@Composable
private fun AveragesCard(avgPeriod: Int?, avgCycle: Int?) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                stringResource(R.string.stats_avg_section_title, 6),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SmallSpacer(28)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Period days
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${avgPeriod ?: "-"}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SmallSpacer(4)
                    Text(
                        stringResource(R.string.stats_period_days),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Cycle days
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${avgCycle ?: "-"}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SmallSpacer(4)
                    Text(
                        stringResource(R.string.stats_cycle_days),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 新增的週期規律性分析卡片。
 * 顯示標準差、預測信心分數、以及預測誤差區間。
 */
@Composable
private fun RegularityAnalysisCard(
    stdDevDays: Float,
    confidenceScore: Float,
    windowDays: Int,
    cycleCount: Int
) {
    val regularityLabel = when {
        stdDevDays < 3.0f -> stringResource(R.string.stats_regularity_highly)
        stdDevDays < 7.0f -> stringResource(R.string.stats_regularity_moderately)
        else -> stringResource(R.string.stats_regularity_irregular)
    }

    val confidencePercent = (confidenceScore * 100).roundToInt()

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.stats_analysis_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                // 規律性標籤 (Highly Regular 等) 以 Badge 形式呈現
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        regularityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            SmallSpacer(28)
            
            // Metrics row (完全比照 AveragesCard 的字體與排版)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Std Dev
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format("%.1f", stdDevDays),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SmallSpacer(4)
                    Text(
                        stringResource(R.string.stats_std_dev_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
                // Error Window
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "±$windowDays",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SmallSpacer(4)
                    Text(
                        stringResource(R.string.stats_pred_window_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Confidence
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$confidencePercent%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SmallSpacer(4)
                    Text(
                        stringResource(R.string.stats_confidence_percent, confidencePercent).replace(Regex("\\d+%\\s*"), ""), // 提取純文字標籤
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}