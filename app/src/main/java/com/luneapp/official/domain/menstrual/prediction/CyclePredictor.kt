package com.luneapp.official.domain.menstrual.prediction

import kotlinx.datetime.LocalDate

/**
 * Base interface for all cycle prediction algorithms.
 * 
 * Each predictor implements a specific statistical or ML approach:
 * - RobustStatisticalPredictor: Median and trimmed mean
 * - TrendAwarePredictor: Linear regression with recent cycle weighting
 * - (Future) ARIMAPredictor: Time series forecasting
 * - (Future) LSTMPredictor: Deep learning (optional, experimental)
 * 
 * Design principles:
 * 1. Each predictor is independent and testable
 * 2. Each self-assesses confidence based on data characteristics
 * 3. Ensemble combines them via backtesting-based weighting
 */
interface CyclePredictor {
    /**
     * Unique identifier for this predictor (e.g., "Median", "TrendAware").
     */
    val name: String
    
    /**
     * Minimum cycles required for this predictor to function.
     * Simple algorithms (median) need fewer; complex ones (LSTM) need more.
     */
    val minimumCyclesRequired: Int
    
    /**
     * Generate a prediction for the next period start date.
     * 
     * @param cycles Historical cycle data (already preprocessed/cleaned)
     * @param lastPeriodStart Most recent period start date
     * @return Prediction with confidence score
     * @throws IllegalArgumentException if cycles.size < minimumCyclesRequired
     */
    fun predict(cycles: List<CycleData>, lastPeriodStart: LocalDate): PredictionCandidate
    
    /**
     * Check if this predictor can run with the given data.
     */
    fun canPredict(cycles: List<CycleData>): Boolean =
        cycles.size >= minimumCyclesRequired
}

/**
 * Data preprocessor: converts raw records to clean cycle data.
 * 
 * Responsibilities:
 * 1. Convert MenstrualRecord pairs to CycleData
 * 2. Detect and filter outliers (IQR method)
 * 3. Assess data quality (quantity + regularity)
 * 4. Detect likely tracking skips (unusually long cycles)
 */
interface CycleDataProcessor {
    /**
     * Convert consecutive period records into cycle data.
     * 
     * Each cycle is defined by: startDate → nextStartDate
     * Requires at least 2 records to produce 1 cycle.
     */
    fun recordsToCycles(records: List<com.luneapp.official.domain.menstrual.MenstrualRecord>): List<CycleData>
    
    /**
     * Filter outliers using Interquartile Range (IQR) method.
     * 
     * Standard statistical outlier detection:
     * - Q1 = 25th percentile
     * - Q3 = 75th percentile
     * - IQR = Q3 - Q1
     * - Outliers: < Q1 - 1.5*IQR or > Q3 + 1.5*IQR
     * 
     * Also applies ConditionProfile validCycleLengthRange.
     */
    fun filterOutliers(cycles: List<CycleData>, validRange: IntRange): List<CycleData>
    
    /**
     * Assess data quality based on quantity and regularity.
     * 
     * Uses coefficient of variation (CV = stdDev / mean):
     * - CV < 0.10: Highly regular
     * - CV 0.10-0.15: Regular
     * - CV 0.15-0.25: Moderately irregular
     * - CV > 0.25: Highly irregular
     */
    fun assessDataQuality(cycles: List<CycleData>): DataQuality
    
    /**
     * Detect likely tracking skips (cycles > 60 days).
     * 
     * Returns indices of cycles that are probably missed tracking events
     * rather than true long cycles. Used for warning users.
     */
    fun detectSkips(cycles: List<CycleData>): List<Int>
}

/**
 * Backtesting engine: evaluates predictor performance on historical data.
 * 
 * Uses leave-one-cycle-out cross-validation (LOOCV):
 * - For each cycle i in [minimumRequired..n]:
 *   1. Train on cycles [0..i-1]
 *   2. Predict cycle i start date
 *   3. Calculate error = |predicted - actual|
 * - MAE = mean of all errors
 * 
 * This gives an honest estimate of real-world prediction accuracy.
 */
interface BacktestingEngine {
    /**
     * Evaluate a single predictor via LOOCV.
     * 
     * @param predictor The algorithm to evaluate
     * @param cycles All historical cycle data
     * @return Evaluation with MAE and weight for ensemble
     */
    fun evaluatePredictor(
        predictor: CyclePredictor,
        cycles: List<CycleData>
    ): PredictorEvaluation
    
    /**
     * Evaluate all predictors and return sorted by performance.
     * 
     * @param predictors All available algorithms
     * @param cycles Historical cycle data
     * @return Evaluations sorted by MAE (best first)
     */
    fun evaluateAll(
        predictors: List<CyclePredictor>,
        cycles: List<CycleData>
    ): List<PredictorEvaluation>
}

/**
 * Ensemble combiner: merges multiple predictions into final result.
 * 
 * Strategy:
 * 1. Weight each predictor by 1 / (1 + MAE) from backtesting
 * 2. Calculate weighted average of predicted dates
 * 3. Assess predictor agreement (std dev of predictions)
 * 4. Combine data quality + agreement + MAE into confidence score
 * 5. Set uncertainty window based on std dev and MAE
 */
interface EnsemblePredictor {
    /**
     * Combine multiple predictions into final ensemble result.
     * 
     * @param candidates All predictor outputs
     * @param evaluations Backtesting results for weighting
     * @param dataQuality Assessment of input data
     * @param cycleCount Number of cycles used
     * @param cycleStdDev Std dev of cycle lengths
     * @return Final prediction with confidence metadata
     */
    fun combine(
        candidates: List<PredictionCandidate>,
        evaluations: List<PredictorEvaluation>,
        dataQuality: DataQuality,
        cycleCount: Int,
        cycleStdDev: Float
    ): EnsemblePrediction
}
