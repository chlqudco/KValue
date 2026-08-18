/*
 * 앱 전체에서 사용하는 오류 분류를 한곳에 정의한다.
 * 네트워크 라이브러리의 예외나 서버 응답 문구를 UI에 직접 노출하지 않고 이 타입으로 변환한다.
 * sealed interface이므로 호출자는 when 식에서 가능한 오류를 빠짐없이 처리할 수 있다.
 * PartialData는 단순 실패와 구분해 누락 정보를 값으로 보존한다.
 */
package com.chlqudco.kvalue.common

sealed interface AppError {
    data object InvalidInput : AppError
    data object StockNotFound : AppError
    data object NetworkUnavailable : AppError
    data object Timeout : AppError
    data object Unauthorized : AppError
    data object RateLimited : AppError
    data object ServiceUnavailable : AppError
    data class PartialData(val missing: Set<String>) : AppError
    data object Unknown : AppError
}
