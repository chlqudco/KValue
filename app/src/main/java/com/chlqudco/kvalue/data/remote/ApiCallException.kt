/*
 * 원격 데이터 소스 내부에서 AppError를 예외 흐름으로 전달하기 위한 얇은 래퍼다.
 * HTTP 상태, 공급자 오류 코드, 네트워크 IOException은 먼저 AppError로 정규화된 뒤 이 예외에 담긴다.
 * Repository 경계에서는 이 예외를 잡아 StockAnalysisResult 같은 명시적인 결과 타입으로 다시 변환한다.
 */
package com.chlqudco.kvalue.data.remote

import com.chlqudco.kvalue.common.AppError

internal class ApiCallException(
    val error: AppError
) : Exception()
