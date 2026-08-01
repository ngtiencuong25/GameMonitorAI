package com.bon.gamemonitor.validation

import com.bon.gamemonitor.data.Metric

interface Validator<T> {
    fun validate(metric: Metric<T>): ValidationResult
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String, val metric: Metric<*>) : ValidationResult()
}
