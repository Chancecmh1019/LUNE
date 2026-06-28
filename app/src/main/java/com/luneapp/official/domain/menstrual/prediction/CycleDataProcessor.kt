package com.luneapp.official.domain.menstrual.prediction

import com.luneapp.official.domain.menstrual.MenstrualRecord
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Default implementation of cycle data processor.
 * 
 * Converts raw menstrual records into clean, analysis-ready cycle data:
 * 1. Pair consecutive periods to form cycles
 * 2. Filter outliers using IQR method
 * 3. Assess data quality
 * 4. Detect likely tracking skips
 * 
 * Robust preprocessing is essential for accurate predictions.
 */
class DefaultCycleDataProcessor : CycleDataProcessor {
    
    override fun recordsToCycles(records: List<MenstrualRecord>): List<CycleData> {
        // Need at least 2 periods to create 1 cycle
        if (records.size < 2) return emptyList()
        
        // Sort by start date and filter deleted records
        val validRecords = records
            .filter { !it.isDeleted }
            .sortedBy { it.startDate }
        
        if (validRecords.size < 2) return emptyList()
        
        // Create cycles from consecutive periods
        return validRecords.zipWithNext { current, next ->
            CycleData(
                startDate = current.startDate,
                nextStartDate = next.startDate,
                periodDurationDays = calculatePeriodDuration(current)
            )
        }
    }
    
    override fun filterOutliers(cycles: List<CycleData>, validRange: IntRange): List<CycleData> {
        if (cycles.size < 4) {
            // Not enough data for IQR outlier detection
            // Just apply range filter
            return cycles.filter { it.lengthDays in validRange }
        }
        
        val cycleLengths = cycles.map { it.lengthDays }
        
        // Apply IQR method
        val q1 = percentile(cycleLengths, 25.0)
        val q3 = percentile(cycleLengths, 75.0)
        val iqr = q3 - q1
        
        // IQR outlier bounds
        val lowerBound = q1 - 1.5 * iqr
        val upperBound = q3 + 1.5 * iqr
        
        // Filter: must be within both IQR bounds AND validRange
        return cycles.filter { cycle ->
            val length = cycle.lengthDays.toDouble()
            length >= lowerBound && 
            length <= upperBound && 
            cycle.lengthDays in validRange
        }
    }
    
    override fun assessDataQuality(cycles: List<CycleData>): DataQuality {
        val count = cycles.size
        
        // Insufficient data
        if (count < 3) return DataQuality.INSUFFICIENT
        
        // Calculate coefficient of variation (CV) for regularity
        val lengths = cycles.map { it.lengthDays.toDouble() }
        val mean = lengths.average()
        val stdDev = if (lengths.size > 1) {
            sqrt(lengths.map { (it - mean) * (it - mean) }.average())
        } else {
            0.0
        }
        
        val cv = if (mean > 0) stdDev / mean else 1.0
        
        // Assess based on quantity and regularity
        return when {
            count >= 15 && cv < 0.10 -> DataQuality.EXCELLENT
            count >= 10 && cv < 0.15 -> DataQuality.GOOD
            count >= 6 && cv < 0.25 -> DataQuality.FAIR
            count >= 3 -> DataQuality.POOR
            else -> DataQuality.INSUFFICIENT
        }
    }
    
    override fun detectSkips(cycles: List<CycleData>): List<Int> {
        // A cycle longer than 60 days is likely a missed tracking event
        // rather than a true long cycle (even for PCOS users, this is uncommon)
        return cycles.mapIndexedNotNull { index, cycle ->
            if (cycle.lengthDays > 60) index else null
        }
    }
    
    /**
     * Calculate period duration from a menstrual record.
     * 
     * If endDate is available, use it. Otherwise estimate from averages.
     */
    private fun calculatePeriodDuration(record: MenstrualRecord): Int {
        return if (record.endDate != null) {
            val start = record.startDate
            val end = record.endDate
            (kotlin.math.abs(end.toEpochDays() - start.toEpochDays()) + 1).toInt()
        } else {
            // Default estimate: 5 days
            5
        }
    }
    
    /**
     * Calculate percentile of a list of values.
     * 
     * Uses linear interpolation between closest ranks.
     * 
     * @param values List of integers
     * @param p Percentile (0-100)
     * @return Percentile value
     */
    private fun percentile(values: List<Int>, p: Double): Double {
        require(values.isNotEmpty()) { "Cannot calculate percentile of empty list" }
        require(p in 0.0..100.0) { "Percentile must be between 0 and 100" }
        
        val sorted = values.sorted()
        
        if (sorted.size == 1) return sorted[0].toDouble()
        
        // Calculate position: p% through the sorted list
        val position = (p / 100.0) * (sorted.size - 1)
        val lowerIndex = floor(position).toInt()
        val upperIndex = ceil(position).toInt()
        
        if (lowerIndex == upperIndex) {
            return sorted[lowerIndex].toDouble()
        }
        
        // Linear interpolation
        val lowerValue = sorted[lowerIndex].toDouble()
        val upperValue = sorted[upperIndex].toDouble()
        val fraction = position - lowerIndex
        
        return lowerValue + (upperValue - lowerValue) * fraction
    }
}

/**
 * Enhanced processor with additional diagnostics and cleaning options.
 * 
 * Provides:
 * - Configurable outlier sensitivity
 * - Detailed outlier report
 * - Gap detection (large time gaps between records)
 * - Duplicate detection
 */
class EnhancedCycleDataProcessor(
    private val iqrMultiplier: Double = 1.5,  // Standard is 1.5, can adjust sensitivity
    private val detectGaps: Boolean = true
) : CycleDataProcessor {
    
    private val baseProcessor = DefaultCycleDataProcessor()
    
    override fun recordsToCycles(records: List<MenstrualRecord>): List<CycleData> {
        return baseProcessor.recordsToCycles(records)
    }
    
    override fun filterOutliers(cycles: List<CycleData>, validRange: IntRange): List<CycleData> {
        if (cycles.size < 4) {
            return cycles.filter { it.lengthDays in validRange }
        }
        
        val cycleLengths = cycles.map { it.lengthDays }
        
        // Custom IQR with configurable multiplier
        val q1 = percentile(cycleLengths, 25.0)
        val q3 = percentile(cycleLengths, 75.0)
        val iqr = q3 - q1
        
        val lowerBound = q1 - iqrMultiplier * iqr
        val upperBound = q3 + iqrMultiplier * iqr
        
        return cycles.filter { cycle ->
            val length = cycle.lengthDays.toDouble()
            length >= lowerBound && 
            length <= upperBound && 
            cycle.lengthDays in validRange
        }
    }
    
    override fun assessDataQuality(cycles: List<CycleData>): DataQuality {
        return baseProcessor.assessDataQuality(cycles)
    }
    
    override fun detectSkips(cycles: List<CycleData>): List<Int> {
        return baseProcessor.detectSkips(cycles)
    }
    
    /**
     * Detect large gaps in tracking (user stopped tracking for a while).
     * 
     * A gap is defined as a period >90 days between cycles.
     * Different from skips: skip = forgot one period, gap = stopped tracking for months.
     * 
     * @return Indices of cycles after which there's a large gap
     */
    fun detectGaps(cycles: List<CycleData>): List<Int> {
        if (!detectGaps) return emptyList()
        
        return cycles.mapIndexedNotNull { index, cycle ->
            if (cycle.lengthDays > 90) index else null
        }
    }
    
    /**
     * Get detailed outlier report: which cycles are outliers and why.
     * 
     * Useful for showing users which data points were excluded.
     */
    fun getOutlierReport(cycles: List<CycleData>, validRange: IntRange): OutlierReport {
        if (cycles.size < 4) {
            return OutlierReport(
                totalCycles = cycles.size,
                outlierIndices = emptyList(),
                outlierReasons = emptyMap(),
                iqrBounds = null
            )
        }
        
        val cycleLengths = cycles.map { it.lengthDays }
        val q1 = percentile(cycleLengths, 25.0)
        val q3 = percentile(cycleLengths, 75.0)
        val iqr = q3 - q1
        val lowerBound = q1 - iqrMultiplier * iqr
        val upperBound = q3 + iqrMultiplier * iqr
        
        val outlierIndices = mutableListOf<Int>()
        val outlierReasons = mutableMapOf<Int, String>()
        
        cycles.forEachIndexed { index, cycle ->
            val length = cycle.lengthDays.toDouble()
            val reasons = mutableListOf<String>()
            
            if (length < lowerBound) {
                reasons.add("too short (< ${lowerBound.toInt()} days)")
            }
            if (length > upperBound) {
                reasons.add("too long (> ${upperBound.toInt()} days)")
            }
            if (cycle.lengthDays < validRange.first) {
                reasons.add("below valid range (< ${validRange.first} days)")
            }
            if (cycle.lengthDays > validRange.last) {
                reasons.add("above valid range (> ${validRange.last} days)")
            }
            
            if (reasons.isNotEmpty()) {
                outlierIndices.add(index)
                outlierReasons[index] = reasons.joinToString("; ")
            }
        }
        
        return OutlierReport(
            totalCycles = cycles.size,
            outlierIndices = outlierIndices,
            outlierReasons = outlierReasons,
            iqrBounds = IQRBounds(lowerBound.toInt(), upperBound.toInt(), q1.toInt(), q3.toInt())
        )
    }
    
    private fun percentile(values: List<Int>, p: Double): Double {
        require(values.isNotEmpty()) { "Cannot calculate percentile of empty list" }
        
        val sorted = values.sorted()
        if (sorted.size == 1) return sorted[0].toDouble()
        
        val position = (p / 100.0) * (sorted.size - 1)
        val lowerIndex = floor(position).toInt()
        val upperIndex = ceil(position).toInt()
        
        if (lowerIndex == upperIndex) {
            return sorted[lowerIndex].toDouble()
        }
        
        val lowerValue = sorted[lowerIndex].toDouble()
        val upperValue = sorted[upperIndex].toDouble()
        val fraction = position - lowerIndex
        
        return lowerValue + (upperValue - lowerValue) * fraction
    }
}

/**
 * Report of outliers detected during preprocessing.
 */
data class OutlierReport(
    val totalCycles: Int,
    val outlierIndices: List<Int>,
    val outlierReasons: Map<Int, String>,
    val iqrBounds: IQRBounds?
) {
    val outlierCount: Int
        get() = outlierIndices.size
    
    val cleanCycleCount: Int
        get() = totalCycles - outlierCount
    
    val outlierPercentage: Float
        get() = if (totalCycles > 0) {
            (outlierCount.toFloat() / totalCycles) * 100f
        } else {
            0f
        }
}

/**
 * IQR bounds for outlier detection.
 */
data class IQRBounds(
    val lowerBound: Int,
    val upperBound: Int,
    val q1: Int,
    val q3: Int
) {
    val iqr: Int
        get() = q3 - q1
}
