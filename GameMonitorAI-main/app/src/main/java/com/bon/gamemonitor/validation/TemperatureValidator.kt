package com.bon.gamemonitor.validation

import com.bon.gamemonitor.data.Metric
import kotlin.math.isFinite

class TemperatureValidator : Validator<Float> {
    override fun validate(metric: Metric<Float>): ValidationResult {
        val value = metric.value ?: return ValidationResult.Invalid("Temperature value is null", metric)
        if (!value.isFinite()) {
            return ValidationResult.Invalid("Temperature value is non-finite: $value", metric)
        }
        if (value < 0f) {
            return ValidationResult.Invalid("Temperature cannot be negative", metric)
        }
        return ValidationResult.Valid
    }
}