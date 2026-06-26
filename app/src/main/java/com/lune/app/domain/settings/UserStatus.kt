package com.lune.app.domain.settings

/**
 * Represents the user's current health or life stage that may affect
 * menstrual cycle regularity and prediction accuracy.
 *
 * When the status is not [REGULAR], the prediction engine will suppress
 * or flag its output with an accuracy disclaimer, and the daily log will
 * expose additional clinical symptom fields relevant to that condition.
 */
enum class UserStatus(val isIrregular: Boolean) {

    /** Typical cycle with no known condition affecting regularity. */
    REGULAR(isIrregular = false),

    /**
     * Postpartum period or active breastfeeding.
     * Prolactin-driven anovulation commonly causes amenorrhea for
     * several months post-delivery. Cycle return is unpredictable.
     */
    POSTPARTUM(isIrregular = true),

    /**
     * Undergoing breast cancer treatment with Tamoxifen (a selective
     * estrogen receptor modulator). Tamoxifen frequently causes cycle
     * irregularity, amenorrhea, or induced menopause. Amenorrhea on
     * Tamoxifen does not reliably indicate true menopause.
     */
    ONCOLOGY_TAMOXIFEN(isIrregular = true),

    /**
     * Undergoing other oncology treatments (e.g., chemotherapy,
     * aromatase inhibitors, ovarian suppression therapy).
     * These agents frequently cause amenorrhea or erratic bleeding.
     */
    ONCOLOGY_OTHER(isIrregular = true),

    /**
     * Polycystic Ovary Syndrome (PCOS).
     * Characterized by oligo-ovulation or anovulation, leading to
     * cycles that range from < 21 to > 35 days, or absent for months.
     */
    PCOS(isIrregular = true),

    /**
     * Endometriosis.
     * May cause heavy, prolonged, or irregular bleeding and chronic
     * pelvic pain. Cycle length itself may be normal but flow character
     * is frequently abnormal.
     */
    ENDOMETRIOSIS(isIrregular = true),

    /**
     * Using hormonal contraception (combined oral contraceptive pill,
     * progestin-only pill, hormonal IUD, implant, or injectable).
     * Withdrawal bleeds on hormonal contraception are not true menstrual
     * cycles; standard ovulation-based predictions do not apply.
     */
    HORMONAL_CONTRACEPTION(isIrregular = true),

    /**
     * Thyroid disorder (hypothyroidism or hyperthyroidism).
     * Thyroid dysfunction disrupts the hypothalamic-pituitary-ovarian
     * axis, commonly producing oligomenorrhea, amenorrhea, or menorrhagia.
     */
    THYROID_DISORDER(isIrregular = true),

    /**
     * Perimenopause or menopause transition.
     * Cycles become increasingly variable in length and flow before
     * cessation. FSH elevation and estrogen fluctuation make predictions
     * unreliable.
     */
    PERIMENOPAUSE(isIrregular = true),

    /**
     * Other condition not listed above that the user knows affects
     * their cycle regularity.
     */
    OTHER_IRREGULAR(isIrregular = true);

    companion object {
        fun fromValue(value: String?): UserStatus =
            entries.firstOrNull { it.name == value } ?: REGULAR
    }
}
