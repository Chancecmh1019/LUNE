package com.luneapp.official.domain.menstrual

sealed class AddRecordResult {
    data class Success(val record: MenstrualRecord) : AddRecordResult()
    object OverlappingPeriod : AddRecordResult()    // date range overlaps an existing record
    object InvalidDateRange : AddRecordResult()     // endDate < startDate
}
