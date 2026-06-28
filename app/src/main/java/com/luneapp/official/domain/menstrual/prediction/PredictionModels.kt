package com.luneapp.official.domain.menstrual.prediction

import com.luneapp.official.domain.menstrual.MenstrualRecord
import kotlinx.datetime.LocalDate

/**
 * Data quality assessment for prediction reliability.
 * 
 * Based on quantity and consistency of historical cycle data:
 * - INSUFFICIENT: < 3 cycles - Cannot make reliable predictions
 * - POOR: 3-5 cycles or CV > 0.25 - High uncertainty
 * - FAIR: 6-9 cycles, CV 0.15-0.25 - Moderate reliability
 * - GOOD: 10-14 cycles, CV 0.10-0.15 - Good reliability
 * - EXCELLENT: 15+ cycles, CV < 0.10 - High reliability
 * 
 * CV (Coefficient of Variation) = stdDev / mean, measures cycle regularity
 */
enum class DataQuality {
    INSUFFICIENT,  // < 3 cycles
    POOR,          // 3-5 cycles or highly irregular (CV > 0.25)
    FAIR,          // 6-9 cycles with moderate regularity (CV 0.15-0.25)
    GOOD,          // 10-14 cycles with good regularity (CV 0.10-0.15)
    EXCELLENT;     // 15+ cycles with high consistency (CV < 0.10)
    
    val minCyclesRequired: Int
        get() = when (this) {
            INSUFFICIENT -> 0
            POOR -> 3
            FAIR -> 6
            GOOD -> 10
            EXCELLENT -> 15
        }
}

/**
 * A single cycle computed from consecutive period records.
 * 
 * @param startDate First day of the period
 * @param nextStartDate First day of the next period (defines cycle length)
 * @param periodDurationDays Length of menstrual bleeding in days
 */
data class CycleData(
    val startDate: LocalDate,
    val nextStartDate: LocalDate,
    val periodDurationDays: Int
) {
    val lengthDays: Int = 
        startDate.daysUntil(nextStartDate)
    
    /**
     * Whether this cycle is an outlier based on medical definitions.
     * Abnormally short (< 21 days) or long (> 45 days) for regular users.
     * ConditionProfile may override these ranges.
     */
    fun isOutlier(validRange: IntRange = 21..45): Boolean =
        lengthDays !in validRange
}

/**
 * Prediction from a single algorithm/predictor.
 * 
 * @param predictorName Identifier for the algorithm (e.g., "Median", "TrendAware")
 * @param predictedDate Predicted next period start date
 * @param confidence Algorithm's self-assessed confidence (0.0-1.0)
 * @param historicalMAE Mean Absolute Error from backtesting (days), if available
 */
data class PredictionCandidate(
    val predictorName: String,
    val predictedDate: LocalDate,
    val confidence: Float,
    val historicalMAE: Float? = null
)

/**
 * Backtesting evaluation result for a predictor.
 * 
 * Uses leave-one-cycle-out cross-validation:
 * For each historical cycle, predict it using all previous cycles,
 * then measure error against actual date.
 * 
 * @param predictorName Identifier for the algorithm
 * @param meanAbsoluteError Average prediction error in days
 * @param evaluatedCycles Number of cycles used in evaluation
 * @param weight Normalized weight for ensemble (inversely proportional to MAE)
 */
data class PredictorEvaluation(
    val predictorName: String,
    val meanAbsoluteError: Float,
    val evaluatedCycles: Int,
    val weight: Float
) {
    val isReliable: Boolean
        get() = evaluatedCycles >= 3 && meanAbsoluteError < 7f
}

/**
 * Final ensemble prediction with full metadata.
 * 
 * This is what the UI consumes. Contains:
 * - Most likely prediction date (weighted ensemble result)
 * - Confidence score and level
 * - Prediction window (± days representing uncertainty)
 * - All candidate predictions for transparency
 * - Data quality assessment
 * - Human-readable explanation
 * 
 * @param predictedDate Most likely next period start date (ensemble result)
 * @param confidenceScore Overall confidence 0.0-1.0 (combines data quality + predictor agreement)
 * @param confidenceWindowDays Uncertainty window: actual date may be ± this many days
 * @param dataQuality Assessment of historical data quality
 * @param candidates Individual predictions from all algorithms
 * @param selectedPredictorName Primary algorithm used (highest weight in ensemble)
 * @param explanation Human-readable description of prediction basis
 * @param windowStart Early boundary of confidence window
 * @param windowEnd Late boundary of confidence window
 */
data class EnsemblePrediction(
    val predictedDate: LocalDate,
    val confidenceScore: Float,
    val confidenceWindowDays: Int,
    val dataQuality: DataQuality,
    val candidates: List<PredictionCandidate>,
    val selectedPredictorName: String,
    val explanation: String,
    val basedOnCycleCount: Int,
    val cycleStdDevDays: Float
) {
    val windowStart: LocalDate
        get() = predictedDate.minusDays(confidenceWindowDays)
    
    val windowEnd: LocalDate
        get() = predictedDate.plusDays(confidenceWindowDays)
    
    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidenceScore >= 0.75f -> ConfidenceLevel.HIGH
            confidenceScore >= 0.45f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    
    fun isDateInWindow(date: LocalDate): Boolean =
        date in windowStart..windowEnd
}

/**
 * Visual confidence level for UI rendering.
 */
enum class ConfidenceLevel {
    /** High confidence: ≥6 cycles, low std dev, good predictor agreement */
    HIGH,
    /** Medium confidence: 3-5 cycles or moderate irregularity */
    MEDIUM,
    /** Low confidence: <3 cycles or high irregularity */
    LOW
}

/**
 * Extension: Calculate days between two dates.
 */
private fun LocalDate.daysUntil(other: LocalDate): Int =
    (other.toEpochDays() - this.toEpochDays()).toInt()

private fun LocalDate.minusDays(days: Int): LocalDate =
    kotlinx.datetime.LocalDate.Companion.fromEpochDays(
        this.toEpochDays() - days
    )

private fun LocalDate.plusDays(days: Int): LocalDate =
    kotlinx.datetime.LocalDate.Companion.fromEpochDays(
        this.toEpochDays() + days
    )
