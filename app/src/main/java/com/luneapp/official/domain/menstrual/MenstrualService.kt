package com.luneapp.official.domain.menstrual

import kotlinx.datetime.*
import me.tatarka.inject.annotations.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock

data class MenstrualCycle(
    val record: MenstrualRecord,
    val cycleLength: Int?,
    val cycleEndDate: LocalDate,
    val ovulationDates: Set<LocalDate>,
    val ovulationPeakDate: LocalDate,
    /**
     * 是否為異常週期。
     * 升級：雙向異常（過短 <21 天 OR 過長 >90 天），不再只判斷 <14 天。
     */
    val isAnomaly: Boolean = false,
)

@Inject
class MenstrualService(private val repository: RecordsRepository) {

    // ---------- Record period arrival / departure ----------

    /**
     * User confirms period has arrived on [startDate].
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
     * User confirms period ended on [endDate].
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
     *
     * 升級要點：
     * - 傳入 [userStatus] 後取得 ConditionProfile
     * - 使用 ConditionProfile 決定異常值範圍、自動確認、預測開關
     * - 預測結果現在包含完整信心元數據
     *
     * @param cycleLength 用戶在設定中配置的週期長度（預設 28）
     * @param userStatus  用戶的健康情境，決定演算法行為
     */
    suspend fun getCycleState(
        cycleLength: Int = 28,
        userStatus: com.luneapp.official.domain.settings.UserStatus =
            com.luneapp.official.domain.settings.UserStatus.REGULAR,
    ): CycleState {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val profile = userStatus.toConditionProfile()
        val all = repository.getAllRecords()

        // 使用條件感知的預測引擎
        val predictions = predictNextCyclesWithProfile(all, profile, cycleLength)

        // 自動確認邏輯：僅在 ConditionProfile 允許時執行
        if (profile.autoConfirmEnabled) {
            autoConfirmPastPredictions(all, predictions, today)
        }

        // Re-read after potential auto-confirmation inserts
        val records = repository.getAllRecords()
            .sortedByDescending { it.startDate }
            .take(10)

        val updatedPredictions = if (profile.predictionsEnabled) {
            predictNextCyclesWithProfile(repository.getAllRecords(), profile, cycleLength)
        } else {
            // 預測停用（如產後、化療），仍可返回空列表
            emptyList()
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

    // ---------- Prediction (public) ----------

    suspend fun predictNextCycles(count: Int = 3, cycleLength: Int = 28): List<PredictedCycle> =
        predictNextCyclesWithProfile(repository.getAllRecords(), null, cycleLength, count)

    // ---------- Cycle Analysis ----------

    /**
     * Get detailed cycle information for a specific record.
     *
     * 升級要點：
     * 1. isAnomaly：雙向異常判定（<21 天 OR >90 天）
     * 2. 排卵窗口：修正為 -5..0（WHO 標準 6 天受孕窗口：排卵日前5天+排卵日當天）
     * 3. 當 ConditionProfile 停用排卵追蹤時，返回空 ovulationDates
     */
    fun getMenstrualCycle(
        record: MenstrualRecord,
        allRecords: List<MenstrualRecord>,
        predictions: List<PredictedCycle> = emptyList(),
        defaultCycleLength: Int = 28,
        profile: ConditionProfile? = null,
    ): MenstrualCycle {
        val sorted = allRecords.filter { !it.isDeleted }.sortedBy { it.startDate }
        val index = sorted.indexOfFirst { it.id == record.id }

        val cycleLength = if (index != -1 && index < sorted.size - 1) {
            sorted[index].startDate.until(sorted[index + 1].startDate, DateTimeUnit.DAY).toInt()
        } else null

        // 升級：雙向異常值判定
        val validRange = profile?.validCycleLengthRange ?: 21..90
        val isAnomaly = cycleLength != null && cycleLength !in validRange

        val effectiveLength = cycleLength ?: defaultCycleLength
        val cycleEndDate = if (index != -1 && index < sorted.size - 1) {
            sorted[index + 1].startDate.plus(-1, DateTimeUnit.DAY)
        } else {
            val nextStart = predictions.firstOrNull()?.predictedStart
                ?: record.startDate.plus(effectiveLength, DateTimeUnit.DAY)
            nextStart.plus(-1, DateTimeUnit.DAY)
        }

        // 排卵日計算：週期倒數 14 天（count-back method）
        val peakDayOffset = (effectiveLength - 14).coerceAtLeast(0)
        val peakDate = record.startDate.plus(peakDayOffset, DateTimeUnit.DAY)

        // ✅ 修正排卵窗口：-5..0（WHO 標準：排卵日前5天+排卵日當天）
        // 原本 -4..1 錯誤往後偏移了1天
        val ovulationDates = if (profile?.ovulationTrackingEnabled != false) {
            buildSet {
                for (offset in -5..0) {
                    add(peakDate.plus(offset, DateTimeUnit.DAY))
                }
            }
        } else {
            // 荷爾蒙避孕、化療等情況停用排卵計算
            emptySet()
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

    // ---------- Private: Condition-Aware Prediction Engine ----------

    /**
     * 條件感知的預測引擎（核心升級）。
     *
     * 改進項目：
     * 1. 使用 ConditionProfile.validCycleLengthRange 進行雙向過濾（取代舊的 >= 14 單向過濾）
     * 2. 加權平均保持不變（越新記錄權重越高）
     * 3. 計算週期標準差，用於信心分數
     * 4. 套用 ConditionProfile.confidenceMultiplier 得出最終信心分數
     * 5. 每個 PredictedCycle 包含完整信心元數據
     *
     * @param records  所有歷史記錄
     * @param profile  使用者的 ConditionProfile（null 時使用預設規律用戶設定）
     * @param fallbackCycleLength 無歷史數據時使用的預設週期長度
     * @param count    要預測的週期數量
     */
    private fun predictNextCyclesWithProfile(
        records: List<MenstrualRecord>,
        profile: ConditionProfile?,
        fallbackCycleLength: Int = 28,
        count: Int = 3,
    ): List<PredictedCycle> {
        // 若 ConditionProfile 停用預測，直接返回
        if (profile?.predictionsEnabled == false) return emptyList()

        val validRange = profile?.validCycleLengthRange ?: 21..35
        val sorted = records.filter { !it.isDeleted }.sortedBy { it.startDate }

        // 計算原始週期長度列表
        val rawLengths = mutableListOf<Int>()
        for (i in 0 until sorted.size - 1) {
            val length = sorted[i].startDate.until(sorted[i + 1].startDate, DateTimeUnit.DAY).toInt()
            rawLengths.add(length)
        }

        // ✅ 條件感知雙向過濾（取代舊的 >= 14 單向過濾）
        val validLengths = filterValidCycleLengths(rawLengths, validRange)

        // 計算加權平均週期長度
        val effectiveCycleLength: Int
        val stdDev: Float
        val cycleCount: Int

        if (validLengths.isNotEmpty()) {
            effectiveCycleLength = weightedAverageCycleLength(validLengths)?.roundToInt()
                ?: fallbackCycleLength
            stdDev = cycleStdDev(validLengths)
            cycleCount = validLengths.size
        } else {
            effectiveCycleLength = fallbackCycleLength
            stdDev = 0f
            cycleCount = 0
        }

        // 計算原始信心分數
        val rawConfidence = calculateConfidenceScore(cycleCount, stdDev)

        // 套用 ConditionProfile 乘數得出最終信心
        val finalConfidence = profile?.applyConfidenceMultiplier(rawConfidence) ?: rawConfidence

        // 計算誤差窗口
        val windowDays = calculateConfidenceWindowDays(stdDev)

        val avgPeriodLength = averagePeriodLength(records)
        val lastStart = records
            .filter { !it.isDeleted }
            .maxByOrNull { it.startDate }?.startDate ?: return emptyList()

        return (1..count).map { i ->
            val start = lastStart.plus(effectiveCycleLength * i, DateTimeUnit.DAY)
            val end = avgPeriodLength?.let { start.plus(it - 1, DateTimeUnit.DAY) }
            PredictedCycle(
                predictedStart = start,
                predictedEnd = end,
                confidenceScore = finalConfidence,
                confidenceWindowDays = windowDays,
                basedOnCycleCount = cycleCount,
                cycleStdDevDays = stdDev,
            )
        }
    }

    private fun newId() = "record_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
}
