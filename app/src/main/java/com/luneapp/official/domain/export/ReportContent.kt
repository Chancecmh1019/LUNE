package com.luneapp.official.domain.export

import com.luneapp.official.domain.menstrual.BleedingType
import com.luneapp.official.domain.menstrual.DailyRecord
import com.luneapp.official.domain.menstrual.Intensity
import com.luneapp.official.domain.menstrual.MenstrualRecord
import com.luneapp.official.domain.menstrual.Mood
import com.luneapp.official.domain.menstrual.averagePeriodLength
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.until
import kotlin.time.Clock

/**
 * Platform-independent description of the clinical report image.
 * A renderer (Android Canvas) walks this list once to measure heights,
 * a second time to draw. No decorative characters or emoji are used;
 * the output is formatted as formal clinical documentation.
 */
internal sealed interface ReportLine {
    data class Text(val text: String, val style: ReportTextStyle) : ReportLine
    data class Spacer(val height: Float) : ReportLine
    data object HorizontalRule : ReportLine
    data object CardBegin : ReportLine
    data object CardEnd : ReportLine
}

internal enum class ReportTextStyle {
    Title,           // 48 bold
    Subtitle,        // 22 muted
    PatientNote,     // 20 muted italic-equivalent
    SectionHeading,  // 34 bold
    RecordTitle,     // 28 bold
    FieldLabel,      // 22 bold
    Body,            // 26
    BulletMuted,     // 22
    SmallMuted,      // 20 muted
    Disclaimer,      // 18 muted
}

internal data class ReportTextSpec(
    val fontSize: Float,
    val bold: Boolean,
    val muted: Boolean,
    val lineHeightMultiplier: Float,
)

internal fun ReportTextStyle.spec(): ReportTextSpec = when (this) {
    ReportTextStyle.Title          -> ReportTextSpec(48f, bold = true,  muted = false, lineHeightMultiplier = 1.30f)
    ReportTextStyle.Subtitle       -> ReportTextSpec(22f, bold = false, muted = true,  lineHeightMultiplier = 1.50f)
    ReportTextStyle.PatientNote    -> ReportTextSpec(20f, bold = false, muted = true,  lineHeightMultiplier = 1.55f)
    ReportTextStyle.SectionHeading -> ReportTextSpec(34f, bold = true,  muted = false, lineHeightMultiplier = 1.40f)
    ReportTextStyle.RecordTitle    -> ReportTextSpec(28f, bold = true,  muted = false, lineHeightMultiplier = 1.40f)
    ReportTextStyle.FieldLabel     -> ReportTextSpec(22f, bold = true,  muted = false, lineHeightMultiplier = 1.45f)
    ReportTextStyle.Body           -> ReportTextSpec(26f, bold = false, muted = false, lineHeightMultiplier = 1.55f)
    ReportTextStyle.BulletMuted    -> ReportTextSpec(22f, bold = false, muted = false, lineHeightMultiplier = 1.60f)
    ReportTextStyle.SmallMuted     -> ReportTextSpec(20f, bold = false, muted = true,  lineHeightMultiplier = 1.55f)
    ReportTextStyle.Disclaimer     -> ReportTextSpec(18f, bold = false, muted = true,  lineHeightMultiplier = 1.60f)
}

internal object ReportLayout {
    const val ImageWidth = 1080
    const val MarginLeft = 80
    const val MarginTop = 80
    const val MarginBottom = 80
    const val CardInset = 16f
    const val CardInnerPadding = 24f
    const val CardCorner = 8f
    const val CardHorizontalBleed = 0f
    const val RuleThickness = 3f

    // Clinical: monochrome palette, no accent colors
    const val ColorBackground: Int  = 0xFFFFFFFF.toInt()
    const val ColorCardBg: Int      = 0xFFFAFAFA.toInt()
    const val ColorCardBorder: Int  = 0xFFDDDDDD.toInt()
    const val ColorRuleBg: Int      = 0xFF444444.toInt()
    const val ColorText: Int        = 0xFF000000.toInt()
    const val ColorMuted: Int       = 0xFF555555.toInt()
}

internal object ReportContent {

    fun build(records: List<MenstrualRecord>, strings: ExportStrings): List<ReportLine> = buildList {
        val sorted = records.filter { !it.isDeleted }.sortedBy { it.startDate }

        // --- Header ---
        add(ReportLine.Text(strings.reportTitle, ReportTextStyle.Title))
        add(ReportLine.Spacer(6f))
        add(ReportLine.Text(strings.reportSubtitle, ReportTextStyle.Subtitle))
        add(ReportLine.Spacer(8f))
        add(ReportLine.Text("${strings.generatedOn}: ${formatNow()}", ReportTextStyle.SmallMuted))
        add(ReportLine.Spacer(12f))
        add(ReportLine.Text(strings.patientNote, ReportTextStyle.PatientNote))
        add(ReportLine.Spacer(28f))
        add(ReportLine.HorizontalRule)
        add(ReportLine.Spacer(24f))

        // --- Summary ---
        add(ReportLine.Text(strings.summaryHeader, ReportTextStyle.SectionHeading))
        add(ReportLine.Spacer(12f))
        add(ReportLine.CardBegin)

        val avgCycle  = computeAvgCycle(sorted)
        val avgPeriod = averagePeriodLength(sorted)
        val minCycle  = computeMinCycle(sorted)
        val maxCycle  = computeMaxCycle(sorted)

        addField(strings.summaryTotalRecords, sorted.size.toString())
        addField(
            strings.summaryAverageCycle,
            avgCycle?.let { "$it ${strings.unitDays}" } ?: strings.recordNoEnd,
        )
        addField(
            strings.summaryAveragePeriod,
            avgPeriod?.let { "$it ${strings.unitDays}" } ?: strings.recordNoEnd,
        )
        addField(
            strings.summaryShortestCycle,
            minCycle?.let { "$it ${strings.unitDays}" } ?: strings.recordNoEnd,
        )
        addField(
            strings.summaryLongestCycle,
            maxCycle?.let { "$it ${strings.unitDays}" } ?: strings.recordNoEnd,
        )
        add(ReportLine.CardEnd)
        add(ReportLine.Spacer(32f))

        // --- Records ---
        add(ReportLine.HorizontalRule)
        add(ReportLine.Spacer(24f))
        add(ReportLine.Text(strings.recordsHeader, ReportTextStyle.SectionHeading))
        add(ReportLine.Spacer(16f))

        if (sorted.isEmpty()) {
            add(ReportLine.Text(strings.noRecords, ReportTextStyle.BulletMuted))
        } else {
            sorted.forEachIndexed { index, record ->
                addRecordCard(index + 1, record, strings)
                add(ReportLine.Spacer(20f))
            }
        }

        add(ReportLine.Spacer(20f))
        add(ReportLine.HorizontalRule)
        add(ReportLine.Spacer(16f))
        add(ReportLine.Text(strings.disclaimer, ReportTextStyle.Disclaimer))
    }

    fun buildFileName(language: String): String {
        val now = Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val y  = now.year
        val m  = now.date.month.number.toString().padStart(2, '0')
        val d  = now.date.day.toString().padStart(2, '0')
        val hh = now.hour.toString().padStart(2, '0')
        val mm = now.minute.toString().padStart(2, '0')
        val prefix = if (language == "zh") "月經週期臨床報告" else "ClinicalCycleReport"
        return "${prefix}_${y}${m}${d}_${hh}${mm}.png"
    }

    // ---------- internals ----------

    private fun MutableList<ReportLine>.addRecordCard(
        indexDisplay: Int,
        record: MenstrualRecord,
        strings: ExportStrings,
    ) {
        add(ReportLine.CardBegin)

        // Header row: index + date range
        val startStr    = record.startDate.toString()
        val endStr      = record.endDate?.toString() ?: strings.recordOngoing
        val durationStr = record.endDate?.let {
            val days = record.startDate.until(it, DateTimeUnit.DAY).toInt() + 1
            "$days ${strings.unitDays}"
        } ?: strings.recordNoEnd

        add(ReportLine.Text("${strings.recordIndex} #$indexDisplay  ·  $startStr — $endStr  ($durationStr)", ReportTextStyle.RecordTitle))

        val daily = record.dailyRecords.sortedBy { it.date }
        if (daily.isNotEmpty()) {
            add(ReportLine.Spacer(12f))
            add(ReportLine.Text("${strings.dailyHeader}:", ReportTextStyle.FieldLabel))
            add(ReportLine.Spacer(4f))

            daily.forEach { day ->
                // Date line as a sub-header
                add(ReportLine.Text("  ${day.date}", ReportTextStyle.Body))

                // Each clinical field on its own indented line
                day.intensity?.let {
                    add(ReportLine.Text("    ${strings.dailyIntensity}: ${intensityLabel(it, strings)}", ReportTextStyle.BulletMuted))
                }
                if (day.bleedingType != BleedingType.NORMAL) {
                    add(ReportLine.Text("    ${strings.dailyBleedingType}: ${bleedingLabel(day.bleedingType, strings)}", ReportTextStyle.BulletMuted))
                }
                day.mood?.let {
                    add(ReportLine.Text("    ${strings.dailyMood}: ${moodLabel(it, strings)}", ReportTextStyle.BulletMuted))
                }
                if (day.symptoms.isNotEmpty()) {
                    add(ReportLine.Text("    ${strings.dailySymptoms}: ${day.symptoms.joinToString(", ")}", ReportTextStyle.BulletMuted))
                }
                if (!day.medications.isNullOrBlank()) {
                    add(ReportLine.Text("    ${strings.dailyMedications}: ${day.medications}", ReportTextStyle.BulletMuted))
                }
                if (!day.clinicalNotes.isNullOrBlank()) {
                    add(ReportLine.Text("    ${strings.dailyClinicalNotes}: ${day.clinicalNotes}", ReportTextStyle.BulletMuted))
                }
                if (!day.notes.isNullOrBlank()) {
                    add(ReportLine.Text("    ${strings.dailyNotes}: ${day.notes}", ReportTextStyle.BulletMuted))
                }
                add(ReportLine.Spacer(6f))
            }
        }

        add(ReportLine.CardEnd)
    }


    private fun MutableList<ReportLine>.addField(label: String, value: String) {
        add(ReportLine.Text("$label: $value", ReportTextStyle.Body))
        add(ReportLine.Spacer(4f))
    }

    private fun formatNow(): String {
        val now = Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val m  = now.date.month.number.toString().padStart(2, '0')
        val d  = now.date.day.toString().padStart(2, '0')
        val hh = now.hour.toString().padStart(2, '0')
        val mm = now.minute.toString().padStart(2, '0')
        return "${now.year}-$m-$d $hh:$mm"
    }

    private fun intensityLabel(intensity: Intensity, s: ExportStrings) = when (intensity) {
        Intensity.LIGHT  -> s.intensityLight
        Intensity.MEDIUM -> s.intensityMedium
        Intensity.HEAVY  -> s.intensityHeavy
    }

    private fun moodLabel(mood: Mood, s: ExportStrings) = when (mood) {
        Mood.HAPPY    -> s.moodHappy
        Mood.NEUTRAL  -> s.moodNeutral
        Mood.SAD      -> s.moodSad
        Mood.VERY_SAD -> s.moodVerySad
    }

    private fun bleedingLabel(type: BleedingType, s: ExportStrings) = when (type) {
        BleedingType.NORMAL         -> ""
        BleedingType.SPOTTING       -> s.bleedingSpotting
        BleedingType.HEAVY          -> s.bleedingHeavy
        BleedingType.CLOTS          -> s.bleedingClots
        BleedingType.PROLONGED      -> s.bleedingProlonged
    }

    private fun computeAvgCycle(records: List<MenstrualRecord>): Int? {
        val lens = cycleLengths(records)
        return if (lens.isEmpty()) null else lens.sum() / lens.size
    }

    private fun computeMinCycle(records: List<MenstrualRecord>): Int? =
        cycleLengths(records).minOrNull()

    private fun computeMaxCycle(records: List<MenstrualRecord>): Int? =
        cycleLengths(records).maxOrNull()

    private fun cycleLengths(records: List<MenstrualRecord>): List<Int> {
        val lens = mutableListOf<Int>()
        for (i in 0 until records.size - 1) {
            val len = records[i].startDate.until(records[i + 1].startDate, DateTimeUnit.DAY).toInt()
            if (len in 14..120) lens.add(len)
        }
        return lens
    }
}
