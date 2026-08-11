package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PerAssumptions

sealed interface PerValidationResult {
    data class Valid(val assumptions: PerAssumptions) : PerValidationResult
    data object InvalidValue : PerValidationResult
    data object InvalidOrder : PerValidationResult
}

object PerAssumptionsValidator {
    fun validate(
        conservative: String,
        base: String,
        optimistic: String
    ): PerValidationResult {
        val values = listOf(conservative, base, optimistic).map { it.toDoubleOrNull() }
        if (values.any { it == null || !it.isFinite() || it <= 0.0 }) {
            return PerValidationResult.InvalidValue
        }
        val assumptions = PerAssumptions(
            conservative = requireNotNull(values[0]),
            base = requireNotNull(values[1]),
            optimistic = requireNotNull(values[2])
        )
        if (assumptions.conservative > assumptions.base ||
            assumptions.base > assumptions.optimistic
        ) {
            return PerValidationResult.InvalidOrder
        }
        return PerValidationResult.Valid(assumptions)
    }
}
