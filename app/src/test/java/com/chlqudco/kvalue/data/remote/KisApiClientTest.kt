package com.chlqudco.kvalue.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class KisApiClientTest {
    @Test
    fun usesAdjustedPricesForPeriodChart() {
        assertEquals("0", KIS_ADJUSTED_PRICE_CODE)
    }
}
