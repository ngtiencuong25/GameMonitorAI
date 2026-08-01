package com.bon.gamemonitor.validation

import com.bon.gamemonitor.data.Metric

class BatteryValidator : Validator<Int> {
    override fun validate(metric: Metric<Int>): ValidationResult {
        return when {
            metric.value == null -> ValidationResult.Invalid("Battery value is null", metric)
            metric.value!! < 0 -> ValidationResult.Invalid("Battery cannot be negative", metric)
            metric.value!! > 100 -> ValidationResult.Invalid("Battery cannot exceed 100", metric)
            else -> ValidationResult.Valid
        }
    }
}
