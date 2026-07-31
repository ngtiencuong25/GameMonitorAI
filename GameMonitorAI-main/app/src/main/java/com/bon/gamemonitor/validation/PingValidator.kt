package com.bon.gamemonitor.validation

import com.bon.gamemonitor.data.Metric

class PingValidator : Validator<Int> {
    override fun validate(metric: Metric<Int>): ValidationResult {
        return when {
            metric.value == null -> ValidationResult.Invalid("Ping value is null", metric)
            metric.value!! < 0 -> ValidationResult.Invalid("Ping cannot be negative", metric)
            else -> ValidationResult.Valid
        }
    }
}
