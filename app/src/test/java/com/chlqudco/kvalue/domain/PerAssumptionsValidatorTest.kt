package com.chlqudco.kvalue.domain

import com.chlqudco.kvalue.domain.model.PerAssumptions
import org.junit.Assert.assertEquals
import org.junit.Test

class PerAssumptionsValidatorTest {
    @Test
    fun acceptsPositiveAscendingValues() {
        assertEquals(
            PerValidationResult.Valid(PerAssumptions(10.0, 15.0, 20.0)),
            PerAssumptionsValidator.validate("10", "15", "20")
        )
    }

    @Test
    fun rejectsEmptyZeroNegativeAndNonFiniteValues() {
        assertEquals(
            PerValidationResult.InvalidValue,
            PerAssumptionsValidator.validate("", "15", "20")
        )
        assertEquals(
            PerValidationResult.InvalidValue,
            PerAssumptionsValidator.validate("0", "15", "20")
        )
        assertEquals(
            PerValidationResult.InvalidValue,
            PerAssumptionsValidator.validate("-1", "15", "20")
        )
        assertEquals(
            PerValidationResult.InvalidValue,
            PerAssumptionsValidator.validate("NaN", "15", "20")
        )
    }

    @Test
    fun rejectsDescendingScenarioOrder() {
        assertEquals(
            PerValidationResult.InvalidOrder,
            PerAssumptionsValidator.validate("20", "15", "10")
        )
    }
}
