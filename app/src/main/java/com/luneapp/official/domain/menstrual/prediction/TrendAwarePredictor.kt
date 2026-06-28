package com.luneapp.official.domain.menstrual.prediction

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Trend-aware predictor using linear regression and exponential weighting.
 * 
 * This predictor detects and adapts to changing cycle patterns:
 * - Lengthening cycles (perimenopause, PCOS progression)
 * - Shortening cycles (perimenopause early phase, stress)
 * - Recent cycle changes (lifestyle, medication, age)
 * 
 * Algorithm:
 * 1. Detect linear trend in cycle lengths via simple linear regression
 * 2. Calculate exponentially weighted average (recent cycles weighted higher)
 * 3. Combine trend-adjusted prediction with weighted average
 * 4. Adjust confidence based on trend strength and stability
 * 
 * When to use:
 * - User has 5+ cycles showing consistent directional change
 * - Better than robust stats when cycles are systematically changing
 * - Ensemble will automatically favor this if it performs better in backtesting
 * 
 * Research basis:
 * - Harlow et al. (2003): Perimenopause shows progressive cycle lengthening
 * - Exponential smoothing: standard time series technique for recent data emphasis
 * - Linear regression: detects systematic trends vs random variation
 * 
 * @param decayRate Controls how much to emphasize recent cycles (default 0.2)
 *                  Higher = more emphasis on recent data
 */
class TrendAwarePredictor(
    private val decayRate: Double = 0.2
) : CyclePredictor {
    
    override val name: String = "TrendAware"
    
    override val minimumCyclesRequired: Int = 5
    
    override fun predict(cycles: List<CycleData>, lastPeriodStart: LocalDate): PredictionCandidate {
        require(cycles.size >= minimumCyclesRequired) {
            "$name predictor requires at least $minimumCyclesRequired cycles, got ${cycles.size}"
        }
        
        val cycleLengths = cycles.map { it.lengthDays }
        
        // Detect linear trend
        val trend = detectLinearTrend(cycleLengths)
        
        // Calculate exponentially weighted average (recent cycles emphasized)
        val weightedAvg = exponentiallyWeightedAverage(cycleLengths, decayRate)
        
        // Combine: start with weighted average, adjust by trend
        // If trend is positive (lengthening), predict slightly longer
        // If trend is negative (shortening), predict slightly shorter
        val trendAdjustment = trend.slope
        val predictedLength = weightedAvg + trendAdjustment
        
        val predictedDate = lastPeriodStart.plusDays(predictedLength.roundToInt())
        
        // Calculate confidence based on trend strength and stability
        val confidence = calculateConfidence(cycleLengths, trend)
        
        return PredictionCandidate(
            predictorName = name,
            predictedDate = predictedDate,
            confidence = confidence,
            historicalMAE = null
        )
    }
    
    /**
     * Detect linear trend using simple linear regression.
     * 
     * Fits a line: y = slope * x + intercept
     * Where:
     * - x = cycle index (0, 1, 2, ...)
     * - y = cycle length (days)
     * 
     * Slope interpretation:
     * - Positive slope: cycles getting longer (e.g., +0.5 means ~2 weeks longer over 4 cycles)
     * - Negative slope: cycles getting shorter
     * - Near-zero slope: no trend (robust predictor likely better)
     * 
     * Formula:
     * slope = Σ((x - x̄)(y - ȳ)) / Σ((x - x̄)²)
     * 
     * @return TrendResult with slope, R² (goodness of fit), and significance
     */
    private fun detectLinearTrend(cycleLengths: List<Int>): TrendResult {
        val n = cycleLengths.size
        val x = (0 until n).map { it.toDouble() }
        val y = cycleLengths.map { it.toDouble() }
        
        val xMean = x.average()
        val yMean = y.average()
        
        // Calculate slope: covariance(x,y) / variance(x)
        val numerator = x.zip(y).sumOf { (xi, yi) -> (xi - xMean) * (yi - yMean) }
        val denominator = x.sumOf { (it - xMean) * (it - xMean) }
        
        val slope = if (denominator > 0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean
        
        // Calculate R² (coefficient of determination): how well line fits data
        // R² = 1 - (SS_residual / SS_total)
        // R² near 1: strong trend, R² near 0: no trend
        val predictions = x.map { xi -> slope * xi + intercept }
        val ssResidual = y.zip(predictions).sumOf { (yi, pred) -> (yi - pred) * (yi - pred) }
        val ssTotal = y.sumOf { (it - yMean) * (it - yMean) }
        
        val rSquared = if (ssTotal > 0) 1.0 - (ssResidual / ssTotal) else 0.0
        
        // Statistical significance: is slope meaningfully different from zero?
        // Simple test: if |slope| > threshold and R² > threshold, consider significant
        val isSignificant = abs(slope) > 0.3 && rSquared > 0.3
        
        return TrendResult(
            slope = slope,
            intercept = intercept,
            rSquared = rSquared.coerceIn(0.0, 1.0),
            isSignificant = isSignificant
        )
    }
    
    /**
     * Calculate exponentially weighted average.
     * 
     * Recent cycles get exponentially higher weights:
     * - Most recent cycle: weight = e^0 = 1.0
     * - Second most recent: weight = e^(-λ) ≈ 0.82 (if λ=0.2)
     * - Third most recent: weight = e^(-2λ) ≈ 0.67
     * - And so on...
     * 
     * This naturally emphasizes recent patterns while still considering history.
     * Standard technique in time series forecasting (exponential smoothing).
     * 
     * @param values Cycle lengths (oldest to newest)
     * @param lambda Decay rate (higher = more emphasis on recent)
     * @return Weighted average cycle length
     */
    private fun exponentiallyWeightedAverage(values: List<Int>, lambda: Double = 0.2): Double {
        require(values.isNotEmpty()) { "Cannot calculate weighted average of empty list" }
        
        val n = values.size
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        values.forEachIndexed { index, value ->
            // More recent cycles (higher index) get higher weight
            // age = how many cycles ago (0 = most recent)
            val age = (n - 1 - index).toDouble()
            val weight = exp(-lambda * age)
            
            weightedSum += value * weight
            totalWeight += weight
        }
        
        return weightedSum / totalWeight
    }
    
    /**
     * Calculate confidence based on trend characteristics.
     * 
     * High confidence when:
     * - Clear, stable trend (high R²)
     * - Moderate slope (not extreme)
     * - Sufficient data (5+ cycles)
     * - Low variability around trend line
     * 
     * Lower confidence when:
     * - No clear trend (low R²) → robust predictor probably better
     * - Extreme slope (sudden change) → may be anomaly, not trend
     * - High variability (random fluctuations)
     */
    private fun calculateConfidence(cycleLengths: List<Int>, trend: TrendResult): Float {
        val n = cycleLengths.size
        
        // Base confidence from trend strength (R²)
        val trendConfidence = when {
            trend.rSquared > 0.7 -> 0.85f  // Strong trend
            trend.rSquared > 0.5 -> 0.70f  // Moderate trend
            trend.rSquared > 0.3 -> 0.55f  // Weak trend
            else -> 0.40f                   // No clear trend
        }
        
        // Penalty for extreme slopes (likely anomaly, not sustainable trend)
        // Slope > 2 means cycles changing by >2 days per cycle (very rare)
        val slopePenalty = when {
            abs(trend.slope) < 0.5 -> 1.0f    // Gentle trend: no penalty
            abs(trend.slope) < 1.5 -> 0.9f    // Moderate change: slight penalty
            abs(trend.slope) < 2.5 -> 0.7f    // Strong change: moderate penalty
            else -> 0.5f                       // Extreme change: high penalty
        }
        
        // Sample size adjustment
        val sampleSizeAdjustment = when (n) {
            in 0..4 -> 0.6f   // Below minimum (shouldn't happen)
            in 5..6 -> 0.85f  // Minimum for trend detection
            in 7..9 -> 0.95f  // Good sample
            else -> 1.0f      // Excellent sample
        }
        
        // Calculate residual variance (scatter around trend line)
        val residuals = cycleLengths.mapIndexed { index, length ->
            val predicted = trend.intercept + trend.slope * index
            abs(length - predicted)
        }
        val avgResidual = residuals.average()
        
        val residualPenalty = when {
            avgResidual < 2.0 -> 1.0f   // Very close to trend
            avgResidual < 4.0 -> 0.9f   // Moderate scatter
            avgResidual < 7.0 -> 0.75f  // High scatter
            else -> 0.6f                 // Very high scatter
        }
        
        return (trendConfidence * slopePenalty * sampleSizeAdjustment * residualPenalty)
            .coerceIn(0f, 1f)
    }
    
    /**
     * Result of linear trend analysis.
     */
    private data class TrendResult(
        val slope: Double,           // Days per cycle change
        val intercept: Double,       // Y-intercept of trend line
        val rSquared: Double,        // Goodness of fit (0-1)
        val isSignificant: Boolean   // Whether trend is meaningful
    )
}

/**
 * Helper extension: add days to a date.
 */
private fun LocalDate.plusDays(days: Int): LocalDate =
    kotlinx.datetime.LocalDate.Companion.fromEpochDays(this.toEpochDays() + days)
