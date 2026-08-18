/*
 * 도메인 모델의 원본 숫자를 한국 사용자에게 보여줄 문자열로 변환한다.
 * 계산 로직은 숫자 타입을 유지하고 UI 경계에서만 이 포매터를 사용하도록 책임을 분리한다.
 * 원화, 배수, 백분율, 기준시각과 큰 금액의 조·억 단위 표현을 동일한 규칙으로 제공한다.
 * NumberFormat에 Locale.KOREA를 지정해 천 단위 구분과 소수점 표현이 기기 언어에 흔들리지 않게 한다.
 */
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

    fun number(value: Double): String = decimal(value)

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

    // 큰 실적 금액을 조와 억으로 나누되 부호를 절댓값 계산과 분리해 Long 원본 의미를 유지한다.
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

    // 배수와 백분율은 일관되게 소수 둘째 자리까지 표시한다.
    private fun decimal(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale.KOREA)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(value)
    }
}
