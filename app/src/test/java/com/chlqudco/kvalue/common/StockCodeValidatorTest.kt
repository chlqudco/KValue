package com.chlqudco.kvalue.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StockCodeValidatorTest {
    @Test
    fun acceptsSixDigitsAndTrimsOuterWhitespace() {
        assertEquals(
            StockCodeValidation.Valid("005930"),
            StockCodeValidator.validate(" 005930 ")
        )
    }

    @Test
    fun rejectsEmptyShortAndNonNumericInputs() {
        assertEquals(StockCodeValidation.Empty, StockCodeValidator.validate("   "))
        assertEquals(StockCodeValidation.InvalidFormat, StockCodeValidator.validate("5930"))
        assertEquals(StockCodeValidation.InvalidFormat, StockCodeValidator.validate("00A930"))
        assertEquals(StockCodeValidation.InvalidFormat, StockCodeValidator.validate("0059301"))
    }
}
