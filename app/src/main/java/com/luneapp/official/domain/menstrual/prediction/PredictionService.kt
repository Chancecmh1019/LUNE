package com.luneapp.official.domain.menstrual.prediction

import com.luneapp.official.domain.menstrual.ConditionProfile
import com.luneapp.official.domain.menstrual.MenstrualRecord
import com.luneapp.official.domain.menstrual.PredictedCycle
import com.luneapp.official.domain.menstrual.applyConfidenceMultiplier
import kotlinx.datetime.LocalDate

/**
 * Complete prediction service: orchestrates all components.
 * 
 * This is the main entry point for menstrual cycle prediction.
 * Replaces the simple weighted average logic with a sophisticated ensemble system.
 * 
 * Architecture:
 * 1. Data Processing: Convert records → clean cycles
 * 2. Predictor Evaluation: Backtest all algorithms on user's data
 * 3. Prediction Generation: Run all predictors on latest data
 * 4. Ensemble Combination: Weighted merge based on backtesting results
 * 5. Profile Adjustment: Apply ConditionProfile confidence multiplier
 * 
 * Usage:
 * ```kotlin
 * val service = StandardPredictionService()
 * val predictions = service.predictNextCycles(
 *     records = allMenstrualRecords,
 *     profile = userStatus.toConditionProfile(),
 *     count = 3
 * )
 * ```
 */
class StandardPredictionService(
    private val dataProcessor: CycleDataProcessor = DefaultCycleDataProcessor(),
    private val backtester: BacktestingEngine = DefaultBacktestingEngine(),
    private val ensemble: EnsemblePredictor = WeightedEnsemblePredictor()
) {
    
    // Available predictors
    private val predictors: List<CyclePredictor> = listOf(
        RobustStatisticalPredictor(useMedian = false),  // TrimmedMean (default)
        RobustStatisticalPredictor(useMedian = true),   // Median (fallback)
        TrendAwarePredictor()                            // Trend detection
    )
    
    /**
     * Predict next N menstrual cycles with full ensemble analysis.
     * 
     * @param records All menstrual records for this user
     * @param profile ConditionProfile (determines valid ranges, confidence multiplier)
     * @param count Number of future cycles to predict (default 3)
     * @return List of predicted cycles with confidence metadata
     */
    fun predictNextCycles(
        records: List<MenstrualRecord>,
        profile: ConditionProfile,
        count: Int = 3
    ): List<PredictedCycle> {
        
        // If profile disables predictions, return empty
        if (!profile.predictionsEnabled) return emptyList()
        
        // 1. Convert records to cycles
        val allCycles = dataProcessor.recordsToCycles(records)
        if (allCycles.isEmpty()) return emptyList()
        
        // 2. Filter outliers using profile's valid range
        val cleanCycles = dataProcessor.filterOutliers(allCycles, profile.validCycleLengthRange)
        if (cleanCycles.size < 3) return emptyList() // Need minimum data
        
        // 3. Assess data quality
        val dataQuality = dataProcessor.assessDataQuality(cleanCycles)
        if (dataQuality == DataQuality.INSUFFICIENT) return emptyList()
        
        // 4. Calculate cycle statistics
        val cycleLengths = cleanCycles.map { it.lengthDays }
        val meanLength = cycleLengths.average()
        val stdDev = if (cycleLengths.size > 1) {
            kotlin.math.sqrt(cycleLengths.map { (it - meanLength) * (it - meanLength) }.average())
        } else {
            0.0
        }.toFloat()
        
        // 5. Get last period start date
        val lastRecord = records
            .filter { !it.isDeleted }
            .maxByOrNull { it.startDate } ?: return emptyList()
        
        val lastPeriodStart = lastRecord.startDate
        
        // 6. Backtest all predictors
        val evaluations = backtester.evaluateAll(predictors, cleanCycles)
        
        // 7. Generate predictions from all predictors
        val candidates = predictors.mapNotNull { predictor ->
            try {
                if (predictor.canPredict(cleanCycles)) {
                    predictor.predict(cleanCycles, lastPeriodStart)
                } else {
                    null
                }
            } catch (e: Exception) {
                null  // Skip predictors that fail
            }
        }
        
        if (candidates.isEmpty()) return emptyList()
        
        // 8. Combine into ensemble prediction
        val baseEnsemble = ensemble.combine(
            candidates = candidates,
            evaluations = evaluations,
            dataQuality = dataQuality,
            cycleCount = cleanCycles.size,
            cycleStdDev = stdDev
        )
        
        // 9. Apply ConditionProfile confidence multiplier
        val adjustedConfidence = profile.applyConfidenceMultiplier(baseEnsemble.confidenceScore)
        
        // 10. Determine period duration for prediction end dates
        val avgPeriodDuration = calculateAveragePeriodDuration(records)
        
        // 11. Generate sequence of predictions
        return (1..count).map { cycleNumber ->
            val predictedStart = baseEnsemble.predictedDate.plusDays(
                (meanLength.toInt() * (cycleNumber - 1))
            )
            val predictedEnd = predictedStart.plusDays(avgPeriodDuration - 1)
            
            PredictedCycle(
                predictedStart = predictedStart,
                predictedEnd = predictedEnd,
                confidenceScore = adjustedConfidence,
                confidenceWindowDays = baseEnsemble.confidenceWindowDays,
                basedOnCycleCount = cleanCycles.size,
                cycleStdDevDays = stdDev
            )
        }
    }
    
    /**
     * Get detailed prediction analysis for advanced users/debugging.
     * 
     * Returns full ensemble prediction with all candidate predictions,
     * backtesting results, and data quality assessment.
     */
    fun getPredictionAnalysis(
        records: List<MenstrualRecord>,
        profile: ConditionProfile
    ): PredictionAnalysis? {
        
        if (!profile.predictionsEnabled) {
            return PredictionAnalysis(
                ensemble = null,
                evaluations = emptyList(),
                dataQuality = DataQuality.INSUFFICIENT,
                cycleCount = 0,
                outlierCount = 0,
                skippedCycles = emptyList(),
                message = "Predictions disabled for this health status"
            )
        }
        
        val allCycles = dataProcessor.recordsToCycles(records)
        if (allCycles.isEmpty()) return null
        
        val cleanCycles = dataProcessor.filterOutliers(allCycles, profile.validCycleLengthRange)
        if (cleanCycles.isEmpty()) return null
        
        val dataQuality = dataProcessor.assessDataQuality(cleanCycles)
        val skips = dataProcessor.detectSkips(allCycles)
        
        val cycleLengths = cleanCycles.map { it.lengthDays }
        val stdDev = if (cycleLengths.size > 1) {
            kotlin.math.sqrt(cycleLengths.map { 
                val mean = cycleLengths.average()
                (it - mean) * (it - mean) 
            }.average())
        } else {
            0.0
        }.toFloat()
        
        val lastRecord = records
            .filter { !it.isDeleted }
            .maxByOrNull { it.startDate } ?: return null
        
        val evaluations = backtester.evaluateAll(predictors, cleanCycles)
        
        val candidates = predictors.mapNotNull { predictor ->
            try {
                if (predictor.canPredict(cleanCycles)) {
                    predictor.predict(cleanCycles, lastRecord.startDate)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        
        val ensemble = if (candidates.isNotEmpty()) {
            ensemble.combine(
                candidates = candidates,
                evaluations = evaluations,
                dataQuality = dataQuality,
                cycleCount = cleanCycles.size,
                cycleStdDev = stdDev
            )
        } else {
            null
        }
        
        return PredictionAnalysis(
            ensemble = ensemble,
            evaluations = evaluations,
            dataQuality = dataQuality,
            cycleCount = cleanCycles.size,
            outlierCount = allCycles.size - cleanCycles.size,
            skippedCycles = skips,
            message = "Analysis complete"
        )
    }
    
    /**
     * Calculate average period duration from historical records.
     */
    private fun calculateAveragePeriodDuration(records: List<MenstrualRecord>): Int {
        val durations = records
            .filter { !it.isDeleted && it.endDate != null }
            .map { record ->
                val start = record.startDate
                val end = record.endDate!!
                kotlin.math.abs(end.toEpochDays() - start.toEpochDays()) + 1
            }
        
        return if (durations.isNotEmpty()) {
            durations.average().toInt().coerceIn(3, 10)
        } else {
            5  // Default
        }
    }
}

/**
 * Detailed prediction analysis for debugging/advanced users.
 */
data class PredictionAnalysis(
    val ensemble: EnsemblePrediction?,
    val evaluations: List<PredictorEvaluation>,
    val dataQuality: DataQuality,
    val cycleCount: Int,
    val outlierCount: Int,
    val skippedCycles: List<Int>,
    val message: String
) {
    val hasReliablePrediction: Boolean
        get() = ensemble != null && 
                ensemble.confidenceScore >= 0.45f &&
                dataQuality != DataQuality.INSUFFICIENT
    
    val bestPredictor: PredictorEvaluation?
        get() = evaluations.minByOrNull { it.meanAbsoluteError }
    
    val worstPredictor: PredictorEvaluation?
        get() = evaluations.maxByOrNull { it.meanAbsoluteError }
}

/**
 * Helper extension: add days to LocalDate.
 */
private fun LocalDate.plusDays(days: Int): LocalDate =
    LocalDate.fromEpochDays(this.toEpochDays() + days)
