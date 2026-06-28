package com.luneapp.official.domain.menstrual.prediction

import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ensemble predictor: combines multiple algorithms into final prediction.
 * 
 * Why ensemble methods work:
 * 1. Wisdom of crowds: Multiple independent estimates are more accurate than any single one
 * 2. Robustness: If one predictor fails, others compensate
 * 3. Adaptivity: Ensemble automatically favors best predictor per user
 * 4. Confidence calibration: Agreement between predictors indicates reliability
 * 
 * Strategy:
 * 1. Weight each predictor by backtesting performance: weight = 1 / (1 + MAE)
 * 2. Calculate weighted average of predicted dates
 * 3. Measure predictor agreement (std dev of predictions)
 * 4. Combine factors into confidence score:
 *    - Data quality (quantity + regularity)
 *    - Predictor agreement (low std dev = high confidence)
 *    - Best predictor MAE (historical accuracy)
 * 5. Set uncertainty window based on agreement and MAE
 * 
 * Example:
 * - Median: predicts Day 30, MAE = 2.5, weight = 0.29
 * - TrendAware: predicts Day 31, MAE = 2.0, weight = 0.33
 * - (Future) ARIMA: predicts Day 29, MAE = 3.0, weight = 0.25
 * 
 * Weighted average:
 * (30×0.29 + 31×0.33 + 29×0.25) / (0.29+0.33+0.25) = 30.1 ≈ Day 30
 * 
 * Agreement:
 * stdDev([30, 31, 29]) = 0.82 days (low disagreement = high confidence)
 * 
 * Research basis:
 * - Random forests, gradient boosting: ensemble learning is state-of-art in ML
 * - Medical prediction models: ensembles reduce variance and bias
 * - Bayesian model averaging: weight by performance
 */
class WeightedEnsemblePredictor : EnsemblePredictor {
    
    override fun combine(
        candidates: List<PredictionCandidate>,
        evaluations: List<PredictorEvaluation>,
        dataQuality: DataQuality,
        cycleCount: Int,
        cycleStdDev: Float
    ): EnsemblePrediction {
        
        require(candidates.isNotEmpty()) { "Cannot create ensemble from zero candidates" }
        require(evaluations.isNotEmpty()) { "Cannot create ensemble without evaluations" }
        
        // Filter to only reliable predictors (MAE exists and is reasonable)
        val reliableCandidates = candidates.filter { candidate ->
            val eval = evaluations.find { it.predictorName == candidate.predictorName }
            eval != null && eval.meanAbsoluteError < Float.MAX_VALUE
        }
        
        if (reliableCandidates.isEmpty()) {
            // Fallback: use all candidates with equal weights
            return createFallbackPrediction(candidates, dataQuality, cycleCount, cycleStdDev)
        }
        
        // Calculate weighted average of predicted dates
        val weightedDate = calculateWeightedAverageDate(reliableCandidates, evaluations)
        
        // Measure predictor agreement (std dev of predictions)
        val agreementStdDev = calculatePredictionAgreement(reliableCandidates)
        
        // Select best predictor (lowest MAE)
        val bestEvaluation = evaluations
            .filter { it.meanAbsoluteError < Float.MAX_VALUE }
            .minByOrNull { it.meanAbsoluteError }
            ?: evaluations.first()
        
        // Calculate overall confidence score
        val confidence = calculateOverallConfidence(
            dataQuality = dataQuality,
            agreementStdDev = agreementStdDev,
            bestMAE = bestEvaluation.meanAbsoluteError,
            cycleCount = cycleCount
        )
        
        // Calculate uncertainty window
        val windowDays = calculateUncertaintyWindow(
            agreementStdDev = agreementStdDev,
            bestMAE = bestEvaluation.meanAbsoluteError,
            dataQuality = dataQuality
        )
        
        // Generate human-readable explanation
        val explanation = generateExplanation(
            dataQuality = dataQuality,
            cycleCount = cycleCount,
            bestPredictorName = bestEvaluation.predictorName,
            bestMAE = bestEvaluation.meanAbsoluteError,
            agreementStdDev = agreementStdDev,
            confidence = confidence
        )
        
        // Add MAE to candidates
        val enrichedCandidates = reliableCandidates.map { candidate ->
            val eval = evaluations.find { it.predictorName == candidate.predictorName }
            candidate.copy(historicalMAE = eval?.meanAbsoluteError)
        }
        
        return EnsemblePrediction(
            predictedDate = weightedDate,
            confidenceScore = confidence,
            confidenceWindowDays = windowDays,
            dataQuality = dataQuality,
            candidates = enrichedCandidates,
            selectedPredictorName = bestEvaluation.predictorName,
            explanation = explanation,
            basedOnCycleCount = cycleCount,
            cycleStdDevDays = cycleStdDev
        )
    }
    
    /**
     * Calculate weighted average of predicted dates.
     * 
     * Each predictor contributes proportionally to its weight (inversely to MAE).
     * 
     * Formula:
     * weighted_date = Σ(date_i × weight_i) / Σ(weight_i)
     * 
     * Must convert dates to epoch days for arithmetic, then back to date.
     */
    private fun calculateWeightedAverageDate(
        candidates: List<PredictionCandidate>,
        evaluations: List<PredictorEvaluation>
    ): LocalDate {
        
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        candidates.forEach { candidate ->
            val eval = evaluations.find { it.predictorName == candidate.predictorName }
            if (eval != null && eval.weight > 0) {
                val epochDays = candidate.predictedDate.toEpochDays().toDouble()
                weightedSum += epochDays * eval.weight
                totalWeight += eval.weight
            }
        }
        
        val averageEpochDays = if (totalWeight > 0) {
            (weightedSum / totalWeight).roundToInt()
        } else {
            // Fallback: use first candidate
            candidates.first().predictedDate.toEpochDays().toInt()
        }
        
        return LocalDate.fromEpochDays(averageEpochDays)
    }
    
    /**
     * Measure agreement between predictors.
     * 
     * Low std dev = predictors agree = high confidence
     * High std dev = predictors disagree = low confidence
     * 
     * Returns std dev in days.
     */
    private fun calculatePredictionAgreement(candidates: List<PredictionCandidate>): Float {
        if (candidates.size < 2) return 0f
        
        val epochDays = candidates.map { it.predictedDate.toEpochDays().toDouble() }
        val mean = epochDays.average()
        val variance = epochDays.map { (it - mean) * (it - mean) }.average()
        
        return sqrt(variance).toFloat()
    }
    
    /**
     * Calculate overall confidence score (0.0 - 1.0).
     * 
     * Combines four factors:
     * 1. Data quality (40%): More cycles + lower regularity CV = higher confidence
     * 2. Predictor agreement (25%): Low std dev of predictions = higher confidence
     * 3. Historical accuracy (25%): Lower MAE from backtesting = higher confidence
     * 4. Sample size (10%): More evaluated cycles = higher confidence
     * 
     * Formula:
     * confidence = 0.4×dataScore + 0.25×agreementScore + 0.25×accuracyScore + 0.1×sampleScore
     */
    private fun calculateOverallConfidence(
        dataQuality: DataQuality,
        agreementStdDev: Float,
        bestMAE: Float,
        cycleCount: Int
    ): Float {
        
        // 1. Data quality score (based on enum)
        val dataScore = when (dataQuality) {
            DataQuality.EXCELLENT -> 1.0f
            DataQuality.GOOD -> 0.85f
            DataQuality.FAIR -> 0.65f
            DataQuality.POOR -> 0.40f
            DataQuality.INSUFFICIENT -> 0.20f
        }
        
        // 2. Agreement score (based on std dev of predictions)
        // 0 days disagreement = 1.0, 5+ days disagreement = 0.0
        val agreementScore = (1f - (agreementStdDev / 5f)).coerceIn(0f, 1f)
        
        // 3. Accuracy score (based on best predictor's MAE)
        // MAE 0 days = 1.0, MAE 7+ days = 0.0
        val accuracyScore = (1f - (bestMAE / 7f)).coerceIn(0f, 1f)
        
        // 4. Sample size score (based on cycle count)
        // 3 cycles = 0.5, 10+ cycles = 1.0
        val sampleScore = ((cycleCount - 3) / 7f).coerceIn(0f, 1f)
        
        // Weighted combination
        val confidence = (
            dataScore * 0.40f +
            agreementScore * 0.25f +
            accuracyScore * 0.25f +
            sampleScore * 0.10f
        ).coerceIn(0f, 1f)
        
        return confidence
    }
    
    /**
     * Calculate uncertainty window (± days).
     * 
     * Larger window when:
     * - Predictors disagree (high std dev)
     * - Historical accuracy poor (high MAE)
     * - Data quality low
     * 
     * Formula:
     * window = max(agreementStdDev × 1.5, bestMAE × 1.0, dataQualityMin)
     * Clamped to 1-10 days
     */
    private fun calculateUncertaintyWindow(
        agreementStdDev: Float,
        bestMAE: Float,
        dataQuality: DataQuality
    ): Int {
        
        // Base window from predictor disagreement
        val agreementWindow = agreementStdDev * 1.5f
        
        // Base window from historical error
        val accuracyWindow = bestMAE * 1.0f
        
        // Minimum window based on data quality
        val dataQualityMin = when (dataQuality) {
            DataQuality.EXCELLENT -> 1
            DataQuality.GOOD -> 2
            DataQuality.FAIR -> 3
            DataQuality.POOR -> 4
            DataQuality.INSUFFICIENT -> 5
        }
        
        // Take the maximum of all factors
        val window = maxOf(agreementWindow, accuracyWindow, dataQualityMin.toFloat())
        
        // Clamp to reasonable range
        return window.roundToInt().coerceIn(1, 10)
    }
    
    /**
     * Generate human-readable explanation for users.
     * 
     * Explains:
     * - How much data was used
     * - Which algorithm was selected
     * - How accurate we expect to be
     * - Why confidence is high/medium/low
     */
    private fun generateExplanation(
        dataQuality: DataQuality,
        cycleCount: Int,
        bestPredictorName: String,
        bestMAE: Float,
        agreementStdDev: Float,
        confidence: Float
    ): String {
        
        val dataDesc = when (dataQuality) {
            DataQuality.EXCELLENT -> "excellent data quality with $cycleCount cycles"
            DataQuality.GOOD -> "good data quality with $cycleCount cycles"
            DataQuality.FAIR -> "moderate data quality with $cycleCount cycles"
            DataQuality.POOR -> "limited data with $cycleCount cycles"
            DataQuality.INSUFFICIENT -> "insufficient data (need at least 3 cycles)"
        }
        
        val algorithmDesc = when (bestPredictorName) {
            "Median" -> "median cycle length"
            "TrimmedMean" -> "trimmed mean (robust average)"
            "TrendAware" -> "trend-aware prediction"
            else -> bestPredictorName
        }
        
        val accuracyDesc = when {
            bestMAE < 2f -> "typically accurate within 2 days"
            bestMAE < 4f -> "typically accurate within 4 days"
            bestMAE < 7f -> "typically accurate within a week"
            else -> "predictions have higher uncertainty"
        }
        
        val confidenceReason = when {
            confidence >= 0.75f -> "High confidence: regular cycles and good algorithm agreement"
            confidence >= 0.45f -> when {
                agreementStdDev > 3f -> "Medium confidence: algorithms show some disagreement"
                bestMAE > 4f -> "Medium confidence: historical predictions varied"
                cycleCount < 6 -> "Medium confidence: limited cycle history"
                else -> "Medium confidence: moderate cycle regularity"
            }
            else -> when {
                dataQuality == DataQuality.INSUFFICIENT -> "Low confidence: need more cycle data"
                agreementStdDev > 5f -> "Low confidence: high prediction uncertainty"
                bestMAE > 7f -> "Low confidence: irregular cycle patterns"
                else -> "Low confidence: variable cycle lengths"
            }
        }
        
        return "Based on $dataDesc, using $algorithmDesc. $accuracyDesc. $confidenceReason."
    }
    
    /**
     * Fallback when no reliable evaluations available.
     * 
     * Uses simple average of all candidates with conservative confidence.
     */
    private fun createFallbackPrediction(
        candidates: List<PredictionCandidate>,
        dataQuality: DataQuality,
        cycleCount: Int,
        cycleStdDev: Float
    ): EnsemblePrediction {
        
        // Simple average of predicted dates
        val avgEpochDays = candidates
            .map { it.predictedDate.toEpochDays().toDouble() }
            .average()
            .roundToInt()
        
        val predictedDate = LocalDate.fromEpochDays(avgEpochDays)
        
        // Conservative confidence and wide window
        val confidence = 0.35f
        val windowDays = 5
        
        val explanation = "Based on $cycleCount cycles. Limited backtesting data available. " +
            "Prediction uses simple average with conservative confidence."
        
        return EnsemblePrediction(
            predictedDate = predictedDate,
            confidenceScore = confidence,
            confidenceWindowDays = windowDays,
            dataQuality = dataQuality,
            candidates = candidates,
            selectedPredictorName = candidates.first().predictorName,
            explanation = explanation,
            basedOnCycleCount = cycleCount,
            cycleStdDevDays = cycleStdDev
        )
    }
}
