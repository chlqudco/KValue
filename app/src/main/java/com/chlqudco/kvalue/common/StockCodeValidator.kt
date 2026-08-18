/*
 * 사용자가 입력한 값이 한국 주식의 6자리 종목코드인지 검증한다.
 * 빈 입력과 형식 오류를 구분해 UI가 서로 다른 안내 문구를 표시할 수 있게 한다.
 * 앞뒤 공백만 제거하며 숫자 보정이나 0 채우기를 하지 않아 사용자의 입력을 임의로 추정하지 않는다.
 * Android API에 의존하지 않는 순수 Kotlin 코드라 JVM 단위 테스트로 빠르게 검증할 수 있다.
 */
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
