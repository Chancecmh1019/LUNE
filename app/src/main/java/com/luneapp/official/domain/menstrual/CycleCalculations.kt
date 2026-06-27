package com.luneapp.official.domain.menstrual

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.until
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 月經週期計算工具函數集
 *
 * 升級要點（v2）：
 * 1. averagePeriodLength() 改為加權平均（越近期記錄權重越高），與 MenstrualService 一致
 * 2. 加入 weightedAverageCycleLength()：提取可複用的加權平均邏輯
 * 3. 加入 cycleStdDev()：計算週期標準差，用於信心分數
 * 4. 加入 filterValidCycleLengths()：支援條件感知的雙向異常值過濾
 * 5. 加入 calculateConfidenceScore()：統一的信心分數計算公式
 */

/**
 * 計算加權平均月經長度（天數）。
 *
 * 改進：使用線性加權平均（較新記錄權重較高），
 * 與 MenstrualService.predictNextCycles() 的邏輯保持一致。
 * 只計算已確認結束日期的記錄（endConfirmed = true 或 endDate != null）。
 */
internal fun averagePeriodLength(records: List<MenstrualRecord>): Int? {
    val completed = records
        .filter { !it.isDeleted && it.endDate != null }
        .sortedBy { it.startDate }
    if (completed.isEmpty()) return null

    var weightedSum = 0.0
    var totalWeight = 0.0
    completed.forEachIndexed { index, record ->
        val len = record.startDate.until(record.endDate!!, DateTimeUnit.DAY).toInt() + 1
        val weight = (index + 1).toDouble()   // 越新，index 越大，weight 越高
        weightedSum += len * weight
        totalWeight += weight
    }
    return weightedSum.div(totalWeight).roundToInt()
}

/**
 * 依據條件感知的有效範圍過濾週期長度列表。
 *
 * @param rawLengths 原始週期長度列表（天數）
 * @param validRange ConditionProfile.validCycleLengthRange
 * @return 過濾後的有效週期長度列表
 *
 * 雙向過濾原則：
 * - 低於下界：可能為點狀出血誤記為完整週期
 * - 高於上界：規律用戶可能是記錄錯誤；PCOS 用戶上界設為 180 允許超長週期
 */
internal fun filterValidCycleLengths(
    rawLengths: List<Int>,
    validRange: IntRange = 21..35,
): List<Int> = rawLengths.filter { it in validRange }

/**
 * 計算一組週期長度的加權平均值（較新數據權重較高）。
 *
 * @param lengths 已過濾的有效週期長度列表（按時間從舊到新排列）
 * @return 加權平均週期長度，若列表為空則返回 null
 */
internal fun weightedAverageCycleLength(lengths: List<Int>): Double? {
    if (lengths.isEmpty()) return null
    var weightedSum = 0.0
    var totalWeight = 0.0
    lengths.forEachIndexed { index, len ->
        val weight = (index + 1).toDouble()
        weightedSum += len * weight
        totalWeight += weight
    }
    return weightedSum / totalWeight
}

/**
 * 計算週期長度的標準差（天數）。
 *
 * 標準差越小 = 週期越規律 = 預測越準確。
 * 用於計算信心分數的「穩定性」維度。
 *
 * @param lengths 有效週期長度列表
 * @return 標準差（天），若少於 2 個數據點則返回 0f
 */
internal fun cycleStdDev(lengths: List<Int>): Float {
    if (lengths.size < 2) return 0f
    val mean = lengths.average()
    val variance = lengths.map { (it - mean) * (it - mean) }.average()
    return sqrt(variance).toFloat()
}

/**
 * 計算預測信心分數（0.0 ~ 1.0）。
 *
 * 信心分數計算公式（加權兩個維度）：
 * - 數據量維度（40%）：數據越多，信心越高；最多計算到 12 個週期
 * - 穩定性維度（60%）：標準差越小，信心越高；以 7 天為標準差上限
 *
 * 此分數尚未套用 ConditionProfile.confidenceMultiplier，
 * 最終信心 = calculateConfidenceScore() × profile.confidenceMultiplier
 *
 * @param cycleCount 計算所依據的週期數量
 * @param stdDevDays 週期標準差（天）
 * @return 原始信心分數（0.0-1.0），尚未套用條件乘數
 */
internal fun calculateConfidenceScore(cycleCount: Int, stdDevDays: Float): Float {
    // 數據量分數：0 個週期 = 0.0，12+ 個週期 = 1.0
    val dataScore = (cycleCount.coerceIn(0, 12) / 12.0f)

    // 穩定性分數：標準差 0 天 = 1.0，7+ 天 = 0.0（線性）
    val stabilityScore = (1f - (stdDevDays / 7f).coerceIn(0f, 1f))

    return (dataScore * 0.4f + stabilityScore * 0.6f).coerceIn(0f, 1f)
}

/**
 * 由信心分數和標準差計算預測誤差窗口（± 天數）。
 *
 * 公式：窗口 = stdDev × 1.5，取整後限制在 1~7 天
 * 若無標準差數據（首次使用），預設窗口為 3 天
 *
 * @param stdDevDays 週期標準差（天）
 * @return 誤差窗口天數（正整數）
 */
internal fun calculateConfidenceWindowDays(stdDevDays: Float): Int {
    if (stdDevDays == 0f) return 3   // 預設值（少於 2 個週期）
    return (stdDevDays * 1.5f).roundToInt().coerceIn(1, 7)
}
