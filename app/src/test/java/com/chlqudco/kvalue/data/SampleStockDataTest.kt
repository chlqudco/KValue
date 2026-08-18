package com.chlqudco.kvalue.data

import com.chlqudco.kvalue.domain.HistoricalForecastCalculator
import com.chlqudco.kvalue.domain.model.HistoricalForecastResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleStockDataTest {
    @Test
    fun providesLongHistoryAndValidatedForecast() {
        val analysis = SampleStockData.samsungElectronics()

        val result = HistoricalForecastCalculator.calculate(
            history = analysis.forecastHistory,
            currentPrice = analysis.price.currentPrice
        )

        assertEquals(100, analysis.priceHistory.size)
        assertTrue(analysis.forecastHistory.size >= 600)
        assertTrue(result is HistoricalForecastResult.Available)
    }
}
