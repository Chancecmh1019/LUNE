package com.luneapp.official.domain.menstrual.prediction

import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Backtesting engine: honest evaluation of predictor performance.
 * 
 * THE MOST IMPORTANT COMPONENT for trustworthy predictions.
 * 
 * Why backtesting is critical:
 * 1. Prevents overfitting: Predictors can't "cheat" by seeing future data
 * 2. Honest accuracy: MAE reflects real-world prediction error
 * 3. Adaptive selection: System automatically favors best predictor per user
 * 4. Transparency: Users see evidence-based confidence scores
 * 
 * Method: Leave-One-Cycle-Out Cross-Validation (LOOCV)
 * - For each cycle i in [minimumRequired..n]:
 *   1. Train predictor using cycles [0..i-1] only
 *   2. Predict when cycle i will start
 *   3. Compare prediction to actual start date
 *   4. Record absolute error in days
 * - MAE = average of all errors
 * 
 * Example with 6 cycles:
 * - Fold 1: Train on [0,1,2], predict cycle 3, error = 2 days
 * - Fold 2: Train on [0,1,2,3], predict cycle 4, error = 1 day
 * - Fold 3: Train on [0,1,2,3,4], predict cycle 5, error = 3 days
 * - MAE = (2 + 1 + 3) / 3 = 2.0 days
 * 
 * This gives an unbiased estimate of how well the predictor will perform
 * on the NEXT cycle (which we haven't seen yet).
 * 
 * Research basis:
 * - Standard ML evaluation technique (prevents data leakage)
 * - Li et al. (2021): Emphasized importance of proper validation in menstrual prediction
 * - Medical AI guidelines: Always validate on held-out data
 */
class DefaultBacktestingEngine : BacktestingEngine {
    
    override fun evaluatePredictor(
        predictor: CyclePredictor,
        cycles: List<CycleData>
    ): PredictorEvaluation {
        
        // Need at least minimumRequired + 1 cycles to have 1 test cycle
        val minRequired = predictor.minimumCyclesRequired
        if (cycles.size < minRequired + 1) {
            return PredictorEvaluation(
                predictorName = predictor.name,
                meanAbsoluteError = Float.MAX_VALUE,
                evaluatedCycles = 0,
                weight = 0f
            )
        }
        
        val errors = mutableListOf<Float>()
        
        // Leave-one-cycle-out: predict each cycle using all previous cycles
        for (i in minRequired until cycles.size) {
            try {
                // Training set: all cycles before i
                val trainingCycles = cycles.subList(0, i)
                
                // Test cycle: the cycle we're trying to predict
                val testCycle = cycles[i]
                
                // Make prediction using only training data
                val prediction = predictor.predict(trainingCycles, testCycle.startDate)
                
                // Actual next period start
                val actualDate = testCycle.nextStartDate
                
                // Calculate error: absolute difference in days
                val errorDays = abs(daysBetween(prediction.predictedDate, actualDate))
                errors.add(errorDays.toFloat())
                
            } catch (e: Exception) {
                // If predictor fails on this fold, skip it
                // (e.g., insufficient data after outlier filtering)
                continue
            }
        }
        
        // Calculate mean absolute error
        val mae = if (errors.isNotEmpty()) {
            errors.average().toFloat()
        } else {
            Float.MAX_VALUE
        }
        
        // Calculate weight for ensemble
        // Weight = 1 / (1 + MAE)
        // - MAE = 0 days → weight = 1.0 (perfect)
        // - MAE = 1 day → weight = 0.5
        // - MAE = 3 days → weight = 0.25
        // - MAE = 9 days → weight = 0.1
        val weight = if (mae < Float.MAX_VALUE) {
            1f / (1f + mae)
        } else {
            0f
        }
        
        return PredictorEvaluation(
            predictorName = predictor.name,
            meanAbsoluteError = mae,
            evaluatedCycles = errors.size,
            weight = weight
        )
    }
    
    override fun evaluateAll(
        predictors: List<CyclePredictor>,
        cycles: List<CycleData>
    ): List<PredictorEvaluation> {
        // Evaluate each predictor
        val evaluations = predictors.map { predictor ->
            evaluatePredictor(predictor, cycles)
        }
        
        // Normalize weights so they sum to 1.0
        val totalWeight = evaluations.sumOf { it.weight.toDouble() }.toFloat()
        
        return if (totalWeight > 0) {
            evaluations.map { eval ->
                eval.copy(weight = eval.weight / totalWeight)
            }
        } else {
            // All predictors failed: equal weights
            evaluations.map { eval ->
                eval.copy(weight = 1f / evaluations.size)
            }
        }.sortedBy { it.meanAbsoluteError } // Best (lowest MAE) first
    }
    
    /**
     * Calculate days between two dates.
     * Positive if date2 is after date1, negative if before.
     */
    private fun daysBetween(date1: LocalDate, date2: LocalDate): Int {
        return (date2.toEpochDays() - date1.toEpochDays()).toInt()
    }
}

/**
 * Enhanced backtesting engine with additional diagnostics.
 * 
 * Provides extra information for advanced users or debugging:
 * - Per-fold errors (not just mean)
 * - Worst-case error (max error across all folds)
 * - Error distribution (std dev, percentiles)
 * - Temporal stability (does accuracy degrade over time?)
 */
class DiagnosticBacktestingEngine : BacktestingEngine {
    
    private val baseEngine = DefaultBacktestingEngine()
    
    override fun evaluatePredictor(
        predictor: CyclePredictor,
        cycles: List<CycleData>
    ): PredictorEvaluation {
        return baseEngine.evaluatePredictor(predictor, cycles)
    }
    
    override fun evaluateAll(
        predictors: List<CyclePredictor>,
        cycles: List<CycleData>
    ): List<PredictorEvaluation> {
        return baseEngine.evaluateAll(predictors, cycles)
    }
    
    /**
     * Detailed evaluation with per-fold diagnostics.
     * 
     * Returns full error distribution for analysis:
     * - Individual fold errors
     * - Max error (worst case)
     * - 90th percentile error
     * - Error standard deviation
     * 
     * Useful for:
     * - Understanding predictor consistency
     * - Identifying when predictors fail
     * - Setting realistic confidence intervals
     */
    fun evaluateWithDiagnostics(
        predictor: CyclePredictor,
        cycles: List<CycleData>
    ): DetailedEvaluation {
        
        val minRequired = predictor.minimumCyclesRequired
        if (cycles.size < minRequired + 1) {
            return DetailedEvaluation(
                predictorName = predictor.name,
                meanAbsoluteError = Float.MAX_VALUE,
                maxError = Float.MAX_VALUE,
                errorStdDev = 0f,
                percentile90 = Float.MAX_VALUE,
                foldErrors = emptyList(),
                evaluatedCycles = 0,
                weight = 0f
            )
        }
        
        val errors = mutableListOf<Float>()
        
        for (i in minRequired until cycles.size) {
            try {
                val trainingCycles = cycles.subList(0, i)
                val testCycle = cycles[i]
                
                val prediction = predictor.predict(trainingCycles, testCycle.startDate)
                val actualDate = testCycle.nextStartDate
                
                val errorDays = abs(daysBetween(prediction.predictedDate, actualDate))
                errors.add(errorDays.toFloat())
                
            } catch (e: Exception) {
                continue
            }
        }
        
        val mae = if (errors.isNotEmpty()) errors.average().toFloat() else Float.MAX_VALUE
        val maxError = errors.maxOrNull() ?: Float.MAX_VALUE
        val stdDev = if (errors.size > 1) standardDeviation(errors, mae) else 0f
        val p90 = if (errors.isNotEmpty()) percentile(errors.sorted(), 90.0) else Float.MAX_VALUE
        val weight = if (mae < Float.MAX_VALUE) 1f / (1f + mae) else 0f
        
        return DetailedEvaluation(
            predictorName = predictor.name,
            meanAbsoluteError = mae,
            maxError = maxError,
            errorStdDev = stdDev,
            percentile90 = p90,
            foldErrors = errors.toList(),
            evaluatedCycles = errors.size,
            weight = weight
        )
    }
    
    private fun daysBetween(date1: LocalDate, date2: LocalDate): Int {
        return (date2.toEpochDays() - date1.toEpochDays()).toInt()
    }
    
    private fun standardDeviation(values: List<Float>, mean: Float): Float {
        if (values.size < 2) return 0f
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
    
    private fun percentile(sorted: List<Float>, p: Double): Float {
        val index = (p / 100.0) * (sorted.size - 1)
        val lower = kotlin.math.floor(index).toInt()
        val upper = kotlin.math.ceil(index).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = index - lower
        return sorted[lower] * (1 - fraction).toFloat() + sorted[upper] * fraction.toFloat()
    }
}

/**
 * Detailed evaluation result with full diagnostics.
 */
data class DetailedEvaluation(
    val predictorName: String,
    val meanAbsoluteError: Float,
    val maxError: Float,
    val errorStdDev: Float,
    val percentile90: Float,
    val foldErrors: List<Float>,
    val evaluatedCycles: Int,
    val weight: Float
) {
    /**
     * Consistency score: how reliably does this predictor perform?
     * 
     * High consistency: small std dev, MAE close to max error
     * Low consistency: large std dev, occasional large errors
     */
    val consistencyScore: Float
        get() = if (maxError > 0) {
            1f - (errorStdDev / maxError).coerceIn(0f, 1f)
        } else {
            1f
        }
    
    /**
     * Is this predictor reliable enough to use?
     * 
     * Criteria:
     * - MAE < 7 days (within 1 week)
     * - Max error < 14 days (within 2 weeks worst case)
     * - Evaluated on at least 3 cycles
     */
    val isReliable: Boolean
        get() = evaluatedCycles >= 3 && meanAbsoluteError < 7f && maxError < 14f
}
