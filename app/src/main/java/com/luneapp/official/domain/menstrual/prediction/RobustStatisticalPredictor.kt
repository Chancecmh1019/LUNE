package com.luneapp.official.domain.menstrual.prediction

import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Robust statistical predictor using median and trimmed mean.
 * 
 * This is the DEFAULT and SAFEST predictor for LUNE.
 * 
 * Why robust statistics?
 * - Resistant to outliers: One abnormal cycle won't skew predictions
 * - Well-established: Decades of statistical research backing these methods
 * - Transparent: Users can understand "we look at your typical cycle length"
 * - No overfitting: Simple methods with few assumptions
 * 
 * Algorithm:
 * 1. Calculate median cycle length (50th percentile)
 * 2. Calculate 10% trimmed mean (remove extreme 10% on each end)
 * 3. Use trimmed mean as primary prediction
 * 4. Confidence based on cycle regularity (coefficient of variation)
 * 
 * Research basis:
 * - Simmons et al. (2018): Personalized approaches outperform fixed 28-day assumptions
 * - Li et al. (2021): Robust statistics essential for real-world period tracking
 * - skipTrack (Duttweiler et al., 2024): Account for irregularity in confidence
 * 
 * @param useMedian If true, use median instead of trimmed mean (more conservative)
 */
class RobustStatisticalPredictor(
    private val useMedian: Boolean = false
) : CyclePredictor {
    
    override val name: String = if (useMedian) "Median" else "TrimmedMean"
    
    override val minimumCyclesRequired: Int = 3
    
    override fun predict(cycles: List<CycleData>, lastPeriodStart: LocalDate): PredictionCandidate {
        require(cycles.size >= minimumCyclesRequired) {
            "$name predictor requires at least $minimumCyclesRequired cycles, got ${cycles.size}"
        }
        
        val cycleLengths = cycles.map { it.lengthDays }
        
        // Choose prediction method
        val predictedLength = if (useMedian) {
            median(cycleLengths)
        } else {
            trimmedMean(cycleLengths, trimFraction = 0.1)
        }
        
        val predictedDate = lastPeriodStart.plusDays(predictedLength.roundToInt())
        
        // Calculate confidence based on cycle regularity
        val confidence = calculateConfidence(cycleLengths)
        
        return PredictionCandidate(
            predictorName = name,
            predictedDate = predictedDate,
            confidence = confidence,
            historicalMAE = null // Will be filled by backtesting
        )
    }
    
    /**
     * Calculate median (50th percentile) of cycle lengths.
     * 
     * Median is robust to outliers: the middle value when sorted.
     * More resistant to extreme values than mean.
     * 
     * Example: [25, 27, 28, 28, 29, 45] → median = 28
     * Compare to mean = 30.3 (pulled up by outlier 45)
     */
    private fun median(values: List<Int>): Double {
        require(values.isNotEmpty()) { "Cannot calculate median of empty list" }
        
        val sorted = values.sorted()
        val mid = sorted.size / 2
        
        return if (sorted.size % 2 == 0) {
            // Even number: average of two middle values
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            // Odd number: exact middle value
            sorted[mid].toDouble()
        }
    }
    
    /**
     * Calculate trimmed mean: remove extreme values, then average.
     * 
     * Trimmed mean balances robustness and efficiency:
     * - Remove extreme outliers (top and bottom trimFraction)
     * - Average the remaining "typical" values
     * - More efficient than median with sufficient data
     * 
     * Standard trim: 10% from each end (20% total)
     * 
     * Example with 10% trim on [21, 25, 26, 27, 28, 28, 29, 30, 31, 60]:
     * - Remove 1 lowest (21) and 1 highest (60)
     * - Average [25, 26, 27, 28, 28, 29, 30, 31] = 28.0
     * 
     * @param trimFraction Fraction to remove from each end (0.1 = 10%)
     */
    private fun trimmedMean(values: List<Int>, trimFraction: Double = 0.1): Double {
        require(values.isNotEmpty()) { "Cannot calculate trimmed mean of empty list" }
        require(trimFraction in 0.0..0.5) { "Trim fraction must be between 0 and 0.5" }
        
        // Need at least 6 values for meaningful 10% trimming (remove 1 from each end)
        if (values.size < 6) {
            return values.average()
        }
        
        val sorted = values.sorted()
        val trimCount = (sorted.size * trimFraction).toInt()
        
        // Remove trimCount from start and end
        val trimmed = sorted.drop(trimCount).dropLast(trimCount)
        
        return if (trimmed.isNotEmpty()) {
            trimmed.average()
        } else {
            // Fallback if trimming removes everything (shouldn't happen with valid trimFraction)
            values.average()
        }
    }
    
    /**
     * Calculate confidence score based on cycle regularity.
     * 
     * Uses Coefficient of Variation (CV) = stdDev / mean
     * CV measures relative variability:
     * - CV < 0.10: Highly regular → confidence 0.85
     * - CV 0.10-0.15: Regular → confidence 0.70
     * - CV 0.15-0.25: Moderately irregular → confidence 0.55
     * - CV > 0.25: Highly irregular → confidence 0.40
     * 
     * Why CV instead of just stdDev?
     * - CV is scale-independent: 3-day stdDev means different things for 25-day vs 35-day cycles
     * - Medical literature uses CV for cycle regularity assessment
     * 
     * Also adjusts for sample size: fewer cycles = lower confidence
     */
    private fun calculateConfidence(cycleLengths: List<Int>): Float {
        if (cycleLengths.isEmpty()) return 0f
        if (cycleLengths.size == 1) return 0.3f
        
        val mean = cycleLengths.average()
        val stdDev = standardDeviation(cycleLengths, mean)
        val cv = stdDev / mean // Coefficient of Variation
        
        // Base confidence from CV (regularity)
        val baseConfidence = when {
            cv < 0.10 -> 0.85f
            cv < 0.15 -> 0.70f
            cv < 0.25 -> 0.55f
            else -> 0.40f
        }
        
        // Adjust for sample size: fewer cycles = lower confidence
        val sampleSizeAdjustment = when (cycleLengths.size) {
            in 0..2 -> 0.5f  // Very low confidence
            in 3..5 -> 0.8f  // Moderate adjustment
            in 6..9 -> 0.95f // Small adjustment
            else -> 1.0f     // No adjustment for 10+ cycles
        }
        
        return (baseConfidence * sampleSizeAdjustment).coerceIn(0f, 1f)
    }
    
    /**
     * Calculate standard deviation: measure of variability.
     * 
     * Formula: sqrt( Σ(x - mean)² / (n-1) )
     * Uses Bessel's correction (n-1) for unbiased estimate
     */
    private fun standardDeviation(values: List<Int>, mean: Double? = null): Double {
        if (values.size < 2) return 0.0
        
        val avg = mean ?: values.average()
        val variance = values.map { (it - avg) * (it - avg) }.sum() / (values.size - 1)
        
        return sqrt(variance)
    }
}

/**
 * Helper extension: add days to a date.
 */
private fun LocalDate.plusDays(days: Int): LocalDate =
    kotlinx.datetime.LocalDate.Companion.fromEpochDays(this.toEpochDays() + days)
