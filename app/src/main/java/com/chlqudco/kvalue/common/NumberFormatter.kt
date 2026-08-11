package com.chlqudco.kvalue.common

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object NumberFormatter {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun won(value: Long): String = "${integer(value)}원"

    fun won(value: Double): String = won(value.roundToLong())

    fun multiple(value: Double): String = "${decimal(value)}배"

    fun percentage(value: Double): String = "${decimal(value)}%"

    fun signedPercentage(value: Double): String {
        val prefix = when {
            value > 0.0 -> "+"
            value < 0.0 -> "-"
            else -> ""
        }
        return "$prefix${decimal(abs(value))}%"
    }

    fun dateTime(value: LocalDateTime): String = value.format(dateTimeFormatter)

    fun compactWon(value: Long): String {
        val sign = if (value < 0L) "-" else ""
        val absolute = abs(value)
        val trillionUnit = 1_000_000_000_000L
        val hundredMillionUnit = 100_000_000L
        val trillion = absolute / trillionUnit
        val hundredMillion = absolute % trillionUnit / hundredMillionUnit
        return when {
            trillion > 0L && hundredMillion > 0L ->
                "$sign${integer(trillion)}조 ${integer(hundredMillion)}억 원"
            trillion > 0L -> "$sign${integer(trillion)}조 원"
            hundredMillion > 0L -> "$sign${integer(hundredMillion)}억 원"
            else -> "$sign${integer(absolute)}원"
        }
    }

    private fun integer(value: Long): String =
        NumberFormat.getIntegerInstance(Locale.KOREA).format(value)

    private fun decimal(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.KOREA)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(value)
    }
}
