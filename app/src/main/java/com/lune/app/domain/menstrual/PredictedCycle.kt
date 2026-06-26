package com.lune.app.domain.menstrual

import kotlinx.datetime.LocalDate

data class PredictedCycle(
    val predictedStart: LocalDate,
    val predictedEnd: LocalDate?
)
