package com.luneapp.official.domain.menstrual

import kotlinx.datetime.*
import kotlin.time.Clock

/**
 * The four phases of a menstrual cycle.
 * Approximate proportions based on a standard 28-day cycle:
 * - Menstrual:   ~days 1-5   (0%V18%)
 * - Follicular:  ~days 6-13  (18%V46%)
 * - Ovulation:   ~days 14-16 (46%V57%)
 * - Luteal:      ~days 17-28 (57%V100%)
 */
enum class CyclePhase {
    MENSTRUAL,
    FOLLICULAR,
    OVULATION,
    LUTEAL,
}

/**
 * Current cycle phase information derived from historical data.
 */
data class CyclePhaseInfo(
    val phase: CyclePhase,
    val dayInCycle: Int,            // 1-based day within current cycle
    val cycleLength: Int,           // specific cycle length in days
    val periodLength: Int,          // average period length in days
    val progress: Float,            // 0f..1f progress through cycle
    val daysUntilNextPeriod: Int,   // days remaining until next predicted period
    val nextPeriodStart: LocalDate?,
) {
    companion object {
        /**
         * Calculates the cycle phase for a given date based on the most recent
         * period start and the configured cycle and period lengths.
         */
        fun getPhaseInfo(
            date: LocalDate,
            state: CycleState,
            avgCycleLength: Int,
        ): CyclePhaseInfo? {
            val allRecords = state.records.filter { !it.isDeleted }.sortedBy { it.startDate }
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            
            // 1. Find the start of the most recent period
            val refRecord = allRecords.lastOrNull { it.startDate <= date }
            val refPrediction = state.predictions.lastOrNull { it.predictedStart <= date }
            
            val cycleStart = if (refPrediction != null && (refRecord == null || refPrediction.predictedStart > refRecord.startDate)) {
                refPrediction.predictedStart
            } else {
                refRecord?.startDate
            } ?: return null
            
            // 2. Determine the end of the current cycle
            val currentCycleLen = if (cycleStart == refRecord?.startDate) {
                val index = allRecords.indexOf(refRecord)
                if (index < allRecords.size - 1) {
                    refRecord.startDate.until(allRecords[index + 1].startDate, DateTimeUnit.DAY).toInt()
                } else {
                    val nextPred = state.predictions.firstOrNull { it.predictedStart > refRecord.startDate }
                    nextPred?.let { refRecord.startDate.until(it.predictedStart, DateTimeUnit.DAY).toInt() } ?: avgCycleLength
                }
            } else {
                val pred = state.predictions.find { it.predictedStart == cycleStart }
                val index = state.predictions.indexOf(pred)
                if (index != -1 && index < state.predictions.size - 1) {
                    cycleStart.until(state.predictions[index + 1].predictedStart, DateTimeUnit.DAY).toInt()
                } else {
                    avgCycleLength
                }
            }
            
            val dayInCycle = cycleStart.until(date, DateTimeUnit.DAY).toInt() + 1
            if (dayInCycle < 1 || dayInCycle > avgCycleLength * 3) return null
            
            // 3. Determine phase
            val avgPeriod = averagePeriodLength(allRecords) ?: 5
            
            val inActualPeriod = allRecords.any { r ->
                val rEnd = r.endDate ?: today
                date in r.startDate..rEnd
            }
            val inPredictedPeriod = state.predictions.any { p ->
                val pEnd = p.predictedEnd ?: p.predictedStart.plus(avgPeriod - 1, DateTimeUnit.DAY)
                date in p.predictedStart..pEnd
            }
            
            val currentPeriodLen = refRecord?.let { r ->
                val end = r.endDate ?: if (r.startDate == allRecords.last().startDate && state.currentPeriod != null) today else null
                end?.let { r.startDate.until(it, DateTimeUnit.DAY).toInt() + 1 }
            } ?: avgPeriod
            
            // Peak day = 14 days before next period (medical convention),
            // i.e. day (currentCycleLen + 1) ? 14. Matches MenstrualService.ovulationPeakDate.
            val peakDayInCycle = (currentCycleLen - 13).coerceAtLeast(1)

            val phase = when {
                inActualPeriod || inPredictedPeriod -> CyclePhase.MENSTRUAL
                dayInCycle <= currentPeriodLen -> CyclePhase.MENSTRUAL
                dayInCycle < peakDayInCycle - 4 -> CyclePhase.FOLLICULAR
                dayInCycle <= peakDayInCycle + 1 -> CyclePhase.OVULATION
                else -> CyclePhase.LUTEAL
            }
            
            val progress = (dayInCycle.toFloat() / currentCycleLen).coerceIn(0f, 1f)
            val nextStart = state.predictions.firstOrNull { it.predictedStart > date }?.predictedStart
            val daysUntilNext = if (nextStart != null) date.until(nextStart, DateTimeUnit.DAY).toInt() else (currentCycleLen - dayInCycle).coerceAtLeast(0)
            
            return CyclePhaseInfo(
                phase = phase,
                dayInCycle = dayInCycle,
                cycleLength = currentCycleLen,
                periodLength = avgPeriod,
                progress = progress,
                daysUntilNextPeriod = daysUntilNext,
                nextPeriodStart = nextStart
            )
        }

    }

    /**
     * Day-in-cycle (1-based) of the predicted ovulation peak.
     * Convention: peak is 14 days before the *next* period, i.e. day (cycleLength + 1) ? 14.
     * Matches MenstrualService.getMenstrualCycle().ovulationPeakDate.
     */
    val peakDayInCycle: Int get() = (cycleLength - 13).coerceAtLeast(1)

    /** True when this date is the predicted ovulation peak (ovulation day). */
    val isOvulationPeakDay: Boolean get() = phase == CyclePhase.OVULATION && dayInCycle == peakDayInCycle

    /** Start day (1-based) of each phase within this cycle. */
    fun phaseStartDay(p: CyclePhase): Int = when (p) {
        CyclePhase.MENSTRUAL -> 1
        CyclePhase.FOLLICULAR -> periodLength + 1
        CyclePhase.OVULATION -> peakDayInCycle - 4
        CyclePhase.LUTEAL -> peakDayInCycle + 2
    }

    /** 
     * Days until a given phase starts. 
     * Negative = already past (days since end), 0 = current phase, Positive = future phase 
     */
    fun daysUntilPhase(p: CyclePhase): Int {
        if (p == phase) return 0
        val startDay = phaseStartDay(p)
        return if (startDay > dayInCycle) {
            // Future phase in current cycle
            startDay - dayInCycle
        } else {
            // Phase already passed this cycle — return negative value
            // to indicate how many days ago it ended
            startDay - dayInCycle
        }
    }
    
    /** Check if a phase is in the past for this cycle */
    fun isPhasePast(p: CyclePhase): Boolean {
        if (p == phase) return false
        val startDay = phaseStartDay(p)
        return startDay < dayInCycle
    }
}
