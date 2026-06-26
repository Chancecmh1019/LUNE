package com.lune.app.domain.menstrual

data class CycleState(
    val records: List<MenstrualRecord>,
    val predictions: List<PredictedCycle>,
    val currentPeriod: MenstrualRecord?,
    val inPredictedPeriod: Boolean,
    /**
     * Whether the prediction engine considers its output reliable.
     * False when the user's [UserStatus] is irregular (e.g. Tamoxifen,
     * PCOS, postpartum). The UI should display a clinical disclaimer
     * rather than presenting predictions as authoritative.
     */
    val predictionReliable: Boolean = true,
) {
    val inPeriod: Boolean get() = currentPeriod != null || inPredictedPeriod
}
