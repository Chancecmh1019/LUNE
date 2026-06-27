package com.luneapp.official.domain.menstrual

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus

/**
 * 預測週期數據 — 包含完整的統計信心元數據。
 *
 * 升級要點：
 * - 加入信心分數（0.0-1.0）與信心等級
 * - 加入預測誤差窗口（± 天數），用於日曆視覺化的「模糊區間」
 * - 加入計算依據的週期數與週期標準差
 * - windowStart / windowEnd 便於 UI 直接渲染半透明信心區間
 */
data class PredictedCycle(
    val predictedStart: LocalDate,
    val predictedEnd: LocalDate?,

    /**
     * 預測信心分數，0.0（完全不確定）到 1.0（高度確定）。
     * 已套用 ConditionProfile.confidenceMultiplier 後的最終值。
     */
    val confidenceScore: Float = 0.5f,

    /**
     * 預測誤差窗口：±天數。
     * 例如 3 代表「預測日期可能提前或延後 3 天」。
     * 由週期標準差 × 1.5 計算得出，最小 1，最大 7。
     */
    val confidenceWindowDays: Int = 3,

    /**
     * 計算此預測所依據的歷史週期數量。
     * 越多數據，預測越可靠。
     */
    val basedOnCycleCount: Int = 0,

    /**
     * 歷史週期長度的標準差（天）。
     * 越小代表週期越規律，預測越準確。
     */
    val cycleStdDevDays: Float = 0f,
) {
    /**
     * 信心窗口的早期邊界（預測日期可能提前的最早日）
     */
    val windowStart: LocalDate
        get() = predictedStart.minus(confidenceWindowDays, DateTimeUnit.DAY)

    /**
     * 信心窗口的晚期邊界（預測日期可能延後的最晚日）
     */
    val windowEnd: LocalDate
        get() = predictedStart.plus(confidenceWindowDays, DateTimeUnit.DAY)

    /**
     * 信心等級，供 UI 選擇對應的視覺樣式
     */
    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidenceScore >= 0.75f -> ConfidenceLevel.HIGH
            confidenceScore >= 0.45f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

    /**
     * 給定某一日期，是否落在此預測的信心窗口內
     */
    fun isInConfidenceWindow(date: LocalDate): Boolean =
        date in windowStart..windowEnd

    /**
     * 給定某一日期，是否是預測的月經期間（含誤差）
     */
    fun isInPredictedPeriod(date: LocalDate, avgPeriodLength: Int): Boolean {
        val end = predictedEnd ?: predictedStart.plus(avgPeriodLength - 1, DateTimeUnit.DAY)
        return date in predictedStart..end
    }
}

/**
 * 預測信心等級，用於 UI 視覺化
 */
enum class ConfidenceLevel {
    /** 高信心：週期規律，數據充足（≥ 6 個週期，標準差 < 3 天） */
    HIGH,
    /** 中等信心：數據中等，或有輕微不規律 */
    MEDIUM,
    /** 低信心：數據不足（< 3 個週期）或高度不規律 */
    LOW,
}
