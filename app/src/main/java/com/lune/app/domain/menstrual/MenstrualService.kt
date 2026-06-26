package com.lune.app.domain.menstrual

import kotlinx.datetime.*
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

data class MenstrualCycle(
    val record: MenstrualRecord,
    val cycleLength: Int?,
    val cycleEndDate: LocalDate,
    val ovulationDates: Set<LocalDate>,
    val ovulationPeakDate: LocalDate,
    val isAnomaly: Boolean = false // 是否為異常短週期（< 14天）
)

@Inject
class MenstrualService(private val repository: RecordsRepository) {

    // ---------- Record period arrival / departure ----------

    /**
     * User confirms "月經來了" — period has arrived on [startDate].
     * If [endDate] is provided (user selected it), use it directly.
     * Otherwise, creates a record with an estimated end date based on average period length.
     */
    suspend fun recordPeriodStart(startDate: LocalDate, endDate: LocalDate? = null): AddRecordResult {
        val all = repository.getAllRecords()

        val avgPeriod = averagePeriodLength(all) ?: 5
        val estimatedEnd = endDate ?: startDate.plus(avgPeriod - 1, DateTimeUnit.DAY)

        val overlaps = all.any { record ->
            val rEnd = record.endDate ?: startDate // treat legacy null-endDate as today
            record.startDate <= estimatedEnd && startDate <= rEnd
        }
        if (overlaps) return AddRecordResult.OverlappingPeriod

        val now = Clock.System.now().toEpochMilliseconds()
        return repository.insertRecord(
            MenstrualRecord(
                id = newId(), startDate = startDate, endDate = estimatedEnd,
                endConfirmed = endDate != null,
                createdAtEpochMillis = now, updatedAtEpochMillis = now,
                source = RecordSource.MANUAL,
            )
        )
    }

    /**
     * User confirms "月經結束了" — period ended on [endDate].
     * Finds the most recent record covering today (or the latest record) and sets its endDate.
     */
    suspend fun recordPeriodEnd(endDate: LocalDate): Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val all = repository.getAllRecords()

        // Find the record that covers today, or the most recent record
        val record = all.find { r ->
            val rEnd = r.endDate ?: today
            today in r.startDate..rEnd
        } ?: all.maxByOrNull { it.startDate } ?: return false

        if (endDate < record.startDate) return false
        val trimmedDailyRecords = record.dailyRecords.filter { it.date <= endDate }
        return repository.updateRecord(
            record.copy(
                endDate = endDate,
                endConfirmed = true,
                dailyRecords = trimmedDailyRecords,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    // ---------- Daily logging ----------

    suspend fun logDay(recordId: String, day: DailyRecord): Boolean {
        val record = repository.getAllRecords().find { it.id == recordId } ?: return false
        val updatedDaily = record.dailyRecords.filter { it.date != day.date } + day
        return repository.updateRecord(
            record.copy(dailyRecords = updatedDaily,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds())
        )
    }

    // ---------- Backfill ----------

    suspend fun backfillPeriod(startDate: LocalDate, endDate: LocalDate): AddRecordResult {
        if (endDate < startDate) return AddRecordResult.InvalidDateRange

        val all = repository.getAllRecords()
        val overlaps = all.any { record ->
            val rEnd = record.endDate ?: endDate
            record.startDate <= endDate && startDate <= rEnd
        }
        if (overlaps) return AddRecordResult.OverlappingPeriod

        val now = Clock.System.now().toEpochMilliseconds()
        return repository.insertRecord(
            MenstrualRecord(id = newId(), startDate = startDate, endDate = endDate,
                endConfirmed = true, createdAtEpochMillis = now, updatedAtEpochMillis = now)
        )
    }

    // ---------- State ----------

    /**
     * Get the current cycle state.
     * [cycleLength] is the user-configured cycle length from settings (default 28).
     * [userStatus] controls whether predictions are considered reliable.
     */
    suspend fun getCycleState(
        cycleLength: Int = 28,
        userStatus: com.lune.app.domain.settings.UserStatus =
            com.lune.app.domain.settings.UserStatus.REGULAR,
    ): CycleState {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val all = repository.getAllRecords()
        val predictions = predictNextCycles(all, cycleLength = cycleLength)

        // Auto-confirm predictions that are 3+ days past their end
        // only for regular users; irregular users may have amenorrhea.
        if (!userStatus.isIrregular) {
            autoConfirmPastPredictions(all, predictions, today)
        }

        // Re-read after potential auto-confirmation inserts
        val records = repository.getAllRecords()
            .sortedByDescending { it.startDate }
            .take(10)

        val updatedPredictions = if (userStatus.isIrregular) {
            // Supply predictions but mark state as unreliable via flag
            predictNextCycles(repository.getAllRecords(), cycleLength = cycleLength)
        } else {
            predictNextCycles(repository.getAllRecords(), cycleLength = cycleLength)
        }

        // Current period: a record covering today whose end hasn't been confirmed yet
        val currentPeriod = records.find { r ->
            !r.endConfirmed && r.endDate != null &&
            today in r.startDate..r.endDate
        }

        // In predicted period: today falls within a prediction (and no real record covers it)
        val inPredictedPeriod = currentPeriod == null && updatedPredictions.any { pred ->
            val avgPeriod = averagePeriodLength(records) ?: 5
            val pEnd = pred.predictedEnd ?: pred.predictedStart.plus(avgPeriod - 1, DateTimeUnit.DAY)
            today in pred.predictedStart..pEnd
        }

        return CycleState(
            records = records,
            predictions = updatedPredictions,
            currentPeriod = currentPeriod,
            inPredictedPeriod = inPredictedPeriod,
            predictionReliable = !userStatus.isIrregular,
        )
    }

    private suspend fun autoConfirmPastPredictions(
        existingRecords: List<MenstrualRecord>,
        predictions: List<PredictedCycle>,
        today: LocalDate,
    ) {
        val avgPeriod = averagePeriodLength(existingRecords) ?: 5

        for (pred in predictions) {
            val pEnd = pred.predictedEnd ?: pred.predictedStart.plus(avgPeriod - 1, DateTimeUnit.DAY)
            val confirmDate = pEnd.plus(3, DateTimeUnit.DAY)

            if (today < confirmDate) continue

            // Check no real record already covers this range
            val alreadyCovered = existingRecords.any { r ->
                val rEnd = r.endDate ?: today
                r.startDate <= pEnd && pred.predictedStart <= rEnd
            }
            if (alreadyCovered) continue

            val now = Clock.System.now().toEpochMilliseconds()
            repository.insertRecord(
                MenstrualRecord(
                    id = newId(),
                    startDate = pred.predictedStart,
                    endDate = pEnd,
                    endConfirmed = true,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    source = RecordSource.AUTO_CONFIRMED,
                )
            )
        }
    }

    // ---------- Edit / Delete ----------

    suspend fun editRecordDates(recordId: String, newStart: LocalDate?, newEnd: LocalDate?): Boolean {
        val record = repository.getAllRecords().find { it.id == recordId } ?: return false
        val start = newStart ?: record.startDate
        val end = newEnd ?: record.endDate
        if (end != null && end < start) return false
        val trimmedDaily = if (end != null) record.dailyRecords.filter { it.date in start..end } else record.dailyRecords
        return repository.updateRecord(
            record.copy(
                startDate = start,
                endDate = end,
                dailyRecords = trimmedDaily,
                updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    suspend fun deleteRecord(recordId: String): Boolean =
        repository.deleteRecord(recordId)

    // ---------- Data management ----------

    suspend fun clearAllData() {
        repository.clearAll()
    }

    /**
     * Return every non-deleted record, sorted by start date ascending.
     * Used by the PDF export flow.
     */
    suspend fun getAllRecords(): List<MenstrualRecord> =
        repository.getAllRecords()
            .filter { !it.isDeleted }
            .sortedBy { it.startDate }

    // ---------- Prediction ----------

    suspend fun predictNextCycles(count: Int = 3, cycleLength: Int = 28): List<PredictedCycle> =
        predictNextCycles(repository.getAllRecords(), count, cycleLength)

    // ---------- Cycle Analysis ----------

    /**
     * Get detailed cycle information for a specific record.
     * Logic:
     * 1. Cycle Length: If next record exists, use (next.start - this.start). Otherwise use [defaultCycleLength].
     * 2. Cycle End: The day before next record starts.
     * 3. Ovulation: Scientific calculation based on the *actual or estimated cycle length*.
     *    Standard fertile window is usually in the middle of the cycle.
     *    According to user request, we use the "count-back method" (CycleLength - 14).
     *    Luteal phase is relatively constant (around 14 days).
     */
    fun getMenstrualCycle(
        record: MenstrualRecord,
        allRecords: List<MenstrualRecord>,
        predictions: List<PredictedCycle> = emptyList(),
        defaultCycleLength: Int = 28
    ): MenstrualCycle {
        val sorted = allRecords.filter { !it.isDeleted }.sortedBy { it.startDate }
        val index = sorted.indexOfFirst { it.id == record.id }
        
        val cycleLength = if (index != -1 && index < sorted.size - 1) {
            sorted[index].startDate.until(sorted[index + 1].startDate, DateTimeUnit.DAY).toInt()
        } else null
        
        // 標記是否為異常短週期
        val isAnomaly = cycleLength != null && cycleLength < 14

        val effectiveLength = cycleLength ?: defaultCycleLength
        val cycleEndDate = if (index != -1 && index < sorted.size - 1) {
            sorted[index + 1].startDate.plus(-1, DateTimeUnit.DAY)
        } else {
            val nextStart = predictions.firstOrNull()?.predictedStart 
                ?: record.startDate.plus(effectiveLength, DateTimeUnit.DAY)
            nextStart.plus(-1, DateTimeUnit.DAY)
        }

        // 醫學標準計算法：排卵日通常在下次月經開始前的第14天左右。
        // 黃體期（排卵到下次月經）相對固定，約為14天。
        // 而濾泡期（月經開始到排卵）變動較大，週期的長短主要取決於濾泡期。
        val peakDayOffset = (effectiveLength - 14).coerceAtLeast(0)
        val peakDate = record.startDate.plus(peakDayOffset, DateTimeUnit.DAY)
        
        val ovulationDates = buildSet {
            // 若為異常短週期，排卵計算可能不適用，但這裡仍按公式給出一個參考，UI 層會提示異常
            // 排卵期窗口通常為6天（高峰日前4天到後1天）
            for (offset in -4..1) {
                add(peakDate.plus(offset, DateTimeUnit.DAY))
            }
        }

        return MenstrualCycle(
            record = record,
            cycleLength = cycleLength ?: defaultCycleLength,
            cycleEndDate = cycleEndDate,
            ovulationDates = ovulationDates,
            ovulationPeakDate = peakDate,
            isAnomaly = isAnomaly
        )
    }


    // ---------- Private ----------

    /**
     * Predict next cycles using the user-configured [cycleLength].
     */
    private fun predictNextCycles(records: List<MenstrualRecord>, count: Int = 3, cycleLength: Int = 28): List<PredictedCycle> {
        // 計算歷史平均週期長度，排除週期小於 14 天的記錄
        val sorted = records.filter { !it.isDeleted }.sortedBy { it.startDate }
        val cycleLengths = mutableListOf<Int>()
        for (i in 0 until sorted.size - 1) {
            val length = sorted[i].startDate.until(sorted[i + 1].startDate, DateTimeUnit.DAY).toInt()
            if (length >= 14) {
                cycleLengths.add(length)
            }
        }
        
        val effectiveCycleLength = if (cycleLengths.isNotEmpty()) {
            var weightedSum = 0.0
            var totalWeight = 0.0
            cycleLengths.forEachIndexed { index, length ->
                val weight = index + 1.0 // higher weight for recent cycles
                weightedSum += length * weight
                totalWeight += weight
            }
            kotlin.math.round(weightedSum / totalWeight).toInt()
        } else {
            cycleLength
        }

        val avgPeriodLength = averagePeriodLength(records)
        val lastStart = records.maxByOrNull { it.startDate }?.startDate ?: return emptyList()
        return (1..count).map { i ->
            val start = lastStart.plus(effectiveCycleLength * i, DateTimeUnit.DAY)
            PredictedCycle(start, avgPeriodLength?.let { start.plus(it - 1, DateTimeUnit.DAY) })
        }
    }


    private fun newId() = "record_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
}
