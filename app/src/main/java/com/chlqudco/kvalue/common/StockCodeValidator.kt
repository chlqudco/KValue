package com.chlqudco.kvalue.common

sealed interface StockCodeValidation {
    data class Valid(val code: String) : StockCodeValidation
    data object Empty : StockCodeValidation
    data object InvalidFormat : StockCodeValidation
}

object StockCodeValidator {
    private val pattern = Regex("^\\d{6}$")

    fun validate(input: String): StockCodeValidation {
        val normalized = input.trim()
        return when {
            normalized.isEmpty() -> StockCodeValidation.Empty
            !pattern.matches(normalized) -> StockCodeValidation.InvalidFormat
            else -> StockCodeValidation.Valid(normalized)
        }
    }
}
