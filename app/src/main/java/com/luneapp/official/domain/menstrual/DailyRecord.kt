package com.luneapp.official.domain.menstrual

import kotlinx.datetime.LocalDate

/** Menstrual flow volume. */
enum class Intensity { LIGHT, MEDIUM, HEAVY }

/** General mood state. */
enum class Mood { HAPPY, NEUTRAL, SAD, VERY_SAD }

/**
 * Bleeding character for spotting or non-standard flow events.
 * Distinct from [Intensity] which describes normal menstrual volume.
 */
enum class BleedingType {
    /** Normal menstrual flow (default; no need to record separately). */
    NORMAL,
    /** Intermenstrual spotting or light mid-cycle bleeding. */
    SPOTTING,
    /** Heavier-than-usual or prolonged bleeding outside a typical period. */
    ABNORMAL_HEAVY,
}

/**
 * A single day's clinical log entry within a [MenstrualRecord].
 *
 * [symptoms] holds keys corresponding to the symptom catalogue defined
 * in the UI layer (e.g. "cramps", "hot_flash", "pelvic_pain"). The key
 * set is open so future symptoms can be added without a schema migration.
 *
 * [medications] is a free-text field for recording any medications taken
 * on this day (e.g. "Tamoxifen 20 mg", "Letrozole 2.5 mg", "NSAIDs").
 * This field is intentionally unstructured to accommodate the diversity
 * of oncology and hormonal regimens.
 *
 * [clinicalNotes] is a free-text field for the user to record lab values,
 * clinician observations, or any medically relevant information
 * (e.g. "FSH 42 IU/L", "CA-125 normal", "post-op day 3").
 *
 * [bleedingType] distinguishes normal flow from spotting or abnormal
 * bleeding, which is clinically significant for users on Tamoxifen
 * (where uterine bleeding must be reported promptly) or with PCOS.
 */
data class DailyRecord(
    val date: LocalDate,
    val intensity: Intensity? = null,
    val mood: Mood? = null,
    /** Open-ended symptom key list from the UI catalogue. */
    val symptoms: List<String> = emptyList(),
    /** General free-text notes (diary-style). */
    val notes: String? = null,
    /** Medication(s) taken today, free-text. */
    val medications: String? = null,
    /** Clinical observations, lab values, or clinician notes. */
    val clinicalNotes: String? = null,
    /** Bleeding character if different from normal menstrual flow. */
    val bleedingType: BleedingType = BleedingType.NORMAL,
)
