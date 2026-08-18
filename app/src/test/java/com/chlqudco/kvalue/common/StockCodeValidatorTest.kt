/*
 * StockCodeValidator의 가장 작은 입력 계약을 확인하는 JVM 단위 테스트다.
 * 정상 6자리 코드의 공백 제거와 빈 값·짧은 값·문자 포함 값의 거부를 검증한다.
 * Android 기기 없이 실행되므로 입력 검증 로직이 프레임워크와 분리됐다는 점도 보여준다.
 */
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
