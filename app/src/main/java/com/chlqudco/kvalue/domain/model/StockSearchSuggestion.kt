/*
 * 자동완성 목록 한 행에 필요한 최소 도메인 모델이다.
 * 검색 알고리즘과 UI가 원격 DTO를 공유하지 않도록 종목코드와 회사명만 독립된 값으로 보존한다.
 * data class이므로 값 비교가 가능해 검색 정렬과 Compose 테스트에서 그대로 활용할 수 있다.
 */
package com.chlqudco.kvalue.domain.model

data class StockSearchSuggestion(
    val stockCode: String,
    val companyName: String
)
